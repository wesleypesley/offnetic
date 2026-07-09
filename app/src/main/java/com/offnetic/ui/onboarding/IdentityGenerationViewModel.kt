package com.offnetic.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.crypto.IdentityKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class IdentityGenerationViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager
) : ViewModel() {

    private val _generated = MutableStateFlow(false)
    val generated: StateFlow<Boolean> = _generated.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun generateIdentity() {
        if (_generated.value) return
        viewModelScope.launch {
            _error.value = null
            try {
                identityKeyManager.generateIdentityIfNeeded()
                _generated.value = true
            } catch (e: Exception) {
                Timber.e(e, "Identity generation failed")
                _error.value = "Identity generation failed. Please try again."
            }
        }
    }
}
