package com.offnetic.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.crypto.NcapEnvelope
import com.offnetic.data.local.db.dao.BlockedPeerDao
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.PreKeyDao
import com.offnetic.data.local.db.dao.SessionDao
import com.offnetic.data.nearby.NcapManager
import com.offnetic.domain.model.Contact
import com.offnetic.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactDetailUiState(
    val contact: Contact? = null,
    val isBlocked: Boolean = false,
    val isLoading: Boolean = true,
    val actionMessage: String? = null
)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactDao: ContactDao,
    private val blockedPeerDao: BlockedPeerDao,
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val preKeyDao: PreKeyDao,
    private val ncapManager: NcapManager
) : ViewModel() {

    private val publicKey: String = Routes.decodeKey(savedStateHandle.get<String>("publicKey") ?: "")

    private val _uiState = MutableStateFlow(ContactDetailUiState())
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    init {
        loadContact()
    }

    private fun loadContact() {
        viewModelScope.launch {
            val contact = contactDao.getByPublicKey(publicKey)
            val isBlocked = blockedPeerDao.isBlocked(publicKey)
            _uiState.value = ContactDetailUiState(
                contact = contact?.let { Contact.fromEntity(it) },
                isBlocked = isBlocked,
                isLoading = false
            )
        }
    }

    fun blockContact() {
        viewModelScope.launch {
            val contact = contactDao.getByPublicKey(publicKey) ?: return@launch

            ncapManager.peers.value
                .filter { it.publicKey == publicKey && it.connectionState == com.offnetic.domain.model.ConnectionState.CONNECTED }
                .forEach { ncapManager.disconnectFromEndpoint(it.endpointId) }

            sessionDao.deleteByRemotePublicKey(publicKey)
            preKeyDao.delete(publicKey)

            val blockedPeer = com.offnetic.data.local.db.entity.BlockedPeer(
                blockedPublicKey = publicKey,
                blockedAt = System.currentTimeMillis(),
                displayNameSnapshot = contact.displayName
            )
            blockedPeerDao.insert(blockedPeer)

            _uiState.value = _uiState.value.copy(isBlocked = true, actionMessage = "Contact blocked")
        }
    }

    fun unblockContact() {
        viewModelScope.launch {
            blockedPeerDao.unblock(publicKey)
            _uiState.value = _uiState.value.copy(isBlocked = false, actionMessage = "Contact unblocked. Re-add via QR scan to restore session.")
        }
    }

    fun softDelete() {
        viewModelScope.launch {
            contactDao.delete(publicKey)
            _uiState.value = _uiState.value.copy(
                actionMessage = "Contact removed. Session preserved. Reversible by re-scanning QR."
            )
        }
    }

    fun hardDelete() {
        viewModelScope.launch {
            val contact = contactDao.getByPublicKey(publicKey)
            if (contact != null) {
                val peer = ncapManager.peers.value.find { it.publicKey == publicKey }
                if (peer != null) {
                    try {
                        val envelope = com.offnetic.data.crypto.NcapEnvelope.Plain(
                            senderPublicKey = publicKey,
                            payloadType = NcapEnvelope.PayloadType.SIGNAL_SESSION_TERMINATED,
                            payload = ByteArray(0)
                        )
                        ncapManager.sendPayload(peer.endpointId, envelope.toBytes())
                    } catch (_: Exception) {}
                }
            }

            messageDao.deleteAllForChat(publicKey)
            sessionDao.deleteByRemotePublicKey(publicKey)
            preKeyDao.delete(publicKey)
            contactDao.delete(publicKey)

            _uiState.value = _uiState.value.copy(
                contact = null,
                actionMessage = "Contact deleted. All messages and session data wiped."
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }
}
