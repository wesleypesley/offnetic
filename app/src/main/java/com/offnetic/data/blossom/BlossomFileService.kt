package com.offnetic.data.blossom

import android.content.Context
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.relay.RelayControlSender
import com.offnetic.util.MessageNotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
    suspend fun sendFile(npub: String, plain: File, mime: String): Boolean {
        val sealed = try {
            FileCrypto.encryptToTemp(plain, context.cacheDir)
        } catch (e: Exception) {
            Timber.e(e, "BlossomFileService: encrypt failed")
            return false
        }
        try {
            val servers = blossomClient.upload(sealed.ciphertext, sealed.sha256Hex)
            if (servers.isEmpty()) return false
            val payload = JSONObject().apply {
                put("sha256", sealed.sha256Hex)
                put("key", sealed.keyB64)
                put("file_name", plain.name)
                put("file_size", plain.length())
                put("mime_type", mime)
                put("servers", JSONArray(servers))
            }
            return relayControlSender.sendFileBlossom(npub, payload.toString())
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
    ): File? {
        val temp = blossomClient.download(servers, sha256, context.cacheDir) ?: return null
        return try {
            FileCrypto.decryptToTemp(temp, keyB64, context.filesDir, name)
        } catch (e: Exception) {
            Timber.w(e, "BlossomFileService: decrypt failed ${sha256.take(8)}")
            null
        } finally {
            temp.delete()
        }
    }
}
