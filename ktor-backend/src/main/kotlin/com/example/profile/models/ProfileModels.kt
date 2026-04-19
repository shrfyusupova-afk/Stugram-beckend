package com.example.profile.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 100).uniqueIndex()
    val passwordHash = text("password_hash")
    val fullName = varchar("full_name", 100).nullable()
    val bio = text("bio").nullable()
    val profilePicUrl = text("profile_pic_url").nullable()
    val coverPicUrl = text("cover_pic_url").nullable()
    val isVerified = bool("is_verified").default(false)
    val isPrivate = bool("is_private").default(false)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

object PostsTable : Table("posts") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id)
    val imageUrl = text("image_url")
    val caption = text("caption").nullable()
    val likesCount = integer("likes_count").default(0)
    val type = varchar("type", 10).default("POST") // POST or REEL
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

object FollowsTable : Table("follows") {
    val followerId = integer("follower_id").references(UsersTable.id)
    val followingId = integer("following_id").references(UsersTable.id)
    val status = varchar("status", 20).default("ACCEPTED") // PENDING, ACCEPTED
    override val primaryKey = PrimaryKey(followerId, followingId)
}

object NotificationsTable : Table("notifications") {
    val id = integer("id").autoIncrement()
    val receiverId = integer("receiver_id").references(UsersTable.id)
    val senderId = integer("sender_id").references(UsersTable.id)
    val type = varchar("type", 20) // FOLLOW, LIKE, COMMENT, FOLLOW_REQUEST
    val postId = integer("post_id").references(PostsTable.id).nullable()
    val isRead = bool("is_read").default(false)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class BaseResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)

@Serializable
data class UserProfile(
    val id: Int,
    val username: String,
    val fullName: String?,
    val bio: String?,
    val profilePicUrl: String?,
    val coverPicUrl: String?,
    val isVerified: Boolean,
    val isPrivate: Boolean,
    val followersCount: Long,
    val followingCount: Long,
    val posts: List<PostDto> = emptyList(),
    val isFollowing: Boolean = false,
    val followStatus: String? = null // PENDING, ACCEPTED, null
)

@Serializable
data class PostDto(
    val id: Int,
    val imageUrl: String,
    val caption: String?,
    val likesCount: Int,
    val type: String,
    val createdAt: String
)

@Serializable
data class NotificationDto(
    val id: Int,
    val senderUsername: String,
    val type: String,
    val postId: Int?,
    val createdAt: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val username: String
)

@Serializable
data class EditProfileRequest(
    val fullName: String?,
    val bio: String?,
    val profilePicUrl: String?,
    val isPrivate: Boolean?
)
