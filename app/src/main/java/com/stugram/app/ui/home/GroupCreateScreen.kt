package com.stugram.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.repository.GroupChatRepository
import com.stugram.app.data.repository.SearchRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupCreateScreen(
    isDarkMode: Boolean,
    accentBlue: Color,
    onClose: () -> Unit,
    onGroupCreated: (GroupChat) -> Unit
) {
    val context = LocalContext.current
    val searchRepository = remember {
        RetrofitClient.init(context.applicationContext)
        SearchRepository()
    }
    val groupRepository = remember { GroupChatRepository() }
    val scope = rememberCoroutineScope()
    val contentColor = if (isDarkMode) Color.White else Color(0xFF111827)
    val secondaryColor = contentColor.copy(alpha = 0.62f)
    val surface = if (isDarkMode) Color(0xFF111317) else Color.White
    val cardSurface = if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.03f)
    val outline = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)

    var groupName by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var searchLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<ProfileSummary>>(emptyList()) }
    var selectedMembers by remember { mutableStateOf<List<ProfileSummary>>(emptyList()) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var createError by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        avatarUri = uri
    }

    LaunchedEffect(query) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            searchResults = emptyList()
            searchLoading = false
            return@LaunchedEffect
        }

        searchLoading = true
        delay(250)
        val response = runCatching { searchRepository.searchUsers(normalized, page = 1, limit = 20) }.getOrNull()
        searchResults = if (response?.isSuccessful == true) {
            response.body()?.data.orEmpty().filter { candidate ->
                selectedMembers.none { it.id == candidate.id }
            }
        } else {
            emptyList()
        }
        searchLoading = false
    }

    fun toggleMember(profile: ProfileSummary) {
        selectedMembers = if (selectedMembers.any { it.id == profile.id }) {
            selectedMembers.filterNot { it.id == profile.id }
        } else {
            selectedMembers + profile
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = contentColor)
                }
                Text(
                    text = "Create group",
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = groupName.trim().isNotBlank() && selectedMembers.isNotEmpty() && !isCreating,
                    onClick = {
                        scope.launch {
                            createError = null
                            isCreating = true
                            val response = runCatching {
                                groupRepository.createGroupChat(
                                    context = context,
                                    name = groupName,
                                    memberIds = selectedMembers.map { it.id },
                                    avatarUri = avatarUri
                                )
                            }.getOrNull()

                            val group = response?.body()?.data
                            if (response?.isSuccessful == true && group != null) {
                                onGroupCreated(
                                    GroupChat(
                                        backendId = group.id,
                                        name = group.name,
                                        avatar = group.avatar,
                                        lastMessage = "Group created",
                                        time = "Now",
                                        unreadCount = 0
                                    )
                                )
                            } else {
                                createError = response?.body()?.message
                                    ?: response?.message().orEmpty().ifBlank { "Could not create group" }
                            }
                            isCreating = false
                        }
                    }
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentBlue)
                    } else {
                        Text("Create", color = accentBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    Surface(
                        color = cardSurface,
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(78.dp)
                                        .clip(CircleShape)
                                        .background(accentBlue.copy(alpha = 0.12f))
                                        .border(1.dp, accentBlue.copy(alpha = 0.2f), CircleShape)
                                        .clickable { avatarPicker.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarUri != null) {
                                        AppAvatar(
                                            imageModel = avatarUri,
                                            name = groupName.ifBlank { "Group" },
                                            username = null,
                                            modifier = Modifier.fillMaxSize(),
                                            isDarkMode = isDarkMode
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.AddAPhoto, null, tint = accentBlue)
                                            Text("Avatar", color = accentBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Spacer(Modifier.size(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Group details", color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Choose at least 1 member, set a group name, and optionally add an avatar.",
                                        color = secondaryColor,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = groupName,
                                onValueChange = { groupName = it.take(120) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Group name") },
                                placeholder = { Text("Weekend Crew") },
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = accentBlue,
                                    unfocusedBorderColor = outline,
                                    focusedTextColor = contentColor,
                                    unfocusedTextColor = contentColor
                                )
                            )
                        }
                    }

                    if (createError != null) {
                        Text(
                            text = createError.orEmpty(),
                            color = Color.Red.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search users") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search by username or full name") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBlue,
                            unfocusedBorderColor = outline,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = contentColor,
                            unfocusedTextColor = contentColor
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                }

                if (selectedMembers.isNotEmpty()) {
                    item {
                        Text("Selected members", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            selectedMembers.forEach { member ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = accentBlue.copy(alpha = 0.14f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "@${member.username}",
                                            color = accentBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        IconButton(
                                            onClick = { toggleMember(member) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Close, null, tint = accentBlue, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Members", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${selectedMembers.size} selected",
                            color = secondaryColor,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                when {
                    searchLoading -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = accentBlue, strokeWidth = 2.dp)
                            }
                        }
                    }

                    query.isBlank() -> {
                        item {
                            EmptyGroupCreateState(
                                title = "Search for members",
                                subtitle = "Start typing to find people and add them to this group.",
                                accentBlue = accentBlue,
                                contentColor = contentColor,
                                secondaryColor = secondaryColor
                            )
                        }
                    }

                    searchResults.isEmpty() -> {
                        item {
                            EmptyGroupCreateState(
                                title = "No users found",
                                subtitle = "Try a different username or full name.",
                                accentBlue = accentBlue,
                                contentColor = contentColor,
                                secondaryColor = secondaryColor
                            )
                        }
                    }

                    else -> {
                        items(searchResults, key = { it.id }) { member ->
                            GroupMemberPickRow(
                                member = member,
                                isDarkMode = isDarkMode,
                                accentBlue = accentBlue,
                                isSelected = selectedMembers.any { it.id == member.id },
                                onToggle = { toggleMember(member) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMemberPickRow(
    member: ProfileSummary,
    isDarkMode: Boolean,
    accentBlue: Color,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color(0xFF111827)
    val secondaryColor = contentColor.copy(alpha = 0.62f)
    val surface = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.025f)
    val outline = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(1.dp, if (isSelected) accentBlue.copy(alpha = 0.45f) else outline, RoundedCornerShape(20.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppAvatar(
            imageModel = member.avatar,
            name = member.fullName,
            username = member.username,
            modifier = Modifier.size(52.dp),
            isDarkMode = isDarkMode,
            fontSize = 18.sp
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
            Text(
                text = member.fullName.ifBlank { member.username },
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${member.username}",
                color = accentBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            val subtitle = listOfNotNull(member.school, member.region).firstOrNull()
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = secondaryColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isSelected) accentBlue else Color.Transparent)
                .border(1.dp, if (isSelected) accentBlue else outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun EmptyGroupCreateState(
    title: String,
    subtitle: String,
    accentBlue: Color,
    contentColor: Color,
    secondaryColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(accentBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Groups, null, tint = accentBlue, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = secondaryColor,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}
