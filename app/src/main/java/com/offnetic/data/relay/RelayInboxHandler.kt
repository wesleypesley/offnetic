package com.offnetic.data.relay

import com.offnetic.data.crypto.Bech32
import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.crypto.nostr.GiftWrap
import com.offnetic.data.crypto.nostr.Hex
import com.offnetic.data.crypto.nostr.NostrEvent
import com.offnetic.data.crypto.nostr.Rumor
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.PendingRequestDao
import com.offnetic.data.local.db.entity.Message
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.local.db.entity.RequestDirection
import com.offnetic.domain.model.MessageDeliveryState
import com.offnetic.util.ActiveChatTracker
import com.offnetic.util.MessageNotificationManager
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
    private val identityDao: IdentityDao
) {
    private val rateLimit = ConcurrentHashMap<String, Long>()

    suspend fun handleGiftWrap(event: NostrEvent) {
        val myPriv = nostrIdentityManager.getKeyPair()?.privateKey ?: return
        val result = runCatching { GiftWrap.unwrap(myPriv, event) }.getOrNull() ?: return
        val rumor = result.rumor
        val senderNpub = Bech32.npub(Hex.decode(result.senderPubkey))
        val type = RelayControl.typeOf(rumor)
        val uuid = rumor.tags.firstOrNull { it.size >= 2 && it[0] == "u" }?.get(1) ?: ""

        when (type) {
            RelayControl.TYPE_REQUEST -> { Timber.d("Inbox uuid=${uuid.take(8)} type=req sender=${senderNpub.take(8)}"); handleRequest(senderNpub, rumor) }
            RelayControl.TYPE_BUNDLE -> { Timber.d("Inbox uuid=${uuid.take(8)} type=bundle sender=${senderNpub.take(8)}"); handleBundle(senderNpub, rumor) }
            RelayControl.TYPE_ACK -> { if (uuid.isNotEmpty()) messageDao.markDelivered(uuid); Timber.d("Inbox ack uuid=${uuid.take(8)} -> DELIVERED") }
            RelayControl.TYPE_READ -> { if (uuid.isNotEmpty()) messageDao.markRead(uuid); Timber.d("Inbox read uuid=${uuid.take(8)} -> READ") }
            else -> handleMessage(senderNpub, rumor)
        }
    }

    private suspend fun handleMessage(senderNpub: String, rumor: Rumor) {
        val uuid = rumor.tags.firstOrNull { it.size >= 2 && it[0] == "u" }?.get(1) ?: return
        val existing = messageDao.getByMessageUuid(uuid)
        if (existing != null) {
            relayControlSender.sendDeliveryAck(senderNpub, uuid)
            if (existing.isRead) relayControlSender.sendReadReceipt(senderNpub, uuid)
            Timber.d("Inbox uuid=${uuid.take(8)} dedup — re-acked")
            return
        }

        val contact = contactDao.getByNostrPublicKey(senderNpub) ?: return

        val ciphertext = runCatching { Base64.getDecoder().decode(rumor.content) }.getOrNull() ?: return
        val plaintext = signalProtocolManager.decryptMessage(contact.publicKey, ciphertext) ?: return

        val json = runCatching { JSONObject(String(plaintext, Charsets.UTF_8)) }.getOrNull() ?: return
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

        val json = runCatching { JSONObject(rumor.content) }.getOrNull() ?: return
        val peerOffneticKey = json.optString("pk", "")
        if (peerOffneticKey.isEmpty()) return
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
        val contact = contactDao.getByNostrPublicKey(senderNpub) ?: return
        val bundle = runCatching { Base64.getDecoder().decode(rumor.content) }.getOrNull() ?: return
        val ok = runCatching {
            signalProtocolManager.processBundleAndCreateSession(contact.publicKey, bundle)
        }.isSuccess
        if (ok) {
            Timber.d("Inbox bundle pk=${contact.publicKey.take(8)} session established")
            relaySessionService.onSessionReady(contact.publicKey)
        }
    }

    companion object {
        private const val REQUEST_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val COOLDOWN_MS = 10_000L
    }

    private fun isRateLimited(npub: String, now: Long = System.currentTimeMillis()): Boolean {
        val last = rateLimit[npub]
        if (last != null && now - last < COOLDOWN_MS) return true
        rateLimit[npub] = now
        return false
    }
}
