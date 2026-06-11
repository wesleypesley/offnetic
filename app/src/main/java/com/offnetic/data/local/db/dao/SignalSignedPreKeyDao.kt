package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.SignalSignedPreKeyEntity

@Dao
interface SignalSignedPreKeyDao {
    @Query("SELECT * FROM signal_signed_pre_keys WHERE signedPreKeyId = :signedPreKeyId")
    suspend fun load(signedPreKeyId: Int): SignalSignedPreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun store(entity: SignalSignedPreKeyEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM signal_signed_pre_keys WHERE signedPreKeyId = :signedPreKeyId)")
    suspend fun contains(signedPreKeyId: Int): Boolean

    @Query("DELETE FROM signal_signed_pre_keys WHERE signedPreKeyId = :signedPreKeyId")
    suspend fun remove(signedPreKeyId: Int)
}
