package com.offnetic.ui.onboarding

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.db.entity.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    val identityDao: IdentityDao,
    val profileDao: ProfileDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun saveProfile(displayName: String, avatarUri: Uri?) {
        viewModelScope.launch {
            val identity = identityDao.getIdentity() ?: return@launch
            val profile = Profile(
                publicKey = identity.publicKey,
                displayName = displayName,
                avatarBlob = avatarUri?.let { uriToBase64(it) },
                timestamp = System.currentTimeMillis()
            )
            profileDao.insert(profile)
        }
    }

    private suspend fun uriToBase64(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input)
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            val output = ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } ?: ""
    }
}
