package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_trusted_identities")
data class SignalIdentityEntity(
    @PrimaryKey
    val address: String,
    val identityKey: ByteArray
)
