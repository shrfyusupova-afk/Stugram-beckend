package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.PostInteractionHistoryItem
import com.stugram.app.data.remote.model.SavedPostActionData
import com.stugram.app.data.remote.model.UpdatePostRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface PostApi {
    @PATCH("posts/{postId}")
    suspend fun updatePost(
        @Path("postId") postId: String,
        @Body request: UpdatePostRequest
    ): Response<BaseResponse<PostModel>>

    @DELETE("posts/{postId}")
    suspend fun deletePost(@Path("postId") postId: String): Response<BaseResponse<DeleteResult>>

    @GET("posts/{postId}")
    suspend fun getPost(@Path("postId") postId: String): Response<BaseResponse<PostModel>>

    @GET("posts/user/{username}")
    suspend fun getUserPosts(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<PostModel>>

    @GET("posts/feed/me")
    suspend fun getFeed(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<PostModel>>

    @POST("posts/{postId}/save")
    suspend fun savePost(@Path("postId") postId: String): Response<BaseResponse<SavedPostActionData>>

    @DELETE("posts/{postId}/save")
    suspend fun unsavePost(@Path("postId") postId: String): Response<BaseResponse<SavedPostActionData>>

    @GET("posts/saved/me")
    suspend fun getSavedPosts(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<PostInteractionHistoryItem>>
}

data class DeleteResult(
    val deleted: Boolean = false,
    val updated: Boolean = false,
    val unfollowed: Boolean = false,
    val removed: Boolean = false,
    val left: Boolean = false,
    val status: String? = null
)
