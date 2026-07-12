package com.offnetic.domain.model

data class Message(
    val id: Long = 0,
    val messageUuid: String = "",
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
    val quotedSender: String? = null,
    val quotedPreview: String? = null,
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

        fun fromEntity(entity: com.offnetic.data.local.db.entity.Message): Message {
            return Message(
                id = entity.id,
                sessionId = entity.sessionId,
                chatId = entity.chatId,
                senderPublicKey = entity.senderPublicKey,
                content = entity.content,
                type = entity.type,
                timestamp = entity.timestamp,
                messageUuid = entity.messageUuid,
                deliveryState = entity.deliveryState,
                isRead = entity.isRead,
                attachmentPath = entity.attachmentPath,
                attachmentType = entity.attachmentType,
                replyToId = entity.replyToId,
                quotedSender = entity.quotedSender,
                quotedPreview = entity.quotedPreview,
                waveformData = entity.waveformData
            )
        }
    }

    fun toEntity(): com.offnetic.data.local.db.entity.Message {
        return com.offnetic.data.local.db.entity.Message(
            id = id,
            sessionId = sessionId,
            chatId = chatId,
            senderPublicKey = senderPublicKey,
            content = content,
            type = type,
            timestamp = timestamp,
            messageUuid = messageUuid,
            deliveryState = deliveryState,
            isRead = isRead,
            attachmentPath = attachmentPath,
            attachmentType = attachmentType,
            replyToId = replyToId,
            quotedSender = quotedSender,
            quotedPreview = quotedPreview,
            waveformData = waveformData
        )
    }
}