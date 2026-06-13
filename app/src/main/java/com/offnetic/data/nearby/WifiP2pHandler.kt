package com.offnetic.data.nearby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

interface WifiP2pCallback {
    fun onP2pReady(peerPublicKey: String, peerIp: String)
}

@Singleton
class WifiP2pHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ncapManagerProvider: Provider<NcapManager>
) {
    private val p2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var connectionInfoDeferred: CompletableDeferred<WifiP2pInfo>? = null
    private var isActive = false
    private var receiverRegistered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile var p2pConnected = false
    @Volatile var p2pWasActive = false
    @Volatile var p2pRetryAttempted = false

    private var callback: WifiP2pCallback? = null
    private val p2pConnectionReady = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var p2pCallJob: Job? = null

    companion object {
        private const val TAG = "P2P_CALL"
        private const val TIMEOUT_MS = 10_000L
        private const val MAX_RETRIES = 3
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            android.util.Log.e(TAG, "P2P broadcast received: ${intent.action}")
            if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(
                    WifiP2pManager.EXTRA_NETWORK_INFO
                )
                android.util.Log.e(TAG, "P2P networkInfo: isConnected=${networkInfo?.isConnected} isActive=$isActive")
                if (networkInfo?.isConnected == true && isActive) {
                    p2pManager?.requestConnectionInfo(channel) { info ->
                        if (info.groupFormed) {
                            android.util.Log.e(TAG,
                                "P2P connected — groupOwner=${info.isGroupOwner} " +
                                "ownerIp=${info.groupOwnerAddress?.hostAddress}")
                            p2pConnected = true
                            val deferred = connectionInfoDeferred
                            connectionInfoDeferred = null
                            deferred?.complete(info)
                        }
                    }
                } else if (networkInfo?.isConnected == false && p2pConnected) {
                    android.util.Log.e(TAG, "P2P disconnected mid-call")
                    p2pConnected = false
                }
            }
        }
    }

    suspend fun awaitP2pConnection(peerPublicKey: String, timeoutMs: Long = 15_000L): Boolean {
        return try {
            withTimeout(timeoutMs) {
                p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.await()
            }
        } catch (_: TimeoutCancellationException) {
            android.util.Log.e(TAG, "P2P connection timeout for ${peerPublicKey.take(8)}")
            false
        }
    }

    fun setCallback(cb: WifiP2pCallback) {
        callback = cb
    }

    fun startP2pCall(endpointId: String, peerPublicKey: String, myPublicKey: String) {
        if (p2pManager == null) {
            android.util.Log.e(TAG, "P2P manager null — P2P not supported on this device")
            p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
            return
        }
        val isInitiator = myPublicKey < peerPublicKey
        android.util.Log.e(TAG, "startP2pCall myKey=${myPublicKey.take(8)}... peerKey=${peerPublicKey.take(8)}... initiator=$isInitiator")
        if (isInitiator) {
            startAsInitiator(endpointId, peerPublicKey)
        } else {
            enterDiscoveryMode(peerPublicKey)
        }
    }

    private fun startAsInitiator(endpointId: String, peerPublicKey: String) {
        p2pCallJob?.cancel()
        p2pCallJob = scope.launch(Dispatchers.Main) {
            try {
                ensureChannel()
                isActive = true
                removeExistingGroups()
                deletePersistentGroups()
                android.util.Log.e(TAG, "Discovering peer (initiator)...")
                val peerMac = discoverPeerWithRetry(maxAttempts = 10, intervalMs = 2000L)
                if (peerMac.isNullOrEmpty() || peerMac == "02:00:00:00:00:00") {
                    android.util.Log.e(TAG, "no peer found via discovery")
                    p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
                    return@launch
                }

                suspendCancellableCoroutine<Unit> { cont ->
                    p2pManager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() { cont.resume(Unit) }
                        override fun onFailure(reason: Int) { cont.resume(Unit) }
                    })
                }

                val config = WifiP2pConfig().apply {
                    deviceAddress = peerMac
                    groupOwnerIntent = 1
                }

                android.util.Log.e(TAG, "Negotiating P2P with $peerMac via connect()")
                val deferred = CompletableDeferred<WifiP2pInfo>()
                connectionInfoDeferred = deferred

                p2pManager?.connect(channel!!, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { android.util.Log.e(TAG, "connect initiated") }
                    override fun onFailure(reason: Int) {
                        android.util.Log.e(TAG, "connect failed: $reason, retrying...")
                        scope.launch {
                            kotlinx.coroutines.delay(1000L)
                            if (isActive) {
                                p2pManager?.connect(channel!!, config, this@WifiP2pHandler.retryAction("connect", config) {
                                    android.util.Log.e(TAG, "connect retry initiated")
                                })
                            }
                        }
                    }
                })

                val info = try {
                    withTimeout(TIMEOUT_MS) { deferred.await() }
                } catch (_: Exception) {
                    android.util.Log.e(TAG, "negotiation timeout")
                    p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
                    return@launch
                }

                android.util.Log.e(TAG, "P2P established — GO=${info.isGroupOwner} ip=${info.groupOwnerAddress?.hostAddress}")
                p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(true)

                val peerIp = derivePeerIp(info)
                if (peerIp != null) {
                    callback?.onP2pReady(peerPublicKey, peerIp)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "P2P setup failed: ${e.message}")
                p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
            }
        }
    }

    private fun derivePeerIp(info: WifiP2pInfo): String? {
        val goIp = info.groupOwnerAddress?.hostAddress ?: return null
        if (!info.isGroupOwner) {
            return goIp
        }
        val parts = goIp.split(".")
        if (parts.size != 4) return goIp
        val last = parts[3].toIntOrNull() ?: return goIp
        val peerLast = if (last == 1) 2 else last - 1
        return "${parts[0]}.${parts[1]}.${parts[2]}.$peerLast"
    }

    private suspend fun discoverPeerWithRetry(maxAttempts: Int = 10, intervalMs: Long = 2000L): String? {
        android.util.Log.e(TAG, "discoverPeerWithRetry: starting discovery (maxAttempts=$maxAttempts, interval=$intervalMs)")
        val startDeferred = CompletableDeferred<Boolean>()
        p2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { startDeferred.complete(true) }
            override fun onFailure(reason: Int) {
                android.util.Log.e(TAG, "discoverPeers FAILED reason=$reason")
                startDeferred.complete(false)
            }
        })
        val started = startDeferred.await()
        android.util.Log.e(TAG, "discoverPeers started=$started")

        for (attempt in 1..maxAttempts) {
            if (!isActive) return null
            delay(intervalMs)
            val mac = suspendCancellableCoroutine<String?> { cont ->
                p2pManager?.requestPeers(channel) { peers ->
                    val mac = peers?.deviceList?.firstOrNull()?.deviceAddress
                    android.util.Log.e(TAG, "requestPeers attempt $attempt: MAC=$mac count=${peers?.deviceList?.size ?: 0}")
                    cont.resume(mac)
                }
            }
            if (!mac.isNullOrEmpty() && mac != "02:00:00:00:00:00") {
                return mac
            }
        }
        return null
    }

    fun enterDiscoveryMode(peerPublicKey: String) {
        if (p2pManager == null) {
            p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
            return
        }
        p2pCallJob?.cancel()
        p2pCallJob = scope.launch(Dispatchers.Main) {
            try {
                ensureChannel()
                isActive = true
                removeExistingGroups()
                deletePersistentGroups()
                android.util.Log.e(TAG, "Entering discovery mode (responder) for ${peerPublicKey.take(8)}")
                p2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { android.util.Log.e(TAG, "discovery mode active (responder)") }
                    override fun onFailure(reason: Int) { android.util.Log.e(TAG, "discovery mode failed reason=$reason") }
                })

                val deferred = CompletableDeferred<WifiP2pInfo>()
                connectionInfoDeferred = deferred

                val info = try {
                    withTimeout(30_000L) { deferred.await() }
                } catch (_: Exception) {
                    android.util.Log.e(TAG, "responder: connection wait timeout")
                    p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
                    return@launch
                }

                android.util.Log.e(TAG, "Responder connected — GO=${info.isGroupOwner} ip=${info.groupOwnerAddress?.hostAddress}")
                p2pConnected = true
                p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(true)

                val peerIp = derivePeerIp(info)
                if (peerIp != null) {
                    callback?.onP2pReady(peerPublicKey, peerIp)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "discovery mode error: ${e.message}")
                p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
            }
        }
    }

    fun onP2pPayload(senderPublicKey: String, endpointId: String, json: JSONObject) {
        android.util.Log.e(TAG, "P2P payload received (negotiated mode — ignoring)")
    }

    private fun ensureChannel() {
        if (channel == null && p2pManager != null) {
            channel = p2pManager!!.initialize(context, context.mainLooper, null)
        }
        if (!receiverRegistered && channel != null) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            }
            context.registerReceiver(receiver, filter)
            receiverRegistered = true
        }
    }

    fun teardown() {
        if (!isActive) return
        isActive = false
        p2pConnected = false
        p2pWasActive = false
        p2pRetryAttempted = false
        p2pCallJob?.cancel()
        p2pCallJob = null
        android.util.Log.e(TAG, "P2P teardown")
        try { p2pManager?.removeGroup(channel, null) } catch (_: Exception) {}
        connectionInfoDeferred = null
        p2pConnectionReady.values.forEach { if (!it.isCompleted) it.complete(false) }
        p2pConnectionReady.clear()
    }

    fun destroy() {
        teardown()
        if (receiverRegistered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        channel = null
    }

    private suspend fun removeExistingGroups() {
        val hasGroup = suspendCancellableCoroutine { cont ->
            try {
                p2pManager?.requestGroupInfo(channel) { group ->
                    cont.resume(group != null)
                }
            } catch (_: Exception) {
                cont.resume(false)
            }
        }
        if (hasGroup) {
            android.util.Log.e(TAG, "Removing stale P2P group")
            suspendCancellableCoroutine<Unit> { cont ->
                try {
                    p2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() { cont.resume(Unit) }
                        override fun onFailure(reason: Int) { cont.resume(Unit) }
                    })
                } catch (_: Exception) {
                    cont.resume(Unit)
                }
            }
        }
    }

    private fun deletePersistentGroups() {
        try {
            val method = WifiP2pManager::class.java.getMethod("deletePersistentGroup", WifiP2pManager.Channel::class.java, Int::class.javaPrimitiveType, WifiP2pManager.ActionListener::class.java)
            for (netid in 0..31) {
                try { method.invoke(p2pManager, channel, netid, null) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun retryAction(
        name: String,
        config: WifiP2pConfig?,
        attempt: Int = 1,
        onSuccess: () -> Unit
    ): WifiP2pManager.ActionListener {
        return object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                android.util.Log.e(TAG, "$name succeeded (attempt $attempt)")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                val reasonStr = when (reason) {
                    0 -> "ERROR"
                    1 -> "P2P_UNSUPPORTED"
                    2 -> "BUSY"
                    3 -> "NO_SERVICE_REQUESTS"
                    else -> "UNKNOWN($reason)"
                }
                android.util.Log.e(TAG, "$name failed (attempt $attempt, reason=$reason=$reasonStr)")
                if (reason != 1 && attempt < MAX_RETRIES) {
                    val delayMs = 500L * (1 shl (attempt - 1))
                    android.util.Log.e(TAG, "$name retrying in ${delayMs}ms")
                    scope.launch {
                        delay(delayMs)
                        if (isActive && config != null) {
                            p2pManager?.connect(
                                channel!!, config,
                                retryAction(name, config, attempt + 1, onSuccess)
                            )
                        }
                    }
                }
            }
        }
    }
}
