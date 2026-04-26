package com.stugram.app.data.repository

import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.ExploreTrendingData

class ExploreRepository {
    private val exploreApi get() = RetrofitClient.exploreApi

    suspend fun getTrending(limit: Int = 20) = exploreApi.getTrending(limit)

    suspend fun getTrendingData(limit: Int = 20): ExploreTrendingData? =
        exploreApi.getTrending(limit).body()?.data

    suspend fun getCreators(page: Int = 1, limit: Int = 20) = exploreApi.getCreators(page, limit)
}
