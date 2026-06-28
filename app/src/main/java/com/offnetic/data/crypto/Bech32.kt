package com.offnetic.data.crypto

object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun npub(publicKey: ByteArray): String = encode("npub", publicKey)

    fun nsec(privateKey: ByteArray): String = encode("nsec", privateKey)

    fun encode(hrp: String, data: ByteArray): String {
        val values = convertBits(data, 8, 5, true)
        val combined = values + createChecksum(hrp, values)
        val sb = StringBuilder(hrp).append('1')
        for (v in combined) sb.append(CHARSET[v])
        return sb.toString()
    }

    fun decode(bech: String): Pair<String, ByteArray>? {
        if (bech != bech.lowercase() && bech != bech.uppercase()) return null
        val lower = bech.lowercase()
        val pos = lower.lastIndexOf('1')
        if (pos < 1 || pos + 7 > lower.length) return null
        val hrp = lower.substring(0, pos)
        val dataPart = lower.substring(pos + 1)
        val values = IntArray(dataPart.length)
        for (i in dataPart.indices) {
            val idx = CHARSET.indexOf(dataPart[i])
            if (idx == -1) return null
            values[i] = idx
        }
        if (!verifyChecksum(hrp, values.toList())) return null
        val payload = values.copyOfRange(0, values.size - 6).toList()
        val bytes = convertBits5to8(payload) ?: return null
        return hrp to bytes
    }

    private fun polymod(values: List<Int>): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0..4) {
                if (((top ushr i) and 1) == 1) chk = chk xor GENERATOR[i]
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): List<Int> {
        val result = ArrayList<Int>()
        for (c in hrp) result.add(c.code ushr 5)
        result.add(0)
        for (c in hrp) result.add(c.code and 31)
        return result
    }

    private fun createChecksum(hrp: String, data: List<Int>): List<Int> {
        val values = hrpExpand(hrp) + data + listOf(0, 0, 0, 0, 0, 0)
        val mod = polymod(values) xor 1
        return (0..5).map { (mod ushr (5 * (5 - it))) and 31 }
    }

    private fun verifyChecksum(hrp: String, data: List<Int>): Boolean =
        polymod(hrpExpand(hrp) + data) == 1

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): List<Int> {
        var acc = 0
        var bits = 0
        val result = ArrayList<Int>()
        val maxv = (1 shl toBits) - 1
        for (b in data) {
            acc = (acc shl fromBits) or (b.toInt() and 0xff)
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc ushr bits) and maxv)
            }
        }
        if (pad && bits > 0) result.add((acc shl (toBits - bits)) and maxv)
        return result
    }

    private fun convertBits5to8(data: List<Int>): ByteArray? {
        var acc = 0
        var bits = 0
        val result = ArrayList<Byte>()
        for (value in data) {
            acc = (acc shl 5) or value
            bits += 5
            while (bits >= 8) {
                bits -= 8
                result.add(((acc ushr bits) and 0xff).toByte())
            }
        }
        if (bits >= 5 || ((acc shl (8 - bits)) and 0xff) != 0) return null
        return result.toByteArray()
    }
}
