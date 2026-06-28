package com.offnetic.data.relay

import android.app.Application
import com.offnetic.data.crypto.nostr.GiftWrap
import com.offnetic.data.crypto.nostr.Hex
import fr.acinq.secp256k1.Secp256k1
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OkHttpRelayConnectionLiveTest {

    @Test
    fun `publishes and reads back a gift wrap via relay`() = runBlocking {
        assumeTrue("set OFFNETIC_LIVE_RELAY=1 to run", System.getenv("OFFNETIC_LIVE_RELAY") == "1")

        val recipientPriv = GiftWrap.generateEphemeralKey()
        val recipientPub = Secp256k1.pubkeyCreate(recipientPriv).copyOfRange(1, 33)
        val senderPriv = GiftWrap.generateEphemeralKey()
        val marker = "offnetic-live-${System.currentTimeMillis()}"
        val giftWrap = GiftWrap.wrap(senderPriv, recipientPub, marker)

        val client = OkHttpRelayConnection.defaultClient()
        val conn = OkHttpRelayConnection("wss://relay.damus.io", client, this)
        val got = CompletableDeferred<String>()

        val collector = launch {
            conn.incoming.collect { text ->
                val msg = RelayMessage.parse(text)
                if (msg is RelayMessage.Event && msg.event.kind == GiftWrap.KIND_GIFT_WRAP) {
                    runCatching { GiftWrap.unwrap(recipientPriv, msg.event) }
                        .getOrNull()
                        ?.let { if (it.rumor.content == marker) got.complete(marker) }
                }
            }
        }

        conn.connect()
        conn.state.first { it == RelayConnectionState.CONNECTED }
        conn.send(
            RelayMessage.req(
                "live",
                RelayFilter(kinds = listOf(GiftWrap.KIND_GIFT_WRAP), pTags = listOf(Hex.encode(recipientPub)))
            )
        )
        conn.send(RelayMessage.event(giftWrap))

        val result = withTimeout(25_000) { got.await() }
        assertEquals(marker, result)

        collector.cancel()
        conn.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
