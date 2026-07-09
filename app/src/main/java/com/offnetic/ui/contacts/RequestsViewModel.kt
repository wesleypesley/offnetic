package com.offnetic.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.relay.RelayRequestManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequestsUiState(
    val requests: List<PendingRequestEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val relayRequestManager: RelayRequestManager
) : ViewModel() {

    val uiState: StateFlow<RequestsUiState> = relayRequestManager.pendingRequestsFlow()
        .map { list -> RequestsUiState(requests = list, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RequestsUiState())

    private val _inFlight = MutableStateFlow<Set<String>>(emptySet())
    val inFlight: StateFlow<Set<String>> = _inFlight.asStateFlow()

    fun accept(requestId: String) {
        if (_inFlight.value.contains(requestId)) return
        _inFlight.update { it + requestId }
        viewModelScope.launch {
            try {
                relayRequestManager.acceptRequest(requestId)
            } finally {
                _inFlight.update { it - requestId }
            }
        }
    }

    fun ignore(requestId: String) {
        if (_inFlight.value.contains(requestId)) return
        _inFlight.update { it + requestId }
        viewModelScope.launch {
            try {
                relayRequestManager.ignoreRequest(requestId)
            } finally {
                _inFlight.update { it - requestId }
            }
        }
    }
}
