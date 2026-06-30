package com.offnetic.data.blossom

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object FileCrypto {
    private const val NONCE_BYTES = 12
    private const val KEY_BYTES = 32
    private const val TAG_BITS = 128

    data class Sealed(val ciphertext: File, val keyB64: String, val sha256Hex: String)

    fun encryptToTemp(plain: File, cacheDir: File): Sealed {
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val outFile = File(cacheDir, "blossom_enc_${System.currentTimeMillis()}.bin")
        val digest = MessageDigest.getInstance("SHA-256")

        outFile.outputStream().use { rawOut ->
            val out = DigestingOut(rawOut, digest)
            out.write(nonce)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            plain.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    cipher.update(buf, 0, read)?.let { out.write(it) }
                }
                cipher.doFinal()?.let { out.write(it) }
            }
        }

        val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }
        return Sealed(outFile, Base64.getEncoder().encodeToString(key), sha256Hex)
    }

    // throws javax.crypto.AEADBadTagException on key mismatch / tampered ciphertext
    fun decryptToTemp(ciphertext: File, keyB64: String, outDir: File, name: String): File {
        val key = Base64.getDecoder().decode(keyB64)
        val safeName = name.replace(Regex("""[\\/]"""), "_").take(120)
        val outFile = File(outDir, safeName)
        try {
            ciphertext.inputStream().use { input ->
                val nonce = ByteArray(NONCE_BYTES)
                var filled = 0
                while (filled < NONCE_BYTES) {
                    val r = input.read(nonce, filled, NONCE_BYTES - filled)
                    if (r == -1) error("Truncated: no nonce")
                    filled += r
                }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                outFile.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        cipher.update(buf, 0, read)?.let { output.write(it) }
                    }
                    cipher.doFinal()?.let { output.write(it) }
                }
            }
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }
        return outFile
    }

    private class DigestingOut(out: OutputStream, private val digest: MessageDigest) : FilterOutputStream(out) {
        override fun write(b: Int) { digest.update(b.toByte()); out.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { digest.update(b, off, len); out.write(b, off, len) }
    }
}
