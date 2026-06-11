package com.offnetic.domain.model

data class Session(
    val sessionId: String,
    val remotePublicKey: String,
    val localIdentityKey: String,
    val remoteIdentityKey: String,
    val rootKey: String,
    val chainKeySend: String,
    val chainKeyRecv: String,
    val senderRatchetKey: String,
    val receiverRatchetKey: String,
    val messageNumberSend: Int = 0,
    val messageNumberRecv: Int = 0,
    val previousChainLength: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val isShattered: Boolean = false
) {
    fun toEntity(): com.offnetic.data.local.db.entity.Session {
        return com.offnetic.data.local.db.entity.Session(
            sessionId = sessionId,
            remotePublicKey = remotePublicKey,
            localIdentityKey = localIdentityKey,
            remoteIdentityKey = remoteIdentityKey,
            rootKey = rootKey,
            chainKeySend = chainKeySend,
            chainKeyRecv = chainKeyRecv,
            senderRatchetKey = senderRatchetKey,
            receiverRatchetKey = receiverRatchetKey,
            messageNumberSend = messageNumberSend,
            messageNumberRecv = messageNumberRecv,
            previousChainLength = previousChainLength,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isShattered = isShattered
        )
    }

    companion object {
        fun fromEntity(entity: com.offnetic.data.local.db.entity.Session): Session {
            return Session(
                sessionId = entity.sessionId,
                remotePublicKey = entity.remotePublicKey,
                localIdentityKey = entity.localIdentityKey,
                remoteIdentityKey = entity.remoteIdentityKey,
                rootKey = entity.rootKey,
                chainKeySend = entity.chainKeySend,
                chainKeyRecv = entity.chainKeyRecv,
                senderRatchetKey = entity.senderRatchetKey,
                receiverRatchetKey = entity.receiverRatchetKey,
                messageNumberSend = entity.messageNumberSend,
                messageNumberRecv = entity.messageNumberRecv,
                previousChainLength = entity.previousChainLength,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isShattered = entity.isShattered
            )
        }
    }
}