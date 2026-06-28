package com.offnetic.data.relay

import com.offnetic.data.crypto.nostr.NostrEvent
import com.offnetic.data.crypto.nostr.NostrJson
import org.json.JSONArray

sealed class RelayMessage {
    data class Event(val subscriptionId: String, val event: NostrEvent) : RelayMessage()
    data class Ok(val eventId: String, val accepted: Boolean, val message: String) : RelayMessage()
    data class Eose(val subscriptionId: String) : RelayMessage()
    data class Closed(val subscriptionId: String, val message: String) : RelayMessage()
    data class Notice(val message: String) : RelayMessage()
    data class Unknown(val raw: String) : RelayMessage()

    companion object {
        fun event(event: NostrEvent): String =
            JSONArray().put("EVENT").put(NostrJson.toJsonObject(event)).toString()

        fun req(subscriptionId: String, vararg filters: RelayFilter): String {
            val arr = JSONArray().put("REQ").put(subscriptionId)
            for (f in filters) arr.put(f.toJsonObject())
            return arr.toString()
        }

        fun close(subscriptionId: String): String =
            JSONArray().put("CLOSE").put(subscriptionId).toString()

        fun parse(text: String): RelayMessage {
            return try {
                val arr = JSONArray(text)
                when (arr.getString(0)) {
                    "EVENT" -> Event(arr.getString(1), NostrJson.fromJsonObject(arr.getJSONObject(2)))
                    "OK" -> Ok(arr.getString(1), arr.getBoolean(2), arr.optString(3, ""))
                    "EOSE" -> Eose(arr.getString(1))
                    "CLOSED" -> Closed(arr.getString(1), arr.optString(2, ""))
                    "NOTICE" -> Notice(arr.optString(1, ""))
                    else -> Unknown(text)
                }
            } catch (_: Exception) {
                Unknown(text)
            }
        }
    }
}
