package com.offnetic.data.local.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.offnetic.data.local.db.entity.Identity
import com.offnetic.data.local.db.dao.IdentityDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPrivateKey as JavaECPrivateKey
import java.security.interfaces.ECPublicKey as JavaECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityKeyManagerImpl @Inject constructor(
    private val context: Context,
    private val identityDao: IdentityDao
) : IdentityKeyManager {

    companion object {
        private const val WRAPPING_KEY_ALIAS = "offnetic_identity_wrapping_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val GCM_TAG_LENGTH = 128
    }

    private val keyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    private val cipherMutex = Mutex()

    override suspend fun generateIdentityIfNeeded(): Identity = withContext(Dispatchers.IO) {
        val existing = getIdentity()
        if (existing != null) {
            try {
                getIdentityKeyPair()
                return@withContext existing
            } catch (_: Exception) {
                deleteIdentity()
            }
        }

        val wrappingKey = getOrCreateWrappingKey()

        val signalKeyPair = org.signal.libsignal.protocol.ecc.ECKeyPair.generate()
        val serializedPublic = signalKeyPair.publicKey.serialize()
        val serializedPrivate = signalKeyPair.privateKey.serialize()

        val encryptedPrivate = encryptWithKeystore(wrappingKey, serializedPrivate)
        val ivBase64 = Base64.encodeToString(encryptedPrivate.first, Base64.NO_WRAP)
        val ciphertextBase64 = Base64.encodeToString(encryptedPrivate.second, Base64.NO_WRAP)
        val publicKeyBase64 = Base64.encodeToString(serializedPublic, Base64.NO_WRAP or Base64.URL_SAFE)

        val registrationId = ((System.currentTimeMillis() / 1000).toInt() % 16380 + 1)

        val identity = Identity(
            publicKey = publicKeyBase64,
            encryptedPrivateKey = ciphertextBase64,
            privateKeyIv = ivBase64,
            registrationId = registrationId
        )

        identityDao.insert(identity)
        identity
    }

    override suspend fun getIdentity(): Identity? = withContext(Dispatchers.IO) {
        identityDao.getIdentity()
    }

    override suspend fun getIdentityKeyPair(): IdentityKeyPair? = withContext(Dispatchers.IO) {
        val identity = identityDao.getIdentity() ?: return@withContext null

        val wrappingKey = getOrCreateWrappingKey()
        val iv = Base64.decode(identity.privateKeyIv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(identity.encryptedPrivateKey, Base64.NO_WRAP)

        val serializedPrivate = decryptWithKeystore(wrappingKey, iv, ciphertext)
        val serializedPublic = Base64.decode(identity.publicKey, Base64.NO_WRAP or Base64.URL_SAFE)

        val libsignalPublicKey = ECPublicKey(serializedPublic)
        val libsignalPrivateKey = ECPrivateKey(serializedPrivate)

        IdentityKeyPair(IdentityKey(libsignalPublicKey), libsignalPrivateKey)
    }

    override suspend fun deleteIdentity() = withContext(Dispatchers.IO) {
        if (keyStore.containsAlias(WRAPPING_KEY_ALIAS)) {
            keyStore.deleteEntry(WRAPPING_KEY_ALIAS)
        }
        identityDao.deleteIdentity()
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        if (!keyStore.containsAlias(WRAPPING_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(WRAPPING_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private suspend fun encryptWithKeystore(key: SecretKey, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        return cipherMutex.withLock {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            Pair(iv, ciphertext)
        }
    }

    private suspend fun decryptWithKeystore(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        return cipherMutex.withLock {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(ciphertext)
        }
    }

    private fun extractRawPoint(publicKey: JavaECPublicKey): ByteArray {
        val point = publicKey.w
        val x = toFixedLengthByteArray(point.affineX, 32)
        val y = toFixedLengthByteArray(point.affineY, 32)
        return byteArrayOf(0x04) + x + y
    }

    private fun toFixedLengthByteArray(value: BigInteger, length: Int): ByteArray {
        val bytes = ByteArray(length)
        val src = value.toByteArray()
        val offset = maxOf(0, src.size - length)
        System.arraycopy(src, offset, bytes, maxOf(0, length - src.size), minOf(src.size, length))
        return bytes
    }
}
