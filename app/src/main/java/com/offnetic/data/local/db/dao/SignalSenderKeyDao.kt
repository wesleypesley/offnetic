package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.SignalSenderKeyEntity

@Dao
interface SignalSenderKeyDao {
    @Query("SELECT * FROM signal_sender_keys WHERE senderKeyId = :senderKeyId")
    suspend fun load(senderKeyId: String): SignalSenderKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun store(entity: SignalSenderKeyEntity)

    @Query("DELETE FROM signal_sender_keys WHERE senderKeyId = :senderKeyId")
    suspend fun remove(senderKeyId: String)
}
