package com.offnetic.domain.model

data class PeerInfo(
    val endpointId: String,
    val publicKey: String,
    val displayName: String,
    val isContact: Boolean,
    val isBlocked: Boolean,
    val connectionState: ConnectionState,
    val lastSeenAt: Long,
    val lastPingedAt: Long
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}
