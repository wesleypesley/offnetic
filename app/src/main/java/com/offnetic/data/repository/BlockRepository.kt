package com.offnetic.data.repository

import com.offnetic.domain.model.BlockedPeer
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface BlockRepository {
    suspend fun block(peer: BlockedPeer): Result<Unit>
    suspend fun isBlocked(publicKey: String): Result<Boolean>
    suspend fun getBlockedPeer(publicKey: String): Result<BlockedPeer?>
    fun getAll(): Flow<Result<List<BlockedPeer>>>
    suspend fun unblock(publicKey: String): Result<Unit>
    suspend fun getCount(): Result<Int>
}