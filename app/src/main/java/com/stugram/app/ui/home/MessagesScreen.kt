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
import com.stugram.app.data.remote.model.DirectConversationModel
import com.stugram.app.data.remote.model.GroupConversationModel
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.repository.ChatRepository
import com.stugram.app.data.repository.FollowRepository
import com.stugram.app.data.repository.GroupChatRepository
import com.stugram.app.data.repository.MessagesInboxCache
import com.stugram.app.data.repository.ProfileRepository
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
    onNavigateToProfile: (String) -> Unit = {},
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
    val profileRepository = remember { ProfileRepository() }
    // Background color: dark at night, light during the day
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color(0xFFF2F2F2)
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val secondaryContentColor = contentColor.copy(alpha = 0.6f)
    val accentBlue = Color(0xFF00A3FF)

    var selectedSection by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var inboxRefreshNonce by remember { mutableIntStateOf(0) }
    var isInboxRefreshing by remember { mutableStateOf(false) }
    var chatsLoading by remember { mutableStateOf(MessagesInboxCache.chats.value.isEmpty()) }
    var groupsLoading by remember { mutableStateOf(MessagesInboxCache.groups.value.isEmpty()) }
    var requestsLoading by remember { mutableStateOf(MessagesInboxCache.requests.value.isEmpty()) }
    var suggestionsLoading by remember { mutableStateOf(MessagesInboxCache.suggestedProfiles.value.isEmpty()) }
    var processingRequestIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var openingSuggestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var requestActionError by remember { mutableStateOf<String?>(null) }
    var showComposerActions by remember { mutableStateOf(false) }
    var showGroupCreate by remember { mutableStateOf(false) }
    var suggestedProfiles by remember { mutableStateOf(MessagesInboxCache.suggestedProfiles.value) }

    val scope = rememberCoroutineScope()
    val cachedChats by MessagesInboxCache.chats.collectAsState()
    val cachedGroups by MessagesInboxCache.groups.collectAsState()
    val cachedRequests by MessagesInboxCache.requests.collectAsState()
    val cachedSuggestedProfiles by MessagesInboxCache.suggestedProfiles.collectAsState()
    val presenceMap by chatSocketManager.presenceState.collectAsState()
    val effectiveRefreshing = isRefreshing || isInboxRefreshing

    fun requestInboxRefresh() {
        inboxRefreshNonce += 1
    }

    fun setRequestProcessing(requestId: String, active: Boolean) {
        processingRequestIds = if (active) {
            processingRequestIds + requestId
        } else {
            processingRequestIds - requestId
        }
    }

    fun removeRequestFromCache(requestId: String) {
        MessagesInboxCache.updateRequests(cachedRequests.filterNot { it.requestId == requestId })
    }

    fun prependConversationToInbox(item: ChatMessage) {
        MessagesInboxCache.upsertChat(item)
    }

    LaunchedEffect(Unit) {
        chatSocketManager.connect()
    }

    LaunchedEffect(Unit) {
        chatSocketManager.connectionEvents.collect { event ->
            if (event is com.stugram.app.core.socket.SocketConnectionEvent.Reconnected) {
                requestInboxRefresh()
            }
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.conversationUpdates.collect {
            MessagesInboxCache.upsertChat(it.toInboxChat())
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.messageSeenEvents.collect {
            it.conversationId?.let { conversationId ->
                MessagesInboxCache.patchChat(conversationId) { chat ->
                    chat.copy(unreadCount = 0)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.messageReactionEvents.collect {
            val conversationId = it.conversationId
            val message = it.message
            if (!conversationId.isNullOrBlank()) {
                MessagesInboxCache.patchChat(conversationId) { chat ->
                    if (chat.lastMessage == (message.text ?: "No messages yet")) {
                        chat.copy(time = formatRelativeTime(message.updatedAt ?: message.createdAt))
                    } else {
                        chat
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.messageDeletedEvents.collect {
            it.conversationId?.let { conversationId ->
                MessagesInboxCache.patchChat(conversationId) { chat ->
                    chat.copy(lastMessage = "Message removed")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupConversationUpdates.collect {
            MessagesInboxCache.upsertGroup(it.toInboxGroup())
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMessageSeenEvents.collect {
            it.groupId?.let { groupId ->
                MessagesInboxCache.patchGroup(groupId) { group ->
                    group.copy(unreadCount = 0)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMessageReactionEvents.collect {
            val groupId = it.groupId
            val message = it.message
            if (!groupId.isNullOrBlank()) {
                MessagesInboxCache.patchGroup(groupId) { group ->
                    if (group.lastMessage == (message.text ?: "No messages yet")) {
                        group.copy(time = formatRelativeTime(message.updatedAt ?: message.createdAt))
                    } else {
                        group
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMessageDeletedEvents.collect {
            it.groupId?.let { groupId ->
                MessagesInboxCache.patchGroup(groupId) { group ->
                    group.copy(lastMessage = "Message removed")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMemberAddedEvents.collect {
            it.group?.let { group -> MessagesInboxCache.upsertGroup(group.toInboxGroup()) }
        }
    }

    LaunchedEffect(Unit) {
        chatSocketManager.groupMemberRemovedEvents.collect {
            it.groupId?.let { groupId ->
                MessagesInboxCache.patchGroup(groupId) { group -> group }
            }
        }
    }

    LaunchedEffect(cachedSuggestedProfiles) {
        if (cachedSuggestedProfiles.isNotEmpty()) {
            suggestedProfiles = cachedSuggestedProfiles
            suggestionsLoading = false
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) requestInboxRefresh()
    }

    LaunchedEffect(inboxRefreshNonce) {
        if (inboxRefreshNonce == 0 && MessagesInboxCache.hasWarmInbox()) {
            chatsLoading = false
            groupsLoading = false
            requestsLoading = false
            suggestionsLoading = false
            suggestedProfiles = cachedSuggestedProfiles
            return@LaunchedEffect
        }
        if (inboxRefreshNonce > 0) delay(450)
        if (isInboxRefreshing) return@LaunchedEffect
        isInboxRefreshing = true
        chatsLoading = cachedChats.isEmpty()
        groupsLoading = cachedGroups.isEmpty()
        requestsLoading = cachedRequests.isEmpty()

        val summaryDeferred = async {
            runCatching { chatRepository.getSummary() }.getOrNull()
        }

        val chatDeferred = async {
            runCatching {
                val response = chatRepository.getConversations(page = 1, limit = 50)
                if (!response.isSuccessful) return@runCatching null
                response.body()?.data.orEmpty().map { item ->
                    ChatMessage(
                        backendId = item.id,
                        userId = item.otherParticipant?.id,
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
                val response = groupChatRepository.getGroupChats(page = 1, limit = 50)
                if (!response.isSuccessful) return@runCatching null
                response.body()?.data.orEmpty().map { item ->
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
                    RequestInboxItem(
                        requestId = item.id,
                        requesterId = item.requester?.id,
                        username = item.requester?.username.orEmpty(),
                        name = item.requester?.fullName?.takeIf { it.isNotBlank() }
                            ?: item.requester?.username
                            ?: "Follow request",
                        avatar = item.requester?.avatar,
                        bio = item.requester?.bio,
                        status = item.status
                    )
                }
            }.getOrNull()
        }
        val suggestionsDeferred = async {
            runCatching {
                profileRepository.getProfileSuggestions(page = 1, limit = 20).body()?.data.orEmpty().map { item ->
                    SuggestedChatProfile(
                        userId = item.id,
                        username = item.username,
                        name = item.fullName.takeIf { it.isNotBlank() } ?: item.username,
                        avatar = item.avatar,
                        bio = item.bio,
                        followStatus = item.followStatus
                    )
                }
            }.getOrNull()
        }

        summaryDeferred.await()?.takeIf { it.isSuccessful }?.body()?.data?.let { summary ->
            MessagesInboxCache.setDirectUnreadSummary(summary.totalUnreadMessages)
        }
        chatDeferred.await()?.let { MessagesInboxCache.updateChats(it) }
        groupDeferred.await()?.let { MessagesInboxCache.updateGroups(it) }
        requestDeferred.await()?.let { MessagesInboxCache.updateRequests(it) }
        suggestionsDeferred.await()?.let {
            suggestedProfiles = it
            MessagesInboxCache.updateSuggestedProfiles(it)
        }
        MessagesInboxCache.markFullRefresh()

        chatsLoading = false
        groupsLoading = false
        requestsLoading = false
        suggestionsLoading = false
        isInboxRefreshing = false
    }

    val normalizedSearch = searchQuery.trim()
    val visibleChats = remember(cachedChats, normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            cachedChats
        } else {
            cachedChats.filter {
                it.name.contains(normalizedSearch, ignoreCase = true) ||
                    (it.username?.contains(normalizedSearch, ignoreCase = true) == true) ||
                    it.lastMessage.contains(normalizedSearch, ignoreCase = true)
            }
        }
    }
    val visibleGroups = remember(cachedGroups, normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            cachedGroups
        } else {
            cachedGroups.filter {
                it.name.contains(normalizedSearch, ignoreCase = true) ||
                    it.lastMessage.contains(normalizedSearch, ignoreCase = true)
            }
        }
    }
    val visibleRequests = remember(cachedRequests, normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            cachedRequests
        } else {
            cachedRequests.filter {
                it.name.contains(normalizedSearch, ignoreCase = true) ||
                    it.username.contains(normalizedSearch, ignoreCase = true) ||
                    (it.bio?.contains(normalizedSearch, ignoreCase = true) == true)
            }
        }
    }
    val visibleSuggestedProfiles = remember(suggestedProfiles, visibleChats, normalizedSearch) {
        val existingUserIds = visibleChats.mapNotNull { it.userId }.toSet()
        val existingUsernames = visibleChats.mapNotNull { it.username }.toSet()
        suggestedProfiles.filter { profile ->
            profile.userId !in existingUserIds &&
                profile.username !in existingUsernames &&
                (
                    normalizedSearch.isBlank() ||
                        profile.name.contains(normalizedSearch, ignoreCase = true) ||
                        profile.username.contains(normalizedSearch, ignoreCase = true) ||
                        (profile.bio?.contains(normalizedSearch, ignoreCase = true) == true)
                    )
        }
    }

    PullToRefreshBox(
        isRefreshing = effectiveRefreshing,
        onRefresh = {
            if (!effectiveRefreshing) {
                requestInboxRefresh()
            }
        },
        modifier = Modifier.fillMaxSize().background(backgroundColor),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = rememberPullToRefreshState(),
                isRefreshing = effectiveRefreshing,
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
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = secondaryContentColor,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    },
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

                Spacer(Modifier.width(10.dp))

                IconButton(
                    onClick = { showComposerActions = true },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(accentBlue.copy(alpha = 0.12f))
                ) {
                    Icon(Icons.Default.Edit, "Compose", tint = accentBlue)
                }
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
                if (normalizedSearch.isNotBlank()) {
                    Text(
                        text = "Search",
                        color = secondaryContentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            requestActionError?.let { message ->
                Text(
                    text = message,
                    color = Color.Red.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
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
                            when {
                                visibleChats.isNotEmpty() || visibleSuggestedProfiles.isNotEmpty() || suggestionsLoading -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                    items(visibleChats, key = { it.backendId ?: it.username ?: it.name }) { chat ->
                                        ChatItem(
                                            chat = chat,
                                            contentColor = contentColor,
                                            secondaryColor = secondaryContentColor,
                                            accent = accentBlue,
                                            isOnline = chat.userId?.let { presenceMap[it]?.isOnline == true } == true,
                                            onChatClick = { name ->
                                            chat.backendId?.let { MessagesInboxCache.clearChatUnread(it) }
                                            onNavigateToChat(chat.backendId ?: "", chat.username ?: name, false)
                                            }
                                        )
                                    }
                                    if (visibleSuggestedProfiles.isNotEmpty() || suggestionsLoading) {
                                        item("suggested_header") {
                                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                                                HorizontalDivider(color = secondaryContentColor.copy(alpha = 0.12f))
                                                Spacer(Modifier.height(14.dp))
                                                Text(
                                                    text = if (normalizedSearch.isBlank()) "Suggested profiles" else "Matching profiles",
                                                    color = accentBlue,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Tap any profile to start chatting.",
                                                    color = secondaryContentColor,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        if (suggestionsLoading && visibleSuggestedProfiles.isEmpty()) {
                                            items(3) { SuggestedChatProfileSkeleton(isDarkMode = isDarkMode) }
                                        } else {
                                            items(visibleSuggestedProfiles, key = { it.userId }) { profile ->
                                                SuggestedChatProfileRow(
                                                    profile = profile,
                                                    isDarkMode = isDarkMode,
                                                    accent = accentBlue,
                                                    isOpening = openingSuggestionIds.contains(profile.userId),
                                                    onOpen = {
                                                        if (openingSuggestionIds.contains(profile.userId)) return@SuggestedChatProfileRow
                                                        openingSuggestionIds = openingSuggestionIds + profile.userId
                                                        scope.launch {
                                                            val conversationResponse = runCatching {
                                                                chatRepository.createConversation(profile.userId)
                                                            }.getOrNull()
                                                            if (conversationResponse?.isSuccessful == true) {
                                                                val conversation = conversationResponse.body()?.data
                                                                val chatItem = ChatMessage(
                                                                    backendId = conversation?.id,
                                                                    userId = profile.userId,
                                                                    name = profile.name,
                                                                    username = profile.username,
                                                                    avatar = profile.avatar,
                                                                    lastMessage = conversation?.lastMessage ?: "No messages yet",
                                                                    time = formatRelativeTime(conversation?.lastMessageAt),
                                                                    unreadCount = conversation?.unreadCount ?: 0
                                                                )
                                                                prependConversationToInbox(chatItem)
                                                                onNavigateToChat(conversation?.id.orEmpty(), profile.username, false)
                                                            }
                                                            openingSuggestionIds = openingSuggestionIds - profile.userId
                                                        }
                                                    }
                                                )
                                            }
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
                            when {
                                visibleGroups.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                    items(visibleGroups, key = { it.backendId ?: it.name }) { group ->
                                        GroupChatItem(group, contentColor, secondaryContentColor, accentBlue) { id, name ->
                                            if (id.isNotBlank()) {
                                                MessagesInboxCache.clearGroupUnread(id)
                                            }
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
                            when {
                                visibleRequests.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                    items(visibleRequests, key = { it.requestId }) { request ->
                                        RequestCard(
                                            request = request,
                                            isDarkMode = isDarkMode,
                                            accent = accentBlue,
                                            isProcessing = processingRequestIds.contains(request.requestId),
                                            onOpenProfile = {
                                                if (request.username.isNotBlank()) {
                                                    onNavigateToProfile(request.username)
                                                }
                                            },
                                            onAccept = {
                                                val requesterId = request.requesterId
                                                if (requesterId.isNullOrBlank()) {
                                                    requestActionError = "Could not open chat for this request"
                                                } else {
                                                    scope.launch {
                                                    requestActionError = null
                                                    setRequestProcessing(request.requestId, true)
                                                    val acceptResponse = runCatching {
                                                        followRepository.acceptFollowRequest(request.requestId)
                                                    }.getOrNull()
                                                    if (acceptResponse?.isSuccessful != true) {
                                                        requestActionError = acceptResponse?.body()?.message
                                                            ?: acceptResponse?.message().orEmpty().ifBlank { "Could not accept follow request" }
                                                        setRequestProcessing(request.requestId, false)
                                                        return@launch
                                                    }

                                                    val conversationResponse = runCatching {
                                                        chatRepository.createConversation(requesterId)
                                                    }.getOrNull()

                                                    removeRequestFromCache(request.requestId)

                                                    if (conversationResponse?.isSuccessful == true) {
                                                        val conversation = conversationResponse.body()?.data
                                                        val chatItem = ChatMessage(
                                                            backendId = conversation?.id,
                                                            userId = requesterId,
                                                            name = request.name,
                                                            username = request.username,
                                                            avatar = request.avatar,
                                                            lastMessage = "You can message each other now",
                                                            time = "Now",
                                                            unreadCount = 0
                                                        )
                                                        prependConversationToInbox(chatItem)
                                                        selectedSection = 0
                                                        onNavigateToChat(conversation?.id.orEmpty(), request.username, false)
                                                    } else {
                                                        requestActionError = conversationResponse?.body()?.message
                                                            ?: conversationResponse?.message().orEmpty().ifBlank { "Request accepted, but chat could not be opened yet" }
                                                        requestInboxRefresh()
                                                    }
                                                    setRequestProcessing(request.requestId, false)
                                                }
                                                }
                                            },
                                            onDecline = {
                                                scope.launch {
                                                    requestActionError = null
                                                    setRequestProcessing(request.requestId, true)
                                                    val response = runCatching {
                                                        followRepository.rejectFollowRequest(request.requestId)
                                                    }.getOrNull()
                                                    if (response?.isSuccessful == true) {
                                                        removeRequestFromCache(request.requestId)
                                                    } else {
                                                        requestActionError = response?.body()?.message
                                                            ?: response?.message().orEmpty().ifBlank { "Could not decline follow request" }
                                                    }
                                                    setRequestProcessing(request.requestId, false)
                                                }
                                            },
                                            onBlock = {
                                                val requesterId = request.requesterId
                                                if (requesterId.isNullOrBlank()) {
                                                    requestActionError = "Could not block this user"
                                                } else {
                                                    scope.launch {
                                                    requestActionError = null
                                                    setRequestProcessing(request.requestId, true)
                                                    val blockResponse = runCatching {
                                                        chatRepository.blockUser(requesterId)
                                                    }.getOrNull()
                                                    if (blockResponse?.isSuccessful == true) {
                                                        runCatching { followRepository.rejectFollowRequest(request.requestId) }
                                                        removeRequestFromCache(request.requestId)
                                                    } else {
                                                        requestActionError = blockResponse?.body()?.message
                                                            ?: blockResponse?.message().orEmpty().ifBlank { "Could not block this user" }
                                                    }
                                                    setRequestProcessing(request.requestId, false)
                                                }
                                                }
                                            }
                                        )
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

    if (showComposerActions) {
        ModalBottomSheet(
            onDismissRequest = { showComposerActions = false },
            containerColor = if (isDarkMode) Color(0xFF15171B) else Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Start something new",
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ComposerActionRow(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "New chat",
                    subtitle = "Switch to direct messages and use search to start a conversation.",
                    accent = accentBlue,
                    contentColor = contentColor,
                    secondaryColor = secondaryContentColor
                ) {
                    showComposerActions = false
                    selectedSection = 0
                }
                ComposerActionRow(
                    icon = Icons.Default.Groups,
                    title = "Create group",
                    subtitle = "Pick members, choose a name, and open the new group chat.",
                    accent = accentBlue,
                    contentColor = contentColor,
                    secondaryColor = secondaryContentColor
                ) {
                    showComposerActions = false
                    showGroupCreate = true
                }
            }
        }
    }

    if (showGroupCreate) {
        GroupCreateScreen(
            isDarkMode = isDarkMode,
            accentBlue = accentBlue,
            onClose = { showGroupCreate = false },
            onGroupCreated = { group ->
                showGroupCreate = false
                MessagesInboxCache.upsertGroup(group)
                onNavigateToGroupChat(group.backendId.orEmpty(), group.name)
            }
        )
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
fun ChatItem(chat: ChatMessage, contentColor: Color, secondaryColor: Color, accent: Color, isOnline: Boolean = false, onChatClick: (String) -> Unit) {
    val isUnread = chat.unreadCount > 0
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChatClick(chat.name) }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AppAvatar(
                imageModel = chat.avatar,
                name = chat.name,
                username = chat.username ?: chat.name,
                modifier = Modifier.size(56.dp),
                isDarkMode = contentColor == Color.White,
                fontSize = 20.sp
            )
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00C853))
                        .border(1.5.dp, if (contentColor == Color.White) Color(0xFF0F0F0F) else Color.White, CircleShape)
                )
            }
        }
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
data class ChatMessage(val backendId: String? = null, val userId: String? = null, val name: String, val username: String? = null, val avatar: String? = null, val lastMessage: String, val time: String, val unreadCount: Int = 0)
data class GroupChat(val backendId: String? = null, val name: String, val avatar: String? = null, val lastMessage: String, val time: String, val unreadCount: Int = 0)
data class RequestInboxItem(
    val requestId: String,
    val requesterId: String? = null,
    val username: String,
    val name: String,
    val avatar: String? = null,
    val bio: String? = null,
    val status: String = "pending"
)
data class SuggestedChatProfile(
    val userId: String,
    val username: String,
    val name: String,
    val avatar: String? = null,
    val bio: String? = null,
    val followStatus: String? = null
)

@Composable
private fun ComposerActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    contentColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(contentColor.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(title, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = secondaryColor, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = secondaryColor)
    }
}

@Composable
fun RequestCard(
    request: RequestInboxItem,
    isDarkMode: Boolean,
    accent: Color,
    isProcessing: Boolean,
    onOpenProfile: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onBlock: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val secondary = contentColor.copy(alpha = 0.64f)
    val surface = if (isDarkMode) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.03f)
    val outline = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(surface)
            .border(1.dp, outline, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenProfile() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppAvatar(
                imageModel = request.avatar,
                name = request.name,
                username = request.username,
                modifier = Modifier.size(54.dp),
                isDarkMode = isDarkMode,
                fontSize = 19.sp
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                Text(request.name, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("@${request.username}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    request.bio?.takeIf { it.isNotBlank() } ?: "Requested to follow you",
                    color = secondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = secondary)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onAccept,
                enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Accept", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = onDecline,
                enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, outline)
            ) {
                Text("Decline", color = contentColor, fontWeight = FontWeight.SemiBold)
            }
        }

        TextButton(
            onClick = onBlock,
            enabled = !isProcessing,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
        ) {
            Text("Block user", color = Color.Red.copy(alpha = 0.92f), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SuggestedChatProfileRow(
    profile: SuggestedChatProfile,
    isDarkMode: Boolean,
    accent: Color,
    isOpening: Boolean,
    onOpen: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val secondary = contentColor.copy(alpha = 0.64f)
    val surface = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
    val outline = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(1.dp, outline, RoundedCornerShape(20.dp))
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppAvatar(
            imageModel = profile.avatar,
            name = profile.name,
            username = profile.username,
            modifier = Modifier.size(52.dp),
            isDarkMode = isDarkMode,
            fontSize = 18.sp
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
            Text(
                text = profile.name,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${profile.username}",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = profile.bio?.takeIf { it.isNotBlank() } ?: "Start a conversation",
                color = secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Button(
            onClick = onOpen,
            enabled = !isOpening,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            if (isOpening) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text("Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SuggestedChatProfileSkeleton(
    isDarkMode: Boolean
) {
    val pulse by rememberInfiniteTransition(label = "suggestion_skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "suggestion_skeleton_alpha"
    )
    val skeletonColor = if (isDarkMode) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(skeletonColor.copy(alpha = 0.04f * pulse))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(skeletonColor.copy(alpha = 0.08f * pulse))
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(skeletonColor.copy(alpha = 0.08f * pulse))
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(skeletonColor.copy(alpha = 0.07f * pulse))
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(skeletonColor.copy(alpha = 0.06f * pulse))
            )
        }
        Box(
            modifier = Modifier
                .width(62.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(skeletonColor.copy(alpha = 0.1f * pulse))
        )
    }
}

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

private fun DirectConversationModel.toInboxChat(): ChatMessage =
    ChatMessage(
        backendId = id,
        userId = otherParticipant?.id,
        name = otherParticipant?.fullName?.takeIf { it.isNotBlank() }
            ?: otherParticipant?.username
            ?: "Unknown user",
        username = otherParticipant?.username,
        avatar = otherParticipant?.avatar,
        lastMessage = lastMessage ?: "No messages yet",
        time = formatRelativeTime(lastMessageAt),
        unreadCount = unreadCount
    )

private fun GroupConversationModel.toInboxGroup(): GroupChat =
    GroupChat(
        backendId = id,
        name = name,
        avatar = avatar,
        lastMessage = lastMessage ?: "No messages yet",
        time = formatRelativeTime(lastMessageAt),
        unreadCount = unreadCount
    )
