package com.offnetic.data.relay

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class RelayConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

interface RelayConnection {
    val url: String
    val state: StateFlow<RelayConnectionState>
    val incoming: Flow<String>
    fun connect()
    fun send(text: String): Boolean
    fun close()
}
