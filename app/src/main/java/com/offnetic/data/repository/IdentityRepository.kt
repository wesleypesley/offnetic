package com.offnetic.data.repository

import com.offnetic.domain.model.Identity
import com.offnetic.domain.model.Result

interface IdentityRepository {
    suspend fun generateIdentityIfNeeded(): Result<Identity>
    suspend fun getIdentity(): Result<Identity?>
    suspend fun deleteIdentity(): Result<Unit>
}