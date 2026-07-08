package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.local.db.entity.RequestState

@Dao
interface PendingRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingRequestEntity)

    @Query("SELECT * FROM pending_request WHERE requestId = :requestId")
    suspend fun getById(requestId: String): PendingRequestEntity?

    @Query("SELECT * FROM pending_request WHERE direction = 'INBOUND' AND state = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getInboundPending(): List<PendingRequestEntity>

    @Query("SELECT * FROM pending_request WHERE direction = 'OUTBOUND' AND state = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getOutboundPending(): List<PendingRequestEntity>

    @Query("SELECT * FROM pending_request WHERE peerOffneticKey = :peerOffneticKey ORDER BY createdAt DESC LIMIT 1")
    suspend fun getByPeer(peerOffneticKey: String): PendingRequestEntity?

    @Query("UPDATE pending_request SET state = :state WHERE requestId = :requestId")
    suspend fun updateState(requestId: String, state: RequestState)

    @Query("DELETE FROM pending_request WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM pending_request WHERE expiresAt < :now AND direction = 'INBOUND'")
    suspend fun deleteExpiredInbound(now: Long)

    @Query("DELETE FROM pending_request")
    suspend fun clear()
}
