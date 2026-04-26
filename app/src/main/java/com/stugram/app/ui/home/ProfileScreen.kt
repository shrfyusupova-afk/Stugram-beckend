package com.stugram.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.ProfileQuickSummaryModel
import com.stugram.app.data.repository.ProfileRepository
import kotlinx.coroutines.launch
import com.stugram.app.core.social.FollowEvent
import com.stugram.app.core.social.FollowEvents
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isDarkMode: Boolean, 
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    isMyProfile: Boolean = true, 
    profileId: Int? = null,
    profileUsername: String? = null,
    onBack: (() -> Unit)? = null,
    onSettingsToggle: (Boolean) -> Unit = {},
    onNavigateToChat: ((conversationId: String, userName: String) -> Unit)? = null,
    onNavigateToProfile: ((username: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context.applicationContext) }
    val profileRepository = remember { ProfileRepository() }
    val currentUser by tokenManager.currentUser.collectAsState(initial = null)
    val activeUsername = if (isMyProfile) currentUser?.username else profileUsername
    var profileData by remember(currentUser, activeUsername, isMyProfile) {
        mutableStateOf<ProfileModel?>(if (isMyProfile) currentUser else null)
    }
    LaunchedEffect(currentUser, activeUsername, isMyProfile) {
        profileData = if (isMyProfile) currentUser else null
        if (isMyProfile) {
            val response = runCatching { profileRepository.getCurrentProfile() }.getOrNull()
            if (response?.isSuccessful == true) {
                response.body()?.data?.let { fresh ->
                    profileData = fresh
                    tokenManager.updateCurrentUser(fresh)
                }
            }
        } else if (!activeUsername.isNullOrBlank()) {
            profileData = runCatching {
                profileRepository.getProfile(activeUsername).body()?.data
            }.getOrNull() ?: profileData
        }
    }
    var profileSummary by remember(activeUsername, isMyProfile) { mutableStateOf<ProfileQuickSummaryModel?>(null) }
    LaunchedEffect(activeUsername, isMyProfile) {
        profileSummary = null
        if (!isMyProfile && !activeUsername.isNullOrBlank()) {
            profileSummary = runCatching {
                profileRepository.getProfileSummary(activeUsername).body()?.data
            }.getOrNull()
        }
    }

    val backgroundColor = if (isDarkMode) Color(0xFF050505) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val accentBlue = Color(0xFF00A3FF)
    
    var currentView by remember { mutableStateOf("profile") } // "profile", "settings", "edit_profile", "post_detail", "follow_list"
    var followListType by remember { mutableStateOf("followers") } // "followers" or "following"
    var selectedProfileTab by remember { mutableIntStateOf(0) }
    var selectedPostIndex by remember { mutableIntStateOf(0) }
    var profilePostDetailList by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var profileReelsViewerVisible by remember { mutableStateOf(false) }
    var profileReelsViewerList by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var profileReelsInitialPage by remember { mutableIntStateOf(0) }
    
    // Settings holati o'zgarganda xabar beramiz
    LaunchedEffect(currentView) {
        onSettingsToggle(currentView == "settings" || currentView == "post_detail" || currentView == "follow_list")
    }
    
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val bannerHeight = screenWidth * 578f / 1080f
    
    val avatarSize = 116.dp
    val avatarOverlap = avatarSize * 0.35f

    Crossfade(targetState = currentView, label = "profile_view_transition") { view ->
        when (view) {
            "follow_list" -> {
                FollowListScreen(
                    isDarkMode = isDarkMode,
                    initialType = followListType,
                    username = activeUsername ?: "",
                    onBack = { currentView = "profile" },
                    onOpenProfile = { username ->
                        currentView = "profile"
                        onNavigateToProfile?.invoke(username)
                    },
                    onOpenChat = { conversationId, userName ->
                        currentView = "profile"
                        onNavigateToChat?.invoke(conversationId, userName)
                    }
                )
            }
            "settings" -> {
                SettingsScreen(
                    isDarkMode = isDarkMode, 
                    onBack = { currentView = "profile" },
                    onThemeChange = onThemeChange
                )
            }
            "edit_profile" -> {
                EditProfileScreen(
                    isDarkMode = isDarkMode, 
                    onBack = { currentView = "profile" },
                    onSave = {
                        onRefresh()
                        currentView = "profile" 
                    }
                )
            }
            "post_detail" -> {
                ProfilePostDetailFeed(
                    isDarkMode = isDarkMode,
                    posts = profilePostDetailList,
                    initialIndex = selectedPostIndex,
                    currentUserId = currentUser?.id,
                    onBack = { currentView = "profile" }
                )
            }
            else -> {
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
                    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
                        // Banner Section
                        Box(modifier = Modifier.fillMaxWidth().height(bannerHeight)) {
                            AppBanner(
                                imageModel = profileData?.banner,
                                title = profileData?.fullName,
                                modifier = Modifier.fillMaxSize(),
                                isDarkMode = isDarkMode
                            )

                            if (isMyProfile) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 12.dp, end = 16.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(0.6f))
                                        .clickable { currentView = "edit_profile" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item { Spacer(modifier = Modifier.height(bannerHeight - avatarOverlap)) }

                            item {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val profileSurface = if (isDarkMode) Color.Black else backgroundColor
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                                            .background(profileSurface)
                                        .padding(top = 21.dp, bottom = 16.dp)
                                    ) {
                                        ProfileHeaderRefinedDesign(
                                            isDarkMode = isDarkMode, 
                                            isMyProfile = isMyProfile,
                                            profile = profileData,
                                            summary = profileSummary,
                                            onFollowClick = { type ->
                                                followListType = type
                                                currentView = "follow_list"
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(11.dp))
                                        ProfileButtonsFinalAction(
                                            isDarkMode = isDarkMode, 
                                            isMyProfile = isMyProfile, 
                                            profile = profileData,
                                            accentBlue = accentBlue,
                                            onEditClick = { currentView = "edit_profile" },
                                            onFollowStateChanged = {
                                                onRefresh()
                                            },
                                            onMessageClick = { conversationId, username ->
                                                onNavigateToChat?.invoke(conversationId, username)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        FavoriteMomentsRectSection(isDarkMode = isDarkMode)
                                    }

                                    // Profile Picture
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 20.dp)
                                            .offset(y = (-avatarOverlap))
                                            .size(avatarSize)
                                            .background(profileSurface, CircleShape)
                                            .border(3.dp, accentBlue.copy(alpha = 0.95f), CircleShape)
                                            .padding(3.dp)
                                            .clip(CircleShape)
                                            .background(if (isDarkMode) Color(0xFF262626) else Color(0xFFF0F0F0))
                                            .clickable(enabled = isMyProfile) { currentView = "edit_profile" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppAvatar(
                                            imageModel = profileData?.avatar,
                                            name = profileData?.fullName,
                                            username = profileData?.username,
                                            modifier = Modifier.fillMaxSize(),
                                            isDarkMode = isDarkMode,
                                            fontSize = 30.sp
                                        )
                                    }
                                }
                            }

                            item {
                                ProfileTabSelectorOptimized(
                                    selectedTab = selectedProfileTab,
                                    onTabSelected = { selectedProfileTab = it },
                                    isDarkMode = isDarkMode
                                )
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(390.dp)
                                ) {
                                    when (selectedProfileTab) {
                                        0 -> ProfilePostsGrid(
                                            isDarkMode = isDarkMode,
                                            onPostClick = { posts, index ->
                                                profilePostDetailList = posts
                                                selectedPostIndex = index
                                                currentView = "post_detail"
                                            }
                                        )
                                        1 -> ProfileReelsGrid(
                                            isDarkMode = isDarkMode,
                                            username = activeUsername,
                                            onReelClick = { reels, index ->
                                                profileReelsViewerList = reels
                                                profileReelsInitialPage = index
                                                profileReelsViewerVisible = true
                                            }
                                        )
                                        2 -> ProfileTaggedGrid(isDarkMode = isDarkMode)
                                        else -> ProfileInfoTabFinalLayout(isDarkMode = isDarkMode, profile = profileData)
                                    }
                                }
                            }
                        }
                        
                        // Top Navigation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 18.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onBack != null) {
                                Box(
                                    modifier = Modifier
                                        .size(41.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.42f))
                                        .clickable { onBack() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(23.dp))
                                }
                            } else {
                                Spacer(Modifier.size(41.dp))
                            }

                            if (isMyProfile) {
                                Box(
                                    modifier = Modifier
                                        .size(35.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.42f))
                                        .clickable { currentView = "settings" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Menu, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = profileReelsViewerVisible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.985f),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.985f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ReelsScreen(
                reels = profileReelsViewerList,
                accentBlue = accentBlue,
                isDarkMode = true,
                initialPage = profileReelsInitialPage,
                currentUserId = currentUser?.id,
                contentBottomPadding = 28.dp,
                onClose = { profileReelsViewerVisible = false },
                onProfileClick = { username ->
                    profileReelsViewerVisible = false
                    onNavigateToProfile?.invoke(username)
                }
            )
        }
    }
}

@Composable
private fun ProfileHeaderRefinedDesign(
    isDarkMode: Boolean, 
    isMyProfile: Boolean,
    profile: ProfileModel?,
    summary: ProfileQuickSummaryModel?,
    onFollowClick: (String) -> Unit
) {
    val accentBlue = Color(0xFF00A3FF)
    val contentColor = if (isDarkMode) Color.White else Color.Black
    
    Column(modifier = Modifier.padding(horizontal = 19.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Spacer(modifier = Modifier.width(121.dp)) 
            Spacer(modifier = Modifier.width(16.dp))

            var isNameExpanded by remember { mutableStateOf(false) }
            val name = profile?.fullName?.takeIf { it.isNotBlank() } ?: if (isMyProfile) "Your profile" else "Profile"
            val handle = profile?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "@profile"
            Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                Text(
                    text = name, color = contentColor, 
                    fontSize = if (isNameExpanded) 19.sp else 24.sp, fontWeight = FontWeight.Black,
                    maxLines = if (isNameExpanded) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isNameExpanded = !isNameExpanded }
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = handle, color = accentBlue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItemHeader((summary?.postsCount ?: profile?.postsCount ?: 0).toString(), "Posts", contentColor) { }
                    StatItemHeader((summary?.followersCount ?: profile?.followersCount ?: 0).toString(), "Followers", contentColor) {
                        onFollowClick("followers")
                    }
                    StatItemHeader((summary?.followingCount ?: profile?.followingCount ?: 0).toString(), "Following", contentColor) {
                        onFollowClick("following")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            val locationTitle = profile?.location?.takeIf { it.isNotBlank() }
                ?: profile?.region?.takeIf { it.isNotBlank() }
                ?: "Location not set"
            val schoolTitle = profile?.school?.takeIf { it.isNotBlank() } ?: "School not set"
            Column(modifier = Modifier.width(144.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.School, null, tint = accentBlue, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(locationTitle.uppercase(), fontSize = 12.sp, color = contentColor, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(schoolTitle.uppercase(), fontSize = 13.sp, color = contentColor, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = profile?.bio?.takeIf { it.isNotBlank() } ?: "No bio yet.",
                fontSize = 14.sp, color = contentColor, lineHeight = 19.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatItemHeader(value: String, label: String, contentColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Text(text = value, fontSize = 23.sp, fontWeight = FontWeight.Black, color = contentColor)
        Text(text = label, fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ProfileButtonsFinalAction(
    isDarkMode: Boolean,
    isMyProfile: Boolean,
    profile: ProfileModel?,
    accentBlue: Color,
    onEditClick: () -> Unit,
    onFollowStateChanged: () -> Unit = {},
    onMessageClick: (conversationId: String, username: String) -> Unit = { _, _ -> }
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glossyBg = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.05f)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val followRepository = remember { com.stugram.app.data.repository.FollowRepository() }
    val chatRepository = remember {
        com.stugram.app.data.remote.RetrofitClient.init(context.applicationContext)
        com.stugram.app.data.repository.ChatRepository()
    }
    var followStatus by remember(profile?.id) {
        mutableStateOf(
            profile?.followStatus?.lowercase()?.trim()
                ?: if (profile?.isFollowing == true) "following" else "not_following"
        )
    }
    val isFollowing = followStatus == "following"
    var isBusy by remember(profile?.id) { mutableStateOf(false) }
    var lastError by remember(profile?.id) { mutableStateOf<String?>(null) }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isMyProfile) {
            Button(onClick = onEditClick, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = accentBlue)) {
                Text("Edit Profile", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
            }
            Button(onClick = { }, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = glossyBg), border = BorderStroke(0.8.dp, Color.White.copy(alpha = if (isDarkMode) 0.08f else 0.16f))) {
                Text("Share Profile", color = contentColor, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.5.sp)
            }
        } else {
            Button(
                onClick = {
                    val userId = profile?.id ?: return@Button
                    if (isBusy) return@Button
                    val previous = followStatus
                    val willFollow = previous == "not_following"
                    followStatus = if (willFollow) "following" else "not_following"
                    FollowEvents.emit(
                        FollowEvent(
                            userId = userId,
                            username = profile.username,
                            isFollowing = followStatus == "following",
                            followStatus = followStatus
                        )
                    )
                    isBusy = true
                    lastError = null
                    scope.launch {
                        try {
                            if (willFollow) {
                                val response = followRepository.followUser(userId)
                                if (!response.isSuccessful) {
                                    if (response.code() == 409) {
                                        followStatus = "following"
                                        FollowEvents.emit(
                                            FollowEvent(
                                                userId = userId,
                                                username = profile.username,
                                                isFollowing = true,
                                                followStatus = "following"
                                            )
                                        )
                                        onFollowStateChanged()
                                    } else {
                                        followStatus = previous
                                        FollowEvents.emit(
                                            FollowEvent(
                                                userId = userId,
                                                username = profile.username,
                                                isFollowing = previous == "following",
                                                followStatus = previous
                                            )
                                        )
                                        lastError = response.profileActionErrorMessage("Follow action failed")
                                    }
                                } else {
                                    val responseStatus = response.body()?.data?.status?.lowercase()?.trim()
                                    followStatus = when (responseStatus) {
                                        "following", "requested", "self", "not_following" -> responseStatus
                                        else -> "following"
                                    }
                                    FollowEvents.emit(
                                        FollowEvent(
                                            userId = userId,
                                            username = profile.username,
                                            isFollowing = followStatus == "following",
                                            followStatus = followStatus
                                        )
                                    )
                                    onFollowStateChanged()
                                }
                            } else {
                                val response = followRepository.unfollowUser(userId)
                                if (!response.isSuccessful) {
                                    followStatus = previous
                                    FollowEvents.emit(
                                        FollowEvent(
                                            userId = userId,
                                            username = profile.username,
                                            isFollowing = previous == "following",
                                            followStatus = previous
                                        )
                                    )
                                    lastError = response.profileActionErrorMessage("Follow action failed")
                                } else {
                                    followStatus = "not_following"
                                    FollowEvents.emit(
                                        FollowEvent(
                                            userId = userId,
                                            username = profile.username,
                                            isFollowing = false,
                                            followStatus = "not_following"
                                        )
                                    )
                                    onFollowStateChanged()
                                }
                            }
                        } catch (e: Exception) {
                            followStatus = previous
                            FollowEvents.emit(
                                FollowEvent(
                                    userId = userId,
                                    username = profile.username,
                                    isFollowing = previous == "following",
                                    followStatus = previous
                                )
                            )
                            lastError = e.localizedMessage ?: "Follow action failed"
                        } finally {
                            isBusy = false
                        }
                    }
                },
                enabled = !isBusy && profile?.id != null,
                modifier = Modifier.weight(1f).height(41.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isFollowing) glossyBg else accentBlue),
                border = if (isFollowing) BorderStroke(1.dp, accentBlue) else null
            ) {
                Text(
                    when (followStatus) {
                        "following" -> "Following"
                        "requested" -> "Requested"
                        "self" -> "You"
                        else -> "Follow"
                    },
                    color = when (followStatus) {
                        "following" -> accentBlue
                        "requested" -> contentColor
                        "self" -> contentColor
                        else -> Color.White
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = {
                    val userId = profile?.id ?: return@Button
                    val username = profile.username
                    if (isBusy) return@Button
                    isBusy = true
                    lastError = null
                    scope.launch {
                        try {
                            val response = chatRepository.createConversation(userId)
                            val conversation = response.body()?.data
                            if (response.isSuccessful && conversation != null) {
                                onMessageClick(conversation.id, username)
                            } else {
                                lastError = response.body()?.message ?: response.message().ifBlank { "Could not open chat" }
                            }
                        } catch (e: Exception) {
                            lastError = e.localizedMessage ?: "Could not open chat"
                        } finally {
                            isBusy = false
                        }
                    }
                },
                enabled = !isBusy && profile?.id != null,
                modifier = Modifier.weight(1f).height(41.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = glossyBg),
                border = BorderStroke(0.5.dp, Color.Gray.copy(0.2f))
            ) {
                Text("Message", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
    }

    lastError?.let { message ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            color = Color.Red.copy(alpha = 0.9f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
private fun FavoriteMomentsRectSection(isDarkMode: Boolean) {
    val moments = listOf("Dubai", "Work", "Family", "Fashion", "Gym")
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glossyBg = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

    Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
        Text("Favorite Moments", fontSize = 19.sp, fontWeight = FontWeight.Black, color = contentColor, modifier = Modifier.padding(start = 18.dp, bottom = 15.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(moments) { title ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(77.dp, 103.dp).clip(RoundedCornerShape(16.dp)).background(glossyBg).border(1.dp, Color.Gray.copy(0.18f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(title, fontSize = 12.sp, color = contentColor, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ProfileTabSelectorOptimized(selectedTab: Int, onTabSelected: (Int) -> Unit, isDarkMode: Boolean) {
    val tabs = listOf(Icons.Default.GridView, Icons.Default.PlayCircle, Icons.Default.AssignmentInd, Icons.Default.Info)
    val glassBg = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.05f)

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp).clip(RoundedCornerShape(28.dp)).background(glassBg).border(0.5.dp, Color.Gray.copy(0.2f), RoundedCornerShape(28.dp)).padding(4.dp)) {
        val configuration = LocalConfiguration.current
        val tabWidth = (configuration.screenWidthDp.dp - 48.dp) / tabs.size
        val indicatorOffset by animateDpAsState(targetValue = tabWidth * selectedTab, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "indicator")
        Box(modifier = Modifier.offset(x = indicatorOffset).width(tabWidth).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color(0xFF00A3FF)))
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, icon ->
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabSelected(index) }, contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (selectedTab == index) Color.White else Color.Gray, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfilePostsGrid(isDarkMode: Boolean, onPostClick: (List<PostData>, Int) -> Unit) {
    val viewModel: HomeViewModel = viewModel()
    val posts = remember(viewModel.profilePosts) { viewModel.profilePosts.filter { !it.isVideo } }
    if (posts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No posts yet", color = Color.Gray, fontWeight = FontWeight.Bold)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3), 
        modifier = Modifier.fillMaxSize(), 
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(posts) { index, post ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDarkMode) Color(0xFF262626) else Color(0xFFF5F5F5))
                    .clickable { onPostClick(posts, index) }
            ) {
                AppBanner(
                    imageModel = post.thumbnailUrl ?: post.image,
                    title = post.user,
                    modifier = Modifier.fillMaxSize(),
                    isDarkMode = isDarkMode,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
fun ProfilePostDetailFeed(
    isDarkMode: Boolean,
    posts: List<PostData>,
    initialIndex: Int,
    currentUserId: String? = null,
    onBack: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel()
    val detailPosts = posts.ifEmpty { viewModel.profilePosts.filter { !it.isVideo } }
    val safeInitialIndex = initialIndex.coerceIn(0, (detailPosts.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitialIndex)
    val accentBlue = Color(0xFF00A3FF)
    var selectedCommentsPost by remember { mutableStateOf<PostData?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(if (isDarkMode) Color.Black else Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (isDarkMode) Color.White else Color.Black)
            }
            Text(
                "Postlar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDarkMode) Color.White else Color.Black
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            if (detailPosts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No posts yet", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                items(detailPosts, key = { it.backendId ?: it.id }) { post ->
                    DashboardPostItem(
                        post = post,
                        accentBlue = accentBlue,
                        isDarkMode = isDarkMode,
                        onCommentsClick = { selectedCommentsPost = it },
                        onProfileClick = {},
                        currentUserId = currentUserId,
                        onToggleLike = { viewModel.toggleLike(it) },
                        onToggleSave = { viewModel.toggleSave(it) }
                    )
                }
            }
        }
    }

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

    BackHandler {
        onBack()
    }
}

@Composable
private fun ProfileReelsGrid(
    isDarkMode: Boolean,
    username: String?,
    onReelClick: (List<PostData>, Int) -> Unit
) {
    val repository = remember { ProfileRepository() }
    var reels by remember(username) { mutableStateOf(emptyList<PostData>()) }
    LaunchedEffect(username) {
        reels = if (username.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                repository.getProfileReels(username, page = 1, limit = 60)
                    .body()
                    ?.data
                    .orEmpty()
                    .map { it.toProfilePostData() }
            }.getOrDefault(emptyList())
        }
    }
    if (reels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No reels yet", color = Color.Gray, fontWeight = FontWeight.Bold)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(reels) { index, reel ->
                Box(
                    modifier = Modifier
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFF0F0F0))
                        .clickable { onReelClick(reels, index) }
                ) {
                    AppBanner(
                        imageModel = reel.thumbnailUrl ?: reel.image,
                        title = reel.user,
                        modifier = Modifier.fillMaxSize(),
                        isDarkMode = isDarkMode,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                                )
                            )
                    )
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.align(Alignment.Center), tint = Color.White)
                }
            }
        }
    }
}

private fun PostModel.toProfilePostData(): PostData {
    val firstMedia = media.firstOrNull()
    return PostData(
        id = id.hashCode(),
        backendId = id,
        authorId = author.id,
        user = author.username,
        authorFullName = author.fullName,
        image = firstMedia?.url,
        thumbnailUrl = firstMedia?.thumbnailUrl,
        userAvatar = author.avatar,
        caption = caption,
        likes = likesCount,
        comments = commentsCount,
        isLiked = viewerHasLiked == true,
        isSaved = viewerHasSaved == true,
        isVideo = firstMedia?.type == "video",
        mediaAspectRatio = mediaAspectRatioFromDimensions(firstMedia?.width, firstMedia?.height),
        authorFollowStatus = author.followStatus,
        createdAt = createdAt
    )
}

private fun retrofit2.Response<*>.profileActionErrorMessage(defaultMessage: String): String {
    val raw = runCatching { errorBody()?.string() }.getOrNull().orEmpty()
    val bodyMessage = runCatching {
        JSONObject(raw).optString("message")
    }.getOrNull().orEmpty()
    return bodyMessage.takeIf { it.isNotBlank() }
        ?: message().takeIf { it.isNotBlank() }
        ?: defaultMessage
}

@Composable
private fun ProfileTaggedGrid(isDarkMode: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Tagged posts", color = Color.Gray, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ProfileInfoTabFinalLayout(isDarkMode: Boolean, profile: ProfileModel?) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val accentBlue = Color(0xFF00A3FF)
    val birthday = profile?.birthday?.takeIf { it.isNotBlank() } ?: "Not set"
    val location = listOf(
        profile?.district?.takeIf { !it.isNullOrBlank() },
        profile?.region?.takeIf { !it.isNullOrBlank() }
    ).filterNotNull().joinToString(", ").ifBlank {
        profile?.location?.takeIf { it.isNotBlank() } ?: "Not set"
    }
    val education = listOf(
        profile?.school?.takeIf { !it.isNullOrBlank() },
        profile?.grade?.takeIf { !it.isNullOrBlank() },
        profile?.group?.takeIf { !it.isNullOrBlank() }
    ).filterNotNull().joinToString(" • ").ifBlank { "Not set" }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp).verticalScroll(rememberScrollState())) {
        Text("Profile details", color = accentBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 12.dp))
        InfoBoxItemDetail(Icons.Default.Person, "Full name", profile?.fullName ?: "Not set", contentColor, isDarkMode)
        InfoBoxItemDetail(Icons.Default.Badge, "Username", profile?.username?.let { "@$it" } ?: "Not set", contentColor, isDarkMode)
        InfoBoxItemDetail(Icons.Default.Cake, "Birthday", birthday, contentColor, isDarkMode)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Location and education", color = accentBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 12.dp))
        InfoBoxItemDetail(Icons.Default.LocationCity, "Location", location, contentColor, isDarkMode)
        InfoBoxItemDetail(Icons.Default.School, "School", education, contentColor, isDarkMode)
    }
}

@Composable
private fun InfoBoxItemDetail(icon: ImageVector, label: String, value: String, contentColor: Color, isDarkMode: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF00A3FF).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFF00A3FF), modifier = Modifier.size(22.dp)) }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, color = if (isDarkMode) Color.Gray else Color.DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(value, color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
