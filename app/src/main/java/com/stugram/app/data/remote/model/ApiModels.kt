package com.stugram.app.data.remote.model

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject

data class BaseResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?,
    val meta: MetaData? = null
)

data class PaginatedResponse<T>(
    val success: Boolean,
    val message: String,
    val data: List<T> = emptyList(),
    val meta: MetaData? = null
)

data class ExploreTrendingData(
    val posts: List<PostModel> = emptyList(),
    val reels: List<PostModel> = emptyList(),
    val creators: List<ProfileSummary> = emptyList()
)

data class MetaData(
    val page: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
    val totalPages: Int? = null
)

data class SendOtpRequest(
    val identity: String,
    val purpose: String = "register"
)

data class VerifyOtpRequest(
    val identity: String,
    val otp: String,
    val purpose: String = "register"
)

data class RegisterRequest(
    val identity: String,
    val otp: String,
    val fullName: String,
    val username: String,
    val password: String,
    val type: String? = null,
    val region: String? = null,
    val district: String? = null,
    val school: String? = null,
    val birthday: String? = null,
    val grade: String? = null,
    val group: String? = null,
    val bio: String? = null,
    val isPrivateAccount: Boolean = false
)

data class FullRegisterRequest(
    val identity: String,
    val password: String,
    val fullName: String,
    val username: String,
    val region: String = "",
    val district: String = "",
    val school: String = "",
    val grade: String = "",
    val group: String = "",
    val profilePicUrl: String? = null,
    val coverPicUrl: String? = null
)

data class LoginRequest(
    val identityOrUsername: String,
    val password: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class GoogleLoginRequest(
    val idToken: String
)

data class ForgotPasswordRequest(
    val identity: String
)

data class ForgotPasswordData(
    val identity: String,
    val expiresAt: String? = null,
    val resetToken: String? = null
)

data class ResetPasswordRequest(
    val token: String,
    val password: String
)

data class ResetPasswordData(
    val reset: Boolean = false
)

data class SwitchProfileRequest(
    val profileId: String
)

data class CreateProfileRequest(
    val username: String,
    val firstName: String,
    val lastName: String,
    val type: String,
    val region: String? = null,
    val district: String? = null,
    val school: String? = null,
    val password: String
)

data class OtpData(
    val identity: String,
    val purpose: String,
    val expiresAt: String,
    val otp: String? = null
)

data class VerifyOtpData(
    val identity: String,
    val purpose: String,
    val verified: Boolean
)

data class LogoutData(
    val loggedOut: Boolean
)

data class GoogleLoginData(
    val provider: String,
    val tokenPreview: String,
    val message: String
)

data class AuthPayload(
    val user: ProfileModel,
    val accessToken: String,
    val refreshToken: String
)

data class ProfileModel(
    @SerializedName("_id") val id: String,
    val identity: String? = null,
    val username: String,
    val fullName: String,
    val bio: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val birthday: String? = null,
    val location: String? = null,
    val school: String? = null,
    val region: String? = null,
    val district: String? = null,
    val grade: String? = null,
    val group: String? = null,
    val type: String? = null,
    val role: String? = null,
    val isPrivateAccount: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val reelsCount: Int? = null,
    val isFollowing: Boolean = false,
    val followStatus: String? = null,
    val createdAt: String? = null,
    val lastLoginAt: String? = null
)

data class ProfileQuickSummaryModel(
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val reelsCount: Int = 0,
    val storiesActive: Boolean = false
)

data class ProfileHighlightItemModel(
    val id: String,
    val storyId: String? = null,
    val mediaUrl: String,
    val thumbnailUrl: String? = null,
    val mediaType: String,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val order: Int = 0
)

data class ProfileHighlightModel(
    val id: String,
    val ownerId: String,
    val title: String,
    val coverImageUrl: String? = null,
    val coverUrl: String? = null,
    val storyIds: List<String> = emptyList(),
    val items: List<ProfileHighlightItemModel> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isArchived: Boolean = false
)

data class CreateProfileHighlightRequest(
    val title: String,
    val storyIds: List<String>,
    val coverStoryId: String? = null
)

data class UpdateProfileHighlightRequest(
    val title: String? = null,
    val coverStoryId: String? = null
)

data class AddStoryToHighlightRequest(
    val storyId: String,
    val insertAt: Int? = null,
    val makeCover: Boolean = false
)

data class DeleteProfileHighlightResult(
    val deleted: Boolean = false,
    val id: String? = null,
    val removedStoryId: String? = null,
    val becameEmpty: Boolean = false,
    val highlight: ProfileHighlightModel? = null
)

data class ProfileSummary(
    @SerializedName("_id") val id: String,
    val username: String,
    val fullName: String,
    val avatar: String? = null,
    val bio: String? = null,
    val region: String? = null,
    val district: String? = null,
    val school: String? = null,
    val grade: String? = null,
    val group: String? = null,
    val followersCount: Int = 0,
    val followStatus: String? = null
)

data class AccountProfileItemModel(
    val id: String,
    val username: String,
    val fullName: String,
    val avatar: String? = null,
    val banner: String? = null,
    val type: String = "student",
    val region: String = "",
    val district: String = "",
    val school: String = "",
    val createdAt: String? = null
)

data class DirectConversationModel(
    @SerializedName("_id") val id: String,
    val otherParticipant: ProfileSummary? = null,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val pinnedMessage: ChatMessageModel? = null,
    val pinnedAt: String? = null,
    val unreadCount: Int = 0
)

data class GroupConversationModel(
    @SerializedName("_id") val id: String,
    val name: String,
    val avatar: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val pinnedMessage: ChatMessageModel? = null,
    val pinnedAt: String? = null,
    val unreadCount: Int = 0,
    val membersCount: Int = 0
)

data class ChatSummaryModel(
    val totalUnreadMessages: Int = 0,
    val unreadConversations: Int = 0
)

data class GroupMemberModel(
    val user: ProfileSummary,
    val joinedAt: String? = null,
    val role: String = "member"
)

data class ChatMessageModel(
    @SerializedName("_id") val id: String,
    val conversation: String? = null,
    val sender: ProfileSummary? = null,
    val clientId: String? = null,
    val text: String? = null,
    val messageType: String = "text",
    val media: PostMediaModel? = null,
    val metadata: ChatStructuredMetadataModel? = null,
    val replyToMessage: ChatReplyMessageModel? = null,
    val reactions: List<MessageReactionModel> = emptyList(),
    val seenBy: List<String> = emptyList(),
    val seenByRecords: List<MessageSeenRecordModel> = emptyList(),
    val deliveredAt: String? = null,
    val forwardedFromMessageId: String? = null,
    val forwardedFromSenderId: String? = null,
    val forwardedFromConversationId: String? = null,
    val forwardedAt: String? = null,
    val editedAt: String? = null,
    val isDeletedForEveryone: Boolean = false,
    val deletedForEveryoneAt: String? = null,
    val deletedForEveryoneBy: String? = null,
    val readAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val serverSequence: Long? = null,
    val editVersion: Int? = null,
    val reactionVersion: Int? = null,
    val deliveryVersion: Int? = null,
    val deleteSequence: Long? = null
)

data class ChatReplayEventsData(
    val targetId: String,
    val targetType: String,
    val fromSequence: Long = 0,
    val toSequence: Long = 0,
    val events: List<ChatReplayEventModel> = emptyList(),
    val hasMore: Boolean = false
)

data class ChatReplayEventModel(
    val sequence: Long,
    val type: String,
    val targetType: String,
    val targetId: String,
    val messageId: String? = null,
    val clientId: String? = null,
    val actorId: String? = null,
    val createdAt: String? = null,
    val payload: JsonObject? = null
)

data class MessageDeleteResult(
    val deleted: Boolean = false,
    val deletedForEveryone: Boolean = false,
    val deletedAt: String? = null,
    val conversationId: String? = null,
    val groupId: String? = null,
    val participantIds: List<String> = emptyList(),
    val message: ChatMessageModel? = null
)

data class MessageReactionModel(
    val user: ProfileSummary? = null,
    val emoji: String
)

data class MessageSeenRecordModel(
    val user: ProfileSummary? = null,
    val seenAt: String? = null
)

data class ChatReplyMessageModel(
    @SerializedName("_id") val id: String,
    val text: String? = null,
    val messageType: String = "text",
    val media: PostMediaModel? = null,
    val metadata: ChatStructuredMetadataModel? = null,
    val sender: ProfileSummary? = null,
    val createdAt: String? = null
)

data class SendChatMessageRequest(
    val text: String? = null,
    val messageType: String = "text",
    val replyToMessageId: String? = null,
    val clientId: String? = null
)

data class UpdateProfileRequest(
    val fullName: String? = null,
    val username: String? = null,
    val bio: String? = null,
    val birthday: String? = null,
    val location: String? = null,
    val school: String? = null,
    val region: String? = null,
    val district: String? = null,
    val grade: String? = null,
    val group: String? = null,
    val isPrivateAccount: Boolean? = null
)

data class PostMediaModel(
    val url: String,
    val publicId: String,
    val type: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val durationSeconds: Double? = null
)

data class ChatStructuredMetadataModel(
    val kind: String,
    val payload: ChatStructuredPayloadModel? = null
)

data class ChatStructuredPayloadModel(
    val title: String? = null,
    val subtitle: String? = null,
    val tertiary: String? = null,
    val imageUrl: String? = null,
    val targetId: String? = null
)

data class PostModel(
    @SerializedName("_id") val id: String,
    val author: ProfileSummary,
    val media: List<PostMediaModel>,
    val caption: String,
    val hashtags: List<String> = emptyList(),
    val location: String = "",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val savesCount: Int = 0,
    val viewerHasLiked: Boolean? = null,
    val viewerHasSaved: Boolean? = null,
    val isEdited: Boolean = false,
    val createdAt: String? = null
)

data class UpdatePostRequest(
    val caption: String? = null,
    val hashtags: List<String>? = null,
    val location: String? = null
)

data class StoryViewerModel(
    val user: ProfileSummary,
    val viewedAt: String
)

data class StoryLikeModel(
    val user: ProfileSummary,
    val likedAt: String
)

data class StoryReplyModel(
    @SerializedName("_id") val id: String,
    val author: ProfileSummary,
    val content: String,
    val createdAt: String
)

data class StoryInsightsModel(
    val totalViews: Int = 0,
    val totalLikes: Int = 0,
    val totalReplies: Int = 0,
    val latestViewers: List<StoryViewerModel> = emptyList(),
    val latestLikers: List<StoryLikeModel> = emptyList(),
    val latestCommenters: List<StoryReplyModel> = emptyList()
)

data class StoryReplyToDmData(
    val conversationId: String? = null,
    val participantIds: List<String>? = null,
    val message: com.stugram.app.data.remote.model.ChatMessageModel? = null
)

data class StoryModel(
    @SerializedName("_id") val id: String,
    val author: ProfileSummary,
    val media: PostMediaModel,
    val caption: String = "",
    val viewersCount: Int = 0,
    val likesCount: Int = 0,
    val repliesCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val isViewedByMe: Boolean = false,
    val expiresAt: String,
    val createdAt: String? = null
)

data class FollowRequestModel(
    @SerializedName("_id") val id: String,
    val requester: ProfileSummary? = null,
    val recipient: String? = null,
    val status: String
)

data class FollowActionData(
    val updated: Boolean = false,
    val status: String,
    val request: FollowRequestModel? = null
)

data class NotificationModel(
    @SerializedName("_id") val id: String,
    val type: String,
    val message: String,
    val isRead: Boolean,
    val actor: ProfileSummary? = null,
    val post: PostModel? = null,
    val comment: CommentModel? = null,
    val createdAt: String? = null
)

data class CommentModel(
    @SerializedName("_id") val id: String,
    val content: String,
    val author: ProfileSummary? = null,
    val parentComment: CommentParentModel? = null,
    val repliesCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class CommentParentModel(
    @SerializedName("_id") val id: String,
    val content: String? = null,
    val author: String? = null
)

data class CreateCommentRequest(
    val content: String,
    val parentCommentId: String? = null
)

data class LikeActionData(
    val likesCount: Int = 0,
    val liked: Boolean = false
)

data class SavedPostActionData(
    val saved: Boolean = false,
    val post: PostModel? = null,
    val postId: String? = null
)

data class PostInteractionHistoryItem(
    val likedAt: String? = null,
    val savedAt: String? = null,
    val post: PostModel? = null
)

data class HashtagResult(
    val hashtag: String,
    val postsCount: Int
)

data class SimpleFlagData(
    val updated: Boolean = false,
    val deleted: Boolean = false,
    val removed: Boolean = false,
    val left: Boolean = false,
    val status: String? = null
)

data class UsernameAvailabilityResponse(
    val available: Boolean
)

data class RegisterPushTokenRequest(
    val token: String? = null,
    val pushToken: String? = null,
    val platform: String = "android",
    val deviceId: String,
    val appVersion: String? = null
)

data class DeletePushTokenRequest(
    val token: String? = null,
    val pushToken: String? = null,
    val deviceId: String? = null
)
