package com.stugram.app.data.repository

import com.stugram.app.data.remote.RetrofitClient

class ExploreRepository {
    private val exploreApi get() = RetrofitClient.exploreApi

    suspend fun getTrending(limit: Int = 20) = exploreApi.getTrending(limit)

    suspend fun getCreators(page: Int = 1, limit: Int = 20) = exploreApi.getCreators(page, limit)
}

