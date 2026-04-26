package com.stugram.app.data.repository

import android.content.Context
import com.stugram.app.data.local.chat.ChatRoomStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

data class PendingChatPayload(
    val text: String? = null,
    val replyToMessageId: String? = null,
    val mediaUri: String? = null,
    val mediaLocalPath: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val originalDisplayName: String? = null,
    val messageTypeOverride: String? = null
)

data class PendingChatEnvelope(
    val localId: String,
    val scope: String,
    val targetId: String,
    val status: String,
    val payload: PendingChatPayload,
    val createdAt: Long,
    val retryCount: Int = 0,
    val nextAttemptAt: Long? = null,
    val errorCode: String? = null,
    val terminalError: String? = null,
    val terminalFailedAt: Long? = null
)

object ChatPendingOutbox {
    private const val DRAFT_PREFS = "stugram_chat_pending_outbox"
    private const val DRAFT_PREFIX = "draft:"
    private const val DIRECT_SCOPE = "direct"
    private const val GROUP_SCOPE = "group"

    fun upsertDirect(context: Context, conversationId: String, localId: String, payload: PendingChatPayload) {
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).upsertPending(DIRECT_SCOPE, conversationId, localId, payload)
        }
    }

    fun upsertGroup(context: Context, groupId: String, localId: String, payload: PendingChatPayload) {
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).upsertPending(GROUP_SCOPE, groupId, localId, payload)
        }
    }

    fun markRetry(
        context: Context,
        localId: String,
        nextAttemptAt: Long = System.currentTimeMillis(),
        errorCode: String? = null
    ) {
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).markPendingRetry(localId, nextAttemptAt, errorCode)
        }
    }

    fun markSending(context: Context, localId: String) {
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).markPendingSending(localId)
        }
    }

    fun markTerminalFailure(context: Context, localId: String, message: String) {
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).markTerminalFailure(localId, message)
        }
    }

    fun markTerminalFailureAndRemove(context: Context, localId: String, errorCode: String, message: String) {
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).markTerminalFailureAndRemove(localId, errorCode, message)
        }
    }

    fun remove(context: Context, localId: String) {
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).removePending(localId)
        }
    }

    fun loadDirect(context: Context, conversationId: String): List<PendingChatEnvelope> =
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).loadPending(DIRECT_SCOPE, conversationId)
        }

    fun loadGroup(context: Context, groupId: String): List<PendingChatEnvelope> =
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).loadPending(GROUP_SCOPE, groupId)
        }

    fun loadPending(context: Context): List<PendingChatEnvelope> =
        runBlocking(Dispatchers.IO) {
            ChatRoomStore.get(context).loadActivePending()
        }

    fun observeDirect(context: Context, conversationId: String): Flow<List<PendingChatEnvelope>> =
        ChatRoomStore.get(context).observePending(DIRECT_SCOPE, conversationId)

    fun observeGroup(context: Context, groupId: String): Flow<List<PendingChatEnvelope>> =
        ChatRoomStore.get(context).observePending(GROUP_SCOPE, groupId)

    fun saveDraft(context: Context, key: String, text: String) {
        val prefs = context.applicationContext.getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE)
        if (text.isBlank()) {
            prefs.edit().remove(DRAFT_PREFIX + key).apply()
        } else {
            prefs.edit().putString(DRAFT_PREFIX + key, text).apply()
        }
    }

    fun loadDraft(context: Context, key: String): String =
        context.applicationContext
            .getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE)
            .getString(DRAFT_PREFIX + key, "")
            .orEmpty()

    fun clearDraft(context: Context, key: String) {
        context.applicationContext
            .getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(DRAFT_PREFIX + key)
            .apply()
    }
}
