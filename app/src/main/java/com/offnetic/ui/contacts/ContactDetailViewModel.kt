package com.offnetic.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.offnetic.data.crypto.NcapEnvelope
import com.offnetic.data.local.db.OffneticDatabase
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.PreKeyDao
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
    val isLoading: Boolean = true,
    val actionMessage: String? = null
)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val database: OffneticDatabase,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
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
            _uiState.value = ContactDetailUiState(
                contact = contact?.let { Contact.fromEntity(it) },
                isLoading = false
            )
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

            // Atomic — a crash mid-delete won't leave orphaned rows in any of the tables
            database.withTransaction {
                messageDao.deleteAllForChat(publicKey)
                preKeyDao.delete(publicKey)
                contactDao.delete(publicKey)
            }

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
