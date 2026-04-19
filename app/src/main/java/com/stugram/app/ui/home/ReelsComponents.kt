package com.stugram.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    reels: List<PostData>,
    accentBlue: Color, 
    isDarkMode: Boolean, 
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    errorMessage: String? = null,
    onToggleLike: (PostData) -> Unit = {},
    onToggleSave: (PostData) -> Unit = {},
    onCommentAdded: (String) -> Unit = {},
    onToggleFollow: (PostData, Boolean, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onProfileClick: (String) -> Unit,
    initialPage: Int = 0,
    contentBottomPadding: Dp = 100.dp,
    onClose: (() -> Unit)? = null
) {
    val safeInitialPage = initialPage.coerceIn(0, (reels.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { reels.size.coerceAtLeast(1) }
    )
    var showSettingsModal by remember { mutableStateOf(false) }
    var selectedCommentsPost by remember { mutableStateOf<PostData?>(null) }
    var isAutoScroll by remember { mutableStateOf(false) }

    LaunchedEffect(isAutoScroll, pagerState.currentPage) {
        if (isAutoScroll) {
            while (true) {
                delay(5000)
                if (pagerState.currentPage < reels.size - 1) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                } else {
                    pagerState.animateScrollToPage(0)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = rememberPullToRefreshState(),
                    isRefreshing = isRefreshing,
                    containerColor = Color(0xFF1A1A1A),
                    color = accentBlue,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            if (!errorMessage.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Couldn't load reels", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(errorMessage, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("Retry", color = accentBlue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onRefresh() })
                    }
                }
            } else if (reels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Movie, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No reels yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Published video posts will appear here.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { page ->
                    ReelItem(
                        reel = reels[page],
                        accentBlue = accentBlue,
                        onMoreClick = { showSettingsModal = true },
                        onCommentsClick = { selectedCommentsPost = it },
                        onToggleLike = onToggleLike,
                        onToggleSave = onToggleSave,
                        onToggleFollow = onToggleFollow,
                        onProfileClick = onProfileClick,
                        bottomPadding = contentBottomPadding
                    )
                }
            }
        }

        if (onClose != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 12.dp),
                color = Color.Black.copy(alpha = 0.35f),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f))
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close reels", tint = Color.White)
                }
            }
        }
    }

    if (showSettingsModal) {
        ReelsSettingsModal(
            onDismiss = { showSettingsModal = false },
            accentBlue = accentBlue,
            isAutoScroll = isAutoScroll,
            onToggleAutoScroll = { isAutoScroll = !isAutoScroll }
        )
    }

    selectedCommentsPost?.let { post ->
        CommentsBottomSheet(
            post = post,
            isDarkMode = isDarkMode,
            accentBlue = accentBlue,
            onDismiss = { selectedCommentsPost = null },
            onCommentAdded = {
                post.backendId?.let(onCommentAdded)
            }
        )
    }
}

@Composable
fun ReelItem(
    reel: PostData,
    accentBlue: Color,
    onMoreClick: () -> Unit,
    onCommentsClick: (PostData) -> Unit,
    onToggleLike: (PostData) -> Unit,
    onToggleSave: (PostData) -> Unit,
    onToggleFollow: (PostData, Boolean, (Boolean) -> Unit) -> Unit,
    onProfileClick: (String) -> Unit,
    bottomPadding: Dp = 100.dp
) {
    var isCaptionExpanded by remember { mutableStateOf(false) }
    var showLikeHeart by remember { mutableStateOf(false) }
    var heartOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var isFollowing by remember(reel.authorId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content (video playback for reels, image fallback otherwise)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (!reel.isLiked) onToggleLike(reel)
                            heartOffset = offset
                            showLikeHeart = true
                            scope.launch { delay(800); showLikeHeart = false }
                        }
                    )
                }
        ) {
            if (reel.isVideo && !reel.image.isNullOrBlank()) {
                ReelVideoPlayer(
                    url = reel.image,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AppBanner(
                    imageModel = reel.image,
                    title = reel.user,
                    modifier = Modifier.fillMaxSize(),
                    isDarkMode = true
                )
            }
        }

        // Gradient overlays for better readability
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color.Black.copy(0.3f), Color.Transparent, Color.Black.copy(0.6f))
            )
        ))

        // Right-side action buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ReelInteractionButton(
                icon = if (reel.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = reel.likes.toString(),
                tint = if (reel.isLiked) Color.Red else Color.White,
                onClick = { onToggleLike(reel) }
            )
            ReelInteractionButton(Icons.Default.ChatBubbleOutline, reel.comments.toString(), onClick = { onCommentsClick(reel) })
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // Bottom metadata (user and caption)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = bottomPadding, end = 80.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppAvatar(
                        imageModel = reel.userAvatar,
                        name = reel.user,
                        username = reel.user,
                        modifier = Modifier
                            .size(32.dp)
                            .border(1.dp, Color.White.copy(0.5f), CircleShape)
                            .clickable { onProfileClick(reel.user) },
                        isDarkMode = true,
                        borderColor = Color.Transparent,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = reel.user,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { onProfileClick(reel.user) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isFollowing) "Following" else "Follow",
                        color = accentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clickable {
                                onToggleFollow(reel, isFollowing) { newValue ->
                                    isFollowing = newValue
                                }
                            }
                            .padding(horizontal = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = reel.caption,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = if (isCaptionExpanded) 10 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .animateContentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isCaptionExpanded = !isCaptionExpanded }
            )
        }

        if (showLikeHeart) {
            PopLikeAnimation(heartOffset)
        }
    }
}

@Composable
private fun ReelVideoPlayer(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasError by remember(url) { mutableStateOf(false) }
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            runCatching { exoPlayer.release() }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            }
        )
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.BrokenImage, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Video unavailable", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Pull to refresh or try again later.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ReelInteractionButton(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsSettingsModal(onDismiss: () -> Unit, accentBlue: Color, isAutoScroll: Boolean, onToggleAutoScroll: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReelSettingMainItem(if (isAutoScroll) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, if (isAutoScroll) "To'xtatish" else "Avto-aylantirish", Modifier.weight(1f), onClick = { onToggleAutoScroll(); onDismiss() })
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ReelSettingMainItem(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.08f))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ReelSettingListItem(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
