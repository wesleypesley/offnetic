package com.offnetic.data.repository

import com.offnetic.data.local.db.dao.BlockedPeerDao
import com.offnetic.domain.model.BlockedPeer
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockRepositoryImpl @Inject constructor(
    private val blockedPeerDao: BlockedPeerDao
) : BlockRepository {

    override suspend fun block(peer: BlockedPeer): Result<Unit> {
        return try { blockedPeerDao.insert(peer.toEntity()); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to block peer", e) }
    }

    override suspend fun isBlocked(publicKey: String): Result<Boolean> {
        return try { Result.Success(blockedPeerDao.isBlocked(publicKey)) }
        catch (e: Exception) { Result.Error("Failed to check block status", e) }
    }

    override suspend fun getBlockedPeer(publicKey: String): Result<BlockedPeer?> {
        return try {
            val entity = blockedPeerDao.getBlockedPeer(publicKey)
            Result.Success(entity?.let { BlockedPeer.fromEntity(it) })
        } catch (e: Exception) { Result.Error("Failed to get blocked peer", e) }
    }

    override fun getAll(): Flow<Result<List<BlockedPeer>>> {
        return blockedPeerDao.getAll()
            .map { Result.Success(it.map { BlockedPeer.fromEntity(it) }) }
    }

    override suspend fun unblock(publicKey: String): Result<Unit> {
        return try { blockedPeerDao.unblock(publicKey); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to unblock peer", e) }
    }

    override suspend fun getCount(): Result<Int> {
        return try { Result.Success(blockedPeerDao.getCount()) }
        catch (e: Exception) { Result.Error("Failed to get blocked count", e) }
    }
}