package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.ChatReplayEventsData
import com.stugram.app.data.remote.model.ChatSummaryModel
import com.stugram.app.data.remote.model.DirectConversationModel
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.SendChatMessageRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.HTTP
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Query

interface ChatApi {
    @POST("chats/conversations")
    suspend fun createConversation(
        @Body request: CreateConversationRequest
    ): Response<BaseResponse<DirectConversationModel>>

    @GET("chats/conversations")
    suspend fun getConversations(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<DirectConversationModel>>

    @GET("chats/conversations/search")
    suspend fun searchConversations(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<DirectConversationModel>>

    @GET("chats/summary")
    suspend fun getSummary(): Response<BaseResponse<ChatSummaryModel>>

    @GET("chats/events")
    suspend fun getConversationEvents(
        @Query("conversationId") conversationId: String,
        @Query("after") after: Long,
        @Query("limit") limit: Int
    ): Response<BaseResponse<ChatReplayEventsData>>

    @GET("chats/conversations/{conversationId}/messages")
    suspend fun getConversationMessages(
        @Path("conversationId") conversationId: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ChatMessageModel>>

    @GET("chats/conversations/{conversationId}")
    suspend fun getConversationDetail(
        @Path("conversationId") conversationId: String
    ): Response<BaseResponse<DirectConversationModel>>

    @POST("chats/conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Body request: SendChatMessageRequest
    ): Response<BaseResponse<ChatMessageModel>>

    @Multipart
    @POST("chats/conversations/{conversationId}/messages/media")
    suspend fun sendMediaMessage(
        @Path("conversationId") conversationId: String,
        @Part media: MultipartBody.Part,
        @Part("messageType") messageType: RequestBody,
        @Part("replyToMessageId") replyToMessageId: RequestBody? = null,
        @Part("clientId") clientId: RequestBody? = null
    ): Response<BaseResponse<ChatMessageModel>>

    @PATCH("chats/messages/{messageId}/seen")
    suspend fun markMessageSeen(
        @Path("messageId") messageId: String
    ): Response<BaseResponse<ChatMessageModel>>

    @PATCH("chats/messages/{messageId}/reaction")
    suspend fun updateReaction(
        @Path("messageId") messageId: String,
        @Body request: UpdateReactionRequest
    ): Response<BaseResponse<ChatMessageModel>>

    @PATCH("chats/messages/{messageId}")
    suspend fun editMessage(
        @Path("messageId") messageId: String,
        @Body request: EditMessageRequest
    ): Response<BaseResponse<ChatMessageModel>>

    @POST("chats/conversations/{conversationId}/messages/forward")
    suspend fun forwardMessage(
        @Path("conversationId") conversationId: String,
        @Body request: ForwardMessageRequest
    ): Response<BaseResponse<ChatMessageModel>>

    @POST("chats/conversations/{conversationId}/pin/{messageId}")
    suspend fun pinMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String
    ): Response<BaseResponse<DirectConversationModel>>

    @DELETE("chats/conversations/{conversationId}/pin")
    suspend fun unpinMessage(
        @Path("conversationId") conversationId: String
    ): Response<BaseResponse<DirectConversationModel>>

    @DELETE("chats/messages/{messageId}/reaction")
    suspend fun removeReaction(
        @Path("messageId") messageId: String
    ): Response<BaseResponse<ChatMessageModel>>

    @HTTP(method = "DELETE", path = "chats/messages/{messageId}", hasBody = true)
    suspend fun deleteMessage(
        @Path("messageId") messageId: String,
        @Body request: DeleteMessageRequest
    ): Response<BaseResponse<com.stugram.app.data.remote.model.MessageDeleteResult>>

    @POST("chats/conversations/{conversationId}/mute")
    suspend fun muteConversation(
        @Path("conversationId") conversationId: String,
        @Body request: MuteConversationRequest
    ): Response<BaseResponse<com.stugram.app.data.remote.model.SimpleFlagData>>

    @DELETE("chats/conversations/{conversationId}/mute")
    suspend fun unmuteConversation(
        @Path("conversationId") conversationId: String,
        @Body request: MuteConversationRequest
    ): Response<BaseResponse<com.stugram.app.data.remote.model.SimpleFlagData>>

    @GET("chats/conversations/{conversationId}/search")
    suspend fun searchConversationMessages(
        @Path("conversationId") conversationId: String,
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ChatMessageModel>>

    @POST("chats/reports")
    suspend fun reportUser(@Body request: ReportUserRequest): Response<BaseResponse<com.stugram.app.data.remote.model.SimpleFlagData>>

    @POST("chats/users/{userId}/block")
    suspend fun blockUser(@Path("userId") userId: String): Response<BaseResponse<com.stugram.app.data.remote.model.SimpleFlagData>>

    @DELETE("chats/users/{userId}/block")
    suspend fun unblockUser(@Path("userId") userId: String): Response<BaseResponse<com.stugram.app.data.remote.model.SimpleFlagData>>
}

data class CreateConversationRequest(
    val participantId: String
)

data class UpdateReactionRequest(
    val emoji: String
)

data class MuteConversationRequest(
    val durationMinutes: Int? = null
)

data class ReportUserRequest(
    val userId: String,
    val reason: String
)

data class EditMessageRequest(
    val text: String
)

data class ForwardMessageRequest(
    val sourceMessageId: String,
    val comment: String? = null
)

data class DeleteMessageRequest(
    val scope: String = "self"
)
