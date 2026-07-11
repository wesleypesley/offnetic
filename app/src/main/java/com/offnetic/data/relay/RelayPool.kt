package com.offnetic.data.relay

import com.offnetic.data.crypto.nostr.NostrEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import timber.log.Timber

data class OkAck(val eventId: String, val accepted: Boolean, val relayUrl: String)

class RelayPool(
    private val connections: List<RelayConnection>,
    private val scope: CoroutineScope,
    private val deduper: EventDeduper = EventDeduper()
) {
    private val _events = MutableSharedFlow<NostrEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<NostrEvent> = _events.asSharedFlow()

    private val _acks = MutableSharedFlow<OkAck>(extraBufferCapacity = 64)
    val acks: SharedFlow<OkAck> = _acks.asSharedFlow()

    private val lifecycleLock = Any()
    private val jobs = mutableListOf<Job>()
    private var started = false

    fun connect() {
        synchronized(lifecycleLock) {
            if (started) return
            started = true
            connections.forEach { conn ->
                conn.connect()
                jobs += scope.launch {
                    conn.incoming.collect { text ->
                        when (val msg = RelayMessage.parse(text)) {
                            is RelayMessage.Event ->
                                if (deduper.markSeen(msg.event.id)) _events.emit(msg.event)
                            is RelayMessage.Ok ->
                                _acks.emit(OkAck(msg.eventId, msg.accepted, conn.url))
                            // Protocol-level feedback was silently discarded — relays use
                            // NOTICE/CLOSED to report rejections and errors (#38)
                            is RelayMessage.Notice ->
                                Timber.w("Relay NOTICE from ${conn.url}: ${msg.message}")
                            is RelayMessage.Closed ->
                                Timber.w("Relay CLOSED sub=${msg.subscriptionId} from ${conn.url}: ${msg.message}")
                            is RelayMessage.Eose ->
                                Timber.d("Relay EOSE sub=${msg.subscriptionId} from ${conn.url}")
                            is RelayMessage.Unknown ->
                                Timber.w("Relay unparseable message from ${conn.url}: ${msg.raw.take(120)}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Suspends until at least one relay reports CONNECTED, or [timeoutMs] elapses.
     * Returns false on timeout so callers can log instead of subscribing blind (#60).
     */
    suspend fun awaitConnected(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            combine(connections.map { it.state }) { states ->
                states.any { it == RelayConnectionState.CONNECTED }
            }.first { it }
        } ?: false

    fun publish(event: NostrEvent): Int {
        val text = RelayMessage.event(event)
        val sent = connections.count { it.send(text) }
        Timber.d("RelayPool published event=${event.id.take(8)} relays=$sent/${connections.size}")
        return sent
    }

    fun subscribe(subscriptionId: String, filter: RelayFilter) {
        val text = RelayMessage.req(subscriptionId, filter)
        connections.forEach { it.send(text) }
    }

    fun closeSubscription(subscriptionId: String) {
        val text = RelayMessage.close(subscriptionId)
        connections.forEach { it.send(text) }
    }

    fun close() {
        synchronized(lifecycleLock) {
            connections.forEach { it.close() }
            jobs.forEach { it.cancel() }
            jobs.clear()
            started = false
        }
    }

    companion object {
        val DEFAULT_RELAYS = com.offnetic.config.OffneticConfig.DEFAULT_RELAYS

        fun create(
            scope: CoroutineScope,
            urls: List<String> = DEFAULT_RELAYS,
            client: OkHttpClient = OkHttpRelayConnection.defaultClient()
        ): RelayPool = RelayPool(urls.map { OkHttpRelayConnection(it, client, scope) }, scope)
    }
}
