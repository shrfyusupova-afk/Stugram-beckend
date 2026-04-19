package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.LikeActionData
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.PostInteractionHistoryItem
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LikeApi {
    @GET("likes/posts/me")
    suspend fun getLikedPosts(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<PostInteractionHistoryItem>>

    @POST("likes/posts/{postId}")
    suspend fun likePost(@Path("postId") postId: String): Response<BaseResponse<LikeActionData>>

    @DELETE("likes/posts/{postId}")
    suspend fun unlikePost(@Path("postId") postId: String): Response<BaseResponse<LikeActionData>>
}
