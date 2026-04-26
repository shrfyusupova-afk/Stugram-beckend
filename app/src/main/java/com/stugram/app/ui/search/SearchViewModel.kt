package com.stugram.app.ui.search

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stugram.app.data.remote.SearchHistoryItem
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.repository.SearchRepository
import com.stugram.app.data.repository.FollowRepository
import com.stugram.app.data.repository.PostInteractionRepository
import com.stugram.app.data.repository.ExploreRepository
import com.stugram.app.core.social.FollowEvents
import com.stugram.app.ui.home.mediaAspectRatioFromDimensions
import com.stugram.app.ui.home.PostData
import com.stugram.app.ui.home.RecommendedProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val searchRepository = SearchRepository()
    private val followRepository = FollowRepository()
    private val interactionRepository = PostInteractionRepository()
    private val exploreRepository = ExploreRepository()

    var searchQuery by mutableStateOf("")
    var isSearchLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var searchErrorMessage by mutableStateOf<String?>(null)
    
    // Results
    var userResults by mutableStateOf<List<RecommendedProfile>>(emptyList())
    var postResults by mutableStateOf<List<PostData>>(emptyList())
    var historyResults by mutableStateOf<List<SearchHistoryItem>>(emptyList())
    var pendingFollowIds by mutableStateOf<Set<String>>(emptySet())
    
    // Explore Data
    var activeCreators by mutableStateOf<List<RecommendedProfile>>(emptyList())
    var trendingPosts by mutableStateOf<List<PostData>>(emptyList())
    
    // Filters
    var selectedViloyat by mutableStateOf<String?>(null)
    var selectedTuman by mutableStateOf<String?>(null)
    var selectedMaktab by mutableStateOf<String?>(null)
    var selectedSinf by mutableStateOf<String?>(null)
    var selectedGuruh by mutableStateOf<String?>(null)

    private var searchJob: Job? = null
    private var exploreJob: Job? = null
    private var historyJob: Job? = null
    private var refreshJob: Job? = null

    init {
        loadExploreData()
        loadSearchHistory()
        observeFollowEvents()
    }

    private fun observeFollowEvents() {
        viewModelScope.launch {
            FollowEvents.events.collect { event ->
                val userId = event.userId ?: return@collect
                val followStatus = event.followStatus?.lowercase()?.trim()
                    ?: if (event.isFollowing) "following" else "not_following"
                userResults = userResults.map {
                    if (it.backendId == userId) {
                        it.copy(
                            isFollowed = followStatus == "following",
                            followStatus = followStatus
                        )
                    } else it
                }
                activeCreators = activeCreators.map {
                    if (it.backendId == userId) {
                        it.copy(
                            isFollowed = followStatus == "following",
                            followStatus = followStatus
                        )
                    } else it
                }
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        searchQuery = newQuery
        searchErrorMessage = null
        searchJob?.cancel()
        if (newQuery.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                delay(500) // Debounce
                performSearch(newQuery.trim())
            }
        } else if (newQuery.isBlank() && !hasActiveFilters()) {
            userResults = emptyList()
            postResults = emptyList()
        }
    }

    private fun loadExploreData() {
        if (exploreJob?.isActive == true) return
        exploreJob = viewModelScope.launch {
            loadExploreDataNow()
        }
    }

    private fun loadSearchHistory() {
        if (historyJob?.isActive == true) return
        historyJob = viewModelScope.launch {
            loadSearchHistoryNow()
        }
    }

    private suspend fun loadExploreDataNow() {
        try {
            coroutineScope {
                val interactionDeferred = async { loadInteractionIds() }
                val creatorsDeferred = async { exploreRepository.getCreators(page = 1, limit = 30) }
                val trendingDeferred = async { exploreRepository.getTrending(limit = 60) }
                val (likedIds, savedIds) = interactionDeferred.await()
                val creatorsRes = creatorsDeferred.await()
                if (creatorsRes.isSuccessful) {
                    activeCreators = creatorsRes.body()?.data?.map { it.toUIModel() } ?: emptyList()
                }

                val trendingRes = trendingDeferred.await()
                if (trendingRes.isSuccessful) {
                    val trendingData = trendingRes.body()?.data
                    trendingPosts = buildExploreLoop(trendingData?.reels.orEmpty(), trendingData?.posts.orEmpty())
                        .distinctBy { it.id }
                        .map { it.toPostData(likedIds, savedIds) }
                    if (activeCreators.isEmpty()) {
                        activeCreators = trendingData?.creators.orEmpty().map { it.toUIModel() }
                    }
                }
            }
        } catch (e: Exception) {
            searchErrorMessage = "Qidiruv ma'lumotlarini yuklashda xatolik yuz berdi"
        }
    }

    private suspend fun loadSearchHistoryNow() {
        try {
            val res = searchRepository.getSearchHistory(1, 20)
            if (res.isSuccessful) {
                historyResults = res.body()?.data ?: emptyList()
            }
        } catch (e: Exception) {}
    }

    suspend fun performSearch(query: String = searchQuery) {
        if (isSearchLoading) return
        val normalizedQuery = query.trim()
        isSearchLoading = true
        searchErrorMessage = null
        try {
            coroutineScope {
                val interactionDeferred = async { loadInteractionIds() }
                val userDeferred = async {
                    if (hasActiveFilters()) {
                        searchRepository.searchUsersAdvanced(
                            region = selectedViloyat,
                            district = selectedTuman,
                            school = selectedMaktab,
                            grade = selectedSinf,
                            group = selectedGuruh,
                            page = 1,
                            limit = 20
                        )
                    } else {
                        searchRepository.searchUsers(normalizedQuery, 1, 20)
                    }
                }
                val postDeferred = async {
                    if (normalizedQuery.isNotBlank()) {
                        searchRepository.searchPosts(normalizedQuery, 1, 20)
                    } else null
                }

                val (likedIds, savedIds) = interactionDeferred.await()
                val userRes = userDeferred.await()
                if (userRes.isSuccessful) {
                    userResults = userRes.body()?.data?.map { it.toUIModel() } ?: emptyList()
                }

                val postRes = postDeferred.await()
                if (normalizedQuery.isNotBlank()) {
                    if (postRes?.isSuccessful == true) {
                        postResults = postRes.body()?.data?.map { it.toPostData(likedIds, savedIds) } ?: emptyList()
                    }
                } else {
                    postResults = emptyList()
                }
                
                // Save search history
                if (normalizedQuery.isNotEmpty()) {
                    searchRepository.saveSearchHistory(normalizedQuery, "text")
                }
            }
        } catch (e: Exception) {
            searchErrorMessage = "Qidiruv natijalarini yuklab bo'lmadi"
        } finally {
            isSearchLoading = false
        }
    }

    fun hasActiveFilters(): Boolean {
        return selectedViloyat != null || selectedTuman != null || 
               selectedMaktab != null || selectedSinf != null || selectedGuruh != null
    }

    fun isShowingExploreState(): Boolean = searchQuery.isBlank() && !hasActiveFilters()

    fun applyFilters(
        viloyat: String?, 
        tuman: String?, 
        maktab: String?, 
        sinf: String?, 
        guruh: String?
    ) {
        selectedViloyat = viloyat
        selectedTuman = tuman
        selectedMaktab = maktab
        selectedSinf = sinf
        selectedGuruh = guruh
        
        viewModelScope.launch {
            if (hasActiveFilters() || searchQuery.isNotBlank()) {
                performSearch(searchQuery)
            } else {
                userResults = emptyList()
                postResults = emptyList()
            }
        }
    }

    fun toggleFollow(userId: String) {
        if (pendingFollowIds.contains(userId)) return
        val current = (userResults.firstOrNull { it.backendId == userId }
            ?: activeCreators.firstOrNull { it.backendId == userId })
        val currentStatus = current?.followStatus?.lowercase()?.trim()
            ?: if (current?.isFollowed == true) "following" else "not_following"
        if (currentStatus == "self") return
        val willFollow = currentStatus == "not_following"
        val optimisticStatus = if (willFollow) "following" else "not_following"

        // Optimistically update local state (global event is emitted only after backend success).
        pendingFollowIds = pendingFollowIds + userId
        userResults = userResults.map {
            if (it.backendId == userId) {
                it.copy(isFollowed = optimisticStatus == "following", followStatus = optimisticStatus)
            } else it
        }
        activeCreators = activeCreators.map {
            if (it.backendId == userId) {
                it.copy(isFollowed = optimisticStatus == "following", followStatus = optimisticStatus)
            } else it
        }

        viewModelScope.launch {
            try {
                if (willFollow) {
                    val followRes = followRepository.followUser(userId)
                    if (followRes.isSuccessful) {
                        val responseStatus = followRes.body()?.data?.status?.lowercase()?.trim()
                        val finalStatus = when (responseStatus) {
                            "following", "requested", "self", "not_following" -> responseStatus
                            else -> optimisticStatus
                        }
                        com.stugram.app.core.social.FollowEvents.emit(
                            com.stugram.app.core.social.FollowEvent(
                                userId = userId,
                                username = current?.username,
                                isFollowing = finalStatus == "following",
                                followStatus = finalStatus
                            )
                        )
                    } else {
                        // Revert optimistic state if backend rejected.
                        userResults = userResults.map {
                            if (it.backendId == userId) {
                                it.copy(
                                    isFollowed = currentStatus == "following",
                                    followStatus = currentStatus
                                )
                            } else it
                        }
                        activeCreators = activeCreators.map {
                            if (it.backendId == userId) {
                                it.copy(
                                    isFollowed = currentStatus == "following",
                                    followStatus = currentStatus
                                )
                            } else it
                        }
                        searchErrorMessage = followRes.message().ifBlank { "Follow action failed" }
                    }
                } else {
                    val unfollowRes = followRepository.unfollowUser(userId)
                    if (unfollowRes.isSuccessful) {
                        com.stugram.app.core.social.FollowEvents.emit(
                            com.stugram.app.core.social.FollowEvent(
                                userId = userId,
                                username = current?.username,
                                isFollowing = false,
                                followStatus = "not_following"
                            )
                        )
                    } else {
                        userResults = userResults.map {
                            if (it.backendId == userId) {
                                it.copy(
                                    isFollowed = currentStatus == "following",
                                    followStatus = currentStatus
                                )
                            } else it
                        }
                        activeCreators = activeCreators.map {
                            if (it.backendId == userId) {
                                it.copy(
                                    isFollowed = currentStatus == "following",
                                    followStatus = currentStatus
                                )
                            } else it
                        }
                        searchErrorMessage = unfollowRes.message().ifBlank { "Follow action failed" }
                    }
                }
            } catch (e: Exception) {
                userResults = userResults.map {
                    if (it.backendId == userId) {
                        it.copy(
                            isFollowed = currentStatus == "following",
                            followStatus = currentStatus
                        )
                    } else it
                }
                activeCreators = activeCreators.map {
                    if (it.backendId == userId) {
                        it.copy(
                            isFollowed = currentStatus == "following",
                            followStatus = currentStatus
                        )
                    } else it
                }
                searchErrorMessage = e.localizedMessage ?: "Follow action failed"
            } finally {
                pendingFollowIds = pendingFollowIds - userId
            }
        }
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            try {
                val res = searchRepository.deleteSearchHistoryItem(id)
                if (res.isSuccessful) {
                    historyResults = historyResults.filter { it._id != id }
                }
            } catch (e: Exception) {}
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                val res = searchRepository.clearSearchHistory()
                if (res.isSuccessful) {
                    historyResults = emptyList()
                }
            } catch (e: Exception) {}
        }
    }

    fun retrySearch() {
        viewModelScope.launch {
            if (hasActiveFilters() || searchQuery.isNotBlank()) {
                performSearch(searchQuery)
            } else {
                loadExploreData()
                loadSearchHistory()
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            isRefreshing = true
            try {
                if (hasActiveFilters() || searchQuery.isNotBlank()) {
                    performSearch(searchQuery)
                } else {
                    loadExploreDataNow()
                    loadSearchHistoryNow()
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun recordUserSelection(profile: RecommendedProfile) {
        viewModelScope.launch {
            try {
                val label = profile.username.ifBlank { profile.name }
                searchRepository.saveSearchHistory(
                    queryText = label,
                    searchType = "user",
                    targetId = profile.backendId
                )
                loadSearchHistory()
            } catch (_: Exception) {
            }
        }
    }

    fun toggleLike(post: PostData) {
        val postId = post.backendId ?: return
        val toggled = !post.isLiked
        updatePosts(postId) {
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
                updatePosts(postId) { it.copy(isLiked = result.liked, likes = result.likesCount) }
            } else {
                updatePosts(postId) { it.copy(isLiked = post.isLiked, likes = post.likes) }
            }
        }
    }

    fun toggleSave(post: PostData) {
        val postId = post.backendId ?: return
        val toggled = !post.isSaved
        updatePosts(postId) { it.copy(isSaved = toggled) }

        viewModelScope.launch {
            val response = runCatching {
                if (toggled) interactionRepository.savePost(postId) else interactionRepository.unsavePost(postId)
            }.getOrNull()
            val result = response?.body()?.data
            if (response?.isSuccessful == true && result != null) {
                updatePosts(postId) { it.copy(isSaved = result.saved) }
            } else {
                updatePosts(postId) { it.copy(isSaved = post.isSaved) }
            }
        }
    }

    fun handleCommentAdded(postId: String) {
        updatePosts(postId) { it.copy(comments = it.comments + 1) }
    }

    private fun updatePosts(postId: String, transform: (PostData) -> PostData) {
        postResults = postResults.map { if (it.backendId == postId) transform(it) else it }
        trendingPosts = trendingPosts.map { if (it.backendId == postId) transform(it) else it }
    }

    private suspend fun fetchLikedIds(): Set<String> = runCatching {
        interactionRepository.getLikedPosts(limit = 50).body()?.data
            ?.mapNotNull { it.post?.id }
            ?.toSet()
    }.getOrNull().orEmpty()

    private suspend fun fetchSavedIds(): Set<String> = runCatching {
        interactionRepository.getSavedPosts(limit = 50).body()?.data
            ?.mapNotNull { it.post?.id }
            ?.toSet()
    }.getOrNull().orEmpty()

    private suspend fun loadInteractionIds(): Pair<Set<String>, Set<String>> = coroutineScope {
        val likedDeferred = async { fetchLikedIds() }
        val savedDeferred = async { fetchSavedIds() }
        likedDeferred.await() to savedDeferred.await()
    }
}

// Extension to map API model to UI model
fun ProfileSummary.toUIModel(): RecommendedProfile {
    val followStatus = this.followStatus?.lowercase()?.trim().orEmpty().ifBlank { "not_following" }
    return RecommendedProfile(
        id = this.id.hashCode(),
        name = this.fullName,
        image = this.avatar,
        username = this.username,
        backendId = this.id,
        isFollowed = followStatus == "following",
        followStatus = followStatus
    )
}

private fun buildExploreLoop(reels: List<PostModel>, posts: List<PostModel>): List<PostModel> {
    val loop = mutableListOf<PostModel>()
    var reelIndex = 0
    var postIndex = 0
    while (reelIndex < reels.size || postIndex < posts.size) {
        repeat(2) {
            if (reelIndex < reels.size) {
                loop += reels[reelIndex]
                reelIndex += 1
            }
        }
        repeat(4) {
            if (postIndex < posts.size) {
                loop += posts[postIndex]
                postIndex += 1
            }
        }
    }
    return loop
}

private fun PostModel.toPostData(
    likedIds: Set<String> = emptySet(),
    savedIds: Set<String> = emptySet()
): PostData {
    return PostData(
        id = this.id.hashCode(),
        backendId = this.id,
        authorId = this.author.id,
        user = this.author.username,
        authorFullName = this.author.fullName,
        image = this.media.firstOrNull()?.url,
        thumbnailUrl = this.media.firstOrNull()?.thumbnailUrl,
        userAvatar = this.author.avatar,
        caption = this.caption,
        likes = this.likesCount,
        comments = this.commentsCount,
        isLiked = likedIds.contains(this.id),
        isSaved = savedIds.contains(this.id),
        isVideo = this.media.firstOrNull()?.type == "video",
        mediaAspectRatio = this.media.firstOrNull()?.let { mediaAspectRatioFromDimensions(it.width, it.height) },
        authorFollowStatus = this.author.followStatus,
        createdAt = this.createdAt
    )
}
