package com.offnetic.data.crypto.nostr

import fr.acinq.secp256k1.Secp256k1
import org.json.JSONObject
import java.security.SecureRandom

data class Rumor(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String
)

data class UnwrapResult(val senderPubkey: String, val rumor: Rumor)

object GiftWrap {
    const val KIND_DM = 14
    const val KIND_SEAL = 13
    const val KIND_GIFT_WRAP = 1059

    fun wrap(
        senderPriv: ByteArray,
        recipientPub: ByteArray,
        content: String,
        kind: Int = KIND_DM,
        tags: List<List<String>> = emptyList(),
        rumorCreatedAt: Long = nowSeconds(),
        ephemeralPriv: ByteArray = generateEphemeralKey(),
        sealCreatedAt: Long = randomizedTimestamp(),
        giftWrapCreatedAt: Long = randomizedTimestamp()
    ): NostrEvent {
        val senderPubHex = Hex.encode(Secp256k1.pubkeyCreate(senderPriv).copyOfRange(1, 33))
        val rumor = createRumor(senderPubHex, rumorCreatedAt, kind, tags, content)
        val seal = buildSeal(senderPriv, recipientPub, rumor, sealCreatedAt)
        return buildGiftWrap(seal, recipientPub, ephemeralPriv, giftWrapCreatedAt)
    }

    fun unwrap(recipientPriv: ByteArray, giftWrap: NostrEvent): UnwrapResult {
        require(giftWrap.kind == KIND_GIFT_WRAP) { "not a gift wrap" }
        val ephemeralPub = Hex.decode(giftWrap.pubkey)
        val sealJson = Nip44.decrypt(giftWrap.content, Nip44.conversationKey(recipientPriv, ephemeralPub))
        val seal = NostrJson.parseEvent(sealJson)
        require(seal.kind == KIND_SEAL) { "inner event is not a seal" }
        require(NostrEventSigner.verify(seal)) { "seal signature invalid" }
        val senderPub = Hex.decode(seal.pubkey)
        val rumorJson = Nip44.decrypt(seal.content, Nip44.conversationKey(recipientPriv, senderPub))
        val rumor = parseRumor(rumorJson)
        require(rumor.pubkey == seal.pubkey) { "rumor author does not match seal" }
        val expectedId = NostrEvent.computeId(rumor.pubkey, rumor.createdAt, rumor.kind, rumor.tags, rumor.content)
        require(rumor.id.isEmpty() || rumor.id == expectedId) { "rumor id mismatch" }
        return UnwrapResult(seal.pubkey, rumor.copy(id = expectedId))
    }

    internal fun createRumor(
        senderPubHex: String,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String
    ): Rumor {
        val id = NostrEvent.computeId(senderPubHex, createdAt, kind, tags, content)
        return Rumor(id, senderPubHex, createdAt, kind, tags, content)
    }

    internal fun buildSeal(senderPriv: ByteArray, recipientPub: ByteArray, rumor: Rumor, createdAt: Long): NostrEvent {
        val encrypted = Nip44.encrypt(rumorToJson(rumor), Nip44.conversationKey(senderPriv, recipientPub))
        return NostrEventSigner.sign(senderPriv, createdAt, KIND_SEAL, emptyList(), encrypted)
    }

    internal fun buildGiftWrap(seal: NostrEvent, recipientPub: ByteArray, ephemeralPriv: ByteArray, createdAt: Long): NostrEvent {
        val recipientPubHex = Hex.encode(recipientPub)
        val encrypted = Nip44.encrypt(NostrJson.eventToJson(seal), Nip44.conversationKey(ephemeralPriv, recipientPub))
        return NostrEventSigner.sign(ephemeralPriv, createdAt, KIND_GIFT_WRAP, listOf(listOf("p", recipientPubHex)), encrypted)
    }

    internal fun generateEphemeralKey(): ByteArray {
        val priv = ByteArray(32)
        val rnd = SecureRandom()
        do { rnd.nextBytes(priv) } while (!Secp256k1.secKeyVerify(priv))
        return priv
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    private fun randomizedTimestamp(): Long {
        val twoDays = 2L * 24 * 60 * 60
        return nowSeconds() - (SecureRandom().nextDouble() * twoDays).toLong()
    }

    private fun rumorToJson(rumor: Rumor): String =
        JSONObject().apply {
            put("id", rumor.id)
            put("pubkey", rumor.pubkey)
            put("created_at", rumor.createdAt)
            put("kind", rumor.kind)
            put("tags", NostrJson.tagsToJson(rumor.tags))
            put("content", rumor.content)
        }.toString()

    internal fun parseRumor(json: String): Rumor {
        val o = JSONObject(json)
        return Rumor(
            id = o.optString("id", ""),
            pubkey = o.getString("pubkey"),
            createdAt = o.getLong("created_at"),
            kind = o.getInt("kind"),
            tags = NostrJson.parseTags(o.getJSONArray("tags")),
            content = o.getString("content")
        )
    }
}
