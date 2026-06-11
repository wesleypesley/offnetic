package com.offnetic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.crypto.IdentityKeyManager
import com.offnetic.data.local.db.dao.BlockedPeerDao
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.PreKeyDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.db.entity.Profile
import com.offnetic.data.local.db.dao.SessionDao
import com.offnetic.data.local.db.dao.SignalIdentityDao
import com.offnetic.data.local.db.dao.SignalPreKeyDao
import com.offnetic.data.local.db.dao.SignalSenderKeyDao
import com.offnetic.data.local.db.dao.SignalSessionDao
import com.offnetic.data.local.db.dao.SignalSignedPreKeyDao
import com.offnetic.data.nearby.NcapManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AccountUiState(
    val actionMessage: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
    private val identityDao: IdentityDao,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val preKeyDao: PreKeyDao,
    private val blockedPeerDao: BlockedPeerDao,
    private val profileDao: ProfileDao,
    private val signalPreKeyDao: SignalPreKeyDao,
    private val signalSignedPreKeyDao: SignalSignedPreKeyDao,
    private val signalSessionDao: SignalSessionDao,
    private val signalSenderKeyDao: SignalSenderKeyDao,
    private val signalIdentityDao: SignalIdentityDao,
    private val ncapManager: NcapManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val _profileDisplayName = MutableStateFlow("")
    val profileDisplayName: StateFlow<String> = _profileDisplayName.asStateFlow()

    init {
        viewModelScope.launch {
            identityDao.getIdentity()?.let { id ->
                profileDao.getByPublicKeyFlow(id.publicKey).collect { profile ->
                    _profileDisplayName.value = profile?.displayName ?: ""
                }
            }
        }
    }

    fun eraseAllContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val contacts = contactDao.getAll().first()
                contacts.forEach { messageDao.deleteAllForChat(it.publicKey) }
                Timber.d("All content erased — identity preserved")
                _uiState.value = AccountUiState(actionMessage = "All messages and media erased. Identity and contacts preserved.")
            } catch (e: Exception) {
                Timber.e(e, "Erase content failed")
                _uiState.value = AccountUiState(actionMessage = "Failed to erase content")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun eraseAllContentAndLogOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                eraseAllContentData()
                ncapManager.stopAll()
                Timber.d("Content erased, NCAPI stopped — identity preserved")
                _uiState.value = AccountUiState(actionMessage = "Content erased, connections stopped. Restart to return to biometric lock.")
            } catch (e: Exception) {
                Timber.e(e, "Erase and logout failed")
                _uiState.value = AccountUiState(actionMessage = "Failed to erase")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                ncapManager.stopAll()
                eraseAllContentData()
                identityKeyManager.deleteIdentity()
                identityDao.deleteIdentity()
                Timber.d("Account deleted — all data, keys, and identity destroyed")
                _uiState.value = AccountUiState(actionMessage = "Account deleted. All data destroyed. App must be restarted.")
            } catch (e: Exception) {
                Timber.e(e, "Delete account failed")
                _uiState.value = AccountUiState(actionMessage = "Failed to delete account")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun eraseAllContentData() {
        val contacts = contactDao.getAll().first()
        contacts.forEach { contact ->
            messageDao.deleteAllForChat(contact.publicKey)
            sessionDao.deleteByRemotePublicKey(contact.publicKey)
            preKeyDao.delete(contact.publicKey)
            signalSessionDao.delete(contact.publicKey)
            contactDao.delete(contact.publicKey)
        }

        val blocked = blockedPeerDao.getAll().first()
        blocked.forEach { blockedPeerDao.unblock(it.blockedPublicKey) }

        val profiles = profileDao.getAll().first()
        profiles.forEach { profileDao.delete(it.publicKey) }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    fun updateDisplayName(displayName: String) {
        viewModelScope.launch {
            val identity = identityDao.getIdentity() ?: return@launch
            val existing = profileDao.getByPublicKey(identity.publicKey)
            val profile = Profile(
                publicKey = identity.publicKey,
                displayName = displayName,
                avatarBlob = existing?.avatarBlob,
                timestamp = System.currentTimeMillis()
            )
            profileDao.insert(profile)
            _uiState.value = AccountUiState(actionMessage = "Username updated")
        }
    }
}
