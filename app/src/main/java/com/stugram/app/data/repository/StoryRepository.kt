package com.stugram.app.data.repository

import android.content.Context
import android.net.Uri
import com.stugram.app.data.remote.DeleteResult
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.SendChatMessageRequest
import com.stugram.app.data.remote.model.StoryInsightsModel
import com.stugram.app.data.remote.model.StoryModel
import com.stugram.app.data.remote.model.StoryReplyToDmData
import com.stugram.app.data.remote.model.StoryViewerModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class StoryRepository {
    private val storyApi get() = RetrofitClient.storyApi
    private val mediaApi get() = RetrofitClient.mediaApi

    suspend fun createStory(context: Context, mediaUri: Uri, caption: String?): Response<BaseResponse<StoryModel>> {
        val mimeType = context.contentResolver.getType(mediaUri) ?: "image/*"
        val file = context.copyUriToTempFile(mediaUri, "story", mimeType)
        return try {
            val mediaPart = MultipartBody.Part.createFormData(
                "media",
                file.name,
                file.asRequestBody(mimeType.toMediaType())
            )
            val captionPart = caption?.toRequestBody("text/plain".toMediaType())
            mediaApi.createStory(mediaPart, captionPart)
        } finally {
            file.delete()
        }
    }

    suspend fun getStoriesFeed(page: Int, limit: Int): Response<PaginatedResponse<StoryModel>> =
        storyApi.getStoriesFeed(page, limit)

    suspend fun getUserStories(username: String, page: Int, limit: Int): Response<PaginatedResponse<StoryModel>> =
        storyApi.getUserStories(username, page, limit)

    suspend fun markViewed(storyId: String): Response<BaseResponse<StoryModel>> =
        storyApi.markViewed(storyId)

    suspend fun likeStory(storyId: String): Response<BaseResponse<StoryModel>> =
        storyApi.likeStory(storyId)

    suspend fun unlikeStory(storyId: String): Response<BaseResponse<StoryModel>> =
        storyApi.unlikeStory(storyId)

    suspend fun replyToStory(storyId: String, text: String): Response<BaseResponse<StoryReplyToDmData>> =
        storyApi.replyToStory(storyId, SendChatMessageRequest(text = text))

    suspend fun getStoryViewers(storyId: String): Response<PaginatedResponse<StoryViewerModel>> =
        storyApi.getStoryViewers(storyId)

    suspend fun getStoryLikes(storyId: String): Response<PaginatedResponse<com.stugram.app.data.remote.model.StoryLikeModel>> =
        storyApi.getStoryLikes(storyId)

    suspend fun getStoryReplies(storyId: String): Response<PaginatedResponse<com.stugram.app.data.remote.model.StoryReplyModel>> =
        storyApi.getStoryReplies(storyId)

    suspend fun getStoryInsights(storyId: String): Response<BaseResponse<StoryInsightsModel>> =
        storyApi.getStoryInsights(storyId)

    suspend fun deleteStory(storyId: String): Response<BaseResponse<DeleteResult>> =
        storyApi.deleteStory(storyId)

}
