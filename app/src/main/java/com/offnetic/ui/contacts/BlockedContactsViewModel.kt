package com.offnetic.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.dao.BlockedPeerDao
import com.offnetic.domain.model.BlockedPeer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockedContactsViewModel @Inject constructor(
    private val blockedPeerDao: BlockedPeerDao
) : ViewModel() {

    val blockedPeers: StateFlow<List<BlockedPeer>> = blockedPeerDao.getAll()
        .map { entities -> entities.map { BlockedPeer.fromEntity(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unblock(publicKey: String) {
        viewModelScope.launch {
            blockedPeerDao.unblock(publicKey)
        }
    }
}
