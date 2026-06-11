package com.offnetic.data.repository

import com.offnetic.domain.model.Session
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun insert(session: Session): Result<Unit>
    suspend fun update(session: Session): Result<Unit>
    suspend fun getBySessionId(sessionId: String): Result<Session?>
    suspend fun getByRemotePublicKey(remotePublicKey: String): Result<Session?>
    fun getAll(): Flow<Result<List<Session>>>
    suspend fun delete(sessionId: String): Result<Unit>
    suspend fun deleteByRemotePublicKey(remotePublicKey: String): Result<Unit>
    suspend fun markShattered(sessionId: String): Result<Unit>
}