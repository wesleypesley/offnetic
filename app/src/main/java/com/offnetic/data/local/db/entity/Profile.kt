package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey
    val publicKey: String,
    val displayName: String,
    val avatarHash: String? = null,
    val avatarBlob: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)