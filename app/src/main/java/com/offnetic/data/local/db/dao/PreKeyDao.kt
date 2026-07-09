package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.PreKeyBundleEntity

@Dao
interface PreKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bundle: PreKeyBundleEntity)

    @Query("SELECT * FROM prekey_bundles WHERE publicKey = :publicKey")
    suspend fun getByPublicKey(publicKey: String): PreKeyBundleEntity?

    @Query("DELETE FROM prekey_bundles WHERE publicKey = :publicKey")
    suspend fun delete(publicKey: String)

    @Query("DELETE FROM prekey_bundles WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}