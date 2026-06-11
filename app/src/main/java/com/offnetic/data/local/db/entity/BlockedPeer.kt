package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_peers")
data class BlockedPeer(
    @PrimaryKey
    val blockedPublicKey: String,
    val blockedAt: Long = System.currentTimeMillis(),
    val displayNameSnapshot: String
)