package com.offnetic.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.relay.RelayRequestManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun accept(requestId: String) {
        viewModelScope.launch {
            relayRequestManager.acceptRequest(requestId)
            load()
        }
    }

    fun ignore(requestId: String) {
        viewModelScope.launch {
            relayRequestManager.ignoreRequest(requestId)
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = RequestsUiState(
                requests = relayRequestManager.pendingRequests(),
                isLoading = false
            )
        }
    }
}
