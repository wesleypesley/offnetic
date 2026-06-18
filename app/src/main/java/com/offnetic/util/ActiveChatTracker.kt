package com.offnetic.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveChatTracker @Inject constructor() {
    @Volatile
    var activeChatKey: String? = null
}
