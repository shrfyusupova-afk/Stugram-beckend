package com.stugram.app.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stugram.app.ui.theme.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.platform.LocalContext
import com.stugram.app.core.socket.ChatSocketManager
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.repository.ChatRepository
import com.stugram.app.data.repository.FollowRepository
import com.stugram.app.data.repository.GroupChatRepository
import com.stugram.app.data.repository.MessagesInboxCache
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    isDarkMode: Boolean, 
    onBack: () -> Unit,
    onNavigateToChat: (String, String, Boolean) -> Unit,
    onNavigateToGroupChat: (String, String) -> Unit,
    onNavigateToPost: (String) -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context.applicationContext) }
    val chatSocketManager = remember { ChatSocketManager.getInstance(tokenManager) }
    val chatRepository = remember {
        RetrofitClient.init(context.applicationContext)
        ChatRepository()
    }
    val groupChatRepository = remember { GroupChatRepository() }
    val followRepository = remember { FollowRepository() }
    // Background color: dark at night, light during the day
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color(0xFFF2F2F2)
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val secondaryContentColor = contentColor.copy(alpha = 0.6f)
    val accentBlue = Color(0xFF00A3FF)

    var selectedSection by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchLoading by remember { mutableStateOf(false) }
    var chatSearchResults by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inboxRefreshNonce by remember { mutableIntStateOf(0) }
    var chatsLoading by remember { mutableStateOf(MessagesInboxCache.chats.value.isEmpty()) }
    var groupsLoading by remember { mutableStateOf(MessagesInboxCache.groups.value.isEmpty()) }
    var requestsLoading by remember { mutableStateOf(MessagesInboxCache.requests.value.isEmpty()) }

    val scope = rememberCoroutineScope()
    val cachedChats by MessagesInboxCache.chats.collectAsState()
    val cachedGroups by MessagesInboxCache.groups.collectAsState()
    val cachedRequests by MessagesInboxCache.requests.collectAsState()

    LaunchedEffect(Unit) {
        chatSocketManager.connect()
    }

    LaunchedEffect(Unit) {
        chatSocketManager.connectionEvents.collect { event ->
            if (event is com.stugram.app.core.socket.SocketConnectionEvent.Reconnected) {
                inboxRefreshNonce += 1
            }
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.conversationUpdates.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.messageSeenEvents.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.messageReactionEvents.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.messageDeletedEvents.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupConversationUpdates.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMessageSeenEvents.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMessageReactionEvents.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMessageDeletedEvents.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMemberAddedEvents.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMemberRemovedEvents.collect {
            inboxRefreshNonce += 1
        }
    }

    LaunchedEffect(inboxRefreshNonce, isRefreshing) {
        chatsLoading = cachedChats.isEmpty()
        groupsLoading = cachedGroups.isEmpty()
        requestsLoading = cachedRequests.isEmpty()

        val chatDeferred = async {
            runCatching {
                chatRepository.getConversations(page = 1, limit = 50).body()?.data.orEmpty().map { item ->
                    ChatMessage(
                        backendId = item.id,
                        name = item.otherParticipant?.fullName?.takeIf { it.isNotBlank() }
                            ?: item.otherParticipant?.username
                            ?: "Unknown user",
                        username = item.otherParticipant?.username,
                        avatar = item.otherParticipant?.avatar,
                        lastMessage = item.lastMessage ?: "No messages yet",
                        time = formatRelativeTime(item.lastMessageAt),
                        unreadCount = item.unreadCount
                    )
                }
            }.getOrNull()
        }

        val groupDeferred = async {
            runCatching {
                groupChatRepository.getGroupChats(page = 1, limit = 50).body()?.data.orEmpty().map { item ->
                    GroupChat(
                        backendId = item.id,
                        name = item.name,
                        avatar = item.avatar,
                        lastMessage = item.lastMessage ?: "No messages yet",
                        time = formatRelativeTime(item.lastMessageAt),
                        unreadCount = item.unreadCount
                    )
                }
            }.getOrNull()
        }

        val requestDeferred = async {
            runCatching {
                followRepository.getFollowRequests(page = 1, limit = 20).body()?.data.orEmpty().map { item ->
                    ChatMessage(
                        backendId = item.id,
                        name = item.requester?.fullName?.takeIf { it.isNotBlank() }
                            ?: item.requester?.username
                            ?: "Follow request",
                        username = item.requester?.username,
                        avatar = item.requester?.avatar,
                        lastMessage = "Requested to follow you",
                        time = item.status.replaceFirstChar { it.uppercase() },
                        unreadCount = if (item.status == "pending") 1 else 0
                    )
                }
            }.getOrNull()
        }

        chatDeferred.await()?.let { MessagesInboxCache.updateChats(it) }
        groupDeferred.await()?.let { MessagesInboxCache.updateGroups(it) }
        requestDeferred.await()?.let { MessagesInboxCache.updateRequests(it) }

        chatsLoading = false
        groupsLoading = false
        requestsLoading = false
    }

    LaunchedEffect(searchQuery, selectedSection) {
        if (selectedSection != 0) {
            chatSearchResults = emptyList()
            searchLoading = false
            return@LaunchedEffect
        }

        val trimmed = searchQuery.trim()
        if (trimmed.isBlank()) {
            chatSearchResults = emptyList()
            searchLoading = false
            return@LaunchedEffect
        }

        searchLoading = true
        delay(250)
        val response = runCatching { chatRepository.searchConversations(trimmed, page = 1, limit = 50) }.getOrNull()
        chatSearchResults = if (response?.isSuccessful == true) {
            response.body()?.data.orEmpty().map { item ->
                ChatMessage(
                    backendId = item.id,
                    name = item.otherParticipant?.fullName?.takeIf { it.isNotBlank() }
                        ?: item.otherParticipant?.username
                        ?: "Unknown user",
                    username = item.otherParticipant?.username,
                    avatar = item.otherParticipant?.avatar,
                    lastMessage = item.lastMessage ?: "No messages yet",
                    time = formatRelativeTime(item.lastMessageAt),
                    unreadCount = item.unreadCount
                )
            }
        } else {
            emptyList()
        }
        searchLoading = false
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(backgroundColor),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = rememberPullToRefreshState(),
                isRefreshing = isRefreshing,
                containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White,
                color = accentBlue,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = accentBlue)
                }
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (selectedSection == 1) "Search groups..." else "Search messages...", color = secondaryContentColor.copy(0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = secondaryContentColor, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = contentColor.copy(alpha = 0.05f),
                        unfocusedContainerColor = contentColor.copy(alpha = 0.05f),
                        focusedBorderColor = accentBlue,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = contentColor)
                )
            }

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (selectedSection) {
                        1 -> "Groups"
                        2 -> "Requests"
                        else -> "Messages"
                    },
                    color = accentBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (searchLoading && selectedSection == 0) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = accentBlue)
                }
            }

            // Custom Tab Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(contentColor.copy(alpha = 0.08f))
                    .border(0.5.dp, contentColor.copy(0.1f), RoundedCornerShape(20.dp))
            ) {
                val configuration = LocalConfiguration.current
                val screenWidth = configuration.screenWidthDp.dp
                val tabWidth = (screenWidth - 32.dp) / 3

                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedSection,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                    label = "indicator"
                )

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(accentBlue)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    val sections = listOf("Messages", "Groups", "Requests")
                    sections.forEachIndexed { index, title ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { selectedSection = index }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (selectedSection == index) Color.White else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = if (selectedSection == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Section Content
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedSection,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    },
                    label = "section_slide"
                ) { targetPage ->
                    when (targetPage) {
                        0 -> {
                            val visibleChats = if (searchQuery.isBlank()) cachedChats else chatSearchResults
                            when {
                                visibleChats.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                    items(visibleChats, key = { it.backendId ?: it.username ?: it.name }) { chat ->
                                        ChatItem(chat, contentColor, secondaryContentColor, accentBlue) { name ->
                                            onNavigateToChat(chat.backendId ?: "", chat.username ?: name, false)
                                        }
                                    }
                                }
                                chatsLoading -> MessagesSkeletonList(isDarkMode = isDarkMode)
                                else -> InboxEmptyState(
                                    title = if (searchQuery.isBlank()) "No conversations yet" else "No results",
                                    subtitle = if (searchQuery.isBlank()) "Start a chat and it will appear here." else "Try a different search term.",
                                    contentColor = contentColor,
                                    secondaryColor = secondaryContentColor,
                                    accent = accentBlue
                                )
                            }
                        }
                        1 -> {
                            val visibleGroups = if (searchQuery.isBlank()) cachedGroups else cachedGroups.filter {
                                it.name.contains(searchQuery, ignoreCase = true) || it.lastMessage.contains(searchQuery, ignoreCase = true)
                            }
                            when {
                                visibleGroups.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                    items(visibleGroups, key = { it.backendId ?: it.name }) { group ->
                                        GroupChatItem(group, contentColor, secondaryContentColor, accentBlue) { id, name ->
                                            onNavigateToGroupChat(id, name)
                                        }
                                    }
                                }
                                groupsLoading -> MessagesSkeletonList(isDarkMode = isDarkMode)
                                else -> InboxEmptyState(
                                    title = if (searchQuery.isBlank()) "No groups yet" else "No results",
                                    subtitle = if (searchQuery.isBlank()) "Groups you join will appear here." else "Try another search term.",
                                    contentColor = contentColor,
                                    secondaryColor = secondaryContentColor,
                                    accent = accentBlue
                                )
                            }
                        }
                        2 -> {
                            val visibleRequests = if (searchQuery.isBlank()) cachedRequests else cachedRequests.filter {
                                it.name.contains(searchQuery, ignoreCase = true) || it.username.orEmpty().contains(searchQuery, ignoreCase = true)
                            }
                            when {
                                visibleRequests.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                    items(visibleRequests, key = { it.backendId ?: it.name }) { request ->
                                        ChatItem(request, contentColor, secondaryContentColor, accentBlue) { name ->
                                            onNavigateToChat(request.backendId ?: "", request.username ?: name, true)
                                        }
                                    }
                                }
                                requestsLoading -> MessagesSkeletonList(isDarkMode = isDarkMode)
                                else -> InboxEmptyState(
                                    title = "No requests yet",
                                    subtitle = "When someone asks to follow you, it will appear here.",
                                    contentColor = contentColor,
                                    secondaryColor = secondaryContentColor,
                                    accent = accentBlue
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun NoteItem(noteData: NoteItemData, isDarkMode: Boolean, onClick: (Offset) -> Unit) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val bubbleBg = (if (isDarkMode) Color(0xFF262626) else Color.White).copy(alpha = 0.9f)
    val accentBlue = Color(0xFF00A3FF)
    var currentOffset by remember { mutableStateOf(Offset.Zero) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(85.dp)
            .onGloballyPositioned { coordinates ->
                currentOffset = coordinates.positionInRoot()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick(currentOffset) }
            )
    ) {
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.height(100.dp)) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).size(70.dp),
                shape = CircleShape,
                color = if (isDarkMode) Color(0xFF262626) else Color(0xFFE5E5E5),
                border = BorderStroke(0.5.dp, contentColor.copy(0.1f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = contentColor.copy(0.2f))
                }
            }

            if (noteData.note != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(bottom = 2.dp).widthIn(max = 85.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = bubbleBg,
                    border = BorderStroke(0.5.dp, contentColor.copy(0.1f)),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = noteData.note, color = contentColor, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 2, textAlign = TextAlign.Center, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (noteData.isMe) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(24.dp)
                        .background(accentBlue, CircleShape)
                        .border(2.dp, if (isDarkMode) Color(0xFF0F0F0F) else Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            text = if (noteData.isMe) "Sizning qaydingiz" else noteData.name,
            color = contentColor.copy(0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ReplyNoteModal(isDarkMode: Boolean, data: NoteItemData?, onDismiss: () -> Unit) {
    val bgColor = (if (isDarkMode) Color(0xFF262626) else Color.White).copy(alpha = 0.95f)
    val contentColor = if (isDarkMode) Color.White else Color.Black
    var replyText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .border(
                1.dp, 
                Brush.verticalGradient(listOf(contentColor.copy(0.1f), Color.Transparent)), 
                RoundedCornerShape(24.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.Gray.copy(0.3f)))
        Spacer(Modifier.height(20.dp))
        Text(text = data?.name ?: "", color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(60.dp), shape = CircleShape, color = contentColor.copy(0.05f)) {
                Icon(Icons.Default.Person, null, modifier = Modifier.padding(15.dp), tint = contentColor.copy(0.2f))
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp), 
                color = contentColor.copy(0.05f),
                border = BorderStroke(0.5.dp, contentColor.copy(0.1f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(text = data?.note ?: "", color = contentColor, fontSize = 14.sp)
                    Text(text = "Tarjimani ko'rish", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(
            value = replyText, 
            onValueChange = { replyText = it },
            placeholder = { 
                Text("${data?.name}ga javob berish...", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 14.sp) 
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            trailingIcon = {
                if (replyText.isNotEmpty()) {
                    IconButton(onClick = { onDismiss() }) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF00A3FF), modifier = Modifier.size(20.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = contentColor.copy(0.05f),
                unfocusedContainerColor = contentColor.copy(0.05f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = contentColor
            ),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 14.sp, color = contentColor)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ManageNoteModal(isDarkMode: Boolean, noteText: String, onDismiss: () -> Unit, onLeaveNew: () -> Unit, onDelete: () -> Unit) {
    val bgColor = (if (isDarkMode) Color(0xFF262626) else Color.White).copy(alpha = 0.95f)
    val contentColor = if (isDarkMode) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .border(1.dp, contentColor.copy(0.1f), RoundedCornerShape(24.dp))
            .padding(bottom = 24.dp, top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.Gray.copy(0.3f)))
        Spacer(Modifier.height(24.dp))
        
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.height(120.dp)) {
            Surface(modifier = Modifier.align(Alignment.BottomCenter).size(80.dp), shape = CircleShape, color = contentColor.copy(0.05f)) {
                Icon(Icons.Default.Person, null, modifier = Modifier.padding(20.dp), tint = contentColor.copy(0.2f))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(min = 100.dp, max = 220.dp),
                shape = RoundedCornerShape(20.dp), 
                color = if (isDarkMode) Color.Black.copy(0.3f) else Color.White, 
                border = BorderStroke(0.5.dp, contentColor.copy(0.1f)),
                shadowElevation = 8.dp
            ) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(noteText, color = contentColor, textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text("Do'stlar bilan ulashildi · Hozir", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLeaveNew,
            modifier = Modifier.fillMaxWidth(0.85f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3797EF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Yangi qayd qoldirish", fontWeight = FontWeight.Bold, color = Color.White)
        }
        TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
            Text("Qaydni o'chirish", color = Color.Red, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CreateNoteScreen(isDarkMode: Boolean, currentNote: String?, onDismiss: () -> Unit, onShare: (String) -> Unit) {
    var noteText by remember { mutableStateOf(currentNote ?: "") }
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val bgColor = (if (isDarkMode) Color(0xFF262626) else Color.White).copy(alpha = 0.95f)

    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(28.dp))
            .background(bgColor)
            .border(1.dp, contentColor.copy(0.1f), RoundedCornerShape(28.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = contentColor) }
            Button(
                onClick = { onShare(noteText) },
                colors = ButtonDefaults.buttonColors(containerColor = if (noteText.isNotEmpty()) Color(0xFF3797EF) else Color.Gray.copy(0.2f)),
                shape = RoundedCornerShape(20.dp), modifier = Modifier.height(36.dp)
            ) { Text("Ulashish", fontWeight = FontWeight.Bold, color = Color.White) }
        }
        Spacer(Modifier.height(30.dp))
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.height(120.dp)) {
            Surface(modifier = Modifier.align(Alignment.BottomCenter).size(80.dp), shape = CircleShape, color = contentColor.copy(0.05f)) {
                Icon(Icons.Default.Person, null, modifier = Modifier.padding(20.dp), tint = contentColor.copy(0.2f))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(min = 100.dp, max = 220.dp),
                shape = RoundedCornerShape(20.dp), 
                color = if (isDarkMode) Color.Black.copy(0.3f) else Color.White, 
                border = BorderStroke(0.5.dp, contentColor.copy(0.1f)),
                shadowElevation = 8.dp
            ) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(if (noteText.isEmpty()) "Fikr bilan ulashish..." else noteText, color = if (noteText.isEmpty()) Color.Gray else contentColor, textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        TextField(
            value = noteText, onValueChange = { if (it.length <= 60) noteText = it },
            placeholder = { Text("Nimalar haqida o'ylayapsiz?", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color(0xFF3797EF), focusedTextColor = contentColor),
            modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 16.sp, color = contentColor)
        )
        Text("${noteText.length}/60", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
fun ChatItem(chat: ChatMessage, contentColor: Color, secondaryColor: Color, accent: Color, onChatClick: (String) -> Unit) {
    val isUnread = chat.unreadCount > 0
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChatClick(chat.name) }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppAvatar(
            imageModel = chat.avatar,
            name = chat.name,
            username = chat.username ?: chat.name,
            modifier = Modifier.size(56.dp),
            isDarkMode = contentColor == Color.White,
            fontSize = 20.sp
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(chat.name, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                chat.lastMessage,
                color = contentColor,
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(chat.time, color = accent, fontSize = 11.sp)
            if (isUnread) Box(modifier = Modifier.padding(top = 4.dp).size(8.dp).background(accent, CircleShape))
        }
    }
}

@Composable
fun GroupChatItem(
    group: GroupChat, 
    contentColor: Color, 
    secondaryColor: Color, 
    accent: Color,
    onGroupClick: (String, String) -> Unit
) {
    val isUnread = group.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onGroupClick(group.backendId ?: group.name, group.name) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!group.avatar.isNullOrBlank()) {
            AppAvatar(
                imageModel = group.avatar,
                name = group.name,
                username = group.name,
                modifier = Modifier.size(56.dp),
                isDarkMode = contentColor == Color.White,
                fontSize = 20.sp
            )
        } else {
            Box(modifier = Modifier.size(56.dp)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.1f)) {
                    Icon(Icons.Default.Groups, null, modifier = Modifier.padding(12.dp), tint = accent)
                }
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(group.name, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(group.lastMessage, color = contentColor, fontSize = 13.sp, maxLines = 1, fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(group.time, color = accent, fontSize = 11.sp)
            if (isUnread) {
                Surface(
                    color = accent,
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = group.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagesSkeletonList(isDarkMode: Boolean) {
    val pulse by rememberInfiniteTransition(label = "messages_skeleton").animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "messages_skeleton_alpha"
    )
    val skeletonColor = if (isDarkMode) Color.White else Color.Black

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(skeletonColor.copy(alpha = 0.08f * pulse))
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Box(
                        modifier = Modifier
                            .height(14.dp)
                            .fillMaxWidth(0.52f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(skeletonColor.copy(alpha = 0.08f * pulse))
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .height(12.dp)
                            .fillMaxWidth(0.78f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(skeletonColor.copy(alpha = 0.06f * pulse))
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .height(10.dp)
                            .width(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(skeletonColor.copy(alpha = 0.06f * pulse))
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(skeletonColor.copy(alpha = 0.08f * pulse))
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxEmptyState(
    title: String,
    subtitle: String,
    contentColor: Color,
    secondaryColor: Color,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

enum class NoteModalType { NONE, CREATE, VIEW_REPLY, MANAGE_OWN }
data class NoteItemData(val name: String, val note: String? = null, val isMe: Boolean = false)
data class ChatMessage(val backendId: String? = null, val name: String, val username: String? = null, val avatar: String? = null, val lastMessage: String, val time: String, val unreadCount: Int = 0)
data class GroupChat(val backendId: String? = null, val name: String, val avatar: String? = null, val lastMessage: String, val time: String, val unreadCount: Int = 0)

private fun formatRelativeTime(value: String?): String {
    if (value.isNullOrBlank()) return "Now"
    val millis = runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull() ?: return "Now"
    return DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
