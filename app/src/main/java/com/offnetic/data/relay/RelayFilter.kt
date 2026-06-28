package com.offnetic.data.relay

import org.json.JSONArray
import org.json.JSONObject

data class RelayFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val pTags: List<String>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null
) {
    fun toJsonObject(): JSONObject =
        JSONObject().apply {
            ids?.let { put("ids", JSONArray(it)) }
            authors?.let { put("authors", JSONArray(it)) }
            kinds?.let { put("kinds", JSONArray(it)) }
            pTags?.let { put("#p", JSONArray(it)) }
            since?.let { put("since", it) }
            until?.let { put("until", it) }
            limit?.let { put("limit", it) }
        }

    fun toJson(): String = toJsonObject().toString()
}
