package com.offnetic.data.relay

import android.app.Application
import androidx.room.Room
import com.offnetic.data.crypto.Bech32
import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.crypto.Secp256k1NostrKeyGenerator
import com.offnetic.data.crypto.nostr.GiftWrap
import com.offnetic.data.local.db.OffneticDatabase
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.RelayOutboxDao
import com.offnetic.data.local.db.entity.Contact
import com.offnetic.data.local.db.entity.RelayOutboxEntity
import com.offnetic.data.local.db.entity.RelayOutboxState
import fr.acinq.secp256k1.Secp256k1
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelayOutboxProcessorTest {

    private lateinit var db: OffneticDatabase
    private lateinit var outbox: RelayOutboxDao
    private lateinit var contacts: ContactDao
    private lateinit var fake: FakeRelayConnection
    private lateinit var processor: RelayOutboxProcessor

    @Before
    fun setUp() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, OffneticDatabase::class.java)
            .allowMainThreadQueries().build()
        outbox = db.relayOutboxDao()
        contacts = db.contactDao()

        val nostrIdentityManager = NostrIdentityManager(db.nostrIdentityDao(), Secp256k1NostrKeyGenerator())
        nostrIdentityManager.generateIfNeeded()

        val recipientPriv = GiftWrap.generateEphemeralKey()
        val recipientNpub = Bech32.npub(Secp256k1.pubkeyCreate(recipientPriv).copyOfRange(1, 33))
        contacts.insert(Contact(publicKey = "peer1", displayName = "Peer 1", nostrPublicKey = recipientNpub))
        contacts.insert(Contact(publicKey = "peer-no-nostr", displayName = "No Nostr"))

        fake = FakeRelayConnection("wss://test")
        val pool = RelayPool(listOf(fake), this)
        processor = RelayOutboxProcessor(outbox, contacts, nostrIdentityManager, pool)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(uuid: String, chatId: String, expiresAt: Long): RelayOutboxEntity =
        RelayOutboxEntity(
            messageUuid = uuid,
            chatId = chatId,
            ciphertext = byteArrayOf(1, 2, 3, 4),
            createdAt = System.currentTimeMillis(),
            expiresAt = expiresAt
        )

    private fun publishedEventId(): String =
        JSONArray(fake.sent.single()).getJSONObject(1).getString("id")

    @Test
    fun `processPending wraps and publishes a pending row`() = runBlocking {
        outbox.upsert(row("m1", "peer1", System.currentTimeMillis() + 86_400_000))

        processor.processPending()

        assertEquals(1, fake.sent.size)
        val arr = JSONArray(fake.sent.single())
        assertEquals("EVENT", arr.getString(0))
        assertEquals(1059, arr.getJSONObject(1).getInt("kind"))
        val updated = outbox.getByUuid("m1")
        assertNotNull(updated)
        assertEquals(RelayOutboxState.RELAYED, updated!!.state)
        assertEquals(1, updated.retryCount)
    }

    @Test
    fun `accepted ack marks acknowledged and prunes the row`() = runBlocking {
        outbox.upsert(row("m1", "peer1", System.currentTimeMillis() + 86_400_000))
        processor.processPending()
        val eventId = publishedEventId()

        processor.handleAck(OkAck(eventId, accepted = true, relayUrl = "wss://test"))

        assertNull(outbox.getByUuid("m1"))
    }

    @Test
    fun `rejected ack leaves the row relayed`() = runBlocking {
        outbox.upsert(row("m1", "peer1", System.currentTimeMillis() + 86_400_000))
        processor.processPending()
        val eventId = publishedEventId()

        processor.handleAck(OkAck(eventId, accepted = false, relayUrl = "wss://test"))

        assertEquals(RelayOutboxState.RELAYED, outbox.getByUuid("m1")!!.state)
    }

    @Test
    fun `expired row is marked FAILED and never published`() = runBlocking {
        outbox.upsert(row("m1", "peer1", System.currentTimeMillis() - 1000))

        processor.processPending()

        assertEquals(0, fake.sent.size)
        assertEquals(RelayOutboxState.FAILED, outbox.getByUuid("m1")!!.state)
    }

    @Test
    fun `row for contact without a nostr key is marked FAILED`() = runBlocking {
        outbox.upsert(row("m1", "peer-no-nostr", System.currentTimeMillis() + 86_400_000))

        processor.processPending()

        assertEquals(0, fake.sent.size)
        assertEquals(RelayOutboxState.FAILED, outbox.getByUuid("m1")!!.state)
    }

    @Test
    fun `no connected relay leaves the row PENDING without burning retries`() = runBlocking {
        fake.connected = false
        outbox.upsert(row("m1", "peer1", System.currentTimeMillis() + 86_400_000))

        processor.processPending()

        val r = outbox.getByUuid("m1")!!
        assertEquals(RelayOutboxState.PENDING, r.state)
        assertEquals(0, r.retryCount)
    }
}
