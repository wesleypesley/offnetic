package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.CallHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: CallHistoryEntity): Long

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE peerPublicKey = :peerPublicKey ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForPeer(peerPublicKey: String): CallHistoryEntity?

    @Query("SELECT * FROM call_history WHERE peerPublicKey = :peerPublicKey ORDER BY timestamp DESC")
    fun getByPeer(peerPublicKey: String): Flow<List<CallHistoryEntity>>
}
