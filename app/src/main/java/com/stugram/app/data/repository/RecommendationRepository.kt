package com.stugram.app.data.repository

import com.stugram.app.data.remote.RetrofitClient

class RecommendationRepository {
    private val recommendationApi get() = RetrofitClient.recommendationApi

    suspend fun getMyReels(page: Int = 1, limit: Int = 20) =
        recommendationApi.getMyReels(page, limit)
}

