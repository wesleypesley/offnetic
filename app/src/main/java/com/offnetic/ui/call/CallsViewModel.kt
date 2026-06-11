package com.offnetic.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.db.dao.CallHistoryDao
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.entity.CallHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CallSummary(
    val peerPublicKey: String,
    val displayName: String,
    val lastCallType: Int,
    val lastCallDirection: Int,
    val lastCallTimestamp: Long,
    val lastCallDurationSeconds: Int
)

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val callHistoryDao: CallHistoryDao
) : ViewModel() {

    val callSummaries: StateFlow<List<CallSummary>> = combine(
        callHistoryDao.getAll(),
        contactDao.getAll()
    ) { calls, contacts ->
        val nameMap = contacts.associate { it.publicKey to it.displayName }
        calls
            .groupBy { it.peerPublicKey }
            .mapValues { (_, list) -> list.first() }
            .values
            .map { call ->
                CallSummary(
                    peerPublicKey = call.peerPublicKey,
                    displayName = nameMap[call.peerPublicKey] ?: call.peerPublicKey.take(12) + "...",
                    lastCallType = call.type,
                    lastCallDirection = call.direction,
                    lastCallTimestamp = call.timestamp,
                    lastCallDurationSeconds = call.durationSeconds
                )
            }
            .sortedByDescending { it.lastCallTimestamp }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
}
