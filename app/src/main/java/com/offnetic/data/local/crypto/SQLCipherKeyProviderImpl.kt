package com.offnetic.data.local.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import java.security.SecureRandom

@Singleton
class SQLCipherKeyProviderImpl @Inject constructor(
    private val context: Context
) : SQLCipherKeyProvider {

    companion object {
        private const val KEYSTORE_ALIAS = "offnetic_sqlcipher_wrapping_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ENCRYPTED_KEY_PREFS = "offnetic_keystore_prefs"
        private const val ENCRYPTED_KEY_ENTRY = "encrypted_sqlcipher_key"
        private const val GCM_IV_LENGTH = 12
    }

    private val keyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    private val encryptedKeyPrefs by lazy {
        context.getSharedPreferences(ENCRYPTED_KEY_PREFS, Context.MODE_PRIVATE)
    }

    override fun getKey(): ByteArray {
        val wrappingKey = getOrCreateWrappingKey()
        val encryptedBase64 = encryptedKeyPrefs.getString(ENCRYPTED_KEY_ENTRY, null)

        if (encryptedBase64 == null) {
            val sqlCipherKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
            encryptAndStore(wrappingKey, sqlCipherKey)
            return sqlCipherKey
        }

        return decrypt(wrappingKey, encryptedBase64)
    }

    override fun deleteKey() {
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keyStore.deleteEntry(KEYSTORE_ALIAS)
        }
        encryptedKeyPrefs.edit().remove(ENCRYPTED_KEY_ENTRY).apply()
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encryptAndStore(secretKey: SecretKey, plaintext: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val combined = iv + ciphertext
        encryptedKeyPrefs.edit()
            .putString(ENCRYPTED_KEY_ENTRY, Base64.encodeToString(combined, Base64.NO_WRAP))
            .apply()
    }

    private fun decrypt(secretKey: SecretKey, encryptedBase64: String): ByteArray {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}
