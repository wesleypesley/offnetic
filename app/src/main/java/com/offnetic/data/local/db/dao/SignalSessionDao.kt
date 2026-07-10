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

    // Exact prefix match via substr — LIKE would treat '_' in base64url public keys
    // as a single-character wildcard and could match other peers' addresses (DB16)
    @Query("DELETE FROM signal_sessions WHERE substr(address, 1, length(:name) + 1) = :name || ':'")
    suspend fun deleteAll(name: String)

    @Query("SELECT address FROM signal_sessions WHERE substr(address, 1, length(:name) + 1) = :name || ':'")
    suspend fun getSubDeviceSessions(name: String): List<String>
}
