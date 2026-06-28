package com.offnetic.data.crypto.nostr

import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Nip44 {
    private const val VERSION: Byte = 2
    private val SALT = "nip44-v2".toByteArray(Charsets.UTF_8)

    fun conversationKey(privateKeyA: ByteArray, publicKeyB: ByteArray): ByteArray {
        val compressedB = ByteArray(33)
        compressedB[0] = 0x02
        System.arraycopy(publicKeyB, 0, compressedB, 1, 32)
        val sharedPoint = Secp256k1.pubKeyTweakMul(compressedB, privateKeyA)
        val sharedX = when (sharedPoint.size) {
            64 -> sharedPoint.copyOfRange(0, 32)
            else -> sharedPoint.copyOfRange(1, 33)
        }
        return hkdfExtract(SALT, sharedX)
    }

    fun encrypt(plaintext: String, conversationKey: ByteArray, nonce: ByteArray = randomNonce()): String {
        require(nonce.size == 32) { "nonce must be 32 bytes" }
        val keys = messageKeys(conversationKey, nonce)
        val padded = pad(plaintext.toByteArray(Charsets.UTF_8))
        val ciphertext = chacha20(keys.chachaKey, keys.chachaNonce, padded)
        val mac = hmacAad(keys.hmacKey, ciphertext, nonce)
        val payload = ByteArray(1 + 32 + ciphertext.size + 32)
        payload[0] = VERSION
        System.arraycopy(nonce, 0, payload, 1, 32)
        System.arraycopy(ciphertext, 0, payload, 33, ciphertext.size)
        System.arraycopy(mac, 0, payload, 33 + ciphertext.size, 32)
        return Base64.getEncoder().encodeToString(payload)
    }

    fun decrypt(payload: String, conversationKey: ByteArray): String {
        require(payload.isNotEmpty() && payload[0] != '#') { "unsupported payload" }
        val data = Base64.getDecoder().decode(payload)
        require(data.size in 99..65603) { "invalid payload length" }
        require(data[0] == VERSION) { "unknown version" }
        val nonce = data.copyOfRange(1, 33)
        val mac = data.copyOfRange(data.size - 32, data.size)
        val ciphertext = data.copyOfRange(33, data.size - 32)
        val keys = messageKeys(conversationKey, nonce)
        val expectedMac = hmacAad(keys.hmacKey, ciphertext, nonce)
        require(constantTimeEquals(mac, expectedMac)) { "invalid MAC" }
        val padded = chacha20(keys.chachaKey, keys.chachaNonce, ciphertext)
        return unpad(padded)
    }

    fun calcPaddedLen(unpaddedLen: Int): Int {
        require(unpaddedLen > 0) { "length must be positive" }
        if (unpaddedLen <= 32) return 32
        val nextPower = 1 shl (32 - Integer.numberOfLeadingZeros(unpaddedLen - 1))
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((unpaddedLen - 1) / chunk + 1)
    }

    internal class MessageKeys(val chachaKey: ByteArray, val chachaNonce: ByteArray, val hmacKey: ByteArray)

    internal fun messageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        val expanded = hkdfExpand(conversationKey, nonce, 76)
        return MessageKeys(
            chachaKey = expanded.copyOfRange(0, 32),
            chachaNonce = expanded.copyOfRange(32, 44),
            hmacKey = expanded.copyOfRange(44, 76)
        )
    }

    private fun pad(plaintext: ByteArray): ByteArray {
        val unpaddedLen = plaintext.size
        require(unpaddedLen in 1..65535) { "plaintext length out of range" }
        val result = ByteArray(2 + calcPaddedLen(unpaddedLen))
        result[0] = (unpaddedLen ushr 8).toByte()
        result[1] = (unpaddedLen and 0xFF).toByte()
        System.arraycopy(plaintext, 0, result, 2, unpaddedLen)
        return result
    }

    private fun unpad(padded: ByteArray): String {
        require(padded.size >= 2) { "padded too short" }
        val unpaddedLen = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
        require(unpaddedLen in 1..65535) { "invalid unpadded length" }
        require(padded.size == 2 + calcPaddedLen(unpaddedLen)) { "invalid padding" }
        return String(padded, 2, unpaddedLen, Charsets.UTF_8)
    }

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val result = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val toCopy = minOf(t.size, length - pos)
            System.arraycopy(t, 0, result, pos, toCopy)
            pos += toCopy
            counter++
        }
        return result
    }

    internal fun chacha20(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(nonce.size == 12) { "nonce must be 12 bytes" }
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = leInt(key, i * 4)
        for (i in 0 until 3) state[13 + i] = leInt(nonce, i * 4)
        val out = ByteArray(data.size)
        var counter = 0
        var pos = 0
        while (pos < data.size) {
            state[12] = counter
            val block = chachaBlock(state)
            val n = minOf(64, data.size - pos)
            for (j in 0 until n) {
                out[pos + j] = (data[pos + j].toInt() xor block[j].toInt()).toByte()
            }
            pos += n
            counter++
        }
        return out
    }

    private fun chachaBlock(state: IntArray): ByteArray {
        val x = state.copyOf()
        repeat(10) {
            quarterRound(x, 0, 4, 8, 12)
            quarterRound(x, 1, 5, 9, 13)
            quarterRound(x, 2, 6, 10, 14)
            quarterRound(x, 3, 7, 11, 15)
            quarterRound(x, 0, 5, 10, 15)
            quarterRound(x, 1, 6, 11, 12)
            quarterRound(x, 2, 7, 8, 13)
            quarterRound(x, 3, 4, 9, 14)
        }
        val out = ByteArray(64)
        for (i in 0 until 16) {
            val v = x[i] + state[i]
            out[i * 4] = v.toByte()
            out[i * 4 + 1] = (v ushr 8).toByte()
            out[i * 4 + 2] = (v ushr 16).toByte()
            out[i * 4 + 3] = (v ushr 24).toByte()
        }
        return out
    }

    private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = rotateLeft(x[d] xor x[a], 16)
        x[c] += x[d]; x[b] = rotateLeft(x[b] xor x[c], 12)
        x[a] += x[b]; x[d] = rotateLeft(x[d] xor x[a], 8)
        x[c] += x[d]; x[b] = rotateLeft(x[b] xor x[c], 7)
    }

    private fun rotateLeft(v: Int, n: Int): Int = (v shl n) or (v ushr (32 - n))

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun hmacAad(key: ByteArray, message: ByteArray, aad: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(aad)
        mac.update(message)
        return mac.doFinal()
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }

    private fun randomNonce(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
}
