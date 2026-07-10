package com.offnetic.data.crypto

import com.offnetic.data.local.crypto.IdentityKeyManager
import com.offnetic.data.local.db.dao.SignalIdentityDao
import com.offnetic.data.local.db.dao.SignalPreKeyDao
import com.offnetic.data.local.db.dao.SignalSenderKeyDao
import com.offnetic.data.local.db.dao.SignalSessionDao
import com.offnetic.data.local.db.dao.SignalSignedPreKeyDao
import com.offnetic.data.local.db.entity.SignalIdentityEntity
import com.offnetic.data.local.db.entity.SignalPreKeyEntity
import com.offnetic.data.local.db.entity.SignalSenderKeyEntity
import com.offnetic.data.local.db.entity.SignalSessionEntity
import com.offnetic.data.local.db.entity.SignalSignedPreKeyEntity
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalProtocolStoreImpl @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
    private val signalPreKeyDao: SignalPreKeyDao,
    private val signalSignedPreKeyDao: SignalSignedPreKeyDao,
    private val signalSessionDao: SignalSessionDao,
    private val signalSenderKeyDao: SignalSenderKeyDao,
    private val signalIdentityDao: SignalIdentityDao
) : SignalProtocolStore {

    // Refreshed by SignalProtocolManager.initialize() (called on every service start,
    // under its init mutex) — a rotated identity is picked up on the next initialize,
    // not silently served stale for the process lifetime (DB15)
    private var cachedIdentityKeyPair: IdentityKeyPair? = null
    private var cachedRegistrationId: Int? = null

    override fun getIdentityKeyPair(): IdentityKeyPair =
        cachedIdentityKeyPair ?: throw IllegalStateException("Identity not loaded")

    override fun getLocalRegistrationId(): Int =
        cachedRegistrationId ?: throw IllegalStateException("Identity not loaded")

    fun setIdentityKeyPair(keyPair: IdentityKeyPair) { cachedIdentityKeyPair = keyPair }
    fun setRegistrationId(id: Int) { cachedRegistrationId = id }

    // --- IdentityKeyStore ---

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey?): IdentityKeyStore.IdentityChange {
        if (identityKey == null) return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED

        val existing = runBlocking { signalIdentityDao.get(address.toString()) }
        val isReplace = existing != null

        runBlocking {
            signalIdentityDao.save(SignalIdentityEntity(address.toString(), identityKey.serialize()))
        }

        return if (isReplace) IdentityKeyStore.IdentityChange.REPLACED_EXISTING
               else IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey?,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        if (identityKey == null) return false

        val stored = runBlocking { signalIdentityDao.get(address.toString()) }
        if (stored == null) return direction == IdentityKeyStore.Direction.RECEIVING

        val storedKey = IdentityKey(stored.identityKey)
        return identityKey.serialize().contentEquals(storedKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val stored = runBlocking { signalIdentityDao.get(address.toString()) } ?: return null
        return IdentityKey(stored.identityKey)
    }

    // --- PreKeyStore ---

    override fun loadPreKey(preKeyId: Int): PreKeyRecord? {
        val entity = runBlocking { signalPreKeyDao.load(preKeyId) } ?: return null
        return PreKeyRecord(entity.record)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        runBlocking { signalPreKeyDao.store(SignalPreKeyEntity(preKeyId, record.serialize())) }
    }

    override fun containsPreKey(preKeyId: Int): Boolean =
        runBlocking { signalPreKeyDao.contains(preKeyId) }

    override fun removePreKey(preKeyId: Int) {
        runBlocking { signalPreKeyDao.remove(preKeyId) }
    }

    fun trimPreKeys() {
        runBlocking { signalPreKeyDao.trimToLimit() }
    }

    // Suspend helpers for SignalProtocolManager — used to seed ID counters from
    // persisted state so restarts never reuse (and overwrite) existing key IDs.
    suspend fun maxPreKeyId(): Int? = signalPreKeyDao.maxId()

    suspend fun maxSignedPreKeyId(): Int? = signalSignedPreKeyDao.maxId()

    suspend fun maxKyberPreKeyId(): Int? = signalSenderKeyDao.kyberKeyIds()
        .mapNotNull { it.removePrefix("kyber_").toIntOrNull() }
        .maxOrNull()

    suspend fun oneTimePreKeyCount(): Int = signalPreKeyDao.count()

    suspend fun latestSignedPreKey(): SignedPreKeyRecord? =
        signalSignedPreKeyDao.maxId()?.let { id ->
            signalSignedPreKeyDao.load(id)?.let { SignedPreKeyRecord(it.record) }
        }

    // --- SignedPreKeyStore ---

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord? {
        val entity = runBlocking { signalSignedPreKeyDao.load(signedPreKeyId) } ?: return null
        return SignedPreKeyRecord(entity.record)
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        runBlocking { signalSignedPreKeyDao.store(SignalSignedPreKeyEntity(signedPreKeyId, record.serialize())) }
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        runBlocking { signalSignedPreKeyDao.contains(signedPreKeyId) }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        runBlocking { signalSignedPreKeyDao.remove(signedPreKeyId) }
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
        return mutableListOf()
    }

    // --- SessionStore ---

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? {
        val entity = runBlocking { signalSessionDao.load(address.toString()) } ?: return null
        return SessionRecord(entity.record)
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        runBlocking { signalSessionDao.store(SignalSessionEntity(address.toString(), record.serialize())) }
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        runBlocking { signalSessionDao.contains(address.toString()) }

    override fun deleteSession(address: SignalProtocolAddress) {
        runBlocking { signalSessionDao.delete(address.toString()) }
    }

    override fun deleteAllSessions(name: String) {
        runBlocking { signalSessionDao.deleteAll(name) }
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        val addresses = runBlocking { signalSessionDao.getSubDeviceSessions(name) }
        return addresses.mapNotNull { addr ->
            val parts = addr.split(":")
            if (parts.size == 2) parts[1].toIntOrNull() else null
        }.toMutableList()
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> {
        return addresses.mapNotNull { loadSession(it) }.toMutableList()
    }

    // --- SenderKeyStore ---

    override fun storeSenderKey(
        address: SignalProtocolAddress,
        distributionId: UUID,
        record: SenderKeyRecord
    ) {
        val id = "${address}_${distributionId}"
        runBlocking { signalSenderKeyDao.store(SignalSenderKeyEntity(id, record.serialize())) }
    }

    override fun loadSenderKey(
        address: SignalProtocolAddress,
        distributionId: UUID
    ): SenderKeyRecord? {
        val id = "${address}_${distributionId}"
        val entity = runBlocking { signalSenderKeyDao.load(id) } ?: return null
        return SenderKeyRecord(entity.record)
    }

    // --- KyberPreKeyStore ---

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        val entity = runBlocking { signalSenderKeyDao.load("kyber_$kyberPreKeyId") }
            ?: throw InvalidKeyIdException("No kyber pre-key with id $kyberPreKeyId")
        return KyberPreKeyRecord(entity.record)
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> {
        return mutableListOf()
    }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        runBlocking {
            signalSenderKeyDao.store(SignalSenderKeyEntity("kyber_$kyberPreKeyId", record.serialize()))
        }
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
        return runBlocking { signalSenderKeyDao.load("kyber_$kyberPreKeyId") } != null
    }

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, deviceId: Int, identityKey: ECPublicKey) {
        runBlocking { signalSenderKeyDao.remove("kyber_$kyberPreKeyId") }
    }
}
