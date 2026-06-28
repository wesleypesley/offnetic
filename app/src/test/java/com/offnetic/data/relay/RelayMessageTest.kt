package com.offnetic.data.relay

import android.app.Application
import com.offnetic.data.crypto.nostr.Hex
import com.offnetic.data.crypto.nostr.NostrEventSigner
import com.offnetic.data.crypto.nostr.NostrJson
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelayMessageTest {

    private val event = NostrEventSigner.sign(
        Hex.decode("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef"),
        1700000000L, 1059, listOf(listOf("p", "abc")), "wrapped-blob"
    )

    @Test
    fun `builds EVENT message`() {
        val arr = JSONArray(RelayMessage.event(event))
        assertEquals("EVENT", arr.getString(0))
        assertEquals(event.id, arr.getJSONObject(1).getString("id"))
    }

    @Test
    fun `builds REQ with filter`() {
        val arr = JSONArray(RelayMessage.req("sub1", RelayFilter(kinds = listOf(1059), pTags = listOf("me"))))
        assertEquals("REQ", arr.getString(0))
        assertEquals("sub1", arr.getString(1))
        assertEquals(1059, arr.getJSONObject(2).getJSONArray("kinds").getInt(0))
    }

    @Test
    fun `builds CLOSE message`() {
        val arr = JSONArray(RelayMessage.close("sub1"))
        assertEquals("CLOSE", arr.getString(0))
        assertEquals("sub1", arr.getString(1))
    }

    @Test
    fun `parses inbound EVENT preserving the signed event`() {
        val inbound = JSONArray().put("EVENT").put("sub1").put(NostrJson.toJsonObject(event)).toString()
        val msg = RelayMessage.parse(inbound)
        assertTrue(msg is RelayMessage.Event)
        msg as RelayMessage.Event
        assertEquals("sub1", msg.subscriptionId)
        assertEquals(event.id, msg.event.id)
        assertTrue(NostrEventSigner.verify(msg.event))
    }

    @Test
    fun `parses OK`() {
        val msg = RelayMessage.parse("""["OK","evt123",true,"saved"]""")
        assertTrue(msg is RelayMessage.Ok)
        msg as RelayMessage.Ok
        assertEquals("evt123", msg.eventId)
        assertTrue(msg.accepted)
        assertEquals("saved", msg.message)
    }

    @Test
    fun `parses EOSE NOTICE and CLOSED`() {
        assertTrue(RelayMessage.parse("""["EOSE","s"]""") is RelayMessage.Eose)
        assertTrue(RelayMessage.parse("""["NOTICE","hello"]""") is RelayMessage.Notice)
        assertTrue(RelayMessage.parse("""["CLOSED","s","bye"]""") is RelayMessage.Closed)
    }

    @Test
    fun `malformed or unknown input becomes Unknown`() {
        assertTrue(RelayMessage.parse("not json at all") is RelayMessage.Unknown)
        assertTrue(RelayMessage.parse("""["AUTH","challenge"]""") is RelayMessage.Unknown)
    }
}
