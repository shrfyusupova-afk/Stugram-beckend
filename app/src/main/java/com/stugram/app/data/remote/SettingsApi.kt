package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.SimpleFlagData
import retrofit2.Response
import retrofit2.http.*

interface SettingsApi {
    @GET("settings/me")
    suspend fun getSettings(): Response<BaseResponse<UserSettingsModel>>

    @PATCH("settings/me")
    suspend fun updateSettings(@Body updates: Map<String, Any>): Response<BaseResponse<UserSettingsModel>>

    @GET("settings/me/notifications")
    suspend fun getNotificationSettings(): Response<BaseResponse<NotificationSettingsModel>>

    @PATCH("settings/me/notifications")
    suspend fun updateNotificationSettings(@Body updates: NotificationSettingsModel): Response<BaseResponse<NotificationSettingsModel>>

    @GET("blocks/me")
    suspend fun getBlockedUsers(): Response<BaseResponse<List<BlockedUserModel>>>

    @DELETE("blocks/{userId}")
    suspend fun unblockUser(@Path("userId") userId: String): Response<BaseResponse<Unit>>

    @POST("blocks/{userId}")
    suspend fun blockUser(@Path("userId") userId: String): Response<BaseResponse<Unit>>

    @GET("auth/sessions")
    suspend fun getLoginSessions(): Response<BaseResponse<List<LoginSessionModel>>>

    @DELETE("auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: String): Response<BaseResponse<Unit>>

    @GET("settings/me/hidden-words")
    suspend fun getHiddenWords(): Response<BaseResponse<List<String>>>

    @PATCH("settings/me/hidden-words")
    suspend fun updateHiddenWords(@Body words: List<String>): Response<BaseResponse<List<String>>>

    @POST("auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<BaseResponse<Unit>>
}

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class UserSettingsModel(
    val _id: String,
    val userId: String,
    val isPrivateAccount: Boolean = false,
    val isDarkMode: Boolean = true,
    val readReceipts: Boolean = true,
    val dataSaver: Boolean = false,
    val videoAutoPlay: Boolean = true,
    val sensitiveFilter: Boolean = false,
    val notifications: NotificationSettingsModel = NotificationSettingsModel()
)

data class NotificationSettingsModel(
    val likes: Boolean = true,
    val comments: Boolean = true,
    val followRequests: Boolean = true,
    val messages: Boolean = true
)

data class BlockedUserModel(
    val _id: String,
    val username: String,
    val fullName: String,
    val avatar: String? = null
)

data class LoginSessionModel(
    val sessionId: String,
    val familyId: String? = null,
    val deviceId: String? = null,
    val userAgent: String? = null,
    val ipAddress: String? = null,
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    val expiresAt: String? = null,
    val isRevoked: Boolean = false,
    val revokedReason: String? = null,
    val isSuspicious: Boolean = false,
    val isCompromised: Boolean = false,
    val isCurrent: Boolean = false
)
