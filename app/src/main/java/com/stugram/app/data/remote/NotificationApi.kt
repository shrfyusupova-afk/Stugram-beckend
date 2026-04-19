package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.NotificationModel
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.SimpleFlagData
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {
    @GET("notifications/summary")
    suspend fun getSummary(): Response<BaseResponse<Map<String, Any>>>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): Response<BaseResponse<UnreadCountData>>

    @GET("notifications")
    suspend fun getNotifications(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<NotificationModel>>

    @PATCH("notifications/{notificationId}/read")
    suspend fun markAsRead(@Path("notificationId") notificationId: String): Response<BaseResponse<NotificationModel>>

    @PATCH("notifications/read-all")
    suspend fun markAllAsRead(): Response<BaseResponse<SimpleFlagData>>
}

data class UnreadCountData(
    val unreadCount: Int
)
