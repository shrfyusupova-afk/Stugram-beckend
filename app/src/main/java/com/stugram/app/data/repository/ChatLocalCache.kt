package com.stugram.app.data.repository

import android.content.Context
import com.stugram.app.data.local.chat.ChatRoomStore
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.domain.chat.MessageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

object ChatLocalCache {
    private const val DIRECT_SCOPE = "direct"
    private const val GROUP_SCOPE = "group"

    fun upsertDirect(
        context: Context,
        conversationId: String,
        messages: List<ChatMessageModel>,
        source: MessageSource = MessageSource.SocketEvent
    ) {
        if (conversationId.isBlank() || messages.isEmpty()) return
        runBlocking(Dispatchers.IO) {
            val store = ChatRoomStore.get(context)
            messages.forEach { message ->
                store.applyRemoteMessage(DIRECT_SCOPE, conversationId, message, source)
            }
        }
    }

    fun upsertGroup(
        context: Context,
        groupId: String,
        messages: List<ChatMessageModel>,
        source: MessageSource = MessageSource.SocketEvent
    ) {
        if (groupId.isBlank() || messages.isEmpty()) return
        runBlocking(Dispatchers.IO) {
            val store = ChatRoomStore.get(context)
            messages.forEach { message ->
                store.applyRemoteMessage(GROUP_SCOPE, groupId, message, source)
            }
        }
    }

    fun loadDirect(context: Context, conversationId: String, limit: Int = 60): List<ChatMessageModel> =
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).loadMessageModels(DIRECT_SCOPE, conversationId).takeLast(limit.coerceAtLeast(1))
        }

    fun loadGroup(context: Context, groupId: String, limit: Int = 60): List<ChatMessageModel> =
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).loadMessageModels(GROUP_SCOPE, groupId).takeLast(limit.coerceAtLeast(1))
        }

    fun observeDirect(context: Context, conversationId: String): Flow<List<ChatMessageModel>> =
        ChatRoomStore.get(context).observeMessageModels(DIRECT_SCOPE, conversationId)

    fun observeGroup(context: Context, groupId: String): Flow<List<ChatMessageModel>> =
        ChatRoomStore.get(context).observeMessageModels(GROUP_SCOPE, groupId)

    fun removeDirect(context: Context, conversationId: String, messageId: String) {
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).applyDelete(DIRECT_SCOPE, conversationId, messageId)
        }
    }

    fun removeGroup(context: Context, groupId: String, messageId: String) {
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).applyDelete(GROUP_SCOPE, groupId, messageId)
        }
    }
}
