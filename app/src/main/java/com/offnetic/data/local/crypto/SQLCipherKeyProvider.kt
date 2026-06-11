package com.offnetic.data.local.crypto

interface SQLCipherKeyProvider {
    fun getKey(): ByteArray
    fun deleteKey()
}