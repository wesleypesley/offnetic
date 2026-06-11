package com.offnetic.data.repository

import com.offnetic.domain.model.Contact
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    suspend fun insert(contact: Contact): Result<Unit>
    suspend fun update(contact: Contact): Result<Unit>
    suspend fun getByPublicKey(publicKey: String): Result<Contact?>
    fun getAll(): Flow<Result<List<Contact>>>
    fun getVerifiedContacts(): Flow<Result<List<Contact>>>
    suspend fun getVerifiedByPublicKey(publicKey: String): Result<Contact?>
    suspend fun delete(publicKey: String): Result<Unit>
    suspend fun updateLastSeen(publicKey: String, timestamp: Long): Result<Unit>
    suspend fun updateLastPinged(publicKey: String, timestamp: Long): Result<Unit>
    suspend fun getVerifiedCount(): Result<Int>
}