package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "relay_state")
data class RelayStateEntity(
    @PrimaryKey
    val id: Int = 0,
    val lastSeenAt: Long = 0
)
