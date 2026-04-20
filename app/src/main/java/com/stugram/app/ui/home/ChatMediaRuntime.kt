package com.stugram.app.ui.home

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Locale

data class ChatMediaUi(
    val url: String,
    val type: String,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val durationSeconds: Double? = null
)

@Composable
fun VoiceMessageCard(
    media: ChatMediaUi,
    isDarkMode: Boolean,
    preferLightContent: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contentColor = if (preferLightContent || isDarkMode) Color.White else Color.Black
    val accentColor = if (preferLightContent) Color(0xFF4FA7FF) else Color(0xFF62B8FF)
    val surfaceColor = if (isDarkMode) Color(0xFF16232C) else Color(0xFFEAF6FF)
    var mediaPlayer by remember(media.url) { mutableStateOf<MediaPlayer?>(null) }
    var isPreparing by remember(media.url) { mutableStateOf(false) }
    var isPlaying by remember(media.url) { mutableStateOf(false) }
    var totalMs by remember(media.url) { mutableIntStateOf((media.durationSeconds?.times(1000))?.toInt() ?: 0) }
    var progressMs by remember(media.url) { mutableIntStateOf(0) }

    DisposableEffect(media.url) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying && mediaPlayer != null) {
            progressMs = runCatching { mediaPlayer?.currentPosition ?: progressMs }.getOrDefault(progressMs)
            delay(250)
        }
    }

    Surface(
        modifier = modifier.widthIn(max = 250.dp),
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .clickable {
                        val player = mediaPlayer
                        if (player != null) {
                            if (isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                player.start()
                                isPlaying = true
                            }
                        } else {
                            isPreparing = true
                            val newPlayer = MediaPlayer().apply {
                                setDataSource(context, Uri.parse(media.url))
                                setOnPreparedListener {
                                    totalMs = it.duration
                                    progressMs = 0
                                    isPreparing = false
                                    isPlaying = true
                                    it.start()
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    progressMs = 0
                                    it.seekTo(0)
                                }
                                setOnErrorListener { mp, _, _ ->
                                    isPreparing = false
                                    isPlaying = false
                                    mp.release()
                                    mediaPlayer = null
                                    true
                                }
                                prepareAsync()
                            }
                            mediaPlayer = newPlayer
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isPreparing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accentColor)
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Voice message",
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(contentColor.copy(alpha = 0.12f))
                ) {
                    val fraction = if (totalMs > 0) progressMs.toFloat() / totalMs.toFloat() else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(accentColor)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))
            Text(
                text = formatPlaybackTime(if (isPlaying) progressMs / 1000 else ((media.durationSeconds ?: totalMs / 1000.0).toInt())),
                color = contentColor.copy(alpha = 0.70f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun RoundVideoMessageCard(
    media: ChatMediaUi,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember(media.url) { mutableStateOf(false) }
    var isPrepared by remember(media.url) { mutableStateOf(false) }
    val borderColor = if (isDarkMode) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .size(170.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.16f))
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(Uri.parse(media.url))
                    setOnPreparedListener { player ->
                        player.isLooping = false
                        isPrepared = true
                        seekTo(1)
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        seekTo(1)
                    }
                }
            },
            update = { videoView ->
                if (isPlaying && !videoView.isPlaying) {
                    videoView.start()
                } else if (!isPlaying && videoView.isPlaying) {
                    videoView.pause()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )

        if (!isPrepared) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        Surface(
            modifier = Modifier
                .size(48.dp)
                .clickable(enabled = isPrepared) { isPlaying = !isPlaying },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.34f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Text(
        text = formatPlaybackTime((media.durationSeconds ?: 0.0).toInt()),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FileMessageCard(
    media: ChatMediaUi,
    isDarkMode: Boolean,
    preferLightContent: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val contentColor = if (preferLightContent || isDarkMode) Color.White else Color.Black
    val accentColor = when {
        media.mimeType?.contains("pdf", ignoreCase = true) == true -> Color(0xFFFF5A5F)
        media.mimeType?.contains("zip", ignoreCase = true) == true -> Color(0xFF7C5CFF)
        else -> Color(0xFF00A3FF)
    }
    val surfaceColor = if (isDarkMode) Color(0xFF15212A) else Color(0xFFF2F7FB)

    Surface(
        modifier = modifier
            .widthIn(max = 250.dp)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(media.url), media.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            },
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.InsertDriveFile, null, tint = accentColor)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.fileName ?: "Attachment",
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val metaLine = listOfNotNull(
                    media.mimeType?.substringAfterLast('/')?.uppercase(Locale.getDefault()),
                    media.fileSize?.toReadableSize()
                ).joinToString(" • ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        color = contentColor.copy(alpha = 0.68f),
                        fontSize = 11.sp
                    )
                }
            }
            Icon(Icons.Default.Description, null, tint = accentColor.copy(alpha = 0.82f))
        }
    }
}

@Composable
fun RoundVideoRecordingOverlay(
    isDarkMode: Boolean,
    seconds: Int,
    onPreviewReady: (PreviewView) -> Unit
) {
    val transition = rememberInfiniteTransition(label = "round_video_glow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isDarkMode) 0.48f else 0.36f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .blur(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF00A3FF).copy(alpha = 0.18f))
        )

        Box(
            modifier = Modifier
                .size(250.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00A3FF).copy(alpha = glowAlpha),
                            Color.Black.copy(alpha = 0.24f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).also(onPreviewReady)
                },
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Recording round video",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatPlaybackTime(seconds),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp
            )
        }
    }
}

private fun formatPlaybackTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

private fun Long.toReadableSize(): String {
    if (this <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = this.toDouble()
    var index = 0
    while (size >= 1024 && index < units.lastIndex) {
        size /= 1024
        index += 1
    }
    return DecimalFormat("#,##0.#").format(size) + " " + units[index]
}
