package com.offnetic.data.crypto.nostr

import java.security.MessageDigest

data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
) {
    companion object {
        fun canonicalString(
            pubkey: String,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String
        ): String {
            val sb = StringBuilder()
            sb.append("[0,\"").append(pubkey).append("\",")
            sb.append(createdAt).append(',')
            sb.append(kind).append(',')
            appendTags(sb, tags)
            sb.append(',')
            sb.append('"').append(escape(content)).append('"')
            sb.append(']')
            return sb.toString()
        }

        fun computeId(
            pubkey: String,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String
        ): String {
            val canonical = canonicalString(pubkey, createdAt, kind, tags, content)
            val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            return Hex.encode(hash)
        }

        private fun appendTags(sb: StringBuilder, tags: List<List<String>>) {
            sb.append('[')
            for ((i, tag) in tags.withIndex()) {
                if (i > 0) sb.append(',')
                sb.append('[')
                for ((j, item) in tag.withIndex()) {
                    if (j > 0) sb.append(',')
                    sb.append('"').append(escape(item)).append('"')
                }
                sb.append(']')
            }
            sb.append(']')
        }

        private fun escape(s: String): String {
            val sb = StringBuilder(s.length + 2)
            for (c in s) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (c < '\u0020') {
                            sb.append("\\u").append(String.format("%04x", c.code))
                        } else {
                            sb.append(c)
                        }
                    }
                }
            }
            return sb.toString()
        }
    }
}
