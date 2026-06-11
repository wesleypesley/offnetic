package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_signed_pre_keys")
data class SignalSignedPreKeyEntity(
    @PrimaryKey
    val signedPreKeyId: Int,
    val record: ByteArray
)
