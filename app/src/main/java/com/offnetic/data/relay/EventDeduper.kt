package com.offnetic.data.relay

class EventDeduper(
    private val maxSize: Int = com.offnetic.config.OffneticConfig.EVENT_DEDUPER_CAPACITY
) {

    private val seen = object : LinkedHashMap<String, Boolean>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean =
            size > maxSize
    }

    @Synchronized
    fun markSeen(id: String): Boolean {
        if (seen.containsKey(id)) return false
        seen[id] = true
        return true
    }
}
