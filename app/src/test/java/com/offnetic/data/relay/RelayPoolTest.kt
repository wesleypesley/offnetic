package com.offnetic.data.relay

import android.app.Application
import com.offnetic.data.crypto.nostr.Hex
import com.offnetic.data.crypto.nostr.NostrEvent
import com.offnetic.data.crypto.nostr.NostrEventSigner
import com.offnetic.data.crypto.nostr.NostrJson
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeRelayConnection(override val url: String) : RelayConnection {
    val sent = mutableListOf<String>()
    var connected: Boolean = true
    private val _state = MutableStateFlow(RelayConnectionState.DISCONNECTED)
    override val state: StateFlow<RelayConnectionState> = _state
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incoming: Flow<String> = _incoming

    override fun connect() { _state.value = RelayConnectionState.CONNECTED }
    override fun send(text: String): Boolean { sent.add(text); return connected }
    override fun close() { _state.value = RelayConnectionState.DISCONNECTED }

    suspend fun deliver(text: String) { _incoming.emit(text) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelayPoolTest {

    private fun signedEvent(content: String): NostrEvent =
        NostrEventSigner.sign(
            Hex.decode("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef"),
            1700000000L, 1059, emptyList(), content
        )

    private fun eventFrame(subId: String, event: NostrEvent): String =
        JSONArray().put("EVENT").put(subId).put(NostrJson.toJsonObject(event)).toString()

    @Test
    fun `publish fans out EVENT to all connections`() = runBlocking {
        val a = FakeRelayConnection("a")
        val b = FakeRelayConnection("b")
        val pool = RelayPool(listOf(a, b), this)
        val event = signedEvent("hi")

        pool.publish(event)

        assertEquals(1, a.sent.size)
        assertEquals(1, b.sent.size)
        assertTrue(a.sent[0].startsWith("[\"EVENT\""))
        assertTrue(a.sent[0].contains(event.id))
        assertTrue(b.sent[0].contains(event.id))
    }

    @Test
    fun `subscribe fans out REQ to all connections`() = runBlocking {
        val a = FakeRelayConnection("a")
        val b = FakeRelayConnection("b")
        val pool = RelayPool(listOf(a, b), this)

        pool.subscribe("sub1", RelayFilter(kinds = listOf(1059)))

        assertTrue(a.sent[0].startsWith("[\"REQ\",\"sub1\""))
        assertTrue(b.sent[0].startsWith("[\"REQ\",\"sub1\""))
    }

    @Test
    fun `merges and dedupes the same event from multiple relays`() = runBlocking {
        val a = FakeRelayConnection("a")
        val b = FakeRelayConnection("b")
        val pool = RelayPool(listOf(a, b), this)
        pool.connect()
        val event = signedEvent("dup")
        val received = mutableListOf<NostrEvent>()
        launch { pool.events.collect { received.add(it) } }
        delay(100)

        a.deliver(eventFrame("s", event))
        b.deliver(eventFrame("s", event))
        delay(150)

        coroutineContext.cancelChildren()
        assertEquals(1, received.size)
        assertEquals(event.id, received[0].id)
    }

    @Test
    fun `distinct events from different relays both surface`() = runBlocking {
        val a = FakeRelayConnection("a")
        val b = FakeRelayConnection("b")
        val pool = RelayPool(listOf(a, b), this)
        pool.connect()
        val e1 = signedEvent("one")
        val e2 = signedEvent("two")
        val received = mutableListOf<NostrEvent>()
        launch { pool.events.collect { received.add(it) } }
        delay(100)

        a.deliver(eventFrame("s", e1))
        b.deliver(eventFrame("s", e2))
        delay(150)

        coroutineContext.cancelChildren()
        assertEquals(2, received.size)
        assertTrue(received.map { it.id }.containsAll(listOf(e1.id, e2.id)))
    }

    @Test
    fun `OK acks surface with relay url`() = runBlocking {
        val a = FakeRelayConnection("relay-a")
        val pool = RelayPool(listOf(a), this)
        pool.connect()
        val acks = mutableListOf<OkAck>()
        launch { pool.acks.collect { acks.add(it) } }
        delay(100)

        a.deliver("""["OK","evt1",true,"ok"]""")
        delay(100)

        coroutineContext.cancelChildren()
        assertEquals(1, acks.size)
        assertEquals("evt1", acks[0].eventId)
        assertTrue(acks[0].accepted)
        assertEquals("relay-a", acks[0].relayUrl)
    }
}
