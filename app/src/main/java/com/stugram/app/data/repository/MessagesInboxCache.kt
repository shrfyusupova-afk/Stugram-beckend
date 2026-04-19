package com.stugram.app.data.repository

import com.stugram.app.ui.home.ChatMessage
import com.stugram.app.ui.home.GroupChat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MessagesInboxCache {
    private val _chats = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _groups = MutableStateFlow<List<GroupChat>>(emptyList())
    private val _requests = MutableStateFlow<List<ChatMessage>>(emptyList())

    val chats: StateFlow<List<ChatMessage>> = _chats.asStateFlow()
    val groups: StateFlow<List<GroupChat>> = _groups.asStateFlow()
    val requests: StateFlow<List<ChatMessage>> = _requests.asStateFlow()

    fun updateChats(items: List<ChatMessage>) {
        _chats.value = items
    }

    fun updateGroups(items: List<GroupChat>) {
        _groups.value = items
    }

    fun updateRequests(items: List<ChatMessage>) {
        _requests.value = items
    }
}
