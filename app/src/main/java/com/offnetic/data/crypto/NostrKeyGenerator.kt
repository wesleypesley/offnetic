package com.offnetic.data.crypto

import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class NostrKeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray
)

interface NostrKeyGenerator {
    fun generate(): NostrKeyPair
}

@Singleton
class Secp256k1NostrKeyGenerator @Inject constructor() : NostrKeyGenerator {
    override fun generate(): NostrKeyPair {
        val privateKey = ByteArray(32)
        val random = SecureRandom()
        do {
            random.nextBytes(privateKey)
        } while (!Secp256k1.secKeyVerify(privateKey))
        val publicKey = Secp256k1.pubkeyCreate(privateKey).copyOfRange(1, 33)
        return NostrKeyPair(privateKey, publicKey)
    }
}
