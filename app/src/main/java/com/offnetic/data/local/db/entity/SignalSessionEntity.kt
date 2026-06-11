package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_sessions")
data class SignalSessionEntity(
    @PrimaryKey
    val address: String,
    val record: ByteArray
)
