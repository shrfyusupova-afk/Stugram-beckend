package com.example.profile.repository

import com.example.profile.database.DatabaseFactory.dbQuery
import com.example.profile.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.format.DateTimeFormatter

class ProfileRepository {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun getProfileByUsername(currentUserId: Int, targetUsername: String): UserProfile? = dbQuery {
        val userRow = UsersTable.select { UsersTable.username eq targetUsername }.singleOrNull() ?: return@dbQuery null
        val targetId = userRow[UsersTable.id]
        val isPrivate = userRow[UsersTable.isPrivate]

        val followRow = FollowsTable.select { 
            (FollowsTable.followerId eq currentUserId) and (FollowsTable.followingId eq targetId) 
        }.singleOrNull()
        
        val isFollowing = followRow != null && followRow[FollowsTable.status] == "ACCEPTED"
        val followStatus = followRow?.get(FollowsTable.status)

        val followersCount = FollowsTable.select { 
            (FollowsTable.followingId eq targetId) and (FollowsTable.status eq "ACCEPTED") 
        }.count()
        
        val followingCount = FollowsTable.select { 
            (FollowsTable.followerId eq targetId) and (FollowsTable.status eq "ACCEPTED") 
        }.count()

        // Privacy Check: Only show posts if public or if current user is an accepted follower
        val canSeeContent = !isPrivate || isFollowing || currentUserId == targetId
        
        val userPosts = if (canSeeContent) {
            PostsTable.select { PostsTable.userId eq targetId }
                .orderBy(PostsTable.createdAt, SortOrder.DESC)
                .limit(20) // Default limit for pagination
                .map { it.toPostDto() }
        } else emptyList()

        UserProfile(
            id = targetId,
            username = userRow[UsersTable.username],
            fullName = userRow[UsersTable.fullName],
            bio = userRow[UsersTable.bio],
            profilePicUrl = userRow[UsersTable.profilePicUrl],
            coverPicUrl = userRow[UsersTable.coverPicUrl],
            isVerified = userRow[UsersTable.isVerified],
            isPrivate = isPrivate,
            followersCount = followersCount,
            followingCount = followingCount,
            posts = userPosts,
            isFollowing = isFollowing,
            followStatus = followStatus
        )
    }

    suspend fun getPosts(username: String, type: String, limit: Int, offset: Long): List<PostDto> = dbQuery {
        val user = UsersTable.select { UsersTable.username eq username }.singleOrNull() ?: return@dbQuery emptyList()
        
        PostsTable.select { (PostsTable.userId eq user[UsersTable.id]) and (PostsTable.type eq type) }
            .orderBy(PostsTable.createdAt, SortOrder.DESC)
            .limit(limit, offset = offset)
            .map { it.toPostDto() }
    }

    suspend fun searchUsers(query: String): List<UserProfile> = dbQuery {
        UsersTable.select { UsersTable.username.lowerCase() like "%${query.lowercase()}%" }
            .limit(10)
            .map { row ->
                UserProfile(
                    id = row[UsersTable.id],
                    username = row[UsersTable.username],
                    fullName = row[UsersTable.fullName],
                    bio = row[UsersTable.bio],
                    profilePicUrl = row[UsersTable.profilePicUrl],
                    coverPicUrl = row[UsersTable.coverPicUrl],
                    isVerified = row[UsersTable.isVerified],
                    isPrivate = row[UsersTable.isPrivate],
                    followersCount = 0, // Simplified for search
                    followingCount = 0,
                    posts = emptyList()
                )
            }
    }

    suspend fun toggleFollow(followerId: Int, targetId: Int): String = dbQuery {
        val targetUser = UsersTable.select { UsersTable.id eq targetId }.singleOrNull() ?: return@dbQuery "User not found"
        val isPrivate = targetUser[UsersTable.isPrivate]
        
        val existingFollow = FollowsTable.select { 
            (FollowsTable.followerId eq followerId) and (FollowsTable.followingId eq targetId) 
        }.singleOrNull()

        if (existingFollow != null) {
            FollowsTable.deleteWhere { (FollowsTable.followerId eq followerId) and (FollowsTable.followingId eq targetId) }
            "Unfollowed"
        } else {
            val status = if (isPrivate) "PENDING" else "ACCEPTED"
            FollowsTable.insert {
                it[FollowsTable.followerId] = followerId
                it[FollowsTable.followingId] = targetId
                it[FollowsTable.status] = status
            }
            
            // Create notification
            NotificationsTable.insert {
                it[receiverId] = targetId
                it[senderId] = followerId
                it[type] = if (isPrivate) "FOLLOW_REQUEST" else "FOLLOW"
            }
            
            if (isPrivate) "Requested" else "Followed"
        }
    }

    suspend fun getFollowRequests(userId: Int): List<UserProfile> = dbQuery {
        (FollowsTable innerJoin UsersTable)
            .select { (FollowsTable.followingId eq userId) and (FollowsTable.status eq "PENDING") }
            .map { row ->
                UserProfile(
                    id = row[UsersTable.id],
                    username = row[UsersTable.username],
                    fullName = row[UsersTable.fullName],
                    bio = row[UsersTable.bio],
                    profilePicUrl = row[UsersTable.profilePicUrl],
                    coverPicUrl = row[UsersTable.coverPicUrl],
                    isVerified = row[UsersTable.isVerified],
                    isPrivate = row[UsersTable.isPrivate],
                    followersCount = 0,
                    followingCount = 0,
                    posts = emptyList()
                )
            }
    }

    suspend fun handleFollowRequest(followerId: Int, userId: Int, accept: Boolean): Boolean = dbQuery {
        if (accept) {
            FollowsTable.update({ (FollowsTable.followerId eq followerId) and (FollowsTable.followingId eq userId) }) {
                it[status] = "ACCEPTED"
            } > 0
        } else {
            FollowsTable.deleteWhere { (FollowsTable.followerId eq followerId) and (FollowsTable.followingId eq userId) } > 0
        }
    }

    suspend fun isUsernameAvailable(username: String): Boolean = dbQuery {
        UsersTable.select { UsersTable.username eq username }.count() == 0L
    }

    suspend fun updateProfile(userId: Int, request: EditProfileRequest): Boolean = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            request.fullName?.let { fullName -> it[UsersTable.fullName] = fullName }
            request.bio?.let { bio -> it[UsersTable.bio] = bio }
            request.profilePicUrl?.let { url -> it[UsersTable.profilePicUrl] = url }
            request.isPrivate?.let { private -> it[UsersTable.isPrivate] = private }
        } > 0
    }

    private fun ResultRow.toPostDto() = PostDto(
        id = this[PostsTable.id],
        imageUrl = this[PostsTable.imageUrl],
        caption = this[PostsTable.caption],
        likesCount = this[PostsTable.likesCount],
        type = this[PostsTable.type],
        createdAt = this[PostsTable.createdAt].format(formatter)
    )
}
