package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offnetic.data.local.db.entity.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(contact: Contact)

    @Update
    suspend fun update(contact: Contact)

    @Query("SELECT * FROM contacts WHERE publicKey = :publicKey")
    suspend fun getByPublicKey(publicKey: String): Contact?

    @Query("SELECT * FROM contacts WHERE nostrPublicKey = :nostrPublicKey LIMIT 1")
    suspend fun getByNostrPublicKey(nostrPublicKey: String): Contact?

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun getAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE isVerified = 1 ORDER BY displayName ASC")
    fun getVerifiedContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE publicKey = :publicKey AND isVerified = 1")
    suspend fun getVerifiedByPublicKey(publicKey: String): Contact?

    @Query("DELETE FROM contacts WHERE publicKey = :publicKey")
    suspend fun delete(publicKey: String)

    @Query("UPDATE contacts SET lastSeenAt = :timestamp WHERE publicKey = :publicKey")
    suspend fun updateLastSeen(publicKey: String, timestamp: Long)

    @Query("UPDATE contacts SET lastPingedAt = :timestamp WHERE publicKey = :publicKey")
    suspend fun updateLastPinged(publicKey: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM contacts WHERE isVerified = 1")
    suspend fun getVerifiedCount(): Int
}