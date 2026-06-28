package com.offnetic.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.local.crypto.IdentityKeyManager
import com.offnetic.data.local.db.dao.ProfileDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
    private val profileDao: ProfileDao,
    private val nostrIdentityManager: NostrIdentityManager
) : ViewModel() {

    private val _hasIdentity = MutableStateFlow(false)
    val hasIdentity: StateFlow<Boolean> = _hasIdentity.asStateFlow()

    private val _hasProfile = MutableStateFlow(false)
    val hasProfile: StateFlow<Boolean> = _hasProfile.asStateFlow()

    init {
        viewModelScope.launch {
            val id = identityKeyManager.getIdentity()
            _hasIdentity.value = id != null
            if (id != null) {
                _hasProfile.value = profileDao.getByPublicKey(id.publicKey) != null
            }
            try {
                nostrIdentityManager.generateIfNeeded()
            } catch (e: Exception) {
                Timber.w(e, "Nostr identity generation failed")
            }
        }
    }
}
