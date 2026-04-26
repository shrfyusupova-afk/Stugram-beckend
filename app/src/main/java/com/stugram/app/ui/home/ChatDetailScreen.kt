@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.stugram.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import android.widget.Toast
import android.util.Log
import org.json.JSONObject
import java.util.UUID
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.DirectConversationModel
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.data.repository.ChatRepository
import com.stugram.app.data.repository.ChatPendingOutbox
import com.stugram.app.data.repository.ChatLocalCache
import com.stugram.app.data.repository.GroupChatRepository
import com.stugram.app.data.repository.MessagesInboxCache
import com.stugram.app.data.repository.PendingChatEnvelope
import com.stugram.app.data.repository.PendingChatPayload
import com.stugram.app.domain.chat.ChatMessageSnapshot
import com.stugram.app.domain.chat.ChatMessageStatus
import com.stugram.app.domain.chat.ChatReducer
import com.stugram.app.domain.chat.MessageSource
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.ui.theme.IosEmojiFont
import com.stugram.app.ui.theme.PremiumBlue
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import com.stugram.app.core.socket.ChatSocketManager
import com.stugram.app.core.notification.PushNotificationBus
import com.stugram.app.core.notification.ForegroundChatRegistry
import com.stugram.app.core.messaging.ForwardDraft
import com.stugram.app.core.messaging.ForwardDraftStore
import com.stugram.app.core.messaging.ChatOutboxScheduler
import com.stugram.app.core.socket.UserPresenceState
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    userName: String,
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    isRequest: Boolean = false,
    onNavigateToChat: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onNavigateToGroupChat: (String, String) -> Unit = { _, _ -> },
    onNavigateToPost: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val directViewModel: DirectChatViewModel = viewModel(
        factory = DirectChatViewModel.factory(
            application = application,
            conversationId = conversationId,
            userName = userName,
            isRequest = isRequest
        )
    )
    val state by directViewModel.uiState.collectAsStateWithLifecycle()
    val chatRepository = remember {
        RetrofitClient.init(context.applicationContext)
        ChatRepository()
    }
    val groupChatRepository = remember { GroupChatRepository() }
    val currentUser = state.currentUser
    val forwardDraft = state.forwardDraft
    val remoteProfile = state.remoteProfile
    val remotePresence = state.remotePresence
    val conversationDetail = state.conversationDetail
    val messages = state.messages
    val messageText = state.composerText
    val isInitialLoading = state.isInitialLoading
    val isLoadingMore = state.isLoadingMore
    val loadError = state.error?.message
    val isMuted = state.isMuted
    val isTyping = state.typingState is ChatTypingState.Direct
    val typingUser = (state.typingState as? ChatTypingState.Direct)?.username
    val backgroundColor = if (isDarkMode) Color.Black else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glassBorder = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(0.1f)
    val accentBlue = Color(0xFF00A3FF)
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val headerHeight = 110.dp
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pinnedBannerTop = statusBarTop + 64.dp
    
    var showChatInfo by remember { mutableStateOf(false) }
    var showSearchModal by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMemberProfile by remember { mutableStateOf(false) }
    var replyingToMessage by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var selectedMessageForActions by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var messageBounds by remember(conversationId) { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var editingMessage by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var showForwardPicker by remember(conversationId) { mutableStateOf(false) }
    var forwardSourceMessage by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var showDeleteDialogFor by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var deleteForEveryone by remember(conversationId) { mutableStateOf(false) }
    var highlightedMessageId by remember(conversationId) { mutableStateOf<String?>(null) }
    var isMemberProfileRefreshing by remember { mutableStateOf(false) }
    var animatedMessageIds by remember(conversationId) { mutableStateOf(setOf<String>()) }
    var pendingOutgoingEntryAnimation by remember(conversationId) { mutableStateOf(false) }
    var unseenNewMessages by remember(conversationId) { mutableIntStateOf(0) }
    var stickToLatest by remember(conversationId) { mutableStateOf(true) }
    var lastRenderedMessageKey by remember(conversationId) { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val unseenIncomingMessageKey = remember(messages) {
        messages
            .filter { !it.isMe && it.status != MessageStatus.SEEN }
            .mapNotNull { it.backendId }
            .distinct()
            .joinToString("|")
    }
    val isAtLatest by remember(messages, listState) {
        derivedStateOf {
            if (messages.isEmpty()) {
                true
            } else {
                val latestIndex = messages.lastIndex
                listState.layoutInfo.visibleItemsInfo.any { item -> item.index >= latestIndex - 1 }
            }
        }
    }

    LaunchedEffect(unseenIncomingMessageKey) {
        if (unseenIncomingMessageKey.isNotBlank()) {
            delay(180)
            directViewModel.onAction(DirectChatAction.MarkVisibleMessagesSeen)
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (isAtLatest) {
            stickToLatest = true
            unseenNewMessages = 0
        } else if (messages.isNotEmpty()) {
            stickToLatest = false
        }
    }

    LaunchedEffect(messages.lastOrNull()?.stableKeyVm) {
        val latest = messages.lastOrNull() ?: run {
            lastRenderedMessageKey = null
            return@LaunchedEffect
        }
        val latestKey = latest.stableKeyVm
        if (latestKey != lastRenderedMessageKey) {
            val previousKey = lastRenderedMessageKey
            val shouldScroll = stickToLatest || latest.isMe
            if (previousKey != null) {
                animatedMessageIds = animatedMessageIds + latestKey
                scope.launch {
                    delay(620)
                    animatedMessageIds = animatedMessageIds - latestKey
                }
            }
            if (latest.isMe && pendingOutgoingEntryAnimation) {
                pendingOutgoingEntryAnimation = false
            }
            lastRenderedMessageKey = latestKey
            if (shouldScroll) {
                listState.animateScrollToItem(messages.lastIndex)
                stickToLatest = true
                unseenNewMessages = 0
            } else {
                unseenNewMessages += 1
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, isLoadingMore, state.hasMore, messages.size) {
        if (!isInitialLoading && !isLoadingMore && state.hasMore && listState.firstVisibleItemIndex <= 2 && messages.isNotEmpty()) {
            directViewModel.onAction(DirectChatAction.LoadMore)
        }
    }

    suspend fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    DisposableEffect(conversationId) {
        ForegroundChatRegistry.activeConversationId = conversationId
        onDispose {
            if (ForegroundChatRegistry.activeConversationId == conversationId) {
                ForegroundChatRegistry.activeConversationId = null
            }
        }
    }

    val clipboardManager = LocalClipboardManager.current

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        val activeForwardDraft = forwardDraft?.takeIf { it.targetConversationId == conversationId }
        val pinnedMessage = conversationDetail?.pinnedMessage
        val firstUnreadIndex = remember(messages, currentUser?.id) {
            messages.indexOfFirst { !it.isMe && it.status != MessageStatus.SEEN }
        }
        val defaultWallpaperResId = remember(isDarkMode) {
            val resName = if (isDarkMode) "tun" else "kun"
            context.resources.getIdentifier(resName, "drawable", context.packageName)
        }
        if (defaultWallpaperResId != 0) {
            Image(
                painter = painterResource(defaultWallpaperResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback to existing banner behavior if the app doesn't yet include kun/tun drawables.
            AppBanner(
                imageModel = remoteProfile?.banner,
                title = remoteProfile?.fullName ?: userName,
                modifier = Modifier.fillMaxSize(),
                isDarkMode = isDarkMode
            )
        }

        if (isDarkMode) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // --- MESSAGES LIST ---
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = false,
                    verticalArrangement = Arrangement.Bottom,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (pinnedMessage != null) pinnedBannerTop + 46.dp else headerHeight + 8.dp,
                        bottom = 8.dp
                    )
                ) {
                    if (isInitialLoading && messages.isEmpty()) {
                        item("loading") {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 140.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accentBlue)
                            }
                        }
                    } else if (messages.isEmpty()) {
                        item("empty") {
                            ChatEmptyState(
                                title = if (isRequest) "No request messages yet" else "No messages yet",
                                subtitle = if (isRequest) "Open the profile or follow request actions to continue." else (loadError ?: "Start the conversation from the message field below."),
                                isDarkMode = isDarkMode
                            )
                        }
                    }
                    itemsIndexed(messages, key = { _, msg -> msg.stableKeyVm }) { index, message ->
                        val isSameDay = if (index > 0) {
                            isSameDay(message.timestamp, messages[index - 1].timestamp)
                        } else false

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (index == 0 || !isSameDay) {
                                DateHeader(message.timestamp, isDarkMode)
                            }
                            if (index == firstUnreadIndex) {
                                UnreadDividerChip(isDarkMode = isDarkMode)
                            }
                            MessageBubble(
                                message = message,
                                isDarkMode = isDarkMode,
                                currentUserId = currentUser?.id,
                                animateFromInput = animatedMessageIds.contains(message.stableKeyVm),
                                highlighted = message.backendId != null && message.backendId == highlightedMessageId,
                                onRetry = {
                                    (message.localId ?: message.backendId)?.let { retryId ->
                                        directViewModel.onAction(DirectChatAction.Retry(retryId))
                                    }
                                },
                                onTap = { },
                                onLongPress = { selectedMessageForActions = message },
                                onNavigateToPost = onNavigateToPost,
                                onGloballyPositioned = { coords ->
                                    message.backendId?.let { backendId ->
                                        messageBounds = messageBounds + (backendId to coords.boundsInWindow())
                                    }
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = unseenNewMessages > 0 && !isAtLatest,
                        enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { it / 2 },
                        exit = fadeOut(tween(100)) + slideOutVertically(tween(120)) { it / 2 }
                    ) {
                        JumpToLatestChip(
                            count = unseenNewMessages,
                            isDarkMode = isDarkMode,
                            onClick = {
                                scope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.lastIndex)
                                        unseenNewMessages = 0
                                    }
                                }
                            }
                        )
                    }
                }

                // --- TOP AREA (Liquid Glass Header) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                ) {
                    val glassTint = if (isDarkMode) Color.Black else Color.White
                    val blurMaskStart = if (isDarkMode) Color.Black.copy(alpha = 0.995f) else Color.White.copy(alpha = 0.98f)
                    val blurMaskMid = if (isDarkMode) Color.Black.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.94f)

                    // 1. Background with gradient blur (Thicker at top, invisible border at bottom)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                // Seamless blur transition
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.0f to blurMaskStart,
                                        0.5f to blurMaskMid,
                                        1.0f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(80.dp)
                                .background(glassTint.copy(alpha = if (isDarkMode) 0.88f else 0.82f))
                        )
                    }

                    // 2. Specified Glass Opacity Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to glassTint.copy(alpha = if (isDarkMode) 0.22f else 0.16f),
                                    0.6f to glassTint.copy(alpha = if (isDarkMode) 0.34f else 0.28f),
                                    1.0f to Color.Transparent
                                )
                            )
                            .border(
                                width = 0.5.dp,
                                brush = Brush.verticalGradient(
                                    listOf(glassBorder, Color.Transparent)
                                ),
                                shape = RectangleShape
                            )
                    )

                    // 3. SEPARATED GLASS ELEMENTS
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(52.dp)
                    ) {
                        HeaderPillButton(
                            onClick = onBack,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        HeaderCenterCapsule(
                            userName = remoteProfile?.fullName?.takeIf { it.isNotBlank() } ?: userName,
                            username = remoteProfile?.username ?: userName,
                            isTyping = isTyping,
                            isRequest = isRequest,
                            presenceSubtitle = directPresenceSubtitle(
                                isTyping = isTyping,
                                username = remoteProfile?.username ?: userName,
                                presence = remotePresence
                            ),
                            isOnline = remotePresence?.isOnline == true,
                            isDarkMode = isDarkMode,
                            onClick = {
                                keyboardController?.hide()
                                showChatInfo = true
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )

                        HeaderAvatarButton(
                            profile = remoteProfile,
                            isDarkMode = isDarkMode,
                            contentColor = contentColor,
                            isOnline = remotePresence?.isOnline == true,
                            onClick = {
                                keyboardController?.hide()
                                showMemberProfile = true
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = pinnedMessage != null,
                    enter = fadeIn(tween(120)) + expandVertically(tween(120)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = pinnedBannerTop, start = 12.dp, end = 12.dp)
                ) {
                    pinnedMessage?.let { pinned ->
                        PinnedMessageBanner(
                            title = pinned.sender?.fullName?.takeIf { it.isNotBlank() } ?: pinned.sender?.username ?: "Pinned message",
                            text = pinned.text?.takeIf { it.isNotBlank() }
                                ?: pinned.replyToMessage?.text
                                ?: if (pinned.media != null) "Media message" else "Pinned content",
                            isDarkMode = isDarkMode,
                            onClick = {
                                val targetId = pinned.id
                                val index = messages.indexOfFirst { it.backendId == targetId }
                                if (index >= 0) {
                                    highlightedMessageId = targetId
                                    scope.launch {
                                        listState.animateScrollToItem(index)
                                        delay(1000)
                                        if (highlightedMessageId == targetId) highlightedMessageId = null
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // --- BOTTOM AREA ---
        if (isRequest) {
            RequestActionButtons(isDarkMode, onBack)
        } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                    ) {
                    // The input is pinned directly to the bottom; the composer owns its own glass.
                    RichChatComposer(
                        isDarkMode = isDarkMode,
                        textValue = messageText,
                        onTextValueChange = {
                            directViewModel.onAction(DirectChatAction.ComposerChanged(it))
                        },
                        replyPreviewText = when {
                            editingMessage != null -> "Editing message"
                            activeForwardDraft != null -> buildString {
                                append("Forwarding")
                                activeForwardDraft.sourceSenderName?.takeIf { it.isNotBlank() }?.let { append(" from $it") }
                                activeForwardDraft.sourceText?.takeIf { it.isNotBlank() }?.let { append(": ${it.take(48)}") }
                            }
                            !replyingToMessage?.text.isNullOrBlank() -> replyingToMessage?.text
                            else -> replyingToMessage?.replyPreview
                        },
                        onCancelReply = {
                            if (editingMessage != null) {
                                editingMessage = null
                                directViewModel.onAction(DirectChatAction.ComposerChanged(""))
                            } else if (activeForwardDraft != null) {
                                ForwardDraftStore.clear()
                            } else {
                                replyingToMessage = null
                            }
                        },
                        onSendText = {
                            val text = messageText.trim()
                            if (editingMessage != null) {
                                val target = editingMessage ?: return@RichChatComposer
                                val backendId = target.backendId ?: return@RichChatComposer
                                if (text.isNotBlank()) {
                                    scope.launch {
                                        directViewModel.editMessage(backendId, text) { success, error ->
                                            if (success) {
                                                editingMessage = null
                                                directViewModel.onAction(DirectChatAction.ComposerChanged(""))
                                                selectedMessageForActions = null
                                            } else {
                                                Toast.makeText(context, error ?: "Could not edit message", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            } else if (activeForwardDraft != null) {
                                val sourceMessageId = activeForwardDraft.sourceMessageId
                                if (sourceMessageId.isNotBlank()) {
                                    scope.launch {
                                        directViewModel.forwardMessage(sourceMessageId, text.takeIf { it.isNotBlank() }) { success, error ->
                                            if (success) {
                                                directViewModel.onAction(DirectChatAction.ComposerChanged(""))
                                                scope.launch { scrollToBottom() }
                                            } else {
                                                Toast.makeText(context, error ?: "Could not forward message", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            } else if (text.isNotBlank()) {
                                val replyTargetId = replyingToMessage?.backendId
                                replyingToMessage = null
                                pendingOutgoingEntryAnimation = true
                                scope.launch { scrollToBottom() }
                                directViewModel.onAction(DirectChatAction.SendText(text, replyTargetId))
                            }
                        },
                        onSendMedia = { uri, mimeType, messageTypeOverride, replyId ->
                            replyingToMessage = null
                            pendingOutgoingEntryAnimation = true
                            scope.launch { scrollToBottom() }
                            directViewModel.onAction(DirectChatAction.SendMedia(uri, mimeType, replyId))
                            true
                        },
                        onSendStructured = { structuredText, replyId ->
                            replyingToMessage = null
                            pendingOutgoingEntryAnimation = true
                            scope.launch { scrollToBottom() }
                            directViewModel.onAction(DirectChatAction.SendText(structuredText, replyId))
                            true
                        },
                        replyToMessageId = replyingToMessage?.backendId,
                        sendEnabled = conversationId.isNotBlank(),
                        conversationId = conversationId,
                        isGroup = false,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 0.dp)
                    )
                }
            }
        }

        // --- OVERLAYS ---
        AnimatedVisibility(
            visible = showChatInfo,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            ChatInfoScreen(
                userName = remoteProfile?.fullName?.takeIf { it.isNotBlank() } ?: userName,
                avatar = remoteProfile?.avatar,
                isDarkMode = isDarkMode,
                onBack = { showChatInfo = false },
                onProfileClick = {
                    showChatInfo = false
                    showMemberProfile = true
                },
                onSearchClick = { showSearchModal = true },
                onToggleMute = {
                    directViewModel.toggleMute()
                },
                onReport = {
                    directViewModel.reportUser()
                },
                onBlock = {
                    directViewModel.blockUser()
                }
            )
        }

        if (showSearchModal) {
            ModalBottomSheet(
                onDismissRequest = { showSearchModal = false },
                containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray.copy(0.4f)) }
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                    Text("Search in chat", fontWeight = FontWeight.Black, color = contentColor)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            directViewModel.updateSearchQuery(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type keywords…") }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (state.searchLoading) "Searching…" else "Search",
                        color = accentBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            if (searchQuery.trim().isBlank()) return@clickable
                            directViewModel.searchMessages(searchQuery.trim())
                        }
                    )
                    if (!state.searchError.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(state.searchError!!, color = Color.Red.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(state.searchResults, key = { it.backendId ?: it.id }) { msg ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(msg.senderName ?: "", fontWeight = FontWeight.Bold, color = contentColor, fontSize = 12.sp)
                                Text(msg.text, color = contentColor.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 2)
                            }
                            HorizontalDivider(color = contentColor.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showMemberProfile,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            ProfileScreen(
                isDarkMode = isDarkMode,
                isRefreshing = isMemberProfileRefreshing,
                onRefresh = {
                    scope.launch {
                        isMemberProfileRefreshing = true
                        delay(1500)
                        isMemberProfileRefreshing = false
                    }
                },
                onThemeChange = onThemeChange,
                isMyProfile = false,
                profileUsername = remoteProfile?.username ?: userName,
                onBack = { showMemberProfile = false },
                onNavigateToChat = { conversationId, username ->
                    showMemberProfile = false
                    onBack()
                },
                onNavigateToProfile = { _ ->
                    // Stay within chat context (no-op)
                }
            )
        }

        selectedMessageForActions?.let { selectedMessage ->
            val backendId = selectedMessage.backendId
            val currentReactionEmoji = selectedMessage.reactions.firstOrNull { it.userId == currentUser?.id }?.emoji
            val canDelete = selectedMessage.isMe && backendId != null && selectedMessage.deletedGloballyAt == null
            val anchor = backendId?.let { messageBounds[it] }
            if (backendId != null) {
                MessageActionsSheet(
                    isDarkMode = isDarkMode,
                    currentReactionEmoji = currentReactionEmoji,
                    isOwnMessage = selectedMessage.isMe,
                    isPinned = conversationDetail?.pinnedMessage?.id == backendId,
                    canEdit = selectedMessage.isMe && selectedMessage.messageType == "text" && selectedMessage.deletedGloballyAt == null,
                    canForward = selectedMessage.deletedGloballyAt == null,
                    canPin = selectedMessage.deletedGloballyAt == null,
                    anchorBounds = anchor,
                    onDismiss = { selectedMessageForActions = null },
                    onReactionSelected = { emoji ->
                        directViewModel.updateReaction(backendId, emoji)
                    },
                    onForward = {
                        if (backendId != null) {
                            forwardSourceMessage = selectedMessage
                            selectedMessageForActions = null
                            showForwardPicker = true
                        }
                    },
                    onPinToggle = {
                        directViewModel.togglePin(backendId)
                    },
                    onEditMessage = {
                        if (selectedMessage.isMe && selectedMessage.messageType == "text" && selectedMessage.text.isNotBlank()) {
                            editingMessage = selectedMessage
                            replyingToMessage = null
                            directViewModel.onAction(DirectChatAction.ComposerChanged(selectedMessage.text))
                            selectedMessageForActions = null
                        }
                    },
                    onReply = {
                        replyingToMessage = selectedMessage
                        selectedMessageForActions = null
                    },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(selectedMessage.text))
                    },
                    onReadAt = if (selectedMessage.isMe) selectedMessage.readAt else null,
                    onRemoveReaction = {
                        directViewModel.removeReaction(backendId)
                    },
                    onDeleteMessage = if (canDelete) {
                        { showDeleteDialogFor = selectedMessage }
                    } else null
                )
            }
        }

        if (showDeleteDialogFor != null) {
            DeleteMessageDialog(
                isDarkMode = isDarkMode,
                deleteForEveryone = deleteForEveryone,
                onDeleteForEveryoneChange = { deleteForEveryone = it },
                onDismiss = {
                    showDeleteDialogFor = null
                    deleteForEveryone = false
                },
                onConfirm = {
                    val target = showDeleteDialogFor ?: return@DeleteMessageDialog
                    val targetBackendId = target.backendId ?: return@DeleteMessageDialog
                    directViewModel.deleteMessage(targetBackendId, deleteForEveryone)
                    showDeleteDialogFor = null
                    deleteForEveryone = false
                }
            )
        }

        if (showForwardPicker) {
            ForwardTargetPickerSheet(
                isDarkMode = isDarkMode,
                chats = cachedForwardChats(),
                groups = cachedForwardGroups(),
                onDismiss = {
                    showForwardPicker = false
                    forwardSourceMessage = null
                },
                onSelectChat = { chat ->
                    val targetConversationId = chat.backendId ?: return@ForwardTargetPickerSheet
                    val source = forwardSourceMessage ?: return@ForwardTargetPickerSheet
                    source.backendId?.let { sourceId ->
                        ForwardDraftStore.setDraft(
                            ForwardDraft(
                                sourceMessageId = sourceId,
                                sourceConversationId = conversationId,
                                sourceSenderName = source.senderName,
                                sourceText = source.text,
                                sourceMessageType = source.messageType,
                                targetConversationId = targetConversationId,
                                targetTitle = chat.name
                            )
                        )
                        onNavigateToChat(targetConversationId, chat.name, false)
                    }
                    forwardSourceMessage = null
                    showForwardPicker = false
                },
                onSelectGroup = { group ->
                    val targetGroupId = group.backendId ?: return@ForwardTargetPickerSheet
                    val source = forwardSourceMessage ?: return@ForwardTargetPickerSheet
                    source.backendId?.let { sourceId ->
                        ForwardDraftStore.setDraft(
                            ForwardDraft(
                                sourceMessageId = sourceId,
                                sourceConversationId = conversationId,
                                sourceSenderName = source.senderName,
                                sourceText = source.text,
                                sourceMessageType = source.messageType,
                                targetGroupId = targetGroupId,
                                targetTitle = group.name
                            )
                        )
                        onNavigateToGroupChat(targetGroupId, group.name)
                    }
                    forwardSourceMessage = null
                    showForwardPicker = false
                }
            )
        }

    }

@Composable
fun MessageBubble(
    message: MessageData,
    isDarkMode: Boolean,
    currentUserId: String?,
    animateFromInput: Boolean,
    highlighted: Boolean = false,
    onRetry: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onNavigateToPost: (String) -> Unit = {},
    onGloballyPositioned: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {}
) {
    // Alignment is always based on senderId == currentUserId (never "flip" on reload).
    val isMe = currentUserId != null && message.senderId != null && message.senderId == currentUserId
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) PremiumBlue else (if (isDarkMode) Color(0xFF262626) else Color(0xFFF0F2F0))
    val textColor = if (isMe) Color.White else (if (isDarkMode) Color.White else Color.Black)
    val shape = if (isMe) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }
    val highlightedBorder = if (highlighted) BorderStroke(1.2.dp, Color(0xFF00A3FF).copy(alpha = 0.72f)) else null

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = timeFormat.format(Date(message.timestamp))

    var runInputAnimation by remember(message.stableKeyVm, animateFromInput) { mutableStateOf(animateFromInput) }
    val entryTranslation by animateFloatAsState(
        targetValue = if (runInputAnimation) 72f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "message_entry_translation"
    )
    val entryScale by animateFloatAsState(
        targetValue = if (runInputAnimation) 0.92f else 1f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "message_entry_scale"
    )
    val entryAlpha by animateFloatAsState(
        targetValue = if (runInputAnimation) 0.05f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "message_entry_alpha"
    )
    LaunchedEffect(message.stableKeyVm, animateFromInput) {
        if (animateFromInput) {
            delay(18)
            runInputAnimation = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
            .graphicsLayer {
                translationY = entryTranslation
                scaleX = entryScale
                scaleY = entryScale
                alpha = entryAlpha
            }
            .onGloballyPositioned(onGloballyPositioned)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress
            ),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 280.dp),
            border = highlightedBorder
        ) {
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Column(modifier = Modifier.padding(end = 60.dp)) {
                    if (message.deletedGloballyAt != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Block, null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                            Text(
                                text = "This message was deleted",
                                color = textColor.copy(alpha = 0.62f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                    if (!message.replyPreview.isNullOrBlank()) {
                        MessageContextPill(
                            text = message.replyPreview,
                            icon = Icons.AutoMirrored.Filled.Reply,
                            textColor = textColor,
                            accentColor = PremiumBlue,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    if (message.forwardedFromMessageId != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                            text = "Forwarded message",
                            color = textColor.copy(alpha = 0.52f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    ChatMessageContent(
                        text = message.text,
                        messageType = message.messageType,
                        media = message.media,
                        isDarkMode = isDarkMode,
                        preferLightContent = isMe,
                        onOpenStructured = { payload ->
                            if ((payload.type == ChatStructuredType.POST || payload.type == ChatStructuredType.REEL) && !payload.targetId.isNullOrBlank()) {
                                onNavigateToPost(payload.targetId)
                            }
                        }
                    )
                    if (message.reactions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        ReactionSummaryRow(
                            reactions = message.reactions,
                            currentUserId = currentUserId,
                            isMe = isMe,
                            isDarkMode = isDarkMode
                        )
                    }
                    if (message.editedAt != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Edit, null, tint = textColor.copy(alpha = 0.42f), modifier = Modifier.size(10.dp))
                            Text(
                            text = "edited",
                            color = textColor.copy(alpha = 0.45f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        }
                    }
                    MessageLifecycleStrip(message.status, message.media != null, textColor, PremiumBlue)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    MessageStatusFooter(
                        status = message.status,
                        timeString = timeString,
                        isMe = isMe,
                        textColor = textColor,
                        accentColor = PremiumBlue,
                        onRetry = onRetry,
                        isGroupMessage = false
                    )
                }
                if (message.status == MessageStatus.FAILED && !message.errorReason.isNullOrBlank()) {
                    Text(
                        text = message.errorReason,
                        color = Color(0xFFFF8A80),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReactionSummaryRow(
    reactions: List<MessageReactionUi>,
    currentUserId: String?,
    isMe: Boolean,
    isDarkMode: Boolean
) {
    if (reactions.isEmpty()) return

    val contentColor = if (isMe) Color.White else if (isDarkMode) Color.White else Color.Black
    val grouped = reactions.groupBy { it.emoji }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        grouped.forEach { (emoji, entries) ->
            val hasCurrentUser = entries.any { it.userId == currentUserId }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (hasCurrentUser) contentColor.copy(alpha = 0.18f) else contentColor.copy(alpha = 0.1f),
                border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = entries.size.toString(),
                        color = contentColor.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun UnreadDividerChip(isDarkMode: Boolean) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = contentColor.copy(alpha = 0.08f),
            border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.12f))
        ) {
            Text(
                text = "Unread messages",
                color = contentColor.copy(alpha = 0.72f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun PinnedMessageBanner(
    title: String,
    text: String,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp, max = 46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isDarkMode) Color.Black.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.78f),
        border = BorderStroke(0.5.dp, if (isDarkMode) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.64f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PushPin, null, tint = Color(0xFF00A3FF), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = contentColor,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text,
                    color = contentColor.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = contentColor.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun DeleteMessageDialog(
    isDarkMode: Boolean,
    deleteForEveryone: Boolean,
    onDeleteForEveryoneChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White,
        title = { Text("Delete message", color = contentColor, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Choose how to delete this message.", color = contentColor.copy(alpha = 0.72f), fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Checkbox(checked = deleteForEveryone, onCheckedChange = onDeleteForEveryoneChange)
                    Spacer(Modifier.width(8.dp))
                    Text("Also delete for other user(s)", color = contentColor, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = contentColor)
            }
        }
    )
}

@Composable
fun ForwardTargetPickerSheet(
    isDarkMode: Boolean,
    chats: List<ChatMessage>,
    groups: List<GroupChat>,
    onDismiss: () -> Unit,
    onSelectChat: (ChatMessage) -> Unit,
    onSelectGroup: (GroupChat) -> Unit
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDarkMode) Color(0xFF101214) else Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray.copy(0.4f)) }
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
            Text("Forward to", fontWeight = FontWeight.Black, color = contentColor)
            Spacer(Modifier.height(12.dp))
            if (chats.isNotEmpty()) {
                Text("Chats", color = contentColor.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(chats, key = { it.backendId ?: it.name }) { chat ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectChat(chat) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppAvatar(imageModel = chat.avatar, name = chat.name, username = chat.username ?: chat.name, modifier = Modifier.size(40.dp), isDarkMode = isDarkMode, fontSize = 14.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(chat.name, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(chat.lastMessage, color = contentColor.copy(alpha = 0.62f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            if (groups.isNotEmpty()) {
                Text("Groups", color = contentColor.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(groups, key = { it.backendId ?: it.name }) { group ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectGroup(group) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppAvatar(imageModel = group.avatar, name = group.name, username = group.name, modifier = Modifier.size(40.dp), isDarkMode = isDarkMode, fontSize = 14.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(group.lastMessage, color = contentColor.copy(alpha = 0.62f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            if (chats.isEmpty() && groups.isEmpty()) {
                Text("No recent chats yet.", color = contentColor.copy(alpha = 0.65f), fontSize = 13.sp)
            }
        }
    }
}

fun cachedForwardChats(): List<ChatMessage> = MessagesInboxCache.chats.value

fun cachedForwardGroups(): List<GroupChat> = MessagesInboxCache.groups.value

private fun formatTimeLabel(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        val instant = java.time.Instant.parse(value)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())
        formatter.format(instant)
    }.getOrElse { value }
}

private fun directPresenceSubtitle(
    isTyping: Boolean,
    username: String,
    presence: UserPresenceState?
): String {
    if (isTyping) return "typing..."
    if (presence?.isOnline == true) return "Online"
    val lastSeenAt = presence?.lastSeenAt ?: return "@$username"
    val diff = (System.currentTimeMillis() - lastSeenAt).coerceAtLeast(0L)
    return when {
        diff < 120_000L -> "Last seen recently"
        diff < 3_600_000L -> "Last seen ${maxOf(1, diff / 60_000L)} min ago"
        diff < 86_400_000L -> "Last seen ${maxOf(1, diff / 3_600_000L)} h ago"
        else -> "Last seen yesterday"
    }
}

@Composable
fun MessageContextPill(
    text: String,
    icon: ImageVector,
    textColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .border(0.5.dp, textColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(24.dp)
                .background(accentColor.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
        )
        Icon(icon, null, tint = textColor.copy(alpha = 0.58f), modifier = Modifier.size(12.dp))
        Text(
            text = text,
            color = textColor.copy(alpha = 0.72f),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MessageLifecycleStrip(
    status: MessageStatus,
    hasMedia: Boolean,
    textColor: Color,
    accentColor: Color
) {
    if (status != MessageStatus.SENDING || !hasMedia) return
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = if (status == MessageStatus.SENDING) accentColor.copy(alpha = 0.72f) else textColor.copy(alpha = 0.30f),
            trackColor = textColor.copy(alpha = 0.10f)
        )
        Text(
            text = "Uploading media...",
            color = textColor.copy(alpha = 0.46f),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun JumpToLatestChip(
    count: Int,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val surface = if (isDarkMode) Color(0xFF15171B).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f)
    val content = if (isDarkMode) Color.White else Color.Black
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = surface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(0.5.dp, content.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(PremiumBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = if (count == 1) "New message" else "$count new messages",
                color = content.copy(alpha = 0.86f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MessageStatusFooter(
    status: MessageStatus,
    timeString: String,
    isMe: Boolean,
    textColor: Color,
    accentColor: Color,
    onRetry: () -> Unit,
    isGroupMessage: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = timeString, color = textColor.copy(alpha = 0.54f), fontSize = 10.sp)
        if (!isMe) return@Row

        AnimatedContent(
            targetState = status,
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
            label = "message_status"
        ) { currentStatus ->
            when {
                currentStatus == MessageStatus.QUEUED || currentStatus == MessageStatus.SENDING -> Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.66f),
                    modifier = Modifier.size(13.dp)
                )
                currentStatus == MessageStatus.SEEN -> Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                currentStatus == MessageStatus.DELIVERED -> Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.62f),
                    modifier = Modifier.size(13.dp)
                )
                currentStatus == MessageStatus.SENT -> Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.56f),
                    modifier = Modifier.size(13.dp)
                )
                currentStatus == MessageStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "Retry",
                        color = Color(0xFFFF8A80),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onRetry)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, icon: ImageVector, tint: Color, compact: Boolean = false, onClick: () -> Unit) {
    val rowShape = if (compact) 12.dp else 16.dp
    val horizontalPadding = if (compact) 9.dp else 12.dp
    val verticalPadding = if (compact) 7.dp else 10.dp
    val iconSize = if (compact) 15.dp else 18.dp
    val gap = if (compact) 7.dp else 10.dp
    val fontSize = if (compact) 12.sp else 14.sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rowShape))
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(gap))
        Text(label, color = tint, fontSize = fontSize, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MessageActionsSheet(
    isDarkMode: Boolean,
    currentReactionEmoji: String?,
    isOwnMessage: Boolean,
    isPinned: Boolean = false,
    canEdit: Boolean = false,
    canForward: Boolean = true,
    canPin: Boolean = true,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onRemoveReaction: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit = {},
    onPinToggle: () -> Unit = {},
    onEditMessage: () -> Unit = {},
    onReadAt: String? = null,
    onDeleteMessage: (() -> Unit)? = null
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
    var expanded by remember { mutableStateOf(false) }
    var panelVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (anchorBounds == null) return

    fun dismissWithAnimation() {
        panelVisible = false
        scope.launch {
            delay(120)
            onDismiss()
        }
    }

    LaunchedEffect(anchorBounds) {
        panelVisible = true
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val panelWidth = 230.dp
    val panelWidthPx = with(density) { panelWidth.roundToPx() }
    val panelHeightGuessPx = with(density) { (expanded).let { if (it) 258.dp else 194.dp }.roundToPx() }
    val anchorRight = anchorBounds.right.toInt()
    val anchorTop = anchorBounds.top.toInt()
    val anchorBottom = anchorBounds.bottom.toInt()
    val x = (anchorRight - panelWidthPx).coerceIn(with(density) { 12.dp.roundToPx() }, screenWidthPx - panelWidthPx - with(density) { 12.dp.roundToPx() })
    val aboveY = anchorTop - panelHeightGuessPx - with(density) { 12.dp.roundToPx() }
    val belowY = anchorBottom + with(density) { 12.dp.roundToPx() }
    val y = if (aboveY > with(density) { 72.dp.roundToPx() }) aboveY else belowY

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { dismissWithAnimation() }
    ) {
        AnimatedVisibility(
            visible = panelVisible,
            enter = fadeIn(tween(90)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(150, easing = FastOutSlowInEasing)) { it / 10 },
            exit = fadeOut(tween(90)) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(100, easing = FastOutSlowInEasing)),
            modifier = Modifier
                .offset(x = with(density) { x.toDp() }, y = with(density) { y.toDp() })
        ) {
            Surface(
                modifier = Modifier
                .width(panelWidth)
                .animateContentSize(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)),
            shape = RoundedCornerShape(18.dp),
            color = if (isDarkMode) Color(0xFF1B1B1D) else Color.White,
            border = BorderStroke(0.7.dp, if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier.padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reactions.forEach { emoji ->
                        val selected = currentReactionEmoji == emoji
                        val scale by animateFloatAsState(if (selected) 1.18f else 1f, label = "reactionScale")
                        Surface(
                            shape = CircleShape,
                            color = if (selected) PremiumBlue.copy(alpha = 0.22f) else contentColor.copy(alpha = 0.07f),
                            border = BorderStroke(1.dp, if (selected) PremiumBlue else contentColor.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clickable {
                                    if (selected) onRemoveReaction() else onReactionSelected(emoji)
                                    dismissWithAnimation()
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 15.sp)
                            }
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = contentColor.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.08f)),
                        modifier = Modifier.size(30.dp).clickable { expanded = !expanded }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.72f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = expanded, enter = fadeIn(tween(120)) + expandVertically(tween(120)), exit = fadeOut(tween(120)) + shrinkVertically(tween(120))) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val extra = listOf("🔥", "👏", "😁", "😎", "💯", "🎉")
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                            extra.forEach { emoji ->
                                Surface(
                                    shape = CircleShape,
                                    color = contentColor.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.08f)),
                                    modifier = Modifier.weight(1f).clickable {
                                        onReactionSelected(emoji)
                                        dismissWithAnimation()
                                    }
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                        Text(text = emoji, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = contentColor.copy(alpha = 0.08f))

                ActionRow("Reply", Icons.AutoMirrored.Filled.Reply, contentColor, compact = true) {
                    onReply()
                    dismissWithAnimation()
                }
                ActionRow("Copy", Icons.Default.ContentCopy, contentColor, compact = true) {
                    onCopy()
                    dismissWithAnimation()
                }
                if (canForward) {
                    ActionRow("Forward", Icons.Default.Send, contentColor, compact = true) {
                        onForward()
                        dismissWithAnimation()
                    }
                }
                if (canPin) {
                    ActionRow(if (isPinned) "Unpin" else "Pin", Icons.Default.PushPin, contentColor, compact = true) {
                        onPinToggle()
                        dismissWithAnimation()
                    }
                }
                if (canEdit) {
                    ActionRow("Edit", Icons.Default.Edit, contentColor, compact = true) {
                        onEditMessage()
                        dismissWithAnimation()
                    }
                }
                val readAtDisplay = formatTimeLabel(onReadAt)
                if (!readAtDisplay.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = contentColor.copy(alpha = 0.65f), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Read at $readAtDisplay", color = contentColor.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (currentReactionEmoji != null) {
                    ActionRow("Remove reaction", Icons.Default.RemoveCircle, contentColor.copy(alpha = 0.9f), compact = true) {
                        onRemoveReaction()
                        dismissWithAnimation()
                    }
                }
                if (isOwnMessage && onDeleteMessage != null) {
                    ActionRow("Delete", Icons.Default.Delete, Color.Red.copy(alpha = 0.92f), compact = true) {
                        onDeleteMessage()
                        dismissWithAnimation()
                    }
                }
            }
        }
        }
    }
}

@Composable
fun AttachmentMenu(contentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AttachmentItem(Icons.Default.Image, "Galereya", contentColor)
        AttachmentItem(Icons.Default.Description, "File", contentColor)
        AttachmentItem(Icons.Default.MusicNote, "Music", contentColor)
        AttachmentItem(Icons.Default.LocationOn, "Location", contentColor)
    }
}

private fun MessageData.mergeFrom(other: MessageData, incomingSource: MessageSource = MessageSource.SocketEvent): MessageData {
    val result = ChatReducer.merge(
        toReducerSnapshot(MessageSource.LocalCache),
        other.toReducerSnapshot(incomingSource)
    )
    val reduced = result.message
    val otherText = other.text.takeIf { it.isNotBlank() }
    val otherReply = other.replyPreview?.takeIf { it.isNotBlank() }
    val otherIsServer = other.backendId != null
    return copy(
        localId = reduced.localId,
        backendId = reduced.backendId,
        text = if (result.textFromIncoming) otherText ?: text else text,
        isMe = isMe || other.isMe,
        senderId = other.senderId ?: senderId,
        timestamp = reduced.timestamp,
        status = reduced.status.toUiStatus(),
        errorReason = reduced.errorReason,
        retryCount = reduced.retryCount,
        senderName = other.senderName ?: senderName,
        messageType = if (other.messageType.isNotBlank()) other.messageType else messageType,
        media = other.media ?: media,
        pendingPayload = other.pendingPayload ?: pendingPayload,
        replyPreview = if (result.textFromIncoming) otherReply ?: replyPreview else replyPreview,
        reactions = if (result.reactionsFromIncoming) other.reactions else if (!otherIsServer && other.reactions.isNotEmpty()) other.reactions else reactions,
        forwardedFromMessageId = if (otherIsServer) other.forwardedFromMessageId else other.forwardedFromMessageId ?: forwardedFromMessageId,
        forwardedFromSenderId = if (otherIsServer) other.forwardedFromSenderId else other.forwardedFromSenderId ?: forwardedFromSenderId,
        forwardedFromConversationId = if (otherIsServer) other.forwardedFromConversationId else other.forwardedFromConversationId ?: forwardedFromConversationId,
        forwardedAt = if (otherIsServer) other.forwardedAt else other.forwardedAt ?: forwardedAt,
        readAt = other.readAt ?: readAt,
        editedAt = if (result.editFromIncoming) other.editedAt ?: editedAt else editedAt,
        deletedGloballyAt = other.deletedGloballyAt ?: deletedGloballyAt
    )
}

fun messageStatusRank(status: MessageStatus): Int = when (status) {
    else -> ChatReducer.statusRank(status.toReducerStatus())
}

fun MessageStatus.toReducerStatus(): ChatMessageStatus = when (this) {
    MessageStatus.QUEUED -> ChatMessageStatus.QUEUED
    MessageStatus.SENDING -> ChatMessageStatus.SENDING
    MessageStatus.SENT -> ChatMessageStatus.SENT
    MessageStatus.DELIVERED -> ChatMessageStatus.DELIVERED
    MessageStatus.SEEN -> ChatMessageStatus.SEEN
    MessageStatus.FAILED -> ChatMessageStatus.FAILED
}

fun ChatMessageStatus.toUiStatus(): MessageStatus = when (this) {
    ChatMessageStatus.QUEUED -> MessageStatus.QUEUED
    ChatMessageStatus.SENDING -> MessageStatus.SENDING
    ChatMessageStatus.SENT -> MessageStatus.SENT
    ChatMessageStatus.DELIVERED -> MessageStatus.DELIVERED
    ChatMessageStatus.SEEN -> MessageStatus.SEEN
    ChatMessageStatus.FAILED -> MessageStatus.FAILED
}

private fun MessageData.toReducerSnapshot(source: MessageSource): ChatMessageSnapshot =
    ChatMessageSnapshot(
        localId = localId,
        clientId = localId,
        backendId = backendId,
        text = text,
        status = status.toReducerStatus(),
        source = source,
        timestamp = timestamp,
        editedAt = editedAt.toEpochMillisOrNull(),
        deletedAt = deletedGloballyAt.toEpochMillisOrNull(),
        reactions = reactions.map { "${it.userId.orEmpty()}:${it.emoji}" },
        hasReactionSnapshot = backendId != null && source != MessageSource.LocalCache,
        errorReason = errorReason,
        retryCount = retryCount
    )

private fun retrofit2.Response<*>?.apiErrorMessage(defaultMessage: String): String {
    val response = this ?: return defaultMessage
    val body = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
    if (body.isNotBlank()) {
        val parsed = runCatching { JSONObject(body).optString("message") }.getOrNull()?.trim().orEmpty()
        if (parsed.isNotBlank()) return parsed
        return body.trim().takeIf { it.isNotBlank() } ?: response.message().ifBlank { defaultMessage }
    }
    return response.message().ifBlank { defaultMessage }
}

fun retrofit2.Response<*>?.shouldQueueForRetry(): Boolean {
    val response = this ?: return true
    return response.code() == 408 || response.code() == 429 || response.code() in 500..599
}

fun Throwable?.chatSendErrorCategory(): String {
    val error = this ?: return "no_http_response"
    val name = error::class.java.simpleName
    return when {
        name.contains("Timeout", ignoreCase = true) -> "timeout"
        name.contains("UnknownHost", ignoreCase = true) -> "dns_or_offline"
        name.contains("Connect", ignoreCase = true) -> "connect_failed"
        name.contains("SSL", ignoreCase = true) -> "ssl"
        name.contains("Socket", ignoreCase = true) -> "socket_io"
        else -> name.ifBlank { "network_exception" }
    }
}

fun Throwable?.chatSendErrorMessage(): String {
    val category = chatSendErrorCategory()
    return when (category) {
        "timeout" -> "Network timeout. Queued for retry."
        "dns_or_offline" -> "No internet connection. Queued for retry."
        "connect_failed" -> "Could not reach server. Queued for retry."
        "ssl" -> "Secure connection failed. Queued for retry."
        else -> "Waiting for connection. Queued for retry."
    }
}

@Composable
fun AttachmentItem(icon: ImageVector, label: String, contentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
        Text(label, color = contentColor, fontSize = 8.sp)
    }
}

@Composable
fun HeaderPillButton(
    onClick: () -> Unit,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TelegramIosGlass(
        modifier = modifier.size(42.dp),
        shape = CircleShape,
        isDarkMode = isDarkMode,
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun HeaderCenterCapsule(
    userName: String,
    username: String,
    isTyping: Boolean,
    isRequest: Boolean,
    presenceSubtitle: String,
    isOnline: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    TelegramIosGlass(
        modifier = modifier
            .wrapContentWidth()
            .height(46.dp),
        shape = RoundedCornerShape(23.dp),
        isDarkMode = isDarkMode,
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = userName,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isRequest) {
                AnimatedContent(
                    targetState = presenceSubtitle,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "status"
                ) { subtitle ->
                    Text(
                        text = subtitle,
                        color = if (isTyping || isOnline) Color(0xFF00C853) else Color(0xFF007AFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderAvatarButton(
    profile: ProfileModel?,
    isDarkMode: Boolean,
    contentColor: Color,
    isOnline: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TelegramIosGlass(
        modifier = modifier.size(42.dp),
        shape = CircleShape,
        isDarkMode = isDarkMode,
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        AppAvatar(
            imageModel = profile?.avatar,
            name = profile?.fullName,
            username = profile?.username,
            modifier = Modifier.size(36.dp),
            isDarkMode = isDarkMode,
            fontSize = 14.sp
        )
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00C853))
                    .border(1.5.dp, if (isDarkMode) Color(0xFF101214) else Color.White, CircleShape)
            )
        }
    }
}

@Composable
fun TelegramIosGlass(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "glassScale"
    )

    val glassBrush = if (isDarkMode) {
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.72f),
                Color.Black.copy(alpha = 0.54f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.82f),
                Color.White.copy(alpha = 0.64f),
                Color.Black.copy(alpha = 0.05f)
            )
        )
    }

    val borderColor = if (isDarkMode) Color.White.copy(0.18f) else Color.White.copy(0.62f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(glassBrush)
            .border(0.5.dp, borderColor, shape)
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

@Composable
fun ChatInfoScreen(
    userName: String, 
    avatar: String? = null,
    isDarkMode: Boolean, 
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleMute: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val accentBlue = Color(0xFF00A3FF)
    var selectedFilter by remember { mutableIntStateOf(0) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppAvatar(
                imageModel = avatar,
                name = userName,
                username = userName,
                modifier = Modifier
                    .size(120.dp)
                    .border(2.dp, accentBlue, CircleShape)
                    .padding(6.dp),
                isDarkMode = isDarkMode,
                fontSize = 42.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            var isInfoNameExpanded by remember { mutableStateOf(false) }
            Text(
                text = userName, 
                color = contentColor, 
                fontSize = if (isInfoNameExpanded) 24.sp else if (userName.length > 15) 18.sp else 22.sp, 
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = if (isInfoNameExpanded) 2 else 1,
                softWrap = isInfoNameExpanded,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isInfoNameExpanded = !isInfoNameExpanded }
            )

            Text(text = "@${userName.lowercase().replace(" ", "")}", color = accentBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(24.dp))

            // TOP ACTIONS (real backend actions only)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoActionButton(Icons.Default.Person, "Profile", isDarkMode, onClick = onProfileClick)
                InfoActionButton(Icons.Default.Search, "Qidiruv", isDarkMode, onClick = onSearchClick)
                InfoActionButton(Icons.Default.Notifications, "Mute", isDarkMode, onClick = onToggleMute)
            }

            Spacer(modifier = Modifier.height(28.dp))

            // SETTINGS LIST (Mute, Report, Block)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                SettingsListItem(Icons.Default.Notifications, "Mute", "Xabarlarni o'chirish", contentColor, onClick = onToggleMute)
                SettingsListItem(Icons.Default.Flag, "Report", "Ariza tashlash", Color.Red, onClick = onReport)
                SettingsListItem(Icons.Default.Block, "Block", "Foydalanuvchini bloklash", Color.Red, onClick = onBlock)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- 5 ICONS NAVIGATION BAR ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.04f))
                    .border(0.5.dp, contentColor.copy(0.1f), RoundedCornerShape(28.dp))
            ) {
                val screenWidth = LocalConfiguration.current.screenWidthDp
                val barPadding = 48 
                val itemWidth = (screenWidth - barPadding) / 5
                val indicatorOffset by animateDpAsState(
                    targetValue = (selectedFilter * itemWidth).dp,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "indicator"
                )

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth.dp)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(accentBlue.copy(0.2f))
                        .border(1.dp, accentBlue.copy(0.4f), RoundedCornerShape(24.dp))
                )

                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    FilterItem(Icons.Default.Image, selectedFilter == 0, contentColor) { selectedFilter = 0 }
                    FilterItem(Icons.Default.GridView, selectedFilter == 1, contentColor) { selectedFilter = 1 }
                    FilterItem(Icons.Default.Description, selectedFilter == 2, contentColor) { selectedFilter = 2 }
                    FilterItem(Icons.Default.MusicNote, selectedFilter == 3, contentColor) { selectedFilter = 3 }
                    FilterItem(Icons.Default.Folder, selectedFilter == 4, contentColor) { selectedFilter = 4 }
                }
            }

            // --- SHARED CONTENT AREA (Under the icons, eng pastda) ---
            Box(modifier = Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 24.dp)) {
                AnimatedContent(
                    targetState = selectedFilter,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "filter_content"
                ) { filter ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        when(filter) {
                            0 -> { // Gallery
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(6) {
                                        Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(contentColor.copy(0.1f))) {
                                            Icon(Icons.Default.Image, null, modifier = Modifier.align(Alignment.Center), tint = contentColor.copy(0.2f))
                                        }
                                    }
                                }
                            }
                            1 -> { // Posts
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(3) {
                                        Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(contentColor.copy(0.1f))) {
                                            Icon(Icons.Default.GridView, null, modifier = Modifier.align(Alignment.Center), tint = contentColor.copy(0.2f))
                                        }
                                    }
                                }
                            }
                            2 -> { // Documents
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(3) { SettingsListItem(Icons.Default.Description, "Hujjat $it.pdf", "2.4 MB", contentColor) }
                                }
                            }
                            3 -> { // Music
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(3) { SettingsListItem(Icons.Default.MusicNote, "Audio Track $it.mp3", "Artist Name", contentColor) }
                                }
                            }
                            4 -> { // Files
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(3) { SettingsListItem(Icons.Default.Folder, "File_$it.zip", "156 MB", contentColor) }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RowScope.FilterItem(icon: ImageVector, isSelected: Boolean, contentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = if (isSelected) Color(0xFF00A3FF) else contentColor.copy(0.4f), 
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun InfoActionButton(icon: ImageVector, label: String, isDarkMode: Boolean, onClick: () -> Unit) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val outerShape = RoundedCornerShape(20.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(74.dp)
                .height(50.dp)
                .clip(outerShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (isDarkMode)
                            listOf(
                                Color.White.copy(0.22f),
                                Color.White.copy(0.10f),
                                Color.White.copy(0.05f)
                            )
                        else
                            listOf(
                                Color.Black.copy(0.06f),
                                Color.Black.copy(0.04f),
                                Color.Black.copy(0.02f)
                            )
                    )
                )
                .border(
                    0.75.dp,
                    if (isDarkMode) Color.White.copy(0.16f) else Color.Black.copy(0.08f),
                    outerShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(0xFF00A3FF), modifier = Modifier.size(21.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = contentColor.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsListItem(icon: ImageVector, title: String, subtitle: String?, tint: Color, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable { onClick() } else base }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if(tint == Color.Red) Color.Red else Color(0xFF00A3FF), modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(text = subtitle, color = tint.copy(0.5f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun RequestActionButtons(isDarkMode: Boolean, onBack: () -> Unit) {
    val containerBg = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glassBorder = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(0.05f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerBg,
        tonalElevation = 8.dp,
        border = BorderStroke(0.5.dp, glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RequestActionButton(text = "O'chirish", color = Color.Red, modifier = Modifier.weight(1f), onClick = onBack)
            RequestActionButton(text = "Bloklash", color = contentColor, modifier = Modifier.weight(1f), onClick = onBack)
            RequestActionButton(text = "Qo'shish", color = Color(0xFF00A3FF), modifier = Modifier.weight(1f), onClick = onBack)
        }
    }
}

@Composable
fun RequestActionButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(44.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DateHeader(timestamp: Long, isDarkMode: Boolean) {
    val dateString = SimpleDateFormat("dd MMMM", Locale.getDefault()).format(Date(timestamp))
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = if (isDarkMode) Color.White.copy(0.1f) else Color.Black.copy(0.05f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = dateString, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = if (isDarkMode) Color.White.copy(0.6f) else Color.Black.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return f.format(Date(t1)) == f.format(Date(t2))
}

enum class MessageStatus { QUEUED, SENDING, SENT, DELIVERED, SEEN, FAILED }

data class PendingMessagePayload(
    val text: String? = null,
    val replyToMessageId: String? = null,
    val mediaUri: String? = null,
    val mimeType: String? = null,
    val messageTypeOverride: String? = null
)

fun PendingMessagePayload.toOutboxPayload(): PendingChatPayload =
    PendingChatPayload(
        text = text,
        replyToMessageId = replyToMessageId,
        mediaUri = mediaUri,
        mimeType = mimeType,
        messageTypeOverride = messageTypeOverride
    )

fun PendingChatPayload.toPendingMessagePayload(): PendingMessagePayload =
    PendingMessagePayload(
        text = text,
        replyToMessageId = replyToMessageId,
        mediaUri = mediaUri,
        mimeType = mimeType,
        messageTypeOverride = messageTypeOverride
    )

private fun PendingChatEnvelope.toDirectUiMessage(currentUser: ProfileModel?): MessageData {
    val pending = payload.toPendingMessagePayload()
    val mediaType = payload.messageTypeOverride ?: when {
        payload.mimeType?.startsWith("video") == true -> "video"
        payload.mimeType?.startsWith("audio") == true -> "voice"
        payload.mediaUri != null -> "image"
        else -> "text"
    }
    return MessageData(
        id = localId.hashCode(),
        localId = localId,
        backendId = null,
        text = payload.text.orEmpty(),
        isMe = true,
        senderId = currentUser?.id,
        timestamp = createdAt,
        status = if (terminalError.isNullOrBlank()) MessageStatus.QUEUED else MessageStatus.FAILED,
        errorReason = terminalError,
        retryCount = retryCount,
        senderName = currentUser?.fullName ?: currentUser?.username,
        messageType = mediaType,
        media = payload.mediaUri?.let {
            ChatMediaUi(
                url = it,
                type = mediaType,
                fileName = if (mediaType == "video") "Video" else if (mediaType == "voice") "Voice message" else "Photo",
                mimeType = payload.mimeType
            )
        },
        pendingPayload = pending
    )
}

data class MessageReactionUi(
    val userId: String?,
    val username: String?,
    val fullName: String?,
    val avatar: String?,
    val emoji: String
)

data class MessageData(
    val id: Int,
    val localId: String? = null,
    val backendId: String? = null,
    val text: String,
    val isMe: Boolean,
    val senderId: String? = null,
    var isNew: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val errorReason: String? = null,
    val retryCount: Int = 0,
    val senderName: String? = null,
    val messageType: String = "text",
    val media: ChatMediaUi? = null,
    val pendingPayload: PendingMessagePayload? = null,
    val replyPreview: String? = null,
    val reactions: List<MessageReactionUi> = emptyList(),
    val forwardedFromMessageId: String? = null,
    val forwardedFromSenderId: String? = null,
    val forwardedFromConversationId: String? = null,
    val forwardedAt: String? = null,
    val readAt: String? = null,
    val editedAt: String? = null,
    val deletedGloballyAt: String? = null
)

@Composable
fun ChatEmptyState(title: String, subtitle: String, isDarkMode: Boolean) {
    val titleColor = if (isDarkMode) Color.White else Color.Black
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 160.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = titleColor.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

private fun ChatMessageModel.toUiMessage(currentUserId: String?): MessageData {
    val senderId = sender?.id
    val isMine = currentUserId != null && senderId == currentUserId
    val seen = if (isMine) {
        readAt != null || seenBy.any { it != currentUserId }
    } else {
        currentUserId != null && seenBy.any { it == currentUserId }
    }
    val replyPreviewText = replyToMessage?.text
        ?: if (replyToMessage?.media != null) {
            when (replyToMessage.messageType) {
                "video" -> "Video reply"
                "voice" -> "Voice reply"
                "round_video" -> "Round video reply"
                "file" -> "File reply"
                else -> "Photo reply"
            }
        } else null
    val messageTextValue = text?.takeIf { it.isNotBlank() } ?: ""
    return MessageData(
        id = id.hashCode(),
        localId = clientId,
        backendId = id,
        text = messageTextValue,
        isMe = isMine,
        senderId = senderId,
        timestamp = createdAt.toEpochMillis(),
        status = when {
            seen -> MessageStatus.SEEN
            deliveredAt != null -> MessageStatus.DELIVERED
            else -> MessageStatus.SENT
        },
        senderName = sender?.fullName ?: sender?.username,
        messageType = messageType,
        media = media?.let {
            ChatMediaUi(
                url = it.url,
                type = messageType,
                fileName = it.fileName,
                fileSize = it.fileSize,
                mimeType = it.mimeType,
                durationSeconds = it.durationSeconds ?: it.duration
            )
        },
        replyPreview = replyPreviewText,
        reactions = reactions.map {
            MessageReactionUi(
                userId = it.user?.id,
                username = it.user?.username,
                fullName = it.user?.fullName,
                avatar = it.user?.avatar,
                emoji = it.emoji
            )
        },
        forwardedFromMessageId = forwardedFromMessageId,
        forwardedFromSenderId = forwardedFromSenderId,
        forwardedFromConversationId = forwardedFromConversationId,
        forwardedAt = forwardedAt,
        readAt = readAt ?: deliveredAt,
        editedAt = editedAt,
        deletedGloballyAt = if (isDeletedForEveryone) deletedForEveryoneAt else null
    )
}

private fun String?.toEpochMillis(): Long {
    if (this.isNullOrBlank()) return System.currentTimeMillis()
    return runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
}

fun String?.toEpochMillisOrNull(): Long? {
    if (isNullOrBlank()) return null
    return runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()
}
