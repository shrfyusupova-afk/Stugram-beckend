package com.stugram.app.data.repository

import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.FollowActionData
import com.stugram.app.data.remote.model.FollowRequestModel
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.remote.model.SimpleFlagData
import retrofit2.Response

class FollowRepository {
    private val followApi get() = RetrofitClient.followApi

    suspend fun followUser(userId: String): Response<BaseResponse<FollowActionData>> =
        followApi.followUser(userId)

    suspend fun unfollowUser(userId: String): Response<BaseResponse<SimpleFlagData>> =
        followApi.unfollowUser(userId)

    suspend fun getFollowRequests(page: Int, limit: Int): Response<PaginatedResponse<FollowRequestModel>> =
        followApi.getFollowRequests(page, limit)

    suspend fun acceptFollowRequest(requestId: String): Response<BaseResponse<FollowRequestModel>> =
        followApi.acceptFollowRequest(requestId)

    suspend fun rejectFollowRequest(requestId: String): Response<BaseResponse<FollowRequestModel>> =
        followApi.rejectFollowRequest(requestId)

    suspend fun getFollowers(username: String, page: Int, limit: Int): Response<PaginatedResponse<ProfileSummary>> =
        followApi.getFollowers(username, page, limit)

    suspend fun getFollowing(username: String, page: Int, limit: Int): Response<PaginatedResponse<ProfileSummary>> =
        followApi.getFollowing(username, page, limit)

    suspend fun removeFollower(userId: String): Response<BaseResponse<SimpleFlagData>> =
        followApi.removeFollower(userId)
}
