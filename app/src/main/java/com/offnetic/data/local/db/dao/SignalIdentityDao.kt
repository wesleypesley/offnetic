package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.SignalIdentityEntity

@Dao
interface SignalIdentityDao {
    @Query("SELECT * FROM signal_trusted_identities WHERE address = :address")
    suspend fun get(address: String): SignalIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: SignalIdentityEntity)

    @Query("DELETE FROM signal_trusted_identities WHERE address = :address")
    suspend fun delete(address: String)
}
