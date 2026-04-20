package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.CommentModel
import com.stugram.app.data.remote.model.CreateCommentRequest
import com.stugram.app.data.remote.model.PaginatedResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CommentApi {
    @POST("comments/posts/{postId}")
    suspend fun addComment(
        @Path("postId") postId: String,
        @Body request: CreateCommentRequest
    ): Response<BaseResponse<CommentModel>>

    @GET("comments/posts/{postId}")
    suspend fun getComments(
        @Path("postId") postId: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<CommentModel>>

    @DELETE("comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: String): Response<BaseResponse<DeleteResult>>
}
