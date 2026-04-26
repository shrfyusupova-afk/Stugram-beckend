package com.stugram.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stugram.app.data.remote.model.ProfileHighlightModel

@Composable
fun ProfileHighlightsRow(
    isDarkMode: Boolean,
    isMyProfile: Boolean,
    highlights: List<ProfileHighlightModel>,
    errorMessage: String?,
    onHighlightClick: (ProfileHighlightModel) -> Unit,
    onAddClick: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val secondaryText = if (isDarkMode) Color(0xFFB8BDC7) else Color(0xFF636B74)
    val surface = if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.03f)

    Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            "Favorite Moments",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isMyProfile) {
                item(key = "add_highlight") {
                    HighlightCircleItem(
                        title = "New",
                        isDarkMode = isDarkMode,
                        onClick = onAddClick
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(surface)
                                .border(1.5.dp, ProfileAccentBlue.copy(alpha = 0.42f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, null, tint = ProfileAccentBlue, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }

            items(highlights, key = { it.id }) { highlight ->
                val cover = highlight.coverImageUrl ?: highlight.coverUrl ?: highlight.items.firstOrNull()?.thumbnailUrl ?: highlight.items.firstOrNull()?.mediaUrl
                HighlightCircleItem(
                    title = highlight.title,
                    isDarkMode = isDarkMode,
                    onClick = { onHighlightClick(highlight) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(surface)
                            .border(1.dp, Color.Gray.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AppBanner(
                            imageModel = cover,
                            title = highlight.title,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape),
                            isDarkMode = isDarkMode,
                            shape = CircleShape
                        )
                        if (highlight.items.any { it.mediaType == "video" }) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = Color.Red.copy(alpha = 0.9f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp)
            )
        }

        if (!isMyProfile && highlights.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No highlights yet",
                color = secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun HighlightCircleItem(
    title: String,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        )
    }
}
