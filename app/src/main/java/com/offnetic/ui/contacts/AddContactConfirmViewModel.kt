package com.offnetic.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.entity.Message
import com.offnetic.data.nearby.NcapManager
import com.offnetic.data.repository.ContactRepository
import com.offnetic.domain.model.Contact
import com.offnetic.domain.model.MessageDeliveryState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddContactConfirmState(
    val publicKey: String = "",
    val displayName: String = "",
    val safetyNumber: String = "",
    val hasNostr: Boolean = false,
    val isValid: Boolean = true,
    val isProcessing: Boolean = false,
    val done: Boolean = false
)

@HiltViewModel
class AddContactConfirmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val messageDao: MessageDao,
    private val identityDao: IdentityDao,
    private val ncapManager: NcapManager
) : ViewModel() {

    private val _state = MutableStateFlow(AddContactConfirmState())
    val state: StateFlow<AddContactConfirmState> = _state.asStateFlow()

    private val data: QrPairingData? =
        savedStateHandle.get<String>("payload")?.let { QrPairingData.fromQrPayload(it) }

    init {
        val d = data
        if (d == null) {
            _state.value = _state.value.copy(isValid = false)
        } else {
            viewModelScope.launch {
                val myPublicKey = identityDao.getIdentity()?.publicKey ?: ""
                _state.value = _state.value.copy(
                    publicKey = d.publicKey,
                    displayName = d.displayName ?: d.publicKey.take(12),
                    safetyNumber = SafetyNumber.formatGroups(SafetyNumber.compute(myPublicKey, d.publicKey)),
                    hasNostr = d.nostrPublicKey != null,
                    isValid = true
                )
            }
        }
    }

    fun confirm() {
        val d = data ?: return
        if (_state.value.isProcessing || _state.value.done) return
        _state.value = _state.value.copy(isProcessing = true)
        viewModelScope.launch {
            val key = d.publicKey
            val displayName = d.displayName ?: key.take(12)
            val contact = Contact(
                publicKey = key,
                displayName = displayName,
                nostrPublicKey = d.nostrPublicKey,
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
                deliveryState = MessageDeliveryState.SENT_LOCAL,
                isRead = true
            )
            messageDao.insert(systemMsg)

            ncapManager.reconnectToContact(key)

            _state.value = _state.value.copy(isProcessing = false, done = true)
        }
    }
}
