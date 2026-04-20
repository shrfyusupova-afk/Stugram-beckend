package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.PostModel
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface RecommendationApi {
    @GET("reels/me")
    suspend fun getMyReels(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<BaseResponse<List<PostModel>>>
}

