package com.offnetic.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.crypto.IdentityKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IdentityGenerationViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager
) : ViewModel() {

    private val _generated = MutableStateFlow(false)
    val generated: StateFlow<Boolean> = _generated.asStateFlow()

    fun generateIdentity() {
        viewModelScope.launch {
            identityKeyManager.generateIdentityIfNeeded()
            _generated.value = true
        }
    }
}
