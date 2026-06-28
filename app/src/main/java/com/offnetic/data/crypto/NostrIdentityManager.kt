package com.offnetic.data.crypto

import com.offnetic.data.local.db.dao.NostrIdentityDao
import com.offnetic.data.local.db.entity.NostrIdentityEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NostrIdentityManager @Inject constructor(
    private val nostrIdentityDao: NostrIdentityDao,
    private val keyGenerator: NostrKeyGenerator
) {
    private val mutex = Mutex()

    suspend fun generateIfNeeded(): NostrKeyPair = mutex.withLock {
        val existing = nostrIdentityDao.get()
        if (existing != null) {
            NostrKeyPair(existing.privateKey, existing.publicKey)
        } else {
            val pair = keyGenerator.generate()
            nostrIdentityDao.upsert(
                NostrIdentityEntity(privateKey = pair.privateKey, publicKey = pair.publicKey)
            )
            pair
        }
    }

    suspend fun getKeyPair(): NostrKeyPair? =
        nostrIdentityDao.get()?.let { NostrKeyPair(it.privateKey, it.publicKey) }

    suspend fun getNpub(): String? =
        nostrIdentityDao.get()?.let { Bech32.npub(it.publicKey) }
}
