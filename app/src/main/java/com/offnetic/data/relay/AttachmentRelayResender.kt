package com.offnetic.data.relay

import com.offnetic.data.blossom.BlossomFileService
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.entity.Message
import com.offnetic.data.network.NetworkMonitor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRelayResender @Inject constructor(
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val identityDao: IdentityDao,
    private val networkMonitor: NetworkMonitor,
    private val blossomFileService: BlossomFileService
) {
    private val mutex = Mutex()
    private val backoffUntil = ConcurrentHashMap<String, Long>()
    private val attempts = ConcurrentHashMap<String, Int>()

    suspend fun processPending() = mutex.withLock {
        if (!networkMonitor.isOnline.value) return@withLock
        val myPk = identityDao.getIdentity()?.publicKey ?: return@withLock
        val now = System.currentTimeMillis()
        for (msg in messageDao.getUnsentAttachments(myPk)) {
            if (now - msg.timestamp < FRESH_GRACE_MS) continue
            if (now < (backoffUntil[msg.messageUuid] ?: 0L)) continue
            val npub = contactDao.getByPublicKey(msg.chatId)?.nostrPublicKey ?: continue
            val path = msg.attachmentPath ?: continue
            val file = File(path)
            if (!file.exists()) continue
            val mime = if (msg.type == Message.TYPE_VOICE_NOTE) "audio/mp4" else mimeFor(file.name)
            val sent = runCatching { blossomFileService.sendFile(npub, file, mime, msg.messageUuid, msg.content) }
                .getOrElse { Timber.w(it, "AttachmentResender: send threw for ${msg.messageUuid.take(8)}"); false }
            if (sent) {
                if (messageDao.getById(msg.id)?.type != Message.TYPE_CANCELLED) {
                    messageDao.markSentRelay(msg.messageUuid)
                }
                backoffUntil.remove(msg.messageUuid)
                attempts.remove(msg.messageUuid)
                Timber.d("AttachmentResender: relayed ${msg.messageUuid.take(8)}")
            } else {
                val n = (attempts[msg.messageUuid] ?: 0) + 1
                attempts[msg.messageUuid] = n
                backoffUntil[msg.messageUuid] = now + minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl minOf(n, 5))
            }
        }
    }

    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    companion object {
        private const val FRESH_GRACE_MS = 60_000L
        private const val BASE_BACKOFF_MS = 15_000L
        private const val MAX_BACKOFF_MS = 10L * 60_000L
    }
}
