package com.offnetic.data.local.db

import android.app.Application
import androidx.room.Room
import com.offnetic.data.local.db.dao.PendingRequestDao
import com.offnetic.data.local.db.dao.RelayOutboxDao
import com.offnetic.data.local.db.dao.RelayStateDao
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.local.db.entity.RelayOutboxEntity
import com.offnetic.data.local.db.entity.RelayOutboxState
import com.offnetic.data.local.db.entity.RequestDirection
import com.offnetic.data.local.db.entity.RequestState
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
class RelayTablesTest {

    private lateinit var db: OffneticDatabase
    private lateinit var outbox: RelayOutboxDao
    private lateinit var requests: PendingRequestDao
    private lateinit var relayState: RelayStateDao

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, OffneticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        outbox = db.relayOutboxDao()
        requests = db.pendingRequestDao()
        relayState = db.relayStateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun outboxEntry(uuid: String, chatId: String = "alice", createdAt: Long = 0L) =
        RelayOutboxEntity(
            messageUuid = uuid,
            chatId = chatId,
            ciphertext = byteArrayOf(1, 2, 3),
            createdAt = createdAt,
            expiresAt = createdAt + 1000
        )

    @Test
    fun `outbox transitions pending to relayed to acknowledged`() = runBlocking {
        outbox.upsert(outboxEntry("m1"))
        assertEquals(RelayOutboxState.PENDING, outbox.getByUuid("m1")!!.state)
        outbox.updateState("m1", RelayOutboxState.RELAYED)
        assertEquals(RelayOutboxState.RELAYED, outbox.getByUuid("m1")!!.state)
        outbox.updateState("m1", RelayOutboxState.ACKNOWLEDGED)
        assertEquals(RelayOutboxState.ACKNOWLEDGED, outbox.getByUuid("m1")!!.state)
    }

    @Test
    fun `prune deletes only acknowledged`() = runBlocking {
        outbox.upsert(outboxEntry("m1"))
        outbox.upsert(outboxEntry("m2"))
        outbox.updateState("m1", RelayOutboxState.ACKNOWLEDGED)
        outbox.pruneAcknowledged()
        assertNull(outbox.getByUuid("m1"))
        assertEquals(RelayOutboxState.PENDING, outbox.getByUuid("m2")!!.state)
    }

    @Test
    fun `eviction keeps newest cap pending per chat`() = runBlocking {
        for (i in 1..5) outbox.upsert(outboxEntry("m$i", createdAt = i.toLong()))
        outbox.evictOldestPending("alice", cap = 3)
        assertEquals(3, outbox.countForChat("alice"))
        assertNull(outbox.getByUuid("m1"))
        assertNull(outbox.getByUuid("m2"))
        assertEquals(RelayOutboxState.PENDING, outbox.getByUuid("m5")!!.state)
    }

    @Test
    fun `eviction never drops relayed entries`() = runBlocking {
        for (i in 1..4) outbox.upsert(outboxEntry("m$i", createdAt = i.toLong()))
        outbox.updateState("m1", RelayOutboxState.RELAYED)
        outbox.evictOldestPending("alice", cap = 2)
        assertEquals(RelayOutboxState.RELAYED, outbox.getByUuid("m1")!!.state)
        assertNull(outbox.getByUuid("m2"))
        assertEquals(3, outbox.countForChat("alice"))
    }

    @Test
    fun `inbound request appears then clears on accept`() = runBlocking {
        requests.upsert(
            PendingRequestEntity(
                requestId = "r1",
                direction = RequestDirection.INBOUND,
                peerOffneticKey = "bob",
                peerNostrKey = "npub",
                displayName = "Bob",
                createdAt = 0,
                expiresAt = 1000
            )
        )
        assertEquals(1, requests.getInboundPending().size)
        requests.updateState("r1", RequestState.ACCEPTED)
        assertEquals(0, requests.getInboundPending().size)
        assertEquals(RequestState.ACCEPTED, requests.getById("r1")!!.state)
    }

    @Test
    fun `expired requests are deleted`() = runBlocking {
        requests.upsert(
            PendingRequestEntity(
                requestId = "r1",
                direction = RequestDirection.OUTBOUND,
                peerOffneticKey = "bob",
                peerNostrKey = "npub",
                displayName = "Bob",
                createdAt = 0,
                expiresAt = 100
            )
        )
        requests.deleteExpired(now = 200)
        assertNull(requests.getById("r1"))
    }

    @Test
    fun `relay state cursor round-trips`() = runBlocking {
        assertNull(relayState.getLastSeen())
        relayState.setLastSeen(12345L)
        assertEquals(12345L, relayState.getLastSeen())
        relayState.setLastSeen(67890L)
        assertEquals(67890L, relayState.getLastSeen())
    }
}
