package com.stugram.app.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// --- GLOBAL KONSTANTALAR ---
val GlobalBackgroundColor = Color(0xFF0F0F0F)

// --- MA'LUMOT MODELLARI ---
@Immutable
data class PostData(
    val id: Int, 
    val backendId: String? = null,
    val authorId: String? = null,
    val user: String, 
    val authorFullName: String? = null,
    val image: String? = null,
    val userAvatar: String? = null,
    val caption: String = "Love your mine #lovetoyou #foryourpage #beautifull #popular #peoplefrost",
    val likes: Int = 1245,
    val comments: Int = 173,
    val reposts: Int = 229,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isVideo: Boolean = false,
    val createdAt: String? = null
)

@Immutable
data class StoryMedia(
    val id: Int, 
    val backendId: String? = null,
    val mediaUrl: String, 
    val isVideo: Boolean = false,
    val viewersCount: Int = 0,
    val likesCount: Int = 0,
    val repliesCount: Int = 0,
    val isLiked: Boolean = false
)

@Immutable
data class StoryProfile(
    val id: Int,
    val name: String,
    val avatar: String? = null,
    val stories: List<StoryMedia>,
    val isLive: Boolean = false,
    val isSeen: Boolean = false,
    val isMine: Boolean = false
)

@Immutable
data class StoryActivityUser(
    val userId: String? = null,
    val conversationId: String? = null,
    val name: String,
    val avatar: String? = null,
    val subtitle: String
)

@Immutable
data class TabItem(val name: String, val icon: ImageVector)

@Immutable
data class RecommendedProfile(
    val id: Int, 
    val name: String, 
    val image: String? = null, 
    val username: String,
    val backendId: String? = null,
    val isFollowed: Boolean = false,
    val followStatus: String = "not_following"
)

@Immutable
data class CommentData(
    val id: Int,
    val user: String,
    val avatar: String? = null,
    val text: String,
    val time: String,
    val likes: Int = 0,
    val replies: List<CommentData> = emptyList()
)
