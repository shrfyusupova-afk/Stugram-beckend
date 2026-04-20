package com.stugram.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@androidx.compose.runtime.Immutable
data class SettingsFolder(
    val id: Int,
    val title: String,
    val icon: ImageVector,
    val subItems: List<String>,
    val isDanger: Boolean = false
)

@Composable
fun AnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color = Color(0xFF00A3FF),
    isDarkMode: Boolean
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumb_offset"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) activeColor else (if (isDarkMode) Color(0xFF333333) else Color(0xFFE0E0E0)),
        animationSpec = tween(300),
        label = "track_color"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumb_scale"
    )

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .graphicsLayer(scaleX = thumbScale, scaleY = thumbScale)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun SettingsOptionRow(title: String, value: String, contentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, color = contentColor, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            if (value.isNotEmpty()) {
                Text(value, color = Color(0xFF00A3FF), fontSize = 14.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ToggleOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    isDarkMode: Boolean,
    accentBlue: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = contentColor, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
        AnimatedSwitch(checked = checked, onCheckedChange = onCheckedChange, isDarkMode = isDarkMode, activeColor = accentBlue)
    }
}

@Composable
fun SettingsFolderCard(
    folder: SettingsFolder,
    accentBlue: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = contentColor.copy(alpha = 0.03f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accentBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(folder.icon, null, tint = accentBlue, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(text = folder.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = contentColor)
            }
            Icon(Icons.Default.ChevronRight, null, tint = contentColor.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionChip(text: String, accentBlue: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = accentBlue.copy(0.1f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, accentBlue.copy(0.3f))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
