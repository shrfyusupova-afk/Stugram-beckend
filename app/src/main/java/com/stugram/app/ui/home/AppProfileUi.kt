package com.stugram.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private fun Any?.isDemoProfileVisual(): Boolean = when (this) {
    null -> true
    is Int -> true
    is String -> {
        val value = trim().lowercase()
        value.isBlank() ||
            value == "false" ||
            value == "true" ||
            value == "null" ||
            value == "undefined" ||
            value.contains("picsum.photos") ||
            value.contains("/seed/") ||
            value.contains("photo_1") ||
            value.contains("photo_3") ||
            value.contains("drawable/kun") ||
            value.contains("drawable/tun")
    }
    else -> false
}

private fun profileInitial(name: String?, username: String?): String {
    val source = listOfNotNull(name, username)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?.removePrefix("@")
        .orEmpty()
    return source.firstOrNull()?.uppercase() ?: "?"
}

@Composable
fun AppAvatar(
    imageModel: Any?,
    name: String?,
    username: String?,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    borderColor: Color = Color.Transparent,
    fontSize: TextUnit = 16.sp
) {
    val initial = profileInitial(name, username)
    val fallbackBrush = Brush.linearGradient(
        colors = if (isDarkMode) {
            listOf(Color(0xFF1F2937), Color(0xFF374151))
        } else {
            listOf(Color(0xFFE5E7EB), Color(0xFFD1D5DB))
        }
    )
    val fallbackTextColor = if (isDarkMode) Color.White else Color(0xFF111827)

    var failed by remember(imageModel) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(fallbackBrush, CircleShape)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(width = 1.dp, color = borderColor, shape = CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!failed && !imageModel.isDemoProfileVisual()) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) failed = true
                }
            )
        } else {
            if (initial == "?") {
                Icon(Icons.Rounded.Person, null, tint = fallbackTextColor.copy(alpha = 0.85f))
            } else {
                Text(
                    text = initial,
                    color = fallbackTextColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AppBanner(
    imageModel: Any?,
    title: String?,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    shape: RoundedCornerShape? = null,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val fallbackBrush = Brush.verticalGradient(
        colors = if (isDarkMode) {
            listOf(Color(0xFF111827), Color(0xFF1F2937), Color(0xFF0F172A))
        } else {
            listOf(Color(0xFFF3F4F6), Color(0xFFE5E7EB), Color(0xFFD1D5DB))
        }
    )
    val bannerModifier = if (shape != null) modifier.clip(shape) else modifier

    var failed by remember(imageModel) { mutableStateOf(false) }
    Box(
        modifier = bannerModifier.background(fallbackBrush),
        contentAlignment = Alignment.Center
    ) {
        if (!failed && !imageModel.isDemoProfileVisual()) {
            AsyncImage(
                model = imageModel,
                contentDescription = title ?: "Profile banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) failed = true
                }
            )
        } else {
            Icon(Icons.Rounded.BrokenImage, null, tint = Color.White.copy(alpha = if (isDarkMode) 0.18f else 0.22f))
        }
        overlay()
    }
}
