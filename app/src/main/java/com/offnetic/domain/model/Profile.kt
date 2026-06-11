package com.offnetic.domain.model

data class Profile(
    val publicKey: String,
    val displayName: String,
    val avatarHash: String? = null,
    val avatarBlob: String? = null,
    val timestamp: Long = 0
) {
    fun toEntity(): com.offnetic.data.local.db.entity.Profile {
        return com.offnetic.data.local.db.entity.Profile(
            publicKey = publicKey,
            displayName = displayName,
            avatarHash = avatarHash,
            avatarBlob = avatarBlob,
            timestamp = timestamp
        )
    }

    companion object {
        fun fromEntity(entity: com.offnetic.data.local.db.entity.Profile): Profile {
            return Profile(
                publicKey = entity.publicKey,
                displayName = entity.displayName,
                avatarHash = entity.avatarHash,
                avatarBlob = entity.avatarBlob,
                timestamp = entity.timestamp
            )
        }
    }
}