package com.stugram.app.data.repository

import android.content.Context
import android.net.Uri
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.domain.chat.ChatDomainEvent
import com.stugram.app.data.remote.model.DirectConversationModel
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.SendChatMessageRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

class ChatRepository {
    private val chatApi get() = RetrofitClient.chatApi
    private val authRepository by lazy { AuthRepository(RetrofitClient.requireTokenManager()) }

    suspend fun createConversation(participantId: String): Response<BaseResponse<DirectConversationModel>> =
        chatApi.createConversation(com.stugram.app.data.remote.CreateConversationRequest(participantId))

    suspend fun getConversations(page: Int, limit: Int): Response<PaginatedResponse<DirectConversationModel>> =
        chatApi.getConversations(page, limit)

    suspend fun searchConversations(query: String, page: Int = 1, limit: Int = 50): Response<PaginatedResponse<DirectConversationModel>> =
        chatApi.searchConversations(query, page, limit)

    suspend fun getSummary() =
        chatApi.getSummary()

    suspend fun getDirectEvents(
        conversationId: String,
        afterSequence: Long,
        limit: Int = 100
    ): List<ChatDomainEvent> {
        val response = chatApi.getConversationEvents(conversationId, afterSequence, limit)
        if (!response.isSuccessful) return emptyList()
        return response.body()?.data?.events.orEmpty()
            .sortedBy { it.sequence }
            .mapNotNull(ChatReplayEventMapper::toDomainEvent)
    }

    suspend fun getConversationMessages(conversationId: String, page: Int, limit: Int): Response<PaginatedResponse<ChatMessageModel>> =
        chatApi.getConversationMessages(conversationId, page, limit)

    suspend fun getConversationDetail(conversationId: String): Response<BaseResponse<DirectConversationModel>> =
        chatApi.getConversationDetail(conversationId)

    suspend fun sendMessage(conversationId: String, request: SendChatMessageRequest): Response<BaseResponse<ChatMessageModel>> =
        sendWithRefresh { chatApi.sendMessage(conversationId, request) }

    suspend fun sendMediaMessage(
        context: Context,
        conversationId: String,
        uri: Uri,
        mimeType: String,
        replyToMessageId: String? = null,
        messageTypeOverride: String? = null,
        clientId: String? = null
    ): Response<BaseResponse<ChatMessageModel>> {
        val resolvedType = messageTypeOverride ?: when {
            mimeType.startsWith("audio") -> "voice"
            mimeType.startsWith("video") -> "video"
            mimeType.startsWith("image") -> "image"
            else -> "file"
        }
        val file = context.copyUriToTempFile(uri, "chat_media", mimeType)
        return try {
            val mediaPart = MultipartBody.Part.createFormData(
                "media",
                file.name,
                file.asRequestBody(mimeType.toMediaType())
            )
            val messageTypePart = resolvedType.toRequestBody("text/plain".toMediaType())
            val replyPart = replyToMessageId?.toRequestBody("text/plain".toMediaType())
            val clientIdPart = clientId?.toRequestBody("text/plain".toMediaType())
            sendWithRefresh { chatApi.sendMediaMessage(conversationId, mediaPart, messageTypePart, replyPart, clientIdPart) }
        } finally {
            file.delete()
        }
    }

    suspend fun sendStagedMediaMessage(
        conversationId: String,
        file: File,
        mimeType: String,
        replyToMessageId: String? = null,
        messageTypeOverride: String? = null,
        clientId: String? = null,
        displayName: String? = null
    ): Response<BaseResponse<ChatMessageModel>> {
        val resolvedType = messageTypeOverride ?: when {
            mimeType.startsWith("audio") -> "voice"
            mimeType.startsWith("video") -> "video"
            mimeType.startsWith("image") -> "image"
            else -> "file"
        }
        val mediaPart = MultipartBody.Part.createFormData(
            "media",
            displayName?.takeIf { it.isNotBlank() } ?: file.name,
            file.asRequestBody(mimeType.toMediaType())
        )
        val messageTypePart = resolvedType.toRequestBody("text/plain".toMediaType())
        val replyPart = replyToMessageId?.toRequestBody("text/plain".toMediaType())
        val clientIdPart = clientId?.toRequestBody("text/plain".toMediaType())
        return sendWithRefresh { chatApi.sendMediaMessage(conversationId, mediaPart, messageTypePart, replyPart, clientIdPart) }
    }

    suspend fun markMessageSeen(messageId: String): Response<BaseResponse<ChatMessageModel>> =
        chatApi.markMessageSeen(messageId)

    suspend fun updateReaction(messageId: String, emoji: String) =
        chatApi.updateReaction(messageId, com.stugram.app.data.remote.UpdateReactionRequest(emoji))

    suspend fun editMessage(messageId: String, text: String) =
        sendWithRefresh { chatApi.editMessage(messageId, com.stugram.app.data.remote.EditMessageRequest(text)) }

    suspend fun forwardMessage(conversationId: String, sourceMessageId: String, comment: String? = null) =
        sendWithRefresh { chatApi.forwardMessage(conversationId, com.stugram.app.data.remote.ForwardMessageRequest(sourceMessageId, comment)) }

    suspend fun pinMessage(conversationId: String, messageId: String) =
        sendWithRefresh { chatApi.pinMessage(conversationId, messageId) }

    suspend fun unpinMessage(conversationId: String) =
        sendWithRefresh { chatApi.unpinMessage(conversationId) }

    suspend fun removeReaction(messageId: String) =
        chatApi.removeReaction(messageId)

    suspend fun deleteMessage(messageId: String, scope: String = "self") =
        sendWithRefresh { chatApi.deleteMessage(messageId, com.stugram.app.data.remote.DeleteMessageRequest(scope)) }

    suspend fun muteConversation(conversationId: String, durationMinutes: Int? = null) =
        chatApi.muteConversation(conversationId, com.stugram.app.data.remote.MuteConversationRequest(durationMinutes))

    suspend fun unmuteConversation(conversationId: String, durationMinutes: Int? = null) =
        chatApi.unmuteConversation(conversationId, com.stugram.app.data.remote.MuteConversationRequest(durationMinutes))

    suspend fun searchConversationMessages(conversationId: String, query: String, page: Int = 1, limit: Int = 30) =
        chatApi.searchConversationMessages(conversationId, query, page, limit)

    suspend fun reportUser(userId: String, reason: String) =
        chatApi.reportUser(com.stugram.app.data.remote.ReportUserRequest(userId = userId, reason = reason))

    suspend fun blockUser(userId: String) =
        chatApi.blockUser(userId)

    suspend fun unblockUser(userId: String) =
        chatApi.unblockUser(userId)

    private suspend fun <T> sendWithRefresh(call: suspend () -> Response<T>): Response<T> {
        val initial = call()
        if (initial.code() != 401) return initial

        val refresh = authRepository.refreshToken()
        if (refresh !is com.stugram.app.data.remote.model.ApiResult.Success) {
            return initial
        }

        return call()
    }
}
