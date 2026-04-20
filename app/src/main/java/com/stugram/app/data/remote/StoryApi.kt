package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.SendChatMessageRequest
import com.stugram.app.data.remote.model.StoryInsightsModel
import com.stugram.app.data.remote.model.StoryModel
import com.stugram.app.data.remote.model.StoryReplyToDmData
import com.stugram.app.data.remote.model.StoryViewerModel
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query

interface StoryApi {
    @GET("stories/feed/me")
    suspend fun getStoriesFeed(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<StoryModel>>

    @GET("stories/user/{username}")
    suspend fun getUserStories(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<StoryModel>>

    @PATCH("stories/{storyId}/view")
    suspend fun markViewed(@Path("storyId") storyId: String): Response<BaseResponse<StoryModel>>

    @POST("stories/{storyId}/like")
    suspend fun likeStory(@Path("storyId") storyId: String): Response<BaseResponse<StoryModel>>

    @DELETE("stories/{storyId}/like")
    suspend fun unlikeStory(@Path("storyId") storyId: String): Response<BaseResponse<StoryModel>>

    @POST("stories/{storyId}/reply")
    suspend fun replyToStory(
        @Path("storyId") storyId: String,
        @Body request: SendChatMessageRequest
    ): Response<BaseResponse<StoryReplyToDmData>>

    @GET("stories/{storyId}/insights")
    suspend fun getStoryInsights(@Path("storyId") storyId: String): Response<BaseResponse<StoryInsightsModel>>

    @GET("stories/{storyId}/viewers")
    suspend fun getStoryViewers(
        @Path("storyId") storyId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<PaginatedResponse<StoryViewerModel>>

    @GET("stories/{storyId}/likes")
    suspend fun getStoryLikes(
        @Path("storyId") storyId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<PaginatedResponse<com.stugram.app.data.remote.model.StoryLikeModel>>

    @GET("stories/{storyId}/replies")
    suspend fun getStoryReplies(
        @Path("storyId") storyId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<PaginatedResponse<com.stugram.app.data.remote.model.StoryReplyModel>>

    @DELETE("stories/{storyId}")
    suspend fun deleteStory(@Path("storyId") storyId: String): Response<BaseResponse<com.stugram.app.data.remote.DeleteResult>>
}
