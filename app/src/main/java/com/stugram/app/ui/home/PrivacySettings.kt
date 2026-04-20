package com.stugram.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    isDarkMode: Boolean,
    accentBlue: Color,
    settingsStates: MutableMap<String, Boolean>,
    onBack: () -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Privacy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Account Privacy", color = contentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 16.dp))
            
            ToggleOptionRow(
                title = "Private Account",
                subtitle = "Only followers can see your posts",
                checked = settingsStates["private_account"] ?: false,
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onCheckedChange = { settingsStates["private_account"] = it }
            )

            Spacer(Modifier.height(24.dp))
            Text("Interactions", color = contentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 16.dp))

            ToggleOptionRow(
                title = "Allow Mentions",
                subtitle = "Allow everyone to mention you in their posts",
                checked = settingsStates["allow_mentions"] ?: true,
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onCheckedChange = { settingsStates["allow_mentions"] = it }
            )

            ToggleOptionRow(
                title = "Show Active Status",
                subtitle = "Allow accounts you follow to see when you're active",
                checked = settingsStates["show_active_status"] ?: true,
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onCheckedChange = { settingsStates["show_active_status"] = it }
            )

            Spacer(Modifier.height(24.dp))
            Text("Data Usage", color = contentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 16.dp))

            ToggleOptionRow(
                title = "High Quality Uploads",
                subtitle = "Always upload videos and photos in highest quality",
                checked = settingsStates["high_quality"] ?: true,
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onCheckedChange = { settingsStates["high_quality"] = it }
            )
        }
    }
}
