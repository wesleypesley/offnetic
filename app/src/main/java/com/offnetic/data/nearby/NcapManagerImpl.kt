package com.offnetic.data.nearby

import android.content.Context
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
import com.offnetic.data.local.db.dao.BlockedPeerDao
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
    private val blockedPeerDao: BlockedPeerDao,
    private val contactDao: ContactDao,
    private val profileDao: ProfileDao,
    private val prefs: PreferencesRepository,
    private val proximityPingNotifier: ProximityPingNotifier,
    private val signalProtocolManager: SignalProtocolManager,
    private val messageDao: MessageDao,
    @ApplicationContext private val context: Context
) : NcapManager {

    companion object {
        const val SERVICE_ID = "com.offnetic.nearby"
        private const val PROXIMITY_SILENT_MS = 5 * 60 * 1000L
        private const val PING_COOLDOWN_MS = 15 * 60 * 1000L
        private const val MAX_DECRYPT_FAILURES = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    override val peers: StateFlow<List<PeerInfo>> = _peers.asStateFlow()

    private val _nearbyState = MutableStateFlow<NearbyState>(NearbyState.Idle)
    override val nearbyState: StateFlow<NearbyState> = _nearbyState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<com.offnetic.domain.model.Message>(
        replay = 0, extraBufferCapacity = 64
    )
    override val incomingMessages: SharedFlow<com.offnetic.domain.model.Message> = _incomingMessages.asSharedFlow()

    private val _incomingCallSignals = MutableSharedFlow<CallSignal>(replay = 5, extraBufferCapacity = 16)
    override val incomingCallSignals: SharedFlow<CallSignal> = _incomingCallSignals.asSharedFlow()

    private val _incomingCallEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)
    override val incomingCallEvents: SharedFlow<String> = _incomingCallEvents.asSharedFlow()

    private val endpointPeers = ConcurrentHashMap<String, PeerInfo>()
    private var isAdvertising = false
    private var isDiscovering = false
    private var currentAdvertisingName: String = ""

    private val endpointPayloadCallbacks = ConcurrentHashMap<String, PayloadCallback>()
    private val endpointPublicKeys = ConcurrentHashMap<String, String>()
    private val decryptFailureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val shatteredSessionBundles = ConcurrentHashMap<String, ByteArray>()
    private val preKeyBundleRetries = ConcurrentHashMap<String, ByteArray>()
    private val identityRetries = ConcurrentHashMap<String, ByteArray>()
    private val heartbeatJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val pendingFileMetas = ConcurrentHashMap<Long, FileMeta>()
    private val incomingFilePayloads = ConcurrentHashMap<Long, Payload>()
    private val outgoingFileTransfers = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            val publicKey = connectionInfo.endpointName
            Timber.d("onConnectionInitiated: endpoint=${endpointId.take(8)}..., name=${publicKey.take(16)}...")
            android.util.Log.e("NcapConn", "onConnectionInitiated from ${publicKey.take(12)}...")
            updatePeerState(endpointId, ConnectionState.CONNECTING)

            scope.launch {
                val blocked = blockedPeerDao.isBlocked(publicKey)
                if (blocked) {
                    rejectConnection(endpointId)
                    return@launch
                }

                if (!endpointPeers.containsKey(endpointId)) {
                    val contact = contactDao.getByPublicKey(publicKey)
                    val displayName = contact?.displayName ?: publicKey.take(12)
                    endpointPeers[endpointId] = PeerInfo(
                        endpointId = endpointId,
                        publicKey = publicKey,
                        displayName = displayName,
                        isContact = contact != null,
                        isBlocked = false,
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
            endpointPublicKeys.remove(endpointId)
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
            Timber.e(e, "Advertising failed (status: ${(e as? com.google.android.gms.common.api.ApiException)?.statusCode ?: "unknown"})")
            _nearbyState.value = NearbyState.Error("Advertising failed: ${e.message}")
        }
    }

    override fun startDiscovery() {
        if (isDiscovering) return

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

            connectionsClient.startDiscovery(SERVICE_ID, discoveryCallback, options)
                .addOnSuccessListener {
                isDiscovering = true
                _nearbyState.value = if (isAdvertising) NearbyState.Active else NearbyState.Discovering
                Timber.d("Discovery started")
            }.addOnFailureListener { e ->
                Timber.e(e, "Discovery failed (status: ${(e as? com.google.android.gms.common.api.ApiException)?.statusCode ?: "unknown"})")
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
        connectionsClient.stopAllEndpoints()
        stopAdvertising()
        stopDiscovery()
        endpointPayloadCallbacks.clear()
        endpointPublicKeys.clear()
        decryptFailureCounts.clear()
        _nearbyState.value = NearbyState.Idle
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

        val callback = createPayloadCallback(endpointId, publicKey)
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

    override fun reconnectToContact(publicKey: String) {
        val endpointId = endpointPeers.entries
            .find { it.value.publicKey == publicKey }?.key ?: run {
                Timber.d("reconnectToContact: peer $publicKey not in endpoint list, ensuring discovery is active")
                startDiscovery()
                return
            }
        val state = endpointPeers[endpointId]?.connectionState
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            Timber.d("reconnectToContact: already $state")
            return
        }
        Timber.d("reconnectToContact: requesting connection to ${publicKey.take(8)}...")
        requestConnection(endpointId)
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

    override suspend fun sendFile(
        endpointId: String,
        fileUri: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        durationLabel: String?
    ) {
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

    private fun createPayloadCallback(endpointId: String, publicKey: String): PayloadCallback {
        return object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
                if (payload.type == Payload.Type.FILE) {
                    // FILE payloads arrive when the transfer STARTS; the file is only
                    // complete on PayloadTransferUpdate SUCCESS — stash until then.
                    incomingFilePayloads[payload.id] = payload
                    return
                }
                scope.launch {
                    handleIncomingPayload(endpointId, publicKey, payload)
                }
            }

            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        outgoingFileTransfers.remove(update.payloadId)?.complete(Unit)
                        incomingFilePayloads.remove(update.payloadId)?.let { payload ->
                            scope.launch {
                                handleIncomingFile(endpointId, publicKey, payload)
                            }
                        }
                    }
                    PayloadTransferUpdate.Status.FAILURE,
                    PayloadTransferUpdate.Status.CANCELED -> {
                        outgoingFileTransfers.remove(update.payloadId)
                            ?.completeExceptionally(java.io.IOException("File transfer failed (status=${update.status})"))
                        if (incomingFilePayloads.remove(update.payloadId) != null) {
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
                isSent = false,
                isRead = false
            )
            messageDao.insert(entity)
            _incomingMessages.emit(com.offnetic.domain.model.Message.fromEntity(entity))
            Timber.w("Incoming file transfer failed from ${senderPublicKey.take(8)}... (payloadId=$payloadId)")
        }
    }

    private suspend fun handleIncomingPayload(
        endpointId: String,
        senderPublicKey: String,
        payload: Payload
    ) {
        val blocked = blockedPeerDao.isBlocked(senderPublicKey)
        if (blocked) {
            Timber.d("Blocked peer payload dropped: ${senderPublicKey.take(8)}...")
            return
        }

        if (payload.type != Payload.Type.BYTES) return

        val bytes = payload.asBytes() ?: return

        val envelope = NcapEnvelope.parse(bytes) ?: run {
            Timber.d("Unknown/unparseable envelope from ${senderPublicKey.take(8)}...")
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
                        handleSignalMessage(senderPublicKey, envelope.payload)
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
                                // Unauthenticated identity claim — a peer may only describe itself
                                Timber.w("INITIAL_IDENTITY publicKey mismatch from ${senderPublicKey.take(8)}..., dropped")
                                return
                            }
                            profileDao.insert(Profile(pk, displayName))
                            val contact = contactDao.getByPublicKey(pk)
                            if (contact != null) {
                                contactDao.update(com.offnetic.data.local.db.entity.Contact(
                                    publicKey = pk,
                                    displayName = displayName,
                                    isVerified = contact.isVerified,
                                    addedAt = contact.addedAt,
                                    lastSeenAt = contact.lastSeenAt,
                                    lastPingedAt = contact.lastPingedAt
                                ))
                                Timber.d("Updated contact $displayName from INITIAL_IDENTITY")
                            } else {
                                val newContact = com.offnetic.data.local.db.entity.Contact(
                                    publicKey = pk,
                                    displayName = displayName,
                                    isVerified = false,
                                    addedAt = System.currentTimeMillis(),
                                    lastSeenAt = System.currentTimeMillis()
                                )
                                contactDao.insertIfNotExists(newContact)
                                Timber.d("Created contact $displayName from INITIAL_IDENTITY")
                            }
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
                            android.util.Log.e("offCall", "NcapManager emitting incomingCallEvents for ${senderPublicKey.take(8)}")
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
                handleSignalMessage(senderPublicKey, envelope.ciphertext)
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

    private suspend fun handleSignalMessage(senderPublicKey: String, ciphertext: ByteArray) {
        Timber.d("handleSignalMessage: decrypting ${ciphertext.size}B from ${senderPublicKey.take(8)}...")
        val identity = identityDao.getIdentity()
        val myPublicKey = identity?.publicKey ?: run {
            Timber.w("No local identity, cannot decrypt")
            return
        }

        val decrypted = signalProtocolManager.decryptMessage(senderPublicKey, ciphertext)
        if (decrypted == null) {
            Timber.w("handleSignalMessage: decryption FAILED for ${senderPublicKey.take(8)}...")
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
                    isSent = false,
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
                    isSent = false,
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
                    isSent = false,
                    isRead = false
                )
            }
        }
        messageDao.insert(entity)
        Timber.d("handleSignalMessage: inserted msg type=$msgType from ${senderPublicKey.take(8)}...")

        val existingContact = contactDao.getByPublicKey(senderPublicKey)
        if (existingContact == null) {
            val contact = com.offnetic.data.local.db.entity.Contact(
                publicKey = senderPublicKey,
                displayName = senderPublicKey.take(12) + "...",
                isVerified = false,
                addedAt = System.currentTimeMillis(),
                lastSeenAt = System.currentTimeMillis()
            )
            contactDao.insertIfNotExists(contact)
            Timber.d("Auto-created contact for ${senderPublicKey.take(8)}...")
        } else {
            contactDao.updateLastSeen(senderPublicKey, System.currentTimeMillis())
        }

        val message = com.offnetic.domain.model.Message.fromEntity(entity)
        _incomingMessages.emit(message)
        Timber.d("Message decrypted for ${senderPublicKey.take(8)}...")
    }

    private suspend fun handleIncomingFile(endpointId: String, senderPublicKey: String, payload: Payload) {
        if (blockedPeerDao.isBlocked(senderPublicKey)) {
            Timber.d("Blocked peer file dropped: ${senderPublicKey.take(8)}...")
            return
        }
        val filePayload = payload.asFile() ?: return
        val meta = pendingFileMetas.remove(payload.id)
        val fileName = meta?.fileName ?: "file"
        val mimeType = meta?.mimeType ?: ""
        val timestamp = System.currentTimeMillis()

        val savedPath: String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val destDir = java.io.File(context.filesDir, "received_files")
                destDir.mkdirs()
                val destFile = java.io.File(destDir, "${timestamp}_$fileName")
                val tmpFile = filePayload.asJavaFile()
                if (tmpFile != null && tmpFile.exists()) {
                    if (!tmpFile.renameTo(destFile)) {
                        tmpFile.copyTo(destFile, overwrite = true)
                        tmpFile.delete()
                    }
                    destFile.absolutePath
                } else {
                    // API 29+: file payloads are backed by a content Uri, not a java File
                    val uri = filePayload.asUri()
                    if (uri != null) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        } ?: return@withContext null
                        destFile.absolutePath
                    } else null
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to save incoming file $fileName")
                null
            }
        }

        val isVoiceNote = mimeType.startsWith("audio/")
        val entity = com.offnetic.data.local.db.entity.Message(
            sessionId = senderPublicKey,
            chatId = senderPublicKey,
            senderPublicKey = senderPublicKey,
            content = if (isVoiceNote) "Voice note  ${meta?.durationLabel ?: ""}".trimEnd()
                else if (savedPath == null) "File transfer failed: $fileName"
                else "File: $fileName",
            type = if (isVoiceNote) com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE
                else com.offnetic.data.local.db.entity.Message.TYPE_FILE,
            timestamp = timestamp,
            isSent = false,
            isRead = false,
            attachmentPath = savedPath,
            attachmentType = if (mimeType.startsWith("image/")) com.offnetic.data.local.db.entity.Message.TYPE_IMAGE
                else if (mimeType.startsWith("video/")) com.offnetic.data.local.db.entity.Message.TYPE_VIDEO
                else com.offnetic.data.local.db.entity.Message.TYPE_FILE
        )
        messageDao.insert(entity)

        val message = com.offnetic.domain.model.Message.fromEntity(entity)
        _incomingMessages.emit(message)
        Timber.d("Incoming file saved: $fileName from ${senderPublicKey.take(8)}... (path=$savedPath)")
    }

    private suspend fun handleEndpointFound(endpointId: String, publicKey: String) {
        val isBlocked = blockedPeerDao.isBlocked(publicKey)
        if (isBlocked) {
            Timber.d("Blocked peer dropped: ${publicKey.take(8)}...")
            return
        }

        val now = System.currentTimeMillis()
        val contact = contactDao.getByPublicKey(publicKey)

        val displayName = contact?.displayName ?: "Unknown"
        val isContact = contact != null
        val lastSeenAt = contact?.lastSeenAt ?: 0L
        val lastPingedAt = contact?.lastPingedAt ?: 0L

        val timeSinceLastSeen = now - lastSeenAt

        if (contact != null) {
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
        }

        val peer = PeerInfo(
            endpointId = endpointId,
            publicKey = publicKey,
            displayName = displayName,
            isContact = isContact,
            isBlocked = false,
            connectionState = ConnectionState.DISCONNECTED,
            lastSeenAt = now,
            lastPingedAt = lastPingedAt
        )

        endpointPeers[endpointId] = peer
        emitPeers()
        Timber.d("handleEndpointFound: added peer ${publicKey.take(8)}... (contact=$isContact), now requesting connection")
        android.util.Log.e("NcapConn", "FOUND peer ${publicKey.take(12)}... → requesting connection")
        requestConnection(endpointId)
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