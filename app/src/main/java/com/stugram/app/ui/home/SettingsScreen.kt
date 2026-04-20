package com.stugram.app.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stugram.app.core.storage.TokenManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedListScreen(isDarkMode: Boolean, accentBlue: Color, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val blockedUsers by viewModel.blockedUsers.collectAsState()

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
                Text("You haven't blocked anyone", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(blockedUsers) { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = user.avatar,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(user.username, fontWeight = FontWeight.Bold, color = contentColor)
                                Text(user.fullName, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = { viewModel.unblockUser(user._id) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text("Unblock", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenWordsScreen(isDarkMode: Boolean, accentBlue: Color, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val words by viewModel.hiddenWords.collectAsState()
    var newWord by remember { mutableStateOf("") }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Hidden Words", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Hide comments that contain specific words or phrases.", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = newWord,
                onValueChange = { newWord = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add word...") },
                trailingIcon = {
                    IconButton(onClick = {
                        if (newWord.isNotBlank()) {
                            viewModel.addHiddenWord(newWord)
                            newWord = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, null, tint = accentBlue)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(words) { word ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(word, color = contentColor)
                        IconButton(onClick = { viewModel.removeHiddenWord(word) }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = contentColor.copy(0.05f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    viewModel: SettingsViewModel = run {
        val context = LocalContext.current.applicationContext
        val tokenManager = remember { TokenManager(context) }
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(tokenManager = tokenManager) as T
                }
            }
        )
    }
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val accentBlue = Color(0xFF00A3FF)

    val settings by viewModel.settings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    var activeFolder by remember { mutableStateOf<SettingsFolder?>(null) }
    var activeSubItem by remember { mutableStateOf<String?>(null) }
    
    val folders = remember {
        listOf(
            // Only include entries that are real/backend-backed or purely device-level (theme).
            SettingsFolder(1, "Account", Icons.Default.Person, listOf("Edit Profile", "Personal Information", "Logout")),
            SettingsFolder(2, "Notifications", Icons.Default.Notifications, listOf("Push Notifications")),
            SettingsFolder(3, "Security", Icons.Default.Lock, listOf("Change Password", "Login Activity")),
            SettingsFolder(4, "Privacy", Icons.Default.PrivacyTip, listOf("Private Account", "Blocked Accounts", "Hidden Words", "Status")),
            SettingsFolder(5, "Display", Icons.Default.Palette, listOf("Dark / Light mode")),
            SettingsFolder(6, "Activity", Icons.Default.History, listOf("Search History", "Liked Posts", "Saved"))
        )
    }

    AnimatedContent(
        targetState = Triple(activeFolder, activeSubItem, isDarkMode),
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "settings_nav"
    ) { state ->
        val folder = state.first
        val subItem = state.second
        
        when {
            subItem != null -> {
                when (subItem) {
                    "Change Password" -> ChangePasswordScreen(isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Login Activity" -> LoginActivityScreen(isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Push Notifications" -> PushNotificationsScreen(isDarkMode, accentBlue, viewModel, onBack = { activeSubItem = null }) { activeSubItem = it }
                    "Liked Posts" -> LikedPostsScreen(isDarkMode, accentBlue) { activeSubItem = null }
                    "Saved" -> SavedPostsScreen(isDarkMode = isDarkMode, accentBlue = accentBlue) { activeSubItem = null }
                    "Search History" -> SearchHistoryScreen(isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Edit Profile" -> EditProfileInfoScreen("Full Name", userProfile?.fullName ?: "", isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Personal Information" -> EditProfileInfoScreen("Bio", userProfile?.bio ?: "", isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Hidden Words" -> HiddenWordsScreen(isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Blocked Accounts" -> BlockedListScreen(isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Likes", "Comments", "Follow / Requests", "Messages" -> NotificationDetailScreen(subItem, isDarkMode, accentBlue, viewModel) { activeSubItem = "Push Notifications" }
                    "Logout" -> {
                        viewModel.logout { onBack() }
                        activeSubItem = null
                    }
                    else -> {
                        // No placeholder fallback screens.
                        // If an item is visible in Settings, it must be fully wired above.
                        activeSubItem = null
                    }
                }
            }
            folder != null -> {
                Scaffold(
                    containerColor = backgroundColor,
                    topBar = {
                        TopAppBar(
                            title = { Text(folder.title, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { activeFolder = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                        folder.subItems.forEach { itemText ->
                            val isSwitch = itemText == "Dark / Light mode" || itemText == "Private Account" || itemText == "Status"
                            val isChecked = when (itemText) {
                                "Dark / Light mode" -> isDarkMode
                                "Private Account" -> settings?.isPrivateAccount ?: false
                                "Status" -> settings?.readReceipts ?: true
                                else -> false
                            }
                            
                            Surface(
                                onClick = {
                                    if (isSwitch) {
                                        if (itemText == "Dark / Light mode") {
                                            onThemeChange(!isDarkMode)
                                        } else if (itemText == "Private Account") {
                                            viewModel.updateSetting("isPrivateAccount", !isChecked)
                                        } else if (itemText == "Status") {
                                            viewModel.updateSetting("readReceipts", !isChecked)
                                        }
                                    } else {
                                        activeSubItem = itemText
                                    }
                                },
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = itemText,
                                        fontSize = 16.sp,
                                        color = if (folder.isDanger || itemText == "Logout" || itemText.contains("Delete")) Color.Red else contentColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    if (isSwitch) {
                                        AnimatedSwitch(
                                            checked = isChecked,
                                            onCheckedChange = {
                                                if (itemText == "Dark / Light mode") {
                                                    onThemeChange(it)
                                                } else if (itemText == "Private Account") {
                                                    viewModel.updateSetting("isPrivateAccount", it)
                                                } else if (itemText == "Status") {
                                                    viewModel.updateSetting("readReceipts", it)
                                                }
                                            },
                                            isDarkMode = isDarkMode
                                        )
                                    } else {
                                        Icon(Icons.Default.ChevronRight, null, tint = contentColor.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            HorizontalDivider(color = contentColor.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }
            else -> {
                Scaffold(
                    containerColor = backgroundColor,
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings", fontWeight = FontWeight.Black, fontSize = 22.sp) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(Modifier.height(8.dp)) }
                        items(folders) { item ->
                            SettingsFolderCard(item, accentBlue, contentColor) { activeFolder = item }
                        }
                        item { Spacer(Modifier.height(100.dp)) }
                    }
                }
            }
        }
    }
}
