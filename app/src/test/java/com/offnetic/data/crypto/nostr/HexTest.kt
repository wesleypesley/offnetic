package com.offnetic.data.crypto.nostr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HexTest {

    @Test
    fun `encode produces lowercase hex`() {
        assertEquals("00ff1a", Hex.encode(byteArrayOf(0x00, 0xFF.toByte(), 0x1A)))
    }

    @Test
    fun `decode known hex`() {
        assertArrayEquals(
            byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            Hex.decode("deadbeef")
        )
    }

    @Test
    fun `decode reverses encode`() {
        val bytes = byteArrayOf(0, 1, 2, 127, -1, -128, 0x55, 0xAA.toByte())
        assertArrayEquals(bytes, Hex.decode(Hex.encode(bytes)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects odd length`() {
        Hex.decode("abc")
    }
}
