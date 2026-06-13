package com.offnetic.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.nearby.NcapManager
import com.offnetic.domain.model.ConnectionState
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
    val isOnline: Boolean = false
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val identityDao: IdentityDao,
    private val contactDao: ContactDao,
    private val profileDao: ProfileDao,
    private val ncapManager: NcapManager
) : ViewModel() {

    private val _myPublicKey = MutableStateFlow("")
    private val _profileDisplayName = MutableStateFlow("")
    val profileDisplayName: StateFlow<String> = _profileDisplayName.asStateFlow()

    val chatSummaries: StateFlow<List<ChatSummary>> = _myPublicKey.flatMapLatest { myPk ->
        combine(
            messageDao.getChatSummaries(),
            messageDao.getUnreadCountsPerChat(myPk),
            contactDao.getAll(),
            ncapManager.peers
        ) { messages, unreadCounts, contacts, peers ->
            val nameMap = contacts.associate { it.publicKey to it.displayName }
            val unreadMap = unreadCounts.associate { it.chatId to it.count }
            val onlineSet = peers.filter { it.connectionState == ConnectionState.CONNECTED }
                .map { it.publicKey }.toSet()
            messages.map { msg ->
                ChatSummary(
                    contactPublicKey = msg.chatId,
                    displayName = nameMap[msg.chatId] ?: msg.chatId.take(12) + "...",
                    lastMessage = msg.content.take(80),
                    lastTimestamp = msg.timestamp,
                    unreadCount = unreadMap[msg.chatId] ?: 0,
                    isOnline = msg.chatId in onlineSet
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            identityDao.getIdentity()?.let { id ->
                _myPublicKey.value = id.publicKey
                profileDao.getByPublicKey(id.publicKey)?.let { profile ->
                    _profileDisplayName.value = profile.displayName
                }
            }
        }
    }
}
