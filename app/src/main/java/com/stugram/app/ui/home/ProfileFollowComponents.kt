package com.stugram.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.PersonRemoveAlt1
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.repository.ChatRepository
import com.stugram.app.data.repository.FollowRepository
import com.stugram.app.data.repository.SettingsRepository
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    isDarkMode: Boolean,
    initialType: String,
    username: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (conversationId: String, username: String) -> Unit
) {
    val accentBlue = Color(0xFF00A3FF)
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glassBg = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
    val pagerState = rememberPagerState(initialPage = if (initialType == "followers") 0 else 1) { 2 }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val followRepository = remember { FollowRepository() }
    val settingsRepository = remember { SettingsRepository() }
    val chatRepository = remember {
        RetrofitClient.init(context.applicationContext)
        ChatRepository()
    }

    var followers by remember(username) { mutableStateOf<List<ProfileSummary>>(emptyList()) }
    var following by remember(username) { mutableStateOf<List<ProfileSummary>>(emptyList()) }
    var isLoading by remember(username) { mutableStateOf(false) }
    var error by remember(username) { mutableStateOf<String?>(null) }

    suspend fun load(type: String) {
        isLoading = true
        error = null
        try {
            val response = if (type == "followers") {
                followRepository.getFollowers(username, page = 1, limit = 50)
            } else {
                followRepository.getFollowing(username, page = 1, limit = 50)
            }
            if (response.isSuccessful) {
                val items = response.body()?.data ?: emptyList()
                if (type == "followers") followers = items else following = items
            } else {
                error = response.message().ifBlank { "Failed to load $type" }
            }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Failed to load $type"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(username) {
        load(if (initialType == "followers") "followers" else "following")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title & Switcher
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(glassBg)
                    .border(0.5.dp, Color.Gray.copy(0.2f), RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                val tabWidth = (LocalConfiguration.current.screenWidthDp.dp - 100.dp) / 2
                val indicatorOffset by animateDpAsState(
                    targetValue = if (pagerState.currentPage == 0) 0.dp else tabWidth,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "tab_indicator"
                )

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(accentBlue)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { scope.launch { pagerState.animateScrollToPage(0) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Followers",
                            color = if (pagerState.currentPage == 0) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { scope.launch { pagerState.animateScrollToPage(1) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Following",
                            color = if (pagerState.currentPage == 1) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Back Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(glassBg)
                    .border(0.5.dp, Color.Gray.copy(0.2f), RoundedCornerShape(16.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, tint = contentColor, modifier = Modifier.size(24.dp))
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val type = if (page == 0) "followers" else "following"
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { scope.launch { load(type) } },
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = rememberPullToRefreshState(),
                        isRefreshing = isLoading,
                        color = accentBlue,
                        containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                FollowUserList(
                    isDarkMode = isDarkMode,
                    isFollowers = page == 0,
                    users = if (page == 0) followers else following,
                    isLoading = isLoading,
                    errorMessage = error,
                    onRetry = { scope.launch { load(type) } },
                    onOpenProfile = onOpenProfile,
                    onMessage = { user ->
                        scope.launch {
                            val userId = user.id
                            val response = runCatching { chatRepository.createConversation(userId) }.getOrNull()
                            val conversation = response?.body()?.data
                            if (response?.isSuccessful == true && conversation != null) {
                                onOpenChat(conversation.id, user.username)
                            }
                        }
                    },
                    onRemoveFollower = { user ->
                        if (page != 0) return@FollowUserList
                        val userId = user.id
                        scope.launch {
                            val res = runCatching { followRepository.removeFollower(userId) }.getOrNull()
                            if (res?.isSuccessful == true) {
                                followers = followers.filterNot { it.id == userId }
                            }
                        }
                    },
                    onUnfollow = { user ->
                        if (page != 1) return@FollowUserList
                        val userId = user.id
                        scope.launch {
                            val res = runCatching { followRepository.unfollowUser(userId) }.getOrNull()
                            if (res?.isSuccessful == true) {
                                following = following.filterNot { it.id == userId }
                            }
                        }
                    },
                    onBlock = { user ->
                        val userId = user.id
                        scope.launch {
                            val res = runCatching { settingsRepository.blockUser(userId) }.getOrNull()
                            if (res?.isSuccessful == true) {
                                followers = followers.filterNot { it.id == userId }
                                following = following.filterNot { it.id == userId }
                            }
                        }
                    }
                )
            }
        }
    }

    BackHandler { onBack() }
}

@Composable
fun FollowUserList(
    isDarkMode: Boolean,
    isFollowers: Boolean,
    users: List<ProfileSummary>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onMessage: (ProfileSummary) -> Unit,
    onRemoveFollower: (ProfileSummary) -> Unit,
    onUnfollow: (ProfileSummary) -> Unit,
    onBlock: (ProfileSummary) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!errorMessage.isNullOrBlank()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage, color = Color.Red.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text("Retry", color = Color(0xFF00A3FF), fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onRetry() })
                }
            }
        } else if (isLoading && users.isEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Color(0xFF00A3FF), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        } else if (users.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(if (isFollowers) "No followers yet" else "Not following anyone yet", color = Color.Gray)
                }
            }
        } else {
            items(users, key = { it.id }) { user ->
                FollowUserItem(
                    user = user,
                    isDarkMode = isDarkMode,
                    isFollowers = isFollowers,
                    onOpenProfile = { onOpenProfile(user.username) },
                    onMessage = { onMessage(user) },
                    onRemoveFollower = { onRemoveFollower(user) },
                    onUnfollow = { onUnfollow(user) },
                    onBlock = { onBlock(user) }
                )
            }
        }
    }
}

@Composable
fun FollowUserItem(
    user: ProfileSummary,
    isDarkMode: Boolean,
    isFollowers: Boolean,
    onOpenProfile: () -> Unit,
    onMessage: () -> Unit,
    onRemoveFollower: () -> Unit,
    onUnfollow: () -> Unit,
    onBlock: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glassBg = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
    val accentBlue = Color(0xFF00A3FF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(glassBg)
            .border(0.5.dp, Color.Gray.copy(0.15f), RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppAvatar(
            imageModel = user.avatar,
            name = user.fullName,
            username = user.username,
            modifier = Modifier
                .size(52.dp)
                .border(1.dp, accentBlue.copy(0.3f), CircleShape),
            isDarkMode = isDarkMode,
            fontSize = 18.sp
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f).clickable { onOpenProfile() }) {
            Text(
                text = user.fullName,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${user.username}",
                color = Color.Gray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onMessage,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = if (isDarkMode) 0.06f else 0.04f))
            ) {
                Icon(Icons.Rounded.ChatBubbleOutline, null, tint = accentBlue)
            }

            if (isFollowers) {
                IconButton(
                    onClick = onRemoveFollower,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (isDarkMode) 0.06f else 0.04f))
                ) {
                    Icon(Icons.Rounded.PersonRemoveAlt1, null, tint = Color.Red.copy(alpha = 0.8f))
                }
            } else {
                IconButton(
                    onClick = onUnfollow,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (isDarkMode) 0.06f else 0.04f))
                ) {
                    Icon(Icons.Rounded.PersonOff, null, tint = Color.Red.copy(alpha = 0.75f))
                }
            }

            IconButton(
                onClick = onBlock,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = if (isDarkMode) 0.06f else 0.04f))
            ) {
                Icon(Icons.Rounded.Block, null, tint = Color.Red.copy(alpha = 0.6f))
            }
        }
    }
}
