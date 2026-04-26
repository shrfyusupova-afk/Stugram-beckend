package com.stugram.app.ui.home

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stugram.app.core.social.FollowEvent
import com.stugram.app.core.social.FollowEvents
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.repository.PostRepository
import com.stugram.app.data.repository.PostInteractionRepository
import com.stugram.app.data.repository.RecommendationRepository
import com.stugram.app.data.repository.StoryRepository
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.data.repository.FollowRepository
import com.stugram.app.data.repository.NotificationRepository
import com.stugram.app.data.repository.ChatRepository
import com.stugram.app.data.repository.GroupChatRepository
import com.stugram.app.data.repository.MessagesInboxCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val postRepository = PostRepository()
    private val interactionRepository = PostInteractionRepository()
    private val recommendationRepository = RecommendationRepository()
    private val storyRepository = StoryRepository()
    private val profileRepository = ProfileRepository()
    private val followRepository = FollowRepository()
    private val notificationRepository = NotificationRepository()
    private val chatRepository = ChatRepository()
    private val groupChatRepository = GroupChatRepository()
    private val tokenManager = TokenManager(application)

    // --- UI STATES ---
    var currentTab by mutableIntStateOf(0)
    var showCreatePostModal by mutableStateOf(false)
    var showCommentsSheet by mutableStateOf(false)
    var activeStoryProfileIndex by mutableStateOf<Int?>(null)
    var showCameraView by mutableStateOf(false)
    var showNotifications by mutableStateOf(false)
    var unreadNotificationsCount by mutableIntStateOf(0)
    var unreadMessagesCount by mutableIntStateOf(0)
    var isSettingsOpen by mutableStateOf(false)
    var selectedProfileId by mutableStateOf<Int?>(null)
    var selectedProfileUsername by mutableStateOf<String?>(null)

    // --- REFRESH STATES ---
    var isHomeRefreshing by mutableStateOf(false)
    var isSearchRefreshing by mutableStateOf(false)
    var isReelsRefreshing by mutableStateOf(false)
    var isMessagesRefreshing by mutableStateOf(false)
    var isProfileRefreshing by mutableStateOf(false)

    // --- DATA ---
    var posts by mutableStateOf<List<PostData>>(emptyList())
    var reels by mutableStateOf<List<PostData>>(emptyList())
    var profilePosts by mutableStateOf<List<PostData>>(emptyList())
    var storyProfiles by mutableStateOf<List<StoryProfile>>(emptyList())
    
    // Insights state
    var storyViewers by mutableStateOf<List<StoryActivityUser>>(emptyList())
    var storyLikes by mutableStateOf<List<StoryActivityUser>>(emptyList())
    var storyReplies by mutableStateOf<List<StoryActivityUser>>(emptyList())
    var isInsightsLoading by mutableStateOf(false)

    var showStoryInsights by mutableStateOf(false)
    var recommendedProfiles by mutableStateOf<List<RecommendedProfile>>(emptyList())

    // Derived / session user (for "new user" UX + story self item)
    var currentUserProfile by mutableStateOf<com.stugram.app.data.remote.model.ProfileModel?>(null)
    var currentUserFollowingCount by mutableIntStateOf(0)

    // --- STABILITY / ERROR ---
    var homeErrorMessage by mutableStateOf<String?>(null)
    var reelsErrorMessage by mutableStateOf<String?>(null)
    var profileErrorMessage by mutableStateOf<String?>(null)

    // --- FEED PAGINATION ---
    private var feedPage by mutableStateOf(1)
    private var feedHasMore by mutableStateOf(true)
    var isHomeLoadingMore by mutableStateOf(false)

    // --- REELS PAGINATION ---
    private var reelsPage by mutableStateOf(1)
    private var reelsHasMore by mutableStateOf(true)
    var isReelsLoadingMore by mutableStateOf(false)

    // --- EVENTS ---
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private var homeRefreshJob: Job? = null
    private var storiesRefreshJob: Job? = null
    private var reelsRefreshJob: Job? = null
    private var profileRefreshJob: Job? = null
    private var messagesRefreshJob: Job? = null
    private var unreadRefreshJob: Job? = null
    private var unreadMessagesRefreshJob: Job? = null

    private var lastCurrentUserFetchAtMs = 0L
    private var lastUnreadFetchAtMs = 0L
    private var lastUnreadMessagesFetchAtMs = 0L
    private var cachedInteractionIds: Pair<Set<String>, Set<String>>? = null
    private var lastInteractionFetchAtMs = 0L

    private companion object {
        const val CURRENT_USER_TTL_MS = 8_000L
        const val INTERACTION_IDS_TTL_MS = 15_000L
        const val UNREAD_COUNT_TTL_MS = 10_000L
        const val UNREAD_MESSAGES_TTL_MS = 8_000L
    }

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
    }

    init {
        viewModelScope.launch {
            MessagesInboxCache.unreadState.collect { unreadState ->
                unreadMessagesCount = unreadState.totalUnreadCount
            }
        }
        refreshAll()
        refreshUnreadMessagesSummary(force = true)
        observeFollowEvents()
    }

    private fun observeFollowEvents() {
        viewModelScope.launch {
            FollowEvents.events.collect { event ->
                applyFollowEvent(event)
            }
        }
    }

    private fun applyFollowEvent(event: FollowEvent) {
        // Recommended profiles slider
        if (!event.userId.isNullOrBlank()) {
            val followStatus = event.followStatus?.lowercase()?.trim()
                ?: if (event.isFollowing) "following" else "not_following"
            recommendedProfiles = recommendedProfiles.map { rp ->
                if (rp.backendId == event.userId) {
                    rp.copy(
                        isFollowed = followStatus == "following",
                        followStatus = followStatus
                    )
                } else rp
            }
        }
    }

    fun syncCurrentUserSnapshot(user: com.stugram.app.data.remote.model.ProfileModel?) {
        currentUserProfile = user
        currentUserFollowingCount = user?.followingCount ?: currentUserFollowingCount
    }

    private suspend fun loadFreshCurrentUser(force: Boolean = false): com.stugram.app.data.remote.model.ProfileModel? {
        val now = System.currentTimeMillis()
        currentUserProfile?.let { cached ->
            if (!force && now - lastCurrentUserFetchAtMs < CURRENT_USER_TTL_MS) return cached
        }
        val response = runCatching { profileRepository.getCurrentProfile() }.getOrNull()
        val fresh = if (response?.isSuccessful == true) response.body()?.data else null
        if (fresh != null) {
            tokenManager.updateCurrentUser(fresh)
            currentUserProfile = fresh
            currentUserFollowingCount = fresh.followingCount
            lastCurrentUserFetchAtMs = now
            return fresh
        }
        return tokenManager.currentUser.firstOrNull()
    }

    fun refreshAll() {
        refreshHome()
        refreshStories()
    }

    fun refreshForPullToRefresh() {
        refreshHome(force = true)
        refreshStories(force = true)
    }

    fun refreshForUserSwitch(user: com.stugram.app.data.remote.model.ProfileModel) {
        clearSessionScopedState(user)
        refreshHome(force = true)
        refreshStories(force = true)
        if (currentTab == 2) refreshReels(force = true)
        if (currentTab == 3) refreshMessages()
        if (currentTab == 4) refreshProfile(user.username, force = true)
    }

    fun refreshAfterContentCreation(type: String) {
        when (type) {
            "story" -> {
                refreshStories(force = true)
                refreshHome(force = true)
            }
            "reel" -> {
                refreshHome(force = true)
                refreshReels(force = true)
                if (currentTab == 4) refreshProfile(currentUserProfile?.username, force = true)
            }
            else -> {
                refreshHome(force = true)
                if (currentTab == 4) refreshProfile(currentUserProfile?.username, force = true)
            }
        }
    }

    private fun clearSessionScopedState(user: com.stugram.app.data.remote.model.ProfileModel) {
        MessagesInboxCache.reset()
        posts = emptyList()
        reels = emptyList()
        profilePosts = emptyList()
        storyProfiles = emptyList()
        recommendedProfiles = emptyList()
        homeErrorMessage = null
        reelsErrorMessage = null
        profileErrorMessage = null
        currentUserProfile = user
        currentUserFollowingCount = user.followingCount
        feedPage = 1
        feedHasMore = true
        reelsPage = 1
        reelsHasMore = true
        cachedInteractionIds = null
        lastInteractionFetchAtMs = 0L
        lastCurrentUserFetchAtMs = 0L
        lastUnreadFetchAtMs = 0L
        lastUnreadMessagesFetchAtMs = 0L
    }

    // --- ACTIONS ---
    fun onTabSelected(index: Int, profileId: Int? = null, profileUsername: String? = null) {
        currentTab = index
        selectedProfileId = profileId
        selectedProfileUsername = profileUsername
        if (index == 4) {
            refreshProfile(profileUsername)
        }
    }

    fun toggleCamera(show: Boolean) {
        showCameraView = show
    }

    fun openStory(index: Int) {
        activeStoryProfileIndex = index
        // Mark first story as viewed if not already
        val profile = storyProfiles.getOrNull(index)
        profile?.stories?.firstOrNull()?.backendId?.let { 
            markStoryViewed(it)
            loadStoryInsights(it)
        }
    }

    fun closeStory() {
        activeStoryProfileIndex = null
        showStoryInsights = false
        storyViewers = emptyList()
        storyLikes = emptyList()
        storyReplies = emptyList()
    }

    fun toggleStoryInsights(show: Boolean, storyId: String? = null) {
        showStoryInsights = show
        if (show) {
            val id = storyId ?: run {
                val index = activeStoryProfileIndex ?: return
                val profile = storyProfiles.getOrNull(index)
                profile?.stories?.firstOrNull()?.backendId
            } ?: return
            
            loadStoryInsights(id)
        }
    }

    fun loadStoryInsights(storyId: String) {
        viewModelScope.launch {
            isInsightsLoading = true
            try {
                // Fetch viewers
                val viewersResponse = storyRepository.getStoryViewers(storyId)
                if (viewersResponse.isSuccessful) {
                    storyViewers = viewersResponse.body()?.data?.map { 
                        StoryActivityUser(
                            userId = it.user.id,
                            name = it.user.username,
                            avatar = it.user.avatar,
                            subtitle = "Viewed at ${it.viewedAt}"
                        )
                    } ?: emptyList()
                }

                // Fetch likes
                val likesResponse = storyRepository.getStoryLikes(storyId)
                if (likesResponse.isSuccessful) {
                    storyLikes = likesResponse.body()?.data?.map {
                        StoryActivityUser(
                            userId = it.user.id,
                            name = it.user.username, 
                            avatar = it.user.avatar, 
                            subtitle = "Liked at ${it.likedAt}"
                        )
                    } ?: emptyList()
                }

                // Fetch replies
                val repliesResponse = storyRepository.getStoryReplies(storyId)
                if (repliesResponse.isSuccessful) {
                    storyReplies = repliesResponse.body()?.data?.map {
                        StoryActivityUser(
                            userId = it.author.id,
                            name = it.author.username, 
                            avatar = it.author.avatar, 
                            subtitle = it.content
                        )
                    } ?: emptyList()
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                isInsightsLoading = false
            }
        }
    }

    fun replyToStory(storyId: String, text: String) {
        viewModelScope.launch {
            try {
                val response = storyRepository.replyToStory(storyId, text)
                if (response.isSuccessful) {
                    _uiEvent.emit(UiEvent.ShowSnackbar("Reply sent!"))
                    // Optimistically update counts in the profile if found
                    storyProfiles = storyProfiles.map { profile ->
                        profile.copy(
                            stories = profile.stories.map { s ->
                                if (s.backendId == storyId) s.copy(repliesCount = s.repliesCount + 1)
                                else s
                            }
                        )
                    }
                } else {
                    _uiEvent.emit(UiEvent.ShowSnackbar("Failed to send reply"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowSnackbar("Error: ${e.message}"))
            }
        }
    }

    fun likeStory(storyId: String) {
        viewModelScope.launch {
            try {
                val currentStory = storyProfiles
                    .flatMap { it.stories }
                    .firstOrNull { it.backendId == storyId }
                val shouldLike = currentStory?.isLiked != true

                val response = if (shouldLike) {
                    storyRepository.likeStory(storyId)
                } else {
                    storyRepository.unlikeStory(storyId)
                }

                if (response.isSuccessful) {
                    val updated = response.body()?.data
                    storyProfiles = storyProfiles.map { profile ->
                        profile.copy(
                            stories = profile.stories.map { s ->
                                if (s.backendId == storyId && updated != null) {
                                    s.copy(
                                        likesCount = updated.likesCount,
                                        isLiked = updated.isLikedByMe
                                    )
                                } else s
                            }
                        )
                    }
                } else {
                    _uiEvent.emit(UiEvent.ShowSnackbar("Failed to update story like"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowSnackbar("Error: ${e.localizedMessage ?: "Unknown error"}"))
            }
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            try {
                val response = storyRepository.deleteStory(storyId)
                if (response.isSuccessful) {
                    _uiEvent.emit(UiEvent.ShowSnackbar("Story deleted"))
                    refreshStories()
                    // If no more stories in this profile, close viewer
                    val activeIndex = activeStoryProfileIndex ?: return@launch
                    val profile = storyProfiles.getOrNull(activeIndex)
                    if (profile == null || profile.stories.isEmpty()) {
                        closeStory()
                    }
                } else {
                    _uiEvent.emit(UiEvent.ShowSnackbar("Failed to delete story"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowSnackbar("Error: ${e.message}"))
            }
        }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch {
            val response = storyRepository.markViewed(storyId)
            if (response.isSuccessful) {
                // Find and update the story in local state to mark as seen
                storyProfiles = storyProfiles.map { profile ->
                    if (profile.stories.any { it.backendId == storyId }) {
                        profile.copy(isSeen = true)
                    } else {
                        profile
                    }
                }
            }
        }
    }

    fun toggleComments(show: Boolean) {
        showCommentsSheet = show
    }

    fun toggleNotifications(show: Boolean) {
        showNotifications = show
    }

    // --- REFRESH LOGIC ---
    fun refreshHome(force: Boolean = false) {
        if (homeRefreshJob?.isActive == true) return
        homeRefreshJob = viewModelScope.launch {
            isHomeRefreshing = true
            homeErrorMessage = null
            try {
                coroutineScope {
                    val unreadDeferred = async { loadUnreadNotifications(force) }
                    val currentUserDeferred = async { loadFreshCurrentUser(force) }
                    val interactionDeferred = async { loadInteractionIds(force) }
                    val feedDeferred = async { postRepository.getFeed(1, 20) }
                    val currentUser = currentUserDeferred.await()
                    currentUserProfile = currentUser
                    currentUserFollowingCount = currentUser?.followingCount ?: 0
                    unreadDeferred.await()
                    val response = feedDeferred.await()
                    if (response.isSuccessful) {
                        val body = response.body()
                        val feedItems = body?.data ?: emptyList()
                        val (likedPostIds, savedPostIds) = interactionDeferred.await()
                        feedPage = body?.meta?.page ?: 1
                        val totalPages = body?.meta?.totalPages ?: 1
                        feedHasMore = feedPage < totalPages
                        posts = feedItems.map { model ->
                            PostData(
                                id = model.id.hashCode(),
                                backendId = model.id,
                                authorId = model.author.id,
                                user = model.author.username,
                                authorFullName = model.author.fullName,
                                image = model.media.firstOrNull()?.url,
                                thumbnailUrl = model.media.firstOrNull()?.thumbnailUrl,
                                userAvatar = model.author.avatar,
                                caption = model.caption,
                                likes = model.likesCount,
                                comments = model.commentsCount,
                                isLiked = likedPostIds.contains(model.id),
                                isSaved = savedPostIds.contains(model.id),
                                isVideo = model.media.firstOrNull()?.type == "video",
                                mediaAspectRatio = model.media.firstOrNull()?.let { mediaAspectRatioFromDimensions(it.width, it.height) },
                                authorFollowStatus = model.author.followStatus,
                                createdAt = model.createdAt
                            )
                        }

                        // Suggested profiles:
                        // - If user follows nobody OR feed is empty, use the real suggestions endpoint.
                        // - Otherwise, fallback to authors from the feed (real data, but less relevant).
                        val shouldUseSuggestions = (currentUserFollowingCount == 0) || feedItems.isEmpty()
                        recommendedProfiles = if (shouldUseSuggestions) {
                            val suggestions = runCatching { profileRepository.getProfileSuggestions(page = 1, limit = 20) }.getOrNull()
                            if (suggestions?.isSuccessful == true) {
                                suggestions.body()?.data.orEmpty().map { u ->
                                    val followStatus = u.followStatus?.lowercase()?.trim().orEmpty().ifBlank { "not_following" }
                                    RecommendedProfile(
                                        id = u.id.hashCode(),
                                        name = u.fullName,
                                        image = u.avatar,
                                        username = u.username,
                                        backendId = u.id,
                                        isFollowed = followStatus == "following",
                                        followStatus = followStatus
                                    )
                                }
                            } else {
                                emptyList()
                            }
                        } else {
                            feedItems
                                .map { model ->
                                    RecommendedProfile(
                                        id = model.author.id.hashCode(),
                                        name = model.author.fullName,
                                        image = model.author.avatar,
                                        username = model.author.username,
                                        backendId = model.author.id,
                                        isFollowed = false,
                                        followStatus = "not_following"
                                    )
                                }
                                .distinctBy { it.backendId ?: it.username }
                                .take(10)
                        }
                    } else {
                        homeErrorMessage = response.message().ifBlank { "Could not load feed" }
                    }
                }
            } catch (e: Exception) {
                homeErrorMessage = e.localizedMessage ?: "Could not load feed"
            } finally {
                isHomeRefreshing = false
            }
        }
    }

    private suspend fun loadUnreadNotifications(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUnreadFetchAtMs < UNREAD_COUNT_TTL_MS) return
        val response = runCatching { notificationRepository.getUnreadCount() }.getOrNull()
        if (response?.isSuccessful == true) {
            unreadNotificationsCount = response.body()?.data?.unreadCount ?: 0
            lastUnreadFetchAtMs = now
        }
    }

    fun refreshUnreadNotifications(force: Boolean = false) {
        if (unreadRefreshJob?.isActive == true) return
        unreadRefreshJob = viewModelScope.launch {
            loadUnreadNotifications(force)
        }
    }

    private suspend fun loadUnreadMessagesSummary(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUnreadMessagesFetchAtMs < UNREAD_MESSAGES_TTL_MS) return

        coroutineScope {
            val summaryDeferred = async { runCatching { chatRepository.getSummary() }.getOrNull() }
            val groupsDeferred = async { runCatching { groupChatRepository.getGroupChats(page = 1, limit = 50) }.getOrNull() }

            val summaryResponse = summaryDeferred.await()
            if (summaryResponse?.isSuccessful == true) {
                val totalUnreadMessages = summaryResponse.body()?.data?.totalUnreadMessages ?: 0
                MessagesInboxCache.setDirectUnreadSummary(totalUnreadMessages)
            }

            val groupsResponse = groupsDeferred.await()
            if (groupsResponse?.isSuccessful == true) {
                val groups = groupsResponse.body()?.data.orEmpty().map { item ->
                    GroupChat(
                        backendId = item.id,
                        name = item.name,
                        avatar = item.avatar,
                        lastMessage = item.lastMessage ?: "No messages yet",
                        time = formatRelativeInboxTime(item.lastMessageAt),
                        unreadCount = item.unreadCount
                    )
                }
                MessagesInboxCache.updateGroups(groups)
            }

            if (summaryResponse?.isSuccessful == true || groupsResponse?.isSuccessful == true) {
                lastUnreadMessagesFetchAtMs = now
            }
        }
    }

    fun refreshUnreadMessagesSummary(force: Boolean = false) {
        if (unreadMessagesRefreshJob?.isActive == true) return
        unreadMessagesRefreshJob = viewModelScope.launch {
            loadUnreadMessagesSummary(force)
        }
    }

    fun loadNextFeedPage() {
        if (isHomeRefreshing || isHomeLoadingMore || !feedHasMore) return
        viewModelScope.launch {
            isHomeLoadingMore = true
            try {
                val nextPage = feedPage + 1
                val (likedPostIds, savedPostIds) = loadInteractionIds()
                val response = postRepository.getFeed(nextPage, 20)
                if (response.isSuccessful) {
                    val body = response.body()
                    val feedItems = body?.data ?: emptyList()
                    val mapped = feedItems.map { model ->
                        PostData(
                            id = model.id.hashCode(),
                            backendId = model.id,
                            authorId = model.author.id,
                            user = model.author.username,
                            authorFullName = model.author.fullName,
                            image = model.media.firstOrNull()?.url,
                            thumbnailUrl = model.media.firstOrNull()?.thumbnailUrl,
                            userAvatar = model.author.avatar,
                            caption = model.caption,
                            likes = model.likesCount,
                            comments = model.commentsCount,
                            isLiked = likedPostIds.contains(model.id),
                            isSaved = savedPostIds.contains(model.id),
                            isVideo = model.media.firstOrNull()?.type == "video",
                            mediaAspectRatio = model.media.firstOrNull()?.let { mediaAspectRatioFromDimensions(it.width, it.height) },
                            authorFollowStatus = model.author.followStatus,
                            createdAt = model.createdAt
                        )
                    }
                    val existingIds = posts.mapNotNull { it.backendId }.toSet()
                    val deduped = mapped.filter { it.backendId !in existingIds }
                    posts = posts + deduped

                    feedPage = body?.meta?.page ?: nextPage
                    val totalPages = body?.meta?.totalPages ?: feedPage
                    feedHasMore = feedPage < totalPages && feedItems.isNotEmpty()
                } else {
                    homeErrorMessage = response.message().ifBlank { "Could not load more posts" }
                }
            } catch (e: Exception) {
                homeErrorMessage = e.localizedMessage ?: "Could not load more posts"
            } finally {
                isHomeLoadingMore = false
            }
        }
    }

    fun refreshStories(force: Boolean = false) {
        if (storiesRefreshJob?.isActive == true) return
        storiesRefreshJob = viewModelScope.launch {
            try {
                val currentUser = loadFreshCurrentUser(force)
                currentUserProfile = currentUser
                val response = storyRepository.getStoriesFeed(1, 20)
                if (response.isSuccessful) {
                    val storyModels = response.body()?.data ?: emptyList()
                    // Group stories by author
                    val grouped = storyModels.groupBy { it.author.id }
                    storyProfiles = grouped.map { (authorId, stories) ->
                        val author = stories.first().author
                        StoryProfile(
                            id = authorId.hashCode(),
                            name = author.username,
                            avatar = author.avatar,
                            isMine = author.id == currentUser?.id,
                            isSeen = stories.all { it.isViewedByMe },
                            stories = stories.map { s ->
                                StoryMedia(
                                    id = s.id.hashCode(),
                                    backendId = s.id,
                                    mediaUrl = s.media.url,
                                    isVideo = s.media.type == "video",
                                    viewersCount = s.viewersCount,
                                    likesCount = s.likesCount,
                                    repliesCount = s.repliesCount,
                                    isLiked = s.isLikedByMe
                                )
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowSnackbar("Stories error: ${e.localizedMessage ?: "Could not load stories"}"))
            }
        }
    }

    fun refreshSearch() {
        // Search logic moved to SearchViewModel
    }

    fun refreshProfile(username: String? = null, force: Boolean = false) {
        if (profileRefreshJob?.isActive == true) return
        profileRefreshJob = viewModelScope.launch {
            isProfileRefreshing = true
            profileErrorMessage = null
            try {
                coroutineScope {
                    val interactionDeferred = async { loadInteractionIds(force) }
                    var targetUsername = username
                    if (targetUsername == null) {
                        val currentUser = loadFreshCurrentUser(force)
                        targetUsername = currentUser?.username
                    }
                    
                    if (targetUsername != null) {
                        val response = postRepository.getUserPosts(targetUsername, 1, 20)
                        if (response.isSuccessful) {
                            val profileItems = response.body()?.data ?: emptyList()
                            val (likedPostIds, savedPostIds) = interactionDeferred.await()
                            profilePosts = profileItems.map { model ->
                                PostData(
                                    id = model.id.hashCode(),
                                    backendId = model.id,
                                    authorId = model.author.id,
                                    user = model.author.username,
                                    authorFullName = model.author.fullName,
                                    image = model.media.firstOrNull()?.url,
                                    thumbnailUrl = model.media.firstOrNull()?.thumbnailUrl,
                                    userAvatar = model.author.avatar,
                                    caption = model.caption,
                                    likes = model.likesCount,
                                    comments = model.commentsCount,
                                    isLiked = likedPostIds.contains(model.id),
                                    isSaved = savedPostIds.contains(model.id),
                                    isVideo = model.media.firstOrNull()?.type == "video",
                                    mediaAspectRatio = model.media.firstOrNull()?.let { mediaAspectRatioFromDimensions(it.width, it.height) },
                                    authorFollowStatus = model.author.followStatus,
                                    createdAt = model.createdAt
                                )
                            }
                        } else {
                            profileErrorMessage = response.message().ifBlank { "Could not load profile posts" }
                        }
                    }
                }
            } catch (e: Exception) {
                profileErrorMessage = e.localizedMessage ?: "Could not load profile posts"
            } finally {
                isProfileRefreshing = false
            }
        }
    }

    fun refreshReels(force: Boolean = false) {
        if (reelsRefreshJob?.isActive == true) return
        reelsRefreshJob = viewModelScope.launch {
            isReelsRefreshing = true
            reelsErrorMessage = null
            try {
                coroutineScope {
                    val interactionDeferred = async { loadInteractionIds(force) }
                    val response = recommendationRepository.getMyReels(1, 20)
                    if (response.isSuccessful) {
                        val body = response.body()
                        val feedItems = body?.data.orEmpty()
                        val (likedPostIds, savedPostIds) = interactionDeferred.await()
                        reelsPage = body?.meta?.page ?: 1
                        val totalPages = body?.meta?.totalPages ?: 1
                        reelsHasMore = reelsPage < totalPages
                        reels = feedItems.map { model ->
                            PostData(
                                id = model.id.hashCode(),
                                backendId = model.id,
                                authorId = model.author.id,
                                user = model.author.username,
                                authorFullName = model.author.fullName,
                                image = model.media.firstOrNull()?.url,
                                thumbnailUrl = model.media.firstOrNull()?.thumbnailUrl,
                                userAvatar = model.author.avatar,
                                caption = model.caption,
                                likes = model.likesCount,
                                comments = model.commentsCount,
                                isLiked = likedPostIds.contains(model.id),
                                isSaved = savedPostIds.contains(model.id),
                                isVideo = model.media.firstOrNull()?.type == "video",
                                mediaAspectRatio = model.media.firstOrNull()?.let { mediaAspectRatioFromDimensions(it.width, it.height) },
                                authorFollowStatus = model.author.followStatus,
                                createdAt = model.createdAt
                            )
                        }.filter { it.isVideo }
                    } else {
                        reelsErrorMessage = response.message().ifBlank { "Could not load reels" }
                    }
                }
            } catch (e: Exception) {
                reelsErrorMessage = e.localizedMessage ?: "Could not load reels"
            } finally {
                isReelsRefreshing = false
            }
        }
    }

    fun loadNextReelsPage() {
        if (isReelsRefreshing || isReelsLoadingMore || !reelsHasMore) return
        viewModelScope.launch {
            isReelsLoadingMore = true
            try {
                val nextPage = reelsPage + 1
                val (likedPostIds, savedPostIds) = loadInteractionIds()
                val response = recommendationRepository.getMyReels(nextPage, 20)
                if (response.isSuccessful) {
                    val body = response.body()
                    val feedItems = body?.data.orEmpty()
                    val mapped = feedItems.map { model ->
                        PostData(
                            id = model.id.hashCode(),
                            backendId = model.id,
                            authorId = model.author.id,
                            user = model.author.username,
                            authorFullName = model.author.fullName,
                            image = model.media.firstOrNull()?.url,
                            thumbnailUrl = model.media.firstOrNull()?.thumbnailUrl,
                            userAvatar = model.author.avatar,
                            caption = model.caption,
                            likes = model.likesCount,
                            comments = model.commentsCount,
                            isLiked = likedPostIds.contains(model.id),
                            isSaved = savedPostIds.contains(model.id),
                            isVideo = model.media.firstOrNull()?.type == "video",
                            mediaAspectRatio = model.media.firstOrNull()?.let { mediaAspectRatioFromDimensions(it.width, it.height) },
                            authorFollowStatus = model.author.followStatus,
                            createdAt = model.createdAt
                        )
                    }.filter { it.isVideo }
                    val existingIds = reels.mapNotNull { it.backendId }.toSet()
                    val deduped = mapped.filter { it.backendId !in existingIds }
                    reels = reels + deduped

                    reelsPage = body?.meta?.page ?: nextPage
                    val totalPages = body?.meta?.totalPages ?: reelsPage
                    reelsHasMore = reelsPage < totalPages && feedItems.isNotEmpty()
                } else {
                    reelsErrorMessage = response.message().ifBlank { "Could not load more reels" }
                }
            } catch (e: Exception) {
                reelsErrorMessage = e.localizedMessage ?: "Could not load more reels"
            } finally {
                isReelsLoadingMore = false
            }
        }
    }

    fun toggleFollowFromCard(profile: RecommendedProfile) {
        val userId = profile.backendId ?: return
        val currentStatus = profile.followStatus.lowercase().ifBlank {
            if (profile.isFollowed) "following" else "not_following"
        }
        if (currentStatus == "self") return
        val willFollow = currentStatus == "not_following"
        val optimisticStatus = if (willFollow) "following" else "not_following"
        recommendedProfiles = recommendedProfiles.map {
            if (it.backendId == userId) it.copy(isFollowed = optimisticStatus == "following", followStatus = optimisticStatus) else it
        }
        FollowEvents.emit(
            FollowEvent(
                userId = userId,
                username = profile.username,
                isFollowing = optimisticStatus == "following",
                followStatus = optimisticStatus
            )
        )

        viewModelScope.launch {
            if (willFollow) {
                val response = runCatching { followRepository.followUser(userId) }.getOrNull()
                if (response?.isSuccessful != true) {
                    // Revert
                    recommendedProfiles = recommendedProfiles.map {
                        if (it.backendId == userId) {
                            it.copy(
                                isFollowed = currentStatus == "following",
                                followStatus = currentStatus
                            )
                        } else it
                    }
                    FollowEvents.emit(
                        FollowEvent(
                            userId = userId,
                            username = profile.username,
                            isFollowing = currentStatus == "following",
                            followStatus = currentStatus
                        )
                    )
                    _uiEvent.emit(UiEvent.ShowSnackbar(response?.message()?.ifBlank { "Follow action failed" } ?: "Follow action failed"))
                } else {
                    val responseStatus = response.body()?.data?.status?.lowercase()?.trim()
                    val finalStatus = when (responseStatus) {
                        "following", "requested", "self", "not_following" -> responseStatus
                        else -> "following"
                    }
                    recommendedProfiles = recommendedProfiles.map {
                        if (it.backendId == userId) {
                            it.copy(
                                isFollowed = finalStatus == "following",
                                followStatus = finalStatus
                            )
                        } else it
                    }
                    FollowEvents.emit(
                        FollowEvent(
                            userId = userId,
                            username = profile.username,
                            isFollowing = finalStatus == "following",
                            followStatus = finalStatus
                        )
                    )
                }
            } else {
                val response = runCatching { followRepository.unfollowUser(userId) }.getOrNull()
                if (response?.isSuccessful == true) {
                    recommendedProfiles = recommendedProfiles.map {
                        if (it.backendId == userId) {
                            it.copy(
                                isFollowed = false,
                                followStatus = "not_following"
                            )
                        } else it
                    }
                    FollowEvents.emit(
                        FollowEvent(
                            userId = userId,
                            username = profile.username,
                            isFollowing = false,
                            followStatus = "not_following"
                        )
                    )
                } else {
                    recommendedProfiles = recommendedProfiles.map {
                        if (it.backendId == userId) {
                            it.copy(
                                isFollowed = currentStatus == "following",
                                followStatus = currentStatus
                            )
                        } else it
                    }
                    FollowEvents.emit(
                        FollowEvent(
                            userId = userId,
                            username = profile.username,
                            isFollowing = currentStatus == "following",
                            followStatus = currentStatus
                        )
                    )
                    _uiEvent.emit(UiEvent.ShowSnackbar(response?.message()?.ifBlank { "Follow action failed" } ?: "Follow action failed"))
                }
            }
        }
    }

    fun toggleFollowByUserId(
        userId: String,
        username: String?,
        isCurrentlyFollowing: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        val currentStatus = if (isCurrentlyFollowing) "following" else "not_following"
        if (currentStatus == "self") return
        val willFollow = currentStatus == "not_following"
        viewModelScope.launch {
            if (willFollow) {
                val response = runCatching { followRepository.followUser(userId) }.getOrNull()
                if (response?.isSuccessful == true) {
                    val responseStatus = response.body()?.data?.status?.lowercase()?.trim()
                    val finalStatus = when (responseStatus) {
                        "following", "requested", "self", "not_following" -> responseStatus
                        else -> "following"
                    }
                    FollowEvents.emit(
                        FollowEvent(
                            userId = userId,
                            username = username,
                            isFollowing = finalStatus == "following",
                            followStatus = finalStatus
                        )
                    )
                    onResult(finalStatus == "following")
                } else {
                    _uiEvent.emit(UiEvent.ShowSnackbar(response?.message()?.ifBlank { "Follow action failed" } ?: "Follow action failed"))
                    onResult(isCurrentlyFollowing)
                }
            } else {
                val response = runCatching { followRepository.unfollowUser(userId) }.getOrNull()
                if (response?.isSuccessful == true) {
                    FollowEvents.emit(
                        FollowEvent(
                            userId = userId,
                            username = username,
                            isFollowing = false,
                            followStatus = "not_following"
                        )
                    )
                    onResult(false)
                } else {
                    _uiEvent.emit(UiEvent.ShowSnackbar(response?.message()?.ifBlank { "Follow action failed" } ?: "Follow action failed"))
                    onResult(isCurrentlyFollowing)
                }
            }
        }
    }

    fun refreshMessages() {
        if (messagesRefreshJob?.isActive == true) return
        messagesRefreshJob = viewModelScope.launch {
            isMessagesRefreshing = true
            try {
                loadUnreadMessagesSummary(force = true)
                val chats = runCatching { chatRepository.getConversations(page = 1, limit = 1) }.getOrNull()
                val groups = runCatching { groupChatRepository.getGroupChats(page = 1, limit = 1) }.getOrNull()
                if (chats?.isSuccessful != true && groups?.isSuccessful != true) {
                    _uiEvent.emit(UiEvent.ShowSnackbar("Could not refresh messages"))
                }
            } catch (_: Exception) {
                _uiEvent.emit(UiEvent.ShowSnackbar("Could not refresh messages"))
            }
            isMessagesRefreshing = false
        }
    }

    private fun formatRelativeInboxTime(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return "Now"
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US)
            val date = parser.parse(timestamp) ?: return@runCatching "Now"
            val diff = System.currentTimeMillis() - date.time
            when {
                diff < 60_000L -> "Now"
                diff < 3_600_000L -> "${(diff / 60_000L).coerceAtLeast(1)}m"
                diff < 86_400_000L -> "${(diff / 3_600_000L).coerceAtLeast(1)}h"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(date.time))
            }
        }.getOrDefault("Now")
    }

    fun toggleLike(post: PostData) {
        val postId = post.backendId ?: return
        val previousPosts = posts
        val previousProfilePosts = profilePosts
        val toggled = !post.isLiked

        updatePostCollections(postId) {
            it.copy(
                isLiked = toggled,
                likes = if (toggled) it.likes + 1 else maxOf(it.likes - 1, 0)
            )
        }

        viewModelScope.launch {
            val response = runCatching {
                if (toggled) interactionRepository.likePost(postId) else interactionRepository.unlikePost(postId)
            }.getOrNull()
            val result = response?.body()?.data
            if (response?.isSuccessful == true && result != null) {
                updatePostCollections(postId) {
                    it.copy(isLiked = result.liked, likes = result.likesCount)
                }
            } else {
                posts = previousPosts
                profilePosts = previousProfilePosts
            }
        }
    }

    fun toggleSave(post: PostData) {
        val postId = post.backendId ?: return
        val previousPosts = posts
        val previousProfilePosts = profilePosts
        val toggled = !post.isSaved

        updatePostCollections(postId) { it.copy(isSaved = toggled) }

        viewModelScope.launch {
            val response = runCatching {
                if (toggled) interactionRepository.savePost(postId) else interactionRepository.unsavePost(postId)
            }.getOrNull()
            val result = response?.body()?.data
            if (response?.isSuccessful == true && result != null) {
                updatePostCollections(postId) { item -> item.copy(isSaved = result.saved) }
            } else {
                posts = previousPosts
                profilePosts = previousProfilePosts
            }
        }
    }

    fun handleCommentAdded(postId: String) {
        updatePostCollections(postId) { it.copy(comments = it.comments + 1) }
    }

    private suspend fun loadInteractionIds(force: Boolean = false): Pair<Set<String>, Set<String>> = coroutineScope {
        val now = System.currentTimeMillis()
        cachedInteractionIds?.let { cached ->
            if (!force && now - lastInteractionFetchAtMs < INTERACTION_IDS_TTL_MS) return@coroutineScope cached
        }
        val likedDeferred = async {
            runCatching {
                interactionRepository.getLikedPosts(limit = 50).body()?.data
                    ?.mapNotNull { it.post?.id }
                    ?.toSet()
            }.getOrNull().orEmpty()
        }
        val savedDeferred = async {
            runCatching {
                interactionRepository.getSavedPosts(limit = 50).body()?.data
                    ?.mapNotNull { it.post?.id }
                    ?.toSet()
            }.getOrNull().orEmpty()
        }

        val result = likedDeferred.await() to savedDeferred.await()
        cachedInteractionIds = result
        lastInteractionFetchAtMs = now
        result
    }

    private fun updatePostCollections(postId: String, transform: (PostData) -> PostData) {
        posts = posts.map { if (it.backendId == postId) transform(it) else it }
        profilePosts = profilePosts.map { if (it.backendId == postId) transform(it) else it }
    }
}
