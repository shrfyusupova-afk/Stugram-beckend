package com.stugram.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSettingsScreen(isDarkMode: Boolean, accentBlue: Color, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    
    var allowCommentsFrom by remember { mutableStateOf("Everyone") }
    var blockCommentsFrom by remember { mutableStateOf("0 people") }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Comment Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Controls", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            
            SettingsOptionRow("Allow comments from", allowCommentsFrom, contentColor) { }
            SettingsOptionRow("Block comments from", blockCommentsFrom, contentColor) { }
            
            Spacer(Modifier.height(32.dp))
            Text("Filters", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            
            var hideOffensive by remember { mutableStateOf(true) }
            ToggleOptionRow("Hide offensive comments", "Automatically hide comments that may be offensive", hideOffensive, isDarkMode, accentBlue) { isChecked ->
                hideOffensive = isChecked
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenWordsScreen(isDarkMode: Boolean, accentBlue: Color, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    
    var hideComments by remember { mutableStateOf(true) }
    var hideMessageRequests by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Hidden words", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Hide offensive content", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            
            ToggleOptionRow("Hide comments", "Comments that may be offensive will be hidden.", hideComments, isDarkMode, accentBlue) { isChecked ->
                hideComments = isChecked
            }
            ToggleOptionRow("Advanced comment filtering", "Hide more offensive comments.", false, isDarkMode, accentBlue) { }
            ToggleOptionRow("Hide message requests", "Message requests that may be offensive will be moved to hidden folder.", hideMessageRequests, isDarkMode, accentBlue) { isChecked ->
                hideMessageRequests = isChecked
            }
            
            Spacer(Modifier.height(32.dp))
            Text("Custom words and phrases", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            
            SettingsOptionRow("Manage custom words and phrases", "", contentColor) { }
            Text(
                "You can list words, phrases, and emojis that you don't want to see. We'll hide comments and message requests that contain them.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DMSettingsScreen(isDarkMode: Boolean, accentBlue: Color, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("DM Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Messages and Replies", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            
            SettingsOptionRow("Who can message you", "Everyone", contentColor) { }
            SettingsOptionRow("Story replies", "Everyone", contentColor) { }
            
            Spacer(Modifier.height(32.dp))
            Text("Status", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            
            var showActivity by remember { mutableStateOf(true) }
            ToggleOptionRow("Show activity status", "Others can see when you're online", showActivity, isDarkMode, accentBlue) { isChecked ->
                showActivity = isChecked
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedListScreen(isDarkMode: Boolean, accentBlue: Color, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val blockedUsers = remember { mutableStateListOf("hacker_99", "spam_bot_01", "annoying_user") }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Blocked Accounts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        if (blockedUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No blocked accounts", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item { Spacer(Modifier.height(16.dp)) }
                items(blockedUsers) { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray.copy(0.2f)))
                            Spacer(Modifier.width(12.dp))
                            Text("@$user", color = contentColor, fontWeight = FontWeight.Medium)
                        }
                        OutlinedButton(
                            onClick = { blockedUsers.remove(user) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, accentBlue)
                        ) {
                            Text("Unblock", color = accentBlue, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = contentColor.copy(0.05f))
                }
            }
        }
    }
}
