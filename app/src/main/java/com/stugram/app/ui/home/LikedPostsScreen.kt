package com.stugram.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.model.PostInteractionHistoryItem
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.repository.PostInteractionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private fun PostModel.toPostDataLiked(): PostData {
    val firstMedia = media.firstOrNull()
    return PostData(
        id = id.hashCode(),
        backendId = id,
        user = author.username,
        authorFullName = author.fullName,
        authorId = author.id,
        image = firstMedia?.url,
        thumbnailUrl = firstMedia?.thumbnailUrl,
        userAvatar = author.avatar,
        caption = caption,
        likes = likesCount,
        comments = commentsCount,
        isLiked = true,
        isSaved = false,
        isVideo = firstMedia?.type == "video",
        mediaAspectRatio = mediaAspectRatioFromDimensions(firstMedia?.width, firstMedia?.height),
        authorFollowStatus = author.followStatus,
        createdAt = createdAt
    )
}

private data class LikedUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val items: List<PostData> = emptyList()
)

private class LikedPostsViewModel(
    private val repo: PostInteractionRepository = PostInteractionRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(LikedUiState(isLoading = true))
    val state: StateFlow<LikedUiState> = _state.asStateFlow()

    fun load(initial: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = initial,
                isRefreshing = !initial,
                errorMessage = null
            )
            try {
                val res = repo.getLikedPosts(page = 1, limit = 50)
                if (res.isSuccessful) {
                    val posts = res.body()?.data
                        .orEmpty()
                        .mapNotNull(PostInteractionHistoryItem::post)
                        .map(PostModel::toPostDataLiked)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        items = posts,
                        errorMessage = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = res.message().ifBlank { "Couldn't load liked posts" }
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = e.localizedMessage ?: "Couldn't load liked posts"
                )
            }
        }
    }

    fun unlike(post: PostData) {
        val postId = post.backendId ?: return
        viewModelScope.launch {
            try {
                val res = repo.unlikePost(postId)
                if (res.isSuccessful) {
                    _state.value = _state.value.copy(items = _state.value.items.filterNot { it.backendId == postId })
                } else {
                    _state.value = _state.value.copy(errorMessage = res.message().ifBlank { "Couldn't remove like" })
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = e.localizedMessage ?: "Couldn't remove like")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedPostsScreen(
    isDarkMode: Boolean,
    accentBlue: Color,
    onNavigateToProfile: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val viewModel: LikedPostsViewModel = viewModel()
    val context = LocalContext.current
    val tokenManager = remember(context) { TokenManager(context.applicationContext) }
    val currentUser by tokenManager.currentUser.collectAsState(initial = null)
    val state by viewModel.state.collectAsState()
    val background = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val content = if (isDarkMode) Color.White else Color.Black

    var selectedPostId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load(initial = true)
    }

    selectedPostId?.let { pid ->
        PostDetailScreen(
            postId = pid,
            onBack = { selectedPostId = null },
            isDarkMode = isDarkMode,
            onThemeChange = {}
        )
        return
    }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("Liked", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = content)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = content,
                    navigationIconContentColor = content
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.load(initial = false) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(background),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = rememberPullToRefreshState(),
                    isRefreshing = state.isRefreshing,
                    color = accentBlue,
                    containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentBlue, strokeWidth = 2.dp)
                    }
                }
                !state.errorMessage.isNullOrBlank() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = Color.Red.copy(alpha = 0.75f), modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Couldn't load liked posts", color = content, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(state.errorMessage!!, color = content.copy(alpha = 0.6f), fontSize = 12.sp)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Retry",
                                color = accentBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { viewModel.load(initial = true) }
                            )
                        }
                    }
                }
                state.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Inbox, null, tint = content.copy(alpha = 0.25f), modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("No liked posts yet", color = content, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Posts you like will appear here.",
                                color = content.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(state.items, key = { it.backendId ?: it.id }) { post ->
                            Box {
                                DashboardPostItem(
                                    post = post,
                                    accentBlue = accentBlue,
                                    isDarkMode = isDarkMode,
                                    onCommentsClick = { selectedPostId = it.backendId },
                                    onProfileClick = { username -> onNavigateToProfile(username) },
                                    currentUserId = currentUser?.id,
                                    onToggleLike = { viewModel.unlike(it) }
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { selectedPostId = post.backendId }
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 18.dp, end = 22.dp)
                                        .clickable { viewModel.unlike(post) }
                                ) {
                                    Icon(Icons.Rounded.Favorite, null, tint = Color.Red.copy(alpha = 0.9f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
