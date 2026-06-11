package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.Identity

@Dao
interface IdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(identity: Identity)

    @Query("SELECT * FROM identity WHERE id = 1")
    suspend fun getIdentity(): Identity?

    @Query("DELETE FROM identity WHERE id = 1")
    suspend fun deleteIdentity()
}