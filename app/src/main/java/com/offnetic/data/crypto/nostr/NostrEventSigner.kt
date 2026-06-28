package com.offnetic.data.crypto.nostr

import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom

object NostrEventSigner {

    fun sign(
        privateKey: ByteArray,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String,
        auxRand: ByteArray? = null
    ): NostrEvent {
        val pubkey = Secp256k1.pubkeyCreate(privateKey).copyOfRange(1, 33)
        val pubkeyHex = Hex.encode(pubkey)
        val id = NostrEvent.computeId(pubkeyHex, createdAt, kind, tags, content)
        val idBytes = Hex.decode(id)
        val aux = auxRand ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
        val sig = Secp256k1.signSchnorr(idBytes, privateKey, aux)
        return NostrEvent(
            id = id,
            pubkey = pubkeyHex,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = Hex.encode(sig)
        )
    }

    fun verify(event: NostrEvent): Boolean {
        return try {
            val expectedId = NostrEvent.computeId(
                event.pubkey, event.createdAt, event.kind, event.tags, event.content
            )
            if (expectedId != event.id) return false
            val idBytes = Hex.decode(event.id)
            val sig = Hex.decode(event.sig)
            val pub = Hex.decode(event.pubkey)
            Secp256k1.verifySchnorr(sig, idBytes, pub)
        } catch (_: Exception) {
            false
        }
    }
}
