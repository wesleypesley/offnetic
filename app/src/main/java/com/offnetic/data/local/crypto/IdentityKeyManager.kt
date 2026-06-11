package com.offnetic.data.local.crypto

import com.offnetic.data.local.db.entity.Identity
import org.signal.libsignal.protocol.IdentityKeyPair

interface IdentityKeyManager {
    suspend fun generateIdentityIfNeeded(): Identity
    suspend fun getIdentity(): Identity?
    suspend fun getIdentityKeyPair(): IdentityKeyPair?
    suspend fun deleteIdentity()
}
