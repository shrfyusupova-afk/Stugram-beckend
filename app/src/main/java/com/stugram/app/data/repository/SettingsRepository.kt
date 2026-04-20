package com.stugram.app.data.repository

import com.stugram.app.data.remote.ChangePasswordRequest
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.UserSettingsModel
import com.stugram.app.data.remote.model.BaseResponse
import retrofit2.Response

class SettingsRepository {
    private val settingsApi get() = RetrofitClient.settingsApi

    suspend fun getSettings(): Response<BaseResponse<UserSettingsModel>> = settingsApi.getSettings()

    suspend fun updateSettings(updates: Map<String, Any>): Response<BaseResponse<UserSettingsModel>> =
        settingsApi.updateSettings(updates)

    suspend fun getNotificationSettings() = settingsApi.getNotificationSettings()

    suspend fun updateNotificationSettings(updates: com.stugram.app.data.remote.NotificationSettingsModel) =
        settingsApi.updateNotificationSettings(updates)

    suspend fun getBlockedUsers() = settingsApi.getBlockedUsers()

    suspend fun unblockUser(userId: String) = settingsApi.unblockUser(userId)

    suspend fun blockUser(userId: String) = settingsApi.blockUser(userId)

    suspend fun getLoginSessions() = settingsApi.getLoginSessions()

    suspend fun revokeSession(sessionId: String) = settingsApi.revokeSession(sessionId)

    suspend fun getHiddenWords() = settingsApi.getHiddenWords()

    suspend fun updateHiddenWords(words: List<String>) = settingsApi.updateHiddenWords(words)

    suspend fun changePassword(request: ChangePasswordRequest) = settingsApi.changePassword(request)
}
