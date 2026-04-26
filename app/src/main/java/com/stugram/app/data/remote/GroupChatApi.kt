package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.GroupConversationModel
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.ChatReplayEventsData
import com.stugram.app.data.remote.model.GroupMemberModel
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.MessageDeleteResult
import com.stugram.app.data.remote.model.SendChatMessageRequest
import com.stugram.app.data.remote.model.SimpleFlagData
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.HTTP
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.DELETE

interface GroupChatApi {
    @Multipart
    @POST("group-chats")
    suspend fun createGroupChat(
        @Part("name") name: RequestBody,
        @Part("memberIds") memberIds: RequestBody,
        @Part avatar: MultipartBody.Part? = null
    ): Response<BaseResponse<GroupConversationModel>>

    @POST("group-chats/{groupId}/leave")
    suspend fun leaveGroup(
        @Path("groupId") groupId: String
    ): Response<BaseResponse<com.stugram.app.data.remote.model.SimpleFlagData>>

    @POST("group-chats/{groupId}/members")
    suspend fun addMembers(
        @Path("groupId") groupId: String,
        @Body request: GroupMembersRequest
    ): Response<BaseResponse<com.stugram.app.data.remote.model.SimpleFlagData>>

    @DELETE("group-chats/{groupId}/members/{userId}")
    suspend fun removeMember(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String
    ): Response<BaseResponse<com.stugram.app.data.remote.model.SimpleFlagData>>
    @GET("group-chats")
    suspend fun getGroupChats(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<GroupConversationModel>>

    @GET("group-chats/events")
    suspend fun getGroupEvents(
        @Query("groupId") groupId: String,
        @Query("after") after: Long,
        @Query("limit") limit: Int
    ): Response<BaseResponse<ChatReplayEventsData>>

    @GET("group-chats/{groupId}")
    suspend fun getGroupChatDetail(@Path("groupId") groupId: String): Response<BaseResponse<GroupConversationModel>>

    @GET("group-chats/{groupId}/members")
    suspend fun getGroupMembers(
        @Path("groupId") groupId: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<GroupMemberModel>>

    @GET("group-chats/{groupId}/messages")
    suspend fun getGroupMessages(
        @Path("groupId") groupId: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ChatMessageModel>>

    @POST("group-chats/{groupId}/messages/forward")
    suspend fun forwardGroupMessage(
        @Path("groupId") groupId: String,
        @Body request: ForwardMessageRequest
    ): Response<BaseResponse<ChatMessageModel>>

    @POST("group-chats/{groupId}/messages")
    suspend fun sendGroupMessage(
        @Path("groupId") groupId: String,
        @Body request: SendChatMessageRequest
    ): Response<BaseResponse<ChatMessageModel>>

    @Multipart
    @POST("group-chats/{groupId}/messages")
    suspend fun sendGroupMediaMessage(
        @Path("groupId") groupId: String,
        @Part media: MultipartBody.Part,
        @Part("messageType") messageType: RequestBody,
        @Part("replyToMessageId") replyToMessageId: RequestBody? = null,
        @Part("clientId") clientId: RequestBody? = null
    ): Response<BaseResponse<ChatMessageModel>>

    @PATCH("group-chats/{groupId}/messages/{messageId}/seen")
    suspend fun markGroupMessageSeen(
        @Path("groupId") groupId: String,
        @Path("messageId") messageId: String
    ): Response<BaseResponse<ChatMessageModel>>

    @GET("group-chats/{groupId}/search")
    suspend fun searchGroupMessages(
        @Path("groupId") groupId: String,
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ChatMessageModel>>

    @PATCH("group-chats/{groupId}/messages/{messageId}/reaction")
    suspend fun updateGroupMessageReaction(
        @Path("groupId") groupId: String,
        @Path("messageId") messageId: String,
        @Body request: com.stugram.app.data.remote.UpdateReactionRequest
    ): Response<BaseResponse<ChatMessageModel>>

    @PATCH("group-chats/{groupId}/messages/{messageId}")
    suspend fun editGroupMessage(
        @Path("groupId") groupId: String,
        @Path("messageId") messageId: String,
        @Body request: EditMessageRequest
    ): Response<BaseResponse<ChatMessageModel>>

    @POST("group-chats/{groupId}/pin/{messageId}")
    suspend fun pinGroupMessage(
        @Path("groupId") groupId: String,
        @Path("messageId") messageId: String
    ): Response<BaseResponse<GroupConversationModel>>

    @DELETE("group-chats/{groupId}/pin")
    suspend fun unpinGroupMessage(
        @Path("groupId") groupId: String
    ): Response<BaseResponse<GroupConversationModel>>

    @DELETE("group-chats/{groupId}/messages/{messageId}/reaction")
    suspend fun removeGroupMessageReaction(
        @Path("groupId") groupId: String,
        @Path("messageId") messageId: String
    ): Response<BaseResponse<ChatMessageModel>>

    @HTTP(method = "DELETE", path = "group-chats/{groupId}/messages/{messageId}", hasBody = true)
    suspend fun deleteGroupMessage(
        @Path("groupId") groupId: String,
        @Path("messageId") messageId: String,
        @Body request: DeleteMessageRequest
    ): Response<BaseResponse<MessageDeleteResult>>
}

data class GroupMembersRequest(
    val userIds: List<String>
)
