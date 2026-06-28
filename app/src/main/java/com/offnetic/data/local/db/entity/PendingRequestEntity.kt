package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RequestDirection {
    INBOUND,
    OUTBOUND
}

enum class RequestState {
    PENDING,
    ACCEPTED,
    EXPIRED
}

@Entity(tableName = "pending_request")
data class PendingRequestEntity(
    @PrimaryKey
    val requestId: String,
    val direction: RequestDirection,
    val peerOffneticKey: String,
    val peerNostrKey: String,
    val displayName: String,
    val state: RequestState = RequestState.PENDING,
    val createdAt: Long,
    val expiresAt: Long,
    val bundle: ByteArray? = null
)
