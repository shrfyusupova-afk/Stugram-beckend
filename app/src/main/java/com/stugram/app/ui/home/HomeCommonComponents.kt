package com.stugram.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.model.CommentModel
import com.stugram.app.data.repository.PostInteractionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

@Composable
fun PopLikeAnimation(offset: Offset) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(1f, tween(200))
            delay(400)
            alpha.animateTo(0f, tween(200))
        }
        scale.animateTo(1.2f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        scale.animateTo(0f, tween(200))
    }

    Box(
        Modifier
            .offset { IntOffset(offset.x.toInt() - 60, offset.y.toInt() - 60) }
            .size(120.dp)
            .graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    post: PostData,
    isDarkMode: Boolean,
    accentBlue: Color,
    onDismiss: () -> Unit,
    onCommentAdded: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context.applicationContext) }
    val currentUser by tokenManager.currentUser.collectAsState(initial = null)
    val interactionRepository = remember { PostInteractionRepository() }
    var comments by remember(post.backendId) { mutableStateOf<List<CommentModel>>(emptyList()) }
    var isLoading by remember(post.backendId) { mutableStateOf(true) }
    var errorMessage by remember(post.backendId) { mutableStateOf<String?>(null) }
    var commentText by remember(post.backendId) { mutableStateOf("") }
    var isSubmitting by remember(post.backendId) { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    fun loadComments() {
        val backendId = post.backendId ?: return
        scope.launch {
            isLoading = true
            errorMessage = null
            val response = runCatching { interactionRepository.getComments(backendId) }.getOrNull()
            if (response?.isSuccessful == true) {
                comments = response.body()?.data ?: emptyList()
            } else {
                errorMessage = response?.body()?.message ?: "Could not load comments"
            }
            isLoading = false
        }
    }

    LaunchedEffect(post.backendId) {
        if (post.backendId != null) loadComments() else {
            isLoading = false
            errorMessage = "Comments are unavailable for this post"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDarkMode) Color(0xFF1A1A1A).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray.copy(0.4f)) }
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.White.copy(0.08f), Color.Transparent))
            ))
            
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "Comments",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if(isDarkMode) Color.White else Color.Black
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    if (isLoading) {
                        item {
                            Text(
                                "Loading comments...",
                                color = if (isDarkMode) Color.White else Color.Black,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                            )
                        }
                    } else if (errorMessage != null) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                                Text(errorMessage!!, color = Color.Red.copy(alpha = 0.85f), fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Retry",
                                    color = accentBlue,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { loadComments() }
                                )
                            }
                        }
                    } else if (comments.isEmpty()) {
                        item {
                            Text(
                                "No comments yet. Be the first to comment.",
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                            )
                        }
                    } else {
                        items(comments, key = { it.id }) { comment ->
                            CommentItem(comment.toUiCommentData(), isDarkMode, accentBlue)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val offset = try { sheetState.requireOffset() } catch (e: Exception) { screenHeightPx }
                        // Inputni har doim ekranning pastki qismida (ko'rinib turgan joyida) ushlab turish:
                        // screenHeightPx - offset = sheetning hozirgi ko'rinib turgan balandligi
                        translationY = (screenHeightPx - offset - size.height).coerceAtLeast(0f)
                    }
            ) {
                CommentInputSection(
                    isDarkMode = isDarkMode,
                    accentBlue = accentBlue,
                    currentUserName = currentUser?.fullName ?: currentUser?.username ?: "You",
                    currentUserAvatar = currentUser?.avatar,
                    commentText = commentText,
                    onCommentChange = { commentText = it },
                    isSubmitting = isSubmitting,
                    onSubmit = {
                        val backendId = post.backendId ?: return@CommentInputSection
                        if (commentText.isBlank() || isSubmitting) return@CommentInputSection
                        scope.launch {
                            isSubmitting = true
                            val submittedText = commentText.trim()
                            val response = runCatching {
                                interactionRepository.addComment(backendId, submittedText)
                            }.getOrNull()
                            val newComment = response?.body()?.data
                            if (response?.isSuccessful == true && newComment != null) {
                                comments = listOf(newComment) + comments
                                commentText = ""
                                onCommentAdded()
                            } else {
                                errorMessage = response?.body()?.message ?: "Could not post comment"
                            }
                            isSubmitting = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CommentItem(comment: CommentData, isDarkMode: Boolean, accentBlue: Color) {
    var isRepliesVisible by remember { mutableStateOf(false) }
    val textColor = if (isDarkMode) Color.White else Color.Black

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            AppAvatar(
                imageModel = comment.avatar,
                name = comment.user,
                username = comment.user,
                modifier = Modifier.size(36.dp),
                isDarkMode = isDarkMode,
                fontSize = 14.sp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.user, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
                    Spacer(Modifier.width(8.dp))
                    Text(comment.time, color = Color.Gray, fontSize = 12.sp)
                }
                Text(comment.text, fontSize = 14.sp, color = textColor, modifier = Modifier.padding(vertical = 4.dp))

                Text("Reply", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { })

                if (comment.replies.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isRepliesVisible) "Hide replies" else "Reply (${comment.replies.size})",
                        color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { isRepliesVisible = !isRepliesVisible }
                    )

                    AnimatedVisibility(visible = isRepliesVisible) {
                        Column {
                            comment.replies.forEach { reply ->
                                Row(modifier = Modifier.padding(top = 12.dp, start = 16.dp)) {
                                    AppAvatar(
                                        imageModel = reply.avatar,
                                        name = reply.user,
                                        username = reply.user,
                                        modifier = Modifier.size(24.dp),
                                        isDarkMode = isDarkMode,
                                        fontSize = 10.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(reply.user, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColor)
                                        Text(reply.text, fontSize = 13.sp, color = textColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FavoriteBorder, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Text(comment.likes.toString(), color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun CommentInputSection(
    isDarkMode: Boolean,
    accentBlue: Color,
    currentUserName: String,
    currentUserAvatar: String?,
    commentText: String,
    onCommentChange: (String) -> Unit,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    val emojis = listOf("❤️", "🙌", "🔥", "👏", "😢", "😍", "😮")
    val glassColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.2f to glassColor.copy(alpha = 0.85f),
                    0.5f to glassColor.copy(alpha = 0.98f),
                    1.0f to glassColor
                )
            )
            .padding(top = 24.dp, bottom = 12.dp)
    ) {
        // The Input UI (Thinner & Polished)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                emojis.forEach { emoji ->
                    Text(
                        emoji,
                        fontSize = 24.sp,
                        modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                        ) { onCommentChange(commentText + emoji) }
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isDarkMode) Color.White.copy(0.15f) else Color.Black.copy(0.06f))
                    .border(
                        0.5.dp,
                        if (isDarkMode) Color.White.copy(0.2f) else Color.Black.copy(0.12f),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 12.dp)
            ) {
                AppAvatar(
                    imageModel = currentUserAvatar,
                    name = currentUserName,
                    username = currentUserName,
                    modifier = Modifier.size(30.dp),
                    isDarkMode = isDarkMode,
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(10.dp))
                
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (commentText.isEmpty()) {
                        Text(
                            "Add a comment...",
                            color = contentColor.copy(alpha = 0.45f),
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = commentText,
                        onValueChange = onCommentChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = contentColor, fontSize = 14.sp),
                        cursorBrush = SolidColor(accentBlue),
                        maxLines = 2
                    )
                }
                
                Text(
                    if (isSubmitting) "..." else "Post",
                    color = if (commentText.isNotBlank() && !isSubmitting) accentBlue else accentBlue.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(enabled = commentText.isNotBlank() && !isSubmitting) { onSubmit() }
                )
            }
        }
    }
}

private fun CommentModel.toUiCommentData(): CommentData {
    return CommentData(
        id = id.hashCode(),
        user = author?.fullName?.takeIf { it.isNotBlank() } ?: author?.username ?: "User",
        avatar = author?.avatar,
        text = content,
        time = createdAt.toCommentAgeLabel(),
        likes = 0
    )
}

private fun String?.toCommentAgeLabel(): String {
    val createdAtValue = this?.takeIf { it.isNotBlank() } ?: return "1 min"
    val createdMillis = runCatching { Instant.parse(createdAtValue).toEpochMilli() }.getOrNull() ?: return "1 min"
    val diffMillis = (System.currentTimeMillis() - createdMillis).coerceAtLeast(0L)
    val minutes = Duration.ofMillis(diffMillis).toMinutes().coerceAtLeast(1L)

    return when {
        minutes < 60 -> "${minutes.toInt()} ${if (minutes == 1L) "min" else "mins"}"
        minutes < 60 * 24 -> {
            val hours = minutes / 60
            "${hours.toInt()} ${if (hours == 1L) "hour" else "hours"}"
        }
        else -> {
            val days = minutes / (60 * 24)
            "${days.toInt()} ${if (days == 1L) "day" else "days"}"
        }
    }
}
