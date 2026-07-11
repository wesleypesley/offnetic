package com.offnetic.data.relay

import com.offnetic.data.blossom.BlossomFileService
import com.offnetic.data.crypto.Bech32
import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.crypto.nostr.GiftWrap
import com.offnetic.data.crypto.nostr.Hex
import com.offnetic.data.crypto.nostr.NostrEvent
import com.offnetic.data.crypto.nostr.Rumor
import com.offnetic.data.local.db.dao.CallHistoryDao
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.PendingRequestDao
import com.offnetic.data.local.db.entity.CallHistoryEntity
import com.offnetic.data.local.db.entity.Message
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.local.db.entity.RequestDirection
import com.offnetic.data.local.db.entity.RequestState
import com.offnetic.data.nearby.WebRtcManager
import com.offnetic.domain.model.MessageDeliveryState
import com.offnetic.util.ActiveChatTracker
import com.offnetic.util.MessageNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class RelayInboxHandler @Inject constructor(
    private val nostrIdentityManager: NostrIdentityManager,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val pendingRequestDao: PendingRequestDao,
    private val signalProtocolManager: SignalProtocolManager,
    private val messageNotificationManager: MessageNotificationManager,
    private val relaySessionService: RelaySessionService,
    private val relayRequestManager: RelayRequestManager,
    private val relayControlSender: RelayControlSender,
    private val activeChatTracker: ActiveChatTracker,
    private val identityDao: IdentityDao,
    private val webRtcManager: WebRtcManager,
    private val callHistoryDao: CallHistoryDao,
    private val blossomFileService: BlossomFileService
) {
    private val rateLimit = ConcurrentHashMap<String, Long>()
    private val inFlightFiles = ConcurrentHashMap.newKeySet<String>()
    private val inFlightMessages = ConcurrentHashMap.newKeySet<String>()
    private val fileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun handleGiftWrap(event: NostrEvent) {
        val myPriv = nostrIdentityManager.getKeyPair()?.privateKey ?: return
        val result = runCatching { GiftWrap.unwrap(myPriv, event) }.getOrNull() ?: run {
            Timber.w("Inbox: GiftWrap unwrap failed for event ${event.id.take(8)}")
            return
        }
        val rumor = result.rumor
        val senderNpub = Bech32.npub(Hex.decode(result.senderPubkey))
        // Drop self-addressed events — replayed or reflected gift wraps must not
        // create requests or messages from ourselves (CR9)
        if (senderNpub == nostrIdentityManager.getNpub()) {
            Timber.w("Inbox: dropped self-addressed event ${event.id.take(8)}")
            return
        }
        val type = RelayControl.typeOf(rumor)
        val uuid = rumor.tags.firstOrNull { it.size >= 2 && it[0] == "u" }?.get(1) ?: ""

        when (type) {
            RelayControl.TYPE_REQUEST -> { Timber.d("Inbox uuid=${uuid.take(8)} type=req sender=${senderNpub.take(8)}"); handleRequest(senderNpub, rumor) }
            RelayControl.TYPE_BUNDLE -> { Timber.d("Inbox uuid=${uuid.take(8)} type=bundle sender=${senderNpub.take(8)}"); handleBundle(senderNpub, rumor) }
            RelayControl.TYPE_ACK -> { if (uuid.isNotEmpty()) messageDao.markDelivered(uuid); Timber.d("Inbox ack uuid=${uuid.take(8)} -> DELIVERED") }
            RelayControl.TYPE_READ -> { if (uuid.isNotEmpty()) messageDao.markRead(uuid); Timber.d("Inbox read uuid=${uuid.take(8)} -> READ") }
            RelayControl.TYPE_CALL_OFFER -> handleCallOffer(senderNpub, rumor)
            RelayControl.TYPE_CALL_ANSWER,
            RelayControl.TYPE_ICE_CANDIDATE,
            RelayControl.TYPE_CALL_HANGUP -> {
                contactDao.getByNostrPublicKey(senderNpub)?.let { c ->
                    webRtcManager.onRelayCallSignal(c.publicKey, type, rumor.content)
                }
            }
            RelayControl.TYPE_FILE_BLOSSOM -> handleFileBlossom(senderNpub, uuid, rumor)
            else -> handleMessage(senderNpub, rumor)
        }
    }

    private suspend fun handleMessage(senderNpub: String, rumor: Rumor) {
        val uuid = rumor.tags.firstOrNull { it.size >= 2 && it[0] == "u" }?.get(1) ?: run {
            Timber.w("Inbox: message from ${senderNpub.take(8)} missing 'u' tag — dropped")
            return
        }
        // Concurrent gift wraps carrying the same uuid both pass the DB dedup check
        // below; the in-flight set closes that window (#30)
        if (!inFlightMessages.add(uuid)) return
        try {
            handleMessageLocked(senderNpub, rumor, uuid)
        } finally {
            inFlightMessages.remove(uuid)
        }
    }

    private suspend fun handleMessageLocked(senderNpub: String, rumor: Rumor, uuid: String) {
        val existing = messageDao.getByMessageUuid(uuid)
        if (existing != null) {
            relayControlSender.sendDeliveryAck(senderNpub, uuid)
            if (existing.isRead) relayControlSender.sendReadReceipt(senderNpub, uuid)
            Timber.d("Inbox uuid=${uuid.take(8)} dedup — re-acked")
            return
        }

        val contact = contactDao.getByNostrPublicKey(senderNpub) ?: run {
            Timber.w("Inbox uuid=${uuid.take(8)}: no contact for ${senderNpub.take(8)} — dropped")
            return
        }

        val ciphertext = runCatching { Base64.getDecoder().decode(rumor.content) }.getOrNull() ?: run {
            Timber.w("Inbox uuid=${uuid.take(8)}: Base64 decode failed — dropped")
            return
        }
        val plaintext = signalProtocolManager.decryptMessage(contact.publicKey, ciphertext) ?: run {
            Timber.w("Inbox uuid=${uuid.take(8)}: decryption failed for ${contact.publicKey.take(8)} — dropped")
            return
        }

        val json = runCatching { JSONObject(String(plaintext, Charsets.UTF_8)) }.getOrNull() ?: run {
            Timber.w("Inbox uuid=${uuid.take(8)}: plaintext is not valid JSON — dropped")
            return
        }
        val content = json.optString("content", "")
        if (content.isEmpty()) return
        val timestamp = json.optLong("timestamp", System.currentTimeMillis())

        messageDao.insert(
            Message(
                messageUuid = uuid,
                sessionId = contact.publicKey,
                chatId = contact.publicKey,
                senderPublicKey = contact.publicKey,
                content = content,
                type = Message.TYPE_TEXT,
                timestamp = timestamp,
                deliveryState = MessageDeliveryState.SAVED,
                isRead = false
            )
        )
        messageNotificationManager.notifyIfNeeded(contact.publicKey)
        Timber.d("Inbox uuid=${uuid.take(8)} pk=${contact.publicKey.take(8)} persisted")
        relayControlSender.sendDeliveryAck(senderNpub, uuid)
        if (activeChatTracker.activeChatKey == contact.publicKey) {
            identityDao.getIdentity()?.publicKey?.let { messageDao.markAsRead(contact.publicKey, it) }
            relayControlSender.sendReadReceipt(senderNpub, uuid)
        }
    }

    private suspend fun handleRequest(senderNpub: String, rumor: Rumor) {
        if (isRateLimited(senderNpub)) return
        if (contactDao.getByNostrPublicKey(senderNpub) != null) return

        // A row the user ignored stays EXPIRED until its TTL prunes it; a republished
        // request must not resurrect it via upsert/REPLACE (CR2)
        val existing = pendingRequestDao.getById(senderNpub)
        if (existing != null && existing.state == RequestState.EXPIRED) {
            Timber.d("Inbox: request from ignored sender ${senderNpub.take(8)} — dropped")
            return
        }

        val json = runCatching { JSONObject(rumor.content) }.getOrNull() ?: run {
            Timber.w("Inbox: request from ${senderNpub.take(8)} has invalid JSON — dropped")
            return
        }
        val peerOffneticKey = json.optString("pk", "")
        if (peerOffneticKey.isEmpty()) return
        // A request claiming our own identity key is a replay or spoof (CR9)
        if (peerOffneticKey == identityDao.getIdentity()?.publicKey) {
            Timber.w("Inbox: request from ${senderNpub.take(8)} claims own identity key — dropped")
            return
        }
        val displayName = json.optString("name", peerOffneticKey.take(12))

        val bundleBytes = json.optString("bundle").takeIf { it.isNotEmpty() }?.let {
            runCatching { Base64.getDecoder().decode(it) }.getOrNull()
        }

        val now = System.currentTimeMillis()
        pendingRequestDao.upsert(
            PendingRequestEntity(
                requestId = senderNpub,
                direction = RequestDirection.INBOUND,
                peerOffneticKey = peerOffneticKey,
                peerNostrKey = senderNpub,
                displayName = displayName,
                createdAt = now,
                expiresAt = now + REQUEST_TTL_MS,
                bundle = bundleBytes
            )
        )
        relayRequestManager.refreshCount()
    }

    private suspend fun handleBundle(senderNpub: String, rumor: Rumor) {
        if (isRateLimited(senderNpub)) return
        val contact = contactDao.getByNostrPublicKey(senderNpub) ?: run {
            Timber.w("Inbox: bundle from unknown sender ${senderNpub.take(8)} — dropped")
            return
        }
        val bundle = runCatching { Base64.getDecoder().decode(rumor.content) }.getOrNull() ?: run {
            Timber.w("Inbox: bundle from ${senderNpub.take(8)} failed Base64 decode — dropped")
            return
        }
        val ok = runCatching {
            signalProtocolManager.processBundleAndCreateSession(contact.publicKey, bundle)
        }.isSuccess
        if (ok) {
            Timber.d("Inbox bundle pk=${contact.publicKey.take(8)} session established")
            relaySessionService.onSessionReady(contact.publicKey)
        }
    }

    private suspend fun handleCallOffer(senderNpub: String, rumor: Rumor) {
        val contact = contactDao.getByNostrPublicKey(senderNpub) ?: return
        val age = System.currentTimeMillis() - rumor.createdAt * 1000L
        if (age > com.offnetic.config.OffneticConfig.STALE_CALL_OFFER_MS) {
            callHistoryDao.insert(
                CallHistoryEntity(
                    peerPublicKey = contact.publicKey,
                    type = CallHistoryEntity.TYPE_VOICE,
                    direction = CallHistoryEntity.DIRECTION_MISSED,
                    timestamp = System.currentTimeMillis()
                )
            )
            Timber.d("Inbox: dropped stale call offer from ${contact.publicKey.take(8)} age=${age}ms")
            return
        }
        webRtcManager.cacheRelayCallOffer(contact.publicKey, rumor.content)
    }

    private fun handleFileBlossom(senderNpub: String, uuid: String, rumor: Rumor) {
        if (uuid.isNotEmpty() && !inFlightFiles.add(uuid)) return // dedup
        fileScope.launch {
            try {
                val contact = contactDao.getByNostrPublicKey(senderNpub) ?: return@launch
                val json = runCatching { JSONObject(rumor.content) }.getOrNull() ?: return@launch
                val servers = json.optJSONArray("servers")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: return@launch
                val sha256 = json.optString("sha256").takeIf { it.isNotEmpty() } ?: return@launch
                val keyB64 = json.optString("key").takeIf { it.isNotEmpty() } ?: return@launch
                val name = json.optString("name", "file")
                val expectedSize = json.optLong("size", 0L)
                val mime = json.optString("mime", "application/octet-stream")

                val file = blossomFileService.receiveFile(servers, sha256, keyB64, name, expectedSize) ?: run {
                    Timber.w("handleFileBlossom: download failed for sha256=${sha256.take(8)}")
                    return@launch
                }
                val msgType = when {
                    mime.startsWith("image/") -> Message.TYPE_IMAGE
                    mime.startsWith("video/") -> Message.TYPE_VIDEO
                    mime.startsWith("audio/") -> Message.TYPE_VOICE_NOTE
                    else -> Message.TYPE_FILE
                }
                val displayContent = when {
                    msgType == Message.TYPE_VOICE_NOTE -> json.optString("content", "").takeIf { it.isNotEmpty() } ?: name
                    else -> name
                }
                val msgUuid = if (uuid.isNotEmpty()) uuid else java.util.UUID.randomUUID().toString()
                messageDao.insert(
                    Message(
                        messageUuid = msgUuid,
                        sessionId = contact.publicKey,
                        chatId = contact.publicKey,
                        senderPublicKey = contact.publicKey,
                        content = displayContent,
                        type = msgType,
                        timestamp = System.currentTimeMillis(),
                        deliveryState = MessageDeliveryState.SAVED,
                        isRead = false,
                        attachmentPath = file.absolutePath
                    )
                )
                messageNotificationManager.notifyIfNeeded(contact.publicKey)
                if (uuid.isNotEmpty()) {
                    relayControlSender.sendDeliveryAck(senderNpub, uuid)
                    if (activeChatTracker.activeChatKey == contact.publicKey) {
                        identityDao.getIdentity()?.publicKey?.let { messageDao.markAsRead(contact.publicKey, it) }
                        relayControlSender.sendReadReceipt(senderNpub, uuid)
                    }
                }
            } finally {
                if (uuid.isNotEmpty()) inFlightFiles.remove(uuid)
            }
        }
    }

    companion object {
        private const val REQUEST_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val COOLDOWN_MS = 10_000L
    }

    private fun isRateLimited(npub: String, now: Long = System.currentTimeMillis()): Boolean {
        // compute() makes the read-check-write atomic; the previous get/put pair let
        // two concurrent events from the same sender both pass (#29)
        var limited = true
        rateLimit.compute(npub) { _, last ->
            if (last != null && now - last < COOLDOWN_MS) {
                last
            } else {
                limited = false
                now
            }
        }
        // Opportunistic prune keeps the map from growing unboundedly across many senders
        if (rateLimit.size > 512) {
            rateLimit.entries.removeIf { now - it.value >= COOLDOWN_MS }
        }
        return limited
    }
}
