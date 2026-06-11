package com.offnetic.data.repository

import com.offnetic.data.local.crypto.IdentityKeyManager
import com.offnetic.domain.model.Identity
import com.offnetic.domain.model.Result
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityRepositoryImpl @Inject constructor(
    private val identityKeyManager: IdentityKeyManager
) : IdentityRepository {

    override suspend fun generateIdentityIfNeeded(): Result<Identity> {
        return try {
            val identity = identityKeyManager.generateIdentityIfNeeded()
            Result.Success(Identity.fromEntity(identity))
        } catch (e: Exception) {
            Result.Error("Failed to generate identity", e)
        }
    }

    override suspend fun getIdentity(): Result<Identity?> {
        return try {
            val identity = identityKeyManager.getIdentity()
            Result.Success(identity?.let { Identity.fromEntity(it) })
        } catch (e: Exception) {
            Result.Error("Failed to get identity", e)
        }
    }

    override suspend fun deleteIdentity(): Result<Unit> {
        return try {
            identityKeyManager.deleteIdentity()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to delete identity", e)
        }
    }
}