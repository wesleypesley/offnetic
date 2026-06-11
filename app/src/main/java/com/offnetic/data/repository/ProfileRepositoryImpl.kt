package com.offnetic.data.repository

import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.domain.model.Profile
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao
) : ProfileRepository {

    override suspend fun insert(profile: Profile): Result<Unit> {
        return try { profileDao.insert(profile.toEntity()); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to insert profile", e) }
    }

    override suspend fun getByPublicKey(publicKey: String): Result<Profile?> {
        return try {
            val entity = profileDao.getByPublicKey(publicKey)
            Result.Success(entity?.let { Profile.fromEntity(it) })
        } catch (e: Exception) { Result.Error("Failed to get profile", e) }
    }

    override fun getByPublicKeyFlow(publicKey: String): Flow<Result<Profile?>> {
        return profileDao.getByPublicKeyFlow(publicKey)
            .map { Result.Success(it?.let { Profile.fromEntity(it) }) }
    }

    override fun getAll(): Flow<Result<List<Profile>>> {
        return profileDao.getAll().map { Result.Success(it.map { Profile.fromEntity(it) }) }
    }

    override suspend fun delete(publicKey: String): Result<Unit> {
        return try { profileDao.delete(publicKey); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to delete profile", e) }
    }
}