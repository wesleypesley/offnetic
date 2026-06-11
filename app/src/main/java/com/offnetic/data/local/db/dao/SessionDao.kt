package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offnetic.data.local.db.entity.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session)

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): Session?

    @Query("SELECT * FROM sessions WHERE remotePublicKey = :remotePublicKey")
    suspend fun getByRemotePublicKey(remotePublicKey: String): Session?

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Session>>

    @Query("DELETE FROM sessions WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("DELETE FROM sessions WHERE remotePublicKey = :remotePublicKey")
    suspend fun deleteByRemotePublicKey(remotePublicKey: String)

    @Query("UPDATE sessions SET isShattered = 1 WHERE sessionId = :sessionId")
    suspend fun markShattered(sessionId: String)
}