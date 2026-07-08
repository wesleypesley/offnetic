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
                if (now - row.createdAt >= REPUBLISH_WINDOW_MS) {
                    outboxDao.updateState(row.messageUuid, RelayOutboxState.FAILED)
                    continue
                }
            } else {
                if (row.retryCount >= row.maxRetries) {
                    outboxDao.updateState(row.messageUuid, RelayOutboxState.FAILED)
                    continue
                }
                if (now - row.lastAttemptAt < backoffMs(row.retryCount)) continue
            }

            // Skip rows whose contact has no nostrPublicKey yet — this is transient
            // (contact not yet fully set up) and will resolve on the next pass.
            val recipientPub = recipientPubkey(row.chatId) ?: continue

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
        val uuid = pendingAcks[ack.eventId] ?: return@withLock
        if (ack.accepted) {
            pendingAcks.remove(ack.eventId)
            outboxDao.updateState(uuid, RelayOutboxState.ACKNOWLEDGED)
            outboxDao.pruneAcknowledged()
            Timber.d("Outbox ack uuid=${uuid.take(8)} event=${ack.eventId.take(8)} relay=${ack.relayUrl}")
        } else {
            // Relay explicitly rejected the event — bump retry count so backoff applies.
            // Don't remove from pendingAcks yet; other relays may still accept.
            Timber.w("Outbox rejected uuid=${uuid.take(8)} event=${ack.eventId.take(8)} relay=${ack.relayUrl}")
            val row = outboxDao.getByUuid(uuid) ?: return@withLock
            if (row.retryCount + 1 >= row.maxRetries) {
                pendingAcks.remove(ack.eventId)
                outboxDao.updateState(uuid, RelayOutboxState.FAILED)
            } else {
                outboxDao.upsert(row.copy(retryCount = row.retryCount + 1, lastAttemptAt = System.currentTimeMillis()))
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
