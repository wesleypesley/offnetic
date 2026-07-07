package com.offnetic.data.relay

import android.app.Application
import androidx.room.Room
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.OffneticDatabase
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.RelayOutboxDao
import com.offnetic.data.local.db.entity.Identity
import com.offnetic.data.local.db.entity.Message
import com.offnetic.data.local.db.entity.RelayOutboxState
import com.offnetic.domain.model.MessageDeliveryState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelaySessionServiceTest {

    private lateinit var db: OffneticDatabase
    private lateinit var messages: MessageDao
    private lateinit var identityDao: IdentityDao
    private lateinit var relayOutboxDao: RelayOutboxDao
    private lateinit var signal: SignalProtocolManager
    private lateinit var processor: RelayOutboxProcessor
    private lateinit var service: RelaySessionService

    @Before
    fun setUp() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, OffneticDatabase::class.java).allowMainThreadQueries().build()
        messages = db.messageDao()
        identityDao = db.identityDao()
        relayOutboxDao = db.relayOutboxDao()
        signal = mockk()
        processor = mockk(relaxed = true)
        service = RelaySessionService(db, messages, identityDao, signal, relayOutboxDao, processor)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `onSessionReady re-encrypts saved messages and enqueues to relay outbox`() = runBlocking {
        identityDao.insert(Identity(id = 1, publicKey = "myPk", encryptedPrivateKey = "", privateKeyIv = "", registrationId = 1))
        messages.insert(
            Message(
                messageUuid = "uuid-1",
                sessionId = "bob",
                chatId = "bob",
                senderPublicKey = "myPk",
                content = "hi bob",
                type = Message.TYPE_TEXT,
                timestamp = 1700000000L,
                deliveryState = MessageDeliveryState.SAVED
            )
        )
        coEvery { signal.encryptMessage("bob", any(), any()) } returns "ciphertext-1".toByteArray(Charsets.UTF_8)

        service.onSessionReady("bob")

        val updated = messages.getById(1L)!!
        assertEquals(MessageDeliveryState.SENT_RELAY, updated.deliveryState)
        val row = relayOutboxDao.getByUuid("uuid-1")
        assertEquals("bob", row!!.chatId)
        assertEquals(RelayOutboxState.PENDING, row.state)
        coVerify { processor.processPending() }
    }

    @Test
    fun `skips non-text messages`() = runBlocking {
        identityDao.insert(Identity(id = 1, publicKey = "myPk", encryptedPrivateKey = "", privateKeyIv = "", registrationId = 1))
        messages.insert(
            Message(
                messageUuid = "uuid-2",
                sessionId = "bob",
                chatId = "bob",
                senderPublicKey = "myPk",
                content = "file.data",
                type = Message.TYPE_FILE,
                timestamp = 1700000000L,
                deliveryState = MessageDeliveryState.SAVED
            )
        )

        service.onSessionReady("bob")

        assertEquals(MessageDeliveryState.SAVED, messages.getById(1L)!!.deliveryState)
        assertNull(relayOutboxDao.getByUuid("uuid-2"))
        coVerify(exactly = 0) { signal.encryptMessage(any(), any(), any()) }
    }
}
