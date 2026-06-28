package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface RelayStateDao {
    @Query("SELECT lastSeenAt FROM relay_state WHERE id = 0")
    suspend fun getLastSeen(): Long?

    @Query("INSERT OR REPLACE INTO relay_state (id, lastSeenAt) VALUES (0, :timestamp)")
    suspend fun setLastSeen(timestamp: Long)

    @Query("DELETE FROM relay_state")
    suspend fun clear()
}
