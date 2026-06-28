package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nostr_identity")
data class NostrIdentityEntity(
    @PrimaryKey
    val id: Int = 0,
    val privateKey: ByteArray,
    val publicKey: ByteArray
)
