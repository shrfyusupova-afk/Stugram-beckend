package com.stugram.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.repository.NotificationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabScreen(
    posts: List<PostData>,
    storyProfiles: List<StoryProfile>,
    recommendedProfiles: List<RecommendedProfile>,
    currentUserProfile: com.stugram.app.data.remote.model.ProfileModel? = null,
    currentUserFollowingCount: Int = 0,
    paddingValues: PaddingValues,
    accentBlue: Color,
    isDarkMode: Boolean,
    contentColor: Color,
    onThemeChange: (Boolean) -> Unit,
    onStoryClick: (Int) -> Unit,
    onCreateClick: () -> Unit,
    onCommentsClick: (PostData) -> Unit,
    onProfileClick: (String) -> Unit = {},
    onReelClick: (PostData) -> Unit = {},
    onToggleLike: (PostData) -> Unit,
    onToggleSave: (PostData) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    listState: LazyListState,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    errorMessage: String? = null,
    onRetry: () -> Unit = onRefresh,
    showNotifications: Boolean,
    onNotificationsToggle: (Boolean) -> Unit,
    unreadCount: Int = 0
) {
    val topBarHeight = 70.dp
    val density = LocalDensity.current
    // Status bar va header to'liq yashirilishi uchun balandlikni aniqroq hisoblaymiz
    val topBarHeightPx = with(density) { topBarHeight.toPx() + 150f } 
    val topBarOffsetHeightPx = remember { mutableStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = topBarOffsetHeightPx.value + delta
                topBarOffsetHeightPx.value = newOffset.coerceIn(-topBarHeightPx, 0f)
                return Offset.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = rememberPullToRefreshState(),
                    isRefreshing = isRefreshing,
                    color = accentBlue,
                    containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 70.dp,
                    bottom = paddingValues.calculateBottomPadding() + 80.dp
                )
            ) {
                errorMessage?.let { message ->
                    item(key = "feed_error") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = message,
                                color = Color.Red.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Retry",
                                color = accentBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onRetry() }
                            )
                        }
                    }
                }
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // "Your story" must always be visible first (UI-level affordance; no fake story records).
                        item(key = "my_story_affordance") {
                            val me = currentUserProfile
                            val myStoryIndex = storyProfiles.indexOfFirst { it.isMine }
                            val hasStory = myStoryIndex >= 0
                            val selfStory = storyProfiles.getOrNull(myStoryIndex)
                            PremiumStoryTrayCard(
                                label = "Your Story",
                                backgroundModel = selfStory?.stories?.firstOrNull()?.mediaUrl,
                                avatarModel = selfStory?.avatar ?: me?.avatar,
                                accentBlue = accentBlue,
                                isDarkMode = isDarkMode,
                                isViewed = selfStory?.isSeen == true,
                                showPlusIcon = !hasStory,
                                onClick = {
                                    if (hasStory) {
                                        onStoryClick(myStoryIndex)
                                    } else {
                                        // Opens the real creation flow; the user can choose "Story" there.
                                        onCreateClick()
                                    }
                                }
                            )
                        }

                        // Other users' stories (and my own story if it exists, as normal item for viewing).
                        itemsIndexed(
                            storyProfiles.filterNot { it.isMine },
                            key = { _, s -> s.id }
                        ) { _, story ->
                            val originalIndex = storyProfiles.indexOfFirst { it.id == story.id }
                            RectangleStoryItem(story, accentBlue, isDarkMode) {
                                if (originalIndex >= 0) onStoryClick(originalIndex)
                            }
                        }
                    }
                }
                item {
                    CreatePostButton(onCreateClick, accentBlue, isDarkMode)
                }

                // New user UX: if following == 0, always show structure (Suggested profiles + Discover posts).
                if (currentUserFollowingCount == 0) {
                    item(key = "suggested_profiles_header") {
                        SectionHeader(
                            title = "Suggested for you",
                            subtitle = if (recommendedProfiles.isEmpty()) "More users joining soon" else null,
                            isDarkMode = isDarkMode
                        )
                    }
                    item(key = "suggested_profiles_row") {
                        if (recommendedProfiles.isNotEmpty()) {
                            SuggestedProfilesRow(
                                profiles = recommendedProfiles,
                                accentBlue = accentBlue,
                                isDarkMode = isDarkMode
                            )
                        }
                    }

                    item(key = "discover_posts_header") {
                        SectionHeader(
                            title = "Discover posts",
                            subtitle = if (posts.isEmpty()) "Discover people" else null,
                            isDarkMode = isDarkMode
                        )
                    }
                }

                itemsIndexed(posts) { index, post ->
                    DashboardPostItem(
                        post = post,
                        accentBlue = accentBlue,
                        isDarkMode = isDarkMode,
                        onCommentsClick = onCommentsClick,
                        onReelClick = onReelClick,
                        onProfileClick = { username -> onProfileClick(username) },
                        onToggleLike = onToggleLike,
                        onToggleSave = onToggleSave
                    )
                    // Keep existing mid-feed recommendations for users who already have a non-empty feed.
                    if (currentUserFollowingCount != 0 && index == 1) RecommendedProfilesSlider(recommendedProfiles, accentBlue, isDarkMode)
                }

                // Never show a blank Home feed structure.
                if (posts.isEmpty() && currentUserFollowingCount != 0) {
                    item(key = "empty_feed_fallback") {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
                            Text(
                                "No posts yet",
                                color = if (isDarkMode) Color.White else Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Discover people",
                                color = accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (recommendedProfiles.isEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "More users joining soon",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            } else {
                                Spacer(Modifier.height(10.dp))
                                SuggestedProfilesRow(
                                    profiles = recommendedProfiles,
                                    accentBlue = accentBlue,
                                    isDarkMode = isDarkMode
                                )
                            }
                        }
                    }
                }

                if (isLoadingMore) {
                    item(key = "feed_load_more_progress") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = accentBlue, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(x = 0, y = topBarOffsetHeightPx.value.toInt()) }
                .fillMaxWidth()
                .zIndex(1f) // List ustida turishi uchun
        ) {
            var showQuickMenu by remember { mutableStateOf(false) }
            HomeHeaderInline(
                isDarkMode = isDarkMode,
                onThemeChange = onThemeChange,
                accentBlue = accentBlue,
                contentColor = contentColor,
                onNotificationsClick = { onNotificationsToggle(true) },
                unreadCount = unreadCount,
                onQuickMenuClick = { showQuickMenu = true }
            )

            if (showQuickMenu) {
                HomeQuickMenuHalfSheet(
                    isDarkMode = isDarkMode,
                    accentBlue = accentBlue,
                    onDismiss = { showQuickMenu = false }
                )
            }
        }

        AnimatedVisibility(
            visible = showNotifications,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.zIndex(2f)
        ) {
            NotificationsScreen(
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onBack = { onNotificationsToggle(false) }
            )
        }
    }
}

@Composable
fun HomeHeaderInline(
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    accentBlue: Color,
    contentColor: Color,
    onNotificationsClick: () -> Unit,
    unreadCount: Int = 0,
    onQuickMenuClick: () -> Unit = {}
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color(0xFFF2F2F2)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onQuickMenuClick) {
                        Icon(Icons.Default.Menu, null, tint = contentColor)
                    }
                    Text(
                        "STUGRAM",
                        color = accentBlue,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNotificationsClick) {
                        Box {
                            Icon(Icons.Default.NotificationsNone, null, tint = contentColor)
                            // Notification Badge
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Red, CircleShape)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-2).dp, y = 2.dp)
                                        .border(1.5.dp, if (isDarkMode) Color.Black else Color.White, CircleShape)
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    isDarkMode: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = if (isDarkMode) Color.White else Color.Black
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun SuggestedProfilesRow(
    profiles: List<RecommendedProfile>,
    accentBlue: Color,
    isDarkMode: Boolean
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(profiles, key = { it.backendId ?: it.username }) { profile ->
            Box {
                RecommendedProfileCard(profile = profile, accentBlue = accentBlue, isDarkMode = isDarkMode)
                Surface(
                    color = Color(0xFF0EA5E9),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        "NEW",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeQuickMenuHalfSheet(
    isDarkMode: Boolean,
    accentBlue: Color,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val bg = if (isDarkMode) Color(0xFF0F1014) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray.copy(0.5f)) }
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.55f).padding(horizontal = 16.dp)) {
            Text(
                "Quick menu",
                color = textColor,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            QuickMenuItem(
                icon = Icons.Default.Map,
                title = "Map",
                badge = "Coming soon",
                enabled = false,
                accentBlue = accentBlue,
                textColor = textColor,
                onClick = {}
            )
            QuickMenuItem(Icons.Default.Event, "Events", "Coming soon", false, accentBlue, textColor) {}
            QuickMenuItem(Icons.Default.NearMe, "Nearby users", "Beta", false, accentBlue, textColor) {}
            QuickMenuItem(Icons.Default.TrendingUp, "Trending topics", "Coming soon", false, accentBlue, textColor) {}
            QuickMenuItem(Icons.Default.Groups, "Communities", "Coming soon", false, accentBlue, textColor) {}
            QuickMenuItem(Icons.Default.Work, "Jobs", "Coming soon", false, accentBlue, textColor) {}

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun QuickMenuItem(
    icon: ImageVector,
    title: String,
    badge: String?,
    enabled: Boolean,
    accentBlue: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.55f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .graphicsLayer(alpha = alpha),
        shape = RoundedCornerShape(16.dp),
        color = textColor.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = accentBlue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (!badge.isNullOrBlank()) {
                Surface(color = accentBlue.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        badge,
                        color = accentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreatePostButton(onClick: () -> Unit, accentBlue: Color, isDarkMode: Boolean) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).height(50.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isDarkMode) Color.White.copy(0.08f) else Color.White.copy(0.7f),
        border = BorderStroke(1.dp, Color.White.copy(0.2f))
    ) {
        Box {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(0.1f), Color.Transparent))))
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddPhotoAlternate, null, tint = accentBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Nima yangiliklar?", color = if (isDarkMode) Color.Gray else Color.DarkGray, fontSize = 14.sp)
                }
                Icon(Icons.Default.AutoAwesome, null, tint = accentBlue.copy(0.5f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun DashboardPostItem(
    post: PostData,
    accentBlue: Color,
    isDarkMode: Boolean,
    onCommentsClick: (PostData) -> Unit,
    onReelClick: (PostData) -> Unit = {},
    onProfileClick: (String) -> Unit,
    onToggleLike: (PostData) -> Unit = {},
    onToggleSave: (PostData) -> Unit = {}
) {
    var showLikeHeart by remember { mutableStateOf(false) }
    var heartOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    val textColor = Color.White
    val iconColor = Color.White

    if (showOptionsSheet) {
        PostOptionsBottomSheet(
            isDarkMode = isDarkMode,
            isSaved = post.isSaved,
            onSaveClick = {
                onToggleSave(post)
                showOptionsSheet = false
            },
            onDismiss = { showOptionsSheet = false }
        )
    }

    if (showShareSheet) {
        PostShareBottomSheet(
            isDarkMode = isDarkMode,
            onDismiss = { showShareSheet = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .aspectRatio(1f / 1.25f),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Image
            Box(
                modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { offset ->
                        if (!post.isLiked) onToggleLike(post)
                        heartOffset = offset
                        showLikeHeart = true
                        scope.launch { delay(800); showLikeHeart = false }
                    }, onTap = {
                        if (post.isVideo) {
                            onReelClick(post)
                        }
                    })
                }
            ) {
                AppBanner(
                    imageModel = post.image,
                    title = post.user,
                    modifier = Modifier.fillMaxSize(),
                    isDarkMode = true // Rasm fonida doim oq matn yaxshi ko'rinishi uchun
                )
                
                // Bottom Gradient for readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                if (showLikeHeart) PopLikeAnimation(heartOffset)
            }

            // Header Overlay
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppAvatar(
                            imageModel = post.userAvatar,
                            name = post.user,
                            username = post.user,
                            modifier = Modifier.size(28.dp).clickable { onProfileClick(post.user) },
                            isDarkMode = true,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = post.user,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { onProfileClick(post.user) }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.size(40.dp),
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, Color.White.copy(0.2f))
                ) {
                    IconButton(onClick = { showOptionsSheet = true }) {
                        Icon(Icons.Default.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Content Overlay (Bottom left)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 20.dp, end = 80.dp)
            ) {
                // Stats Row with Blur-like background
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onToggleLike(post) }) {
                            Icon(
                                if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                null,
                                tint = if (post.isLiked) Color.Red else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(post.likes.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onCommentsClick(post) }) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(post.comments.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showShareSheet = true }) {
                            Icon(Icons.Rounded.Repeat, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("229", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) // Rasmda ko'rsatilgan statik qiymat
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Caption
                Text(
                    text = post.caption,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                
                if (post.caption.contains("#")) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = post.caption.split(" ").filter { it.startsWith("#") }.joinToString(" "),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostOptionsBottomSheet(isDarkMode: Boolean, isSaved: Boolean, onSaveClick: () -> Unit, onDismiss: () -> Unit) {
    var showReportOptions by remember { mutableStateOf(false) }
    val textColor = if (isDarkMode) Color.White else Color.Black
    val containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = textColor.copy(0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (!showReportOptions) {
                // Top row for Save
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                   Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSaveClick() }) {
                       Surface(
                           modifier = Modifier.size(48.dp),
                           color = if (isDarkMode) Color(0xFF262626) else Color(0xFFF0F0F0),
                           shape = CircleShape
                       ) {
                           Box(contentAlignment = Alignment.Center) {
                               Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, null, tint = textColor)
                           }
                       }
                       Text(if (isSaved) "Saqlangan" else "Saqlash", color = textColor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                   }
                }

                HorizontalDivider(color = textColor.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))

                OptionItem(Icons.Outlined.PersonAdd, "Obuna bo'lish", textColor) { onDismiss() }
                OptionItem(Icons.Outlined.VisibilityOff, "Yashirish", textColor) { onDismiss() }
                OptionItem(Icons.Outlined.StarOutline, "Qiziqarli", textColor) { onDismiss() }
                OptionItem(Icons.Outlined.SentimentDissatisfied, "Qiziq emas", textColor) { onDismiss() }
                OptionItem(Icons.Outlined.Flag, "Shikoyat qilish", Color.Red) { showReportOptions = true }
            } else {
                Text(
                    "Nima uchun ushbu post haqida xabar bermoqchisiz?",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )
                val reasons = listOf("Spam", "Yalang'ochlik yoki jinsiy faoliyat", "Nafrat tili yoki ramzlar", "Zo'ravonlik yoki xavfli tashkilotlar", "Haqorat yoki tazyiq", "Intellektual mulkni buzish", "O'z joniga qasd qilish yoki o'ziga zarar yetkazish", "Ovqatlanish buzilishi", "Firibgarlik", "Yolg'on ma'lumot")
                reasons.forEach { reason ->
                    OptionItem(null, reason, textColor) { onDismiss() }
                }
            }
        }
    }
}

@Composable
fun OptionItem(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Text(text, color = color, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostShareBottomSheet(isDarkMode: Boolean, onDismiss: () -> Unit) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = textColor.copy(0.3f)) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Search Input
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Foydalanuvchilarni qidirish...", color = textColor.copy(0.5f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = if (isDarkMode) Color(0xFF262626) else Color(0xFFF5F5F5),
                    unfocusedContainerColor = if (isDarkMode) Color(0xFF262626) else Color(0xFFF5F5F5),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = textColor.copy(0.6f)) }
            )

            // User List (Horizontal scrollable)
            LazyRow(
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(10) { i ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AppAvatar(
                                imageModel = null,
                                name = "User $i",
                                username = "user_$i",
                                modifier = Modifier.size(64.dp),
                                isDarkMode = isDarkMode,
                                fontSize = 18.sp
                            )
                        }
                        Text("User $i", color = textColor, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            HorizontalDivider(color = textColor.copy(0.1f))

            // Fixed Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ShareActionItem(Icons.Outlined.History, "Storyga qo'shish", textColor)
                ShareActionItem(Icons.Outlined.Share, "Ulashish", textColor)
                ShareActionItem(Icons.Outlined.ContentCopy, "Havolani nusxalash", textColor)
                ShareActionItem(Icons.AutoMirrored.Outlined.Send, "Yuborish", textColor)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ShareActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(48.dp),
            color = color.copy(alpha = 0.1f),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
        }
        Text(text, color = color, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun PostStatItem(icon: ImageVector, count: String, isDarkMode: Boolean, tint: Color? = null, onClick: () -> Unit = {}) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(icon, null, tint = tint ?: textColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(count, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun RecommendedProfilesSlider(profiles: List<RecommendedProfile>, accentBlue: Color, isDarkMode: Boolean) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text("Tavsiya etilganlar", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(profiles) { profile -> RecommendedProfileCard(profile, accentBlue, isDarkMode) }
        }
    }
}

@Composable
fun RecommendedProfileCard(profile: RecommendedProfile, accentBlue: Color, isDarkMode: Boolean) {
    var followStatus by remember(profile.backendId) {
        mutableStateOf(profile.followStatus.lowercase().ifBlank {
            if (profile.isFollowed) "following" else "not_following"
        })
    }
    val isFollowed = followStatus == "following"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val followRepository = remember { com.stugram.app.data.repository.FollowRepository() }
    val cardBg = if (isDarkMode) Color(0xFF1A1A1A) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black

    Card(
        modifier = Modifier
            .width(180.dp)
            .height(260.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                AppBanner(
                    imageModel = profile.image,
                    title = profile.name,
                    modifier = Modifier.fillMaxSize(),
                    isDarkMode = isDarkMode,
                    shape = RoundedCornerShape(18.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = profile.name,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${profile.username}",
                    color = accentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                val buttonColor by animateColorAsState(
                    if (isFollowed) Color.White.copy(alpha = 0.1f) else accentBlue,
                    animationSpec = tween(300),
                    label = "buttonColor"
                )
                
                Surface(
                    onClick = {
                        val userId = profile.backendId ?: return@Surface
                        val previous = followStatus
                        val willFollow = previous == "not_following"
                        followStatus = if (willFollow) "following" else "not_following"
                        com.stugram.app.core.social.FollowEvents.emit(
                            com.stugram.app.core.social.FollowEvent(
                                userId = userId,
                                username = profile.username,
                                isFollowing = followStatus == "following",
                                followStatus = followStatus
                            )
                        )
                        scope.launch {
                            if (willFollow) {
                                val followResponse = runCatching { followRepository.followUser(userId) }.getOrNull()
                                if (followResponse?.isSuccessful == true) {
                                    val responseStatus = followResponse.body()?.data?.status?.lowercase()?.trim()
                                    followStatus = when (responseStatus) {
                                        "following", "requested", "self", "not_following" -> responseStatus
                                        else -> "following"
                                    }
                                    com.stugram.app.core.social.FollowEvents.emit(
                                        com.stugram.app.core.social.FollowEvent(
                                            userId = userId,
                                            username = profile.username,
                                            isFollowing = followStatus == "following",
                                            followStatus = followStatus
                                        )
                                    )
                                } else {
                                    followStatus = previous
                                    com.stugram.app.core.social.FollowEvents.emit(
                                        com.stugram.app.core.social.FollowEvent(
                                            userId = userId,
                                            username = profile.username,
                                            isFollowing = previous == "following",
                                            followStatus = previous
                                        )
                                    )
                                }
                            } else {
                                val unfollowResponse = runCatching { followRepository.unfollowUser(userId) }.getOrNull()
                                if (unfollowResponse?.isSuccessful == true) {
                                    followStatus = "not_following"
                                    com.stugram.app.core.social.FollowEvents.emit(
                                        com.stugram.app.core.social.FollowEvent(
                                            userId = userId,
                                            username = profile.username,
                                            isFollowing = false,
                                            followStatus = "not_following"
                                        )
                                    )
                                } else {
                                    followStatus = previous
                                    com.stugram.app.core.social.FollowEvents.emit(
                                        com.stugram.app.core.social.FollowEvent(
                                            userId = userId,
                                            username = profile.username,
                                            isFollowing = previous == "following",
                                            followStatus = previous
                                        )
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = buttonColor,
                    border = if (isFollowed) BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)) else null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (followStatus) {
                                "following" -> "Obuna bo'lingan"
                                "requested" -> "So'rov yuborildi"
                                "self" -> "Siz"
                                else -> "Obuna bo'lish"
                            },
                            color = when (followStatus) {
                                "following" -> accentBlue
                                "requested" -> Color.White
                                "self" -> Color.White
                                else -> Color.White
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    isDarkMode: Boolean,
    accentBlue: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val notificationRepository = remember {
        RetrofitClient.init(context.applicationContext)
        NotificationRepository()
    }
    val scope = rememberCoroutineScope()
    val backgroundColor = if (isDarkMode) Color(0xFF0A0A0A) else Color(0xFFF8F9FA)
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val surfaceColor = if (isDarkMode) Color(0xFF161616) else Color.White
    
    val notifications by produceState(initialValue = emptyList<NotificationData>()) {
        value = runCatching {
            notificationRepository.getNotifications(page = 1, limit = 30).body()?.data.orEmpty().mapIndexed { index, item ->
                NotificationData(
                    id = index + 1,
                    backendId = item.id,
                    type = item.type,
                    user = item.actor?.fullName?.takeIf { it.isNotBlank() } ?: item.actor?.username ?: "System",
                    username = item.actor?.username,
                    userImage = item.actor?.avatar,
                    content = item.message,
                    time = item.createdAt?.substringBefore("T") ?: "Now",
                    isRead = item.isRead
                )
            }
        }.getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // High-end Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = contentColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Bildirishnomalar",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = contentColor,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    runCatching { notificationRepository.markAllAsRead() }
                }
            }) {
                Text("Mark all read", color = accentBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Tabs or Filters (Simplified)
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotificationChip("All", true, accentBlue, isDarkMode)
        }

        // Content List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "Recent",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = contentColor.copy(0.5f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            
            items(notifications) { notification ->
                Surface(
                    color = if (notification.isRead) Color.Transparent else surfaceColor,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        NotificationItem(notification, accentBlue, isDarkMode)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationChip(text: String, isSelected: Boolean, accentBlue: Color, isDarkMode: Boolean) {
    Surface(
        color = if (isSelected) accentBlue else (if (isDarkMode) Color.White.copy(0.05f) else Color.Black.copy(0.05f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isSelected) Color.White else (if (isDarkMode) Color.White else Color.Black),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NotificationItem(notification: NotificationData, accentBlue: Color, isDarkMode: Boolean) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppAvatar(
            imageModel = notification.userImage,
            name = notification.user,
            username = notification.user,
            modifier = Modifier.size(42.dp).border(1.dp, accentBlue.copy(0.3f), CircleShape),
            isDarkMode = isDarkMode,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(notification.user) }
                    append(" ")
                    append(notification.content)
                },
                fontSize = 13.sp,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(notification.time, fontSize = 11.sp, color = contentColor.copy(0.5f))
        }
        
        if (notification.type == "follow") {
            Surface(
                color = accentBlue,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(28.dp).clickable { }
            ) {
                Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("Accept", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class NotificationData(
    val id: Int,
    val backendId: String? = null,
    val type: String,
    val user: String,
    val username: String? = null,
    val userImage: String?,
    val content: String,
    val time: String,
    val isRead: Boolean = false
)

@Composable
fun GlassSlidingNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onProfileLongPress: () -> Unit = {},
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        TabItem("Asosiy", Icons.Rounded.Home),
        TabItem("Qidiruv", Icons.Rounded.Search),
        TabItem("Videolar", Icons.Rounded.Movie),
        TabItem("Xabarlar", Icons.Rounded.ChatBubble),
        TabItem("Profil", Icons.Rounded.Person)
    )

    val backgroundColor = if (isDarkMode) Color(0xFF121212).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val accentBlue = Color(0xFF00A3FF)

    Surface(
        modifier = modifier
            .width(340.dp)
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = backgroundColor,
        shadowElevation = 12.dp,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                        )
                    )
            )
            val tabWidth = 340.dp / items.size
            val offset by animateDpAsState(
                targetValue = tabWidth * selectedTab,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "indicatorOffset"
            )

            // Full Blue Sliding Indicator with Glossy effect
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accentBlue, RoundedCornerShape(24.dp))
                        .shadow(4.dp, RoundedCornerShape(24.dp))
                ) {
                    // Glossy shine
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)
                                )
                            )
                    )
                }
            }

            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, item ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(index) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (index == 4) onProfileLongPress()
                                    },
                                    onTap = { onTabSelected(index) }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val color by animateColorAsState(
                            if (isSelected) Color.White else contentColor.copy(0.6f),
                            label = "iconColor"
                        )
                        val scale by animateFloatAsState(
                            if (isSelected) 1.1f else 1f,
                            animationSpec = tween(durationMillis = 200),
                            label = "iconScale"
                        )
                        Icon(
                            item.icon,
                            null,
                            tint = color,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                        )
                    }
                }
            }
        }
    }
}
