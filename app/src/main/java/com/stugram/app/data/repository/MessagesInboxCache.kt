package com.stugram.app.data.repository

import com.stugram.app.ui.home.ChatMessage
import com.stugram.app.ui.home.GroupChat
import com.stugram.app.ui.home.RequestInboxItem
import com.stugram.app.ui.home.SuggestedChatProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MessagesInboxCache {
    private val _chats = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _groups = MutableStateFlow<List<GroupChat>>(emptyList())
    private val _requests = MutableStateFlow<List<RequestInboxItem>>(emptyList())
    private val _suggestedProfiles = MutableStateFlow<List<SuggestedChatProfile>>(emptyList())
    private val _unreadState = MutableStateFlow(MessagesUnreadState())

    private var directSummaryUnreadCount: Int = 0
    private var directSummaryLoaded = false
    private var chatsLoaded = false
    private var groupsLoaded = false
    private var requestsLoaded = false
    private var suggestionsLoaded = false
    private var lastFullRefreshAtMs = 0L

    val chats: StateFlow<List<ChatMessage>> = _chats.asStateFlow()
    val groups: StateFlow<List<GroupChat>> = _groups.asStateFlow()
    val requests: StateFlow<List<RequestInboxItem>> = _requests.asStateFlow()
    val suggestedProfiles: StateFlow<List<SuggestedChatProfile>> = _suggestedProfiles.asStateFlow()
    val unreadState: StateFlow<MessagesUnreadState> = _unreadState.asStateFlow()

    private fun publishUnreadState() {
        val directUnreadCount = if (directSummaryLoaded) {
            directSummaryUnreadCount.coerceAtLeast(0)
        } else if (chatsLoaded) {
            _chats.value.sumOf { it.unreadCount.coerceAtLeast(0) }
        } else {
            0
        }
        val groupUnreadCount = if (groupsLoaded) {
            _groups.value.sumOf { it.unreadCount.coerceAtLeast(0) }
        } else {
            0
        }
        _unreadState.value = MessagesUnreadState(
            directUnreadCount = directUnreadCount,
            groupUnreadCount = groupUnreadCount,
            totalUnreadCount = directUnreadCount + groupUnreadCount,
            unreadDirectConversations = _chats.value.count { it.unreadCount > 0 },
            unreadGroups = _groups.value.count { it.unreadCount > 0 }
        )
    }

    fun updateChats(items: List<ChatMessage>) {
        chatsLoaded = true
        _chats.value = items
        publishUnreadState()
    }

    fun upsertChat(item: ChatMessage) {
        val previousUnread = _chats.value.firstOrNull { existing ->
            existing.backendId == item.backendId || (existing.username != null && existing.username == item.username)
        }?.unreadCount ?: 0
        val deduped = _chats.value.filterNot { existing ->
            existing.backendId == item.backendId || (existing.username != null && existing.username == item.username)
        }
        chatsLoaded = true
        _chats.value = listOf(item) + deduped
        if (directSummaryLoaded) {
            directSummaryUnreadCount = (directSummaryUnreadCount - previousUnread + item.unreadCount).coerceAtLeast(0)
        }
        publishUnreadState()
    }

    fun patchChat(backendId: String, transform: (ChatMessage) -> ChatMessage) {
        var previousUnread = 0
        var nextUnread = 0
        _chats.value = _chats.value.map { item ->
            if (item.backendId == backendId) {
                previousUnread = item.unreadCount
                transform(item).also { updated -> nextUnread = updated.unreadCount }
            } else item
        }
        if (directSummaryLoaded) {
            directSummaryUnreadCount = (directSummaryUnreadCount - previousUnread + nextUnread).coerceAtLeast(0)
        }
        publishUnreadState()
    }

    fun removeChat(backendId: String) {
        val previousUnread = _chats.value.firstOrNull { it.backendId == backendId }?.unreadCount ?: 0
        _chats.value = _chats.value.filterNot { it.backendId == backendId }
        if (directSummaryLoaded) {
            directSummaryUnreadCount = (directSummaryUnreadCount - previousUnread).coerceAtLeast(0)
        }
        publishUnreadState()
    }

    fun updateGroups(items: List<GroupChat>) {
        groupsLoaded = true
        _groups.value = items
        publishUnreadState()
    }

    fun upsertGroup(item: GroupChat) {
        val deduped = _groups.value.filterNot { existing ->
            existing.backendId == item.backendId || existing.name == item.name
        }
        groupsLoaded = true
        _groups.value = listOf(item) + deduped
        publishUnreadState()
    }

    fun patchGroup(backendId: String, transform: (GroupChat) -> GroupChat) {
        _groups.value = _groups.value.map { item ->
            if (item.backendId == backendId) transform(item) else item
        }
        publishUnreadState()
    }

    fun removeGroup(backendId: String) {
        _groups.value = _groups.value.filterNot { it.backendId == backendId }
        publishUnreadState()
    }

    fun updateRequests(items: List<RequestInboxItem>) {
        requestsLoaded = true
        _requests.value = items
    }

    fun updateSuggestedProfiles(items: List<SuggestedChatProfile>) {
        suggestionsLoaded = true
        _suggestedProfiles.value = items
    }

    fun markFullRefresh() {
        lastFullRefreshAtMs = System.currentTimeMillis()
    }

    fun hasWarmInbox(maxAgeMs: Long = 20_000L): Boolean {
        val loadedEnough = chatsLoaded || groupsLoaded || requestsLoaded || suggestionsLoaded
        return loadedEnough && System.currentTimeMillis() - lastFullRefreshAtMs < maxAgeMs
    }

    fun setDirectUnreadSummary(totalUnreadMessages: Int) {
        directSummaryLoaded = true
        directSummaryUnreadCount = totalUnreadMessages.coerceAtLeast(0)
        publishUnreadState()
    }

    fun incrementChatUnread(
        conversationId: String,
        name: String = "New message",
        username: String? = null,
        avatar: String? = null,
        preview: String = "New message",
        time: String = "Now",
        amount: Int = 1
    ) {
        val existing = _chats.value.firstOrNull { it.backendId == conversationId }
        if (existing != null) {
            patchChat(conversationId) {
                it.copy(
                    lastMessage = preview,
                    time = time,
                    unreadCount = (it.unreadCount + amount).coerceAtLeast(0)
                )
            }
            return
        }

        if (!chatsLoaded && directSummaryLoaded) {
            directSummaryUnreadCount = (directSummaryUnreadCount + amount).coerceAtLeast(0)
            publishUnreadState()
            return
        }

        upsertChat(
            ChatMessage(
                backendId = conversationId,
                userId = null,
                name = name,
                username = username,
                avatar = avatar,
                lastMessage = preview,
                time = time,
                unreadCount = amount.coerceAtLeast(0)
            )
        )
    }

    fun incrementGroupUnread(
        groupId: String,
        name: String = "Group chat",
        avatar: String? = null,
        preview: String = "New message",
        time: String = "Now",
        amount: Int = 1
    ) {
        val existing = _groups.value.firstOrNull { it.backendId == groupId }
        if (existing != null) {
            patchGroup(groupId) {
                it.copy(
                    lastMessage = preview,
                    time = time,
                    unreadCount = (it.unreadCount + amount).coerceAtLeast(0)
                )
            }
            return
        }

        upsertGroup(
            GroupChat(
                backendId = groupId,
                name = name,
                avatar = avatar,
                lastMessage = preview,
                time = time,
                unreadCount = amount.coerceAtLeast(0)
            )
        )
    }

    fun clearChatUnread(conversationId: String): Boolean {
        val existing = _chats.value.firstOrNull { it.backendId == conversationId } ?: return false
        if (existing.unreadCount == 0) return true
        patchChat(conversationId) { it.copy(unreadCount = 0) }
        return true
    }

    fun clearGroupUnread(groupId: String): Boolean {
        val existing = _groups.value.firstOrNull { it.backendId == groupId } ?: return false
        if (existing.unreadCount == 0) return true
        patchGroup(groupId) { it.copy(unreadCount = 0) }
        return true
    }

    fun reset() {
        _chats.value = emptyList()
        _groups.value = emptyList()
        _requests.value = emptyList()
        _suggestedProfiles.value = emptyList()
        directSummaryUnreadCount = 0
        directSummaryLoaded = false
        chatsLoaded = false
        groupsLoaded = false
        requestsLoaded = false
        suggestionsLoaded = false
        lastFullRefreshAtMs = 0L
        publishUnreadState()
    }
}

data class MessagesUnreadState(
    val directUnreadCount: Int = 0,
    val groupUnreadCount: Int = 0,
    val totalUnreadCount: Int = 0,
    val unreadDirectConversations: Int = 0,
    val unreadGroups: Int = 0
)
