package com.stugram.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
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
fun LikedPostsPlaceholderScreen(isDarkMode: Boolean, accentBlue: Color, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Liked Posts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Favorite, null, tint = Color.Red, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Loading...", color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHistoryScreen(isDarkMode: Boolean, accentBlue: Color, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val history by viewModel.searchHistory.collectAsState()

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Search history", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Clear", color = accentBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Search history is empty", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                items(history) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.History, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(item.queryText, color = contentColor)
                        }
                        IconButton(onClick = { viewModel.deleteHistoryItem(item._id) }) {
                            Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = contentColor.copy(0.05f))
                }
            }
        }
    }
}
