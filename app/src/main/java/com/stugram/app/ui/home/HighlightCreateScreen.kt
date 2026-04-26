package com.stugram.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightCreateScreen(
    isDarkMode: Boolean,
    stories: List<StoryMedia>,
    onDismiss: () -> Unit,
    onCreate: (title: String, storyIds: List<String>, coverStoryId: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var coverStoryId by remember { mutableStateOf<String?>(null) }

    val contentColor = if (isDarkMode) Color.White else Color.Black
    val sheetBg = if (isDarkMode) Color(0xFF151515) else Color.White
    val inputBg = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val selectedStories = stories.filter { (it.backendId ?: it.id.toString()) in selectedIds }
    val canCreate = title.isNotBlank() && selectedStories.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Create favorite moment", color = contentColor, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                "Choose stories, then select which one should become the cover.",
                color = Color.Gray,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(30) },
                label = { Text("Highlight title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor,
                    focusedContainerColor = inputBg,
                    unfocusedContainerColor = inputBg,
                    focusedBorderColor = ProfileAccentBlue,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text("Choose stories", color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            if (stories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(inputBg)
                        .border(1.dp, Color.Gray.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Create a story first, then you can pin it here.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(stories, key = { it.backendId ?: it.id.toString() }) { story ->
                        val storyKey = story.backendId ?: story.id.toString()
                        val selected = storyKey in selectedIds
                        val isCover = coverStoryId == storyKey
                        Box(
                            modifier = Modifier
                                .size(84.dp, 112.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(inputBg)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = when {
                                        isCover -> ProfileAccentBlue
                                        selected -> ProfileAccentBlue.copy(alpha = 0.78f)
                                        else -> Color.Gray.copy(alpha = 0.16f)
                                    },
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    selectedIds = if (selected) {
                                        val next = selectedIds - storyKey
                                        if (coverStoryId == storyKey) {
                                            coverStoryId = next.firstOrNull()
                                        }
                                        next
                                    } else {
                                        val next = selectedIds + storyKey
                                        if (coverStoryId == null) coverStoryId = storyKey
                                        next
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AppBanner(
                                imageModel = story.mediaUrl,
                                title = "story",
                                modifier = Modifier.fillMaxWidth().height(112.dp),
                                isDarkMode = isDarkMode,
                                shape = RoundedCornerShape(18.dp)
                            )
                            if (story.isVideo) {
                                androidx.compose.material3.Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            if (selected) {
                                androidx.compose.material3.Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = ProfileAccentBlue,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (selectedStories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text("Choose cover", color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(selectedStories, key = { it.backendId ?: it.id.toString() }) { story ->
                        val storyKey = story.backendId ?: story.id.toString()
                        val selectedAsCover = coverStoryId == storyKey
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(inputBg)
                                    .border(
                                        width = if (selectedAsCover) 2.dp else 1.dp,
                                        color = if (selectedAsCover) ProfileAccentBlue else Color.Gray.copy(alpha = 0.16f),
                                        shape = CircleShape
                                    )
                                    .clickable { coverStoryId = storyKey },
                                contentAlignment = Alignment.Center
                            ) {
                                AppBanner(
                                    imageModel = story.mediaUrl,
                                    title = "cover",
                                    modifier = Modifier.matchParentSize().clip(CircleShape),
                                    isDarkMode = isDarkMode,
                                    shape = CircleShape
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (selectedAsCover) "Cover" else "Select",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedAsCover) ProfileAccentBlue else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    onCreate(
                        title.trim(),
                        selectedStories.mapNotNull { it.backendId ?: it.id.toString() },
                        coverStoryId
                    )
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProfileAccentBlue,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.18f)
                )
            ) {
                Text("Add to profile", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
