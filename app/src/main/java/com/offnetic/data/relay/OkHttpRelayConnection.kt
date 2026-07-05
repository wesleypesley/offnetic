package com.offnetic.data.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class OkHttpRelayConnection(
    override val url: String,
    private val client: OkHttpClient,
    private val scope: CoroutineScope
) : RelayConnection {

    private val _state = MutableStateFlow(RelayConnectionState.DISCONNECTED)
    override val state: StateFlow<RelayConnectionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 256)
    override val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var closed = false
    private val reconnectAttempts = AtomicInteger(0)
    private val reconnectScheduled = AtomicBoolean(false)

    override fun connect() {
        closed = false
        reconnectAttempts.set(0)
        openSocket()
    }

    private fun openSocket() {
        if (closed) return
        val current = _state.value
        if (current == RelayConnectionState.CONNECTING || current == RelayConnectionState.CONNECTED) return
        _state.value = RelayConnectionState.CONNECTING
        socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts.set(0)
            _state.value = RelayConnectionState.CONNECTED
            Timber.d("RelayConn ${url.takeLast(20)} CONNECTED")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _incoming.tryEmit(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _state.value = RelayConnectionState.DISCONNECTED
            Timber.d("RelayConn ${url.takeLast(20)} CLOSED code=$code")
            // onFailure fires for abnormal closes; onClosed fires for clean server-initiated closes.
            // Only reconnect here on non-normal closure — normal closure means we called close() ourselves.
            if (code != NORMAL_CLOSURE) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _state.value = RelayConnectionState.DISCONNECTED
            if (closed) { Timber.d("RelayConn ${url.takeLast(20)} closed, ignoring failure"); return }
            Timber.w(t, "RelayConn ${url.takeLast(20)} FAILURE")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (closed) return
        // CAS prevents double-scheduling if onClosed + onFailure both fire for the same disconnect
        if (!reconnectScheduled.compareAndSet(false, true)) return
        val attempt = reconnectAttempts.getAndIncrement()
        if (attempt >= MAX_RECONNECT_ATTEMPTS) {
            Timber.w("RelayConn ${url.takeLast(20)} giving up after $attempt attempts")
            reconnectScheduled.set(false)
            return
        }
        val backoff = min(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl min(attempt, 6))
        scope.launch {
            delay(backoff)
            reconnectScheduled.set(false)
            Timber.d("RelayConn ${url.takeLast(20)} reconnecting (attempt=$attempt delay=${backoff}ms)")
            openSocket()
        }
    }

    override fun send(text: String): Boolean = socket?.send(text) ?: false

    override fun close() {
        closed = true
        reconnectScheduled.set(false)
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        _state.value = RelayConnectionState.DISCONNECTED
    }

    companion object {
        private const val NORMAL_CLOSURE = 1000
        private const val BASE_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
