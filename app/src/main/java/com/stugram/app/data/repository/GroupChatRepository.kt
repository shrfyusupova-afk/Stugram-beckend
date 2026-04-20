package com.stugram.app.data.repository

import android.content.Context
import android.net.Uri
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.GroupConversationModel
import com.stugram.app.data.remote.model.GroupMemberModel
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.SendChatMessageRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class GroupChatRepository {
    private val groupChatApi get() = RetrofitClient.groupChatApi
    private val authRepository by lazy { AuthRepository(RetrofitClient.requireTokenManager()) }

    suspend fun getGroupChats(page: Int, limit: Int): Response<PaginatedResponse<GroupConversationModel>> =
        groupChatApi.getGroupChats(page, limit)

    suspend fun getGroupChatDetail(groupId: String): Response<BaseResponse<GroupConversationModel>> =
        groupChatApi.getGroupChatDetail(groupId)

    suspend fun getGroupMembers(groupId: String, page: Int, limit: Int): Response<PaginatedResponse<GroupMemberModel>> =
        groupChatApi.getGroupMembers(groupId, page, limit)

    suspend fun getGroupMessages(groupId: String, page: Int, limit: Int): Response<PaginatedResponse<ChatMessageModel>> =
        groupChatApi.getGroupMessages(groupId, page, limit)

    suspend fun sendGroupMessage(groupId: String, request: SendChatMessageRequest): Response<BaseResponse<ChatMessageModel>> =
        sendWithRefresh { groupChatApi.sendGroupMessage(groupId, request) }

    suspend fun sendGroupMediaMessage(
        context: Context,
        groupId: String,
        uri: Uri,
        mimeType: String,
        replyToMessageId: String? = null,
        messageTypeOverride: String? = null
    ): Response<BaseResponse<ChatMessageModel>> {
        val resolvedType = messageTypeOverride ?: when {
            mimeType.startsWith("audio") -> "voice"
            mimeType.startsWith("video") -> "video"
            mimeType.startsWith("image") -> "image"
            else -> "file"
        }
        val file = context.copyUriToTempFile(uri, "group_chat_media", mimeType)
        return try {
            val mediaPart = MultipartBody.Part.createFormData(
                "media",
                file.name,
                file.asRequestBody(mimeType.toMediaType())
            )
            val messageTypePart = resolvedType.toRequestBody("text/plain".toMediaType())
            val replyPart = replyToMessageId?.toRequestBody("text/plain".toMediaType())
            sendWithRefresh { groupChatApi.sendGroupMediaMessage(groupId, mediaPart, messageTypePart, replyPart) }
        } finally {
            file.delete()
        }
    }

    suspend fun markGroupMessageSeen(groupId: String, messageId: String): Response<BaseResponse<ChatMessageModel>> =
        groupChatApi.markGroupMessageSeen(groupId, messageId)

    suspend fun searchGroupMessages(groupId: String, query: String, page: Int = 1, limit: Int = 30) =
        groupChatApi.searchGroupMessages(groupId, query, page, limit)

    suspend fun updateGroupMessageReaction(groupId: String, messageId: String, emoji: String) =
        groupChatApi.updateGroupMessageReaction(groupId, messageId, com.stugram.app.data.remote.UpdateReactionRequest(emoji))

    suspend fun editGroupMessage(groupId: String, messageId: String, text: String) =
        sendWithRefresh { groupChatApi.editGroupMessage(groupId, messageId, com.stugram.app.data.remote.EditMessageRequest(text)) }

    suspend fun forwardGroupMessage(groupId: String, sourceMessageId: String, comment: String? = null) =
        sendWithRefresh { groupChatApi.forwardGroupMessage(groupId, com.stugram.app.data.remote.ForwardMessageRequest(sourceMessageId, comment)) }

    suspend fun pinGroupMessage(groupId: String, messageId: String) =
        sendWithRefresh { groupChatApi.pinGroupMessage(groupId, messageId) }

    suspend fun unpinGroupMessage(groupId: String) =
        sendWithRefresh { groupChatApi.unpinGroupMessage(groupId) }

    suspend fun removeGroupMessageReaction(groupId: String, messageId: String) =
        groupChatApi.removeGroupMessageReaction(groupId, messageId)

    suspend fun deleteGroupMessage(groupId: String, messageId: String, scope: String = "self") =
        sendWithRefresh { groupChatApi.deleteGroupMessage(groupId, messageId, com.stugram.app.data.remote.DeleteMessageRequest(scope)) }

    suspend fun leaveGroup(groupId: String) =
        groupChatApi.leaveGroup(groupId)

    suspend fun addMembers(groupId: String, userIds: List<String>) =
        groupChatApi.addMembers(groupId, com.stugram.app.data.remote.GroupMembersRequest(userIds))

    suspend fun removeMember(groupId: String, userId: String) =
        groupChatApi.removeMember(groupId, userId)

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
