package com.offnetic.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.db.entity.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    val identityDao: IdentityDao,
    val profileDao: ProfileDao
) : ViewModel() {

    fun saveProfile(displayName: String) {
        viewModelScope.launch {
            val identity = identityDao.getIdentity() ?: return@launch
            val profile = Profile(
                publicKey = identity.publicKey,
                displayName = displayName,
                avatarBlob = null,
                timestamp = System.currentTimeMillis()
            )
            profileDao.insert(profile)
        }
    }
}
