package com.offnetic.domain.model

enum class ChatReachability {
    LOCAL,
    INTERNET_RELAY,
    OFFLINE;

    companion object {
        fun from(
            localConnected: Boolean,
            relayEligible: Boolean,
            online: Boolean
        ): ChatReachability = when {
            localConnected -> LOCAL
            relayEligible && online -> INTERNET_RELAY
            else -> OFFLINE
        }

        fun forPeer(
            contactPublicKey: String,
            peers: List<PeerInfo>,
            online: Boolean,
            relayEligible: Boolean
        ): ChatReachability {
            val localConnected = peers.any {
                it.publicKey == contactPublicKey && it.connectionState == ConnectionState.CONNECTED
            }
            return from(localConnected, relayEligible, online)
        }
    }
}
