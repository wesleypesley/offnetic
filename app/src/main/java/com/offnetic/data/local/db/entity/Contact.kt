package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "contacts", indices = [Index(value = ["nostrPublicKey"])])
data class Contact(
    @PrimaryKey
    val publicKey: String,
    val displayName: String,
    val nostrPublicKey: String? = null,
    val avatarHash: String? = null,
    val avatarBlob: String? = null,
    val profileTimestamp: Long = 0,
    val isVerified: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = 0,
    val lastPingedAt: Long = 0
)
