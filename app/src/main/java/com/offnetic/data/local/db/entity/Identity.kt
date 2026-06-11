package com.offnetic.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identity")
data class Identity(
    @PrimaryKey
    val id: Int = 1,
    val publicKey: String,
    val encryptedPrivateKey: String,
    val privateKeyIv: String,
    val registrationId: Int,
    val createdAt: Long = System.currentTimeMillis()
)
