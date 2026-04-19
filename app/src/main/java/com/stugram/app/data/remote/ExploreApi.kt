package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.ProfileSummary
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ExploreApi {
    @GET("explore/trending")
    suspend fun getTrending(
        @Query("limit") limit: Int = 20
    ): Response<BaseResponse<List<PostModel>>>

    @GET("explore/creators")
    suspend fun getCreators(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ProfileSummary>>
}

