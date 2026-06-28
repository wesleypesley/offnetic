package com.offnetic.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Bech32Test {

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `npub matches NIP-19 vector`() {
        val pubkey = hex("7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e")
        assertEquals(
            "npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg",
            Bech32.npub(pubkey)
        )
    }

    @Test
    fun `nsec matches NIP-19 vector`() {
        val privkey = hex("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa")
        assertEquals(
            "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5",
            Bech32.nsec(privkey)
        )
    }

    @Test
    fun `decode round-trips npub back to the public key`() {
        val pubkey = hex("7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e")
        val decoded = Bech32.decode(Bech32.npub(pubkey))
        assertEquals("npub", decoded!!.first)
        assertArrayEquals(pubkey, decoded.second)
    }
}
