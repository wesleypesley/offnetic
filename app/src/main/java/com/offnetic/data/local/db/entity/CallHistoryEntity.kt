package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_history",
    indices = [Index(value = ["peerPublicKey", "timestamp"])]
)
data class CallHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val peerPublicKey: String,
    val type: Int,
    val direction: Int,
    val timestamp: Long,
    val durationSeconds: Int = 0
) {
    companion object {
        const val TYPE_VOICE = 0
        const val TYPE_VIDEO = 1
        const val DIRECTION_OUTGOING = 0
        const val DIRECTION_INCOMING = 1
        const val DIRECTION_MISSED = 2
    }
}
