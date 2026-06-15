package com.offnetic.data.nearby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
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

    fun testCreateGroup() {
        if (p2pManager == null) {
            android.util.Log.e(TAG, "testCreateGroup: P2P manager null")
            return
        }
        scope.launch(Dispatchers.Main) {
            ensureChannel()
            removeExistingGroups()
            deletePersistentGroups()
            android.util.Log.e(TAG, "testCreateGroup: calling createGroup...")
            suspendCancellableCoroutine<Unit> { cont ->
                p2pManager!!.createGroup(channel!!, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        android.util.Log.e(TAG, "testCreateGroup: createGroup SUCCESS")
                        cont.resume(Unit)
                    }
                    override fun onFailure(reason: Int) {
                        android.util.Log.e(TAG, "testCreateGroup: createGroup FAILED reason=$reason")
                        cont.resume(Unit)
                    }
                })
            }
        }
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
            startAsGroupOwner(peerPublicKey)
        } else {
            connectToGroupOwner(peerPublicKey)
        }
    }

    private fun startAsGroupOwner(peerPublicKey: String) {
        p2pCallJob?.cancel()
        p2pCallJob = scope.launch(Dispatchers.Main) {
            try {
                ensureChannel()
                isActive = true
                removeExistingGroups()
                deletePersistentGroups()

                android.util.Log.e(TAG, "Creating P2P group (GO)...")
                suspendCancellableCoroutine<Unit> { cont ->
                    p2pManager!!.createGroup(channel!!, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            android.util.Log.e(TAG, "createGroup SUCCESS — waiting for peer connection")
                            cont.resume(Unit)
                        }
                        override fun onFailure(reason: Int) {
                            android.util.Log.e(TAG, "createGroup FAILED reason=$reason")
                            p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
                            cont.resume(Unit)
                        }
                    })
                }
                if (!isActive) return@launch

                val info = waitForP2pConnection()
                if (info == null || !info.groupFormed) {
                    android.util.Log.e(TAG, "GO: no peer joined group")
                    p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
                    return@launch
                }

                android.util.Log.e(TAG, "GO: group formed — isGO=${info.isGroupOwner} ip=${info.groupOwnerAddress?.hostAddress}")
                p2pConnected = true
                p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(true)

                val peerIp = derivePeerIp(info)
                if (peerIp != null) {
                    callback?.onP2pReady(peerPublicKey, peerIp)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "GO setup failed: ${e.message}")
                p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
            }
        }
    }

    private fun connectToGroupOwner(peerPublicKey: String) {
        p2pCallJob?.cancel()
        p2pCallJob = scope.launch(Dispatchers.Main) {
            try {
                ensureChannel()
                isActive = true
                removeExistingGroups()
                deletePersistentGroups()

                android.util.Log.e(TAG, "Discovering GO (responder)...")
                val goMac = discoverPeerWithRetry(maxAttempts = 15, intervalMs = 2000L)
                if (goMac.isNullOrEmpty() || goMac == "02:00:00:00:00:00") {
                    android.util.Log.e(TAG, "responder: no GO found")
                    p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
                    return@launch
                }

                android.util.Log.e(TAG, "responder: connecting to GO $goMac")
                val config = WifiP2pConfig().apply {
                    deviceAddress = goMac
                    groupOwnerIntent = 0
                    wps.setup = WpsInfo.PBC
                }
                var connected = false
                for (attempt in 1..5) {
                    if (!isActive) break
                    if (attempt > 1) delay(2000L)
                    val result = suspendCancellableCoroutine<Int> { cont ->
                        p2pManager?.connect(channel!!, config, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                android.util.Log.e(TAG, "responder: connect() SUCCESS (attempt $attempt)")
                                cont.resume(0)
                            }
                            override fun onFailure(reason: Int) {
                                android.util.Log.e(TAG, "responder: connect() FAILED reason=$reason (attempt $attempt)")
                                cont.resume(reason)
                            }
                        })
                    }
                    if (result == 0) {
                        connected = true
                        break
                    }
                }
                if (!connected || !isActive) {
                    p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
                    return@launch
                }
                if (!isActive) return@launch

                val info = waitForP2pConnection()
                if (info == null || !info.groupFormed) {
                    android.util.Log.e(TAG, "responder: connection failed")
                    p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
                    return@launch
                }

                android.util.Log.e(TAG, "responder: connected — isGO=${info.isGroupOwner} ip=${info.groupOwnerAddress?.hostAddress}")
                p2pConnected = true
                p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(true)

                val peerIp = derivePeerIp(info)
                if (peerIp != null) {
                    callback?.onP2pReady(peerPublicKey, peerIp)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "responder setup failed: ${e.message}")
                p2pConnectionReady.getOrPut(peerPublicKey) { CompletableDeferred() }.complete(false)
            }
        }
    }

    private suspend fun waitForP2pConnection(): WifiP2pInfo? {
        val deferred = CompletableDeferred<WifiP2pInfo>()
        connectionInfoDeferred = deferred
        return try {
            withTimeout(30_000L) { deferred.await() }
        } catch (_: Exception) {
            null
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
}
