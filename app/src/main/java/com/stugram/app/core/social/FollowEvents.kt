package com.stugram.app.core.social

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class FollowEvent(
    val userId: String?,
    val username: String?,
    val isFollowing: Boolean,
    val followStatus: String? = null
)

/**
 * Minimal shared event bus for follow state consistency across screens.
 * This avoids architecture redesign while keeping UI state in sync.
 */
object FollowEvents {
    private val _events = MutableSharedFlow<FollowEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<FollowEvent> = _events.asSharedFlow()

    fun emit(event: FollowEvent) {
        _events.tryEmit(event)
    }
}
