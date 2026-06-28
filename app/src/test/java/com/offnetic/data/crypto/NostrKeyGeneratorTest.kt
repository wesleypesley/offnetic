package com.offnetic.data.crypto

import fr.acinq.secp256k1.Secp256k1
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrKeyGeneratorTest {

    private val generator = Secp256k1NostrKeyGenerator()

    @Test
    fun `generate produces 32-byte private and x-only public keys`() {
        val pair = generator.generate()
        assertEquals(32, pair.privateKey.size)
        assertEquals(32, pair.publicKey.size)
        assertTrue(Secp256k1.secKeyVerify(pair.privateKey))
    }

    @Test
    fun `public key is the x-only of the private key`() {
        val pair = generator.generate()
        val expected = Secp256k1.pubkeyCreate(pair.privateKey).copyOfRange(1, 33)
        assertArrayEquals(expected, pair.publicKey)
    }
}
