package com.offnetic.data.blossom

import android.content.Context
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.relay.RelayControlSender
import com.offnetic.util.MessageNotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlossomFileService @Inject constructor(
    private val blossomClient: BlossomClient,
    private val relayControlSender: RelayControlSender,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val messageNotificationManager: MessageNotificationManager,
    @ApplicationContext private val context: Context
) {
    // Returns true only if the gift-wrap published successfully.
    // Caller keeps the plaintext local copy; this method handles ciphertext temp lifecycle.
    suspend fun sendFile(npub: String, plain: File, mime: String, messageUuid: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            val sealed = try {
                FileCrypto.encryptToTemp(plain, context.cacheDir)
            } catch (e: Exception) {
                Timber.e(e, "BlossomFileService: encrypt failed")
                return@withContext false
            }
            try {
                val servers = blossomClient.upload(sealed.ciphertext, sealed.sha256Hex)
                if (servers.isEmpty()) return@withContext false
                val payload = JSONObject().apply {
                    put("sha256", sealed.sha256Hex)
                    put("key", sealed.keyB64)
                    put("name", plain.name)
                    put("size", plain.length())
                    put("mime", mime)
                    put("content", content)
                    put("servers", JSONArray(servers))
                }
                val announced = relayControlSender.sendFileBlossom(npub, payload.toString(), messageUuid)
                if (!announced) {
                    // The blob is uploaded but unannounced — unreachable ciphertext that
                    // only the servers' retention policy will clean up. Log it so orphans
                    // are at least observable (#70); deleting needs BUD-02 signed auth.
                    Timber.w("BlossomFileService: announcement failed — orphaned blob sha256=${sealed.sha256Hex.take(16)} on ${servers.size} server(s)")
                }
                announced
            } finally {
                sealed.ciphertext.delete()
            }
        }

    // Downloads, verifies sha256, decrypts to filesDir. Returns null on any failure.
    suspend fun receiveFile(
        servers: List<String>,
        sha256: String,
        keyB64: String,
        name: String,
        expectedSize: Long
    ): File? = withContext(Dispatchers.IO) {
        // Sender-claimed size is untrusted: refuse oversized claims outright instead of
        // streaming up to the global cap before failing (#71)
        if (expectedSize > MAX_DOWNLOAD_BYTES) {
            Timber.w("BlossomFileService: claimed size $expectedSize exceeds cap — refused ${sha256.take(8)}")
            return@withContext null
        }
        val cap = if (expectedSize > 0) minOf(expectedSize + CIPHERTEXT_OVERHEAD, MAX_DOWNLOAD_BYTES) else MAX_DOWNLOAD_BYTES
        val temp = blossomClient.download(servers, sha256, context.cacheDir, cap) ?: return@withContext null
        try {
            FileCrypto.decryptToTemp(temp, keyB64, context.filesDir, "${sha256.take(16)}_$name")
        } catch (e: Exception) {
            Timber.w(e, "BlossomFileService: decrypt failed ${sha256.take(8)}")
            null
        } finally {
            temp.delete()
        }
    }

    companion object {
        private const val CIPHERTEXT_OVERHEAD = 1024L
        private const val MAX_DOWNLOAD_BYTES = com.offnetic.config.OffneticConfig.MAX_FILE_SIZE_BYTES + CIPHERTEXT_OVERHEAD
    }
}
