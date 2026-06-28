package com.offnetic.data.relay

import com.offnetic.data.crypto.Bech32
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.PendingRequestDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.db.entity.Contact
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.local.db.entity.RequestState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayRequestManager @Inject constructor(
    private val pendingRequestDao: PendingRequestDao,
    private val contactDao: ContactDao,
    private val signalProtocolManager: SignalProtocolManager,
    private val controlSender: RelayControlSender,
    private val identityDao: IdentityDao,
    private val profileDao: ProfileDao
) {
    private val _inboundCount = MutableStateFlow(0)
    val inboundCount: StateFlow<Int> = _inboundCount.asStateFlow()

    private data class Attempt(val lastMs: Long, val count: Int)
    private val outboundAttempts = ConcurrentHashMap<String, Attempt>()

    suspend fun pendingRequests(): List<PendingRequestEntity> = pendingRequestDao.getInboundPending()

    suspend fun refreshCount() {
        _inboundCount.value = pendingRequestDao.getInboundPending().size
    }

    suspend fun acceptRequest(requestId: String) {
        val request = pendingRequestDao.getById(requestId) ?: return
        if (decodeNpub(request.peerNostrKey) == null) return

        contactDao.insertIfNotExists(
            Contact(
                publicKey = request.peerOffneticKey,
                displayName = request.displayName,
                nostrPublicKey = request.peerNostrKey,
                isVerified = false,
                addedAt = System.currentTimeMillis()
            )
        )

        request.bundle?.let { peerBundle ->
            runCatching {
                signalProtocolManager.processBundleAndCreateSession(request.peerOffneticKey, peerBundle)
            }
        }

        val bundle = signalProtocolManager.buildPreKeyBundleBytes()
        controlSender.sendBundle(request.peerNostrKey, bundle)
        pendingRequestDao.updateState(requestId, RequestState.ACCEPTED)
        refreshCount()
    }

    suspend fun ignoreRequest(requestId: String) {
        pendingRequestDao.updateState(requestId, RequestState.EXPIRED)
        refreshCount()
    }

    suspend fun republishOutbound() {
        val now = System.currentTimeMillis()
        val identity = identityDao.getIdentity() ?: return
        val myPk = identity.publicKey
        val myName = profileDao.getByPublicKey(myPk)?.displayName ?: (myPk.take(12) + "...")
        for (req in pendingRequestDao.getOutboundPending()) {
            if (now >= req.expiresAt) {
                pendingRequestDao.updateState(req.requestId, RequestState.EXPIRED)
                outboundAttempts.remove(req.requestId)
                continue
            }
            if (signalProtocolManager.hasSession(req.peerOffneticKey)) {
                pendingRequestDao.updateState(req.requestId, RequestState.ACCEPTED)
                outboundAttempts.remove(req.requestId)
                continue
            }
            val prev = outboundAttempts[req.requestId]
            if (now - (prev?.lastMs ?: req.createdAt) < REPUBLISH_INTERVAL_MS) continue
            if ((prev?.count ?: 0) >= MAX_REPUBLISH) continue
            val bundle = runCatching { signalProtocolManager.buildPreKeyBundleBytes() }.getOrNull() ?: continue
            val ok = controlSender.sendConnectionRequest(req.peerNostrKey, myPk, myName, bundle)
            if (ok) outboundAttempts[req.requestId] = Attempt(now, (prev?.count ?: 0) + 1)
        }
    }

    private fun decodeNpub(npub: String): ByteArray? {
        val decoded = Bech32.decode(npub) ?: return null
        val (hrp, bytes) = decoded
        return if (hrp == "npub" && bytes.size == 32) bytes else null
    }

    companion object {
        const val OUTBOUND_PREFIX = "out:"
        private const val REPUBLISH_INTERVAL_MS = 5L * 60 * 1000
        private const val MAX_REPUBLISH = 6
    }
}
