package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_sender_keys")
data class SignalSenderKeyEntity(
    @PrimaryKey
    val senderKeyId: String,
    val record: ByteArray
)
