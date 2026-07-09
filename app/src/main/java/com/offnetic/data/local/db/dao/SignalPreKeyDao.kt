package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.SignalPreKeyEntity

@Dao
interface SignalPreKeyDao {
    @Query("SELECT * FROM signal_pre_keys WHERE preKeyId = :preKeyId")
    suspend fun load(preKeyId: Int): SignalPreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun store(entity: SignalPreKeyEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM signal_pre_keys WHERE preKeyId = :preKeyId)")
    suspend fun contains(preKeyId: Int): Boolean

    @Query("DELETE FROM signal_pre_keys WHERE preKeyId = :preKeyId")
    suspend fun remove(preKeyId: Int)

    @Query("SELECT COUNT(*) FROM signal_pre_keys")
    suspend fun count(): Int

    @Query("SELECT MAX(preKeyId) FROM signal_pre_keys")
    suspend fun maxId(): Int?

    @Query("DELETE FROM signal_pre_keys WHERE preKeyId NOT IN (SELECT preKeyId FROM signal_pre_keys ORDER BY preKeyId DESC LIMIT 200)")
    suspend fun trimToLimit()
}
