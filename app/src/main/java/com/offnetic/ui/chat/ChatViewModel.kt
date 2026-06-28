package com.offnetic.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.crypto.NcapEnvelope
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.PendingRequestDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.db.dao.RelayOutboxDao
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.local.db.entity.RequestDirection
import com.offnetic.data.relay.RelayControlSender
import com.offnetic.data.relay.RelayOutboxProcessor
import com.offnetic.data.relay.RelayRequestManager
import com.offnetic.data.nearby.NcapManager
import com.offnetic.data.network.NetworkMonitor
import com.offnetic.domain.model.ChatReachability
import com.offnetic.domain.model.MessageDeliveryState
import com.offnetic.ui.navigation.Routes
import com.offnetic.util.ActiveChatTracker
import com.offnetic.util.MessageNotificationManager
import com.offnetic.util.media.VoiceNoteRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val RELAY_OUTBOX_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val RELAY_OUTBOX_CAP = 50
private const val CONNECTION_REQUEST_TTL_MS = 72L * 60 * 60 * 1000

data class ChatUiState(
    val messages: List<com.offnetic.domain.model.Message> = emptyList(),
    val contactPublicKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    private val identityDao: IdentityDao,
    private val contactDao: ContactDao,
    private val profileDao: ProfileDao,
    private val relayOutboxDao: RelayOutboxDao,
    private val pendingRequestDao: PendingRequestDao,
    private val relayControlSender: RelayControlSender,
    private val relayOutboxProcessor: RelayOutboxProcessor,
    private val signalProtocolManager: SignalProtocolManager,
    private val ncapManager: NcapManager,
    private val networkMonitor: NetworkMonitor,
    private val voiceNoteRecorder: VoiceNoteRecorder,
    private val activeChatTracker: ActiveChatTracker,
    private val messageNotificationManager: MessageNotificationManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val contactPublicKey: String = Routes.decodeKey(savedStateHandle.get<String>("contactPublicKey") ?: "")

    private val _contactName = MutableStateFlow(contactPublicKey.take(12) + "...")
    val contactName: StateFlow<String> = _contactName.asStateFlow()

    private val _relayEligible = MutableStateFlow(false)

    val reachability: StateFlow<ChatReachability> =
        combine(ncapManager.peers, networkMonitor.isOnline, _relayEligible) { peers, online, relayEligible ->
            ChatReachability.forPeer(
                contactPublicKey = contactPublicKey,
                peers = peers,
                online = online,
                relayEligible = relayEligible
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatReachability.OFFLINE)

    init {
        viewModelScope.launch {
            _relayEligible.value = contactDao.getByPublicKey(contactPublicKey)?.nostrPublicKey != null
        }
    }

    private val _myPublicKey = MutableStateFlow("")
    val myPublicKey: StateFlow<String> = _myPublicKey.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    val locationRequired: SharedFlow<Unit> = ncapManager.locationRequired

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessage: SharedFlow<String> = _toastMessage

    private var recordingGuard = false

    fun setActive() {
        activeChatTracker.activeChatKey = contactPublicKey
        messageNotificationManager.dismissForContact(contactPublicKey)
    }
    fun clearActive() {
        if (activeChatTracker.activeChatKey == contactPublicKey) {
            activeChatTracker.activeChatKey = null
        }
    }

    val messages: StateFlow<List<com.offnetic.domain.model.Message>> = messageDao.getMessagesForChat(contactPublicKey, 100, 0)
        .map { entities -> entities.map { com.offnetic.domain.model.Message.fromEntity(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            contactDao.getByPublicKey(contactPublicKey)?.let { contact ->
                _contactName.value = contact.displayName
            }
        }

        ncapManager.reconnectToContact(contactPublicKey)

        viewModelScope.launch {
            val myIdentity = identityDao.getIdentity()
            val myPk = myIdentity?.publicKey ?: return@launch
            _myPublicKey.value = myPk
            ncapManager.incomingMessages.collect { msg ->
                if (msg.chatId == contactPublicKey && activeChatTracker.activeChatKey == contactPublicKey) {
                    messageDao.markAsRead(contactPublicKey, myPk)
                    if (msg.messageUuid.isNotEmpty()) {
                        ncapManager.sendReadReceipt(contactPublicKey, msg.messageUuid)
                        contactDao.getByPublicKey(contactPublicKey)?.nostrPublicKey?.let {
                            relayControlSender.sendReadReceipt(it, msg.messageUuid)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            var wasOnline = false
            ncapManager.peers.collect { peers ->
                val isNowOnline = peers.any {
                    it.publicKey == contactPublicKey &&
                        (it.connectionState == com.offnetic.domain.model.ConnectionState.CONNECTED ||
                         it.connectionState == com.offnetic.domain.model.ConnectionState.CONNECTING)
                }
                if (isNowOnline && !wasOnline) {
                    retryUnsentMessages()
                }
                wasOnline = isNowOnline
            }
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank() || text.length > 5000) return
        val json = JSONObject().apply {
            put("content", text)
            put("timestamp", System.currentTimeMillis())
        }
        val plaintext = json.toString().toByteArray(Charsets.UTF_8)
        sendEncrypted(plaintext, com.offnetic.data.local.db.entity.Message.TYPE_TEXT, text)
    }

    fun sendFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val identity = identityDao.getIdentity()
                val myPublicKey = identity?.publicKey ?: return@launch
                val file = withContext(Dispatchers.IO) { copyToLocalFile(uri) } ?: run {
                    _toastMessage.emit("Could not read selected file")
                    return@launch
                }
                val maxSize = 100L * 1024 * 1024
                if (file.length() > maxSize) {
                    _toastMessage.emit("File exceeds 100MB limit")
                    return@launch
                }

                val mimeType = mimeTypeFor(file.name)

                val entity = com.offnetic.data.local.db.entity.Message(
                    sessionId = contactPublicKey,
                    chatId = contactPublicKey,
                    senderPublicKey = myPublicKey,
                    content = "File: ${file.name}",
                    type = com.offnetic.data.local.db.entity.Message.TYPE_FILE,
                    timestamp = System.currentTimeMillis(),
                    deliveryState = MessageDeliveryState.SAVED,
                    isRead = false,
                    attachmentPath = file.absolutePath
                )
                val messageId = messageDao.insert(entity)

                val connectedEndpoints = ncapManager.getConnectedEndpointIds(contactPublicKey)
                if (connectedEndpoints.isNotEmpty()) {
                    ncapManager.sendFile(
                        connectedEndpoints.first(),
                        file.absolutePath,
                        file.name,
                        file.length(),
                        mimeType
                    )
                    val current = messageDao.getById(messageId)
                    if (current?.type != com.offnetic.data.local.db.entity.Message.TYPE_CANCELLED) {
                        messageDao.update(entity.copy(id = messageId, deliveryState = MessageDeliveryState.SENT_LOCAL))
                    }
                    Timber.d("File sent via NCAPI to ${contactPublicKey.take(8)}")
                    withContext(Dispatchers.IO) { file.delete() }
                } else {
                    Timber.w("No connected peer for file send to ${contactPublicKey.take(8)}")
                    _toastMessage.emit("${_contactName.value} not connected — file saved")
                }
            } catch (e: Exception) {
                Timber.e(e, "sendFile failed")
                _toastMessage.emit("File send failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun toggleVoiceRecording() {
        if (voiceNoteRecorder.isRecording) {
            val elapsedMs = voiceNoteRecorder.elapsedMs
            viewModelScope.launch {
                val file = voiceNoteRecorder.stopRecording()
                recordingGuard = false
                if (file != null && file.exists() && file.length() > 0) {
                    val duration = formatVoiceDuration(elapsedMs)
                    val identity = identityDao.getIdentity()
                    val myPublicKey = identity?.publicKey ?: return@launch

                    val entity = com.offnetic.data.local.db.entity.Message(
                        sessionId = contactPublicKey,
                        chatId = contactPublicKey,
                        senderPublicKey = myPublicKey,
                        content = "Voice note  $duration",
                        type = com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE,
                        timestamp = System.currentTimeMillis(),
                        deliveryState = MessageDeliveryState.SAVED,
                        isRead = false,
                        attachmentPath = file.absolutePath
                    )
                    val messageId = messageDao.insert(entity)

                    val connectedEndpoints = ncapManager.getConnectedEndpointIds(contactPublicKey)
                    if (connectedEndpoints.isNotEmpty()) {
                        try {
                            ncapManager.sendFile(
                                connectedEndpoints.first(),
                                file.absolutePath,
                                file.name,
                                file.length(),
                                "audio/mp4",
                                duration
                            )
                            val current = messageDao.getById(messageId)
                            if (current?.type != com.offnetic.data.local.db.entity.Message.TYPE_CANCELLED) {
                                messageDao.update(entity.copy(id = messageId, deliveryState = MessageDeliveryState.SENT_LOCAL))
                            }
                            Timber.d("Voice note sent to ${contactPublicKey.take(8)}")
                        } catch (e: Exception) {
                            Timber.e(e, "Voice note send failed")
                            _toastMessage.emit("Voice note send failed — saved")
                        }
                    } else {
                        Timber.w("No connected peer for voice note to ${contactPublicKey.take(8)}")
                        _toastMessage.emit("${_contactName.value} not connected — voice note saved")
                    }
                }
            }
        } else {
            if (recordingGuard) return
            recordingGuard = true
            try {
                voiceNoteRecorder.startRecording()
                _isRecording.value = true
                viewModelScope.launch {
                    while (voiceNoteRecorder.isRecording) {
                        _isRecording.value = true
                        kotlinx.coroutines.delay(100)
                    }
                    _isRecording.value = false
                    recordingGuard = false
                }
            } catch (_: Exception) {
                _isRecording.value = false
                recordingGuard = false
            }
        }
    }

    fun markAsRead() {
        viewModelScope.launch {
            val identity = identityDao.getIdentity()
            val myPk = identity?.publicKey ?: return@launch
            val unreadUuids = messageDao.getUnreadIncomingUuids(contactPublicKey, myPk)
            messageDao.markAsRead(contactPublicKey, myPk)
            if (unreadUuids.isNotEmpty()) {
                val npub = contactDao.getByPublicKey(contactPublicKey)?.nostrPublicKey
                unreadUuids.forEach { uuid ->
                    if (npub != null) relayControlSender.sendReadReceipt(npub, uuid)
                    ncapManager.sendReadReceipt(contactPublicKey, uuid)
                }
            }
        }
    }

    private var connectingToastShown = false

    private fun sendEncrypted(plaintext: ByteArray, type: Int, content: String) {
        Timber.d("sendEncrypted: type=$type, size=${plaintext.size}, peer=${contactPublicKey.take(8)}...")
        viewModelScope.launch {
            try {
                val identity = identityDao.getIdentity()
                val myPublicKey = identity?.publicKey ?: run {
                    Timber.w("sendEncrypted: no local identity, aborting")
                    return@launch
                }

                val entity = com.offnetic.data.local.db.entity.Message(
                    sessionId = contactPublicKey,
                    chatId = contactPublicKey,
                    senderPublicKey = myPublicKey,
                    content = content,
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    deliveryState = MessageDeliveryState.SAVED,
                    isRead = false
                )
                val messageId = messageDao.insert(entity)
                Timber.d("sendEncrypted: inserted id=$messageId uuid=${entity.messageUuid.take(8)} (SAVED)")

                if (!signalProtocolManager.hasSession(contactPublicKey)) {
                    val contact = contactDao.getByPublicKey(contactPublicKey)
                    val nostrPub = contact?.nostrPublicKey
                    if (nostrPub != null) {
                        val bundle = signalProtocolManager.buildPreKeyBundleBytes()
                        val myName = profileDao.getByPublicKey(myPublicKey)?.displayName ?: myPublicKey.take(12) + "..."
                        val now = System.currentTimeMillis()
                        pendingRequestDao.upsert(
                            PendingRequestEntity(
                                requestId = RelayRequestManager.OUTBOUND_PREFIX + nostrPub,
                                direction = RequestDirection.OUTBOUND,
                                peerOffneticKey = contactPublicKey,
                                peerNostrKey = nostrPub,
                                displayName = contact.displayName,
                                createdAt = now,
                                expiresAt = now + CONNECTION_REQUEST_TTL_MS
                            )
                        )
                        relayControlSender.sendConnectionRequest(nostrPub, myPublicKey, myName, bundle)
                        Timber.d("sendEncrypted: uuid=${entity.messageUuid.take(8)} no session — sent connection request + tracked outbound")
                        if (!connectingToastShown) {
                            connectingToastShown = true
                            _toastMessage.emit("${_contactName.value} not nearby — connecting over the internet")
                        }
                    } else {
                        _toastMessage.emit("${_contactName.value} not connected — message saved")
                    }
                    return@launch
                }

                val ciphertext = signalProtocolManager.encryptMessage(contactPublicKey, plaintext)
                Timber.d("sendEncrypted: encrypted ${plaintext.size}B → ${ciphertext.size}B")

                val envelope = NcapEnvelope.Plain(
                    senderPublicKey = myPublicKey,
                    payloadType = NcapEnvelope.PayloadType.SIGNAL_MESSAGE,
                    payload = ciphertext,
                    messageUuid = entity.messageUuid
                )

                val connectedEndpoints = ncapManager.getConnectedEndpointIds(contactPublicKey)
                if (connectedEndpoints.isNotEmpty()) {
                    val endpointId = connectedEndpoints.first()
                    Timber.d("sendEncrypted: sending on endpoint ${endpointId.take(8)}... (${connectedEndpoints.size} connected)")
                    ncapManager.sendPayload(endpointId, envelope.toBytes())
                    val current = messageDao.getById(messageId)
                    if (current?.type != com.offnetic.data.local.db.entity.Message.TYPE_CANCELLED) {
                        messageDao.update(entity.copy(id = messageId, deliveryState = MessageDeliveryState.SENT_LOCAL))
                    }
                    Timber.d("sendEncrypted: sent + marked SENT_LOCAL for msg id=$messageId")
                } else {
                    val nostrKey = contactDao.getByPublicKey(contactPublicKey)?.nostrPublicKey
                    val current = messageDao.getById(messageId)
                    val cancelled = current?.type == com.offnetic.data.local.db.entity.Message.TYPE_CANCELLED
                    if (nostrKey != null && !cancelled) {
                        val now = System.currentTimeMillis()
                        relayOutboxDao.upsert(
                            com.offnetic.data.local.db.entity.RelayOutboxEntity(
                                messageUuid = entity.messageUuid,
                                chatId = contactPublicKey,
                                ciphertext = ciphertext,
                                createdAt = now,
                                expiresAt = now + RELAY_OUTBOX_TTL_MS
                            )
                        )
                        relayOutboxDao.evictOldestPending(contactPublicKey, RELAY_OUTBOX_CAP)
                        messageDao.update(entity.copy(id = messageId, deliveryState = MessageDeliveryState.SENT_RELAY))
                        Timber.d("sendEncrypted: uuid=${entity.messageUuid.take(8)} enqueued to relay outbox (SENT_RELAY)")
                        withContext(Dispatchers.IO) { relayOutboxProcessor.processPending() }
                    } else {
                        _toastMessage.emit("${_contactName.value} not connected — message saved")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "sendEncrypted FAILED: ${e.message}")
                _toastMessage.emit("Send failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    private fun retryUnsentMessages() {
        viewModelScope.launch {
            try {
                val identity = identityDao.getIdentity()
                val myPublicKey = identity?.publicKey ?: return@launch

                val unsent = messageDao.getUnsentMessagesForChat(contactPublicKey, myPublicKey)
                if (unsent.isEmpty()) return@launch
                Timber.d("retryUnsentMessages: found ${unsent.size} unsent messages")

                val connectedEndpoints = ncapManager.getConnectedEndpointIds(contactPublicKey)
                if (connectedEndpoints.isEmpty()) return@launch
                val endpointId = connectedEndpoints.first()

                for (msg in unsent) {
                    when (msg.type) {
                        com.offnetic.data.local.db.entity.Message.TYPE_TEXT -> {
                            val json = org.json.JSONObject().apply {
                                put("content", msg.content)
                                put("timestamp", msg.timestamp)
                            }
                            val plaintext = json.toString().toByteArray(Charsets.UTF_8)
                            val ciphertext = signalProtocolManager.encryptMessage(contactPublicKey, plaintext)
                            val envelope = NcapEnvelope.Plain(
                                senderPublicKey = myPublicKey,
                                payloadType = NcapEnvelope.PayloadType.SIGNAL_MESSAGE,
                                payload = ciphertext
                            )
                            ncapManager.sendPayload(endpointId, envelope.toBytes())
                            if (messageDao.getById(msg.id)?.type != com.offnetic.data.local.db.entity.Message.TYPE_CANCELLED) {
                                messageDao.update(msg.copy(deliveryState = MessageDeliveryState.SENT_LOCAL))
                            }
                            Timber.d("retryUnsentMessages: resent text msg id=${msg.id}")
                        }
                        com.offnetic.data.local.db.entity.Message.TYPE_FILE -> {
                            val path = msg.attachmentPath
                            if (path == null || !java.io.File(path).exists()) continue
                            val file = java.io.File(path)
                            ncapManager.sendFile(endpointId, path, file.name, file.length(), mimeTypeFor(file.name))
                            if (messageDao.getById(msg.id)?.type != com.offnetic.data.local.db.entity.Message.TYPE_CANCELLED) {
                                messageDao.update(msg.copy(deliveryState = MessageDeliveryState.SENT_LOCAL))
                            }
                            Timber.d("retryUnsentMessages: resent file msg id=${msg.id}")
                        }
                        com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE -> {
                            val path = msg.attachmentPath
                            if (path == null || !java.io.File(path).exists()) continue
                            val file = java.io.File(path)
                            val duration = msg.content.removePrefix("Voice note").trim()
                            ncapManager.sendFile(endpointId, path, file.name, file.length(), "audio/mp4", duration)
                            if (messageDao.getById(msg.id)?.type != com.offnetic.data.local.db.entity.Message.TYPE_CANCELLED) {
                                messageDao.update(msg.copy(deliveryState = MessageDeliveryState.SENT_LOCAL))
                            }
                            Timber.d("retryUnsentMessages: resent voice note msg id=${msg.id}")
                        }
                        else -> continue
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "retryUnsentMessages failed")
            }
        }
    }

    /** Resolves a picked Uri to a local file, copying content Uris into cache. Call on Dispatchers.IO. */
    private fun copyToLocalFile(uri: Uri): File? {
        return try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it) }?.takeIf { it.exists() }
            } else {
                val name = queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
                val dest = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$name")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
                dest
            }
        } catch (e: Exception) {
            Timber.w(e, "copyToLocalFile failed")
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun mimeTypeFor(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private fun formatVoiceDuration(elapsedMs: Long): String {
        val seconds = elapsedMs / 1000
        val min = seconds / 60
        val sec = seconds % 60
        return if (min > 0) "$min:${sec.toString().padStart(2, '0')}" else "0:${sec.toString().padStart(2, '0')}"
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            try {
                val msg = messageDao.getById(messageId)
                messageDao.deleteById(messageId)
                val path = msg?.attachmentPath
                if (path != null) {
                    withContext(Dispatchers.IO) {
                        if (!path.startsWith("content://")) {
                            java.io.File(path).delete()
                        }
                    }
                }
            }
            catch (e: Exception) { Timber.e(e, "deleteMessage failed") }
        }
    }

    fun cancelMessage(messageId: Long) {
        viewModelScope.launch {
            try {
                val msg = messageDao.getById(messageId) ?: return@launch
                messageDao.update(
                    com.offnetic.data.local.db.entity.Message(
                        id = msg.id, messageUuid = msg.messageUuid, sessionId = msg.sessionId, chatId = msg.chatId,
                        senderPublicKey = msg.senderPublicKey, content = "Message cancelled",
                        type = com.offnetic.data.local.db.entity.Message.TYPE_CANCELLED,
                        timestamp = msg.timestamp, deliveryState = MessageDeliveryState.SAVED, isRead = false,
                        attachmentPath = msg.attachmentPath, attachmentType = msg.attachmentType
                    )
                )
            } catch (e: Exception) { Timber.e(e, "cancelMessage failed") }
        }
    }

    fun retryMessage(messageId: Long) {
        viewModelScope.launch {
            val msg = messageDao.getById(messageId) ?: return@launch
            if (msg.type == com.offnetic.data.local.db.entity.Message.TYPE_TEXT) {
                val json = org.json.JSONObject().apply {
                    put("content", msg.content)
                    put("timestamp", System.currentTimeMillis())
                }
                val plaintext = json.toString().toByteArray(Charsets.UTF_8)
                val entity = msg.copy(type = com.offnetic.data.local.db.entity.Message.TYPE_TEXT, deliveryState = MessageDeliveryState.SAVED)
                messageDao.update(entity)
                sendEncrypted(plaintext, com.offnetic.data.local.db.entity.Message.TYPE_TEXT, msg.content)
            } else if (msg.type == com.offnetic.data.local.db.entity.Message.TYPE_FILE ||
                       msg.type == com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE ||
                       msg.type == com.offnetic.data.local.db.entity.Message.TYPE_IMAGE ||
                       msg.type == com.offnetic.data.local.db.entity.Message.TYPE_VIDEO) {
                val path = msg.attachmentPath
                if (path != null && java.io.File(path).exists()) {
                    val entity = msg.copy(
                        type = when (msg.type) {
                            com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE -> msg.type
                            else -> com.offnetic.data.local.db.entity.Message.TYPE_FILE
                        },
                        deliveryState = MessageDeliveryState.SAVED
                    )
                    messageDao.update(entity)
                    val connected = ncapManager.getConnectedEndpointIds(contactPublicKey)
                    if (connected.isNotEmpty()) {
                        val file = java.io.File(path)
                        val mime = if (msg.type == com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE) "audio/mp4"
                            else mimeTypeFor(file.name)
                        ncapManager.sendFile(connected.first(), path, file.name, file.length(), mime)
                        messageDao.update(entity.copy(deliveryState = MessageDeliveryState.SENT_LOCAL))
                    } else {
                        _toastMessage.emit("${_contactName.value} not connected")
                    }
                }
            }
        }
    }
}
