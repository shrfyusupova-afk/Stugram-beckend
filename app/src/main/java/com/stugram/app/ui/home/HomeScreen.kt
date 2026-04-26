package com.stugram.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import com.stugram.app.core.notification.ForegroundChatRegistry
import com.stugram.app.core.socket.ChatSocketManager
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.BuildConfig
import com.stugram.app.data.repository.DeviceRepository
import com.stugram.app.data.repository.MessagesInboxCache
import com.stugram.app.data.remote.model.RegisterPushTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import android.provider.Settings
import com.stugram.app.ui.auth.components.LoadingOverlay

/**
 * HomeScreen - Loyihaning asosiy mantiqiy markazi.
 * Ma'lumotlar va holatlar HomeViewModel ichida saqlanadi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToChat: (String, String, Boolean) -> Unit,
    onNavigateToGroupChat: (String, String) -> Unit,
    onNavigateToPost: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundColor = if (isDarkMode) GlobalBackgroundColor else Color(0xFFF2F2F2)
    val accentBlue = Color(0xFF00A3FF)
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val tokenManager = remember { TokenManager(context.applicationContext) }
    val chatSocketManager = remember { ChatSocketManager.getInstance(tokenManager) }
    val currentUser by tokenManager.currentUser.collectAsState(initial = null)
    val sessions by tokenManager.sessions.collectAsState(initial = emptyList())

    val listState = rememberLazyListState()
    var selectedCommentsPost by remember { mutableStateOf<PostData?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val profileSwitchingEnabled = false
    var showProfileSwitcher by remember { mutableStateOf(false) }
    var showAddAccountAuth by remember { mutableStateOf(false) }
    var isSessionTransitioning by remember { mutableStateOf(false) }
    var lastHandledUserId by remember { mutableStateOf<String?>(null) }
    var lastRealtimeUserId by remember { mutableStateOf<String?>(null) }
    var lastPushRegistrationKey by remember { mutableStateOf<String?>(null) }
    var reelsViewerSeed by remember { mutableStateOf<PostData?>(null) }
    var reelsViewerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(reelsViewerVisible) {
        if (!reelsViewerVisible && reelsViewerSeed != null) {
            kotlinx.coroutines.delay(170)
            if (!reelsViewerVisible) {
                reelsViewerSeed = null
            }
        }
    }

    LaunchedEffect(currentUser) {
        viewModel.syncCurrentUserSnapshot(currentUser)
    }

    fun refreshRealtimeAndPushIdentity(userId: String) {
        if (lastRealtimeUserId != userId) {
            lastRealtimeUserId = userId
        } else {
            return
        }
        val socket = ChatSocketManager.getInstance(tokenManager)
        socket.disconnect()
        socket.connect()

        // Notifications identity hardening: re-register push token under the active auth context.
        // Safe best-effort; failure should not block switching.
        val deviceRepository = DeviceRepository()
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result ?: return@addOnCompleteListener
            val registrationKey = "$userId:$token"
            if (lastPushRegistrationKey == registrationKey) return@addOnCompleteListener
            lastPushRegistrationKey = registrationKey
            scope.launch {
                runCatching {
                    deviceRepository.registerPushToken(
                        RegisterPushTokenRequest(
                            token = token,
                            deviceId = deviceId,
                            appVersion = BuildConfig.VERSION_NAME
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(currentUser?.id) {
        val user = currentUser
        if (user == null) {
            MessagesInboxCache.reset()
            if (sessions.isEmpty() && lastHandledUserId != null) {
                onNavigateToAuth()
            }
            return@LaunchedEffect
        }

        if (lastHandledUserId == null) {
            lastHandledUserId = user.id
            refreshRealtimeAndPushIdentity(user.id)
            return@LaunchedEffect
        }

        if (lastHandledUserId == user.id) return@LaunchedEffect

        isSessionTransitioning = true
        lastHandledUserId = user.id

        viewModel.closeStory()
        viewModel.toggleCamera(false)
        viewModel.toggleNotifications(false)
        viewModel.onTabSelected(viewModel.currentTab)
        viewModel.refreshForUserSwitch(user)
        viewModel.refreshUnreadMessagesSummary(force = true)
        refreshRealtimeAndPushIdentity(user.id)
        isSessionTransitioning = false
    }

    LaunchedEffect(currentUser?.id) {
        val activeUserId = currentUser?.id ?: return@LaunchedEffect

        launch {
            chatSocketManager.connectionEvents.collect { event ->
                if (event is com.stugram.app.core.socket.SocketConnectionEvent.Reconnected) {
                    viewModel.refreshUnreadMessagesSummary(force = true)
                }
            }
        }

        launch {
            chatSocketManager.newMessages.collect { message ->
                if (message.sender?.id == activeUserId) return@collect
                val conversationId = message.conversation ?: return@collect
                if (ForegroundChatRegistry.activeConversationId == conversationId) return@collect
                MessagesInboxCache.incrementChatUnread(
                    conversationId = conversationId,
                    name = message.sender?.fullName?.takeIf { it.isNotBlank() } ?: message.sender?.username ?: "New message",
                    username = message.sender?.username,
                    avatar = message.sender?.avatar,
                    preview = message.text ?: when (message.messageType) {
                        "image" -> "Photo"
                        "video" -> "Video"
                        "voice" -> "Voice message"
                        else -> "New message"
                    },
                    time = "Now"
                )
            }
        }

        launch {
            chatSocketManager.groupMessages.collect { (groupId, message) ->
                if (message.sender?.id == activeUserId) return@collect
                if (ForegroundChatRegistry.activeGroupId == groupId) return@collect
                MessagesInboxCache.incrementGroupUnread(
                    groupId = groupId,
                    preview = message.text ?: when (message.messageType) {
                        "image" -> "Photo"
                        "video" -> "Video"
                        "voice" -> "Voice message"
                        else -> "New message"
                    },
                    time = "Now"
                )
            }
        }

        launch {
            chatSocketManager.messageSeenEvents.collect { event ->
                val cleared = event.conversationId?.let { MessagesInboxCache.clearChatUnread(it) } == true
                if (!cleared) {
                    viewModel.refreshUnreadMessagesSummary(force = true)
                }
            }
        }

        launch {
            chatSocketManager.groupMessageSeenEvents.collect { event ->
                val cleared = event.groupId?.let { MessagesInboxCache.clearGroupUnread(it) } == true
                if (!cleared) {
                    viewModel.refreshUnreadMessagesSummary(force = true)
                }
            }
        }
    }

    // Feed pagination trigger: load next page when near end.
    LaunchedEffect(viewModel.currentTab, viewModel.posts.size) {
        if (viewModel.currentTab != 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { lastIndex ->
                val total = listState.layoutInfo.totalItemsCount
                lastIndex to total
            }
            .distinctUntilChanged()
            .filter { (lastIndex, total) -> total > 0 && lastIndex >= total - 4 }
            .collect {
                viewModel.loadNextFeedPage()
            }
    }

    LaunchedEffect(viewModel.uiEvent) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is HomeViewModel.UiEvent.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = if (viewModel.currentTab == 2) Color.Black else backgroundColor,
                bottomBar = {
                    // Kamera, Story, Bildirishnomalar yoki birovning profili ochiq bo'lsa navigatsiyani yashiramiz
                    val isOthersProfile = viewModel.currentTab == 4 && (viewModel.selectedProfileId != null || viewModel.selectedProfileUsername != null)
                    if (!viewModel.showCameraView && viewModel.activeStoryProfileIndex == null && !viewModel.showNotifications && !isOthersProfile && !reelsViewerVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassSlidingNavigation(
                            selectedTab = viewModel.currentTab,
                            onTabSelected = { 
                                if (viewModel.currentTab == it) {
                                    when (it) {
                                        0 -> viewModel.refreshHome()
                                        1 -> viewModel.refreshSearch()
                                        2 -> viewModel.refreshReels()
                                        3 -> viewModel.refreshMessages()
                                        4 -> viewModel.refreshProfile()
                                    }
                                } else {
                                    viewModel.onTabSelected(it) 
                                }
                            },
                            onProfileLongPress = {
                                if (profileSwitchingEnabled) showProfileSwitcher = true
                            },
                            isDarkMode = if (viewModel.currentTab == 2) true else isDarkMode,
                            unreadMessagesCount = viewModel.unreadMessagesCount,
                            modifier = if (viewModel.currentTab == 2) Modifier.graphicsLayer(alpha = 0.8f) else Modifier
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (viewModel.currentTab == 2) Color.Black else backgroundColor)
            ) {
                AnimatedContent(
                    targetState = viewModel.currentTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            // O'ngga o'tganda (Home -> Search kabi)
                            (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn())
                                .togetherWith(slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut())
                        } else {
                            // Chapga o'tganda
                            (slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn())
                                .togetherWith(slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut())
                        }
                    },
                    label = "main_nav"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> HomeTabScreen(
                            posts = viewModel.posts,
                            storyProfiles = viewModel.storyProfiles,
                            recommendedProfiles = viewModel.recommendedProfiles,
                            currentUserProfile = viewModel.currentUserProfile,
                            currentUserFollowingCount = viewModel.currentUserFollowingCount,
                            paddingValues = paddingValues,
                            accentBlue = accentBlue,
                            isDarkMode = isDarkMode,
                            contentColor = contentColor,
                            onThemeChange = onThemeChange,
                            onStoryClick = { viewModel.openStory(it) },
                            onCreateClick = { viewModel.toggleCamera(true) },
                            onCommentsClick = { selectedCommentsPost = it },
                            onProfileClick = { username -> viewModel.onTabSelected(4, profileUsername = username) },
                            onReelClick = {
                                reelsViewerSeed = it
                                reelsViewerVisible = true
                            },
                            onToggleAuthorFollow = { post, isFollowing, onResult ->
                                val userId = post.authorId
                                if (userId.isNullOrBlank()) return@HomeTabScreen
                                viewModel.toggleFollowByUserId(userId, post.user, isFollowing, onResult)
                            },
                            onToggleLike = { viewModel.toggleLike(it) },
                            onToggleSave = { viewModel.toggleSave(it) },
                            isRefreshing = viewModel.isHomeRefreshing,
                            onRefresh = { viewModel.refreshForPullToRefresh() },
                            listState = listState,
                            isLoadingMore = viewModel.isHomeLoadingMore,
                            onLoadMore = { viewModel.loadNextFeedPage() },
                            errorMessage = viewModel.homeErrorMessage,
                            showNotifications = viewModel.showNotifications,
                            onNotificationsToggle = { viewModel.toggleNotifications(it) },
                            unreadCount = viewModel.unreadNotificationsCount
                        )
                        1 -> SearchScreen(
                            isDarkMode = isDarkMode,
                            isRefreshing = viewModel.isSearchRefreshing,
                            onRefresh = { viewModel.refreshSearch() },
                            onProfileClick = { viewModel.onTabSelected(4, profileUsername = it) }
                        )
                        2 -> ReelsScreen(
                            reels = viewModel.reels,
                            accentBlue = accentBlue,
                            isDarkMode = isDarkMode,
                            isRefreshing = viewModel.isReelsRefreshing,
                            onRefresh = { viewModel.refreshReels() },
                            errorMessage = viewModel.reelsErrorMessage,
                            onToggleLike = { viewModel.toggleLike(it) },
                            onToggleSave = { viewModel.toggleSave(it) },
                            onToggleFollow = { post, isFollowing, onResult ->
                                val userId = post.authorId
                                if (userId.isNullOrBlank()) return@ReelsScreen
                                // HomeViewModel doesn't expose direct follow helpers; we rely on the existing
                                // follow consistency flow that emits events on success.
                                viewModel.toggleFollowByUserId(userId, post.user, isFollowing, onResult)
                            },
                            currentUserId = currentUser?.id,
                            onCommentAdded = { viewModel.handleCommentAdded(it) },
                            onProfileClick = { username -> viewModel.onTabSelected(4, profileUsername = username) }
                        )
                        3 -> MessagesScreen(
                            isDarkMode = isDarkMode, 
                            onBack = { viewModel.onTabSelected(0) }, 
                            onNavigateToChat = onNavigateToChat, 
                            onNavigateToGroupChat = onNavigateToGroupChat,
                            onNavigateToProfile = { username ->
                                viewModel.onTabSelected(4, profileUsername = username)
                            },
                            onNavigateToPost = onNavigateToPost,
                            isRefreshing = viewModel.isMessagesRefreshing,
                            onRefresh = { viewModel.refreshMessages() }
                        )
                        4 -> ProfileScreen(
                            isDarkMode = isDarkMode,
                            isRefreshing = viewModel.isProfileRefreshing,
                            onRefresh = {
                                viewModel.refreshProfile(
                                    username = viewModel.selectedProfileUsername,
                                    force = true
                                )
                            },
                            onThemeChange = onThemeChange,
                            isMyProfile = viewModel.selectedProfileId == null && viewModel.selectedProfileUsername == null,
                            profileId = viewModel.selectedProfileId,
                            profileUsername = viewModel.selectedProfileUsername,
                            onBack = { viewModel.onTabSelected(0) },
                            onNavigateToChat = { conversationId, userName ->
                                onNavigateToChat(conversationId, userName, false)
                            },
                            onNavigateToProfile = { username ->
                                viewModel.onTabSelected(4, profileUsername = username)
                            }
                        )
                    }
                }

                // Reels shaffof navigatsiyasi endi Scaffold bottomBar ichida markazlashtirilgan holda boshqariladi
                // Shuning uchun bu yerdagi alohida blok olib tashlandi
            }
        }

        val showReelsViewer = reelsViewerVisible
        AnimatedVisibility(
            visible = showReelsViewer,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.985f, transformOrigin = TransformOrigin.Center),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.985f, transformOrigin = TransformOrigin.Center)
        ) {
            val seed = reelsViewerSeed ?: return@AnimatedVisibility
            val viewerReels = remember(viewModel.reels, seed.backendId, seed.id) {
                buildReelsViewerList(viewModel.reels, seed)
            }
            val initialPage = remember(viewerReels, seed.backendId, seed.id) {
                viewerReels.indexOfFirst { sameReelIdentity(it, seed) }.takeIf { it >= 0 } ?: 0
            }

            Box(modifier = Modifier.fillMaxSize().zIndex(20f)) {
                ReelsScreen(
                    reels = viewerReels,
                    accentBlue = accentBlue,
                    isDarkMode = isDarkMode,
                    isRefreshing = viewModel.isReelsRefreshing,
                    onRefresh = { viewModel.refreshReels() },
                    errorMessage = viewModel.reelsErrorMessage,
                    onToggleLike = { viewModel.toggleLike(it) },
                    onToggleSave = { viewModel.toggleSave(it) },
                    onToggleFollow = { post, isFollowing, onResult ->
                        val userId = post.authorId
                        if (userId.isNullOrBlank()) return@ReelsScreen
                        viewModel.toggleFollowByUserId(userId, post.user, isFollowing, onResult)
                    },
                    currentUserId = currentUser?.id,
                    onCommentAdded = { viewModel.handleCommentAdded(it) },
                    onProfileClick = { username -> viewModel.onTabSelected(4, profileUsername = username) },
                    initialPage = initialPage,
                    contentBottomPadding = 28.dp,
                    onClose = { reelsViewerVisible = false }
                )
            }
        }

        // --- Overlays ---
        AnimatedVisibility(
            visible = viewModel.showCameraView,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            CameraScreen(
                onDismiss = { viewModel.toggleCamera(false) },
                accentBlue = accentBlue,
	                onPostCreated = {
	                    viewModel.refreshAfterContentCreation("post")
	                },
	                onStoryCreated = {
	                    viewModel.refreshAfterContentCreation("story")
	                },
	                onReelCreated = {
	                    viewModel.refreshAfterContentCreation("reel")
	                }
            )
        }

        AnimatedContent(
            targetState = viewModel.activeStoryProfileIndex,
            transitionSpec = {
                if (targetState != null) {
                    (slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(400)) + scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin.Center))
                        .togetherWith(fadeOut(tween(200)))
                } else {
                    fadeIn(tween(200))
                        .togetherWith(slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(400)) + scaleOut(targetScale = 0.85f, transformOrigin = TransformOrigin.Center))
                }
            },
            label = "story_modal"
        ) { index ->
            if (index != null) {
                StoryViewerModal(
                    storyProfiles = viewModel.storyProfiles,
                    startProfileIndex = index,
                    onDismiss = { viewModel.closeStory() },
                    onLike = { viewModel.likeStory(it) },
                    onReply = { storyId, text -> viewModel.replyToStory(storyId, text) },
                    onShowInsights = { storyId -> viewModel.toggleStoryInsights(true, storyId) },
                    onDelete = { viewModel.deleteStory(it) },
                    onNavigateToChat = { conversationId, userName ->
                        viewModel.closeStory()
                        onNavigateToChat(conversationId, userName, false)
                    },
                    showInsightsSheet = viewModel.showStoryInsights,
                    onToggleInsights = { show, storyId -> viewModel.toggleStoryInsights(show, storyId) },
                    viewers = viewModel.storyViewers,
                    likes = viewModel.storyLikes,
                    replies = viewModel.storyReplies,
                    isLoadingInsights = viewModel.isInsightsLoading
                )
            }
    }

    // Removed the separate StoryInsightsBottomSheet call here as it's now inside StoryViewerModal

        selectedCommentsPost?.let { post ->
            CommentsBottomSheet(
                post = post,
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onDismiss = { selectedCommentsPost = null },
                onCommentAdded = {
                    post.backendId?.let { viewModel.handleCommentAdded(it) }
                }
            )
        }

        if (profileSwitchingEnabled && showProfileSwitcher) {
            ProfileSwitcherModal(
                isDarkMode = isDarkMode,
                onDismiss = { showProfileSwitcher = false },
                onSwitched = {
                    showProfileSwitcher = false
                },
                onAddProfile = {
                    showProfileSwitcher = false
                    showAddAccountAuth = true
                }
            )
        }

        if (profileSwitchingEnabled && showAddAccountAuth) {
            AddAccountAuthDialog(
                isDarkMode = isDarkMode,
                onDismiss = { showAddAccountAuth = false },
                onFinished = { showAddAccountAuth = false }
            )
        }

        if (isSessionTransitioning) {
            LoadingOverlay(fullScreen = true, message = "Switching account")
        }
    }
}

private fun sameReelIdentity(first: PostData, second: PostData): Boolean {
    val firstKey = first.backendId ?: first.id.toString()
    val secondKey = second.backendId ?: second.id.toString()
    return firstKey == secondKey
}

private fun buildReelsViewerList(feedReels: List<PostData>, seed: PostData): List<PostData> {
    val normalizedSeed = if (feedReels.any { sameReelIdentity(it, seed) }) {
        feedReels.first { sameReelIdentity(it, seed) }
    } else {
        seed
    }

    val merged = buildList {
        add(normalizedSeed)
        feedReels.forEach { reel ->
            if (!sameReelIdentity(reel, normalizedSeed)) add(reel)
        }
    }

    return merged.distinctBy { it.backendId ?: it.id.toString() }
}
