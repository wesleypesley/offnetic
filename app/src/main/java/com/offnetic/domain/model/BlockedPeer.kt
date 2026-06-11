package com.offnetic.domain.model

data class BlockedPeer(
    val blockedPublicKey: String,
    val blockedAt: Long = 0,
    val displayNameSnapshot: String
) {
    fun toEntity(): com.offnetic.data.local.db.entity.BlockedPeer {
        return com.offnetic.data.local.db.entity.BlockedPeer(
            blockedPublicKey = blockedPublicKey,
            blockedAt = blockedAt,
            displayNameSnapshot = displayNameSnapshot
        )
    }

    companion object {
        fun fromEntity(entity: com.offnetic.data.local.db.entity.BlockedPeer): BlockedPeer {
            return BlockedPeer(
                blockedPublicKey = entity.blockedPublicKey,
                blockedAt = entity.blockedAt,
                displayNameSnapshot = entity.displayNameSnapshot
            )
        }
    }
}