package com.offnetic.data.crypto

import android.util.Base64
import com.offnetic.data.local.crypto.IdentityKeyManager
import com.offnetic.data.local.db.dao.PreKeyDao
import com.offnetic.data.local.db.entity.PreKeyBundleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalProtocolManager @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
    private val protocolStore: SignalProtocolStoreImpl,
    private val preKeyDao: PreKeyDao
) {

    private val preKeyIdCounter = AtomicInteger(1)
    private val signedPreKeyIdCounter = AtomicInteger(1)
    private val kyberPreKeyIdCounter = AtomicInteger(1)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (identityKeyManager.getIdentity() == null) {
            identityKeyManager.generateIdentityIfNeeded()
        }
        val keyPair = identityKeyManager.getIdentityKeyPair()
            ?: throw IllegalStateException("Failed to load identity keypair")
        val identity = identityKeyManager.getIdentity()
            ?: throw IllegalStateException("Failed to load identity")
        protocolStore.setIdentityKeyPair(keyPair)
        protocolStore.setRegistrationId(identity.registrationId)
    }

    suspend fun ensurePreKeys(count: Int = 100) = withContext(Dispatchers.IO) {
        val identityPair = protocolStore.getIdentityKeyPair()

        val signedKeyId = signedPreKeyIdCounter.getAndIncrement()
        val signedKeyPair = ECKeyPair.generate()
        val signature = identityPair.privateKey.calculateSignature(signedKeyPair.publicKey.serialize())
        val signedPreKeyRecord = SignedPreKeyRecord(
            signedKeyId,
            System.currentTimeMillis(),
            signedKeyPair,
            signature
        )
        protocolStore.storeSignedPreKey(signedKeyId, signedPreKeyRecord)

        val kyberKeyId = kyberPreKeyIdCounter.getAndIncrement()
        val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSignature = identityPair.privateKey.calculateSignature(kyberKeyPair.publicKey.serialize())
        val kyberPreKeyRecord = KyberPreKeyRecord(
            kyberKeyId,
            System.currentTimeMillis(),
            kyberKeyPair,
            kyberSignature
        )
        protocolStore.storeKyberPreKey(kyberKeyId, kyberPreKeyRecord)

        repeat(count) {
            val preKeyId = preKeyIdCounter.getAndIncrement()
            val keyPair = ECKeyPair.generate()
            protocolStore.storePreKey(preKeyId, PreKeyRecord(preKeyId, keyPair))
        }
        protocolStore.trimPreKeys()
    }

    suspend fun buildPreKeyBundleBytes(): ByteArray = withContext(Dispatchers.IO) {
        val identity = identityKeyManager.getIdentity()
            ?: throw IllegalStateException("No identity")
        val identityPair = protocolStore.getIdentityKeyPair()

        val preKeyId = preKeyIdCounter.getAndIncrement()
        val keyPair = ECKeyPair.generate()
        protocolStore.storePreKey(preKeyId, PreKeyRecord(preKeyId, keyPair))

        val signedPreKeyId = signedPreKeyIdCounter.getAndIncrement()
        val signedKeyPair = ECKeyPair.generate()
        val sig = identityPair.privateKey.calculateSignature(signedKeyPair.publicKey.serialize())
        val signedRecord = SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), signedKeyPair, sig)
        protocolStore.storeSignedPreKey(signedPreKeyId, signedRecord)

        val kyberPreKeyId = kyberPreKeyIdCounter.getAndIncrement()
        val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSig = identityPair.privateKey.calculateSignature(kyberKeyPair.publicKey.serialize())
        val kyberRecord = KyberPreKeyRecord(kyberPreKeyId, System.currentTimeMillis(), kyberKeyPair, kyberSig)
        protocolStore.storeKyberPreKey(kyberPreKeyId, kyberRecord)

        serializeBundle(
            identity.registrationId, 1,
            preKeyId, keyPair.publicKey,
            signedPreKeyId, signedKeyPair.publicKey, sig,
            identityPair.publicKey,
            kyberPreKeyId, kyberKeyPair.publicKey, kyberSig
        ).also { protocolStore.trimPreKeys() }
    }

    suspend fun processBundleAndCreateSession(
        peerPublicKey: String,
        bundleBytes: ByteArray,
        deviceId: Int = 1
    ) = withContext(Dispatchers.IO) {
        val bundle = deserializeBundle(bundleBytes)
        val address = SignalProtocolAddress(peerPublicKey, deviceId)

        try {
            SessionBuilder(protocolStore, address).process(bundle)
            Timber.d("Session established (PQXDH) for %s", peerPublicKey.take(8))
            saveBundleToEntity(peerPublicKey, bundle)
        } catch (e: org.signal.libsignal.protocol.UntrustedIdentityException) {
            Timber.w("Untrusted identity for %s, accepting TOFU", peerPublicKey.take(8))
            protocolStore.saveIdentity(address, bundle.identityKey)
            SessionBuilder(protocolStore, address).process(bundle)
            Timber.d("Session established (PQXDH/TOFU) for %s", peerPublicKey.take(8))
            saveBundleToEntity(peerPublicKey, bundle)
        }
    }

    suspend fun encryptMessage(
        peerPublicKey: String,
        message: ByteArray,
        deviceId: Int = 1
    ): ByteArray = withContext(Dispatchers.IO) {
        val address = SignalProtocolAddress(peerPublicKey, deviceId)
        val cipher = SessionCipher(protocolStore, address)
        val result = cipher.encrypt(message)
        Timber.d("Ratchet advanced (encrypt) for %s", peerPublicKey.take(8))
        result.serialize()
    }

    suspend fun decryptMessage(
        peerPublicKey: String,
        ciphertext: ByteArray,
        deviceId: Int = 1
    ): ByteArray? = withContext(Dispatchers.IO) {
        val address = SignalProtocolAddress(peerPublicKey, deviceId)

        try {
            val message = deserializeMessage(ciphertext)
            val cipher = SessionCipher(protocolStore, address)
            val plaintext = when (message) {
                is PreKeySignalMessage -> {
                    Timber.d("PreKeySignalMessage decrypted for %s", peerPublicKey.take(8))
                    cipher.decrypt(message)
                }
                is SignalMessage -> {
                    Timber.d("Ratchet advanced (decrypt) for %s", peerPublicKey.take(8))
                    cipher.decrypt(message)
                }
                else -> throw IllegalArgumentException("Unknown message type")
            }
            plaintext
        } catch (e: DuplicateMessageException) {
            // Already delivered — silently drop, do not surface to UI
            Timber.d("DuplicateMessage from %s — already processed, dropping", peerPublicKey.take(8))
            null
        } catch (e: NoSessionException) {
            // Session was wiped or never established — caller should trigger renegotiation
            Timber.w("NoSession for %s — ratchet state lost, session needs reset", peerPublicKey.take(8))
            null
        } catch (e: UntrustedIdentityException) {
            // Remote identity key changed since we last verified it — potential MITM
            Timber.e("UntrustedIdentity from %s — identity key mismatch, possible MITM", peerPublicKey.take(8))
            null
        } catch (e: Exception) {
            Timber.w(e, "Decryption failed for peer %s", peerPublicKey.take(8))
            null
        }
    }

    suspend fun handleShatteredSession(
        peerPublicKey: String,
        deviceId: Int = 1
    ): ByteArray = withContext(Dispatchers.IO) {
        protocolStore.deleteAllSessions(peerPublicKey)
        buildPreKeyBundleBytes()
    }

    suspend fun deleteSession(peerPublicKey: String) = withContext(Dispatchers.IO) {
        protocolStore.deleteAllSessions(peerPublicKey)
    }

    fun hasSession(peerPublicKey: String, deviceId: Int = 1): Boolean {
        val address = SignalProtocolAddress(peerPublicKey, deviceId)
        return protocolStore.containsSession(address)
    }

    private fun deserializeMessage(ciphertext: ByteArray): CiphertextMessage {
        return try {
            SignalMessage(ciphertext)
        } catch (_: Exception) {
            try {
                PreKeySignalMessage(ciphertext)
            } catch (e: Exception) {
                throw IllegalArgumentException("Unknown ciphertext format", e)
            }
        }
    }

    private fun serializeBundle(
        regId: Int, deviceId: Int,
        preKeyId: Int, preKeyPub: ECPublicKey,
        signedId: Int, signedPub: ECPublicKey, signedSig: ByteArray,
        identityKey: IdentityKey,
        kyberId: Int, kyberPub: KEMPublicKey, kyberSig: ByteArray
    ): ByteArray {
        val json = JSONObject().apply {
            put("regId", regId)
            put("deviceId", deviceId)
            put("preKeyId", preKeyId)
            put("preKeyPub", Base64.encodeToString(preKeyPub.serialize(), Base64.NO_WRAP))
            put("signedPreKeyId", signedId)
            put("signedPreKeyPub", Base64.encodeToString(signedPub.serialize(), Base64.NO_WRAP))
            put("signedPreKeySig", Base64.encodeToString(signedSig, Base64.NO_WRAP))
            put("identityKey", Base64.encodeToString(identityKey.serialize(), Base64.NO_WRAP))
            put("kyberPreKeyId", kyberId)
            put("kyberPreKeyPub", Base64.encodeToString(kyberPub.serialize(), Base64.NO_WRAP))
            put("kyberPreKeySig", Base64.encodeToString(kyberSig, Base64.NO_WRAP))
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun deserializeBundle(bytes: ByteArray): PreKeyBundle {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        return PreKeyBundle(
            json.getInt("regId"),
            json.getInt("deviceId"),
            json.getInt("preKeyId"),
            ECPublicKey(Base64.decode(json.getString("preKeyPub"), Base64.NO_WRAP)),
            json.getInt("signedPreKeyId"),
            ECPublicKey(Base64.decode(json.getString("signedPreKeyPub"), Base64.NO_WRAP)),
            Base64.decode(json.getString("signedPreKeySig"), Base64.NO_WRAP),
            IdentityKey(Base64.decode(json.getString("identityKey"), Base64.NO_WRAP)),
            json.getInt("kyberPreKeyId"),
            KEMPublicKey(Base64.decode(json.getString("kyberPreKeyPub"), Base64.NO_WRAP)),
            Base64.decode(json.getString("kyberPreKeySig"), Base64.NO_WRAP)
        )
    }

    private suspend fun saveBundleToEntity(
        peerPublicKey: String,
        bundle: PreKeyBundle
    ) {
        preKeyDao.insert(
            PreKeyBundleEntity(
                publicKey = peerPublicKey,
                registrationId = bundle.registrationId,
                preKeyId = bundle.preKeyId,
                preKeyPublic = Base64.encodeToString(bundle.preKey!!.serialize(), Base64.NO_WRAP),
                signedPreKeyId = bundle.signedPreKeyId,
                signedPreKeyPublic = Base64.encodeToString(bundle.signedPreKey.serialize(), Base64.NO_WRAP),
                signedPreKeySignature = Base64.encodeToString(bundle.signedPreKeySignature, Base64.NO_WRAP),
                identityKey = Base64.encodeToString(bundle.identityKey.serialize(), Base64.NO_WRAP),
                pqPreKeyId = bundle.kyberPreKeyId,
                pqPreKeyPublic = bundle.kyberPreKey.serialize()?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                pqSignedPreKeySignature = Base64.encodeToString(bundle.kyberPreKeySignature, Base64.NO_WRAP)
            )
        )
    }
}
