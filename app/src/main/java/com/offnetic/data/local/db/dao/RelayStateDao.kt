package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.RelayStateEntity

@Dao
interface RelayStateDao {
    @Query("SELECT lastSeenAt FROM relay_state WHERE id = 0")
    suspend fun getLastSeen(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RelayStateEntity)

    suspend fun setLastSeen(timestamp: Long) = upsert(RelayStateEntity(id = 0, lastSeenAt = timestamp))

    @Query("DELETE FROM relay_state")
    suspend fun clear()
}
