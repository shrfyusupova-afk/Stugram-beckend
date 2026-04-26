package com.stugram.app.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.VideoView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaMetadataRetriever
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import java.io.File
import java.io.FileOutputStream
import android.media.ThumbnailUtils
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.PI
import kotlin.math.abs
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import com.stugram.app.data.repository.PostRepository
import com.stugram.app.data.repository.StoryRepository
import com.stugram.app.data.repository.UploadOutcome
import com.stugram.app.data.repository.toUploadOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

enum class StoryEditorTool {
    Text, Sticker, Music, Brush, Effects, Mention, Download
}

private const val CREATION_MAX_UPLOAD_BYTES = 100L * 1024L * 1024L
private const val CREATION_MAX_UPLOAD_LABEL = "100 MB"

data class StoryTextData(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val color: Color,
    val backgroundColor: Color = Color.Transparent,
    val offset: Offset = Offset(200f, 400f),
    val scale: Float = 1f,
    val rotation: Float = 0f
)

data class StoryStickerData(
    val id: Long = System.currentTimeMillis(),
    val imageUrl: String,
    val offset: Offset = Offset(200f, 600f),
    val scale: Float = 1f,
    val rotation: Float = 0f
)

data class MusicData(
    val title: String,
    val artist: String,
    val albumArt: String
)

data class StoryDrawingStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

data class StoryEffectPreset(
    val name: String,
    val tint: Color,
    val alpha: Float,
    val overlay: Brush? = null
)

data class PostDraftMedia(
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    val fileSizeLabel: String,
    val fileSizeBytes: Long,
    val isVideo: Boolean
)

private class MediaTransformState(
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero
) {
    var scale by mutableFloatStateOf(initialScale)
    var offset by mutableStateOf(initialOffset)
}

private data class PostAspectOption(
    val label: String,
    val ratio: Float,
    val icon: ImageVector
)

private val postAspectOptions = listOf(
    PostAspectOption("1:1", 1f, Icons.Rounded.CropSquare),
    PostAspectOption("4:5", 4f / 5f, Icons.Rounded.CropPortrait),
    PostAspectOption("9:16", 9f / 16f, Icons.Rounded.StayCurrentPortrait),
    PostAspectOption("16:9", 16f / 9f, Icons.Rounded.CropLandscape)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraScreen(
    onDismiss: () -> Unit,
    accentBlue: Color,
    onPostCreated: () -> Unit = {},
    onStoryCreated: () -> Unit = {},
    onReelCreated: () -> Unit = {}
) {
    val context = LocalContext.current
    val postRepository = remember { PostRepository() }
    val storyRepository = remember { StoryRepository() }
    val modes = listOf("POST", "STORY", "REELS")
    val pagerState = rememberPagerState(pageCount = { modes.size }, initialPage = 1)
    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }

    var isRecording by remember { mutableStateOf(false) }
    var recordingProgress by remember { mutableStateOf(0f) }
    var showGallery by remember { mutableStateOf(false) }

    var postDraftMedia by remember { mutableStateOf<List<PostDraftMedia>>(emptyList()) }
    var captionInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("") }
    var hashtagInput by remember { mutableStateOf("") }
    var publishError by remember { mutableStateOf<String?>(null) }
    var isPublishingPost by remember { mutableStateOf(false) }
    var postPublishJob by remember { mutableStateOf<Job?>(null) }

    var storyDraftMedia by remember { mutableStateOf<PostDraftMedia?>(null) }
    var storyCaptionInput by remember { mutableStateOf("") }
    var storyPublishError by remember { mutableStateOf<String?>(null) }
    var isPublishingStory by remember { mutableStateOf(false) }
    var storyPublishJob by remember { mutableStateOf<Job?>(null) }

    var reelDraftMedia by remember { mutableStateOf<PostDraftMedia?>(null) }
    var reelCaptionInput by remember { mutableStateOf("") }
    var reelHashtagInput by remember { mutableStateOf("") }
    var reelLocationInput by remember { mutableStateOf("") }
    var reelTagPeopleInput by remember { mutableStateOf("") }
    var reelAudioNameInput by remember { mutableStateOf("Original Audio") }
    var reelAudienceInput by remember { mutableStateOf("Everyone") }
    var reelAiLabelEnabled by remember { mutableStateOf(false) }
    var reelPublishError by remember { mutableStateOf<String?>(null) }
    var isPublishingReel by remember { mutableStateOf(false) }
    var reelPublishProgress by remember { mutableFloatStateOf(0f) }
    var reelPublishId by remember { mutableStateOf<String?>(null) }
    var reelPublishJob by remember { mutableStateOf<Job?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val incoming = uris.mapNotNull { uri ->
                context.toPostDraftMedia(uri)?.takeIf { !it.isVideo }
            }
            if (incoming.isNotEmpty()) {
                val accepted = incoming.filter { it.fileSizeBytes <= CREATION_MAX_UPLOAD_BYTES }
                val rejectedCount = incoming.size - accepted.size
                if (rejectedCount > 0) {
                    publishError = "Some photos are larger than $CREATION_MAX_UPLOAD_LABEL. Please choose smaller media."
                }
                val currentImages = postDraftMedia.filter { !it.isVideo }
                postDraftMedia = (currentImages + accepted)
                    .distinctBy { it.uri.toString() }
                    .take(10)
                if (accepted.isNotEmpty() && rejectedCount == 0) publishError = null
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val draft = uri?.let { context.toPostDraftMedia(it) }
        if (draft != null) {
            if (draft.fileSizeBytes > CREATION_MAX_UPLOAD_BYTES) {
                publishError = "This video is ${draft.fileSizeLabel}. Maximum upload size is $CREATION_MAX_UPLOAD_LABEL."
            } else {
                postDraftMedia = listOf(draft)
                publishError = null
            }
        }
    }

    val storyMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val draft = uri?.let { context.toPostDraftMedia(it) }
        if (draft != null && draft.fileSizeBytes > CREATION_MAX_UPLOAD_BYTES) {
            storyDraftMedia = null
            storyPublishError = "This story media is ${draft.fileSizeLabel}. Maximum upload size is $CREATION_MAX_UPLOAD_LABEL."
        } else {
            storyDraftMedia = draft
            storyPublishError = null
        }
    }

    val reelVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val draft = uri?.let { context.toPostDraftMedia(it) }
        if (draft != null && draft.isVideo && draft.fileSizeBytes > CREATION_MAX_UPLOAD_BYTES) {
            reelPublishError = "This reel video is ${draft.fileSizeLabel}. Maximum upload size is $CREATION_MAX_UPLOAD_LABEL."
        } else if (draft != null && draft.isVideo) {
            reelDraftMedia = draft
            reelPublishError = null
        } else if (uri != null) {
            reelPublishError = "Please choose a valid video for the reel."
        }
    }

    fun resetPostDraft() {
        postPublishJob?.cancel()
        postPublishJob = null
        postDraftMedia = emptyList()
        captionInput = ""
        locationInput = ""
        hashtagInput = ""
        publishError = null
        isPublishingPost = false
    }

    fun resetStoryDraft() {
        storyPublishJob?.cancel()
        storyPublishJob = null
        storyDraftMedia = null
        storyCaptionInput = ""
        storyPublishError = null
        isPublishingStory = false
    }

    fun resetReelDraft() {
        reelPublishJob?.cancel()
        reelPublishJob = null
        reelDraftMedia = null
        reelCaptionInput = ""
        reelHashtagInput = ""
        reelLocationInput = ""
        reelTagPeopleInput = ""
        reelAudioNameInput = "Original Audio"
        reelAudienceInput = "Everyone"
        reelAiLabelEnabled = false
        reelPublishError = null
        isPublishingReel = false
        reelPublishProgress = 0f
        reelPublishId = null
    }

    suspend fun publishPostDraft() {
        if (postDraftMedia.isEmpty() || isPublishingPost) return
        val oversizedMedia = postDraftMedia.firstOrNull { it.fileSizeBytes > CREATION_MAX_UPLOAD_BYTES }
        if (oversizedMedia != null) {
            publishError = "${oversizedMedia.displayName} is ${oversizedMedia.fileSizeLabel}. Maximum upload size is $CREATION_MAX_UPLOAD_LABEL."
            return
        }

        publishError = null
        isPublishingPost = true

        val hashtags = hashtagInput
            .split(" ", ",", "\n")
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinct()

        try {
            val response = postRepository.createPost(
                context = context,
                mediaUris = postDraftMedia.map { it.uri },
                caption = captionInput.trim().takeIf { it.isNotBlank() },
                location = locationInput.trim().takeIf { it.isNotBlank() },
                hashtags = hashtags
            )
            when (val outcome = response.toUploadOutcome("Post upload failed")) {
                is UploadOutcome.Success -> {
                    snackBarHostState.showSnackbar(outcome.message ?: "Post published successfully")
                    onPostCreated()
                    resetPostDraft()
                    onDismiss()
                }
                is UploadOutcome.Failure -> {
                    publishError = outcome.message
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            publishError = error.localizedMessage ?: "Post upload failed. Please check your connection."
        } finally {
            isPublishingPost = false
            postPublishJob = null
        }
    }

    suspend fun publishStoryDraft() {
        val draft = storyDraftMedia ?: return
        if (isPublishingStory) return
        if (draft.fileSizeBytes > CREATION_MAX_UPLOAD_BYTES) {
            storyPublishError = "${draft.displayName} is ${draft.fileSizeLabel}. Maximum upload size is $CREATION_MAX_UPLOAD_LABEL."
            return
        }

        storyPublishError = null
        isPublishingStory = true

        try {
            val response = storyRepository.createStory(
                context = context,
                mediaUri = draft.uri,
                caption = storyCaptionInput.trim().takeIf { it.isNotBlank() }
            )
            when (val outcome = response.toUploadOutcome("Story upload failed")) {
                is UploadOutcome.Success -> {
                    snackBarHostState.showSnackbar(outcome.message ?: "Story published successfully")
                    onStoryCreated()
                    resetStoryDraft()
                    onDismiss()
                }
                is UploadOutcome.Failure -> {
                    storyPublishError = outcome.message
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            storyPublishError = error.localizedMessage ?: "Story upload failed. Please check your connection."
        } finally {
            isPublishingStory = false
            storyPublishJob = null
        }
    }

    suspend fun publishReelDraft() {
        val draft = reelDraftMedia ?: return
        if (isPublishingReel) return
        if (draft.fileSizeBytes > CREATION_MAX_UPLOAD_BYTES) {
            reelPublishError = "${draft.displayName} is ${draft.fileSizeLabel}. Maximum upload size is $CREATION_MAX_UPLOAD_LABEL."
            return
        }

        reelPublishError = null
        isPublishingReel = true
        reelPublishProgress = 0f
        reelPublishId = null

        val hashtags = reelHashtagInput
            .split(" ", ",", "\n")
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinct()

        try {
            val progressJob = scope.launch {
                while (isActive && reelPublishProgress < 0.9f) {
                    delay(160)
                    reelPublishProgress = (reelPublishProgress + 0.05f).coerceAtMost(0.9f)
                }
            }
            val response = postRepository.createPost(
                context = context,
                mediaUris = listOf(draft.uri),
                caption = reelCaptionInput.trim().takeIf { it.isNotBlank() },
                location = reelLocationInput.trim().takeIf { it.isNotBlank() },
                hashtags = hashtags
            )
            when (val outcome = response.toUploadOutcome("Reel upload failed")) {
                is UploadOutcome.Success -> {
                    progressJob.cancel()
                    reelPublishProgress = 1f
                    reelPublishId = outcome.data.id
                    snackBarHostState.showSnackbar(
                        if (reelPublishId.isNullOrBlank()) {
                            outcome.message ?: "Reel published successfully"
                        } else {
                            "${outcome.message ?: "Reel published successfully"} • ID: ${reelPublishId}"
                        }
                    )
                    onReelCreated()
                    resetReelDraft()
                    onDismiss()
                }
                is UploadOutcome.Failure -> {
                    progressJob.cancel()
                    reelPublishError = outcome.message
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reelPublishError = error.localizedMessage ?: "Reel upload failed. Please check your connection."
        } finally {
            isPublishingReel = false
            reelPublishJob = null
            if (reelPublishProgress < 1f) reelPublishProgress = 0f
        }
    }

    fun cancelPostUpload() {
        postPublishJob?.cancel()
        postPublishJob = null
        isPublishingPost = false
    }

    fun cancelStoryUpload() {
        storyPublishJob?.cancel()
        storyPublishJob = null
        isPublishingStory = false
    }

    fun cancelReelUpload() {
        reelPublishJob?.cancel()
        reelPublishJob = null
        isPublishingReel = false
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingProgress = 0f
            val startTime = System.currentTimeMillis()
            val duration = 60000L
            while (isRecording && recordingProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                recordingProgress = (elapsed.toFloat() / duration).coerceAtMost(1f)
                delay(16)
            }
            isRecording = false
        }
    }

    val surfaceState = when {
        pagerState.currentPage == 0 && postDraftMedia.isNotEmpty() -> "post_creation"
        pagerState.currentPage == 1 && storyDraftMedia != null -> "story_creation"
        pagerState.currentPage == 2 && reelDraftMedia != null -> "reel_creation"
        else -> "camera_shell"
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        SnackbarHost(
            hostState = snackBarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        )

        AnimatedContent(
            targetState = surfaceState,
            transitionSpec = {
                fadeIn(animationSpec = tween(320)) togetherWith fadeOut(animationSpec = tween(220))
            },
            label = "CreationSurfaceTransition"
        ) { targetSurface ->
            when (targetSurface) {
                "post_creation" -> {
                    PostComposerScreen(
                        media = postDraftMedia,
                        accentBlue = accentBlue,
                        caption = captionInput,
                        onCaptionChange = { captionInput = it },
                        location = locationInput,
                        onLocationChange = { locationInput = it },
                        hashtags = hashtagInput,
                        onHashtagsChange = { hashtagInput = it },
                        isPublishing = isPublishingPost,
                        errorMessage = publishError,
                        onBack = { resetPostDraft() },
                        onAddPhotos = {
                            if (postDraftMedia.none { it.isVideo }) {
                                imagePickerLauncher.launch("image/*")
                            } else {
                                publishError = "Video post can include only one video. Remove it first to add photos."
                            }
                        },
                        onPickVideo = { videoPickerLauncher.launch("video/*") },
                        onRemoveMedia = { index ->
                            postDraftMedia = postDraftMedia.filterIndexed { itemIndex, _ -> itemIndex != index }
                            if (postDraftMedia.isEmpty()) {
                                publishError = null
                            }
                        },
                        onPublish = {
                            if (postPublishJob?.isActive != true) {
                                postPublishJob = scope.launch { publishPostDraft() }
                            }
                        },
                        onRetry = {
                            if (postPublishJob?.isActive != true) {
                                postPublishJob = scope.launch { publishPostDraft() }
                            }
                        },
                        onCancelUpload = ::cancelPostUpload
                    )
                }

                "story_creation" -> {
                    StoryEditView(
                        imageUrl = storyDraftMedia?.uri.toString(),
                        filterName = "Original",
                        onBack = { resetStoryDraft() },
                        onFilterChange = { },
                        accentBlue = accentBlue,
                        onStoryCreated = onStoryCreated
                    )
                }

                "reel_creation" -> {
                    ReelComposerScreen(
                        media = reelDraftMedia,
                        accentBlue = accentBlue,
                        caption = reelCaptionInput,
                        onCaptionChange = { reelCaptionInput = it },
                        hashtags = reelHashtagInput,
                        onHashtagsChange = { reelHashtagInput = it },
                        location = reelLocationInput,
                        onLocationChange = { reelLocationInput = it },
                        tagPeople = reelTagPeopleInput,
                        onTagPeopleChange = { reelTagPeopleInput = it },
                        audioName = reelAudioNameInput,
                        onAudioNameChange = { reelAudioNameInput = it },
                        audience = reelAudienceInput,
                        onAudienceChange = { reelAudienceInput = it },
                        aiLabelEnabled = reelAiLabelEnabled,
                        onAiLabelChange = { reelAiLabelEnabled = it },
                        isPublishing = isPublishingReel,
                        publishProgress = reelPublishProgress,
                        publishId = reelPublishId,
                        errorMessage = reelPublishError,
                        onBack = { resetReelDraft() },
                        onReplaceVideo = { reelVideoPickerLauncher.launch("video/*") },
                        onPublish = {
                            if (reelPublishJob?.isActive != true) {
                                reelPublishJob = scope.launch { publishReelDraft() }
                            }
                        },
                        onRetry = {
                            if (reelPublishJob?.isActive != true) {
                                reelPublishJob = scope.launch { publishReelDraft() }
                            }
                        },
                        onCancelUpload = ::cancelReelUpload
                    )
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !isRecording
                        ) { page ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color.Black)))
                                )
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val iconScale by animateFloatAsState(if (isRecording) 0.8f else 1f, label = "iconScale")
                                    Icon(
                                        when (modes[page]) {
                                            "REELS" -> Icons.Rounded.Movie
                                            "STORY" -> Icons.Rounded.History
                                            else -> Icons.Rounded.PhotoCamera
                                        },
                                        null,
                                        tint = Color.White.copy(0.1f),
                                        modifier = Modifier
                                            .size(120.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                    )
                                    Text(
                                        if (modes[page] == "POST") "POST CREATION" else "${modes[page]} REJIMI",
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                    if (modes[page] == "POST") {
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "Select photos or one video, add a caption, then publish.",
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(16.dp)
                                .align(Alignment.TopStart),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.background(Color.Black.copy(0.3f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            val previewTools = listOf(
                                Icons.Default.FlashOn,
                                Icons.Default.Settings,
                                Icons.Default.FlipCameraAndroid
                            )

                            previewTools.forEachIndexed { index, icon ->
                                var isToolVisible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    delay(index * 100L)
                                    isToolVisible = true
                                }
                                AnimatedVisibility(
                                    visible = isToolVisible,
                                    enter = slideInHorizontally { -it } + fadeIn()
                                ) {
                                    IconButton(
                                        onClick = onDismiss,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color.White.copy(0.15f), CircleShape)
                                            .border(0.5.dp, Color.White.copy(0.2f), CircleShape)
                                    ) {
                                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                modes.forEachIndexed { index, mode ->
                                    val isSelected = pagerState.currentPage == index
                                    val alpha by animateFloatAsState(if (isSelected) 1f else 0.5f, label = "alpha")
                                    val scale by animateFloatAsState(if (isSelected) 1.2f else 0.9f, label = "scale")

                                    Text(
                                        text = mode,
                                        color = Color.White.copy(alpha = alpha),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .padding(horizontal = 15.dp)
                                            .graphicsLayer(scaleX = scale, scaleY = scale)
                                            .clickable {
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            }
                                    )
                                }
                            }

                            val isPostMode = pagerState.currentPage == 0
                            val shutterSize by animateDpAsState(if (isRecording) 70.dp else 80.dp, label = "size")
                            val innerSize by animateDpAsState(if (isRecording) 25.dp else 60.dp, label = "innerSize")
                            val cornerRadius by animateDpAsState(if (isRecording) 6.dp else 40.dp, label = "radius")

                            Box(
                                modifier = Modifier
                                    .size(shutterSize)
                                    .pointerInput(isPostMode) {
                                        detectTapGestures(
                                            onTap = {
                                                if (isPostMode) {
                                                    showGallery = true
                                                } else {
                                                    showGallery = true
                                                }
                                            },
                                            onLongPress = {
                                                if (!isPostMode) isRecording = true
                                            },
                                            onPress = {
                                                tryAwaitRelease()
                                                isRecording = false
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = size.minDimension / 2,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                                    )
                                    if (isRecording) {
                                        drawArc(
                                            color = Color.Red,
                                            startAngle = -90f,
                                            sweepAngle = 360f * recordingProgress,
                                            useCenter = false,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(innerSize)
                                        .clip(RoundedCornerShape(cornerRadius))
                                        .background(if (isRecording) Color.Red else Color.White)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .navigationBarsPadding()
                                .padding(start = 40.dp, bottom = 45.dp)
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.5.dp, Color.White.copy(0.5f), RoundedCornerShape(10.dp))
                                .background(Color.DarkGray)
                                .clickable { showGallery = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (pagerState.currentPage == 0) Icons.Rounded.Collections else Icons.Rounded.PhotoLibrary,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showGallery,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (pagerState.currentPage == 0) {
                CreationMediaSourceSheet(
                    accentBlue = accentBlue,
                    title = "Create post",
                    subtitle = if (postDraftMedia.any { it.isVideo }) {
                        "A video post supports one video only. Remove the video first if you want to add photos."
                    } else {
                        "Choose multiple photos or a single video for your post."
                    },
                    photoLabel = "Photos",
                    photoDescription = "Select one or multiple images",
                    videoLabel = "Video",
                    videoDescription = "Select one video for a video post",
                    onDismiss = { showGallery = false },
                    onPickPhotos = {
                        showGallery = false
                        imagePickerLauncher.launch("image/*")
                    },
                    onPickVideo = {
                        showGallery = false
                        videoPickerLauncher.launch("video/*")
                    }
                )
            } else if (pagerState.currentPage == 1) {
                CreationMediaSourceSheet(
                    accentBlue = accentBlue,
                    title = "Create story",
                    subtitle = "Choose a photo or short video for your story.",
                    photoLabel = "Photo story",
                    photoDescription = "Pick an image to share quickly",
                    videoLabel = "Video story",
                    videoDescription = "Pick a short video story",
                    onDismiss = { showGallery = false },
                    onPickPhotos = {
                        showGallery = false
                        storyMediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickVideo = {
                        showGallery = false
                        storyMediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
            } else if (pagerState.currentPage == 2) {
                CreationMediaSourceSheet(
                    accentBlue = accentBlue,
                    title = "Create reel",
                    subtitle = "Choose one video for a vertical reel preview and publish flow.",
                    photoLabel = "Photos",
                    photoDescription = "",
                    videoLabel = "Reel video",
                    videoDescription = "Pick one video for the reel",
                    showPhotos = false,
                    onDismiss = { showGallery = false },
                    onPickPhotos = { },
                    onPickVideo = {
                        showGallery = false
                        reelVideoPickerLauncher.launch("video/*")
                    }
                )
            } else {
                GalleryOverlay(
                    onDismiss = { showGallery = false },
                    onImageSelected = { selectedImage ->
                        showGallery = false
                    },
                    accentBlue = accentBlue
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostComposerScreen(
    media: List<PostDraftMedia>,
    accentBlue: Color,
    caption: String,
    onCaptionChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    hashtags: String,
    onHashtagsChange: (String) -> Unit,
    isPublishing: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onAddPhotos: () -> Unit,
    onPickVideo: () -> Unit,
    onRemoveMedia: (Int) -> Unit,
    onPublish: () -> Unit,
    onRetry: () -> Unit,
    onCancelUpload: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { media.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    val publishEnabled = media.isNotEmpty() && !isPublishing
    val hasVideo = media.any { it.isVideo }
    val imageTransforms = remember { mutableStateMapOf<String, MediaTransformState>() }
    var selectedAspectOption by remember { mutableStateOf(postAspectOptions[1]) }

    fun imageTransformState(item: PostDraftMedia): MediaTransformState {
        return imageTransforms.getOrPut(item.uri.toString()) { MediaTransformState() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Create Post", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        if (hasVideo) "Single video post" else "${media.size} selected media",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }

                TextButton(
                    onClick = onPublish,
                    enabled = publishEnabled
                ) {
                    Text(
                        if (isPublishing) "Publishing..." else "Publish",
                        color = if (publishEnabled) accentBlue else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                if (media.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val item = media[page]
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (item.isVideo) {
                                AndroidView(
                                    factory = { context ->
                                        VideoView(context).apply {
                                            setVideoURI(item.uri)
                                            setOnPreparedListener { mediaPlayer ->
                                                mediaPlayer.isLooping = true
                                                start()
                                            }
                                        }
                                    },
                                    update = { view ->
                                        view.setVideoURI(item.uri)
                                        view.setOnPreparedListener { mediaPlayer ->
                                            mediaPlayer.isLooping = true
                                            view.start()
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                PostAspectFrame(
                                    aspectRatio = selectedAspectOption.ratio,
                                    accentBlue = accentBlue,
                                    borderColor = accentBlue.copy(alpha = 0.9f),
                                    modifier = Modifier.matchParentSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column {
                                        Text("Video post", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(item.displayName, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
                                        if (item.fileSizeLabel.isNotBlank()) {
                                            Text(item.fileSizeLabel, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                        }
                                    }
                                }
                            } else {
                                val transformState = imageTransformState(item)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color.Black)
                                        .border(2.dp, accentBlue.copy(alpha = 0.85f), RoundedCornerShape(28.dp))
                                        .pointerInput(item.uri) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                transformState.scale = (transformState.scale * zoom).coerceIn(1f, 5f)
                                                transformState.offset += pan
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                translationX = transformState.offset.x
                                                translationY = transformState.offset.y
                                                scaleX = transformState.scale
                                                scaleY = transformState.scale
                                            },
                                        contentScale = ContentScale.Crop
                                    )
                                    PostAspectFrame(
                                        aspectRatio = selectedAspectOption.ratio,
                                        accentBlue = accentBlue,
                                        borderColor = Color.White.copy(alpha = 0.88f),
                                        modifier = Modifier.matchParentSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(16.dp)
                                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Column {
                                            Text("Photo post", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(item.displayName, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
                                            if (item.fileSizeLabel.isNotBlank()) {
                                                Text(item.fileSizeLabel, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onRemoveMedia(page) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }

                            if (media.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp)
                                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    media.forEachIndexed { index, _ ->
                                        Box(
                                            modifier = Modifier
                                                .size(if (index == pagerState.currentPage) 9.dp else 7.dp)
                                                .clip(CircleShape)
                                                .background(if (index == pagerState.currentPage) accentBlue else Color.White.copy(alpha = 0.35f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (media.isNotEmpty()) {
                PostAspectSizeSelector(
                    options = postAspectOptions,
                    selected = selectedAspectOption,
                    accentBlue = accentBlue,
                    onSelect = { selectedAspectOption = it }
                )
            }

            if (media.size > 1) {
                LazyRow(
                    modifier = Modifier.padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(count = media.size) { index ->
                        val item = media[index]
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    width = if (pagerState.currentPage == index) 2.dp else 1.dp,
                                    color = if (pagerState.currentPage == index) accentBlue else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    if (pagerState.currentPage != index) {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (item.isVideo) {
                                Icon(
                                    Icons.Default.PlayCircleFilled,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = onAddPhotos,
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Collections, null, tint = accentBlue, modifier = Modifier.size(18.dp))
                            Text("Add Photos", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Surface(
                        onClick = onPickVideo,
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.SmartDisplay, null, tint = accentBlue, modifier = Modifier.size(18.dp))
                            Text("Pick Video", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                PostInputField(
                    value = caption,
                    onValueChange = onCaptionChange,
                    title = "Caption",
                    placeholder = "Write a caption that gives context to your post"
                )

                PostInputField(
                    value = location,
                    onValueChange = onLocationChange,
                    title = "Location",
                    placeholder = "Optional location"
                )

                PostInputField(
                    value = hashtags,
                    onValueChange = onHashtagsChange,
                    title = "Hashtags",
                    placeholder = "#uzbekistan #travel #campus"
                )

                if (isPublishing) {
                    CreationUploadStatusCard(
                        title = "Publishing post",
                        subtitle = "Uploading selected media and publishing it to your feed.",
                        accentBlue = accentBlue,
                        onCancel = onCancelUpload
                    )
                }

                errorMessage?.let { message ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0x33FF4D4F),
                        border = BorderStroke(1.dp, Color(0x55FF4D4F))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF7A7A))
                            Text(
                                text = message,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onRetry) {
                                Text("Retry", color = accentBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostAspectFrame(
    aspectRatio: Float,
    accentBlue: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        val containerRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val frameModifier = if (containerRatio > aspectRatio) {
            Modifier.fillMaxHeight().aspectRatio(aspectRatio)
        } else {
            Modifier.fillMaxWidth().aspectRatio(aspectRatio)
        }

        Box(
            modifier = frameModifier
                .clip(RoundedCornerShape(22.dp))
                .border(2.dp, borderColor, RoundedCornerShape(22.dp))
        )
    }
}

@Composable
private fun PostAspectSizeSelector(
    options: List<PostAspectOption>,
    selected: PostAspectOption,
    accentBlue: Color,
    onSelect: (PostAspectOption) -> Unit
) {
    LazyRow(
        modifier = Modifier.padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(options, key = { it.label }) { option ->
            val isSelected = option == selected
            Surface(
                onClick = { onSelect(option) },
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) accentBlue.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                border = BorderStroke(
                    width = if (isSelected) 1.4.dp else 1.dp,
                    color = if (isSelected) accentBlue else Color.White.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        tint = if (isSelected) accentBlue else Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        option.label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PostInputField(
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    placeholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun CreationUploadStatusCard(
    title: String,
    subtitle: String,
    accentBlue: Color,
    onCancel: (() -> Unit)? = null,
    progress: Float? = null,
    publishId: String? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Uploading", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = accentBlue,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = accentBlue,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (publishId.isNullOrBlank()) subtitle else "$subtitle • ID: $publishId",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            onCancel?.let {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = it) {
                        Text("Cancel upload", color = Color.White.copy(alpha = 0.78f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenReelPublishOverlay(
    progress: Float,
    publishId: String?,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Publishing reel", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (publishId.isNullOrBlank()) "Uploading media and preparing publish..." else "Published • ID: $publishId",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = Color(0xFF00A3FF),
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                    Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = onCancel) {
                Text("Cancel upload", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun CreationMediaSourceSheet(
    accentBlue: Color,
    title: String,
    subtitle: String,
    photoLabel: String,
    photoDescription: String,
    videoLabel: String,
    videoDescription: String,
    showPhotos: Boolean = true,
    showVideo: Boolean = true,
    onDismiss: () -> Unit,
    onPickPhotos: () -> Unit,
    onPickVideo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.92f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", color = accentBlue, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (showPhotos) {
                Surface(
                    onClick = onPickPhotos,
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Collections, null, tint = accentBlue)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(photoLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(photoDescription, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                    }
                }
            }

            if (showVideo) {
                Surface(
                    onClick = onPickVideo,
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.SmartDisplay, null, tint = accentBlue)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(videoLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(videoDescription, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun StoryComposerScreen(
    media: PostDraftMedia?,
    accentBlue: Color,
    caption: String,
    onCaptionChange: (String) -> Unit,
    isPublishing: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onReplaceMedia: () -> Unit,
    onPublish: () -> Unit,
    onRetry: () -> Unit,
    onCancelUpload: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        media?.let { draft ->
            if (draft.isVideo) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setVideoURI(draft.uri)
                            setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = true
                                start()
                            }
                        }
                    },
                    update = { view ->
                        view.setVideoURI(draft.uri)
                        view.setOnPreparedListener { mediaPlayer ->
                            mediaPlayer.isLooping = true
                            view.start()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = draft.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.18f), Color.Transparent, Color.Black.copy(0.72f))))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.28f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }

            Text("Story", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            TextButton(onClick = onPublish, enabled = media != null && !isPublishing) {
                Text(if (isPublishing) "Publishing..." else "Share", color = if (media != null && !isPublishing) accentBlue else Color.Gray, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onReplaceMedia,
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.32f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, tint = accentBlue, modifier = Modifier.size(18.dp))
                    Text("Replace media", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                BasicTextField(
                    value = caption,
                    onValueChange = onCaptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { innerTextField ->
                        if (caption.isBlank()) {
                            Text("Add a quick caption (optional)", color = Color.White.copy(alpha = 0.42f), fontSize = 15.sp)
                        }
                        innerTextField()
                    }
                )
            }

            if (isPublishing) {
                CreationUploadStatusCard(
                    title = "Publishing story",
                    subtitle = "Uploading media and refreshing your story tray.",
                    accentBlue = accentBlue,
                    onCancel = onCancelUpload
                )
            }

            errorMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0x33FF4D4F),
                    border = BorderStroke(1.dp, Color(0x55FF4D4F))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF7A7A))
                        Text(message, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRetry) { Text("Retry", color = accentBlue) }
                    }
                }
            }
        }
    }
}

private enum class ReelComposerStep { Preview, Details }

@Composable
fun ReelComposerScreen(
    media: PostDraftMedia?,
    accentBlue: Color,
    caption: String,
    onCaptionChange: (String) -> Unit,
    hashtags: String,
    onHashtagsChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    tagPeople: String,
    onTagPeopleChange: (String) -> Unit,
    audioName: String,
    onAudioNameChange: (String) -> Unit,
    audience: String,
    onAudienceChange: (String) -> Unit,
    aiLabelEnabled: Boolean,
    onAiLabelChange: (Boolean) -> Unit,
    isPublishing: Boolean,
    publishProgress: Float,
    publishId: String?,
    errorMessage: String?,
    onBack: () -> Unit,
    onReplaceVideo: () -> Unit,
    onPublish: () -> Unit,
    onRetry: () -> Unit,
    onCancelUpload: () -> Unit
) {
    var step by remember { mutableStateOf(ReelComposerStep.Preview) }
    var showTextEditor by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf<StoryTextData?>(null) }
    var texts by remember { mutableStateOf(listOf<StoryTextData>()) }
    var stickers by remember { mutableStateOf(listOf<StoryStickerData>()) }
    var selectedMusic by remember { mutableStateOf<MusicData?>(null) }
    var drawingStrokes by remember { mutableStateOf(listOf<StoryDrawingStroke>()) }
    var currentStroke by remember { mutableStateOf<StoryDrawingStroke?>(null) }
    var isDrawingMode by remember { mutableStateOf(false) }
    var drawingColor by remember { mutableStateOf(accentBlue) }
    var drawingStrokeWidth by remember { mutableFloatStateOf(18f) }
    var showEffectsPicker by remember { mutableStateOf(false) }
    var showMentionPicker by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf<StoryEditorTool?>(null) }
    var isToolsExpanded by remember { mutableStateOf(false) }

    fun closeTransientPanels() {
        showTextEditor = false
        editingText = null
        showEffectsPicker = false
        showMentionPicker = false
    }

    fun activateTool(tool: StoryEditorTool) {
        closeTransientPanels()
        activeTool = tool
        when (tool) {
            StoryEditorTool.Text -> showTextEditor = true
            StoryEditorTool.Sticker -> activeTool = null
            StoryEditorTool.Music -> activeTool = null
            StoryEditorTool.Brush -> isDrawingMode = true
            StoryEditorTool.Effects -> showEffectsPicker = true
            StoryEditorTool.Mention -> showMentionPicker = true
            StoryEditorTool.Download -> activeTool = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (step) {
            ReelComposerStep.Preview -> {
                media?.let { draft ->
                    AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                setVideoURI(draft.uri)
                                setOnPreparedListener { mediaPlayer ->
                                    mediaPlayer.isLooping = true
                                    start()
                                }
                            }
                        },
                        update = { view ->
                            view.setVideoURI(draft.uri)
                            view.setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = true
                                view.start()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.18f), Color.Transparent, Color.Black.copy(0.86f))))
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (showTextEditor || showEffectsPicker || showMentionPicker || isDrawingMode) {
                                    closeTransientPanels()
                                    isDrawingMode = false
                                    activeTool = null
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.28f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("New reel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Preview and edit", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = { step = ReelComposerStep.Details },
                            enabled = media != null && !isPublishing
                        ) {
                            Text("Next", color = if (media != null && !isPublishing) accentBlue else Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Text(
                            "Edit reel",
                            color = Color.White.copy(0.24f),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 18.dp)
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .width(56.dp)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val tools = listOf(
                                StoryEditorTool.Text to Icons.Rounded.TextFields,
                                StoryEditorTool.Sticker to Icons.Rounded.StickyNote2,
                                StoryEditorTool.Music to Icons.Rounded.MusicNote,
                                StoryEditorTool.Brush to Icons.Rounded.Brush,
                                StoryEditorTool.Effects to Icons.Rounded.AutoAwesome,
                                StoryEditorTool.Mention to Icons.Rounded.AlternateEmail,
                                StoryEditorTool.Download to Icons.Rounded.Download
                            )
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                tools.forEach { (tool, icon) ->
                                    GlassToolButton(
                                        icon = icon,
                                        isSelected = activeTool == tool,
                                        isDimmed = false
                                    ) {
                                        when (tool) {
                                            StoryEditorTool.Text -> showTextEditor = true
                                            StoryEditorTool.Sticker -> activeTool = null
                                            StoryEditorTool.Music -> activeTool = null
                                            StoryEditorTool.Brush -> isDrawingMode = !isDrawingMode
                                            StoryEditorTool.Effects -> showEffectsPicker = true
                                            StoryEditorTool.Mention -> showMentionPicker = true
                                            StoryEditorTool.Download -> onReplaceVideo()
                                        }
                                    }
                                }
                            }
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isDrawingMode) {
                                    if (isDrawingMode) {
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                currentStroke = StoryDrawingStroke(
                                                    points = listOf(start),
                                                    color = drawingColor,
                                                    width = drawingStrokeWidth
                                                )
                                            },
                                            onDragEnd = {
                                                currentStroke?.let { stroke ->
                                                    if (stroke.points.size > 1) {
                                                        drawingStrokes = drawingStrokes + stroke
                                                    }
                                                }
                                                currentStroke = null
                                            },
                                            onDragCancel = { currentStroke = null },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentStroke = currentStroke?.let { stroke ->
                                                    stroke.copy(points = stroke.points + change.position)
                                                }
                                            }
                                        )
                                    }
                                }
                        ) {
                            (drawingStrokes + listOfNotNull(currentStroke)).forEach { stroke ->
                                val path = Path()
                                stroke.points.firstOrNull()?.let { start ->
                                    path.moveTo(start.x, start.y)
                                    stroke.points.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
                                    drawPath(path = path, color = stroke.color, style = Stroke(width = stroke.width, cap = StrokeCap.Round))
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            texts.forEach { textData ->
                                DraggableTextItem(
                                    data = textData,
                                    onDragStart = { },
                                    onDrag = { },
                                    onDragEnd = { },
                                    onTransformation = { offset, scale, rotation ->
                                        texts = texts.map { if (it.id == textData.id) it.copy(offset = offset, scale = scale, rotation = rotation) else it }
                                    },
                                    onEdit = { editingText = textData; showTextEditor = true },
                                    isShrinking = false
                                )
                            }
                            stickers.forEach { stickerData ->
                                DraggableStickerItem(
                                    data = stickerData,
                                    onDragStart = { },
                                    onDrag = { },
                                    onDragEnd = { },
                                    onTransformation = { offset, scale, rotation ->
                                        stickers = stickers.map { if (it.id == stickerData.id) it.copy(offset = offset, scale = scale, rotation = rotation) else it }
                                    },
                                    isShrinking = false
                                )
                            }
                            selectedMusic?.let { music ->
                                DraggableMusicWidget(
                                    music = music,
                                    onDragStart = { },
                                    onDrag = { },
                                    onDragEnd = { },
                                    onClose = { selectedMusic = null },
                                    isShrinking = false
                                )
                            }
                        }
                    }
                }
            }

            ReelComposerStep.Details -> {
                val coverBitmap by rememberVideoFrameBitmap(media?.uri)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { step = ReelComposerStep.Preview },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.28f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("New reel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Write details before sharing", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }

                        Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(50)) {
                            Text(
                                text = if (publishId.isNullOrBlank()) "Draft" else "ID: $publishId",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Cover preview", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                            ) {
                                if (coverBitmap != null) {
                                    Image(
                                        bitmap = coverBitmap!!.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (media?.isVideo == true) {
                                    AsyncImage(
                                        model = media.uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
                            }
                        }
                    }

                    PostInputField(
                        value = caption,
                        onValueChange = onCaptionChange,
                        title = "Write a caption",
                        placeholder = "Write a caption and add hashtags..."
                    )

                    PostInputField(
                        value = hashtags,
                        onValueChange = onHashtagsChange,
                        title = "Hashtags",
                        placeholder = "#hashtags"
                    )

                    PostInputField(
                        value = tagPeople,
                        onValueChange = onTagPeopleChange,
                        title = "Tag people",
                        placeholder = "Search or mention friends"
                    )

                    PostInputField(
                        value = location,
                        onValueChange = onLocationChange,
                        title = "Add location",
                        placeholder = "Where was this reel made?"
                    )

                    PostInputField(
                        value = audioName,
                        onValueChange = onAudioNameChange,
                        title = "Rename audio",
                        placeholder = "Original Audio"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Audience", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(audience, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            val next = when (audience) {
                                "Everyone" -> "Followers"
                                "Followers" -> "Close Friends"
                                else -> "Everyone"
                            }
                            onAudienceChange(next)
                        }) {
                            Text("Change", color = accentBlue)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI label", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Mark realistic content generated or edited with AI", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                        Switch(checked = aiLabelEnabled, onCheckedChange = onAiLabelChange)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = onReplaceVideo,
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text("Replace video", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Surface(
                            onClick = onPublish,
                            shape = RoundedCornerShape(18.dp),
                            color = accentBlue,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text(if (isPublishing) "Sharing..." else "Share", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    errorMessage?.let { message ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0x33FF4D4F),
                            border = BorderStroke(1.dp, Color(0x55FF4D4F))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF7A7A))
                                Text(message, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = onRetry) { Text("Retry", color = accentBlue) }
                            }
                        }
                    }
                }
            }
        }

        if (isPublishing) {
            FullscreenReelPublishOverlay(
                progress = publishProgress,
                publishId = publishId,
                onCancel = onCancelUpload
            )
        }

        if (showTextEditor || editingText != null) {
            TextEditorOverlay(
                existingData = editingText,
                onDismiss = {
                    showTextEditor = false
                    editingText = null
                },
                onConfirm = { newText ->
                    if (editingText != null) {
                        texts = texts.map { if (it.id == editingText!!.id) it.copy(text = newText.text, color = newText.color, backgroundColor = newText.backgroundColor) else it }
                    } else {
                        texts = texts + newText
                    }
                    showTextEditor = false
                    editingText = null
                }
            )
        }

        if (showEffectsPicker) {
            EffectsPickerOverlay(
                selectedEffect = StoryEffectPreset("Original", Color.Transparent, 0f, null),
                effects = listOf(
                    StoryEffectPreset("Original", Color.Transparent, 0f, null),
                    StoryEffectPreset("Sunset", Color(0xFFFF7A59), 0.20f, Brush.verticalGradient(listOf(Color.Transparent, Color(0x99FF9A62)))),
                    StoryEffectPreset("Night", Color(0xFF304FFE), 0.22f, Brush.verticalGradient(listOf(Color(0x33000000), Color(0xAA111827)))),
                    StoryEffectPreset("Dream", Color(0xFFE91E63), 0.16f, Brush.radialGradient(listOf(Color(0x55FFFFFF), Color.Transparent))),
                    StoryEffectPreset("Mono", Color.Black, 0.28f, Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000))))
                ),
                onDismiss = { showEffectsPicker = false },
                onEffectSelected = { showEffectsPicker = false }
            )
        }

        if (showMentionPicker) {
            MentionPickerOverlay(
                onDismiss = { showMentionPicker = false },
                onMentionSelected = { mention ->
                    onTagPeopleChange((tagPeople + " $mention").trim())
                    showMentionPicker = false
                },
                accentBlue = accentBlue
            )
        }
    }
}

@Composable
fun StoryEditView(
    imageUrl: String,
    filterName: String,
    onBack: () -> Unit,
    onFilterChange: (Int) -> Unit,
    accentBlue: Color,
    onStoryCreated: () -> Unit = {}
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showTextEditor by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf<StoryTextData?>(null) }
    var texts by remember { mutableStateOf(listOf<StoryTextData>()) }
    var stickers by remember { mutableStateOf(listOf<StoryStickerData>()) }
    var selectedMusic by remember { mutableStateOf<MusicData?>(null) }
    var drawingStrokes by remember { mutableStateOf(listOf<StoryDrawingStroke>()) }
    var currentStroke by remember { mutableStateOf<StoryDrawingStroke?>(null) }
    var isDrawingMode by remember { mutableStateOf(false) }
    var drawingColor by remember { mutableStateOf(accentBlue) }
    var drawingStrokeWidth by remember { mutableFloatStateOf(18f) }
    var showEffectsPicker by remember { mutableStateOf(false) }
    var showMentionPicker by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf<StoryEditorTool?>(null) }
    
    val context = LocalContext.current
    var showStickerPicker by remember { mutableStateOf(false) }
    var showMusicPicker by remember { mutableStateOf(false) }
    var isToolsExpanded by remember { mutableStateOf(false) }

    val density = androidx.compose.ui.platform.LocalDensity.current

    suspend fun exportStory(context: android.content.Context): File? {
        // This is a simplified implementation of flattening the UI into a bitmap
        // In a real app, you'd use a more robust way to capture the Compose view or render it to a Canvas
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 1. Draw Background (Simplified)
        val bgPaint = Paint().apply { color = android.graphics.Color.BLACK }
        canvas.drawRect(0f, 0f, 1080f, 1920f, bgPaint)
        
        // 2. Draw Drawings
        drawingStrokes.forEach { stroke ->
            val paint = Paint().apply {
                color = stroke.color.toArgb()
                strokeWidth = stroke.width
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            val androidPath = Path().apply {
                stroke.points.firstOrNull()?.let { start ->
                    moveTo(start.x, start.y)
                    stroke.points.drop(1).forEach { point ->
                        lineTo(point.x, point.y)
                    }
                }
            }.asAndroidPath()
            canvas.drawPath(androidPath, paint)
        }

        // 3. Draw Stickers
        stickers.forEach { sticker ->
             // In a real implementation, you'd need to load the sticker bitmap
             // For now, we'll just draw a placeholder rectangle for the sticker
             val paint = Paint().apply { color = android.graphics.Color.DKGRAY }
             canvas.drawRect(sticker.offset.x, sticker.offset.y, sticker.offset.x + 300f, sticker.offset.y + 300f, paint)
        }

        // 4. Draw Texts
        texts.forEach { textData ->
            val textPaint = Paint().apply {
                color = textData.color.toArgb()
                textSize = 80f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            // Draw background if exists
            if (textData.backgroundColor != Color.Transparent) {
                val bgPaint = Paint().apply { color = textData.backgroundColor.toArgb() }
                canvas.drawRect(200f, 350f, 880f, 450f, bgPaint)
            }
            canvas.drawText(textData.text, 540f, 400f, textPaint)
        }
        
        // 5. Save to file
        val file = File(context.cacheDir, "exported_story_${System.currentTimeMillis()}.jpg")
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    val effectPresets = remember {
        listOf(
            StoryEffectPreset("Original", Color.Transparent, 0f, null),
            StoryEffectPreset("Sunset", Color(0xFFFF7A59), 0.20f, Brush.verticalGradient(listOf(Color.Transparent, Color(0x99FF9A62)))),
            StoryEffectPreset("Night", Color(0xFF304FFE), 0.22f, Brush.verticalGradient(listOf(Color(0x33000000), Color(0xAA111827)))),
            StoryEffectPreset("Dream", Color(0xFFE91E63), 0.16f, Brush.radialGradient(listOf(Color(0x55FFFFFF), Color.Transparent))),
            StoryEffectPreset("Mono", Color.Black, 0.28f, Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000))))
        )
    }
    var selectedEffect by remember { mutableStateOf(effectPresets.first()) }

    var isDraggingAnything by remember { mutableStateOf(false) }
    var draggedTextId by remember { mutableStateOf<Long?>(null) }
    var draggedStickerId by remember { mutableStateOf<Long?>(null) }
    var currentDragPosition by remember { mutableStateOf(Offset.Zero) }
    var trashLayoutPosition by remember { mutableStateOf(Offset.Zero) }

    val isOverTrash = remember(isDraggingAnything, currentDragPosition, trashLayoutPosition) {
        if (!isDraggingAnything) false
        else (currentDragPosition - trashLayoutPosition).getDistance() < 160f
    }

    fun disableDrawingMode() {
        isDrawingMode = false
        currentStroke = null
        if (activeTool == StoryEditorTool.Brush) activeTool = null
    }

    fun closeTransientPanels() {
        showTextEditor = false
        editingText = null
        showStickerPicker = false
        showMusicPicker = false
        showEffectsPicker = false
        showMentionPicker = false
        disableDrawingMode()
    }

    fun activateTool(tool: StoryEditorTool) {
        closeTransientPanels()
        activeTool = tool
        when (tool) {
            StoryEditorTool.Text -> showTextEditor = true
            StoryEditorTool.Sticker -> showStickerPicker = true
            StoryEditorTool.Music -> showMusicPicker = true
            StoryEditorTool.Brush -> isDrawingMode = true
            StoryEditorTool.Effects -> showEffectsPicker = true
            StoryEditorTool.Mention -> showMentionPicker = true
            StoryEditorTool.Download -> {
                activeTool = null
                scope.launch { snackBarHostState.showSnackbar("Story draft saved to device") }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        SnackbarHost(
            hostState = snackBarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (selectedEffect.alpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(selectedEffect.tint.copy(alpha = selectedEffect.alpha))
                )
            }

            selectedEffect.overlay?.let { brush ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush)
                )
            }
            
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    filterName,
                    color = Color.White.copy(0.3f),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 18.dp)
                    .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = { onFilterChange(-1) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ChevronLeft, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text(filterName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                IconButton(onClick = { onFilterChange(1) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            AnimatedVisibility(
                visible = isDrawingMode,
                enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
            ) {
                DrawingControlsBar(
                    selectedColor = drawingColor,
                    strokeWidth = drawingStrokeWidth,
                    onColorSelected = { drawingColor = it },
                    onStrokeWidthChange = { drawingStrokeWidth = it },
                    onUndo = { if (drawingStrokes.isNotEmpty()) drawingStrokes = drawingStrokes.dropLast(1) },
                    onClear = { drawingStrokes = emptyList() }
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isDrawingMode) {
                        if (isDrawingMode) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    currentStroke = StoryDrawingStroke(
                                        points = listOf(start),
                                        color = drawingColor,
                                        width = drawingStrokeWidth
                                    )
                                },
                                onDragEnd = {
                                    currentStroke?.let { stroke ->
                                        if (stroke.points.size > 1) {
                                            drawingStrokes = drawingStrokes + stroke
                                        }
                                    }
                                    currentStroke = null
                                },
                                onDragCancel = { currentStroke = null },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val nextPoint = change.position
                                    currentStroke = currentStroke?.let { stroke ->
                                        stroke.copy(points = stroke.points + nextPoint)
                                    }
                                }
                            )
                        }
                    }
            ) {
                (drawingStrokes + listOfNotNull(currentStroke)).forEach { stroke ->
                    val path = Path()
                    stroke.points.firstOrNull()?.let { start ->
                        path.moveTo(start.x, start.y)
                        stroke.points.drop(1).forEach { point ->
                            path.lineTo(point.x, point.y)
                        }
                        drawPath(
                            path = path,
                            color = stroke.color,
                            style = Stroke(width = stroke.width, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                texts.forEach { textData ->
                    DraggableTextItem(
                        data = textData,
                        onDragStart = { 
                            isDraggingAnything = true 
                            draggedTextId = textData.id
                        },
                        onDrag = { pos -> currentDragPosition = pos },
                        onDragEnd = {
                            if (isOverTrash) {
                                texts = texts.filter { it.id != textData.id }
                            }
                            isDraggingAnything = false
                            draggedTextId = null
                        },
                        onTransformation = { offset, scale, rotation ->
                            texts = texts.map {
                                if (it.id == textData.id) it.copy(offset = offset, scale = scale, rotation = rotation)
                                else it
                            }
                        },
                        onEdit = { editingText = textData },
                        isShrinking = isOverTrash && draggedTextId == textData.id
                    )
                }

                stickers.forEach { stickerData ->
                    DraggableStickerItem(
                        data = stickerData,
                        onDragStart = {
                            isDraggingAnything = true
                            draggedStickerId = stickerData.id
                        },
                        onDrag = { pos: Offset -> currentDragPosition = pos },
                        onDragEnd = {
                            if (isOverTrash) {
                                stickers = stickers.filter { it.id != stickerData.id }
                            }
                            isDraggingAnything = false
                            draggedStickerId = null
                        },
                        onTransformation = { offset, scale, rotation ->
                            stickers = stickers.map {
                                if (it.id == stickerData.id) it.copy(offset = offset, scale = scale, rotation = rotation)
                                else it
                            }
                        },
                        isShrinking = isOverTrash && draggedStickerId == stickerData.id
                    )
                }

                selectedMusic?.let { music ->
                    DraggableMusicWidget(
                        music = music,
                        onDragStart = { isDraggingAnything = true },
                        onDrag = { pos -> currentDragPosition = pos },
                        onDragEnd = {
                            if (isOverTrash) selectedMusic = null
                            isDraggingAnything = false
                        },
                        onClose = { selectedMusic = null },
                        isShrinking = isOverTrash
                    )
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(0.3f), CircleShape)
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp)
                .width(56.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val tools = listOf(
                StoryEditorTool.Text to Icons.Rounded.TextFields,
                StoryEditorTool.Sticker to Icons.Rounded.StickyNote2,
                StoryEditorTool.Music to Icons.Rounded.MusicNote,
                StoryEditorTool.Brush to Icons.Rounded.Brush,
                StoryEditorTool.Effects to Icons.Rounded.AutoAwesome
            )
            
            val expandedTools = listOf(
                StoryEditorTool.Mention to Icons.Rounded.AlternateEmail,
                StoryEditorTool.Download to Icons.Rounded.Download
            )

            val topExpandedTools = expandedTools.take(1)
            val bottomExpandedTools = expandedTools.drop(1)

            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = isToolsExpanded && !isDrawingMode,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        topExpandedTools.forEach { (tool, icon) ->
                            GlassToolButton(
                                icon = icon,
                                isSelected = activeTool == tool,
                                isDimmed = false
                            ) {
                                activateTool(tool)
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !isDrawingMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        tools.forEach { (tool, icon) ->
                            GlassToolButton(
                                icon = icon,
                                isSelected = activeTool == tool,
                                isDimmed = false
                            ) {
                                if (tool == StoryEditorTool.Brush && isDrawingMode) {
                                    disableDrawingMode()
                                } else {
                                    activateTool(tool)
                                }
                            }
                        }

                        GlassToolButton(
                            icon = if (isToolsExpanded) Icons.Default.Remove else Icons.Default.Add,
                            isSelected = isToolsExpanded,
                            isDimmed = false
                        ) {
                            isToolsExpanded = !isToolsExpanded
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isDrawingMode,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    GlassToolButton(
                        icon = Icons.Rounded.Brush,
                        isSelected = true,
                        isDimmed = false
                    ) {
                        disableDrawingMode()
                    }
                }

                AnimatedVisibility(
                    visible = isToolsExpanded && !isDrawingMode,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        bottomExpandedTools.forEach { (tool, icon) ->
                            GlassToolButton(
                                icon = icon,
                                isSelected = activeTool == tool,
                                isDimmed = false
                            ) {
                                activateTool(tool)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val trashSize by animateDpAsState(if (isOverTrash) 100.dp else 70.dp, label = "trashSize")
            val trashIconSize by animateDpAsState(if (isOverTrash) 40.dp else 28.dp, label = "trashIconSize")

            AnimatedVisibility(
                visible = isDraggingAnything,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { 
                            trashLayoutPosition = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                        }
                        .size(trashSize)
                        .background(
                            if (isOverTrash) Color.Red.copy(0.8f) else Color.White.copy(0.15f),
                            CircleShape
                        )
                        .border(2.dp, if (isOverTrash) Color.Red else Color.White.copy(0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(trashIconSize)
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isPublishing = true
                        val selectedStoryUri = runCatching { Uri.parse(imageUrl) }.getOrNull()
                        if (selectedStoryUri != null && imageUrl.isNotBlank() && imageUrl != "null") {
                            val storyRepository = StoryRepository()
                            val response = storyRepository.createStory(
                                context = context,
                                mediaUri = selectedStoryUri,
                                caption = ""
                            )
                            when (val outcome = response.toUploadOutcome("Failed to publish story")) {
                                is UploadOutcome.Success -> {
                                    snackBarHostState.showSnackbar(outcome.message ?: "Story published successfully!")
                                    onStoryCreated()
                                    closeTransientPanels()
                                    onBack()
                                }
                                is UploadOutcome.Failure -> {
                                    snackBarHostState.showSnackbar(outcome.message)
                                }
                            }
                        } else {
                            snackBarHostState.showSnackbar("Story media is not available")
                        }
                        closeTransientPanels()
                        isPublishing = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(25.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Text(if (isPublishing) "Sharing..." else "Your Story", color = Color.Black, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = Color.Black)
            }
        }

        if (showTextEditor || editingText != null) {
            TextEditorOverlay(
                existingData = editingText,
                onDismiss = { 
                    showTextEditor = false
                    editingText = null
                    if (activeTool == StoryEditorTool.Text) activeTool = null
                },
                onConfirm = { newText ->
                    if (editingText != null) {
                        texts = texts.map { if (it.id == editingText!!.id) it.copy(text = newText.text, color = newText.color, backgroundColor = newText.backgroundColor) else it }
                    } else {
                        texts = texts + newText
                    }
                    showTextEditor = false
                    editingText = null
                    if (activeTool == StoryEditorTool.Text) activeTool = null
                }
            )
        }

        if (showStickerPicker) {
            StickerPickerOverlay(
                onDismiss = {
                    showStickerPicker = false
                    if (activeTool == StoryEditorTool.Sticker) activeTool = null
                },
                onStickerSelected = { stickerUrl: String ->
                    stickers = stickers + StoryStickerData(imageUrl = stickerUrl)
                    showStickerPicker = false
                    if (activeTool == StoryEditorTool.Sticker) activeTool = null
                },
                accentBlue = accentBlue
            )
        }

        if (showMusicPicker) {
            MusicPickerOverlay(
                onDismiss = {
                    showMusicPicker = false
                    if (activeTool == StoryEditorTool.Music) activeTool = null
                },
                onMusicSelected = { music: MusicData ->
                    selectedMusic = music
                    showMusicPicker = false
                    if (activeTool == StoryEditorTool.Music) activeTool = null
                },
                accentBlue = accentBlue
            )
        }

        if (showEffectsPicker) {
            EffectsPickerOverlay(
                selectedEffect = selectedEffect,
                effects = effectPresets,
                onDismiss = {
                    showEffectsPicker = false
                    if (activeTool == StoryEditorTool.Effects) activeTool = null
                },
                onEffectSelected = {
                    selectedEffect = it
                    showEffectsPicker = false
                    if (activeTool == StoryEditorTool.Effects) activeTool = null
                }
            )
        }

        if (showMentionPicker) {
            MentionPickerOverlay(
                onDismiss = {
                    showMentionPicker = false
                    if (activeTool == StoryEditorTool.Mention) activeTool = null
                },
                onMentionSelected = { mention ->
                    texts = texts + StoryTextData(
                        text = mention,
                        color = Color.White,
                        backgroundColor = Color.Black.copy(alpha = 0.35f)
                    )
                    showMentionPicker = false
                    if (activeTool == StoryEditorTool.Mention) activeTool = null
                },
                accentBlue = accentBlue
            )
        }
    }
}

@Composable
fun GlassToolButton(
    icon: ImageVector,
    isSelected: Boolean,
    isDimmed: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (isPressed) 0.85f else if (isSelected) 1.08f else 1f,
        label = "scale"
    )
    val backgroundColor by animateColorAsState(
        when {
            isSelected -> Color.White.copy(0.36f)
            isDimmed -> Color.White.copy(0.08f)
            else -> Color.White.copy(0.2f)
        },
        label = "toolBg"
    )
    val borderColor by animateColorAsState(
        if (isSelected) Color.White.copy(0.7f) else Color.White.copy(0.3f),
        label = "toolBorder"
    )
    val iconTint by animateColorAsState(
        if (isDimmed) Color.White.copy(0.45f) else Color.White,
        label = "toolIcon"
    )
    
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(backgroundColor, CircleShape)
            .border(0.5.dp, borderColor, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun DrawingControlsBar(
    selectedColor: Color,
    strokeWidth: Float,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    val palette = listOf(Color.White, Color.Black, Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta)
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                palette.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selectedColor == color) 2.dp else 1.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Thickness", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = strokeWidth,
                    onValueChange = onStrokeWidthChange,
                    valueRange = 6f..40f,
                    modifier = Modifier.width(140.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = selectedColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Surface(
                    color = selectedColor,
                    shape = CircleShape,
                    modifier = Modifier.size(pxToDp(strokeWidth.coerceIn(10f, 26f)))
                ) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUndo) { Text("Undo", color = Color.White) }
                TextButton(onClick = onClear) { Text("Clear", color = Color.White) }
            }
        }
    }
}

@Composable
fun DraggableTextItem(
    data: StoryTextData,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTransformation: (Offset, Float, Float) -> Unit,
    onEdit: () -> Unit,
    isShrinking: Boolean
) {
    var isDragging by remember { mutableStateOf(false) }
    
    val animatedScale by animateFloatAsState(if (isShrinking) 0.1f else 1f, label = "shrink")
    val animatedAlpha by animateFloatAsState(if (isShrinking) 0f else 1f, label = "alpha")

    Box(
        modifier = Modifier
            .offset(pxToDp(data.offset.x), pxToDp(data.offset.y))
            .onGloballyPositioned { 
                if (isDragging) {
                    onDrag(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
                }
            }
            .graphicsLayer {
                scaleX = data.scale * animatedScale
                scaleY = data.scale * animatedScale
                rotationZ = data.rotation
                alpha = animatedAlpha
            }
            .pointerInput(data.id) {
                detectTransformGestures(
                    onGestureStart = {
                        isDragging = true
                        onDragStart()
                    },
                    onGestureEnd = {
                        isDragging = false
                        onDragEnd()
                    }
                ) { _, pan, zoom, rotationChange ->
                    onTransformation(data.offset + pan, data.scale * zoom, data.rotation + rotationChange)
                }
            }
            .pointerInput(data.id) {
                detectTapGestures(onTap = { onEdit() })
            }
            .background(data.backgroundColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = data.text,
            color = data.color,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TextEditorOverlay(
    existingData: StoryTextData? = null,
    onDismiss: () -> Unit,
    onConfirm: (StoryTextData) -> Unit
) {
    var text by remember { mutableStateOf(existingData?.text ?: "") }
    var selectedColor by remember { mutableStateOf(existingData?.color ?: Color.White) }
    var hasBackground by remember { mutableStateOf(existingData?.backgroundColor != Color.Transparent) }
    
    val colors = listOf(Color.White, Color.Black, Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta, Color.Cyan)

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.9f),
        exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.9f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.7f))
                .clickable { onDismiss() }
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { hasBackground = !hasBackground },
                            modifier = Modifier.background(if (hasBackground) Color.White.copy(0.2f) else Color.Transparent, CircleShape)
                        ) {
                            Icon(Icons.Rounded.TextFormat, null, tint = Color.White)
                        }
                        
                        Spacer(Modifier.width(16.dp))
                        
                        IconButton(onClick = { if (text.isNotBlank()) onConfirm(StoryTextData(text = text, color = selectedColor, backgroundColor = if (hasBackground) selectedColor.copy(0.3f) else Color.Transparent)) }) { 
                            Icon(Icons.Default.Check, null, tint = Color.White)
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(
                            color = selectedColor,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (hasBackground) selectedColor.copy(0.3f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        cursorBrush = SolidColor(selectedColor),
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) Text("Write there...", color = Color.White.copy(0.3f), fontSize = 32.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            innerTextField()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (selectedColor == color) 2.dp else 1.dp, Color.White, CircleShape)
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DraggableStickerItem(
    data: StoryStickerData,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTransformation: (Offset, Float, Float) -> Unit,
    isShrinking: Boolean
) {
    var isDragging by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(if (isShrinking) 0.1f else 1f, label = "shrink")
    val animatedAlpha by animateFloatAsState(if (isShrinking) 0f else 1f, label = "alpha")

    Box(
        modifier = Modifier
            .offset(pxToDp(data.offset.x), pxToDp(data.offset.y))
            .onGloballyPositioned {
                if (isDragging) {
                    onDrag(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
                }
            }
            .graphicsLayer {
                scaleX = data.scale * animatedScale
                scaleY = data.scale * animatedScale
                rotationZ = data.rotation
                alpha = animatedAlpha
            }
            .pointerInput(data.id) {
                detectTransformGestures(
                    onGestureStart = {
                        isDragging = true
                        onDragStart()
                    },
                    onGestureEnd = {
                        isDragging = false
                        onDragEnd()
                    }
                ) { _, pan, zoom, rotationChange ->
                    onTransformation(data.offset + pan, data.scale * zoom, data.rotation + rotationChange)
                }
            }
            .size(120.dp)
    ) {
        AsyncImage(
            model = data.imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun StickerPickerOverlay(
    onDismiss: () -> Unit,
    onStickerSelected: (String) -> Unit,
    accentBlue: Color
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.92f))
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.92f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Text("Stickers", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = accentBlue, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(20) { index ->
                        val url = "https://api.dicebear.com/7.x/bottts/svg?seed=$index"
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(0.05f))
                                .clickable { onStickerSelected(url) },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(60.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MusicPickerOverlay(
    onDismiss: () -> Unit,
    onMusicSelected: (MusicData) -> Unit,
    accentBlue: Color
) {
    val musicList = listOf(
        MusicData("Blinding Lights", "The Weeknd", "https://picsum.photos/seed/m1/100/100"),
        MusicData("Stay", "The Kid LAROI", "https://picsum.photos/seed/m2/100/100"),
        MusicData("Heat Waves", "Glass Animals", "https://picsum.photos/seed/m3/100/100"),
        MusicData("Industry Baby", "Lil Nas X", "https://picsum.photos/seed/m4/100/100")
    )

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.92f))
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.92f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Text("Search Music", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = accentBlue, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(musicList) { music ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(0.06f))
                                .clickable { onMusicSelected(music) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = music.albumArt,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(music.title, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(music.artist, color = Color.Gray, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MusicWidget(music: MusicData, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(0.15f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.2f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = music.albumArt,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(music.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(music.artist, color = Color.White.copy(0.7f), fontSize = 10.sp)
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun DraggableMusicWidget(
    music: MusicData,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    isShrinking: Boolean
) {
    var offset by remember { mutableStateOf(Offset(180f, 280f)) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(if (isShrinking) 0.12f else 1f, label = "musicShrink")
    val animatedAlpha by animateFloatAsState(if (isShrinking) 0f else 1f, label = "musicAlpha")

    Box(
        modifier = Modifier
            .offset(pxToDp(offset.x), pxToDp(offset.y))
            .onGloballyPositioned {
                if (isDragging) {
                    onDrag(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
                }
            }
            .graphicsLayer {
                scaleX = scale * animatedScale
                scaleY = scale * animatedScale
                rotationZ = rotation
                alpha = animatedAlpha
            }
            .pointerInput(Unit) {
                detectTransformGestures(
                    onGestureStart = {
                        isDragging = true
                        onDragStart()
                    },
                    onGestureEnd = {
                        isDragging = false
                        onDragEnd()
                    }
                ) { _, pan, zoom, rotationChange ->
                    offset += pan
                    scale = (scale * zoom).coerceIn(0.7f, 2.4f)
                    rotation += rotationChange
                }
            }
    ) {
        MusicWidget(music = music, onClose = onClose)
    }
}

private fun Context.toPostDraftMedia(uri: Uri): PostDraftMedia? {
    val mimeType = contentResolver.getType(uri) ?: return null
    val fileSizeBytes = resolveCreationFileSizeBytes(uri)
    return PostDraftMedia(
        uri = uri,
        mimeType = mimeType,
        displayName = resolveCreationDisplayName(uri),
        fileSizeLabel = fileSizeBytes.takeIf { it > 0L }?.toCreationReadableSize() ?: "",
        fileSizeBytes = fileSizeBytes,
        isVideo = mimeType.startsWith("video")
    )
}

private fun Context.resolveCreationDisplayName(uri: Uri): String {
    val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            return it.getString(0) ?: "Media"
        }
    }
    return "Media"
}

private fun Context.resolveCreationFileSizeBytes(uri: Uri): Long {
    val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val size = it.getLong(0)
            if (size > 0L) return size
        }
    }
    return 0L
}

private fun Long.toCreationReadableSize(): String {
    if (this <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = this.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return DecimalFormat("#,##0.#").format(size) + " " + units[unitIndex]
}

@Composable
fun pxToDp(px: Float) = with(androidx.compose.ui.platform.LocalDensity.current) { px.toDp() }

@Composable
private fun rememberVideoFrameBitmap(uri: Uri?): State<Bitmap?> {
    val context = LocalContext.current
    val frameState = remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        if (uri == null) {
            frameState.value = null
            return@LaunchedEffect
        }
        frameState.value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    if (uri.scheme == "file") {
                        retriever.setDataSource(uri.path)
                    } else {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                            retriever.setDataSource(fd.fileDescriptor)
                        } ?: return@runCatching null
                    }
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
        }
    }
    return frameState
}

suspend fun PointerInputScope.detectTransformGestures(
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        onGestureStart()

        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val pointersChanged = event.changes.any { it.changedToDown() || it.changedToUp() }

                if (!pointersChanged) {
                    val zoomChange = event.calculateZoom()
                    val rotationChange = event.calculateRotation()
                    val panChange = event.calculatePan()

                    if (!pastTouchSlop) {
                        zoom *= zoomChange
                        rotation += rotationChange
                        pan += panChange

                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                        val zoomMotion = abs(1 - zoom) * centroidSize
                        val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                        val panMotion = pan.getDistance()

                        if (panMotion > touchSlop ||
                            zoomMotion > touchSlop ||
                            rotationMotion > touchSlop
                        ) {
                            pastTouchSlop = true
                        }
                    }

                    if (pastTouchSlop) {
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (rotationChange != 0f ||
                            zoomChange != 1f ||
                            panChange != Offset.Zero
                        ) {
                            onGesture(centroid, panChange, zoomChange, rotationChange)
                        }
                        event.changes.forEach {
                            if (it.position != it.previousPosition) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
        onGestureEnd()
    }
}

@Composable
fun EffectsPickerOverlay(
    selectedEffect: StoryEffectPreset,
    effects: List<StoryEffectPreset>,
    onDismiss: () -> Unit,
    onEffectSelected: (StoryEffectPreset) -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.92f))
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.92f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Text("Effects", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(effects) { effect ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selectedEffect.name == effect.name) Color.White.copy(0.14f) else Color.White.copy(0.06f)
                                )
                                .clickable { onEffectSelected(effect) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(effect.tint.copy(alpha = if (effect.alpha == 0f) 0.15f else effect.alpha))
                                    .border(1.dp, Color.White.copy(0.16f), CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(effect.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Tap an effect to apply it",
                    color = Color.White.copy(0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun MentionPickerOverlay(
    onDismiss: () -> Unit,
    onMentionSelected: (String) -> Unit,
    accentBlue: Color
) {
    val suggestions = listOf("@jahongir", "@aziza", "@stugram", "@creatorhub", "@androiddev")
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var isVisible by remember { mutableStateOf(false) }
    val filtered = remember(query.text) {
        suggestions.filter { it.contains(query.text.trim(), ignoreCase = true) || query.text.isBlank() }
    }
    LaunchedEffect(Unit) { isVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.92f))
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.92f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Text("Mentions", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = accentBlue, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentBlue,
                        unfocusedBorderColor = Color.White.copy(0.18f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { mention ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(0.06f))
                                .clickable { onMentionSelected(mention) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(accentBlue.copy(alpha = 0.16f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.AlternateEmail, null, tint = accentBlue)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(mention, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
}

@Composable
fun GalleryOverlay(
    onDismiss: () -> Unit,
    onImageSelected: (String) -> Unit,
    accentBlue: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.92f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Text("Galereya", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) {
                    Text("Done", color = accentBlue, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(1.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items((1..21).toList()) { index ->
                    val imageUrl = "https://picsum.photos/seed/${index + 40}/300/300"
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onImageSelected(imageUrl) }
                    ) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
