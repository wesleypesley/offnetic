package com.offnetic.data.repository

import com.offnetic.domain.model.Profile
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    suspend fun insert(profile: Profile): Result<Unit>
    suspend fun getByPublicKey(publicKey: String): Result<Profile?>
    fun getByPublicKeyFlow(publicKey: String): Flow<Result<Profile?>>
    fun getAll(): Flow<Result<List<Profile>>>
    suspend fun delete(publicKey: String): Result<Unit>
}