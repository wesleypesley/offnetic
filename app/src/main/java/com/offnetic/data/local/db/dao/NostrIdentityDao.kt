package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.NostrIdentityEntity

@Dao
interface NostrIdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NostrIdentityEntity)

    @Query("SELECT * FROM nostr_identity WHERE id = 0")
    suspend fun get(): NostrIdentityEntity?

    @Query("DELETE FROM nostr_identity")
    suspend fun clear()
}
