package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.FollowActionData
import com.stugram.app.data.remote.model.FollowRequestModel
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.remote.model.SimpleFlagData
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FollowApi {
    @POST("follows/{userId}")
    suspend fun followUser(@Path("userId") userId: String): Response<BaseResponse<FollowActionData>>

    @DELETE("follows/{userId}")
    suspend fun unfollowUser(@Path("userId") userId: String): Response<BaseResponse<SimpleFlagData>>

    @GET("follows/requests/me")
    suspend fun getFollowRequests(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<FollowRequestModel>>

    @POST("follows/requests/{requestId}/accept")
    suspend fun acceptFollowRequest(@Path("requestId") requestId: String): Response<BaseResponse<FollowRequestModel>>

    @POST("follows/requests/{requestId}/reject")
    suspend fun rejectFollowRequest(@Path("requestId") requestId: String): Response<BaseResponse<FollowRequestModel>>

    @GET("follows/{username}/followers")
    suspend fun getFollowers(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ProfileSummary>>

    @GET("follows/{username}/following")
    suspend fun getFollowing(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ProfileSummary>>

    @DELETE("follows/followers/{userId}")
    suspend fun removeFollower(
        @Path("userId") userId: String
    ): Response<BaseResponse<SimpleFlagData>>
}
