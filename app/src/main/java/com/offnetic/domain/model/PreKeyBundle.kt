package com.offnetic.domain.model

data class PreKeyBundle(
    val publicKey: String,
    val registrationId: Int,
    val preKeyId: Int,
    val preKeyPublic: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: String,
    val signedPreKeySignature: String,
    val identityKey: String,
    val pqPreKeyId: Int? = null,
    val pqPreKeyPublic: String? = null,
    val pqSignedPreKeyId: Int? = null,
    val pqSignedPreKeyPublic: String? = null,
    val pqSignedPreKeySignature: String? = null,
    val createdAt: Long = 0
) {
    fun toEntity(): com.offnetic.data.local.db.entity.PreKeyBundleEntity {
        return com.offnetic.data.local.db.entity.PreKeyBundleEntity(
            publicKey = publicKey,
            registrationId = registrationId,
            preKeyId = preKeyId,
            preKeyPublic = preKeyPublic,
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublic = signedPreKeyPublic,
            signedPreKeySignature = signedPreKeySignature,
            identityKey = identityKey,
            pqPreKeyId = pqPreKeyId,
            pqPreKeyPublic = pqPreKeyPublic,
            pqSignedPreKeyId = pqSignedPreKeyId,
            pqSignedPreKeyPublic = pqSignedPreKeyPublic,
            pqSignedPreKeySignature = pqSignedPreKeySignature,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromEntity(entity: com.offnetic.data.local.db.entity.PreKeyBundleEntity): PreKeyBundle {
            return PreKeyBundle(
                publicKey = entity.publicKey,
                registrationId = entity.registrationId,
                preKeyId = entity.preKeyId,
                preKeyPublic = entity.preKeyPublic,
                signedPreKeyId = entity.signedPreKeyId,
                signedPreKeyPublic = entity.signedPreKeyPublic,
                signedPreKeySignature = entity.signedPreKeySignature,
                identityKey = entity.identityKey,
                pqPreKeyId = entity.pqPreKeyId,
                pqPreKeyPublic = entity.pqPreKeyPublic,
                pqSignedPreKeyId = entity.pqSignedPreKeyId,
                pqSignedPreKeyPublic = entity.pqSignedPreKeyPublic,
                pqSignedPreKeySignature = entity.pqSignedPreKeySignature,
                createdAt = entity.createdAt
            )
        }
    }
}