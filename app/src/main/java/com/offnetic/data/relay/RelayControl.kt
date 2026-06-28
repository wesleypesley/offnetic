package com.offnetic.data.relay

import com.offnetic.data.crypto.nostr.Rumor

object RelayControl {
    const val TAG_TYPE = "t"
    const val TYPE_MESSAGE = "msg"
    const val TYPE_REQUEST = "req"
    const val TYPE_BUNDLE = "bundle"
    const val TYPE_ACK = "ack"
    const val TYPE_READ = "read"

    fun typeOf(rumor: Rumor): String =
        rumor.tags.firstOrNull { it.size >= 2 && it[0] == TAG_TYPE }?.get(1) ?: TYPE_MESSAGE
}
