package com.offnetic.data.relay

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class RelayConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

interface RelayConnection {
    val url: String
    val state: StateFlow<RelayConnectionState>
    // Hot stream: messages emitted while nobody collects are dropped, matching the
    // OkHttp implementation. Declared as SharedFlow so callers can't assume a cold,
    // replayable Flow (#39).
    val incoming: SharedFlow<String>
    fun connect()
    fun send(text: String): Boolean
    fun close()
}
