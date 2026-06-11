package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.util.Base64

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey
    val publicKey: String,
    val displayName: String,
    val avatarHash: String? = null,
    val avatarBlob: String? = null,
    val profileTimestamp: Long = 0,
    val isVerified: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = 0,
    val lastPingedAt: Long = 0
)