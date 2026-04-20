package com.stugram.app.data.repository

import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.HashtagResult
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.ProfileSummary
import retrofit2.Response

class SearchRepository {
    private val searchApi get() = RetrofitClient.searchApi

    suspend fun searchUsers(query: String, page: Int, limit: Int): Response<PaginatedResponse<ProfileSummary>> =
        searchApi.searchUsers(query, page, limit)

    suspend fun searchUsersAdvanced(
        region: String? = null,
        district: String? = null,
        school: String? = null,
        grade: String? = null,
        group: String? = null,
        page: Int,
        limit: Int
    ): Response<PaginatedResponse<ProfileSummary>> =
        searchApi.searchUsersAdvanced(region, district, school, grade, group, page, limit)

    suspend fun searchPosts(query: String, page: Int, limit: Int): Response<PaginatedResponse<PostModel>> =
        searchApi.searchPosts(query, page, limit)

    suspend fun getSearchSuggestions(query: String, limit: Int = 10) =
        searchApi.getSearchSuggestions(query, limit)

    suspend fun searchHashtags(query: String, page: Int, limit: Int) =
        searchApi.searchHashtags(query, page, limit)

    suspend fun getSearchHistory(page: Int, limit: Int) =
        searchApi.getSearchHistory(page, limit)

    suspend fun saveSearchHistory(queryText: String, searchType: String, targetId: String? = null) =
        searchApi.saveSearchHistory(com.stugram.app.data.remote.SaveSearchHistoryRequest(queryText, searchType, targetId))

    suspend fun deleteSearchHistoryItem(historyId: String) =
        searchApi.deleteSearchHistoryItem(historyId)

    suspend fun clearSearchHistory() =
        searchApi.clearSearchHistory()
}
