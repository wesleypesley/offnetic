package com.offnetic.data.relay

import android.app.Application
import androidx.room.Room
import com.offnetic.data.crypto.Bech32
import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.crypto.NostrKeyPair
import com.offnetic.data.crypto.Secp256k1NostrKeyGenerator
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.crypto.nostr.GiftWrap
import com.offnetic.data.crypto.nostr.NostrEvent
import com.offnetic.data.local.db.OffneticDatabase
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.PendingRequestDao
import com.offnetic.data.local.db.entity.Contact
import com.offnetic.data.local.db.entity.Message
import com.offnetic.domain.model.MessageDeliveryState
import com.offnetic.util.ActiveChatTracker
import com.offnetic.util.MessageNotificationManager
import fr.acinq.secp256k1.Secp256k1
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelayInboxHandlerTest {

    private lateinit var db: OffneticDatabase
    private lateinit var messages: MessageDao
    private lateinit var contacts: ContactDao
    private lateinit var pendingRequests: PendingRequestDao
    private lateinit var signal: SignalProtocolManager
    private lateinit var notifier: MessageNotificationManager
    private lateinit var sessionService: RelaySessionService
    private lateinit var relayRequestManager: RelayRequestManager
    private lateinit var controlSender: RelayControlSender
    private lateinit var activeChatTracker: ActiveChatTracker
    private lateinit var handler: RelayInboxHandler
    private lateinit var myKeys: NostrKeyPair
    private lateinit var senderNostrPriv: ByteArray
    private lateinit var senderNpub: String

    @Before
    fun setUp() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, OffneticDatabase::class.java).allowMainThreadQueries().build()
        messages = db.messageDao()
        contacts = db.contactDao()
        pendingRequests = db.pendingRequestDao()

        val nim = NostrIdentityManager(db.nostrIdentityDao(), Secp256k1NostrKeyGenerator())
        myKeys = nim.generateIfNeeded()

        signal = mockk()
        notifier = mockk(relaxed = true)
        sessionService = mockk(relaxed = true)
        relayRequestManager = mockk(relaxed = true)
        controlSender = mockk(relaxed = true)
        activeChatTracker = mockk(relaxed = true)
        handler = RelayInboxHandler(nim, contacts, messages, pendingRequests, signal, notifier, sessionService, relayRequestManager, controlSender, activeChatTracker, db.identityDao(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

        senderNostrPriv = GiftWrap.generateEphemeralKey()
        senderNpub = Bech32.npub(Secp256k1.pubkeyCreate(senderNostrPriv).copyOfRange(1, 33))
    }

    @After
    fun tearDown() = db.close()

    private fun giftWrapToMe(content: ByteArray, uuid: String, recipient: ByteArray = myKeys.publicKey): NostrEvent =
        GiftWrap.wrap(
            senderPriv = senderNostrPriv,
            recipientPub = recipient,
            content = Base64.getEncoder().encodeToString(content),
            kind = GiftWrap.KIND_DM,
            tags = listOf(listOf("u", uuid))
        )

    @Test
    fun `persists an inbound message from a known contact and notifies`() = runBlocking {
        contacts.insert(Contact(publicKey = "alice", displayName = "Alice", nostrPublicKey = senderNpub))
        coEvery { signal.decryptMessage("alice", any(), any()) } returns
            """{"content":"hi over relay","timestamp":1700000000}""".toByteArray()

        handler.handleGiftWrap(giftWrapToMe("ct".toByteArray(), "uuid-1"))

        val msg = messages.getByMessageUuid("uuid-1")
        assertNotNull(msg)
        assertEquals("hi over relay", msg!!.content)
        assertEquals("alice", msg.chatId)
        assertEquals("alice", msg.senderPublicKey)
        assertEquals(1700000000L, msg.timestamp)
        coVerify { notifier.notifyIfNeeded("alice") }
        verify { controlSender.sendDeliveryAck(senderNpub, "uuid-1") }
    }

    @Test
    fun `ignores a duplicate message uuid`() = runBlocking {
        contacts.insert(Contact(publicKey = "alice", displayName = "Alice", nostrPublicKey = senderNpub))
        coEvery { signal.decryptMessage(any(), any(), any()) } returns """{"content":"x","timestamp":1}""".toByteArray()
        val gw = giftWrapToMe("ct".toByteArray(), "dup-uuid")

        handler.handleGiftWrap(gw)
        handler.handleGiftWrap(gw)

        assertEquals(1, messages.getMessagesForChat("alice", 100, 0).first().size)
    }

    @Test
    fun `ignores a message from an unknown sender`() = runBlocking {
        handler.handleGiftWrap(giftWrapToMe("ct".toByteArray(), "unknown-uuid"))

        assertNull(messages.getByMessageUuid("unknown-uuid"))
        coVerify(exactly = 0) { signal.decryptMessage(any(), any(), any()) }
        coVerify(exactly = 0) { notifier.notifyIfNeeded(any()) }
    }

    @Test
    fun `drops a message when decryption fails`() = runBlocking {
        contacts.insert(Contact(publicKey = "alice", displayName = "Alice", nostrPublicKey = senderNpub))
        coEvery { signal.decryptMessage(any(), any(), any()) } returns null

        handler.handleGiftWrap(giftWrapToMe("ct".toByteArray(), "fail-uuid"))

        assertNull(messages.getByMessageUuid("fail-uuid"))
        coVerify(exactly = 0) { notifier.notifyIfNeeded(any()) }
    }

    @Test
    fun `ignores a gift wrap addressed to someone else`() = runBlocking {
        contacts.insert(Contact(publicKey = "alice", displayName = "Alice", nostrPublicKey = senderNpub))
        val someoneElse = Secp256k1.pubkeyCreate(GiftWrap.generateEphemeralKey()).copyOfRange(1, 33)

        handler.handleGiftWrap(giftWrapToMe("ct".toByteArray(), "other-uuid", recipient = someoneElse))

        assertNull(messages.getByMessageUuid("other-uuid"))
        coVerify(exactly = 0) { signal.decryptMessage(any(), any(), any()) }
    }

    private fun controlToMe(content: String, type: String): NostrEvent =
        GiftWrap.wrap(
            senderPriv = senderNostrPriv,
            recipientPub = myKeys.publicKey,
            content = content,
            kind = GiftWrap.KIND_DM,
            tags = listOf(listOf("t", type))
        )

    @Test
    fun `connection request from an unknown sender lands in the requests tray`() = runBlocking {
        val bundle = "sender-bundle".toByteArray()
        val req = JSONObject().apply {
            put("pk", "alice-offnetic")
            put("name", "Alice")
            put("bundle", Base64.getEncoder().encodeToString(bundle))
        }.toString()
        coEvery { signal.processBundleAndCreateSession("alice-offnetic", any(), any()) } returns Unit

        handler.handleGiftWrap(controlToMe(req, "req"))

        val pending = pendingRequests.getInboundPending()
        assertEquals(1, pending.size)
        assertEquals("alice-offnetic", pending[0].peerOffneticKey)
        assertEquals(senderNpub, pending[0].peerNostrKey)
        assertEquals("Alice", pending[0].displayName)
        assertArrayEquals(bundle, pending[0].bundle)
        coVerify(exactly = 0) { signal.processBundleAndCreateSession(any(), any(), any()) }
        coVerify(exactly = 0) { signal.decryptMessage(any(), any(), any()) }
    }

    @Test
    fun `connection request from a known contact is ignored`() = runBlocking {
        contacts.insert(Contact(publicKey = "alice", displayName = "Alice", nostrPublicKey = senderNpub))
        val req = JSONObject().apply {
            put("pk", "alice")
            put("name", "Alice")
            put("bundle", Base64.getEncoder().encodeToString("bundle".toByteArray()))
        }.toString()

        handler.handleGiftWrap(controlToMe(req, "req"))

        assertEquals(0, pendingRequests.getInboundPending().size)
        coVerify(exactly = 0) { signal.processBundleAndCreateSession(any(), any(), any()) }
    }

    @Test
    fun `bundle from a known contact establishes a session`() = runBlocking {
        contacts.insert(Contact(publicKey = "alice", displayName = "Alice", nostrPublicKey = senderNpub))
        coEvery { signal.processBundleAndCreateSession("alice", any(), any()) } returns Unit
        val bundle = Base64.getEncoder().encodeToString("bundle-bytes".toByteArray())

        handler.handleGiftWrap(controlToMe(bundle, "bundle"))

        coVerify { signal.processBundleAndCreateSession("alice", any(), any()) }
        coVerify { sessionService.onSessionReady("alice") }
    }

    @Test
    fun `bundle from the same peer within cooldown is rate limited`() = runBlocking {
        contacts.insert(Contact(publicKey = "alice", displayName = "Alice", nostrPublicKey = senderNpub))
        coEvery { signal.processBundleAndCreateSession("alice", any(), any()) } returns Unit
        val bundle = Base64.getEncoder().encodeToString("bundle-bytes".toByteArray())

        handler.handleGiftWrap(controlToMe(bundle, "bundle"))
        handler.handleGiftWrap(controlToMe(bundle, "bundle"))

        coVerify(exactly = 1) { signal.processBundleAndCreateSession("alice", any(), any()) }
        coVerify(exactly = 1) { sessionService.onSessionReady("alice") }
    }

    private fun receiptToMe(type: String, uuid: String): NostrEvent =
        GiftWrap.wrap(
            senderPriv = senderNostrPriv,
            recipientPub = myKeys.publicKey,
            content = "",
            kind = GiftWrap.KIND_DM,
            tags = listOf(listOf("t", type), listOf("u", uuid))
        )

    @Test
    fun `delivery ack marks the matching outgoing message DELIVERED`() = runBlocking {
        messages.insert(
            Message(
                messageUuid = "out-1",
                sessionId = "alice",
                chatId = "alice",
                senderPublicKey = "me",
                content = "hello",
                type = Message.TYPE_TEXT,
                timestamp = 1,
                deliveryState = MessageDeliveryState.SENT_RELAY,
                isRead = false
            )
        )

        handler.handleGiftWrap(receiptToMe("ack", "out-1"))

        assertEquals(MessageDeliveryState.DELIVERED, messages.getByMessageUuid("out-1")!!.deliveryState)
    }

    @Test
    fun `read receipt marks the matching outgoing message READ`() = runBlocking {
        messages.insert(
            Message(
                messageUuid = "out-2",
                sessionId = "alice",
                chatId = "alice",
                senderPublicKey = "me",
                content = "hello",
                type = Message.TYPE_TEXT,
                timestamp = 1,
                deliveryState = MessageDeliveryState.DELIVERED,
                isRead = false
            )
        )

        handler.handleGiftWrap(receiptToMe("read", "out-2"))

        assertEquals(MessageDeliveryState.READ, messages.getByMessageUuid("out-2")!!.deliveryState)
    }

    @Test
    fun `active chat sends a read receipt for an inbound relay message`() = runBlocking {
        contacts.insert(Contact(publicKey = "alice", displayName = "Alice", nostrPublicKey = senderNpub))
        coEvery { signal.decryptMessage("alice", any(), any()) } returns
            """{"content":"hi","timestamp":1}""".toByteArray()
        every { activeChatTracker.activeChatKey } returns "alice"

        handler.handleGiftWrap(giftWrapToMe("ct".toByteArray(), "active-uuid"))

        verify { controlSender.sendReadReceipt(senderNpub, "active-uuid") }
    }
}
