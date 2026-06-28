package com.offnetic.data.relay

import android.app.Application
import androidx.room.Room
import com.offnetic.data.crypto.Bech32
import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.crypto.Secp256k1NostrKeyGenerator
import com.offnetic.data.crypto.nostr.GiftWrap
import com.offnetic.data.crypto.nostr.Hex
import com.offnetic.data.local.db.OffneticDatabase
import com.offnetic.data.local.db.entity.Contact
import com.offnetic.data.local.db.entity.RelayOutboxEntity
import com.offnetic.data.local.db.entity.RelayOutboxState
import fr.acinq.secp256k1.Secp256k1
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelayEndToEndLiveTest {

    @Test
    fun `message travels from Alice's outbox through real relays to Bob`() = runBlocking {
        assumeTrue("set OFFNETIC_LIVE_RELAY=1 to run", System.getenv("OFFNETIC_LIVE_RELAY") == "1")

        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, OffneticDatabase::class.java)
            .allowMainThreadQueries().build()

        // Alice (sender): real Nostr identity
        val aliceNim = NostrIdentityManager(db.nostrIdentityDao(), Secp256k1NostrKeyGenerator())
        val aliceKeys = aliceNim.generateIfNeeded()
        val aliceNostrPubHex = Hex.encode(aliceKeys.publicKey)

        // Bob (recipient): a Nostr keypair + a contact row in Alice's DB
        val bobPriv = GiftWrap.generateEphemeralKey()
        val bobPub = Secp256k1.pubkeyCreate(bobPriv).copyOfRange(1, 33)
        val bobPubHex = Hex.encode(bobPub)
        db.contactDao().insert(Contact(publicKey = "bob", displayName = "Bob", nostrPublicKey = Bech32.npub(bobPub)))

        // The message payload (stands in for the Signal ciphertext — opaque to the relay layer)
        val payload = ByteArray(96).also { SecureRandom().nextBytes(it) }
        val uuid = UUID.randomUUID().toString()
        db.relayOutboxDao().upsert(
            RelayOutboxEntity(
                messageUuid = uuid,
                chatId = "bob",
                ciphertext = payload,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + 86_400_000
            )
        )

        val alicePool = RelayPool.create(this)
        val bobPool = RelayPool.create(this)
        val processor = RelayOutboxProcessor(db.relayOutboxDao(), db.contactDao(), aliceNim, alicePool)

        val received = CompletableDeferred<Triple<ByteArray, String?, String>>()
        launch {
            bobPool.events.collect { event ->
                runCatching { GiftWrap.unwrap(bobPriv, event) }.getOrNull()?.let { res ->
                    val ct = Base64.getDecoder().decode(res.rumor.content)
                    val uuidTag = res.rumor.tags.firstOrNull { it.size >= 2 && it[0] == "u" }?.get(1)
                    if (uuidTag == uuid) received.complete(Triple(ct, uuidTag, res.senderPubkey))
                }
            }
        }

        alicePool.connect()
        bobPool.connect()
        delay(5000) // let the websockets open
        bobPool.subscribe(
            "inbox",
            RelayFilter(
                kinds = listOf(GiftWrap.KIND_GIFT_WRAP),
                pTags = listOf(bobPubHex),
                since = System.currentTimeMillis() / 1000 - 3L * 24 * 60 * 60
            )
        )
        delay(1500)

        // Publish (retry until at least one relay accepts -> row goes RELAYED)
        var attempts = 0
        while (db.relayOutboxDao().getByUuid(uuid)!!.state == RelayOutboxState.PENDING && attempts < 12) {
            processor.processPending()
            delay(1000)
            attempts++
        }

        val (gotCt, gotUuid, gotSender) = withTimeout(30_000) { received.await() }

        assertArrayEquals(payload, gotCt)
        assertEquals(uuid, gotUuid)
        assertEquals(aliceNostrPubHex, gotSender)
        assertEquals(RelayOutboxState.RELAYED, db.relayOutboxDao().getByUuid(uuid)!!.state)

        coroutineContext.cancelChildren()
        alicePool.close()
        bobPool.close()
        db.close()
    }
}
