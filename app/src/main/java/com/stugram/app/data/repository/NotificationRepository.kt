package com.stugram.app.data.repository

import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.NotificationModel
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.SimpleFlagData
import retrofit2.Response

class NotificationRepository {
    private val notificationApi get() = RetrofitClient.notificationApi

    suspend fun getNotifications(page: Int, limit: Int): Response<PaginatedResponse<NotificationModel>> =
        notificationApi.getNotifications(page, limit)

    suspend fun getUnreadCount() =
        notificationApi.getUnreadCount()

    suspend fun markAsRead(notificationId: String): Response<BaseResponse<NotificationModel>> =
        notificationApi.markAsRead(notificationId)

    suspend fun markAllAsRead(): Response<BaseResponse<SimpleFlagData>> =
        notificationApi.markAllAsRead()
}
