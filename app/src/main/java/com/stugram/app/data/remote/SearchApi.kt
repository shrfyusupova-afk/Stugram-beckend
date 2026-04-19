package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.HashtagResult
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.ProfileSummary
import retrofit2.Response
import retrofit2.http.*

interface SearchApi {
    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ProfileSummary>>

    @GET("search/users/advanced")
    suspend fun searchUsersAdvanced(
        @Query("region") region: String? = null,
        @Query("district") district: String? = null,
        @Query("school") school: String? = null,
        @Query("grade") grade: String? = null,
        @Query("group") group: String? = null,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ProfileSummary>>

    @GET("search/posts")
    suspend fun searchPosts(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<PostModel>>

    @GET("search/suggestions")
    suspend fun getSearchSuggestions(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): Response<PaginatedResponse<String>>

    @GET("search/hashtags")
    suspend fun searchHashtags(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<HashtagResult>>

    @GET("search/history")
    suspend fun getSearchHistory(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<SearchHistoryItem>>

    @POST("search/history")
    suspend fun saveSearchHistory(
        @Body request: SaveSearchHistoryRequest
    ): Response<SearchHistoryItem>

    @DELETE("search/history/{historyId}")
    suspend fun deleteSearchHistoryItem(
        @Path("historyId") historyId: String
    ): Response<Unit>

    @DELETE("search/history")
    suspend fun clearSearchHistory(): Response<Unit>
}

data class SearchHistoryItem(
    val _id: String,
    val queryText: String,
    val searchType: String, // "text", "user"
    val targetId: String? = null,
    val updatedAt: String
)

data class SaveSearchHistoryRequest(
    val queryText: String,
    val searchType: String,
    val targetId: String? = null
)
