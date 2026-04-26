package com.stugram.app.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.VideoView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stugram.app.core.upload.UploadManager
import com.stugram.app.core.upload.UploadState
import com.stugram.app.core.upload.UploadStatus
import com.stugram.app.core.nativemedia.VoiceWaveformProcessor
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.repository.PostRepository
import com.stugram.app.ui.theme.IosEmojiFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Locale
import kotlin.coroutines.resume

private const val CHAT_SHARE_PREFIX = "[[stugram-share:"
private const val CHAT_SHARE_SUFFIX = "]]"

enum class ChatStructuredType {
    POST, REEL, MUSIC, FILE, LOCATION
}

data class ChatStructuredPayload(
    val type: ChatStructuredType,
    val title: String,
    val subtitle: String? = null,
    val tertiary: String? = null,
    val imageUrl: String? = null,
    val targetId: String? = null
)

data class PendingAttachment(
    val kind: ChatStructuredType? = null,
    val previewTitle: String,
    val previewSubtitle: String? = null,
    val previewTertiary: String? = null,
    val imageUrl: String? = null,
    val uri: Uri? = null,
    val mimeType: String? = null,
    val structuredText: String? = null,
    val messageTypeOverride: String? = null
)

private data class ComposerMusicItem(
    val title: String,
    val artist: String,
    val duration: String
)

private enum class ComposerRecordingKind { VOICE, ROUND_VIDEO }
private enum class ComposerRecordingPhase { HOLDING, LOCKED }
private enum class ComposerActionMode { VOICE, VIDEO }
private enum class ComposerActionState { TEXT, VOICE, VIDEO, RECORDING, SENDING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichChatComposer(
    isDarkMode: Boolean,
    textValue: String,
    onTextValueChange: (String) -> Unit,
    replyPreviewText: String?,
    onCancelReply: () -> Unit,
    onSendText: () -> Unit,
    onSendMedia: suspend (uri: Uri, mimeType: String, messageTypeOverride: String?, replyToMessageId: String?) -> Boolean,
    onSendStructured: suspend (structuredText: String, replyToMessageId: String?) -> Boolean,
    replyToMessageId: String?,
    sendEnabled: Boolean,
    conversationId: String,
    isGroup: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val uploadManager = remember { UploadManager(context) }
    val uploadStates by uploadManager.getUploadStatesForConversation(conversationId).collectAsStateWithLifecycle(initialValue = emptyList())
    val postRepository = remember {
        RetrofitClient.init(context.applicationContext)
        PostRepository()
    }
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glassBorder = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(0.08f)
    val accentBlue = Color(0xFF00A3FF)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showAttachmentSheet by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<PendingAttachment?>(null) }
    var isSendingAttachment by remember { mutableStateOf(false) }
    var isSendTapLocked by remember { mutableStateOf(false) }
    var inlineMessage by remember { mutableStateOf<String?>(null) }
    var recordingKind by remember { mutableStateOf<ComposerRecordingKind?>(null) }
    var recordingPhase by remember { mutableStateOf<ComposerRecordingPhase?>(null) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var recordingDragOffset by remember { mutableFloatStateOf(0f) }
    var activeAudioRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var activeAudioFile by remember { mutableStateOf<File?>(null) }
    var activeVideoRecording by remember { mutableStateOf<Recording?>(null) }
    var activeVideoFile by remember { mutableStateOf<File?>(null) }
    var activePreviewView by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var activeCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var activeVideoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    val waveformBarCount = 18
    val waveformProcessor = remember { VoiceWaveformProcessor(barCount = waveformBarCount) }
    var voiceWaveformLevels by remember { mutableStateOf(FloatArray(waveformBarCount) { 0.12f }) }
    var awaitingVideoFinalize by remember { mutableStateOf(false) }
    var actionMode by rememberSaveable { mutableStateOf(ComposerActionMode.VOICE) }
    val composerActionState = when {
        isSendingAttachment -> ComposerActionState.SENDING
        recordingKind != null -> ComposerActionState.RECORDING
        textValue.isNotBlank() || pendingAttachment != null -> ComposerActionState.TEXT
        actionMode == ComposerActionMode.VIDEO -> ComposerActionState.VIDEO
        else -> ComposerActionState.VOICE
    }
    val feedPosts by produceState(initialValue = emptyList<PostModel>()) {
        value = runCatching { postRepository.getFeed(page = 1, limit = 20).body()?.data.orEmpty() }.getOrDefault(emptyList())
    }

    DisposableEffect(waveformProcessor) {
        onDispose { waveformProcessor.close() }
    }

    LaunchedEffect(recordingKind, recordingPhase, activeAudioRecorder) {
        if (recordingKind == ComposerRecordingKind.VOICE && recordingPhase != null && activeAudioRecorder != null) {
            while (recordingKind == ComposerRecordingKind.VOICE && recordingPhase != null && activeAudioRecorder != null) {
                val amplitude = runCatching { activeAudioRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                voiceWaveformLevels = waveformProcessor.pushAmplitudeSample(amplitude)
                delay(75)
            }
        }
    }

    fun resetRecordingUi() {
        recordingKind = null
        recordingPhase = null
        recordingSeconds = 0
        recordingDragOffset = 0f
        awaitingVideoFinalize = false
        voiceWaveformLevels = FloatArray(waveformBarCount) { 0.12f }
        waveformProcessor.reset()
    }

    fun resetComposerActionMode() {
        actionMode = ComposerActionMode.VOICE
    }

    fun cleanupAudioRecorder(deleteFile: Boolean) {
        runCatching { activeAudioRecorder?.reset() }
        runCatching { activeAudioRecorder?.release() }
        activeAudioRecorder = null
        if (deleteFile) {
            runCatching { activeAudioFile?.delete() }
        }
        activeAudioFile = null
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasPermissions(vararg permissions: String): Boolean = permissions.all(::hasPermission)

    fun startVoiceRecording(initialPhase: ComposerRecordingPhase) {
        runCatching {
            val outputFile = context.createChatCacheFile(prefix = "voice_note_", extension = ".m4a")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setMaxDuration(60_000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            activeAudioFile = outputFile
            activeAudioRecorder = recorder
            recordingKind = ComposerRecordingKind.VOICE
            recordingPhase = initialPhase
            recordingSeconds = 0
            recordingDragOffset = 0f
            voiceWaveformLevels = FloatArray(waveformBarCount) { 0.12f }
            waveformProcessor.reset()
        }.onFailure {
            cleanupAudioRecorder(deleteFile = true)
            inlineMessage = "Voice recording could not start on this device."
            resetComposerActionMode()
        }
    }

    fun startRoundVideoRecording(initialPhase: ComposerRecordingPhase) {
        recordingKind = ComposerRecordingKind.ROUND_VIDEO
        recordingPhase = initialPhase
        recordingSeconds = 0
        recordingDragOffset = 0f
    }

    suspend fun finishAudioRecording(send: Boolean) {
        val recordedFile = activeAudioFile
        val outputUri = recordedFile?.let(Uri::fromFile)
        val mimeType = "audio/mp4"
        val recorder = activeAudioRecorder
        activeAudioRecorder = null
        activeAudioFile = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        resetRecordingUi()

        if (!send || outputUri == null || recordedFile == null || !recordedFile.exists()) {
            runCatching { recordedFile?.delete() }
            resetComposerActionMode()
            return
        }

        val success = onSendMedia(outputUri, mimeType, "voice", replyToMessageId)
        if (!success) {
            uploadManager.enqueueMediaUpload(
                conversationId = conversationId,
                uri = outputUri,
                mimeType = mimeType,
                isGroup = isGroup,
                messageTypeOverride = "voice",
                replyToMessageId = replyToMessageId,
                tempFile = recordedFile
            )
        } else {
            runCatching { recordedFile.delete() }
        }
        resetComposerActionMode()
    }

    suspend fun stopRoundVideoRecording(send: Boolean) {
        val recording = activeVideoRecording
        val recordedFile = activeVideoFile
        val outputUri = recordedFile?.let(Uri::fromFile)

        if (recording == null || recordedFile == null) {
            resetRecordingUi()
            resetComposerActionMode()
            return
        }

        awaitingVideoFinalize = true
        val shouldSend = send
        suspendCancellableCoroutine<Unit> { continuation ->
            recording.stop()
            activeVideoRecording = null
            scope.launch {
                repeat(20) {
                    delay(150)
                    if (!awaitingVideoFinalize) {
                        if (continuation.isActive) continuation.resume(Unit)
                        return@launch
                    }
                }
                awaitingVideoFinalize = false
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        resetRecordingUi()
        runCatching { activeCameraProvider?.unbindAll() }
        activeCameraProvider = null
        activePreviewView = null

        if (!shouldSend || outputUri == null || !recordedFile.exists()) {
            runCatching { recordedFile.delete() }
            activeVideoFile = null
            resetComposerActionMode()
            return
        }

        val success = onSendMedia(outputUri, "video/mp4", "round_video", replyToMessageId)
        if (!success) {
            uploadManager.enqueueMediaUpload(
                conversationId = conversationId,
                uri = outputUri,
                mimeType = "video/mp4",
                isGroup = isGroup,
                messageTypeOverride = "round_video",
                replyToMessageId = replyToMessageId,
                tempFile = recordedFile
            )
        } else {
            runCatching { recordedFile.delete() }
        }
        activeVideoFile = null
        resetComposerActionMode()
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            context.persistReadPermission(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "image/*"
            pendingAttachment = PendingAttachment(
                previewTitle = if (mimeType.startsWith("video")) "Video ready to send" else "Photo ready to send",
                previewSubtitle = context.resolveDisplayName(uri),
                previewTertiary = mimeType,
                uri = uri,
                mimeType = mimeType,
                imageUrl = uri.toString(),
                messageTypeOverride = if (mimeType.startsWith("video")) "video" else "image"
            )
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.persistReadPermission(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            pendingAttachment = PendingAttachment(
                kind = ChatStructuredType.FILE,
                previewTitle = context.resolveDisplayName(uri),
                previewSubtitle = mimeType.substringAfterLast('/').uppercase(Locale.getDefault()),
                previewTertiary = context.resolveFileSize(uri),
                uri = uri,
                mimeType = mimeType,
                messageTypeOverride = "file"
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAttachment = buildLocationAttachment(context)
            if (pendingAttachment == null) inlineMessage = "Location unavailable on this device right now."
        } else {
            inlineMessage = "Location permission is required to share your location."
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording(ComposerRecordingPhase.LOCKED)
        } else {
            inlineMessage = "Microphone permission is required for voice messages."
            resetComposerActionMode()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.CAMERA] == true && result[Manifest.permission.RECORD_AUDIO] == true
        if (granted) {
            startRoundVideoRecording(ComposerRecordingPhase.LOCKED)
        } else {
            inlineMessage = "Camera and microphone permissions are required for round video."
            resetComposerActionMode()
        }
    }

    LaunchedEffect(recordingKind, activePreviewView) {
        if (recordingKind == ComposerRecordingKind.ROUND_VIDEO && activePreviewView != null && activeVideoRecording == null) {
            runCatching {
                val cameraProvider = context.awaitCameraProvider()
                val preview = Preview.Builder().build().also { previewUseCase ->
                    previewUseCase.surfaceProvider = activePreviewView?.surfaceProvider
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.SD
                        )
                    )
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                val outputFile = context.createChatCacheFile(prefix = "round_video_", extension = ".mp4")

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    videoCapture
                )
                activeCameraProvider = cameraProvider
                activeVideoCapture = videoCapture
                activeVideoFile = outputFile
                activeVideoRecording = videoCapture.output
                    .prepareRecording(context, FileOutputOptions.Builder(outputFile).build())
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            awaitingVideoFinalize = false
                            if (event.hasError()) {
                                inlineMessage = "Round video could not be finished on this device."
                            }
                        }
                    }
            }.onFailure {
                inlineMessage = "Round video recording is unavailable right now."
                runCatching { activeVideoFile?.delete() }
                runCatching { activeCameraProvider?.unbindAll() }
                activeCameraProvider = null
                activeVideoFile = null
                activeVideoRecording = null
                activeVideoCapture = null
                activePreviewView = null
                resetRecordingUi()
                resetComposerActionMode()
            }
        }
    }

    LaunchedEffect(recordingKind, recordingPhase) {
        if (recordingKind != null && recordingPhase != null) {
            while (recordingKind != null && recordingPhase != null && recordingSeconds < 60) {
                delay(1000)
                recordingSeconds += 1
            }
            if (recordingSeconds >= 60) {
                if (recordingKind == ComposerRecordingKind.VOICE) {
                    finishAudioRecording(send = true)
                } else {
                    stopRoundVideoRecording(send = true)
                }
            }
        }
    }

    LaunchedEffect(composerActionState, textValue, recordingKind, pendingAttachment, isSendingAttachment, showAttachmentSheet) {
        if (
            actionMode == ComposerActionMode.VIDEO &&
            textValue.isBlank() &&
            recordingKind == null &&
            pendingAttachment == null &&
            !isSendingAttachment &&
            !showAttachmentSheet
        ) {
            delay(900)
            if (
                actionMode == ComposerActionMode.VIDEO &&
                textValue.isBlank() &&
                recordingKind == null &&
                pendingAttachment == null &&
                !isSendingAttachment &&
                !showAttachmentSheet
            ) {
                resetComposerActionMode()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cleanupAudioRecorder(deleteFile = true)
            runCatching { activeVideoRecording?.close() }
            runCatching { activeVideoFile?.delete() }
            runCatching { activeCameraProvider?.unbindAll() }
            activeCameraProvider = null
            activeVideoRecording = null
            activeVideoFile = null
            activeVideoCapture = null
            activePreviewView = null
        }
    }

    suspend fun sendPendingAttachment() {
        val current = pendingAttachment ?: return
        isSendingAttachment = true
        val success = when {
            current.uri != null && current.mimeType != null ->
                onSendMedia(current.uri, current.mimeType, current.messageTypeOverride, replyToMessageId)
            !current.structuredText.isNullOrBlank() ->
                onSendStructured(current.structuredText, replyToMessageId)
            else -> false
        }
        isSendingAttachment = false
        if (success) {
            pendingAttachment = null
            inlineMessage = null
            resetComposerActionMode()
        } else {
            if (current.uri != null && current.mimeType != null) {
                uploadManager.enqueueMediaUpload(
                    conversationId = conversationId,
                    uri = current.uri,
                    mimeType = current.mimeType,
                    isGroup = isGroup,
                    messageTypeOverride = current.messageTypeOverride,
                    replyToMessageId = replyToMessageId
                )
                pendingAttachment = null
                inlineMessage = null
                resetComposerActionMode()
            } else {
                inlineMessage = "Unable to send this item right now. Please try again."
            }
        }
    }

    Column(modifier = modifier.animateContentSize()) {
        AnimatedVisibility(
            visible = uploadStates.any { it.status != UploadStatus.SUCCESS },
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                uploadStates.filter { it.status != UploadStatus.SUCCESS }.forEach { state ->
                    UploadProgressRow(
                        state = state,
                        isDarkMode = isDarkMode,
                        onCancel = { uploadManager.cancelUpload(state.id) },
                        onRetry = { uploadManager.retryUpload(state.id) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = pendingAttachment != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            PendingAttachmentCard(
                pendingAttachment = pendingAttachment,
                isDarkMode = isDarkMode,
                isSending = isSendingAttachment,
                onCancel = { pendingAttachment = null },
                onSend = { scope.launch { sendPendingAttachment() } }
            )
        }

        AnimatedVisibility(
            visible = !replyPreviewText.isNullOrBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ReplyPreviewBar(
                text = replyPreviewText ?: "",
                isDarkMode = isDarkMode,
                onCancel = onCancelReply
            )
        }

        AnimatedVisibility(
            visible = !inlineMessage.isNullOrBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.04f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = inlineMessage ?: "",
                    color = contentColor.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isRecording = recordingKind != null
            val composerBackground by animateColorAsState(
                targetValue = when {
                    isRecording && isDarkMode -> Color(0xFF1A1214)
                    isRecording -> Color(0xFFFFF3F3)
                    isDarkMode -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Black.copy(alpha = 0.06f)
                },
                animationSpec = tween(180),
                label = "composer_background"
            )
            val composerBorderColor by animateColorAsState(
                targetValue = when {
                    isRecording && isDarkMode -> Color(0xFFFF5A5F).copy(alpha = 0.32f)
                    isRecording -> Color(0xFFFF5A5F).copy(alpha = 0.26f)
                    else -> glassBorder
                },
                animationSpec = tween(180),
                label = "composer_border"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(composerBackground)
                    .border(0.5.dp, composerBorderColor, RoundedCornerShape(26.dp))
                    .animateContentSize(animationSpec = tween(180))
                    .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            ) {
                AnimatedContent(
                    targetState = recordingKind != null,
                    transitionSpec = {
                        (fadeIn(tween(140)) + scaleIn(initialScale = 0.98f, animationSpec = tween(180))) togetherWith
                            (fadeOut(tween(110)) + scaleOut(targetScale = 0.98f, animationSpec = tween(110)))
                    },
                    label = "composer_inner_content"
                ) { recording ->
                    if (recording) {
                        RecordingInlineInputContent(
                            kind = recordingKind,
                            phase = recordingPhase,
                            seconds = recordingSeconds,
                            dragOffset = recordingDragOffset,
                            waveformLevels = voiceWaveformLevels,
                            isDarkMode = isDarkMode,
                            onCancel = {
                                scope.launch {
                                    if (recordingKind == ComposerRecordingKind.VOICE) {
                                        finishAudioRecording(send = false)
                                    } else {
                                        stopRoundVideoRecording(send = false)
                                    }
                                    resetComposerActionMode()
                                }
                            },
                            onSend = {
                                scope.launch {
                                    if (recordingKind == ComposerRecordingKind.VOICE) {
                                        finishAudioRecording(send = true)
                                    } else {
                                        stopRoundVideoRecording(send = true)
                                    }
                                    resetComposerActionMode()
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            IconButton(
                                onClick = { showAttachmentSheet = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, null, tint = contentColor.copy(alpha = 0.78f), modifier = Modifier.size(22.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val inputTextStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = contentColor,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = IosEmojiFont
                                )
                                if (textValue.isEmpty()) {
                                    Text(text = "Message", style = inputTextStyle.copy(color = contentColor.copy(0.45f)))
                                }
                                BasicTextField(
                                    value = textValue,
                                    onValueChange = {
                                        onTextValueChange(it)
                                        if (it.isEmpty()) {
                                            resetComposerActionMode()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = inputTextStyle,
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accentBlue),
                                    maxLines = 4
                                )
                            }
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = composerActionState,
                transitionSpec = {
                    (fadeIn(tween(120)) + scaleIn(initialScale = 0.88f, animationSpec = tween(150))) togetherWith
                        (fadeOut(tween(100)) + scaleOut(targetScale = 1.04f, animationSpec = tween(100)))
                },
                label = "composer_action"
            ) { state ->
                if (state == ComposerActionState.TEXT || state == ComposerActionState.SENDING) {
                    IconButton(
                        onClick = {
                            if (pendingAttachment != null) {
                                scope.launch { sendPendingAttachment() }
                            } else if (!isSendTapLocked) {
                                isSendTapLocked = true
                                onSendText()
                                scope.launch {
                                    delay(450)
                                    isSendTapLocked = false
                                }
                            }
                        },
                        enabled = sendEnabled && !isSendingAttachment && !isSendTapLocked,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(accentBlue)
                    ) {
                        if (isSendingAttachment) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
                        }
                        }
                } else {
                    AnimatedContent(
                        targetState = actionMode,
                        transitionSpec = {
                            (fadeIn(tween(120)) + scaleIn(initialScale = 0.88f, animationSpec = tween(150))) togetherWith
                                (fadeOut(tween(100)) + scaleOut(targetScale = 1.03f, animationSpec = tween(100)))
                            },
                            label = "composer_voice_video_icon"
                        ) { mode ->
                            HoldToRecordButton(
                                icon = if (mode == ComposerActionMode.VOICE) Icons.Default.Mic else Icons.Default.VideoCall,
                                tint = if (mode == ComposerActionMode.VOICE) Color(0xFFFF5A5F) else accentBlue,
                                background = if (isDarkMode) Color.White.copy(0.1f) else Color.Black.copy(0.05f),
                                onTap = {
                                    if (mode == ComposerActionMode.VOICE) {
                                        actionMode = ComposerActionMode.VIDEO
                                    } else {
                                        if (hasPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) {
                                            startRoundVideoRecording(ComposerRecordingPhase.LOCKED)
                                        } else {
                                            cameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                                        }
                                    }
                                },
                                onStart = {
                                    if (mode == ComposerActionMode.VOICE) {
                                        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                                            startVoiceRecording(ComposerRecordingPhase.HOLDING)
                                        } else {
                                            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    } else {
                                        if (hasPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) {
                                            startRoundVideoRecording(ComposerRecordingPhase.HOLDING)
                                        } else {
                                            cameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                                        }
                                    }
                                },
                                onLock = {
                                    if (mode == ComposerActionMode.VOICE) {
                                        recordingKind = ComposerRecordingKind.VOICE
                                    } else {
                                        recordingKind = ComposerRecordingKind.ROUND_VIDEO
                                    }
                                    recordingPhase = ComposerRecordingPhase.LOCKED
                                },
                                onRelease = {
                                    if (recordingKind == ComposerRecordingKind.VOICE && recordingPhase == ComposerRecordingPhase.HOLDING) {
                                        scope.launch {
                                            finishAudioRecording(send = true)
                                            resetComposerActionMode()
                                        }
                                    } else if (recordingKind == ComposerRecordingKind.ROUND_VIDEO && recordingPhase == ComposerRecordingPhase.HOLDING) {
                                        scope.launch {
                                            stopRoundVideoRecording(send = true)
                                            resetComposerActionMode()
                                        }
                                    }
                                },
                                onCancelGesture = {
                                    if (recordingKind == ComposerRecordingKind.VOICE) {
                                        scope.launch {
                                            finishAudioRecording(send = false)
                                            resetComposerActionMode()
                                        }
                                    } else if (recordingKind == ComposerRecordingKind.ROUND_VIDEO) {
                                        scope.launch {
                                            stopRoundVideoRecording(send = false)
                                            resetComposerActionMode()
                                        }
                                    } else {
                                        resetComposerActionMode()
                                    }
                                },
                                onDrag = { drag -> recordingDragOffset = drag }
                            )
                        }
                    }
                }
            }
        }

    if (recordingKind == ComposerRecordingKind.ROUND_VIDEO) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            RoundVideoRecordingOverlay(
                isDarkMode = isDarkMode,
                seconds = recordingSeconds,
                onPreviewReady = { previewView -> activePreviewView = previewView }
            )
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = sheetState,
            containerColor = if (isDarkMode) Color(0xFF101214) else Color.White,
            tonalElevation = 0.dp
        ) {
            AttachmentSheet(
                isDarkMode = isDarkMode,
                posts = feedPosts.filter { post -> post.media.none { it.type == "video" } },
                reels = feedPosts.filter { post -> post.media.any { it.type == "video" } },
                onDismiss = { showAttachmentSheet = false },
                onGallery = {
                    showAttachmentSheet = false
                    galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onFile = {
                    showAttachmentSheet = false
                    filePicker.launch(
                        arrayOf(
                            "application/pdf",
                            "text/plain",
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    )
                },
                onLocation = {
                    showAttachmentSheet = false
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                onPickPost = { post ->
                    pendingAttachment = PendingAttachment(
                        kind = ChatStructuredType.POST,
                        previewTitle = post.author.fullName,
                        previewSubtitle = post.caption.takeIf { it.isNotBlank() } ?: "Shared a post",
                        previewTertiary = "@${post.author.username}",
                        imageUrl = post.media.firstOrNull()?.url,
                        structuredText = buildStructuredPayload(
                            ChatStructuredPayload(
                                type = ChatStructuredType.POST,
                                title = post.author.fullName,
                                subtitle = post.caption.takeIf { it.isNotBlank() } ?: "Shared a post",
                                tertiary = "@${post.author.username}",
                                imageUrl = post.media.firstOrNull()?.url,
                                targetId = post.id
                            )
                        )
                    )
                    showAttachmentSheet = false
                },
                onPickReel = { post ->
                    pendingAttachment = PendingAttachment(
                        kind = ChatStructuredType.REEL,
                        previewTitle = post.author.fullName,
                        previewSubtitle = post.caption.takeIf { it.isNotBlank() } ?: "Shared a reel",
                        previewTertiary = "@${post.author.username}",
                        imageUrl = post.media.firstOrNull()?.url,
                        structuredText = buildStructuredPayload(
                            ChatStructuredPayload(
                                type = ChatStructuredType.REEL,
                                title = post.author.fullName,
                                subtitle = post.caption.takeIf { it.isNotBlank() } ?: "Shared a reel",
                                tertiary = "@${post.author.username}",
                                imageUrl = post.media.firstOrNull()?.url,
                                targetId = post.id
                            )
                        )
                    )
                    showAttachmentSheet = false
                },
                onPickMusic = { music ->
                    pendingAttachment = PendingAttachment(
                        kind = ChatStructuredType.MUSIC,
                        previewTitle = music.title,
                        previewSubtitle = music.artist,
                        previewTertiary = music.duration,
                        structuredText = buildStructuredPayload(
                            ChatStructuredPayload(
                                type = ChatStructuredType.MUSIC,
                                title = music.title,
                                subtitle = music.artist,
                                tertiary = music.duration
                            )
                        )
                    )
                    showAttachmentSheet = false
                }
            )
        }
    }
}

@Composable
private fun HoldToRecordButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    onTap: () -> Unit,
    onStart: () -> Unit,
    onLock: () -> Unit,
    onRelease: () -> Unit,
    onCancelGesture: () -> Unit,
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var didStartRecording = false
                    var cancelTriggered = false
                    var locked = false
                    val handler = Handler(Looper.getMainLooper())
                    val startRunnable = Runnable {
                        didStartRecording = true
                        onStart()
                    }
                    handler.postDelayed(startRunnable, 50)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val dragX = change.position.x - down.position.x
                        val dragY = change.position.y - down.position.y
                        onDrag(dragX)
                        if (!locked && dragY < -120f && didStartRecording) {
                            locked = true
                            onLock()
                        }
                        if (!cancelTriggered && dragX < -120f && didStartRecording) {
                            cancelTriggered = true
                            onCancelGesture()
                            onDrag(0f)
                            handler.removeCallbacks(startRunnable)
                            break
                        }
                        if (change.changedToUp()) {
                            handler.removeCallbacks(startRunnable)
                            if (!didStartRecording) {
                                onTap()
                            } else if (!locked && !cancelTriggered) {
                                onRelease()
                            }
                            onDrag(0f)
                            break
                        }
                        if (change.positionChange() == androidx.compose.ui.geometry.Offset.Zero && !change.pressed) {
                            handler.removeCallbacks(startRunnable)
                            onCancelGesture()
                            onDrag(0f)
                            break
                        }
                    }
                    handler.removeCallbacks(startRunnable)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun RecordingInlineInputContent(
    kind: ComposerRecordingKind?,
    phase: ComposerRecordingPhase?,
    seconds: Int,
    dragOffset: Float,
    waveformLevels: FloatArray,
    isDarkMode: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val waveformColor = if (isDarkMode) Color(0xFF9CCBFF) else Color(0xFF4FA7FF)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.Close,
                null,
                tint = contentColor.copy(alpha = if (phase == ComposerRecordingPhase.LOCKED) 0.82f else 0.66f)
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                contentAlignment = Alignment.Center
            ) {
                if (kind == ComposerRecordingKind.VOICE) {
                    VoiceWaveformBars(levels = waveformLevels, barColor = waveformColor)
                } else {
                    RecordingActivityBars(isDarkMode = isDarkMode, barColor = waveformColor)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatSeconds(seconds),
                color = contentColor.copy(alpha = 0.72f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onSend, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                null,
                tint = if (phase == ComposerRecordingPhase.LOCKED) Color(0xFF00A3FF) else contentColor.copy(alpha = 0.54f)
            )
        }
    }
}

@Composable
private fun RecordingStateBar(
    kind: ComposerRecordingKind?,
    phase: ComposerRecordingPhase?,
    seconds: Int,
    dragOffset: Float,
    waveformLevels: FloatArray? = null,
    isDarkMode: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val bg = if (isDarkMode) Color(0xFF1B1113) else Color(0xFFFFF0F1)
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val waveformColor = if (isDarkMode) Color(0xFF9CCBFF) else Color(0xFF4FA7FF)
    val pulse = rememberInfiniteTransition(label = "recording_pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "recording_pulse_alpha"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = bg,
        border = BorderStroke(0.5.dp, Color(0xFFFF5A5F).copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5A5F).copy(alpha = pulseAlpha))
            )
            Spacer(Modifier.width(10.dp))
            if (kind == ComposerRecordingKind.VOICE && waveformLevels != null) {
                VoiceWaveformBars(levels = waveformLevels, barColor = waveformColor)
            } else {
                RecordingActivityBars(isDarkMode = isDarkMode, barColor = waveformColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (kind == ComposerRecordingKind.VOICE) "Recording voice message" else "Recording round video",
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${formatSeconds(seconds)}${if (phase == ComposerRecordingPhase.LOCKED) "  •  Locked" else "  •  Slide up to lock"}",
                    color = contentColor.copy(alpha = 0.66f),
                    fontSize = 12.sp
                )
            }
            if (phase == ComposerRecordingPhase.LOCKED) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, null, tint = contentColor.copy(alpha = 0.75f))
                }
                IconButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF00A3FF))
                }
            } else {
                Icon(Icons.Default.Lock, null, tint = contentColor.copy(alpha = if (dragOffset < -80f) 1f else 0.55f))
            }
        }
    }
}

@Composable
private fun RecordingActivityBars(isDarkMode: Boolean, barColor: Color) {
    val transition = rememberInfiniteTransition(label = "recording_activity")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = index * 100),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "recording_activity_$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((8 + index * 2).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor.copy(alpha = alpha * 0.58f))
            )
        }
    }
}

@Composable
private fun VoiceWaveformBars(levels: FloatArray, barColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        levels.forEachIndexed { index, level ->
            val animatedLevel by animateFloatAsState(
                targetValue = level.coerceIn(0.08f, 1f),
                animationSpec = tween(durationMillis = 95),
                label = "voice_waveform_bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((7f + (animatedLevel * 18f)).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor.copy(alpha = 0.82f))
            )
        }
    }
}

@Composable
private fun ReplyPreviewBar(text: String, isDarkMode: Boolean, onCancel: () -> Unit) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.04f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF00A3FF))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = contentColor.copy(alpha = 0.75f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = contentColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PendingAttachmentCard(
    pendingAttachment: PendingAttachment?,
    isDarkMode: Boolean,
    isSending: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val attachment = pendingAttachment ?: return
    val contentColor = if (isDarkMode) Color.White else Color.Black
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.04f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!attachment.imageUrl.isNullOrBlank() && attachment.uri != null) {
                AsyncImage(
                    model = attachment.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                ShareCardBadge(type = attachment.kind, isDarkMode = isDarkMode)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.previewTitle, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!attachment.previewSubtitle.isNullOrBlank()) {
                    Text(attachment.previewSubtitle, color = contentColor.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!attachment.previewTertiary.isNullOrBlank()) {
                    Text(attachment.previewTertiary, color = Color(0xFF00A3FF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, null, tint = contentColor.copy(alpha = 0.75f))
            }
            IconButton(onClick = onSend, enabled = !isSending, modifier = Modifier.size(36.dp)) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF00A3FF))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF00A3FF))
                }
            }
        }
    }
}

@Composable
private fun ShareCardBadge(type: ChatStructuredType?, isDarkMode: Boolean) {
    val bg = if (isDarkMode) Color.White.copy(0.1f) else Color.Black.copy(0.06f)
    val icon = when (type) {
        ChatStructuredType.POST -> Icons.Default.Image
        ChatStructuredType.REEL -> Icons.Default.SmartDisplay
        ChatStructuredType.MUSIC -> Icons.Default.MusicNote
        ChatStructuredType.FILE -> Icons.Default.InsertDriveFile
        ChatStructuredType.LOCATION -> Icons.Default.LocationOn
        null -> Icons.Default.Description
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color(0xFF00A3FF))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    isDarkMode: Boolean,
    posts: List<PostModel>,
    reels: List<PostModel>,
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onPickPost: (PostModel) -> Unit,
    onPickReel: (PostModel) -> Unit,
    onPickMusic: (ComposerMusicItem) -> Unit,
    onFile: () -> Unit,
    onLocation: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val curatedMusic = remember {
        listOf(
            ComposerMusicItem("Golden Hour", "JVKE", "3:29"),
            ComposerMusicItem("Daylight", "David Kushner", "3:33"),
            ComposerMusicItem("Lose Control", "Teddy Swims", "3:31")
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(contentColor.copy(alpha = 0.18f))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(18.dp))
        Text("Share", color = contentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AttachmentActionTile(Icons.Default.PhotoLibrary, "Gallery", isDarkMode, Modifier.weight(1f), onGallery)
            AttachmentActionTile(Icons.Default.Image, "Post", isDarkMode, Modifier.weight(1f)) { }
            AttachmentActionTile(Icons.Default.SmartDisplay, "Reels", isDarkMode, Modifier.weight(1f)) { }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AttachmentActionTile(Icons.Default.MusicNote, "Music", isDarkMode, Modifier.weight(1f)) { }
            AttachmentActionTile(Icons.Default.InsertDriveFile, "File", isDarkMode, Modifier.weight(1f), onFile)
            AttachmentActionTile(Icons.Default.LocationOn, "Location", isDarkMode, Modifier.weight(1f), onLocation)
        }
        Spacer(Modifier.height(18.dp))
        Text("Posts", color = contentColor.copy(alpha = 0.72f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(posts.take(6)) { post ->
                ShareGridCard(post = post, isReel = false, onClick = { onPickPost(post) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Reels", color = contentColor.copy(alpha = 0.72f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reels.take(6)) { reel ->
                ShareGridCard(post = reel, isReel = true, onClick = { onPickReel(reel) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Music", color = contentColor.copy(alpha = 0.72f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.height(180.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(curatedMusic.size) { index ->
                val music = curatedMusic[index]
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickMusic(music) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isDarkMode) Color.White.copy(0.06f) else Color.Black.copy(0.03f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF00A3FF).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayCircleFilled, null, tint = Color(0xFF00A3FF))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(music.title, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(music.artist, color = contentColor.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                        Text(music.duration, color = contentColor.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.04f)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(20.dp),
            color = bg,
            border = BorderStroke(0.5.dp, if (isDarkMode) Color.White.copy(0.12f) else Color.Black.copy(0.08f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color(0xFF00A3FF))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = if (isDarkMode) Color.White.copy(0.74f) else Color.Black.copy(0.65f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShareGridCard(post: PostModel, isReel: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = post.media.firstOrNull()?.url,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = if (isReel) 0.72f else 0.55f))
                    )
                )
        )
        if (isReel) {
            Icon(
                Icons.Default.SmartDisplay,
                null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(18.dp)
            )
        }
        Text(
            text = post.author.username,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun buildStructuredPayload(payload: ChatStructuredPayload): String {
    val json = JSONObject()
    json.put("type", payload.type.name)
    json.put("title", payload.title)
    payload.subtitle?.let { json.put("subtitle", it) }
    payload.tertiary?.let { json.put("tertiary", it) }
    payload.imageUrl?.let { json.put("imageUrl", it) }
    payload.targetId?.let { json.put("targetId", it) }
    return CHAT_SHARE_PREFIX + json.toString() + CHAT_SHARE_SUFFIX
}

fun parseStructuredPayload(text: String): ChatStructuredPayload? {
    if (!text.startsWith(CHAT_SHARE_PREFIX) || !text.endsWith(CHAT_SHARE_SUFFIX)) return null
    return runCatching {
        val rawJson = text.removePrefix(CHAT_SHARE_PREFIX).removeSuffix(CHAT_SHARE_SUFFIX)
        val json = JSONObject(rawJson)
        ChatStructuredPayload(
            type = ChatStructuredType.valueOf(json.getString("type")),
            title = json.getString("title"),
            subtitle = json.optString("subtitle").takeIf { it.isNotBlank() },
            tertiary = json.optString("tertiary").takeIf { it.isNotBlank() },
            imageUrl = json.optString("imageUrl").takeIf { it.isNotBlank() },
            targetId = json.optString("targetId").takeIf { it.isNotBlank() }
        )
    }.getOrNull()
}

@Composable
fun ChatMessageContent(
    text: String,
    messageType: String,
    media: ChatMediaUi?,
    isDarkMode: Boolean,
    preferLightContent: Boolean = false,
    onOpenStructured: (ChatStructuredPayload) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val structured = parseStructuredPayload(text)
    val contentColor = if (preferLightContent || isDarkMode) Color.White else Color.Black
    var previewMedia by remember(media?.url) { mutableStateOf<ChatMediaUi?>(null) }
    when {
        structured != null -> StructuredMessageCard(
            payload = structured,
            isDarkMode = isDarkMode,
            preferLightContent = preferLightContent,
            onClick = { onOpenStructured(structured) },
            modifier = modifier
        )
        media != null && messageType == "voice" -> {
            Column(modifier = modifier.widthIn(max = 250.dp)) {
                VoiceMessageCard(
                    media = media,
                    isDarkMode = isDarkMode,
                    preferLightContent = preferLightContent
                )
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        media != null && messageType == "round_video" -> {
            Column(modifier = modifier.widthIn(max = 240.dp)) {
                RoundVideoMessageCard(
                    media = media,
                    isDarkMode = isDarkMode
                )
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        media != null && messageType == "file" -> {
            Column(modifier = modifier.widthIn(max = 250.dp)) {
                FileMessageCard(
                    media = media,
                    isDarkMode = isDarkMode,
                    preferLightContent = preferLightContent
                )
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        media != null && (messageType == "image" || messageType == "video") -> {
            Column(modifier = modifier.widthIn(max = 240.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (messageType == "video") 200.dp else 180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { previewMedia = media }
                ) {
                    AsyncImage(
                        model = media.url,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = if (messageType == "video") 0.28f else 0.18f))
                            )
                        )
                    )
                    if (messageType == "video") {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(54.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.36f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayCircleFilled, null, tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                        Text(
                            text = "Video",
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                                .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (text.isNotBlank() && parseStructuredPayload(text) == null && text != "Photo" && text != "Video") {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        else -> {
            Text(
                text = text,
                color = contentColor,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontFamily = IosEmojiFont,
                modifier = modifier
            )
        }
    }

    previewMedia?.let { activeMedia ->
        ChatMediaPreviewDialog(
            media = activeMedia,
            isVideo = messageType == "video" || activeMedia.type == "video",
            onDismiss = { previewMedia = null }
        )
    }
}

@Composable
private fun StructuredMessageCard(
    payload: ChatStructuredPayload,
    isDarkMode: Boolean,
    preferLightContent: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val contentColor = if (preferLightContent || isDarkMode) Color.White else Color.Black
    val cardBg = when (payload.type) {
        ChatStructuredType.POST -> if (isDarkMode) Color(0xFF132F57) else Color(0xFFEAF4FF)
        ChatStructuredType.REEL -> if (isDarkMode) Color(0xFF351E26) else Color(0xFFFFEEF0)
        ChatStructuredType.MUSIC -> if (isDarkMode) Color(0xFF221A3A) else Color(0xFFF1ECFF)
        ChatStructuredType.FILE -> if (isDarkMode) Color(0xFF18242C) else Color(0xFFF2F7FB)
        ChatStructuredType.LOCATION -> if (isDarkMode) Color(0xFF143228) else Color(0xFFEAF8F0)
    }
    Surface(
        modifier = modifier
            .widthIn(max = 250.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = cardBg
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (!payload.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = payload.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (payload.type == ChatStructuredType.REEL) 180.dp else 120.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (payload.type) {
                        ChatStructuredType.POST -> Icons.Default.Image
                        ChatStructuredType.REEL -> Icons.Default.SmartDisplay
                        ChatStructuredType.MUSIC -> Icons.Default.PlayCircleFilled
                        ChatStructuredType.FILE -> Icons.Default.InsertDriveFile
                        ChatStructuredType.LOCATION -> Icons.Default.LocationOn
                    },
                    null,
                    tint = when (payload.type) {
                        ChatStructuredType.REEL -> Color(0xFFFF5A5F)
                        ChatStructuredType.MUSIC -> Color(0xFF7C5CFF)
                        ChatStructuredType.LOCATION -> Color(0xFF2DBE78)
                        else -> Color(0xFF00A3FF)
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (payload.type) {
                        ChatStructuredType.POST -> "Shared Post"
                        ChatStructuredType.REEL -> "Shared Reel"
                        ChatStructuredType.MUSIC -> "Music"
                        ChatStructuredType.FILE -> "File"
                        ChatStructuredType.LOCATION -> "Location"
                    },
                    color = contentColor.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(payload.title, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!payload.subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(payload.subtitle, color = contentColor.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!payload.tertiary.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    payload.tertiary,
                    color = when (payload.type) {
                        ChatStructuredType.LOCATION -> Color(0xFF2DBE78)
                        ChatStructuredType.MUSIC -> Color(0xFF7C5CFF)
                        else -> Color(0xFF00A3FF)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ChatMediaPreviewDialog(
    media: ChatMediaUi,
    isVideo: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isVideo) {
                AndroidView(
                    factory = {
                        VideoView(it).apply {
                            setVideoURI(Uri.parse(media.url))
                            setOnPreparedListener { player ->
                                player.isLooping = false
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = media.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}

private fun Context.resolveDisplayName(uri: Uri): String {
    val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            return it.getString(0) ?: "Attachment"
        }
    }
    return "Attachment"
}

private fun Context.resolveFileSize(uri: Uri): String {
    val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val size = it.getLong(0)
            return size.toReadableSize()
        }
    }
    return ""
}

private fun buildLocationAttachment(context: Context): PendingAttachment? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val hasLocationPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasLocationPermission) return null

    val lastKnown = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .firstOrNull()
        ?: return null
    val payload = buildStructuredPayload(
        ChatStructuredPayload(
            type = ChatStructuredType.LOCATION,
            title = "Shared location",
            subtitle = "${formatCoordinate(lastKnown.latitude)}, ${formatCoordinate(lastKnown.longitude)}",
            tertiary = "Tap to open in maps later"
        )
    )
    return PendingAttachment(
        kind = ChatStructuredType.LOCATION,
        previewTitle = "Current location",
        previewSubtitle = "${formatCoordinate(lastKnown.latitude)}, ${formatCoordinate(lastKnown.longitude)}",
        previewTertiary = "Ready to share",
        structuredText = payload
    )
}

private fun Long.toReadableSize(): String {
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

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.5f", value)

private fun formatSeconds(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

private fun Context.createChatCacheFile(prefix: String, extension: String): File {
    return File(cacheDir, prefix + System.currentTimeMillis() + extension)
}

private fun Context.persistReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                if (continuation.isActive) {
                    continuation.resume(future.get())
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

@Composable
private fun UploadProgressRow(
    state: UploadState,
    isDarkMode: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val accentBlue = Color(0xFF00A3FF)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.04f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                when (state.status) {
                    UploadStatus.QUEUED -> Icon(Icons.Default.Schedule, null, tint = contentColor.copy(0.5f), modifier = Modifier.size(16.dp))
                    UploadStatus.UPLOADING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentBlue)
                    UploadStatus.FAILED -> Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    UploadStatus.SUCCESS -> Icon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = when (state.status) {
                    UploadStatus.QUEUED -> "Queued..."
                    UploadStatus.UPLOADING -> "Uploading..."
                    UploadStatus.FAILED -> "Upload failed"
                    UploadStatus.SUCCESS -> "Uploaded"
                },
                color = if (state.status == UploadStatus.FAILED) Color.Red else contentColor.copy(0.8f),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            if (state.status == UploadStatus.FAILED) {
                IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = accentBlue, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = contentColor.copy(0.5f), modifier = Modifier.size(18.dp))
            }
        }
    }
}
