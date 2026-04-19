package com.example.profile.repository

import com.example.profile.database.DatabaseFactory.dbQuery
import com.example.profile.models.*
import org.jetbrains.exposed.sql.*
import java.time.format.DateTimeFormatter

class NotificationRepository {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun getNotifications(userId: Int, limit: Int, offset: Long): List<NotificationDto> = dbQuery {
        (NotificationsTable innerJoin UsersTable)
            .select { NotificationsTable.receiverId eq userId }
            .orderBy(NotificationsTable.createdAt, SortOrder.DESC)
            .limit(limit, offset = offset)
            .map { row ->
                NotificationDto(
                    id = row[NotificationsTable.id],
                    senderUsername = row[UsersTable.username],
                    type = row[NotificationsTable.type],
                    postId = row[NotificationsTable.postId],
                    createdAt = row[NotificationsTable.createdAt].format(formatter)
                )
            }
    }

    suspend fun markAsRead(userId: Int) = dbQuery {
        NotificationsTable.update({ NotificationsTable.receiverId eq userId }) {
            it[isRead] = true
        }
    }
}
