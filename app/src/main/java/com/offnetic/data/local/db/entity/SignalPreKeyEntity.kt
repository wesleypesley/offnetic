package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_pre_keys")
data class SignalPreKeyEntity(
    @PrimaryKey
    val preKeyId: Int,
    val record: ByteArray
)
