package com.offnetic.domain.model

enum class CallPhase {
    IDLE,
    OUTGOING,
    INCOMING,
    CONNECTING,
    CONNECTED,
    ENDED
}

data class CallState(
    val phase: CallPhase = CallPhase.IDLE,
    val isVideo: Boolean = false,
    val peerPublicKey: String = "",
    val peerDisplayName: String = "",
    val connectedAt: Long = 0L,
    val error: String? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraOn: Boolean = false,
    val isRemoteCameraOn: Boolean = true,
    val isFrontCamera: Boolean = true
)
