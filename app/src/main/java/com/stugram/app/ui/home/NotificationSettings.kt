package com.stugram.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    title: String,
    isDarkMode: Boolean,
    accentBlue: Color,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    
    val settings by viewModel.settings.collectAsState()
    val notificationKey = when(title) {
        "Likes" -> "likes"
        "Comments" -> "comments"
        "Follow / Requests" -> "followRequests"
        "Messages" -> "messages"
        else -> ""
    }

    val isEnabled = when(notificationKey) {
        "likes" -> settings?.notifications?.likes ?: true
        "comments" -> settings?.notifications?.comments ?: true
        "followRequests" -> settings?.notifications?.followRequests ?: true
        "messages" -> settings?.notifications?.messages ?: true
        else -> true
    }

    val options = listOf("Off", "On")

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            
            options.forEach { option ->
                val isSelected = if (option == "On") isEnabled else !isEnabled
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        viewModel.updateNotificationSetting(notificationKey, option == "On")
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(option, color = contentColor, fontSize = 16.sp)
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.updateNotificationSetting(notificationKey, option == "On") },
                        colors = RadioButtonDefaults.colors(selectedColor = accentBlue)
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Text(
                "When this is on, you'll see notifications for $title.",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushNotificationsScreen(
    isDarkMode: Boolean,
    accentBlue: Color,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Push settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {
            val allEnabled = listOf(
                settings?.notifications?.likes ?: true,
                settings?.notifications?.comments ?: true,
                settings?.notifications?.followRequests ?: true,
                settings?.notifications?.messages ?: true
            ).all { it }
            ToggleOptionRow("Pause All", "", !allEnabled, isDarkMode, accentBlue) {
                viewModel.updateAllNotificationSettings(enabled = !it)
            }
            
            Spacer(Modifier.height(24.dp))
            Text("Notifications", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            
            listOf("Likes", "Comments", "Follow / Requests", "Messages").forEach { item ->
                val notificationKey = when(item) {
                    "Likes" -> "likes"
                    "Comments" -> "comments"
                    "Follow / Requests" -> "followRequests"
                    "Messages" -> "messages"
                    else -> ""
                }
                val isEnabled = when(notificationKey) {
                    "likes" -> settings?.notifications?.likes ?: true
                    "comments" -> settings?.notifications?.comments ?: true
                    "followRequests" -> settings?.notifications?.followRequests ?: true
                    "messages" -> settings?.notifications?.messages ?: true
                    else -> true
                }

                SettingsOptionRow(item, if (isEnabled) "On" else "Off", contentColor) { onNavigateToDetail(item) }
                HorizontalDivider(color = contentColor.copy(0.05f))
            }
        }
    }
}
