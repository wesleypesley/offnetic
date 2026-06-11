package com.offnetic.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.nearby.NcapManager
import com.offnetic.domain.model.ConnectionState
import com.offnetic.domain.model.NearbyState
import com.offnetic.domain.model.PeerInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val ncapManager: NcapManager
) : ViewModel() {

    val peers: StateFlow<List<PeerInfo>> = ncapManager.peers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyState: StateFlow<NearbyState> = ncapManager.nearbyState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NearbyState.Idle)

    fun connectToPeer(endpointId: String) {
        ncapManager.requestConnection(endpointId)
    }

    fun disconnectPeer(endpointId: String) {
        ncapManager.disconnectFromEndpoint(endpointId)
    }
}
