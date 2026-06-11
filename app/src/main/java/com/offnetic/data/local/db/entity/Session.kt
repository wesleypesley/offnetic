package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey
    val sessionId: String,
    val remotePublicKey: String,
    val localIdentityKey: String,
    val remoteIdentityKey: String,
    val rootKey: String,
    val chainKeySend: String,
    val chainKeyRecv: String,
    val senderRatchetKey: String,
    val receiverRatchetKey: String,
    val messageNumberSend: Int = 0,
    val messageNumberRecv: Int = 0,
    val previousChainLength: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isShattered: Boolean = false
)