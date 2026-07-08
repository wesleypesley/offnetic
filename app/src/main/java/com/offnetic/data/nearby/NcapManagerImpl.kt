package com.offnetic.data.nearby

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.offnetic.data.crypto.NcapEnvelope
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.db.entity.Profile
import com.offnetic.data.local.datastore.PreferencesRepository
import com.offnetic.domain.model.ConnectionState
import com.offnetic.domain.model.NearbyState
import com.offnetic.domain.model.PeerInfo
import com.offnetic.util.ProximityPingNotifier
import com.offnetic.util.MessageNotificationManager
import com.offnetic.service.IncomingCallService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NcapManagerImpl @Inject constructor(
    private val connectionsClient: ConnectionsClient,
    private val identityDao: IdentityDao,
    private val contactDao: ContactDao,
    private val profileDao: ProfileDao,
    private val prefs: PreferencesRepository,
    private val proximityPingNotifier: ProximityPingNotifier,
    private val messageNotificationManager: MessageNotificationManager,
    private val signalProtocolManager: SignalProtocolManager,
    private val messageDao: MessageDao,
    @ApplicationContext private val context: Context
) : NcapManager {

    companion object {
        const val SERVICE_ID = "com.offnetic.nearby"
        private const val PROXIMITY_SILENT_MS = 5 * 60 * 1000L
        private const val PING_COOLDOWN_MS = 15 * 60 * 1000L
        private const val MAX_DECRYPT_FAILURES = 3
        private const val FILE_META_TTL_MS = 5 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(FILE_META_TTL_MS)
                val cutoff = System.currentTimeMillis() - FILE_META_TTL_MS
                pendingFileMetas.entries.removeIf { it.value.receivedAt < cutoff }
                incomingFilePayloadReceivedAt.entries.removeIf { (payloadId, ts) ->
                    if (ts < cutoff) { incomingFilePayloads.remove(payloadId); true } else false
                }
            }
        }
    }

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    override val peers: StateFlow<List<PeerInfo>> = _peers.asStateFlow()

    private val _nearbyState = MutableStateFlow<NearbyState>(NearbyState.Idle)
    override val nearbyState: StateFlow<NearbyState> = _nearbyState.asStateFlow()
    private val _locationRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val locationRequired: SharedFlow<Unit> = _locationRequired.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<com.offnetic.domain.model.Message>(
        replay = 0, extraBufferCapacity = 64
    )
    override val incomingMessages: SharedFlow<com.offnetic.domain.model.Message> = _incomingMessages.asSharedFlow()

    private val _incomingCallSignals = MutableSharedFlow<CallSignal>(replay = 5, extraBufferCapacity = 16)
    override val incomingCallSignals: SharedFlow<CallSignal> = _incomingCallSignals.asSharedFlow()

    private val _incomingCallEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)
    override val incomingCallEvents: SharedFlow<String> = _incomingCallEvents.asSharedFlow()

    private val endpointPeers = ConcurrentHashMap<String, PeerInfo>()
    @Volatile private var isAdvertising = false
    @Volatile private var isDiscovering = false
    private var currentAdvertisingName: String = ""
    private val callActiveCount = AtomicInteger(0)
    private val deferredFilePayloads = ConcurrentHashMap<Long, Pair<String, Payload>>()
    private val processedFilePayloads = ConcurrentHashMap.newKeySet<Long>()

    override suspend fun getMyPublicKey(): String {
        return identityDao.getIdentity()?.publicKey ?: ""
    }

    override val isCallActive: Boolean get() = callActiveCount.get() > 0

    override fun setCallActive(active: Boolean) {
        val prev: Int
        val now: Int
        if (active) {
            prev = callActiveCount.getAndIncrement()
            now = prev + 1
        } else {
            prev = callActiveCount.getAndUpdate { maxOf(0, it - 1) }
            now = maxOf(0, prev - 1)
        }
        Timber.d("Call active count: $now (was $prev)")

        if (prev > 0 && now == 0) {
            processDeferredFiles()
        }
    }

    private fun processDeferredFiles() {
        val entries = deferredFilePayloads.toMap()
        for ((payloadId, pair) in entries) {
            val (endpointId, payload) = pair
            scope.launch {
                kotlinx.coroutines.delay(500L)
                try {
                    val publicKey = endpointPeers[endpointId]?.publicKey ?: return@launch
                    handleIncomingFileInternal(publicKey, payload)
                } finally {
                    deferredFilePayloads.remove(payloadId)
                }
            }
        }
    }

    private val endpointPayloadCallbacks = ConcurrentHashMap<String, PayloadCallback>()
    private val endpointPublicKeys = ConcurrentHashMap<String, String>()
    private val decryptFailureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val shatteredSessionBundles = ConcurrentHashMap<String, ByteArray>()
    private val preKeyBundleRetries = ConcurrentHashMap<String, ByteArray>()
    private val identityRetries = ConcurrentHashMap<String, ByteArray>()
    private val identitySentPeers = ConcurrentHashMap<String, Boolean>()
    private val heartbeatJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val pendingFileMetas = ConcurrentHashMap<Long, FileMeta>()
    private val incomingFilePayloads = ConcurrentHashMap<Long, Payload>()
    private val incomingFilePayloadReceivedAt = ConcurrentHashMap<Long, Long>()
    private val outgoingFileTransfers = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            val publicKey = connectionInfo.endpointName
            Timber.d("onConnectionInitiated: endpoint=${endpointId.take(8)}..., name=${publicKey.take(16)}...")
            android.util.Log.e("NcapConn", "onConnectionInitiated from ${publicKey.take(12)}...")
            updatePeerState(endpointId, ConnectionState.CONNECTING)

            scope.launch {
                val contact = contactDao.getByPublicKey(publicKey)
                if (contact == null) {
                    // Only accept connections from known contacts — auto-accepting strangers
                    // lets any nearby device force a handshake and probe the local identity.
                    Timber.w("onConnectionInitiated: rejecting unknown peer ${publicKey.take(8)}")
                    android.util.Log.e("NcapConn", "onConnectionInitiated: REJECT unknown ${publicKey.take(12)}")
                    rejectConnection(endpointId)
                    updatePeerState(endpointId, ConnectionState.DISCONNECTED)
                    return@launch
                }

                if (!endpointPeers.containsKey(endpointId)) {
                    endpointPeers[endpointId] = PeerInfo(
                        endpointId = endpointId,
                        publicKey = publicKey,
                        displayName = contact.displayName,
                        isContact = true,
                        connectionState = ConnectionState.CONNECTING,
                        lastSeenAt = System.currentTimeMillis(),
                        lastPingedAt = 0L
                    )
                    emitPeers()
                }

                acceptConnection(endpointId, publicKey)
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                Timber.d("onConnectionResult: CONNECTED endpoint=${endpointId.take(8)}...")
                android.util.Log.e("NcapConn", "onConnectionResult CONNECTED → ${endpointId.take(12)}...")
                updatePeerState(endpointId, ConnectionState.CONNECTED)

                scope.launch {
                    val publicKey = endpointPublicKeys[endpointId]
                        ?: endpointPeers[endpointId]?.publicKey
                        ?: run {
                            Timber.w("onConnectionResult: no public key for endpoint=${endpointId.take(8)}... (not in endpointPublicKeys or endpointPeers), skipping PRE_KEY_BUNDLE")
                            return@launch
                        }
                    val myIdentity = identityDao.getIdentity() ?: run {
                        Timber.w("onConnectionResult: no local identity, skipping PRE_KEY_BUNDLE")
                        return@launch
                    }
                    Timber.d("onConnectionResult: sending PRE_KEY_BUNDLE to peer=${publicKey.take(8)}...")
                    android.util.Log.e("NcapConn", "Sending PRE_KEY_BUNDLE → ${publicKey.take(12)}...")

                    shatteredSessionBundles.remove(publicKey)?.let { buffered ->
                        try {
                            sendPayload(endpointId, buffered)
                            Timber.d("Buffered shattered session reset sent to ${publicKey.take(8)}...")
                        } catch (e: Exception) {
                            shatteredSessionBundles[publicKey] = buffered
                        }
                        return@launch
                    }

                    preKeyBundleRetries.remove(publicKey)?.let { bufferedBundle ->
                        try {
                            sendPayload(endpointId, bufferedBundle)
                            Timber.d("Buffered PRE_KEY_BUNDLE retry sent to ${publicKey.take(8)}...")
                        } catch (e: Exception) {
                            preKeyBundleRetries[publicKey] = bufferedBundle
                        }
                        return@launch
                    }

                    identityRetries.remove(publicKey)?.let { bufferedIdentity ->
                        try {
                            sendPayload(endpointId, bufferedIdentity)
                            Timber.d("Buffered INITIAL_IDENTITY retry sent to ${publicKey.take(8)}...")
                        } catch (e: Exception) {
                            identityRetries[publicKey] = bufferedIdentity
                        }
                    }

                    try {
                        val bundleBytes = signalProtocolManager.buildPreKeyBundleBytes()
                        val envelope = NcapEnvelope.Plain(
                            senderPublicKey = myIdentity.publicKey,
                            payloadType = NcapEnvelope.PayloadType.PRE_KEY_BUNDLE,
                            payload = bundleBytes
                        )
                        sendPayload(endpointId, envelope.toBytes())
                        Timber.d("PRE_KEY_BUNDLE sent to ${publicKey.take(8)}...")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to send PRE_KEY_BUNDLE to ${publicKey.take(8)}..., buffering for retry")
                        val bundleBytes = signalProtocolManager.buildPreKeyBundleBytes()
                        val envelope = NcapEnvelope.Plain(
                            senderPublicKey = myIdentity.publicKey,
                            payloadType = NcapEnvelope.PayloadType.PRE_KEY_BUNDLE,
                            payload = bundleBytes
                        )
                        preKeyBundleRetries[publicKey] = envelope.toBytes()
                    }

                    try {
                        if (identitySentPeers.putIfAbsent(publicKey, true) != null) {
                            Timber.d("INITIAL_IDENTITY already sent to ${publicKey.take(8)}..., skipping")
                        } else {
                            val profile = profileDao.getByPublicKey(myIdentity.publicKey)
                            val displayName = profile?.displayName ?: myIdentity.publicKey.take(12)
                            val identityJson = org.json.JSONObject().apply {
                                put("publicKey", myIdentity.publicKey)
                                put("displayName", displayName)
                            }
                            val idEnvelope = NcapEnvelope.Plain(
                                senderPublicKey = myIdentity.publicKey,
                                payloadType = NcapEnvelope.PayloadType.INITIAL_IDENTITY,
                                payload = identityJson.toString().toByteArray(Charsets.UTF_8)
                            )
                            sendPayload(endpointId, idEnvelope.toBytes())
                            Timber.d("INITIAL_IDENTITY sent to ${publicKey.take(8)}...")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to send INITIAL_IDENTITY to ${publicKey.take(8)}..., buffering for retry")
                        val profile = profileDao.getByPublicKey(myIdentity.publicKey)
                        val displayName = profile?.displayName ?: myIdentity.publicKey.take(12)
                        val identityJson = org.json.JSONObject().apply {
                            put("publicKey", myIdentity.publicKey)
                            put("displayName", displayName)
                        }
                        val idEnvelope = NcapEnvelope.Plain(
                            senderPublicKey = myIdentity.publicKey,
                            payloadType = NcapEnvelope.PayloadType.INITIAL_IDENTITY,
                            payload = identityJson.toString().toByteArray(Charsets.UTF_8)
                        )
                        identityRetries[publicKey] = idEnvelope.toBytes()
                    }
                }
                startHeartbeat(endpointId)
            } else {
                Timber.w("onConnectionResult: FAILED endpoint=${endpointId.take(8)}... status=${resolution.status.statusCode}")
                android.util.Log.e("NcapConn", "onConnectionResult FAILED ${endpointId.take(12)}... code=${resolution.status.statusCode}")
                updatePeerState(endpointId, ConnectionState.FAILED)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Timber.d("Disconnected: $endpointId")
            heartbeatJobs.remove(endpointId)?.cancel()
            endpointPayloadCallbacks.remove(endpointId)
            val disconnectedKey = endpointPublicKeys.remove(endpointId)
                ?: endpointPeers[endpointId]?.publicKey
            if (disconnectedKey != null) identitySentPeers.remove(disconnectedKey)
            updatePeerState(endpointId, ConnectionState.DISCONNECTED)

            scope.launch {
                val peer = endpointPeers[endpointId]
                if (peer != null && peer.isContact) {
                    kotlinx.coroutines.delay(1500)
                    Timber.d("Reconnecting to: ${peer.displayName}")
                    requestConnection(endpointId)
                }
            }
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val publicKey = info.endpointName
            Timber.d("Endpoint found: ${publicKey.take(16)}...")

            scope.launch {
                handleEndpointFound(endpointId, publicKey)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Timber.d("Endpoint lost: $endpointId")
            scope.launch {
                handleEndpointLost(endpointId)
            }
        }
    }

    override fun startAdvertising(name: String) {
        if (isAdvertising) return
        currentAdvertisingName = name

        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            name,
            SERVICE_ID,
            lifecycleCallback,
            options
        ).addOnSuccessListener {
            isAdvertising = true
            _nearbyState.value = if (isDiscovering) NearbyState.Active else NearbyState.Advertising
            Timber.d("Advertising started as: ${name.take(16)}...")
        }.addOnFailureListener { e ->
            isAdvertising = false
            Timber.e(e, "Advertising failed (status: ${(e as? com.google.android.gms.common.api.ApiException)?.statusCode ?: "unknown"})")
            _nearbyState.value = NearbyState.Error("Advertising failed: ${e.message}")
        }
    }

    override fun startDiscovery() {
        if (isDiscovering) return
        try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                android.provider.Settings.Secure.getInt(context.contentResolver, android.provider.Settings.Secure.LOCATION_MODE) != android.provider.Settings.Secure.LOCATION_MODE_OFF
            }
            if (!locationEnabled) {
                android.util.Log.e("NcapConn", "startDiscovery: Location is OFF — NCAPI discovery will fail.")
                _nearbyState.value = NearbyState.Idle
                _locationRequired.tryEmit(Unit)
                return
            }
        } catch (_: Exception) {}

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

            connectionsClient.startDiscovery(SERVICE_ID, discoveryCallback, options)
                .addOnSuccessListener {
                isDiscovering = true
                _nearbyState.value = if (isAdvertising) NearbyState.Active else NearbyState.Discovering
                android.util.Log.e("NcapConn", "startDiscovery: SUCCESS")
            }.addOnFailureListener { e ->
                isDiscovering = false
                android.util.Log.e("NcapConn", "startDiscovery: FAILED — ${e.message}")
                _nearbyState.value = NearbyState.Error("Discovery failed: ${e.message}")
            }
    }

    override fun stopAdvertising() {
        if (!isAdvertising) return
        connectionsClient.stopAdvertising()
        isAdvertising = false
        _nearbyState.value = if (isDiscovering) NearbyState.Discovering else NearbyState.Idle
        Timber.d("Advertising stopped")
    }

    override fun stopDiscovery() {
        if (!isDiscovering) return
        connectionsClient.stopDiscovery()
        isDiscovering = false
        _nearbyState.value = if (isAdvertising) NearbyState.Advertising else NearbyState.Idle
        // Keep connected/connecting peers — only drop discovered-but-unconnected ones
        endpointPeers.entries.removeIf {
            it.value.connectionState != ConnectionState.CONNECTED &&
                it.value.connectionState != ConnectionState.CONNECTING
        }
        emitPeers()
        Timber.d("Discovery stopped")
    }

    override fun stopAll() {
        try { connectionsClient.stopAllEndpoints() } catch (_: Exception) {}
        stopAdvertising()
        stopDiscovery()
        heartbeatJobs.values.forEach { it.cancel() }
        heartbeatJobs.clear()
        endpointPayloadCallbacks.clear()
        endpointPublicKeys.clear()
        decryptFailureCounts.clear()
        deferredFilePayloads.clear()
        identitySentPeers.clear()
        _nearbyState.value = NearbyState.Idle
    }

    override fun forceRestart(name: String) {
        if (isAdvertising) {
            try { connectionsClient.stopAdvertising() } catch (_: Exception) {}
            isAdvertising = false
        }
        if (isDiscovering) {
            try { connectionsClient.stopDiscovery() } catch (_: Exception) {}
            isDiscovering = false
        }
        _nearbyState.value = NearbyState.Idle
        startAdvertising(name)
        startDiscovery()
    }

    override fun requestConnection(endpointId: String) {
        val existing = endpointPeers[endpointId]
        if (existing != null && (existing.connectionState == ConnectionState.CONNECTED || existing.connectionState == ConnectionState.CONNECTING)) {
            Timber.d("requestConnection: already ${existing.connectionState} to ${endpointId.take(8)}..., skipping")
            return
        }
        Timber.d("requestConnection: requesting to ${endpointId.take(8)}...")
        scope.launch {
            val identity = identityDao.getIdentity()
            val name = identity?.publicKey ?: currentAdvertisingName.ifEmpty { "Offnetic" }
            Timber.d("requestConnection: using name=${name.take(16)}...")
            connectionsClient.requestConnection(
                name,
                endpointId,
                lifecycleCallback
            ).addOnSuccessListener {
                Timber.d("requestConnection: success for ${endpointId.take(8)}...")
                android.util.Log.e("NcapConn", "requestConnection OK → ${endpointId.take(12)}...")
                updatePeerState(endpointId, ConnectionState.CONNECTING)
            }.addOnFailureListener { e ->
                Timber.e(e, "requestConnection: FAILED for ${endpointId.take(8)}...")
                android.util.Log.e("NcapConn", "requestConnection FAILED for ${endpointId.take(12)}... — ${e.message}")
                updatePeerState(endpointId, ConnectionState.FAILED)
                scope.launch {
                    kotlinx.coroutines.delay(3000L)
                    if (endpointPeers[endpointId]?.connectionState == ConnectionState.FAILED) {
                        Timber.d("requestConnection: retrying ${endpointId.take(8)}...")
                        requestConnection(endpointId)
                    }
                }
            }
        }
    }

    override fun acceptConnection(endpointId: String, publicKey: String) {
        Timber.d("acceptConnection: endpoint=${endpointId.take(8)}... peer=${publicKey.take(8)}...")
        android.util.Log.e("NcapConn", "acceptConnection ← ${publicKey.take(12)}...")
        endpointPublicKeys[endpointId] = publicKey

        val callback = createPayloadCallback(publicKey)
        endpointPayloadCallbacks[endpointId] = callback

        connectionsClient.acceptConnection(endpointId, callback)
    }

    override fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
        updatePeerState(endpointId, ConnectionState.DISCONNECTED)
    }

    override fun disconnectFromEndpoint(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
        endpointPayloadCallbacks.remove(endpointId)
        endpointPublicKeys.remove(endpointId)
        updatePeerState(endpointId, ConnectionState.DISCONNECTED)
    }

    private val reconnecting = ConcurrentHashMap<String, Boolean>()

    override fun reconnectToContact(publicKey: String) {
        if (reconnecting.putIfAbsent(publicKey, true) != null) {
            android.util.Log.e("NcapConn", "reconnectToContact: already reconnecting to ${publicKey.take(12)}..., skipping")
            return
        }
        scope.launch {
            try {
                val endpointId = findOrAwaitEndpoint(publicKey)
                if (endpointId == null) {
                    Timber.d("reconnectToContact: peer $publicKey not found after retries")
                    return@launch
                }
                val state = endpointPeers[endpointId]?.connectionState
                if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
                    Timber.d("reconnectToContact: already $state")
                    return@launch
                }
                Timber.d("reconnectToContact: requesting connection to ${publicKey.take(8)}...")
                requestConnection(endpointId)
            } finally {
                reconnecting.remove(publicKey)
            }
        }
    }

    private suspend fun findOrAwaitEndpoint(publicKey: String): String? {
        android.util.Log.e("NcapConn", "findOrAwait: search len=${publicKey.length} key=${publicKey.take(12)}...")
        val existing = endpointPeers.entries.map { "${it.key.take(6)}→len${it.value.publicKey.length} ${it.value.publicKey.take(12)}..." }
        android.util.Log.e("NcapConn", "findOrAwait: peers(${endpointPeers.size}): $existing")
        val found = endpointPeers.entries.find { it.value.publicKey == publicKey }?.key
        if (found != null) {
            android.util.Log.e("NcapConn", "findOrAwait: found → $found")
            return found
        }
        val isEmpty = endpointPeers.isEmpty()
        android.util.Log.e("NcapConn", "findOrAwait: NOT found — isEmpty=$isEmpty nearby=${_nearbyState.value} adv=$isAdvertising disc=$isDiscovering")
        if (isEmpty) {
            val name = identityDao.getIdentity()?.publicKey ?: return null
            if (isAdvertising) { try { connectionsClient.stopAdvertising() } catch (_: Exception) {}; isAdvertising = false }
            if (isDiscovering) { try { connectionsClient.stopDiscovery() } catch (_: Exception) {}; isDiscovering = false }
            _nearbyState.value = NearbyState.Idle
            kotlinx.coroutines.delay(1000L)
            startAdvertising(name)
            startDiscovery()
            android.util.Log.e("NcapConn", "findOrAwait: restarted — advertising=$isAdvertising discovering=$isDiscovering")
        }
        for (attempt in 1..30) {
            kotlinx.coroutines.delay(2000L)
            val found2 = endpointPeers.entries.find { it.value.publicKey == publicKey }?.key
            if (found2 != null) {
                android.util.Log.e("NcapConn", "findOrAwait: found on attempt $attempt → $found2")
                return found2
            }
            if (attempt % 5 == 0 && endpointPeers.isEmpty()) {
                android.util.Log.e("NcapConn", "findOrAwait: attempt $attempt — still empty, re-restarting")
                val name = identityDao.getIdentity()?.publicKey ?: return null
                if (isAdvertising) { try { connectionsClient.stopAdvertising() } catch (_: Exception) {}; isAdvertising = false }
                if (isDiscovering) { try { connectionsClient.stopDiscovery() } catch (_: Exception) {}; isDiscovering = false }
                _nearbyState.value = NearbyState.Idle
                kotlinx.coroutines.delay(1000L)
                startAdvertising(name)
                startDiscovery()
            }
            val snapshot = endpointPeers.entries.map { "${it.key.take(6)}→len${it.value.publicKey.length} ${it.value.publicKey.take(12)}..." }
            android.util.Log.e("NcapConn", "findOrAwait: attempt $attempt — peers(${endpointPeers.size}): $snapshot")
        }
        return null
    }

    override suspend fun sendPayload(endpointId: String, payload: ByteArray) {
        try {
            val bytesPayload = Payload.fromBytes(payload)
            val deferred = CompletableDeferred<Unit>()

            connectionsClient.sendPayload(endpointId, bytesPayload)
                .addOnSuccessListener { deferred.complete(Unit) }
                .addOnFailureListener { deferred.completeExceptionally(it) }

            kotlinx.coroutines.withTimeout(30_000L) {
                deferred.await()
            }
        } catch (e: Exception) {
            Timber.w(e, "sendPayload: failed to $endpointId — ${e.message}")
            throw e
        }
    }

    override suspend fun sendReadReceipt(contactPublicKey: String, messageUuid: String) {
        if (messageUuid.isEmpty()) return
        val myPublicKey = identityDao.getIdentity()?.publicKey ?: return
        val endpointId = getConnectedEndpointIds(contactPublicKey).firstOrNull() ?: return
        val envelope = NcapEnvelope.Plain(
            senderPublicKey = myPublicKey,
            payloadType = NcapEnvelope.PayloadType.READ_RECEIPT,
            payload = ByteArray(0),
            messageUuid = messageUuid
        )
        scope.launch { runCatching { sendPayload(endpointId, envelope.toBytes()) } }
    }

    override suspend fun sendFile(
        endpointId: String,
        fileUri: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        durationLabel: String?
    ) {
        if (callActiveCount.get() > 0) {
            Timber.d("sendFile: call active (count=${callActiveCount.get()}), rejecting file send")
            throw IllegalStateException("Cannot send files during an active call")
        }
        val MAX_FILE_SIZE = 100L * 1024 * 1024
        if (fileSize > MAX_FILE_SIZE) {
            throw IllegalArgumentException("File exceeds 100MB limit")
        }

        val identity = identityDao.getIdentity()
        val myPublicKey = identity?.publicKey ?: throw IllegalStateException("No identity")

        val file = java.io.File(fileUri.removePrefix("file://"))
        if (!file.exists()) throw java.io.FileNotFoundException(fileUri)
        // Create the payload first so its id can correlate the metadata on the receiver.
        val filePayload = Payload.fromFile(file)

        val metaJson = JSONObject().apply {
            put("payloadId", filePayload.id)
            put("fileName", fileName)
            put("fileSize", fileSize)
            put("mimeType", mimeType)
            put("senderPublicKey", myPublicKey)
            durationLabel?.let { put("duration", it) }
        }

        val metaEnvelope = NcapEnvelope.Plain(
            senderPublicKey = myPublicKey,
            payloadType = NcapEnvelope.PayloadType.FILE_TRANSFER_REQUEST,
            payload = metaJson.toString().toByteArray(Charsets.UTF_8)
        )
        sendPayload(endpointId, metaEnvelope.toBytes())

        kotlinx.coroutines.delay(500L)

        // Suspend until the transfer completes (SUCCESS) or fails (FAILURE/CANCELED),
        // reported via onPayloadTransferUpdate.
        val transferDone = CompletableDeferred<Unit>()
        outgoingFileTransfers[filePayload.id] = transferDone
        try {
            connectionsClient.sendPayload(endpointId, filePayload)
                .addOnFailureListener { e -> transferDone.completeExceptionally(e) }
            transferDone.await()
        } finally {
            outgoingFileTransfers.remove(filePayload.id)
        }
    }

    override suspend fun sendCallSignal(
        endpointId: String,
        payloadType: NcapEnvelope.PayloadType,
        payload: ByteArray
    ) {
        val identity = identityDao.getIdentity()
        val myPublicKey = identity?.publicKey ?: return

        val envelope = NcapEnvelope.Plain(
            senderPublicKey = myPublicKey,
            payloadType = payloadType,
            payload = payload
        )
        try {
            sendPayload(endpointId, envelope.toBytes())
        } catch (e: Exception) {
            Timber.w(e, "sendCallSignal: $payloadType failed to $endpointId")
        }
    }

    private fun createPayloadCallback(publicKey: String): PayloadCallback {
        return object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
                if (payload.type == Payload.Type.FILE) {
                    android.util.Log.e("NcapFile", "onPayloadReceived FILE payloadId=${payload.id} — stashing")
                    incomingFilePayloads[payload.id] = payload
                    incomingFilePayloadReceivedAt[payload.id] = System.currentTimeMillis()
                    return
                }
                scope.launch {
                    handleIncomingPayload(endpointId, publicKey, payload)
                }
            }

            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                android.util.Log.e("NcapFile", "onPayloadTransferUpdate status=${update.status} payloadId=${update.payloadId}")
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        outgoingFileTransfers.remove(update.payloadId)?.complete(Unit)
                        val stashedPayload = incomingFilePayloads[update.payloadId]
                        if (stashedPayload != null) {
                            val meta = pendingFileMetas[update.payloadId]
                            if (meta != null) {
                                android.util.Log.e("NcapFile", "onPayloadTransferUpdate meta ready — processing file immediately")
                                incomingFilePayloads.remove(update.payloadId)
                                incomingFilePayloadReceivedAt.remove(update.payloadId)
                                scope.launch {
                                    handleIncomingFile(endpointId, publicKey, stashedPayload)
                                }
                            } else {
                                android.util.Log.e("NcapFile", "onPayloadTransferUpdate meta NOT ready — deferring until meta arrives")
                            }
                        }
                    }
                    PayloadTransferUpdate.Status.FAILURE,
                    PayloadTransferUpdate.Status.CANCELED -> {
                        outgoingFileTransfers.remove(update.payloadId)
                            ?.completeExceptionally(java.io.IOException("File transfer failed (status=${update.status})"))
                        if (incomingFilePayloads.remove(update.payloadId) != null) {
                            incomingFilePayloadReceivedAt.remove(update.payloadId)
                            handleFailedIncomingFile(publicKey, update.payloadId)
                        } else {
                            pendingFileMetas.remove(update.payloadId)
                        }
                    }
                    else -> { /* IN_PROGRESS */ }
                }
            }
        }
    }

    private fun handleFailedIncomingFile(senderPublicKey: String, payloadId: Long) {
        val meta = pendingFileMetas.remove(payloadId)
        scope.launch {
            val entity = com.offnetic.data.local.db.entity.Message(
                sessionId = senderPublicKey,
                chatId = senderPublicKey,
                senderPublicKey = senderPublicKey,
                content = "File transfer failed" + (meta?.fileName?.let { ": $it" } ?: ""),
                type = com.offnetic.data.local.db.entity.Message.TYPE_FILE,
                timestamp = System.currentTimeMillis(),
                deliveryState = com.offnetic.domain.model.MessageDeliveryState.SAVED,
                isRead = false
            )
            messageDao.insert(entity)
            _incomingMessages.emit(com.offnetic.domain.model.Message.fromEntity(entity))
            messageNotificationManager.notifyIfNeeded(senderPublicKey)
            Timber.w("Incoming file transfer failed from ${senderPublicKey.take(8)}... (payloadId=$payloadId)")
        }
    }

    private suspend fun handleIncomingPayload(
        endpointId: String,
        senderPublicKey: String,
        payload: Payload
    ) {
        android.util.Log.e("NcapFile", "handleIncomingPayload type=${payload.type} from ${senderPublicKey.take(8)}")
        if (payload.type != Payload.Type.BYTES) return

        val bytes = payload.asBytes() ?: return

        val envelope = NcapEnvelope.parse(bytes) ?: run {
            android.util.Log.e("NcapFile", "envelope parse FAILED for ${senderPublicKey.take(8)} — checking raw JSON")
            try {
                val json = org.json.JSONObject(String(bytes, Charsets.UTF_8))
                if (json.optString("type", "") == "wifi_p2p") {
                    android.util.Log.e("NcapFile", "wifi_p2p payload received — deprecated, ignoring")
                } else {
                    android.util.Log.e("NcapFile", "raw JSON not wifi_p2p either — DROPPING payload")
                }
            } catch (_: Exception) {
                android.util.Log.e("NcapFile", "raw JSON parse also FAILED — DROPPING payload")
            }
            return
        }
        Timber.d("handleIncomingPayload: fmt=${envelope::class.simpleName} from ${senderPublicKey.take(8)}...")

        when (envelope) {
            is NcapEnvelope.Plain -> {
                when (envelope.payloadType) {
                    NcapEnvelope.PayloadType.PRE_KEY_BUNDLE -> {
                        Timber.d("handleIncoming: PRE_KEY_BUNDLE from ${senderPublicKey.take(8)}...")
                        android.util.Log.e("NcapConn", "RECEIVED PRE_KEY_BUNDLE ← ${senderPublicKey.take(12)}...")
                        signalProtocolManager.processBundleAndCreateSession(senderPublicKey, envelope.payload)
                        Timber.d("handleIncoming: session created for ${senderPublicKey.take(8)}...")
                        android.util.Log.e("NcapConn", "Session ESTABLISHED with ${senderPublicKey.take(12)}...")
                    }
                    NcapEnvelope.PayloadType.SIGNAL_PRE_KEY_BUNDLE -> {
                        Timber.d("Processing SIGNAL_PRE_KEY_BUNDLE from ${senderPublicKey.take(8)}...")
                        signalProtocolManager.deleteSession(senderPublicKey)
                        signalProtocolManager.processBundleAndCreateSession(senderPublicKey, envelope.payload)
                        decryptFailureCounts.remove(senderPublicKey)
                    }
                    NcapEnvelope.PayloadType.SIGNAL_SESSION_TERMINATED -> {
                        Timber.d("Processing SIGNAL_SESSION_TERMINATED from ${senderPublicKey.take(8)}...")
                        signalProtocolManager.deleteSession(senderPublicKey)
                    }
                    NcapEnvelope.PayloadType.SIGNAL_MESSAGE -> {
                        Timber.d("handleIncoming: SIGNAL_MESSAGE from ${senderPublicKey.take(8)}... (${envelope.payload.size}B)")
                        handleSignalMessage(endpointId, senderPublicKey, envelope.payload, envelope.messageUuid)
                    }
                    NcapEnvelope.PayloadType.DELIVERY_ACK -> {
                        if (envelope.messageUuid.isNotEmpty()) messageDao.markDelivered(envelope.messageUuid)
                        Timber.d("Nearby delivery ack uuid=${envelope.messageUuid.take(8)} -> DELIVERED")
                    }
                    NcapEnvelope.PayloadType.READ_RECEIPT -> {
                        if (envelope.messageUuid.isNotEmpty()) messageDao.markRead(envelope.messageUuid)
                        Timber.d("Nearby read receipt uuid=${envelope.messageUuid.take(8)} -> READ")
                    }
                    NcapEnvelope.PayloadType.HEARTBEAT -> {
                        Timber.d("Heartbeat from ${senderPublicKey.take(8)}...")
                    }
                    NcapEnvelope.PayloadType.QR_PAIRING_REQUEST -> {
                        Timber.d("QR pairing request from ${senderPublicKey.take(8)}...")
                    }
                    NcapEnvelope.PayloadType.INITIAL_IDENTITY -> {
                        Timber.d("INITIAL_IDENTITY from ${senderPublicKey.take(8)}...")
                        try {
                            val identityJson = org.json.JSONObject(String(envelope.payload, Charsets.UTF_8))
                            val pk = identityJson.getString("publicKey")
                            val displayName = identityJson.getString("displayName")
                            if (pk != senderPublicKey) {
                                Timber.w("INITIAL_IDENTITY publicKey mismatch from ${senderPublicKey.take(8)}..., dropped")
                                return
                            }
                            val existingContact = contactDao.getByPublicKey(pk)
                            if (existingContact != null) {
                                Timber.d("Contact $displayName already known, updating peers")
                                endpointPeers.entries
                                    .filter { it.value.publicKey == pk && !it.value.isContact }
                                    .forEach { (eid, peer) ->
                                        endpointPeers[eid] = peer.copy(
                                            displayName = displayName,
                                            isContact = true
                                        )
                                    }
                                emitPeers()
                                return
                            }
                            profileDao.insert(Profile(pk, displayName))
                            val newContact = com.offnetic.data.local.db.entity.Contact(
                                publicKey = pk,
                                displayName = displayName,
                                isVerified = false,
                                addedAt = System.currentTimeMillis(),
                                lastSeenAt = System.currentTimeMillis()
                            )
                            contactDao.insertIfNotExists(newContact)
                            Timber.d("Created contact $displayName from INITIAL_IDENTITY")
                            val systemMsg = com.offnetic.data.local.db.entity.Message(
                                sessionId = pk,
                                chatId = pk,
                                senderPublicKey = pk,
                                content = "Chat established via QR pairing",
                                type = com.offnetic.data.local.db.entity.Message.TYPE_SYSTEM,
                                timestamp = System.currentTimeMillis(),
                                deliveryState = com.offnetic.domain.model.MessageDeliveryState.SENT_LOCAL,
                                isRead = true
                            )
                            messageDao.insert(systemMsg)
                            endpointPeers.entries
                                .filter { it.value.publicKey == pk }
                                .forEach { (eid, peer) ->
                                    endpointPeers[eid] = peer.copy(
                                        displayName = displayName,
                                        isContact = true
                                    )
                                }
                            emitPeers()
                            scope.launch { reconnectToContact(pk) }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to process INITIAL_IDENTITY")
                        }
                    }
                    NcapEnvelope.PayloadType.FILE_TRANSFER_REQUEST -> {
                        Timber.d("File transfer request from ${senderPublicKey.take(8)}...")
                        try {
                            val metaJson = org.json.JSONObject(String(envelope.payload, Charsets.UTF_8))
                            val payloadId = metaJson.getLong("payloadId")
                            pendingFileMetas[payloadId] = FileMeta(
                                payloadId = payloadId,
                                fileName = metaJson.getString("fileName"),
                                fileSize = metaJson.getLong("fileSize"),
                                mimeType = metaJson.getString("mimeType"),
                                durationLabel = metaJson.optString("duration").takeIf { it.isNotEmpty() }
                            )
                            val waitingPayload = incomingFilePayloads[payloadId]
                            if (waitingPayload != null) {
                                android.util.Log.e("NcapFile", "FILE_TRANSFER_REQUEST meta arrived — file was waiting, processing now")
                                incomingFilePayloads.remove(payloadId)
                                incomingFilePayloadReceivedAt.remove(payloadId)
                                scope.launch {
                                    handleIncomingFile(endpointId, senderPublicKey, waitingPayload)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse file transfer meta")
                        }
                    }
                    NcapEnvelope.PayloadType.FILE_TRANSFER_ACCEPT -> {
                        Timber.d("File transfer accept from ${senderPublicKey.take(8)}...")
                    }
                    NcapEnvelope.PayloadType.FILE_TRANSFER_REJECT -> {
                        Timber.d("File transfer reject from ${senderPublicKey.take(8)}...")
                    }
                    NcapEnvelope.PayloadType.FILE_TRANSFER_COMPLETE -> {
                        Timber.d("File transfer complete from ${senderPublicKey.take(8)}...")
                    }
                    NcapEnvelope.PayloadType.CALL_OFFER,
                    NcapEnvelope.PayloadType.CALL_ANSWER,
                    NcapEnvelope.PayloadType.ICE_CANDIDATE,
                    NcapEnvelope.PayloadType.CALL_HANGUP -> {
                        android.util.Log.e("offCall", "NcapManager received ${envelope.payloadType} from ${senderPublicKey.take(8)} endpoint=$endpointId")
                        if (envelope.payloadType == NcapEnvelope.PayloadType.CALL_OFFER) {
                            val sdpJson = String(envelope.payload, Charsets.UTF_8)
                            WebRtcManager.pendingIncomingOffers[senderPublicKey] = Pair(sdpJson, endpointId)
                            android.util.Log.e("offCall", "CALL_OFFER stored in static cache for ${senderPublicKey.take(8)}")
                            if (callActiveCount.get() == 0) {
                                val callIntent = android.content.Intent(context, IncomingCallService::class.java).apply {
                                    action = IncomingCallService.ACTION_START_RINGING
                                    putExtra(IncomingCallService.EXTRA_PEER_PUBLIC_KEY, senderPublicKey)
                                }
                                context.startForegroundService(callIntent)
                            }
                            _incomingCallEvents.emit(senderPublicKey)
                        }
                        _incomingCallSignals.emit(
                            CallSignal(senderPublicKey, envelope.payloadType, envelope.payload, endpointId)
                        )
                    }
                }
            }
            is NcapEnvelope.SealedSender -> {
                Timber.d("Sealed sender message from ${senderPublicKey.take(8)}..., attempting decrypt")
                handleSignalMessage(endpointId, senderPublicKey, envelope.ciphertext, "")
            }
        }
    }

    private suspend fun trySendOnAnyEndpoint(publicKey: String, payload: ByteArray): Boolean {
        val endpointIds = endpointPeers.filterValues {
            it.publicKey == publicKey && it.connectionState == com.offnetic.domain.model.ConnectionState.CONNECTED
        }.keys.toList()
        if (endpointIds.isEmpty()) return false
        for (eid in endpointIds) {
            try {
                sendPayload(eid, payload)
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    override fun getConnectedEndpointIds(publicKey: String): List<String> {
        return endpointPeers.filterValues {
            it.publicKey == publicKey && it.connectionState == com.offnetic.domain.model.ConnectionState.CONNECTED
        }.keys.toList()
    }

    private suspend fun requestPreKeyBundle(publicKey: String) {
        val myIdentity = identityDao.getIdentity() ?: return
        try {
            val bundleBytes = signalProtocolManager.buildPreKeyBundleBytes()
            val envelope = NcapEnvelope.Plain(
                senderPublicKey = myIdentity.publicKey,
                payloadType = NcapEnvelope.PayloadType.PRE_KEY_BUNDLE,
                payload = bundleBytes
            )
            val sent = trySendOnAnyEndpoint(publicKey, envelope.toBytes())
            if (!sent) {
                preKeyBundleRetries[publicKey] = envelope.toBytes()
                Timber.d("PRE_KEY_BUNDLE buffered for ${publicKey.take(8)}...")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to build PRE_KEY_BUNDLE for ${publicKey.take(8)}...")
        }
    }

    private suspend fun handleSignalMessage(endpointId: String, senderPublicKey: String, ciphertext: ByteArray, messageUuid: String) {
        android.util.Log.e("NcapFile", "handleSignalMessage decrypting ${ciphertext.size}B from ${senderPublicKey.take(8)}")
        val identity = identityDao.getIdentity()
        val myPublicKey = identity?.publicKey ?: run {
            android.util.Log.e("NcapFile", "No local identity, cannot decrypt")
            return
        }

        val decrypted = signalProtocolManager.decryptMessage(senderPublicKey, ciphertext)
        if (decrypted == null) {
            android.util.Log.e("NcapFile", "DECRYPTION FAILED for ${senderPublicKey.take(8)}")
            val hasSession = signalProtocolManager.hasSession(senderPublicKey)
            if (!hasSession) {
                Timber.d("No session exists for ${senderPublicKey.take(8)}..., requesting PRE_KEY_BUNDLE")
                requestPreKeyBundle(senderPublicKey)
                return
            }
            val failures = decryptFailureCounts.getOrPut(senderPublicKey) { AtomicInteger(0) }
            val count = failures.incrementAndGet()
            Timber.d("Decrypt failure $count/$MAX_DECRYPT_FAILURES for ${senderPublicKey.take(8)}...")

            if (count >= MAX_DECRYPT_FAILURES) {
                Timber.d("Session shattered for ${senderPublicKey.take(8)}..., resetting")
                val newBundleBytes = signalProtocolManager.handleShatteredSession(senderPublicKey)
                decryptFailureCounts.remove(senderPublicKey)

                val resetEnvelope = NcapEnvelope.Plain(
                    senderPublicKey = myPublicKey,
                    payloadType = NcapEnvelope.PayloadType.SIGNAL_PRE_KEY_BUNDLE,
                    payload = newBundleBytes
                )
                val sent = trySendOnAnyEndpoint(senderPublicKey, resetEnvelope.toBytes())
                if (!sent) {
                    shatteredSessionBundles[senderPublicKey] = resetEnvelope.toBytes()
                    Timber.d("Shattered session reset buffered for ${senderPublicKey.take(8)}...")
                }
            }
            return
        }

        decryptFailureCounts.remove(senderPublicKey)

        android.util.Log.e("NcapFile", "Decryption OK, parsing JSON: ${String(decrypted, Charsets.UTF_8).take(60)}")

        val decryptedStr = String(decrypted, Charsets.UTF_8)
        val decryptedJson = try {
            org.json.JSONObject(decryptedStr)
        } catch (_: Exception) {
            Timber.w("handleSignalMessage: decrypted payload is not JSON, ignoring")
            return
        }

        val msgType = decryptedJson.optString("type", "text")
        val timestamp = decryptedJson.optLong("timestamp", System.currentTimeMillis())
        val chatId = senderPublicKey
        val resolvedUuid = messageUuid.ifEmpty { java.util.UUID.randomUUID().toString() }

        val entity = when (msgType) {
            "file" -> {
                val fileName = decryptedJson.optString("fileName", "file")
                val fileBase64 = decryptedJson.getString("content")
                val fileBytes = android.util.Base64.decode(fileBase64, android.util.Base64.NO_WRAP)
                val savedFile = java.io.File(context.filesDir, "received_${System.currentTimeMillis()}_$fileName")
                savedFile.writeBytes(fileBytes)
                com.offnetic.data.local.db.entity.Message(
                    sessionId = senderPublicKey,
                    chatId = chatId,
                    senderPublicKey = senderPublicKey,
                    content = "File: $fileName",
                    type = com.offnetic.data.local.db.entity.Message.TYPE_FILE,
                    timestamp = timestamp,
                    deliveryState = com.offnetic.domain.model.MessageDeliveryState.SAVED,
                    isRead = false,
                    attachmentPath = savedFile.absolutePath
                )
            }
            "voice_note" -> {
                val duration = decryptedJson.optString("duration", "0:00")
                val audioBase64 = decryptedJson.getString("content")
                val audioBytes = android.util.Base64.decode(audioBase64, android.util.Base64.NO_WRAP)
                val savedFile = java.io.File(context.filesDir, "received_voice_${System.currentTimeMillis()}.m4a")
                savedFile.writeBytes(audioBytes)
                com.offnetic.data.local.db.entity.Message(
                    sessionId = senderPublicKey,
                    chatId = chatId,
                    senderPublicKey = senderPublicKey,
                    content = "Voice note  $duration",
                    type = com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE,
                    timestamp = timestamp,
                    deliveryState = com.offnetic.domain.model.MessageDeliveryState.SAVED,
                    isRead = false,
                    attachmentPath = savedFile.absolutePath
                )
            }
            else -> {
                val content = decryptedJson.getString("content")
                com.offnetic.data.local.db.entity.Message(
                    sessionId = senderPublicKey,
                    chatId = chatId,
                    senderPublicKey = senderPublicKey,
                    content = content,
                    type = com.offnetic.data.local.db.entity.Message.TYPE_TEXT,
                    timestamp = timestamp,
                    deliveryState = com.offnetic.domain.model.MessageDeliveryState.SAVED,
                    isRead = false
                )
            }
        }.copy(messageUuid = resolvedUuid)
        messageDao.insert(entity)
        android.util.Log.e("NcapFile", "MESSAGE INSERTED: type=$msgType chatId=${chatId.take(8)} content=${entity.content.take(30)}")

        val existingContact = contactDao.getByPublicKey(chatId)
        if (existingContact == null) {
            val contact = com.offnetic.data.local.db.entity.Contact(
                publicKey = chatId,
                displayName = chatId.take(12) + "...",
                isVerified = false,
                addedAt = System.currentTimeMillis(),
                lastSeenAt = System.currentTimeMillis()
            )
            contactDao.insertIfNotExists(contact)
            Timber.d("Auto-created contact for ${chatId.take(8)}...")
        } else {
            contactDao.updateLastSeen(chatId, System.currentTimeMillis())
        }

        val message = com.offnetic.domain.model.Message.fromEntity(entity)
        _incomingMessages.emit(message)
        messageNotificationManager.notifyIfNeeded(senderPublicKey)
        Timber.d("Message decrypted for ${senderPublicKey.take(8)}...")
        if (messageUuid.isNotEmpty()) {
            val ackEnvelope = NcapEnvelope.Plain(
                senderPublicKey = myPublicKey,
                payloadType = NcapEnvelope.PayloadType.DELIVERY_ACK,
                payload = ByteArray(0),
                messageUuid = messageUuid
            )
            scope.launch { runCatching { sendPayload(endpointId, ackEnvelope.toBytes()) } }
        }
    }

    private suspend fun handleIncomingFile(endpointId: String, senderPublicKey: String, payload: Payload) {
        if (!processedFilePayloads.add(payload.id)) {
            android.util.Log.e("NcapFile", "handleIncomingFile: payloadId=${payload.id} ALREADY PROCESSED — skipping duplicate")
            return
        }
        if (callActiveCount.get() > 0) {
            android.util.Log.e("NcapFile", "handleIncomingFile: call active (count=${callActiveCount.get()}), deferring payloadId=${payload.id}")
            deferredFilePayloads[payload.id] = Pair(endpointId, payload)
            processedFilePayloads.remove(payload.id)
            return
        }
        handleIncomingFileInternal(senderPublicKey, payload)
    }

    private suspend fun handleIncomingFileInternal(senderPublicKey: String, payload: Payload) {
        val filePayload = payload.asFile() ?: run {
            android.util.Log.e("NcapFile", "handleIncomingFile: payload.asFile() is null for ${senderPublicKey.take(8)}")
            return
        }
        val meta = pendingFileMetas.remove(payload.id)
        android.util.Log.e("NcapFile", "handleIncomingFile: payloadId=${payload.id} meta=${meta != null} fileName=${meta?.fileName} mimeType=${meta?.mimeType} for ${senderPublicKey.take(8)}")
        val fileName = meta?.fileName ?: "file"
        val mimeType = meta?.mimeType ?: ""
        val timestamp = System.currentTimeMillis()

        val savedPath: String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                // First copy NCAPI's data into a temp internal file we can read back.
                val tmpFile = java.io.File(context.filesDir, "tmp_${timestamp}_$fileName")
                val metaFileSize = meta?.fileSize ?: 0L
                var copiedOk = false
                val ncapiUri = filePayload.asUri()
                if (ncapiUri != null) {
                    context.contentResolver.openInputStream(ncapiUri)?.use { input ->
                        tmpFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    copiedOk = tmpFile.length() > 0 && (metaFileSize == 0L || tmpFile.length() >= metaFileSize * 0.95)
                }
                if (!copiedOk) {
                    @Suppress("DEPRECATION")
                    val javaFile = filePayload.asJavaFile()
                    if (javaFile != null && javaFile.exists() && javaFile.length() > 0) {
                        if (!javaFile.renameTo(tmpFile)) {
                            javaFile.copyTo(tmpFile, overwrite = true)
                        }
                        copiedOk = tmpFile.length() > 0
                    }
                }
                if (!copiedOk || tmpFile.length() == 0L) {
                    android.util.Log.e("NcapFile", "handleIncomingFile: cannot read file data for $fileName")
                    tmpFile.delete()
                    return@withContext null
                }
                android.util.Log.e("NcapFile", "handleIncomingFile: temp copy ok size=${tmpFile.length()}")

                // Save to public storage so files appear in Gallery / file manager.
                val publicPath = saveToPublicStorage(fileName, mimeType, tmpFile)
                if (publicPath != null) {
                    tmpFile.delete()
                    return@withContext publicPath
                }

                // Fallback: keep in internal received_files/ (API 28 or MediaStore failure).
                val destDir = java.io.File(context.filesDir, "received_files")
                destDir.mkdirs()
                val destFile = java.io.File(destDir, "${timestamp}_$fileName")
                if (!tmpFile.renameTo(destFile)) {
                    tmpFile.copyTo(destFile, overwrite = true)
                }
                tmpFile.delete()
                android.util.Log.e("NcapFile", "handleIncomingFile: fallback to internal ${destFile.absolutePath} (size=${destFile.length()})")
                destFile.absolutePath
            } catch (e: Exception) {
                android.util.Log.e("NcapFile", "handleIncomingFile: save failed: ${e.message}", e)
                null
            }
        }

        android.util.Log.e("NcapFile", "handleIncomingFile: savedPath=$savedPath mimeType=$mimeType fileName=$fileName")

        val isVoiceNote = mimeType.startsWith("audio/")
        val isMedia = mimeType.startsWith("image/") || mimeType.startsWith("video/")
        val displayType: Int
        val displayContent: String
        if (savedPath == null) {
            displayType = com.offnetic.data.local.db.entity.Message.TYPE_TEXT
            displayContent = "Transfer failed: $fileName"
        } else if (isVoiceNote) {
            displayType = com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE
            displayContent = "Voice note  ${meta?.durationLabel ?: ""}".trimEnd()
        } else {
            displayType = if (isMedia) {
                if (mimeType.startsWith("image/")) com.offnetic.data.local.db.entity.Message.TYPE_IMAGE
                else com.offnetic.data.local.db.entity.Message.TYPE_VIDEO
            } else com.offnetic.data.local.db.entity.Message.TYPE_FILE
            displayContent = "File: $fileName"
        }
        val entity = com.offnetic.data.local.db.entity.Message(
            sessionId = senderPublicKey,
            chatId = senderPublicKey,
            senderPublicKey = senderPublicKey,
            content = displayContent,
            type = displayType,
            timestamp = timestamp,
            deliveryState = com.offnetic.domain.model.MessageDeliveryState.SAVED,
            isRead = false,
            attachmentPath = savedPath,
            attachmentType = if (mimeType.startsWith("image/")) com.offnetic.data.local.db.entity.Message.TYPE_IMAGE
                else if (mimeType.startsWith("video/")) com.offnetic.data.local.db.entity.Message.TYPE_VIDEO
                else com.offnetic.data.local.db.entity.Message.TYPE_FILE
        )
        messageDao.insert(entity)

        val message = com.offnetic.domain.model.Message.fromEntity(entity)
        _incomingMessages.emit(message)
        messageNotificationManager.notifyIfNeeded(senderPublicKey)
        android.util.Log.e("NcapFile", "handleIncomingFile: done — content=${entity.content} type=${entity.type} attachmentPath=${entity.attachmentPath}")
    }

    private fun saveToPublicStorage(fileName: String, mimeType: String, sourceFile: java.io.File): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        if (mimeType.startsWith("audio/")) return null // voice notes stay internal
        return try {
            val (collection, relativeDir) = when {
                mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "Pictures/Offnetic"
                mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "Movies/Offnetic"
                mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "Music/Offnetic"
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to "Download/Offnetic"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            } ?: run { context.contentResolver.delete(uri, null, null); return null }

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            android.util.Log.e("NcapFile", "saveToPublicStorage: $fileName → $uri")
            uri.toString()
        } catch (e: Exception) {
            android.util.Log.e("NcapFile", "saveToPublicStorage: failed for $fileName: ${e.message}")
            null
        }
    }

    private suspend fun handleEndpointFound(endpointId: String, publicKey: String) {
        android.util.Log.e("NcapConn", "handleEndpointFound: eid=${endpointId.take(6)} key=${publicKey.take(12)}...")
        if (!endpointPeers.containsKey(endpointId)) {
            endpointPeers[endpointId] = PeerInfo(
                endpointId = endpointId,
                publicKey = publicKey,
                displayName = publicKey.take(12),
                isContact = false,
                connectionState = ConnectionState.DISCONNECTED,
                lastSeenAt = System.currentTimeMillis(),
                lastPingedAt = 0L
            )
            emitPeers()
        }

        val contact = contactDao.getByPublicKey(publicKey)
        if (contact == null) {
            Timber.d("Non-contact peer stored for later: ${publicKey.take(8)}...")
            return
        }

        val now = System.currentTimeMillis()
        val displayName = contact.displayName
        val lastSeenAt = contact.lastSeenAt
        val lastPingedAt = contact.lastPingedAt
        val timeSinceLastSeen = now - lastSeenAt

        val pingsEnabled = prefs.proximityPingsEnabled.firstOrNull() ?: true
        val thresholdMinutes = prefs.proximityPingThresholdMinutes.firstOrNull() ?: 30
        val thresholdMs = thresholdMinutes * 60 * 1000L

        if (!pingsEnabled) {
            Timber.d("Proximity pings disabled, skipping")
        } else when {
            timeSinceLastSeen < PROXIMITY_SILENT_MS -> {
                Timber.d("Proximity: silent (${timeSinceLastSeen / 1000}s since last seen)")
            }
            timeSinceLastSeen >= thresholdMs -> {
                val timeSinceLastPing = now - lastPingedAt
                if (timeSinceLastPing >= PING_COOLDOWN_MS) {
                    proximityPingNotifier.firePing(displayName)
                    contactDao.updateLastPinged(publicKey, now)
                    Timber.d("Proximity ping fired: $displayName")
                } else {
                    Timber.d("Proximity ping suppressed (cooldown): $displayName")
                }
            }
        }

        val peer = PeerInfo(
            endpointId = endpointId,
            publicKey = publicKey,
            displayName = displayName,
            isContact = true,
            connectionState = ConnectionState.DISCONNECTED,
            lastSeenAt = now,
            lastPingedAt = lastPingedAt
        )

        endpointPeers[endpointId] = peer
        emitPeers()

        val myIdentity = identityDao.getIdentity()
        if (myIdentity != null) {
            val myKey = myIdentity.publicKey
            when {
                myKey < publicKey -> {
                    Timber.d("handleEndpointFound: initiating connection to ${publicKey.take(8)}... (lower key)")
                    android.util.Log.e("NcapConn", "FOUND contact ${publicKey.take(12)}... → requesting connection")
                    requestConnection(endpointId)
                }
                myKey == publicKey -> {
                    Timber.w("handleEndpointFound: detected self, ignoring")
                }
                else -> {
                    Timber.d("handleEndpointFound: waiting for ${publicKey.take(8)}... (lower key) to initiate")
                    scope.launch {
                        kotlinx.coroutines.delay(10_000L)
                        val currentState = endpointPeers[endpointId]?.connectionState
                        if (currentState == ConnectionState.DISCONNECTED) {
                            Timber.d("handleEndpointFound: retry timeout — taking over for ${publicKey.take(8)}...")
                            android.util.Log.e("NcapConn", "RETRY timeout → requesting connection to ${publicKey.take(12)}...")
                            requestConnection(endpointId)
                        } else {
                            Timber.d("handleEndpointFound: retry timeout — peer ${publicKey.take(8)}... state=$currentState, skipping")
                        }
                    }
                }
            }
        } else {
            Timber.w("handleEndpointFound: no local identity, cannot initiate")
        }
    }

    private suspend fun handleEndpointLost(endpointId: String) {
        val peer = endpointPeers[endpointId] ?: return
        if (peer.isContact) {
            contactDao.updateLastSeen(peer.publicKey, System.currentTimeMillis())
            Timber.d("Departure recorded: ${peer.displayName}")
        }
        endpointPeers.remove(endpointId)
        endpointPayloadCallbacks.remove(endpointId)
        endpointPublicKeys.remove(endpointId)
        emitPeers()
    }

    private fun startHeartbeat(endpointId: String) {
        heartbeatJobs[endpointId]?.cancel()
        heartbeatJobs[endpointId] = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(25_000L)
                try {
                    val heartbeat = NcapEnvelope.Plain(
                        senderPublicKey = "",
                        payloadType = NcapEnvelope.PayloadType.HEARTBEAT,
                        payload = ByteArray(0)
                    )
                    sendPayload(endpointId, heartbeat.toBytes())
                } catch (_: kotlinx.coroutines.CancellationException) {
                    return@launch
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun updatePeerState(endpointId: String, state: ConnectionState) {
        val peer = endpointPeers[endpointId]
        if (peer != null) {
            endpointPeers[endpointId] = peer.copy(connectionState = state)
        } else {
            Timber.d("Peer not yet discovered for $endpointId, creating placeholder")
        }
        emitPeers()
    }

    private fun emitPeers() {
        _peers.value = endpointPeers.values.toList()
            .sortedWith(compareByDescending<PeerInfo> { it.isContact }.thenByDescending { it.lastSeenAt })
    }
}