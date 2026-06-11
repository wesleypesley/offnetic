package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prekey_bundles")
data class PreKeyBundleEntity(
    @PrimaryKey
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
    val createdAt: Long = System.currentTimeMillis()
)