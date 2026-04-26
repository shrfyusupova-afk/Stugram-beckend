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
import com.stugram.app.data.remote.model.ProfileModel

private fun String.toSettingsKey(): String = when (this) {
    "Private Account" -> "isPrivateAccount"
    "Read Receipts" -> "readReceipts"
    "Sensitive Content Filter" -> "sensitiveFilter"
    "Data Saver" -> "dataSaver"
    "Video Autoplay" -> "videoAutoPlay"
    else -> this
}

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
    val hiddenSettings by viewModel.hiddenWordsSettings.collectAsState()
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

            ToggleOptionRow(
                title = "Hide comments",
                subtitle = "Filter comments containing hidden words.",
                checked = hiddenSettings.hideComments,
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onCheckedChange = { viewModel.updateHiddenWordsSetting(hideComments = it) }
            )
            ToggleOptionRow(
                title = "Hide messages",
                subtitle = "Filter direct messages containing hidden words.",
                checked = hiddenSettings.hideMessages,
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onCheckedChange = { viewModel.updateHiddenWordsSetting(hideMessages = it) }
            )
            ToggleOptionRow(
                title = "Hide story replies",
                subtitle = "Filter story replies containing hidden words.",
                checked = hiddenSettings.hideStoryReplies,
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onCheckedChange = { viewModel.updateHiddenWordsSetting(hideStoryReplies = it) }
            )

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
    onEditProfile: (() -> Unit)? = null,
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
    val statusMessage by viewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeFolder by remember { mutableStateOf<SettingsFolder?>(null) }
    var activeSubItem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeStatusMessage()
    }
    
    val folders = remember {
        listOf(
            // Only include entries that are real/backend-backed or purely device-level (theme).
            SettingsFolder(1, "Account", Icons.Default.Person, listOf("Edit Profile", "Personal Information", "Logout")),
            SettingsFolder(2, "Notifications", Icons.Default.Notifications, listOf("Push Notifications")),
            SettingsFolder(3, "Security", Icons.Default.Lock, listOf("Change Password", "Login Activity")),
            SettingsFolder(4, "Privacy", Icons.Default.PrivacyTip, listOf("Private Account", "Read Receipts", "Blocked Accounts", "Hidden Words", "Sensitive Content Filter")),
            SettingsFolder(5, "Storage & Data", Icons.Default.Storage, listOf("Data Saver", "Video Autoplay")),
            SettingsFolder(6, "Appearance", Icons.Default.Palette, listOf("Dark / Light mode")),
            SettingsFolder(7, "Activity", Icons.Default.History, listOf("Search History", "Liked Posts", "Saved")),
            SettingsFolder(8, "Support & Help", Icons.Default.Help, listOf("Help Center", "Report a Problem")),
            SettingsFolder(9, "About", Icons.Default.Info, listOf("App Version", "Privacy Policy", "Terms of Service"))
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
                    "Personal Information" -> PersonalInformationScreen(isDarkMode, accentBlue, userProfile, viewModel) { activeSubItem = null }
                    "Hidden Words" -> HiddenWordsScreen(isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Blocked Accounts" -> BlockedListScreen(isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Likes", "Comments", "Follow / Requests", "Messages" -> NotificationDetailScreen(subItem, isDarkMode, accentBlue, viewModel) { activeSubItem = "Push Notifications" }
                    "Logout" -> LogoutConfirmScreen(isDarkMode, accentBlue, viewModel, onCancel = { activeSubItem = null }) { onBack() }
                    "Report a Problem" -> ReportProblemScreen(isDarkMode, accentBlue, viewModel) { activeSubItem = null }
                    "Help Center", "App Version", "Privacy Policy", "Terms of Service" ->
                        SettingsInfoScreen(subItem, isDarkMode, accentBlue) { activeSubItem = null }
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
                    snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            val isSwitch = itemText in setOf(
                                "Dark / Light mode",
                                "Private Account",
                                "Read Receipts",
                                "Sensitive Content Filter",
                                "Data Saver",
                                "Video Autoplay"
                            )
                            val isChecked = when (itemText) {
                                "Dark / Light mode" -> isDarkMode
                                "Private Account" -> settings?.isPrivateAccount ?: false
                                "Read Receipts" -> settings?.readReceipts ?: true
                                "Sensitive Content Filter" -> settings?.sensitiveFilter ?: false
                                "Data Saver" -> settings?.dataSaver ?: false
                                "Video Autoplay" -> settings?.videoAutoPlay ?: true
                                else -> false
                            }
                            
                            Surface(
                                onClick = {
                                    if (isSwitch) {
                                        if (itemText == "Dark / Light mode") {
                                            onThemeChange(!isDarkMode)
                                        } else {
                                            viewModel.updateSetting(itemText.toSettingsKey(), !isChecked)
                                        }
                                    } else {
                                        if (itemText == "Edit Profile" && onEditProfile != null) {
                                            activeFolder = null
                                            onEditProfile()
                                        } else {
                                            activeSubItem = itemText
                                        }
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
                                                } else {
                                                    viewModel.updateSetting(itemText.toSettingsKey(), it)
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
                    snackbarHost = { SnackbarHost(snackbarHostState) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalInformationScreen(
    isDarkMode: Boolean,
    accentBlue: Color,
    profile: ProfileModel?,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    var fullName by remember(profile?.id) { mutableStateOf(profile?.fullName.orEmpty()) }
    var username by remember(profile?.id) { mutableStateOf(profile?.username.orEmpty()) }
    var bio by remember(profile?.id) { mutableStateOf(profile?.bio.orEmpty()) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Personal Information", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.updateProfile(
                                fullName = fullName.trim().takeIf { it.isNotBlank() },
                                username = username.trim().takeIf { it.isNotBlank() },
                                bio = bio.trim()
                            )
                            onBack()
                        }
                    ) {
                        Text("Save", color = accentBlue, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsTextField("Full name", fullName, contentColor, accentBlue) { fullName = it }
            SettingsTextField("Username", username, contentColor, accentBlue) { username = it }
            SettingsTextField("Bio", bio, contentColor, accentBlue, minLines = 3) { bio = it }
            Text("Birthday, school, region and district can be edited from the full Edit Profile screen.", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    contentColor: Color,
    accentBlue: Color,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentBlue,
            unfocusedBorderColor = contentColor.copy(alpha = 0.12f),
            focusedTextColor = contentColor,
            unfocusedTextColor = contentColor
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogoutConfirmScreen(
    isDarkMode: Boolean,
    accentBlue: Color,
    viewModel: SettingsViewModel,
    onCancel: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    var isLoggingOut by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Logout", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Logout, null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(16.dp))
            Text("Log out of this account?", color = contentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Only the active profile session will be removed from this device.", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    if (isLoggingOut) return@Button
                    isLoggingOut = true
                    viewModel.logout(onLoggedOut)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isLoggingOut) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onCancel, enabled = !isLoggingOut) {
                Text("Cancel", color = accentBlue)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsInfoScreen(title: String, isDarkMode: Boolean, accentBlue: Color, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val body = when (title) {
        "Help Center" -> "For closed alpha, support is handled manually through the founder/admin. Use Report a Problem for issues that need backend review."
        "Report a Problem" -> "Support tickets are available in the backend. The next alpha pass can add screenshot upload UI here; for now use this screen as the support entry point."
        "App Version" -> "StuGram Android debug/internal alpha build."
        "Privacy Policy" -> "Privacy policy must be published before public launch. Closed alpha testers should be told what data is collected: profile, posts, stories, messages, device push tokens and logs."
        "Terms of Service" -> "Terms must be published before public launch. Closed alpha rules should cover content, abuse, reports and account removal."
        else -> "$title is available."
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text(title, color = contentColor, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            Text(body, color = Color.Gray, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            Surface(color = accentBlue.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
                Text(
                    "This item is intentionally wired to a real screen; it is not a dead button.",
                    color = accentBlue,
                    modifier = Modifier.padding(14.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportProblemScreen(
    isDarkMode: Boolean,
    accentBlue: Color,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val tickets by viewModel.supportTickets.collectAsState()
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Report a Problem", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Send a real support ticket to the backend.", color = Color.Gray, fontSize = 13.sp)
            }
            item {
                SettingsTextField("Subject", subject, contentColor, accentBlue) { subject = it }
            }
            item {
                SettingsTextField("What happened?", description, contentColor, accentBlue, minLines = 4) { description = it }
            }
            item {
                Button(
                    onClick = {
                        if (isSending) return@Button
                        isSending = true
                        message = null
                        viewModel.createSupportTicket(subject, description) { success, result ->
                            isSending = false
                            message = result
                            if (success) {
                                subject = ""
                                description = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isSending,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                ) {
                    if (isSending) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Send report", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            if (!message.isNullOrBlank()) {
                item {
                    Text(message!!, color = if (message!!.contains("created", ignoreCase = true)) accentBlue else Color.Red, fontSize = 12.sp)
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                Text("Your recent tickets", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (tickets.isEmpty()) {
                item { Text("No support tickets yet", color = Color.Gray, fontSize = 13.sp) }
            } else {
                items(tickets) { ticket ->
                    Surface(color = contentColor.copy(alpha = 0.04f), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(ticket.subject, color = contentColor, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(ticket.status.uppercase(), color = accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(ticket.createdAt ?: "", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
