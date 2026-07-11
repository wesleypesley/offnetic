package com.offnetic.ui.contacts

object DeepLink {
    private const val PREFIX = "offnetic://add?data="
    private const val MAX_PAYLOAD_LENGTH = 2048

    // Pairing payloads are URL-safe tokens; anything else in a BROWSABLE deep link is
    // hostile or corrupt and must not be forwarded into navigation routes (O31)
    private val PAYLOAD_CHARSET = Regex("^[A-Za-z0-9_\\-=.%+]+$")

    fun buildAddLink(payload: String): String = PREFIX + payload

    fun parseAddLink(uri: String): String? {
        if (!uri.startsWith(PREFIX)) return null
        val data = uri.substring(PREFIX.length).substringBefore('&')
        if (data.isEmpty() || data.length > MAX_PAYLOAD_LENGTH) return null
        if (!PAYLOAD_CHARSET.matches(data)) return null
        return data
    }
}
