package com.offnetic.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatReachabilityTest {

    @Test
    fun `local connection always wins`() {
        for (relayEligible in listOf(true, false)) {
            for (online in listOf(true, false)) {
                assertEquals(
                    ChatReachability.LOCAL,
                    ChatReachability.from(localConnected = true, relayEligible = relayEligible, online = online)
                )
            }
        }
    }

    @Test
    fun `relay eligible and online resolves to internet relay`() {
        assertEquals(
            ChatReachability.INTERNET_RELAY,
            ChatReachability.from(localConnected = false, relayEligible = true, online = true)
        )
    }

    @Test
    fun `relay eligible but offline resolves to offline`() {
        assertEquals(
            ChatReachability.OFFLINE,
            ChatReachability.from(localConnected = false, relayEligible = true, online = false)
        )
    }

    @Test
    fun `not relay eligible resolves to offline regardless of internet`() {
        assertEquals(
            ChatReachability.OFFLINE,
            ChatReachability.from(localConnected = false, relayEligible = false, online = true)
        )
        assertEquals(
            ChatReachability.OFFLINE,
            ChatReachability.from(localConnected = false, relayEligible = false, online = false)
        )
    }

    private fun peer(publicKey: String, state: ConnectionState) = PeerInfo(
        endpointId = "e",
        publicKey = publicKey,
        displayName = "d",
        isContact = true,
        connectionState = state,
        lastSeenAt = 0,
        lastPingedAt = 0
    )

    @Test
    fun `forPeer is local when contact is connected`() {
        val peers = listOf(peer("alice", ConnectionState.CONNECTED))
        assertEquals(
            ChatReachability.LOCAL,
            ChatReachability.forPeer("alice", peers, online = false, relayEligible = false)
        )
    }

    @Test
    fun `forPeer treats connecting as not local`() {
        val peers = listOf(peer("alice", ConnectionState.CONNECTING))
        assertEquals(
            ChatReachability.OFFLINE,
            ChatReachability.forPeer("alice", peers, online = false, relayEligible = false)
        )
    }

    @Test
    fun `forPeer never shows internet without relay eligibility`() {
        assertEquals(
            ChatReachability.OFFLINE,
            ChatReachability.forPeer("alice", emptyList(), online = true, relayEligible = false)
        )
    }

    @Test
    fun `forPeer is internet when relay eligible and online and not local`() {
        assertEquals(
            ChatReachability.INTERNET_RELAY,
            ChatReachability.forPeer("alice", emptyList(), online = true, relayEligible = true)
        )
    }
}
