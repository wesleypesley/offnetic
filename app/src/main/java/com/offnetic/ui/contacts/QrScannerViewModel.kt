package com.offnetic.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.entity.Message
import com.offnetic.data.nearby.NcapManager
import com.offnetic.data.repository.ContactRepository
import com.offnetic.domain.model.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QrScannerState(
    val scannedData: QrPairingData? = null,
    val isProcessing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val signalProtocolManager: SignalProtocolManager,
    private val contactRepository: ContactRepository,
    private val messageDao: MessageDao,
    private val identityDao: IdentityDao,
    private val ncapManager: NcapManager
) : ViewModel() {

    private val _state = MutableStateFlow(QrScannerState())
    val state: StateFlow<QrScannerState> = _state.asStateFlow()

    fun onQrScanned(data: QrPairingData) {
        if (_state.value.isProcessing) return
        _state.value = _state.value.copy(isProcessing = true)
        viewModelScope.launch {
            try {
                val key = data.publicKey
                val displayName = data.displayName ?: key.take(12)
                val contact = Contact(
                    publicKey = key,
                    displayName = displayName,
                    isVerified = true,
                    addedAt = System.currentTimeMillis()
                )
                contactRepository.insert(contact)

                val myIdentity = identityDao.getIdentity()
                val myPublicKey = myIdentity?.publicKey ?: "local"
                val systemMsg = Message(
                    sessionId = key,
                    chatId = key,
                    senderPublicKey = myPublicKey,
                    content = "Chat established via QR pairing",
                    type = Message.TYPE_SYSTEM,
                    timestamp = System.currentTimeMillis(),
                    isSent = true,
                    isRead = true
                )
                messageDao.insert(systemMsg)

                ncapManager.reconnectToContact(key)

                _state.value = _state.value.copy(scannedData = data, isProcessing = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isProcessing = false, error = e.message)
            }
        }
    }

    fun clearScanned() {
        _state.value = _state.value.copy(scannedData = null, error = null)
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
