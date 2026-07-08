package com.offnetic.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.relay.RelayRequestManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    fun accept(requestId: String) {
        viewModelScope.launch {
            relayRequestManager.acceptRequest(requestId)
        }
    }

    fun ignore(requestId: String) {
        viewModelScope.launch {
            relayRequestManager.ignoreRequest(requestId)
        }
    }
}
