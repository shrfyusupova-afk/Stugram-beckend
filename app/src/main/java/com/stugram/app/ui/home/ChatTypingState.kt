package com.stugram.app.ui.home

sealed interface ChatTypingState {
    data object Idle : ChatTypingState
    data class Direct(val username: String?) : ChatTypingState
    data class Group(val usernames: List<String>) : ChatTypingState
}
