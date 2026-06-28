package com.offnetic.ui.contacts

import java.math.BigInteger
import java.security.MessageDigest

object SafetyNumber {
    private const val DIGITS = 60

    fun compute(publicKeyA: String, publicKeyB: String): String {
        val ordered = listOf(publicKeyA, publicKeyB).sorted().joinToString("|")
        val hash = MessageDigest.getInstance("SHA-256").digest(ordered.toByteArray(Charsets.UTF_8))
        val value = BigInteger(1, hash).mod(BigInteger.TEN.pow(DIGITS))
        return value.toString().padStart(DIGITS, '0')
    }

    fun formatGroups(number: String): String = number.chunked(5).joinToString(" ")
}
