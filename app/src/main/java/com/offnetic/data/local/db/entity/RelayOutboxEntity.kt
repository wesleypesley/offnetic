package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RelayOutboxState {
    PENDING,
    RELAYED,
    ACKNOWLEDGED,
    FAILED
}

@Entity(tableName = "relay_outbox")
data class RelayOutboxEntity(
    @PrimaryKey
    val messageUuid: String,
    val chatId: String,
    val ciphertext: ByteArray,
    val retryCount: Int = 0,
    val maxRetries: Int = 8,
    val createdAt: Long,
    val expiresAt: Long,
    val lastAttemptAt: Long = 0,
    val state: RelayOutboxState = RelayOutboxState.PENDING
)
