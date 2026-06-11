package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offnetic.data.local.db.entity.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile)

    @Query("SELECT * FROM profiles WHERE publicKey = :publicKey")
    suspend fun getByPublicKey(publicKey: String): Profile?

    @Query("SELECT * FROM profiles WHERE publicKey = :publicKey")
    fun getByPublicKeyFlow(publicKey: String): Flow<Profile?>

    @Query("SELECT * FROM profiles ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Profile>>

    @Query("DELETE FROM profiles WHERE publicKey = :publicKey")
    suspend fun delete(publicKey: String)
}