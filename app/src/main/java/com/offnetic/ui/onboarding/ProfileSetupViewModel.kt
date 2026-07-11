package com.offnetic.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.db.entity.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface ProfileSaveState {
    data object Idle : ProfileSaveState
    data object Saving : ProfileSaveState
    data object Saved : ProfileSaveState
    data object Failed : ProfileSaveState
}

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val identityDao: IdentityDao,
    private val profileDao: ProfileDao
) : ViewModel() {

    // Navigation must wait for the row to actually exist — the old fire-and-forget
    // save let the user proceed even when the insert failed (O2)
    private val _saveState = MutableStateFlow<ProfileSaveState>(ProfileSaveState.Idle)
    val saveState: StateFlow<ProfileSaveState> = _saveState.asStateFlow()

    fun saveProfile(displayName: String) {
        if (_saveState.value == ProfileSaveState.Saving || _saveState.value == ProfileSaveState.Saved) return
        _saveState.value = ProfileSaveState.Saving
        viewModelScope.launch {
            try {
                val identity = identityDao.getIdentity()
                    ?: throw IllegalStateException("No identity present at profile setup")
                profileDao.insert(
                    Profile(
                        publicKey = identity.publicKey,
                        displayName = displayName,
                        avatarBlob = null,
                        timestamp = System.currentTimeMillis()
                    )
                )
                _saveState.value = ProfileSaveState.Saved
            } catch (e: Exception) {
                Timber.e(e, "Profile save failed")
                _saveState.value = ProfileSaveState.Failed
            }
        }
    }
}
