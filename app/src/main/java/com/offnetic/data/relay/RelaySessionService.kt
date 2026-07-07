package com.offnetic.data.relay

import androidx.room.withTransaction
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.OffneticDatabase
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.RelayOutboxDao
import com.offnetic.data.local.db.entity.Message
import com.offnetic.data.local.db.entity.RelayOutboxEntity
import com.offnetic.domain.model.MessageDeliveryState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class RelaySessionService @Inject constructor(
    private val database: OffneticDatabase,
    private val messageDao: MessageDao,
    private val identityDao: IdentityDao,
    private val signalProtocolManager: SignalProtocolManager,
    private val relayOutboxDao: RelayOutboxDao,
    private val relayOutboxProcessor: RelayOutboxProcessor
) {
    private val sessionMutex = Mutex()

    suspend fun onSessionReady(contactPublicKey: String) = sessionMutex.withLock {
        val myIdentity = identityDao.getIdentity() ?: return
        val myPk = myIdentity.publicKey
        val messages = messageDao.getUnsentMessagesForChat(contactPublicKey, myPk)

        // Encrypt outside the transaction — withContext(IO) inside encryptMessage would break
        // Room's transaction coroutine context if called from inside withTransaction.
        val encrypted = messages
            .filter { it.type == Message.TYPE_TEXT }
            .mapNotNull { msg ->
                val plaintext = JSONObject().apply {
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                }.toString().toByteArray(Charsets.UTF_8)
                val ciphertext = runCatching {
                    signalProtocolManager.encryptMessage(contactPublicKey, plaintext)
                }.getOrNull() ?: return@mapNotNull null
                Pair(msg, ciphertext)
            }

        // Write outbox entries + message state updates atomically — a crash mid-loop
        // previously left messages marked SENT_RELAY without a corresponding outbox row.
        database.withTransaction {
            val now = System.currentTimeMillis()
            for ((msg, ciphertext) in encrypted) {
                relayOutboxDao.upsert(
                    RelayOutboxEntity(
                        messageUuid = msg.messageUuid,
                        chatId = contactPublicKey,
                        ciphertext = ciphertext,
                        createdAt = now,
                        expiresAt = now + RELAY_OUTBOX_TTL_MS
                    )
                )
                relayOutboxDao.evictOldestPending(contactPublicKey, RELAY_OUTBOX_CAP)
                messageDao.update(msg.copy(deliveryState = MessageDeliveryState.SENT_RELAY))
                Timber.d("SessionReady uuid=${msg.messageUuid.take(8)} pk=${contactPublicKey.take(8)} re-encrypted → relay outbox")
            }
        }

        relayOutboxProcessor.processPending()
        Timber.d("SessionReady pk=${contactPublicKey.take(8)} retried ${encrypted.size} messages")
    }

    companion object {
        private const val RELAY_OUTBOX_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val RELAY_OUTBOX_CAP = 50
    }
}
