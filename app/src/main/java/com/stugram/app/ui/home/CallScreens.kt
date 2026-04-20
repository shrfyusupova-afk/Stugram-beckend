package com.stugram.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VoiceCallScreen(userName: String, avatar: String? = null, banner: String? = null, onHangUp: () -> Unit) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isCameraOn by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    
    var floatingOffset by remember { mutableStateOf(Offset(100f, 200f)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            duration++
        }
    }

    val minutes = duration / 60
    val seconds = duration % 60
    val timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    AnimatedContent(
        targetState = isMinimized,
        transitionSpec = {
            (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.8f))
                .togetherWith(fadeOut(animationSpec = tween(500)) + scaleOut(targetScale = 0.8f))
        },
        label = "call_view_anim"
    ) { minimized ->
        if (minimized) {
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(floatingOffset.x.roundToInt(), floatingOffset.y.roundToInt()) }
                        .size(120.dp, 160.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                floatingOffset += dragAmount
                            }
                        }
                        .clickable { isMinimized = false }
                        .shadow(12.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(0.85f),
                    border = BorderStroke(1.5.dp, Color.White.copy(0.2f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AppAvatar(
                            imageModel = avatar,
                            name = userName,
                            username = userName,
                            modifier = Modifier.size(60.dp).border(2.dp, Color(0xFF00A3FF), CircleShape),
                            isDarkMode = true,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(timeString, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Muloqot...", color = Color.White.copy(0.6f), fontSize = 10.sp)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppBanner(
                        imageModel = banner,
                        title = userName,
                        modifier = Modifier.fillMaxSize(),
                        isDarkMode = true
                    )
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(0.4f), Color.Transparent, Color.Black.copy(0.9f)))
                    ))
                    
                    if (isCameraOn) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f))) {
                            Text("Kamera yoqildi (Preview)", color = Color.White, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { isMinimized = true },
                        modifier = Modifier.size(44.dp).background(Color.Black.copy(0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.FullscreenExit, null, tint = Color.White)
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppAvatar(
                        imageModel = avatar,
                        name = userName,
                        username = userName,
                        modifier = Modifier
                            .size(100.dp)
                            .border(2.dp, Color.White.copy(0.4f), CircleShape)
                            .shadow(15.dp, CircleShape),
                        isDarkMode = true,
                        fontSize = 34.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(userName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("@${userName.lowercase().replace(" ", "")}", color = Color.White.copy(0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(timeString, color = Color(0xFF00A3FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallControlButtonSmall(
                            icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            label = "Mute",
                            isActive = isMuted,
                            onClick = { isMuted = !isMuted }
                        )

                        CallControlButtonSmall(
                            icon = if (isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            label = "Camera",
                            isActive = isCameraOn,
                            onClick = { isCameraOn = !isCameraOn }
                        )

                        CallControlButtonSmall(
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            label = "Speaker",
                            isActive = isSpeakerOn,
                            onClick = { isSpeakerOn = !isSpeakerOn }
                        )

                        CallControlButtonSmall(
                            icon = Icons.Default.CallEnd,
                            label = "End",
                            backgroundColor = Color.Red,
                            iconColor = Color.White,
                            onClick = onHangUp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallControlButtonSmall(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    backgroundColor: Color? = null,
    iconColor: Color? = null,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(backgroundColor ?: if (isActive) Color.White else Color.White.copy(0.15f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = iconColor ?: if (isActive) Color.Black else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun VideoCallScreen(userName: String, avatar: String? = null, banner: String? = null, onHangUp: () -> Unit) {
    var isMuted by remember { mutableStateOf(false) }
    var isVideoOff by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var isPeerVideoOff by remember { mutableStateOf(false) }
    var isMeMain by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Main View
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { /* Toggle controls visibility if needed */ }
        ) {
            if (isMeMain) {
                // My camera full screen
                AppBanner(
                    imageModel = banner,
                    title = userName,
                    modifier = Modifier.fillMaxSize(),
                    isDarkMode = true
                )
            } else {
                // Peer camera full screen
                if (isPeerVideoOff) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppBanner(
                            imageModel = banner,
                            title = userName,
                            modifier = Modifier.fillMaxSize().blur(25.dp),
                            isDarkMode = true
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.6f)))
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(100.dp).background(Color.White.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VideocamOff, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(40.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(text = "$userName kamerani o'chirgan", color = Color.White.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    AppBanner(
                        imageModel = banner,
                        title = userName,
                        modifier = Modifier.fillMaxSize(),
                        isDarkMode = true
                    )
                }
            }
        }

        // Small Preview Overlay
        if (!isVideoOff) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 20.dp)
                    .size(110.dp, 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.DarkGray)
                    .border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(16.dp))
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clickable { isMeMain = !isMeMain }
            ) {
                if (isMeMain) {
                    // Peer camera small
                    AppBanner(
                        imageModel = banner,
                        title = userName,
                        modifier = Modifier.fillMaxSize().let { if(isPeerVideoOff) it.blur(10.dp) else it },
                        isDarkMode = true
                    )
                } else {
                    // My camera small
                    AppAvatar(
                        imageModel = avatar,
                        name = userName,
                        username = userName,
                        modifier = Modifier.fillMaxSize(),
                        isDarkMode = true,
                        fontSize = 24.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 60.dp, start = 20.dp)
        ) {
            Text(userName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("00:15", color = Color.White.copy(0.9f), fontSize = 15.sp)
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallControlButtonSmall(
                icon = Icons.Default.FlipCameraIos,
                label = "Flip",
                isActive = false,
                onClick = { isFrontCamera = !isFrontCamera }
            )
            CallControlButtonSmall(
                icon = if (isVideoOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                label = "Camera",
                isActive = isVideoOff,
                onClick = { isVideoOff = !isVideoOff }
            )
            CallControlButtonSmall(
                icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                label = "Mute",
                isActive = isMuted,
                onClick = { isMuted = !isMuted }
            )
            CallControlButtonSmall(
                icon = Icons.Default.CallEnd,
                label = "End",
                backgroundColor = Color.Red,
                iconColor = Color.White,
                onClick = onHangUp
            )
        }
    }
}
