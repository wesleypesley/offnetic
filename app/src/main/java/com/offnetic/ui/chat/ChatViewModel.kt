package com.offnetic.ui.chat

import android.net.wifi.WifiManager
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.crypto.NcapEnvelope
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.nearby.NcapManager
import com.offnetic.ui.navigation.Routes
import com.offnetic.util.media.VoiceNoteRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject

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
    private val signalProtocolManager: SignalProtocolManager,
    private val ncapManager: NcapManager,
    private val voiceNoteRecorder: VoiceNoteRecorder,
    private val wifiManager: WifiManager
) : ViewModel() {

    companion object {
        private const val MAX_BT_VOICE_DURATION_MS = 30_000L
    }

    val contactPublicKey: String = Routes.decodeKey(savedStateHandle.get<String>("contactPublicKey") ?: "")

    private val _contactName = MutableStateFlow(contactPublicKey.take(12) + "...")
    val contactName: StateFlow<String> = _contactName.asStateFlow()

    private val _isContactOnline = MutableStateFlow(false)
    val isContactOnline: StateFlow<Boolean> = _isContactOnline.asStateFlow()

    private val _myPublicKey = MutableStateFlow("")
    val myPublicKey: StateFlow<String> = _myPublicKey.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessage: SharedFlow<String> = _toastMessage

    private var recordingGuard = false

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
                if (msg.chatId == contactPublicKey) {
                    messageDao.markAsRead(contactPublicKey, myPk)
                }
            }
        }

        viewModelScope.launch {
            var wasOnline = false
            ncapManager.peers.collect { peers ->
                val isNowOnline = peers.any {
                    it.publicKey == contactPublicKey && it.connectionState == com.offnetic.domain.model.ConnectionState.CONNECTED
                }
                if (isNowOnline && !wasOnline) {
                    retryUnsentMessages()
                }
                wasOnline = isNowOnline
                _isContactOnline.value = isNowOnline
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

    fun sendFile(fileUri: String) {
        viewModelScope.launch {
            try {
                val identity = identityDao.getIdentity()
                val myPublicKey = identity?.publicKey ?: return@launch
                val file = File(fileUri.removePrefix("file://"))
                if (!file.exists()) return@launch
                val maxSize = 100L * 1024 * 1024
                if (file.length() > maxSize) return@launch

                val mimeType = file.name.substringAfterLast('.', "").lowercase().let { ext ->
                    when (ext) {
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

                val entity = com.offnetic.data.local.db.entity.Message(
                    sessionId = contactPublicKey,
                    chatId = contactPublicKey,
                    senderPublicKey = myPublicKey,
                    content = "File: ${file.name}",
                    type = com.offnetic.data.local.db.entity.Message.TYPE_FILE,
                    timestamp = System.currentTimeMillis(),
                    isSent = false,
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
                    messageDao.update(entity.copy(id = messageId, isSent = true))
                    Timber.d("File sent via NCAPI to ${contactPublicKey.take(8)}")
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
                    if (!wifiManager.isWifiEnabled && elapsedMs > MAX_BT_VOICE_DURATION_MS) {
                        _toastMessage.emit("Connect to Wi-Fi for longer voice notes (max 30s on Bluetooth)")
                        return@launch
                    }
                    val identity = identityDao.getIdentity()
                    val myPublicKey = identity?.publicKey ?: return@launch

                    val entity = com.offnetic.data.local.db.entity.Message(
                        sessionId = contactPublicKey,
                        chatId = contactPublicKey,
                        senderPublicKey = myPublicKey,
                        content = "Voice note  $duration",
                        type = com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE,
                        timestamp = System.currentTimeMillis(),
                        isSent = false,
                        isRead = false,
                        attachmentPath = file.absolutePath
                    )
                    val messageId = messageDao.insert(entity)

                    val audioBytes = file.readBytes()
                    val audioJson = JSONObject().apply {
                        put("type", "voice_note")
                        put("duration", duration)
                        put("content", Base64.encodeToString(audioBytes, Base64.NO_WRAP))
                        put("timestamp", System.currentTimeMillis())
                    }
                    val plaintext = audioJson.toString().toByteArray(Charsets.UTF_8)
                    val ciphertext = signalProtocolManager.encryptMessage(contactPublicKey, plaintext)

                    val envelope = NcapEnvelope.Plain(
                        senderPublicKey = myPublicKey,
                        payloadType = NcapEnvelope.PayloadType.SIGNAL_MESSAGE,
                        payload = ciphertext
                    )

                    val connectedEndpoints = ncapManager.getConnectedEndpointIds(contactPublicKey)
                    if (connectedEndpoints.isNotEmpty()) {
                        ncapManager.sendPayload(connectedEndpoints.first(), envelope.toBytes())
                        messageDao.update(entity.copy(id = messageId, isSent = true))
                        Timber.d("Voice note sent to ${contactPublicKey.take(8)}")
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
            messageDao.markAsRead(contactPublicKey, myPk)
        }
    }

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
                    isSent = false,
                    isRead = false
                )
                val messageId = messageDao.insert(entity)
                Timber.d("sendEncrypted: inserted msg id=$messageId (isSent=false)")

                val ciphertext = signalProtocolManager.encryptMessage(contactPublicKey, plaintext)
                Timber.d("sendEncrypted: encrypted ${plaintext.size}B → ${ciphertext.size}B")

                val envelope = NcapEnvelope.Plain(
                    senderPublicKey = myPublicKey,
                    payloadType = NcapEnvelope.PayloadType.SIGNAL_MESSAGE,
                    payload = ciphertext
                )

                val connectedEndpoints = ncapManager.getConnectedEndpointIds(contactPublicKey)
                if (connectedEndpoints.isNotEmpty()) {
                    val endpointId = connectedEndpoints.first()
                    Timber.d("sendEncrypted: sending on endpoint ${endpointId.take(8)}... (${connectedEndpoints.size} connected)")
                    ncapManager.sendPayload(endpointId, envelope.toBytes())
                    messageDao.update(entity.copy(id = messageId, isSent = true))
                    Timber.d("sendEncrypted: sent + marked isSent=true for msg id=$messageId")
                } else {
                    val anyPeer = ncapManager.peers.value.find { it.publicKey == contactPublicKey }
                    Timber.w("sendEncrypted: no CONNECTED endpoint for ${contactPublicKey.take(8)} (found=${anyPeer != null}, state=${anyPeer?.connectionState}), saved to DB")
                    _toastMessage.emit("${_contactName.value} not connected — message saved")
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
                            messageDao.update(msg.copy(isSent = true))
                            Timber.d("retryUnsentMessages: resent text msg id=${msg.id}")
                        }
                        com.offnetic.data.local.db.entity.Message.TYPE_FILE -> {
                            val path = msg.attachmentPath
                            if (path == null || !java.io.File(path).exists()) continue
                            val file = java.io.File(path)
                            val mimeType = file.name.substringAfterLast('.', "").lowercase().let { ext ->
                                when (ext) {
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
                            ncapManager.sendFile(endpointId, path, file.name, file.length(), mimeType)
                            messageDao.update(msg.copy(isSent = true))
                            Timber.d("retryUnsentMessages: resent file msg id=${msg.id}")
                        }
                        com.offnetic.data.local.db.entity.Message.TYPE_VOICE_NOTE -> {
                            val path = msg.attachmentPath
                            if (path == null || !java.io.File(path).exists()) continue
                            val fileBytes = java.io.File(path).readBytes()
                            val json = org.json.JSONObject().apply {
                                put("type", "voice_note")
                                put("duration", msg.content.removePrefix("Voice note ").trim())
                                put("content", android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP))
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
                            messageDao.update(msg.copy(isSent = true))
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

    private fun formatVoiceDuration(elapsedMs: Long): String {
        val seconds = elapsedMs / 1000
        val min = seconds / 60
        val sec = seconds % 60
        return if (min > 0) "$min:${sec.toString().padStart(2, '0')}" else "0:${sec.toString().padStart(2, '0')}"
    }
}
