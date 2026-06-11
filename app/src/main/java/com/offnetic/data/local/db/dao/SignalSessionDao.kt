package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.SignalSessionEntity

@Dao
interface SignalSessionDao {
    @Query("SELECT * FROM signal_sessions WHERE address = :address")
    suspend fun load(address: String): SignalSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun store(entity: SignalSessionEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM signal_sessions WHERE address = :address)")
    suspend fun contains(address: String): Boolean

    @Query("DELETE FROM signal_sessions WHERE address = :address")
    suspend fun delete(address: String)

    @Query("DELETE FROM signal_sessions WHERE address LIKE :name || ':%'")
    suspend fun deleteAll(name: String)

    @Query("SELECT address FROM signal_sessions WHERE address LIKE :name || ':%'")
    suspend fun getSubDeviceSessions(name: String): List<String>
}
