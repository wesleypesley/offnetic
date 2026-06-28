package com.offnetic.data.relay

import com.offnetic.data.crypto.Bech32
import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.crypto.nostr.GiftWrap
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.RelayOutboxDao
import com.offnetic.data.local.db.entity.RelayOutboxState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class RelayOutboxProcessor @Inject constructor(
    private val outboxDao: RelayOutboxDao,
    private val contactDao: ContactDao,
    private val nostrIdentityManager: NostrIdentityManager,
    private val relayPool: RelayPool
) {
    private val pendingAcks = ConcurrentHashMap<String, String>()
    private val mutex = Mutex()

    suspend fun processPending() = mutex.withLock {
        val myPriv = nostrIdentityManager.getKeyPair()?.privateKey ?: return@withLock
        val now = System.currentTimeMillis()
        for (row in outboxDao.getActive()) {
            if (now >= row.expiresAt) {
                outboxDao.updateState(row.messageUuid, RelayOutboxState.FAILED)
                continue
            }

            if (row.state == RelayOutboxState.RELAYED) {
                if (now - row.lastAttemptAt < REPUBLISH_INTERVAL_MS) continue
                if (now - row.createdAt >= REPUBLISH_WINDOW_MS) continue
            } else {
                if (row.retryCount >= row.maxRetries) {
                    outboxDao.updateState(row.messageUuid, RelayOutboxState.FAILED)
                    continue
                }
                if (now - row.lastAttemptAt < backoffMs(row.retryCount)) continue
            }

            val recipientPub = recipientPubkey(row.chatId)
            if (recipientPub == null) {
                outboxDao.updateState(row.messageUuid, RelayOutboxState.FAILED)
                continue
            }

            val giftWrap = GiftWrap.wrap(
                senderPriv = myPriv,
                recipientPub = recipientPub,
                content = Base64.getEncoder().encodeToString(row.ciphertext),
                kind = GiftWrap.KIND_DM,
                tags = listOf(listOf("u", row.messageUuid))
            )
            val sentCount = relayPool.publish(giftWrap)
            if (sentCount > 0) {
                pendingAcks[giftWrap.id] = row.messageUuid
                outboxDao.upsert(
                    row.copy(
                        state = RelayOutboxState.RELAYED,
                        retryCount = if (row.state == RelayOutboxState.RELAYED) row.retryCount else row.retryCount + 1,
                        lastAttemptAt = now
                    )
                )
                Timber.d("Outbox uuid=${row.messageUuid.take(8)} published (event=${giftWrap.id.take(8)}, relays=$sentCount) state=${row.state}")
            }
        }
    }

    suspend fun handleAck(ack: OkAck) = mutex.withLock {
        if (ack.accepted) {
            pendingAcks.remove(ack.eventId)?.let { uuid ->
                outboxDao.updateState(uuid, RelayOutboxState.ACKNOWLEDGED)
                outboxDao.pruneAcknowledged()
                Timber.d("Outbox ack uuid=${uuid.take(8)} event=${ack.eventId.take(8)} relay=${ack.relayUrl}")
            }
        }
    }

    private suspend fun recipientPubkey(chatId: String): ByteArray? {
        val npub = contactDao.getByPublicKey(chatId)?.nostrPublicKey ?: return null
        val decoded = Bech32.decode(npub) ?: return null
        val (hrp, bytes) = decoded
        if (hrp != "npub" || bytes.size != 32) return null
        return bytes
    }

    private fun backoffMs(retryCount: Int): Long {
        val base = 5_000L
        return minOf(5L * 60_000L, base shl minOf(retryCount, 6))
    }

    companion object {
        private const val REPUBLISH_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val REPUBLISH_WINDOW_MS = 72L * 60 * 60 * 1000
    }
}
