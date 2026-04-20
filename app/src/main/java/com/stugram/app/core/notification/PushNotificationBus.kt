package com.stugram.app.core.notification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PushNotificationEvent(
    val type: String? = null,
    val conversationId: String? = null,
    val groupId: String? = null,
    val messageId: String? = null,
    val targetId: String? = null
)

object PushNotificationBus {
    private val _events = MutableSharedFlow<PushNotificationEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun emit(event: PushNotificationEvent) {
        _events.tryEmit(event)
    }
}
