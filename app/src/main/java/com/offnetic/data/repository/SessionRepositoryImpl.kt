package com.offnetic.data.repository

import com.offnetic.data.local.db.dao.SessionDao
import com.offnetic.domain.model.Session
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
// Sessions table removed in migration 9→10; this impl is unused — kept to avoid breaking
// any future consumer that may reference it, but it is no longer wired into Hilt.
class SessionRepositoryImpl(
    private val sessionDao: SessionDao
) : SessionRepository {

    override suspend fun insert(session: Session): Result<Unit> {
        return try { sessionDao.insert(session.toEntity()); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to insert session", e) }
    }

    override suspend fun update(session: Session): Result<Unit> {
        return try { sessionDao.update(session.toEntity()); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to update session", e) }
    }

    override suspend fun getBySessionId(sessionId: String): Result<Session?> {
        return try {
            val entity = sessionDao.getBySessionId(sessionId)
            Result.Success(entity?.let { Session.fromEntity(it) })
        } catch (e: Exception) { Result.Error("Failed to get session by id", e) }
    }

    override suspend fun getByRemotePublicKey(remotePublicKey: String): Result<Session?> {
        return try {
            val entity = sessionDao.getByRemotePublicKey(remotePublicKey)
            Result.Success(entity?.let { Session.fromEntity(it) })
        } catch (e: Exception) { Result.Error("Failed to get session", e) }
    }

    override fun getAll(): Flow<Result<List<Session>>> {
        return sessionDao.getAll().map { Result.Success(it.map { Session.fromEntity(it) }) }
    }

    override suspend fun delete(sessionId: String): Result<Unit> {
        return try { sessionDao.delete(sessionId); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to delete session", e) }
    }

    override suspend fun deleteByRemotePublicKey(remotePublicKey: String): Result<Unit> {
        return try { sessionDao.deleteByRemotePublicKey(remotePublicKey); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to delete session", e) }
    }

    override suspend fun markShattered(sessionId: String): Result<Unit> {
        return try { sessionDao.markShattered(sessionId); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to mark session shattered", e) }
    }
}