package com.example.profile.service

import com.example.profile.repository.NotificationRepository

class NotificationService(private val repository: NotificationRepository) {
    suspend fun getNotifications(userId: Int, page: Int, limit: Int) =
        repository.getNotifications(userId, limit, (page - 1).toLong() * limit)

    suspend fun markAllAsRead(userId: Int) = repository.markAsRead(userId)
}
