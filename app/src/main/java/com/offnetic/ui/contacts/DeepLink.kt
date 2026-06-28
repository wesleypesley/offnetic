package com.offnetic.ui.contacts

object DeepLink {
    private const val PREFIX = "offnetic://add?data="

    fun buildAddLink(payload: String): String = PREFIX + payload

    fun parseAddLink(uri: String): String? {
        if (!uri.startsWith(PREFIX)) return null
        val data = uri.substring(PREFIX.length).substringBefore('&')
        return data.ifEmpty { null }
    }
}
