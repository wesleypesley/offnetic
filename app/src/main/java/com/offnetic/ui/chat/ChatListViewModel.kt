package com.offnetic.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.relay.RelayRequestManager
import com.offnetic.data.nearby.NcapManager
import com.offnetic.data.network.NetworkMonitor
import com.offnetic.domain.model.ChatReachability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatSummary(
    val contactPublicKey: String,
    val displayName: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val reachability: ChatReachability = ChatReachability.OFFLINE
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val identityDao: IdentityDao,
    private val contactDao: ContactDao,
    private val profileDao: ProfileDao,
    private val relayRequestManager: RelayRequestManager,
    private val ncapManager: NcapManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _myPublicKey = MutableStateFlow("")
    private val _profileDisplayName = MutableStateFlow("")
    val profileDisplayName: StateFlow<String> = _profileDisplayName.asStateFlow()
    val pendingRequestCount: StateFlow<Int> = relayRequestManager.inboundCount

    val chatSummaries: StateFlow<List<ChatSummary>> = _myPublicKey.flatMapLatest { myPk ->
        combine(
            messageDao.getChatSummaries(),
            messageDao.getUnreadCountsPerChat(myPk),
            contactDao.getAll(),
            ncapManager.peers,
            networkMonitor.isOnline
        ) { messages, unreadCounts, contacts, peers, online ->
            val nameMap = contacts.associate { it.publicKey to it.displayName }
            val nostrMap = contacts.associate { it.publicKey to it.nostrPublicKey }
            val unreadMap = unreadCounts.associate { it.chatId to it.count }
            messages.map { msg ->
                ChatSummary(
                    contactPublicKey = msg.chatId,
                    displayName = nameMap[msg.chatId] ?: msg.chatId.take(12) + "...",
                    lastMessage = msg.content.take(80),
                    lastTimestamp = msg.timestamp,
                    unreadCount = unreadMap[msg.chatId] ?: 0,
                    reachability = ChatReachability.forPeer(
                        contactPublicKey = msg.chatId,
                        peers = peers,
                        online = online,
                        relayEligible = nostrMap[msg.chatId] != null
                    )
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            relayRequestManager.refreshCount()
            identityDao.getIdentity()?.let { id ->
                _myPublicKey.value = id.publicKey
                profileDao.getByPublicKey(id.publicKey)?.let { profile ->
                    _profileDisplayName.value = profile.displayName
                }
            }
        }
    }
}
