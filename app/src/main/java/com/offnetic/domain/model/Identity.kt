package com.offnetic.domain.model

data class Identity(
    val publicKey: String,
    val encryptedPrivateKey: String,
    val privateKeyIv: String,
    val registrationId: Int,
    val createdAt: Long = 0
) {
    fun toEntity(): com.offnetic.data.local.db.entity.Identity {
        return com.offnetic.data.local.db.entity.Identity(
            publicKey = publicKey,
            encryptedPrivateKey = encryptedPrivateKey,
            privateKeyIv = privateKeyIv,
            registrationId = registrationId,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromEntity(entity: com.offnetic.data.local.db.entity.Identity): Identity {
            return Identity(
                publicKey = entity.publicKey,
                encryptedPrivateKey = entity.encryptedPrivateKey,
                privateKeyIv = entity.privateKeyIv,
                registrationId = entity.registrationId,
                createdAt = entity.createdAt
            )
        }
    }
}
