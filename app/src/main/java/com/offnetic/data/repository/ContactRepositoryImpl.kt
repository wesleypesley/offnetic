package com.offnetic.data.repository

import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.domain.model.Contact
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao
) : ContactRepository {

    override suspend fun insert(contact: Contact): Result<Unit> {
        return try { contactDao.insert(contact.toEntity()); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to insert contact", e) }
    }

    override suspend fun update(contact: Contact): Result<Unit> {
        return try { contactDao.update(contact.toEntity()); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to update contact", e) }
    }

    override suspend fun getByPublicKey(publicKey: String): Result<Contact?> {
        return try {
            val entity = contactDao.getByPublicKey(publicKey)
            Result.Success(entity?.let { Contact.fromEntity(it) })
        } catch (e: Exception) { Result.Error("Failed to get contact", e) }
    }

    override fun getAll(): Flow<Result<List<Contact>>> {
        return contactDao.getAll()
            .map<List<com.offnetic.data.local.db.entity.Contact>, Result<List<Contact>>> { entities ->
                Result.Success(entities.map { Contact.fromEntity(it) })
            }
            .catch { emit(Result.Error("Failed to observe contacts", it)) }
    }

    override fun getVerifiedContacts(): Flow<Result<List<Contact>>> {
        return contactDao.getVerifiedContacts()
            .map<List<com.offnetic.data.local.db.entity.Contact>, Result<List<Contact>>> { entities ->
                Result.Success(entities.map { Contact.fromEntity(it) })
            }
            .catch { emit(Result.Error("Failed to observe verified contacts", it)) }
    }

    override suspend fun getVerifiedByPublicKey(publicKey: String): Result<Contact?> {
        return try {
            val entity = contactDao.getVerifiedByPublicKey(publicKey)
            Result.Success(entity?.let { Contact.fromEntity(it) })
        } catch (e: Exception) { Result.Error("Failed to get verified contact", e) }
    }

    override suspend fun delete(publicKey: String): Result<Unit> {
        return try { contactDao.delete(publicKey); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to delete contact", e) }
    }

    override suspend fun updateLastSeen(publicKey: String, timestamp: Long): Result<Unit> {
        return try { contactDao.updateLastSeen(publicKey, timestamp); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to update last seen", e) }
    }

    override suspend fun updateLastPinged(publicKey: String, timestamp: Long): Result<Unit> {
        return try { contactDao.updateLastPinged(publicKey, timestamp); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to update last pinged", e) }
    }

    override suspend fun getVerifiedCount(): Result<Int> {
        return try { Result.Success(contactDao.getVerifiedCount()) }
        catch (e: Exception) { Result.Error("Failed to get verified count", e) }
    }
}