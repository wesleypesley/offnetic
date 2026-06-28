package com.offnetic.data.relay

import android.app.Application
import androidx.room.Room
import com.offnetic.data.crypto.Bech32
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.crypto.nostr.GiftWrap
import com.offnetic.data.local.db.OffneticDatabase
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.PendingRequestDao
import com.offnetic.data.local.db.entity.Identity
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.local.db.entity.RequestDirection
import com.offnetic.data.local.db.entity.RequestState
import fr.acinq.secp256k1.Secp256k1
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelayRequestManagerTest {

    private lateinit var db: OffneticDatabase
    private lateinit var pending: PendingRequestDao
    private lateinit var contacts: ContactDao
    private lateinit var signal: SignalProtocolManager
    private lateinit var controlSender: RelayControlSender
    private lateinit var manager: RelayRequestManager
    private lateinit var aliceNpub: String

    @Before
    fun setUp() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, OffneticDatabase::class.java).allowMainThreadQueries().build()
        pending = db.pendingRequestDao()
        contacts = db.contactDao()
        signal = mockk()
        controlSender = mockk(relaxed = true)
        manager = RelayRequestManager(pending, contacts, signal, controlSender, db.identityDao(), db.profileDao())

        val alicePriv = GiftWrap.generateEphemeralKey()
        aliceNpub = Bech32.npub(Secp256k1.pubkeyCreate(alicePriv).copyOfRange(1, 33))
        pending.upsert(
            PendingRequestEntity(
                requestId = aliceNpub,
                direction = RequestDirection.INBOUND,
                peerOffneticKey = "alice-offnetic",
                peerNostrKey = aliceNpub,
                displayName = "Alice",
                createdAt = 0,
                expiresAt = Long.MAX_VALUE
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `accept adds the contact, sends a bundle, and marks accepted`() = runBlocking {
        coEvery { signal.buildPreKeyBundleBytes() } returns "my-bundle".toByteArray()

        manager.acceptRequest(aliceNpub)

        assertNotNull(contacts.getByPublicKey("alice-offnetic"))
        assertEquals(RequestState.ACCEPTED, pending.getById(aliceNpub)!!.state)
        coVerify { controlSender.sendBundle(any(), any()) }
    }

    @Test
    fun `ignore marks the request expired`() = runBlocking {
        manager.ignoreRequest(aliceNpub)
        assertEquals(RequestState.EXPIRED, pending.getById(aliceNpub)!!.state)
    }

    @Test
    fun `republishOutbound re-sends a pending outbound request`() = runBlocking {
        db.identityDao().insert(Identity(publicKey = "me", encryptedPrivateKey = "x", privateKeyIv = "iv", registrationId = 1))
        every { signal.hasSession(any(), any()) } returns false
        coEvery { signal.buildPreKeyBundleBytes() } returns "bundle".toByteArray()
        val id = RelayRequestManager.OUTBOUND_PREFIX + aliceNpub
        pending.upsert(
            PendingRequestEntity(
                requestId = id,
                direction = RequestDirection.OUTBOUND,
                peerOffneticKey = "bob-offnetic",
                peerNostrKey = aliceNpub,
                displayName = "Bob",
                createdAt = 0,
                expiresAt = Long.MAX_VALUE
            )
        )

        manager.republishOutbound()

        coVerify { controlSender.sendConnectionRequest(aliceNpub, "me", any(), any()) }
        assertEquals(RequestState.PENDING, pending.getById(id)!!.state)
    }

    @Test
    fun `republishOutbound expires a past-ttl outbound request`() = runBlocking {
        db.identityDao().insert(Identity(publicKey = "me", encryptedPrivateKey = "x", privateKeyIv = "iv", registrationId = 1))
        val id = RelayRequestManager.OUTBOUND_PREFIX + aliceNpub
        pending.upsert(
            PendingRequestEntity(
                requestId = id,
                direction = RequestDirection.OUTBOUND,
                peerOffneticKey = "bob-offnetic",
                peerNostrKey = aliceNpub,
                displayName = "Bob",
                createdAt = 0,
                expiresAt = 1
            )
        )

        manager.republishOutbound()

        assertEquals(RequestState.EXPIRED, pending.getById(id)!!.state)
        coVerify(exactly = 0) { controlSender.sendConnectionRequest(any(), any(), any(), any()) }
    }

    @Test
    fun `accept processes the stored peer bundle`() = runBlocking {
        coEvery { signal.buildPreKeyBundleBytes() } returns "my-bundle".toByteArray()
        coEvery { signal.processBundleAndCreateSession(any(), any(), any()) } returns Unit
        pending.upsert(
            PendingRequestEntity(
                requestId = "req-with-bundle",
                direction = RequestDirection.INBOUND,
                peerOffneticKey = "bob-offnetic",
                peerNostrKey = aliceNpub,
                displayName = "Bob",
                createdAt = 0,
                expiresAt = Long.MAX_VALUE,
                bundle = "peer-bundle".toByteArray()
            )
        )

        manager.acceptRequest("req-with-bundle")

        coVerify { signal.processBundleAndCreateSession("bob-offnetic", any(), any()) }
        assertEquals(RequestState.ACCEPTED, pending.getById("req-with-bundle")!!.state)
    }
}
