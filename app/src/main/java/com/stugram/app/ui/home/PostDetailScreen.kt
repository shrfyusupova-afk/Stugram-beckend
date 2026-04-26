package com.stugram.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.repository.PostRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit,
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onNavigateToProfile: (String) -> Unit = {}
) {
    val viewModel: HomeViewModel = viewModel()
    val context = LocalContext.current
    val tokenManager = remember(context) { TokenManager(context.applicationContext) }
    val currentUser by tokenManager.currentUser.collectAsState(initial = null)
    val postRepository = remember { PostRepository() }
    var post by remember { mutableStateOf<PostData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val accentBlue = Color(0xFF00A3FF)
    var selectedCommentsPost by remember { mutableStateOf<PostData?>(null) }

    LaunchedEffect(postId) {
        isLoading = true
        try {
            val response = postRepository.getPost(postId)
            if (response.isSuccessful) {
                val model = response.body()?.data
                if (model != null) {
                    val firstMedia = model.media.firstOrNull()
                    post = PostData(
                        id = model.id.hashCode(),
                        backendId = model.id,
                        authorId = model.author.id,
                        user = model.author.username,
                        authorFullName = model.author.fullName,
                        image = firstMedia?.url,
                        thumbnailUrl = firstMedia?.thumbnailUrl,
                        userAvatar = model.author.avatar,
                        caption = model.caption,
                        likes = model.likesCount,
                        comments = model.commentsCount,
                        isLiked = model.viewerHasLiked ?: false,
                        isSaved = model.viewerHasSaved ?: false,
                        isVideo = firstMedia?.type == "video",
                        mediaAspectRatio = mediaAspectRatioFromDimensions(firstMedia?.width, firstMedia?.height),
                        authorFollowStatus = model.author.followStatus,
                        createdAt = model.createdAt
                    )
                }
            } else {
                error = "Failed to load post"
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) Color.Black else Color.White,
                    titleContentColor = if (isDarkMode) Color.White else Color.Black,
                    navigationIconContentColor = if (isDarkMode) Color.White else Color.Black
                )
            )
        },
        containerColor = if (isDarkMode) Color.Black else Color.White
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = accentBlue)
            } else if (error != null) {
                Text(error!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
            } else {
                post?.let { p ->
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            DashboardPostItem(
                                post = p,
                                accentBlue = accentBlue,
                                isDarkMode = isDarkMode,
                                onCommentsClick = { selectedCommentsPost = it },
                                onProfileClick = onNavigateToProfile,
                                currentUserId = currentUser?.id,
                                onToggleLike = { viewModel.toggleLike(it) },
                                onToggleSave = { viewModel.toggleSave(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedCommentsPost?.let { p ->
        CommentsBottomSheet(
            post = p,
            isDarkMode = isDarkMode,
            accentBlue = accentBlue,
            onDismiss = { selectedCommentsPost = null },
            onCommentAdded = {
                p.backendId?.let { viewModel.handleCommentAdded(it) }
            }
        )
    }
}
