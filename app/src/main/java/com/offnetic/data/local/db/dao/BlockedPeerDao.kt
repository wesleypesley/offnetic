package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.BlockedPeer
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedPeerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blockedPeer: BlockedPeer)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_peers WHERE blockedPublicKey = :publicKey)")
    suspend fun isBlocked(publicKey: String): Boolean

    @Query("SELECT * FROM blocked_peers WHERE blockedPublicKey = :publicKey")
    suspend fun getBlockedPeer(publicKey: String): BlockedPeer?

    @Query("SELECT * FROM blocked_peers ORDER BY blockedAt DESC")
    fun getAll(): Flow<List<BlockedPeer>>

    @Query("DELETE FROM blocked_peers WHERE blockedPublicKey = :publicKey")
    suspend fun unblock(publicKey: String)

    @Query("SELECT COUNT(*) FROM blocked_peers")
    suspend fun getCount(): Int
}