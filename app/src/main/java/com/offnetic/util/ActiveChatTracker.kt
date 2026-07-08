package com.offnetic.util

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveChatTracker @Inject constructor() {
    private val _activeChatKey = AtomicReference<String?>(null)

    var activeChatKey: String?
        get() = _activeChatKey.get()
        set(value) { _activeChatKey.set(value) }

    fun clearIfActive(key: String) {
        _activeChatKey.compareAndSet(key, null)
    }
}
