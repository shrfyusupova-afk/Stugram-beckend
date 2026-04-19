package com.example.profile.service

import com.example.profile.database.RedisFactory
import com.example.profile.models.*
import com.example.profile.repository.ProfileRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProfileService(private val repository: ProfileRepository) {
    
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchProfile(currentUserId: Int, targetUsername: String): UserProfile? {
        val cacheKey = "profile:$targetUsername"
        
        // Try Cache
        RedisFactory.getJedis().use { jedis ->
            val cached = jedis.get(cacheKey)
            if (cached != null) {
                return json.decodeFromString<UserProfile>(cached)
            }
        }

        // Fetch from DB
        val profile = repository.getProfileByUsername(currentUserId, targetUsername)
        
        // Save to Cache if exists
        if (profile != null) {
            RedisFactory.getJedis().use { jedis ->
                jedis.setex(cacheKey, 3600, json.encodeToString(profile)) // Cache for 1 hour
            }
        }
        
        return profile
    }
    
    suspend fun fetchPosts(username: String, type: String, page: Int, limit: Int): List<PostDto> {
        val offset = (page - 1).toLong() * limit
        return repository.getPosts(username, type, limit, offset)
    }

    suspend fun updateUserInfo(userId: Int, username: String, request: EditProfileRequest): Boolean {
        val success = repository.updateProfile(userId, request)
        if (success) {
            // Invalidate cache
            RedisFactory.getJedis().use { jedis ->
                jedis.del("profile:$username")
            }
        }
        return success
    }

    suspend fun toggleFollow(followerId: Int, targetId: Int, targetUsername: String): String {
        val result = repository.toggleFollow(followerId, targetId)
        // Invalidate cache for target profile to update follower counts
        RedisFactory.getJedis().use { jedis ->
            jedis.del("profile:$targetUsername")
        }
        return result
    }

    suspend fun checkUsername(username: String): Boolean = repository.isUsernameAvailable(username)

    suspend fun search(query: String) = repository.searchUsers(query)
}
