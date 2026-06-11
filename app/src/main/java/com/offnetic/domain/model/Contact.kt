package com.offnetic.domain.model

data class Contact(
    val publicKey: String,
    val displayName: String,
    val avatarHash: String? = null,
    val avatarBlob: String? = null,
    val profileTimestamp: Long = 0,
    val isVerified: Boolean = false,
    val addedAt: Long = 0,
    val lastSeenAt: Long = 0,
    val lastPingedAt: Long = 0
) {
    fun toEntity(): com.offnetic.data.local.db.entity.Contact {
        return com.offnetic.data.local.db.entity.Contact(
            publicKey = publicKey,
            displayName = displayName,
            avatarHash = avatarHash,
            avatarBlob = avatarBlob,
            profileTimestamp = profileTimestamp,
            isVerified = isVerified,
            addedAt = addedAt,
            lastSeenAt = lastSeenAt,
            lastPingedAt = lastPingedAt
        )
    }

    companion object {
        fun fromEntity(entity: com.offnetic.data.local.db.entity.Contact): Contact {
            return Contact(
                publicKey = entity.publicKey,
                displayName = entity.displayName,
                avatarHash = entity.avatarHash,
                avatarBlob = entity.avatarBlob,
                profileTimestamp = entity.profileTimestamp,
                isVerified = entity.isVerified,
                addedAt = entity.addedAt,
                lastSeenAt = entity.lastSeenAt,
                lastPingedAt = entity.lastPingedAt
            )
        }
    }
}