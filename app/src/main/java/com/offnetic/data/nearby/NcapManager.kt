package com.offnetic.data.nearby

import com.offnetic.data.crypto.NcapEnvelope
import com.offnetic.domain.model.Message
import com.offnetic.domain.model.NearbyState
import com.offnetic.domain.model.PeerInfo
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class FileMeta(
    val payloadId: Long,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val durationLabel: String? = null,
    val receivedAt: Long = System.currentTimeMillis()
)

data class CallSignal(
    val senderPublicKey: String,
    val type: NcapEnvelope.PayloadType,
    val payload: ByteArray,
    val endpointId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

interface NcapManager {
    val peers: StateFlow<List<PeerInfo>>
    val nearbyState: StateFlow<NearbyState>
    val isDiscovering: StateFlow<Boolean>
    val incomingMessages: SharedFlow<Message>
    val incomingCallSignals: SharedFlow<CallSignal>
    val incomingCallEvents: SharedFlow<String>
    val locationRequired: SharedFlow<Unit>

    fun startAdvertising(name: String)
    fun startDiscovery()
    fun stopAdvertising()
    fun stopDiscovery()
    fun stopAll()
    fun requestConnection(endpointId: String)
    fun acceptConnection(endpointId: String, publicKey: String = "")
    fun rejectConnection(endpointId: String)
    fun disconnectFromEndpoint(endpointId: String)
    fun reconnectToContact(publicKey: String)
    suspend fun sendPayload(endpointId: String, payload: ByteArray)
    suspend fun sendFile(endpointId: String, fileUri: String, fileName: String, fileSize: Long, mimeType: String, durationLabel: String? = null)
    /** Returns false if the signal could not be handed to the transport (#47). */
    suspend fun sendCallSignal(endpointId: String, payloadType: NcapEnvelope.PayloadType, payload: ByteArray): Boolean
    fun getConnectedEndpointIds(publicKey: String): List<String>
    suspend fun sendReadReceipt(contactPublicKey: String, messageUuid: String)
    fun forceRestart(name: String)
    fun setCallActive(active: Boolean)
    val isCallActive: Boolean
    suspend fun getMyPublicKey(): String
}
