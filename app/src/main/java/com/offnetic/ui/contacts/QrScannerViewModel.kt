package com.offnetic.ui.contacts

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class QrScannerState(
    val detectedPayload: String? = null,
    val error: String? = null
)

@HiltViewModel
class QrScannerViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(QrScannerState())
    val state: StateFlow<QrScannerState> = _state.asStateFlow()

    fun onCodeDetected(rawPayload: String) {
        if (_state.value.detectedPayload != null) return
        _state.value = _state.value.copy(detectedPayload = rawPayload)
    }

    fun clearDetected() {
        _state.value = _state.value.copy(detectedPayload = null)
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
