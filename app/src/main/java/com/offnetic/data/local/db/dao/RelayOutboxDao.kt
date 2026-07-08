package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.RelayOutboxEntity
import com.offnetic.data.local.db.entity.RelayOutboxState

@Dao
interface RelayOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RelayOutboxEntity)

    @Query("SELECT * FROM relay_outbox WHERE messageUuid = :messageUuid")
    suspend fun getByUuid(messageUuid: String): RelayOutboxEntity?

    @Query("SELECT * FROM relay_outbox WHERE state IN ('PENDING', 'RELAYED') ORDER BY createdAt ASC LIMIT 100")
    suspend fun getActive(): List<RelayOutboxEntity>

    @Query("SELECT COUNT(*) FROM relay_outbox WHERE chatId = :chatId")
    suspend fun countForChat(chatId: String): Int

    @Query("UPDATE relay_outbox SET state = :state WHERE messageUuid = :messageUuid")
    suspend fun updateState(messageUuid: String, state: RelayOutboxState)

    @Query(
        """
        DELETE FROM relay_outbox
        WHERE state = 'PENDING' AND chatId = :chatId
        AND messageUuid NOT IN (
            SELECT messageUuid FROM relay_outbox
            WHERE state = 'PENDING' AND chatId = :chatId
            ORDER BY createdAt DESC LIMIT :cap
        )
        """
    )
    suspend fun evictOldestPending(chatId: String, cap: Int)

    @Query("DELETE FROM relay_outbox WHERE state = 'ACKNOWLEDGED'")
    suspend fun pruneAcknowledged()

    @Query("DELETE FROM relay_outbox WHERE messageUuid = :messageUuid")
    suspend fun deleteByUuid(messageUuid: String)

    @Query("DELETE FROM relay_outbox")
    suspend fun clear()
}
