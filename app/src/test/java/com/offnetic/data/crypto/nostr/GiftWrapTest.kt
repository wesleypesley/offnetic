package com.offnetic.data.crypto.nostr

import android.app.Application
import fr.acinq.secp256k1.Secp256k1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GiftWrapTest {

    private fun keypair(): Pair<ByteArray, ByteArray> {
        val priv = GiftWrap.generateEphemeralKey()
        val pub = Secp256k1.pubkeyCreate(priv).copyOfRange(1, 33)
        return priv to pub
    }

    @Test
    fun `unwraps official NIP-59 example gift wrap`() {
        val giftWrap = NostrEvent(
            id = "",
            pubkey = "18b1a75918f1f2c90c23da616bce317d36e348bcf5f7ba55e75949319210c87c",
            createdAt = 0L,
            kind = 1059,
            tags = emptyList(),
            content = NIP59_EXAMPLE_GIFT_WRAP_CONTENT,
            sig = ""
        )
        val recipientPriv = Hex.decode("e108399bd8424357a710b606ae0c13166d853d327e47a6e5e038197346bdbf45")

        val result = GiftWrap.unwrap(recipientPriv, giftWrap)

        assertEquals("611df01bfcf85c26ae65453b772d8f1dfd25c264621c0277e1fc1518686faef9", result.senderPubkey)
        assertEquals("Are you going to the party tonight?", result.rumor.content)
        assertEquals(1, result.rumor.kind)
        assertEquals("9dd003c6d3b73b74a85a9ab099469ce251653a7af76f523671ab828acd2a0ef9", result.rumor.id)
    }

    @Test
    fun `wrap then unwrap round trips`() {
        val (senderPriv, senderPub) = keypair()
        val (recipientPriv, recipientPub) = keypair()

        val giftWrap = GiftWrap.wrap(senderPriv, recipientPub, "hello over the relay")
        val result = GiftWrap.unwrap(recipientPriv, giftWrap)

        assertEquals(Hex.encode(senderPub), result.senderPubkey)
        assertEquals("hello over the relay", result.rumor.content)
        assertEquals(GiftWrap.KIND_DM, result.rumor.kind)
    }

    @Test
    fun `gift wrap hides sender and tags recipient`() {
        val (senderPriv, senderPub) = keypair()
        val (_, recipientPub) = keypair()

        val giftWrap = GiftWrap.wrap(senderPriv, recipientPub, "secret")

        assertEquals(GiftWrap.KIND_GIFT_WRAP, giftWrap.kind)
        assertNotEquals(Hex.encode(senderPub), giftWrap.pubkey)
        assertTrue(giftWrap.tags.any { it.size >= 2 && it[0] == "p" && it[1] == Hex.encode(recipientPub) })
    }

    @Test
    fun `wrong recipient key cannot unwrap`() {
        val (senderPriv, _) = keypair()
        val (_, recipientPub) = keypair()
        val (otherPriv, _) = keypair()

        val giftWrap = GiftWrap.wrap(senderPriv, recipientPub, "hi")

        assertThrows(IllegalArgumentException::class.java) { GiftWrap.unwrap(otherPriv, giftWrap) }
    }

    @Test
    fun `rejects impersonation when rumor author does not match seal`() {
        val (attackerPriv, _) = keypair()
        val (_, victimPub) = keypair()
        val (recipientPriv, recipientPub) = keypair()

        val maliciousRumor = GiftWrap.createRumor(Hex.encode(victimPub), 1700000000L, 14, emptyList(), "I am the victim")
        val seal = GiftWrap.buildSeal(attackerPriv, recipientPub, maliciousRumor, 1700000000L)
        val giftWrap = GiftWrap.buildGiftWrap(seal, recipientPub, GiftWrap.generateEphemeralKey(), 1700000000L)

        assertThrows(IllegalArgumentException::class.java) { GiftWrap.unwrap(recipientPriv, giftWrap) }
    }

    @Test
    fun `rejects tampered seal signature`() {
        val (senderPriv, senderPub) = keypair()
        val (recipientPriv, recipientPub) = keypair()

        val rumor = GiftWrap.createRumor(Hex.encode(senderPub), 1700000000L, 14, emptyList(), "hi")
        val seal = GiftWrap.buildSeal(senderPriv, recipientPub, rumor, 1700000000L)
        val badSeal = seal.copy(sig = seal.sig.dropLast(2) + if (seal.sig.endsWith("00")) "11" else "00")
        val giftWrap = GiftWrap.buildGiftWrap(badSeal, recipientPub, GiftWrap.generateEphemeralKey(), 1700000000L)

        assertThrows(IllegalArgumentException::class.java) { GiftWrap.unwrap(recipientPriv, giftWrap) }
    }

    private companion object {
        const val NIP59_EXAMPLE_GIFT_WRAP_CONTENT =
            "AhC3Qj/QsKJFWuf6xroiYip+2yK95qPwJjVvFujhzSguJWb/6TlPpBW0CGFwfufCs2Zyb0JeuLmZhNlnqecAAalC4ZCugB+I9ViA5pxLyFfQjs1lcE6KdX3euCHBLAnE9GL/+IzdV9vZnfJH6atVjvBkNPNzxU+OLCHO/DAPmzmMVx0SR63frRTCz6Cuth40D+VzluKu1/Fg2Q1LSst65DE7o2efTtZ4Z9j15rQAOZfE9jwMCQZt27rBBK3yVwqVEriFpg2mHXc1DDwHhDADO8eiyOTWF1ghDds/DxhMcjkIi/o+FS3gG1dG7gJHu3KkGK5UXpmgyFKt+421m5o++RMD/BylS3iazS1S93IzTLeGfMCk+7IKxuSCO06k1+DaasJJe8RE4/rmismUvwrHu/HDutZWkvOAhd4z4khZo7bJLtiCzZCZ74lZcjOB4CYtuAX2ZGpc4I1iOKkvwTuQy9BWYpkzGg3ZoSWRD6ty7U+KN+fTTmIS4CelhBTT15QVqD02JxfLF7nA6sg3UlYgtiGw61oH68lSbx16P3vwSeQQpEB5JbhofW7t9TLZIbIW/ODnI4hpwj8didtk7IMBI3Ra3uUP7ya6vptkd9TwQkd/7cOFaSJmU+BIsLpOXbirJACMn+URoDXhuEtiO6xirNtrPN8jYqpwvMUm5lMMVzGT3kMMVNBqgbj8Ln8VmqouK0DR+gRyNb8fHT0BFPwsHxDskFk5yhe5c/2VUUoKCGe0kfCcX/EsHbJLUUtlHXmTqaOJpmQnW1tZ/siPwKRl6oEsIJWTUYxPQmrM2fUpYZCuAo/29lTLHiHMlTbarFOd6J/ybIbICy2gRRH/LFSryty3Cnf6aae+A9uizFBUdCwTwffc3vCBae802+R92OL78bbqHKPbSZOXNC+6ybqziezwG+OPWHx1Qk39RYaF0aFsM4uZWrFic97WwVrH5i+/Nsf/OtwWiuH0gV/SqvN1hnkxCTF/+XNn/laWKmS3e7wFzBsG8+qwqwmO9aVbDVMhOmeUXRMkxcj4QreQkHxLkCx97euZpC7xhvYnCHarHTDeD6nVK+xzbPNtzeGzNpYoiMqxZ9bBJwMaHnEoI944Vxoodf51cMIIwpTmmRvAzI1QgrfnOLOUS7uUjQ/IZ1Qa3lY08Nqm9MAGxZ2Ou6R0/Z5z30ha/Q71q6meAs3uHQcpSuRaQeV29IASmye2A2Nif+lmbhV7w8hjFYoaLCRsdchiVyNjOEM4VmxUhX4VEvw6KoCAZ/XvO2eBF/SyNU3Of4SO"
    }
}
