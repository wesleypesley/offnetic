package com.offnetic.data.crypto.nostr

import org.json.JSONArray
import org.json.JSONObject

object NostrJson {

    fun toJsonObject(event: NostrEvent): JSONObject =
        JSONObject().apply {
            put("id", event.id)
            put("pubkey", event.pubkey)
            put("created_at", event.createdAt)
            put("kind", event.kind)
            put("tags", tagsToJson(event.tags))
            put("content", event.content)
            put("sig", event.sig)
        }

    fun eventToJson(event: NostrEvent): String = toJsonObject(event).toString()

    fun fromJsonObject(o: JSONObject): NostrEvent =
        NostrEvent(
            id = o.optString("id", ""),
            pubkey = o.getString("pubkey"),
            createdAt = o.getLong("created_at"),
            kind = o.getInt("kind"),
            tags = parseTags(o.getJSONArray("tags")),
            content = o.getString("content"),
            sig = o.optString("sig", "")
        )

    fun parseEvent(json: String): NostrEvent = fromJsonObject(JSONObject(json))

    fun tagsToJson(tags: List<List<String>>): JSONArray {
        val arr = JSONArray()
        for (tag in tags) {
            val inner = JSONArray()
            for (item in tag) inner.put(item)
            arr.put(inner)
        }
        return arr
    }

    fun parseTags(arr: JSONArray): List<List<String>> {
        val result = ArrayList<List<String>>(arr.length())
        for (i in 0 until arr.length()) {
            val inner = arr.getJSONArray(i)
            val tag = ArrayList<String>(inner.length())
            for (j in 0 until inner.length()) tag.add(inner.getString(j))
            result.add(tag)
        }
        return result
    }
}
