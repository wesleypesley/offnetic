package com.offnetic.data.crypto.nostr

import fr.acinq.secp256k1.Secp256k1
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Nip44Test {

    @Test
    fun `message keys match official vector`() {
        val mk = Nip44.messageKeys(
            Hex.decode("a1a3d60f3470a8612633924e91febf96dc5366ce130f658b1f0fc652c20b3b54"),
            Hex.decode("e1e6f880560d6d149ed83dcc7e5861ee62a5ee051f7fde9975fe5d25d2a02d72")
        )
        assertEquals("f145f3bed47cb70dbeaac07f3a3fe683e822b3715edb7c4fe310829014ce7d76", Hex.encode(mk.chachaKey))
        assertEquals("c4ad129bb01180c0933a160c", Hex.encode(mk.chachaNonce))
        assertEquals("027c1db445f05e2eee864a0975b0ddef5b7110583c8c192de3732571ca5838c4", Hex.encode(mk.hmacKey))
    }

    @Test
    fun `chacha20 reverses itself`() {
        val k = ByteArray(32) { it.toByte() }
        val n = ByteArray(12) { it.toByte() }
        val data = ByteArray(40) { (it * 7).toByte() }
        assertArrayEquals(data, Nip44.chacha20(k, n, Nip44.chacha20(k, n, data)))
    }

    private fun xonly(sec: ByteArray) = Secp256k1.pubkeyCreate(sec).copyOfRange(1, 33)

    private val aSec1 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000001")
    private val aSec2 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000002")
    private val aConvKey = "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d"
    private val aNonce = Hex.decode("0000000000000000000000000000000000000000000000000000000000000001")
    private val aPlaintext = "a"
    private val aPayload = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb"

    private val cSec1 = Hex.decode("5c0c523f52a5b6fad39ed2403092df8cebc36318b39383bca6c00808626fab3a")
    private val cSec2 = Hex.decode("4b22aa260e4acb7021e32f38a6cdf4b673c6a277755bfce287e370c924dc936d")
    private val cConvKey = "3e2b52a63be47d34fe0a80e34e73d436d6963bc8f39827f327057a9986c20a45"

    @Test
    fun `conversation key matches official vectors`() {
        assertEquals(aConvKey, Hex.encode(Nip44.conversationKey(aSec1, xonly(aSec2))))
        assertEquals(cConvKey, Hex.encode(Nip44.conversationKey(cSec1, xonly(cSec2))))
    }

    @Test
    fun `conversation key is symmetric`() {
        assertEquals(
            Hex.encode(Nip44.conversationKey(aSec1, xonly(aSec2))),
            Hex.encode(Nip44.conversationKey(aSec2, xonly(aSec1)))
        )
    }

    @Test
    fun `encrypt matches official vector deterministically`() {
        assertEquals(aPayload, Nip44.encrypt(aPlaintext, Hex.decode(aConvKey), aNonce))
    }

    @Test
    fun `decrypt recovers official vector plaintext`() {
        assertEquals(aPlaintext, Nip44.decrypt(aPayload, Hex.decode(aConvKey)))
    }

    @Test
    fun `calcPaddedLen matches official vectors`() {
        assertEquals(32, Nip44.calcPaddedLen(16))
        assertEquals(32, Nip44.calcPaddedLen(32))
        assertEquals(64, Nip44.calcPaddedLen(33))
        assertEquals(64, Nip44.calcPaddedLen(37))
        assertEquals(64, Nip44.calcPaddedLen(45))
        assertEquals(64, Nip44.calcPaddedLen(49))
        assertEquals(64, Nip44.calcPaddedLen(64))
        assertEquals(96, Nip44.calcPaddedLen(65))
        assertEquals(128, Nip44.calcPaddedLen(100))
    }

    @Test
    fun `encrypt then decrypt round trips with random nonce`() {
        val convKey = Nip44.conversationKey(cSec1, xonly(cSec2))
        val message = "hello over the relay 🚀 with enough length to exercise the padding"
        assertEquals(message, Nip44.decrypt(Nip44.encrypt(message, convKey), convKey))
    }

    @Test
    fun `decrypt rejects tampered MAC`() {
        val convKey = Nip44.conversationKey(cSec1, xonly(cSec2))
        val raw = java.util.Base64.getDecoder().decode(Nip44.encrypt("secret", convKey))
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(raw)
        assertThrows(IllegalArgumentException::class.java) { Nip44.decrypt(tampered, convKey) }
    }

    @Test
    fun `decrypt rejects hash-prefixed payload`() {
        val convKey = Hex.decode("ca2527a037347b91bea0c8a30fc8d9600ffd81ec00038671e3a0f0cb0fc9f642")
        assertThrows(IllegalArgumentException::class.java) {
            Nip44.decrypt("#Atqupco0WyaOW2IGDKcshwxI9xO8HgD/P8Ddt46CbxDbrhdG8VmJdU0MIDf06CUvEvdnr1cp1fiMtlM/GrE92xAc1K5odTpCzUB+mjXgbaqtntBUbTToSUoT0ovrlPwzGjyp", convKey)
        }
    }
}
