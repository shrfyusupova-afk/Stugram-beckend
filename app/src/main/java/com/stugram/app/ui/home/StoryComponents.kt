package com.stugram.app.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RectangleStoryItem(story: StoryProfile, accentBlue: Color, isDarkMode: Boolean, onClick: () -> Unit) {
    PremiumStoryTrayCard(
        label = story.name,
        backgroundModel = story.stories.firstOrNull()?.mediaUrl,
        avatarModel = story.avatar,
        accentBlue = accentBlue,
        isDarkMode = isDarkMode,
        isViewed = story.isSeen,
        showPlusIcon = false,
        onClick = onClick
    )
}

@Composable
fun PremiumStoryTrayCard(
    label: String,
    backgroundModel: Any?,
    avatarModel: Any?,
    accentBlue: Color,
    isDarkMode: Boolean,
    isViewed: Boolean,
    showPlusIcon: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "story_press_scale"
    )
    val borderColor = when {
        showPlusIcon -> accentBlue
        isViewed -> Color.Transparent
        else -> accentBlue
    }
    val context = LocalContext.current
    val imageRequest = remember(backgroundModel) {
        backgroundModel?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .build()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(94.dp)
    ) {
        Box(
            modifier = Modifier
                .width(94.dp)
                .height(156.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(shape)
                .border(2.dp, borderColor, shape)
                .background(if (isDarkMode) Color(0xFF111418) else Color(0xFFF2F4F8), shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(18.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2E3540),
                                    Color(0xFF15181E),
                                    Color(0xFF0D0F14)
                                ),
                                radius = 220f
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            )

            Box(
                modifier = Modifier.align(Alignment.Center)
            ) {
                if (showPlusIcon) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.92f))
                            .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = accentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.95f))
                    ) {
                        AsyncImage(
                            model = avatarModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = if (isDarkMode) Color.White else Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StoryViewerModal(
    storyProfiles: List<StoryProfile>,
    startProfileIndex: Int,
    onDismiss: () -> Unit,
    onLike: (String) -> Unit,
    onReply: (String, String) -> Unit,
    onShowInsights: (String) -> Unit,
    onMarkSeen: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onNavigateToChat: (String, String) -> Unit = { _, _ -> },
    // Added for real insights
    showInsightsSheet: Boolean = false,
    onToggleInsights: (Boolean, String?) -> Unit = { _, _ -> },
    viewers: List<StoryActivityUser> = emptyList(),
    likes: List<StoryActivityUser> = emptyList(),
    replies: List<StoryActivityUser> = emptyList(),
    isLoadingInsights: Boolean = false
) {
    val pagerState = rememberPagerState(initialPage = startProfileIndex) { storyProfiles.size }
    val scope = rememberCoroutineScope()
    
    // Track the current story within the profile
    var currentStoryIndex by remember(pagerState.currentPage) { mutableIntStateOf(0) }
    val currentProfile = storyProfiles.getOrNull(pagerState.currentPage)
    val currentStory = currentProfile?.stories?.getOrNull(currentStoryIndex)
    
    // Auto-advance logic
    var currentStoryProgress by remember(pagerState.currentPage, currentStoryIndex) { mutableFloatStateOf(0f) }
    val isPaused = remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, currentStoryIndex) {
        currentStory?.backendId?.let { 
            onMarkSeen(it)
            // Pre-load insights if it's our own story
            if (currentProfile?.isMine == true) {
                onToggleInsights(false, it) // false just to trigger load without showing yet, or handled by VM
            }
        }
        
        currentStoryProgress = 0f
        while (currentStoryProgress < 1f) {
            if (!isPaused.value && !showInsightsSheet) {
                delay(50)
                currentStoryProgress += 0.01f
            } else {
                delay(100)
            }
        }

        // Navigate to next story or next profile
        if (currentProfile != null && currentStoryIndex < currentProfile.stories.size - 1) {
            currentStoryIndex++
        } else if (pagerState.currentPage < storyProfiles.size - 1) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        } else {
            onDismiss()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !showInsightsSheet
        ) { index ->
            val profile = storyProfiles[index]
            val activeStoryIndex = if (index == pagerState.currentPage) currentStoryIndex else 0
            val storyItem = profile.stories.getOrNull(activeStoryIndex)

            StoryViewContent(
                story = profile,
                currentStoryIndex = activeStoryIndex,
                progress = if (index == pagerState.currentPage) currentStoryProgress else 0f,
                onDismiss = onDismiss,
                onNext = {
                    if (currentStoryIndex < profile.stories.size - 1) {
                        currentStoryIndex++
                    } else if (pagerState.currentPage < storyProfiles.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onDismiss()
                    }
                },
                onPrevious = {
                    if (currentStoryIndex > 0) {
                        currentStoryIndex--
                    } else if (pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                },
                onLike = { storyItem?.backendId?.let { onLike(it) } },
                onReply = { text -> storyItem?.backendId?.let { onReply(it, text) } },
                onShowInsights = { storyItem?.backendId?.let { onToggleInsights(true, it) } },
                onDelete = { storyItem?.backendId?.let { onDelete(it) } },
                onPressed = { isPaused.value = it }
            )
        }

        if (showInsightsSheet && currentStory != null) {
            StoryInsightsBottomSheet(
                viewers = viewers,
                likes = likes,
                replies = replies,
                likesCount = currentStory.likesCount,
                repliesCount = currentStory.repliesCount,
                isLoading = isLoadingInsights,
                isDarkMode = true,
                onDismiss = { onToggleInsights(false, null) },
                onUserClick = { user -> 
                    onNavigateToChat(user.conversationId ?: user.userId ?: "", user.name)
                }
            )
        }
    }
}

@Composable
fun StoryViewContent(
    story: StoryProfile,
    currentStoryIndex: Int,
    progress: Float,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLike: () -> Unit,
    onReply: (String) -> Unit,
    onShowInsights: () -> Unit,
    onDelete: () -> Unit = {},
    onPressed: (Boolean) -> Unit = {}
) {
    val currentStory = story.stories.getOrNull(currentStoryIndex)
    var replyText by remember { mutableStateOf("") }
    var localIsLiked by remember(currentStory?.backendId) { mutableStateOf(currentStory?.isLiked ?: false) }
    
    // Heart animation state
    var showBurstHeart by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (showBurstHeart) 1.5f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heart_scale",
        finishedListener = { if (it > 0) showBurstHeart = false }
    )

    var showMoreOptions by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = currentStory?.mediaUrl ?: story.avatar,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gesture handling for Story navigation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onPressed(true)
                        val startTime = System.currentTimeMillis()
                        val up = waitForUpOrCancellation()
                        onPressed(false)
                        val duration = System.currentTimeMillis() - startTime
                        
                        if (up != null && duration < 300) {
                            if (down.position.x < size.width / 3) {
                                onPrevious()
                            } else {
                                onNext()
                            }
                        }
                    }
                }
        )

        // Top Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            // Segmented Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                story.stories.forEachIndexed { index, _ ->
                    val stepProgress = when {
                        index < currentStoryIndex -> 1f
                        index == currentStoryIndex -> progress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { stepProgress },
                        modifier = Modifier.weight(1f).height(2.dp).clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(0.3f),
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = story.avatar,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, Color.White, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(story.name, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("Just now", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                
                if (story.isMine) {
                    Box {
                        IconButton(onClick = { showMoreOptions = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMoreOptions,
                            onDismissRequest = { showMoreOptions = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete Story", color = Color.Red) },
                                onClick = {
                                    showMoreOptions = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            )
                        }
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }

        // Bottom Actions
        if (story.isMine) {
            // Owner View: Insights
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .clickable { onShowInsights() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RemoveRedEye, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Seen by ${currentStory?.viewersCount ?: 0}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Guest View: Reply and Like
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                color = Color.Black.copy(0.3f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("Send a message...", color = Color.White.copy(0.7f), fontSize = 14.sp) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    IconButton(onClick = { 
                        if (!localIsLiked) {
                            showBurstHeart = true
                        }
                        localIsLiked = !localIsLiked
                        onLike() 
                    }) {
                        Icon(
                            if (localIsLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null,
                            tint = if (localIsLiked) Color.Red else Color.White
                        )
                    }
                    IconButton(onClick = {
                        if (replyText.isNotBlank()) {
                            onReply(replyText)
                            replyText = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color.White)
                    }
                }
            }
        }

        // Bursting Heart Animation Overlay
        if (heartScale > 0.01f) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.Red.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = heartScale
                            scaleY = heartScale
                            alpha = 1f - (heartScale - 1f).coerceIn(0f, 1f)
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryInsightsBottomSheet(
    viewers: List<StoryActivityUser>,
    likes: List<StoryActivityUser> = emptyList(),
    replies: List<StoryActivityUser> = emptyList(),
    likesCount: Int,
    repliesCount: Int,
    isLoading: Boolean = false,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onUserClick: (StoryActivityUser) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()
    val sheetBg = if (isDarkMode) Color(0xFF0F1014) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray.copy(0.5f)) }
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.7f)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = textColor,
                divider = { HorizontalDivider(color = Color.Gray.copy(0.2f)) }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${viewers.size}", fontWeight = FontWeight.Bold)
                        Text("Viewers", fontSize = 12.sp)
                    }
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$likesCount", fontWeight = FontWeight.Bold)
                        Text("Likes", fontSize = 12.sp)
                    }
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$repliesCount", fontWeight = FontWeight.Bold)
                        Text("Replies", fontSize = 12.sp)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF00A3FF)
                    )
                } else {
                    val currentList = when (selectedTab) {
                        0 -> viewers
                        1 -> likes
                        else -> replies
                    }

                    if (currentList.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                when(selectedTab) {
                                    0 -> Icons.Default.RemoveRedEye
                                    1 -> Icons.Default.FavoriteBorder
                                    else -> Icons.AutoMirrored.Rounded.Send
                                },
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                when(selectedTab) {
                                    0 -> "No viewers yet"
                                    1 -> "No likes yet"
                                    else -> "No replies yet"
                                },
                                color = Color.Gray
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            items(currentList) { user ->
                                ViewerItem(user, textColor, onClick = { onUserClick(user) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCommentsBottomSheet(
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    // Demo-only comments UI removed (no backend wiring). Keep signature so callers (if any)
    // can still compile, but do not render a clickable placeholder surface.
    onDismiss()
}

@Composable
fun CommentItem(index: Int, textColor: Color) {
    Row {
        AppAvatar(
            imageModel = null,
            name = "user_$index",
            username = "user_$index",
            modifier = Modifier.size(32.dp),
            isDarkMode = true,
            fontSize = 12.sp
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("user_$index", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
                Spacer(Modifier.width(8.dp))
                Text("2h", color = Color.Gray, fontSize = 11.sp)
            }
            Text(
                "Bu juda ajoyib post bo'libdi! Menga juda yoqdi. 🔥🔥🔥",
                fontSize = 13.sp,
                color = textColor.copy(0.9f)
            )
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Javob berish", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.FavoriteBorder, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("12", fontSize = 11.sp, color = Color.Gray)
            }
        }

    }
}

@Composable
fun ViewerItem(user: StoryActivityUser, textColor: Color, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = user.avatar,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .background(Color(0xFFE91E63), CircleShape)
                    .border(2.dp, Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(user.subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.MoreVert, null, tint = textColor.copy(0.7f))
            Icon(Icons.AutoMirrored.Rounded.Send, null, tint = textColor.copy(0.7f), modifier = Modifier.size(22.dp))
        }
    }
}
