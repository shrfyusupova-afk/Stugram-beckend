package com.stugram.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stugram.app.data.remote.model.ProfileHighlightModel

@Composable
fun HighlightViewerScreen(
    isDarkMode: Boolean,
    isMyProfile: Boolean,
    highlight: ProfileHighlightModel,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onSetCover: (storyId: String) -> Unit
) {
    var currentIndex by remember(highlight.id) { mutableIntStateOf(0) }
    var isRenaming by remember(highlight.id) { mutableStateOf(false) }
    var renameValue by remember(highlight.id) { mutableStateOf(highlight.title) }
    val contentColor = Color.White
    val currentItem = highlight.items.getOrNull(currentIndex)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            currentItem?.let { item ->
                AppBanner(
                    imageModel = item.mediaUrl,
                    title = highlight.title,
                    modifier = Modifier.fillMaxSize(),
                    isDarkMode = true,
                    shape = RoundedCornerShape(0.dp)
                )
                if (item.mediaType == "video") {
                    Icon(
                        Icons.Default.PlayCircleOutline,
                        null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(58.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = contentColor)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(highlight.title, color = contentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${currentIndex + 1} / ${highlight.items.size.coerceAtLeast(1)}",
                        color = Color.White.copy(alpha = 0.74f),
                        fontSize = 12.sp
                    )
                }
                if (isMyProfile) {
                    IconButton(
                        onClick = {
                            highlight.items.getOrNull(currentIndex)?.storyId?.let(onSetCover)
                        },
                        enabled = highlight.items.getOrNull(currentIndex)?.storyId != null
                    ) {
                        Icon(Icons.Default.Image, null, tint = contentColor)
                    }
                    IconButton(onClick = { isRenaming = !isRenaming }) {
                        Icon(Icons.Default.Edit, null, tint = contentColor)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, null, tint = Color.Red.copy(alpha = 0.92f))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(enabled = currentIndex > 0) {
                            currentIndex = (currentIndex - 1).coerceAtLeast(0)
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(enabled = currentIndex < highlight.items.lastIndex) {
                            currentIndex = (currentIndex + 1).coerceAtMost(highlight.items.lastIndex)
                        }
                )
            }

            if (isRenaming && isMyProfile) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(Color(0xFF151515))
                        .padding(20.dp)
                ) {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it.take(30) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Highlight title") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (renameValue.isNotBlank()) {
                                onRename(renameValue.trim())
                                isRenaming = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ProfileAccentBlue)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isMyProfile && highlight.items.getOrNull(currentIndex)?.storyId != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 26.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable {
                            highlight.items.getOrNull(currentIndex)?.storyId?.let(onSetCover)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = ProfileAccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Use this as cover", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
