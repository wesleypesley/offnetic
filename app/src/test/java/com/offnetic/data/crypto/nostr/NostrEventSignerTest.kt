package com.offnetic.data.crypto.nostr

import fr.acinq.secp256k1.Secp256k1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrEventSignerTest {

    private val sk0 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000003")
    private val pk0 = "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
    private val aux0 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000000")
    private val msg0 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000000")
    private val sig0 = "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0"

    private val sk1 = Hex.decode("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef")
    private val pk1 = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private val aux1 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000001")
    private val msg1 = Hex.decode("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
    private val sig1 = "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de33418906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a"

    @Test
    fun `bip340 pubkey derivation matches vectors`() {
        assertEquals(pk0, Hex.encode(Secp256k1.pubkeyCreate(sk0).copyOfRange(1, 33)))
        assertEquals(pk1, Hex.encode(Secp256k1.pubkeyCreate(sk1).copyOfRange(1, 33)))
    }

    @Test
    fun `bip340 schnorr sign matches vectors`() {
        assertEquals(sig0, Hex.encode(Secp256k1.signSchnorr(msg0, sk0, aux0)))
        assertEquals(sig1, Hex.encode(Secp256k1.signSchnorr(msg1, sk1, aux1)))
    }

    @Test
    fun `bip340 schnorr verify true`() {
        assertTrue(Secp256k1.verifySchnorr(Hex.decode(sig0), msg0, Hex.decode(pk0)))
        assertTrue(Secp256k1.verifySchnorr(Hex.decode(sig1), msg1, Hex.decode(pk1)))
    }

    @Test
    fun `bip340 schnorr verify false`() {
        val pk = Hex.decode("dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659")
        val msg = Hex.decode("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
        val sig = Hex.decode("1fa62e331edbc21c394792d2ab1100a7b432b013df3f6ff4f99fcb33e0e1515f28890b3edb6e7189b630448b515ce4f8622a954cfe545735aaea5134fccdb2bd")
        assertFalse(Secp256k1.verifySchnorr(sig, msg, pk))
    }

    @Test
    fun `event sign then verify round trips`() {
        val event = NostrEventSigner.sign(
            privateKey = sk1,
            createdAt = 1700000000L,
            kind = 1,
            tags = listOf(listOf("p", pk0)),
            content = "hello nostr"
        )
        assertEquals(pk1, event.pubkey)
        assertEquals(64, event.id.length)
        assertEquals(128, event.sig.length)
        assertTrue(NostrEventSigner.verify(event))
    }

    @Test
    fun `tampered content fails verification`() {
        val event = NostrEventSigner.sign(sk1, 1700000000L, 1, emptyList(), "original")
        assertFalse(NostrEventSigner.verify(event.copy(content = "tampered")))
    }

    @Test
    fun `tampered signature fails verification`() {
        val event = NostrEventSigner.sign(sk1, 1700000000L, 1, emptyList(), "original")
        val badSig = event.sig.dropLast(2) + (if (event.sig.endsWith("00")) "11" else "00")
        assertFalse(NostrEventSigner.verify(event.copy(sig = badSig)))
    }
}
