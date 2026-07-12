package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.offnetic.domain.model.MessageDeliveryState

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId", "timestamp"]),
        Index(value = ["chatId", "id"]),
        Index(value = ["sessionId"]),
        Index(value = ["messageUuid"], unique = true)
    ]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageUuid: String = java.util.UUID.randomUUID().toString(),
    val sessionId: String,
    val chatId: String,
    val senderPublicKey: String,
    val content: String,
    val type: Int,
    val timestamp: Long,
    val deliveryState: MessageDeliveryState = MessageDeliveryState.SAVED,
    val isRead: Boolean = false,
    val attachmentPath: String? = null,
    val attachmentType: Int = 0,
    val replyToId: Long? = null,
    // Self-contained quote (feature #4): sender label + preview travel with the
    // message so rendering never needs a DB lookup and survives source deletion
    val quotedSender: String? = null,
    val quotedPreview: String? = null,
    // Voice note amplitude samples for waveform rendering (feature #5)
    val waveformData: ByteArray? = null
) {
    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_FILE = 1
        const val TYPE_VOICE_NOTE = 2
        const val TYPE_IMAGE = 3
        const val TYPE_VIDEO = 4
        const val TYPE_SYSTEM = 5
        const val TYPE_CANCELLED = 6
    }
}