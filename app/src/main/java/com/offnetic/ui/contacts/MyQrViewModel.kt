package com.offnetic.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.ProfileDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyQrViewModel @Inject constructor(
    private val identityDao: IdentityDao,
    private val profileDao: ProfileDao
) : ViewModel() {

    private val _qrPayload = MutableStateFlow<String?>(null)
    val qrPayload: StateFlow<String?> = _qrPayload.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    init {
        viewModelScope.launch {
            val identity = identityDao.getIdentity()
            if (identity != null) {
                val profile = profileDao.getByPublicKey(identity.publicKey)
                val displayName = profile?.displayName
                val data = QrPairingData(publicKey = identity.publicKey, displayName = displayName)
                _qrPayload.value = data.toQrPayload()
                _displayName.value = displayName ?: identity.publicKey.take(12)
            }
        }
    }
}
