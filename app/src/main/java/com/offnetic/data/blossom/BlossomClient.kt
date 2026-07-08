package com.offnetic.data.blossom

import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.crypto.nostr.NostrEventSigner
import com.offnetic.data.crypto.nostr.NostrJson
import com.offnetic.di.Blossom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlossomClient @Inject constructor(
    private val nostrIdentityManager: NostrIdentityManager,
    @Blossom private val okHttpClient: OkHttpClient
) {
    companion object {
        val DEFAULT_SERVERS = listOf(
            "https://nostr.download",
            "https://cdn.hzrd149.com",
            "https://blossom.dreamith.to"
        )
        private const val KIND_BLOSSOM_AUTH = 24242
    }

    // Returns list of server base-URLs that now hold the blob. Empty = all failed.
    suspend fun upload(ciphertext: File, sha256Hex: String): List<String> = withContext(Dispatchers.IO) {
        val priv = nostrIdentityManager.getKeyPair()?.privateKey ?: return@withContext emptyList()
        var primary: Pair<String, String>? = null
        for (server in DEFAULT_SERVERS) {
            val url = tryUpload(server, ciphertext, sha256Hex, priv)
            if (url != null) { primary = server to url; break }
        }
        val (primaryServer, primaryUrl) = primary ?: return@withContext emptyList()
        val result = mutableListOf(primaryServer)
        for (server in DEFAULT_SERVERS) {
            if (server == primaryServer) continue
            if (tryMirror(server, primaryUrl, sha256Hex, priv)) result.add(server)
        }
        result
    }

    // Returns temp file with verified ciphertext bytes, or null if all servers fail / hash mismatch.
    suspend fun download(servers: List<String>, sha256Hex: String, cacheDir: File, maxBytes: Long): File? = withContext(Dispatchers.IO) {
        for (server in servers) {
            val f = tryDownload(server, sha256Hex, cacheDir, maxBytes)
            if (f != null) return@withContext f
        }
        null
    }

    private fun tryUpload(server: String, file: File, sha256Hex: String, priv: ByteArray): String? = try {
        val domain = URI(server).host
        val token = buildToken(priv, sha256Hex, domain)
        val body = file.asRequestBody("application/octet-stream".toMediaType())
        val req = Request.Builder()
            .url("$server/upload")
            .put(body)
            .header("Authorization", "Nostr $token")
            .header("X-SHA-256", sha256Hex)
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) { Timber.w("Blossom upload $server → ${resp.code}"); return null }
            runCatching { JSONObject(resp.body?.string() ?: "").getString("url") }
                .getOrDefault("$server/$sha256Hex")
        }
    } catch (e: Exception) { Timber.w(e, "Blossom upload $server threw"); null }

    private fun tryMirror(server: String, sourceUrl: String, sha256Hex: String, priv: ByteArray): Boolean = try {
        val domain = URI(server).host
        val token = buildToken(priv, sha256Hex, domain)
        val body = JSONObject().put("url", sourceUrl).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$server/mirror")
            .put(body)
            .header("Authorization", "Nostr $token")
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            resp.isSuccessful.also { if (!it) Timber.w("Blossom mirror $server → ${resp.code}") }
        }
    } catch (e: Exception) { Timber.w(e, "Blossom mirror $server threw"); false }

    private fun tryDownload(server: String, sha256Hex: String, cacheDir: File, maxBytes: Long): File? = try {
        val req = Request.Builder().url("$server/$sha256Hex").get().build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) { Timber.w("Blossom dl $server → ${resp.code}"); return null }
            val body = resp.body ?: return null
            val tmp = File(cacheDir, "blossom_dl_${System.currentTimeMillis()}.bin")
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                DigestInputStream(body.byteStream(), digest).use { dis ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        var total = 0L
                        var read: Int
                        while (dis.read(buf).also { read = it } != -1) {
                            total += read
                            if (total > maxBytes) {
                                Timber.w("Blossom dl $server exceeded cap $maxBytes")
                                tmp.delete(); return null
                            }
                            out.write(buf, 0, read)
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != sha256Hex) {
                    Timber.w("Blossom hash mismatch from $server: got $actual expected $sha256Hex")
                    tmp.delete(); return null
                }
                tmp
            } catch (e: Exception) { tmp.delete(); throw e }
        }
    } catch (e: Exception) { Timber.w(e, "Blossom dl $server threw"); null }

    private fun buildToken(priv: ByteArray, sha256Hex: String, domain: String): String {
        val nowSec = System.currentTimeMillis() / 1000
        val event = NostrEventSigner.sign(
            privateKey = priv,
            createdAt = nowSec - 30,
            kind = KIND_BLOSSOM_AUTH,
            tags = listOf(
                listOf("t", "upload"),
                listOf("expiration", "${nowSec + 300}"),
                listOf("x", sha256Hex),
                listOf("server", domain)
            ),
            content = "Upload ${sha256Hex.take(8)}"
        )
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(NostrJson.eventToJson(event).toByteArray(Charsets.UTF_8))
    }
}
