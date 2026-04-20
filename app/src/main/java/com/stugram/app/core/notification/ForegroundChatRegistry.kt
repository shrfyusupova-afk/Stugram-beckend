package com.stugram.app.core.notification

object ForegroundChatRegistry {
    @Volatile
    var activeConversationId: String? = null

    @Volatile
    var activeGroupId: String? = null
}
