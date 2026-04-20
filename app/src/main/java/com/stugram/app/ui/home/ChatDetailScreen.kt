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
import org.json.JSONObject
import java.util.UUID
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.DirectConversationModel
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.data.repository.ChatRepository
import com.stugram.app.data.repository.GroupChatRepository
import com.stugram.app.data.repository.MessagesInboxCache
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.ui.theme.IosEmojiFont
import com.stugram.app.ui.theme.PremiumBlue
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import com.stugram.app.core.socket.ChatSocketManager
import com.stugram.app.core.notification.PushNotificationBus
import com.stugram.app.core.notification.ForegroundChatRegistry
import com.stugram.app.core.messaging.ForwardDraft
import com.stugram.app.core.messaging.ForwardDraftStore
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
    onNavigateToGroupChat: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context.applicationContext) }
    val chatSocketManager = remember { ChatSocketManager.getInstance(tokenManager) }
    val profileRepository = remember { ProfileRepository() }
    val chatRepository = remember {
        RetrofitClient.init(context.applicationContext)
        ChatRepository()
    }
    val groupChatRepository = remember { GroupChatRepository() }
    val currentUser by tokenManager.currentUser.collectAsState(initial = null)
    val forwardDraft by ForwardDraftStore.draft.collectAsState(initial = null)
    val remoteProfile by produceState<ProfileModel?>(initialValue = null, key1 = userName) {
        value = runCatching { profileRepository.getProfile(userName).body()?.data }.getOrNull()
    }
    val backgroundColor = if (isDarkMode) Color.Black else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glassBorder = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(0.1f)
    val accentBlue = Color(0xFF00A3FF)
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val headerHeight = 110.dp

    var messageText by remember { mutableStateOf("") }
    
    var showChatInfo by remember { mutableStateOf(false) }
    var isMuted by remember(conversationId) { mutableStateOf(false) }
    var showSearchModal by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember(conversationId) { mutableStateOf<List<MessageData>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var showMemberProfile by remember { mutableStateOf(false) }
    var replyingToMessage by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var selectedMessageForActions by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var messageBounds by remember(conversationId) { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var editingMessage by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var conversationDetail by remember(conversationId) { mutableStateOf<DirectConversationModel?>(null) }
    var showForwardPicker by remember(conversationId) { mutableStateOf(false) }
    var forwardSourceMessage by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var showDeleteDialogFor by remember(conversationId) { mutableStateOf<MessageData?>(null) }
    var deleteForEveryone by remember(conversationId) { mutableStateOf(false) }
    var highlightedMessageId by remember(conversationId) { mutableStateOf<String?>(null) }
    
    var isTyping by remember { mutableStateOf(false) }
    var typingUser by remember { mutableStateOf<String?>(null) }
    var isMemberProfileRefreshing by remember { mutableStateOf(false) }

    var messages by remember(conversationId) { mutableStateOf(listOf<MessageData>()) }
    var animatedMessageIds by remember(conversationId) { mutableStateOf(setOf<String>()) }
    var page by remember(conversationId) { mutableIntStateOf(1) }
    var totalPages by remember(conversationId) { mutableIntStateOf(1) }
    var isInitialLoading by remember(conversationId) { mutableStateOf(false) }
    var isLoadingMore by remember(conversationId) { mutableStateOf(false) }
    var loadError by remember(conversationId) { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun mergeMessages(existing: List<MessageData>, incoming: List<MessageData>): List<MessageData> {
        // Source of truth: backend IDs + createdAt. Socket messages merge in without duplicates.
        val byBackendId = LinkedHashMap<String, MessageData>(existing.size + incoming.size)
        fun put(msg: MessageData) {
            val key = msg.backendId ?: return
            val prev = byBackendId[key]
            if (prev == null) {
                byBackendId[key] = msg
            } else {
                byBackendId[key] = prev.mergeFrom(msg)
            }
        }
        existing.forEach(::put)
        incoming.forEach(::put)
        return byBackendId.values
            .sortedWith(compareBy<MessageData> { it.timestamp }.thenBy { it.backendId ?: "" }) // ASC by server time
    }

    fun removeOptimisticMessage(tempBackendId: String) {
        messages = messages.filterNot { it.backendId == tempBackendId }
    }

    fun finalizeOptimisticMessage(tempBackendId: String, sentMessage: MessageData) {
        removeOptimisticMessage(tempBackendId)
        messages = mergeMessages(messages, listOf(sentMessage))
    }

    fun buildOptimisticMessage(
        tempBackendId: String,
        text: String,
        replyPreview: String? = null,
        messageType: String = "text",
        media: ChatMediaUi? = null
    ): MessageData {
        return MessageData(
            id = tempBackendId.hashCode(),
            backendId = tempBackendId,
            text = text,
            isMe = true,
            senderId = currentUser?.id,
            isNew = true,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            senderName = currentUser?.fullName ?: currentUser?.username,
            messageType = messageType,
            media = media,
            replyPreview = replyPreview
        )
    }

    suspend fun loadMessages(targetPage: Int, append: Boolean) {
        if (conversationId.isBlank() || isRequest) {
            messages = emptyList()
            loadError = null
            return
        }
        if (append) isLoadingMore = true else isInitialLoading = true
        loadError = null
        runCatching {
            chatRepository.getConversationMessages(conversationId, targetPage, 30)
        }.onSuccess { response ->
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val newItems = body.data
                    .map { it.toUiMessage(currentUser?.id) }
                    .filter { it.text.isNotBlank() || it.media != null || !it.replyPreview.isNullOrBlank() }
                messages = mergeMessages(messages, newItems)
                page = body.meta?.page ?: targetPage
                totalPages = body.meta?.totalPages ?: 1
                val unseenIncoming = newItems.filter { !it.isMe && it.status != MessageStatus.READ }
                unseenIncoming.forEach { message ->
                    message.backendId?.let { backendId ->
                        runCatching { chatRepository.markMessageSeen(backendId) }
                    }
                }
            } else {
                loadError = response.apiErrorMessage("Could not load conversation")
                if (!append) messages = emptyList()
            }
        }.onFailure {
            loadError = it.message
            if (!append) messages = emptyList()
        }
        isInitialLoading = false
        isLoadingMore = false
    }

    LaunchedEffect(conversationId, isRequest, currentUser?.id) {
        page = 1
        totalPages = 1
        loadMessages(targetPage = 1, append = false)
        
        if (!isRequest && conversationId.isNotBlank()) {
            chatSocketManager.connect()
            chatSocketManager.joinConversation(conversationId)
            conversationDetail = runCatching { chatRepository.getConversationDetail(conversationId).body()?.data }.getOrNull()
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect
        
        chatSocketManager.newMessages.collect { socketMessage ->
            if (socketMessage.conversation == conversationId) {
                val uiMessage = socketMessage.toUiMessage(currentUser?.id)
                messages = mergeMessages(messages, listOf(uiMessage))
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect

        chatSocketManager.messageSeenEvents.collect { event ->
            if (event.conversationId == conversationId) {
                messages = mergeMessages(messages, listOf(event.message.toUiMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect

        chatSocketManager.messageReactionEvents.collect { event ->
            if (event.conversationId == conversationId) {
                messages = mergeMessages(messages, listOf(event.message.toUiMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect

        chatSocketManager.messageEditedEvents.collect { event ->
            if (event.conversationId == conversationId) {
                messages = mergeMessages(messages, listOf(event.message.toUiMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect

        chatSocketManager.messageForwardedEvents.collect { event ->
            if (event.conversationId == conversationId) {
                messages = mergeMessages(messages, listOf(event.message.toUiMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect

        chatSocketManager.messageDeletedEvents.collect { event ->
            if (event.conversationId == conversationId) {
                messages = messages.filterNot { it.backendId == event.messageId }
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect

        chatSocketManager.messageDeletedForEveryoneEvents.collect { event ->
            if (event.conversationId == conversationId) {
                messages = mergeMessages(messages, listOf(event.message.toUiMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect

        chatSocketManager.conversationUpdates.collect { updatedConversation ->
            if (updatedConversation.id == conversationId) {
                conversationDetail = updatedConversation
                loadMessages(targetPage = 1, append = false)
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect
        
        chatSocketManager.typingEvents.collect { event ->
            if (event.conversationId == conversationId) {
                isTyping = event.isTyping
                typingUser = if (event.isTyping) event.username else null
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect

        PushNotificationBus.events.collect { event ->
            if (event.conversationId == conversationId) {
                loadMessages(targetPage = 1, append = false)
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank() || isRequest) return@LaunchedEffect
        chatSocketManager.connectionEvents.collect { event ->
            if (event is com.stugram.app.core.socket.SocketConnectionEvent.Reconnected) {
                // On reconnect, pull latest server history to recover missed messages and seen states.
                loadMessages(targetPage = 1, append = false)
            }
        }
    }

    // Pagination trigger: when user scrolls near the top (older messages).
    LaunchedEffect(listState.firstVisibleItemIndex, isLoadingMore, page, totalPages) {
        if (!isInitialLoading && !isLoadingMore && page < totalPages && listState.firstVisibleItemIndex <= 2 && messages.isNotEmpty()) {
            loadMessages(targetPage = page + 1, append = true)
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
            if (!isRequest && conversationId.isNotBlank()) {
                chatSocketManager.sendTypingStop(conversationId = conversationId)
            }
        }
    }

    var lastTypingSentAt by remember { mutableLongStateOf(0L) }
    fun handleTyping(newText: String) {
        if (!isRequest && conversationId.isNotBlank()) {
            if (newText.isBlank()) {
                chatSocketManager.sendTypingStop(conversationId = conversationId)
                lastTypingSentAt = 0L
            } else {
                val now = System.currentTimeMillis()
                if (now - lastTypingSentAt > 3000) {
                    chatSocketManager.sendTypingStart(conversationId = conversationId)
                    lastTypingSentAt = now
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        val clipboardManager = LocalClipboardManager.current
        val activeForwardDraft = forwardDraft?.takeIf { it.targetConversationId == conversationId }
        val firstUnreadIndex = remember(messages, currentUser?.id) {
            messages.indexOfFirst { !it.isMe && it.status != MessageStatus.READ }
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
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = headerHeight + 16.dp, bottom = 132.dp)
                ) {
                    if (isInitialLoading) {
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
                    itemsIndexed(messages, key = { _, msg -> msg.backendId ?: msg.id }) { index, message ->
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
                                animateFromInput = message.backendId != null && animatedMessageIds.contains(message.backendId),
                                highlighted = message.backendId != null && message.backendId == highlightedMessageId,
                                onTap = { selectedMessageForActions = message },
                                onLongPress = { selectedMessageForActions = message },
                                onGloballyPositioned = { coords ->
                                    message.backendId?.let { backendId ->
                                        messageBounds = messageBounds + (backendId to coords.boundsInWindow())
                                    }
                                }
                            )
                        }
                    }
                }

                // --- TOP AREA (Liquid Glass Header) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                ) {
                    val glassTint = if (isDarkMode) Color.Black else Color.White
                    val blurMaskStart = if (isDarkMode) Color.Black.copy(alpha = 0.98f) else Color.White.copy(alpha = 0.94f)
                    val blurMaskMid = if (isDarkMode) Color.Black.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.88f)

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
                        AppBanner(
                            imageModel = remoteProfile?.banner,
                            title = remoteProfile?.fullName ?: userName,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(80.dp),
                            isDarkMode = isDarkMode,
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                        )
                    }

                    // 2. Specified Glass Opacity Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to glassTint.copy(alpha = if (isDarkMode) 0.22f else 0.16f),
                                    0.6f to glassTint.copy(alpha = if (isDarkMode) 0.1f else 0.07f),
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
                            onClick = {
                                keyboardController?.hide()
                                showMemberProfile = true
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
            }
        }

        AnimatedVisibility(
            visible = conversationDetail?.pinnedMessage != null,
            enter = fadeIn(tween(120)) + expandVertically(tween(120)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = headerHeight + 10.dp, start = 12.dp, end = 12.dp)
        ) {
            conversationDetail?.pinnedMessage?.let { pinned ->
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

        // --- BOTTOM AREA ---
        if (isRequest) {
            RequestActionButtons(isDarkMode, onBack)
        } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                    val glassTint = if (isDarkMode) Color.Black else Color.White

                    // 1. Glass Opacity Layer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.4f to glassTint.copy(alpha = if (isDarkMode) 0.14f else 0.08f),
                                    1.0f to glassTint.copy(alpha = if (isDarkMode) 0.22f else 0.14f)
                                )
                            )
                    )

                    // 3. The Input Row itself
                    RichChatComposer(
                        isDarkMode = isDarkMode,
                        textValue = messageText,
                        onTextValueChange = {
                            messageText = it
                            handleTyping(it)
                            // Keep typing anchored near bottom (latest).
                            scope.launch { scrollToBottom() }
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
                                messageText = ""
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
                                        val response = runCatching {
                                            chatRepository.editMessage(backendId, text)
                                        }.getOrNull()
                                        val updated = response?.body()?.data
                                        if (response?.isSuccessful == true && updated != null) {
                                            messages = mergeMessages(messages, listOf(updated.toUiMessage(currentUser?.id)))
                                            editingMessage = null
                                            messageText = ""
                                            selectedMessageForActions = null
                                        } else {
                                            Toast.makeText(context, response.apiErrorMessage("Could not edit message"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else if (activeForwardDraft != null) {
                                val sourceMessageId = activeForwardDraft.sourceMessageId
                                if (sourceMessageId.isNotBlank()) {
                                    scope.launch {
                                        val response = runCatching {
                                            chatRepository.forwardMessage(
                                                conversationId = conversationId,
                                                sourceMessageId = sourceMessageId,
                                                comment = text.takeIf { it.isNotBlank() }
                                            )
                                        }.getOrNull()
                                        val sent = response?.body()?.data
                                        if (response?.isSuccessful == true && sent != null) {
                                            messages = mergeMessages(messages, listOf(sent.toUiMessage(currentUser?.id)))
                                            animatedMessageIds = animatedMessageIds + sent.id
                                            messageText = ""
                                            ForwardDraftStore.clear()
                                            scope.launch { scrollToBottom() }
                                            delay(260)
                                            animatedMessageIds = animatedMessageIds - sent.id
                                        } else {
                                            Toast.makeText(context, response?.message().orEmpty().ifBlank { "Could not forward message" }, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else if (text.isNotBlank()) {
                                val tempBackendId = "local-${UUID.randomUUID()}"
                                val replyTargetId = replyingToMessage?.backendId
                                val optimisticReplyPreview = replyingToMessage?.text?.takeIf { value -> value.isNotBlank() }
                                    ?: replyingToMessage?.replyPreview
                                val optimistic = buildOptimisticMessage(
                                    tempBackendId = tempBackendId,
                                    text = text,
                                    replyPreview = optimisticReplyPreview
                                )
                                messages = mergeMessages(messages, listOf(optimistic))
                                animatedMessageIds = animatedMessageIds + tempBackendId
                                messageText = ""
                                replyingToMessage = null
                                scope.launch { scrollToBottom() }
                                scope.launch {
                                    val response = runCatching {
                                        chatRepository.sendMessage(
                                            conversationId = conversationId,
                                            request = com.stugram.app.data.remote.model.SendChatMessageRequest(
                                                text = text,
                                                replyToMessageId = replyTargetId
                                            )
                                        )
                                    }.getOrNull()
                                    val sent = response?.body()?.data
                                    if (response?.isSuccessful == true && sent != null) {
                                        finalizeOptimisticMessage(tempBackendId, sent.toUiMessage(currentUser?.id))
                                        animatedMessageIds = animatedMessageIds - tempBackendId + sent.id
                                        scope.launch { scrollToBottom() }
                                        kotlinx.coroutines.delay(260)
                                        animatedMessageIds = animatedMessageIds - sent.id
                                    } else {
                                        removeOptimisticMessage(tempBackendId)
                                        animatedMessageIds = animatedMessageIds - tempBackendId
                                        val errorMessage = when {
                                            response == null -> "Could not send message"
                                            response.code() == 401 -> "Session expired. Please sign in again."
                                            else -> response.apiErrorMessage("Could not send message")
                                        }
                                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onSendMedia = { uri, mimeType, messageTypeOverride, replyId ->
                            val tempBackendId = "local-${UUID.randomUUID()}"
                            val optimisticMessageType = messageTypeOverride ?: if (mimeType.startsWith("video")) "video" else "image"
                            val optimistic = buildOptimisticMessage(
                                tempBackendId = tempBackendId,
                                text = "",
                                replyPreview = replyingToMessage?.text?.takeIf { value -> value.isNotBlank() }
                                    ?: replyingToMessage?.replyPreview,
                                messageType = optimisticMessageType,
                                media = ChatMediaUi(
                                    url = uri.toString(),
                                    type = optimisticMessageType,
                                    fileName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                                        ?: if (mimeType.startsWith("video")) "Video" else "Image",
                                    mimeType = mimeType
                                )
                            )
                            messages = mergeMessages(messages, listOf(optimistic))
                            animatedMessageIds = animatedMessageIds + tempBackendId
                            replyingToMessage = null
                            scope.launch { scrollToBottom() }
                            val response = runCatching {
                                chatRepository.sendMediaMessage(context, conversationId, uri, mimeType, replyId, messageTypeOverride)
                            }.getOrNull()
                            val sent = response?.body()?.data
                            if (response?.isSuccessful == true && sent != null) {
                                finalizeOptimisticMessage(tempBackendId, sent.toUiMessage(currentUser?.id))
                                animatedMessageIds = animatedMessageIds - tempBackendId + sent.id
                                scope.launch { scrollToBottom() }
                                kotlinx.coroutines.delay(260)
                                animatedMessageIds = animatedMessageIds - sent.id
                                true
                            } else {
                                removeOptimisticMessage(tempBackendId)
                                animatedMessageIds = animatedMessageIds - tempBackendId
                                val errorMessage = when {
                                    response == null -> "Could not send media"
                                    response.code() == 401 -> "Session expired. Please sign in again."
                                    else -> response.apiErrorMessage("Could not send media")
                                }
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                                false
                            }
                        },
                        onSendStructured = { structuredText, replyId ->
                            val tempBackendId = "local-${UUID.randomUUID()}"
                            val optimistic = buildOptimisticMessage(
                                tempBackendId = tempBackendId,
                                text = structuredText,
                                replyPreview = replyingToMessage?.text?.takeIf { value -> value.isNotBlank() }
                                    ?: replyingToMessage?.replyPreview
                            )
                            messages = mergeMessages(messages, listOf(optimistic))
                            animatedMessageIds = animatedMessageIds + tempBackendId
                            replyingToMessage = null
                            scope.launch { scrollToBottom() }
                            val response = runCatching {
                                chatRepository.sendMessage(
                                    conversationId = conversationId,
                                    request = com.stugram.app.data.remote.model.SendChatMessageRequest(
                                        text = structuredText,
                                        replyToMessageId = replyId
                                    )
                                )
                            }.getOrNull()
                            val sent = response?.body()?.data
                            if (response?.isSuccessful == true && sent != null) {
                                finalizeOptimisticMessage(tempBackendId, sent.toUiMessage(currentUser?.id))
                                animatedMessageIds = animatedMessageIds - tempBackendId + sent.id
                                scope.launch { scrollToBottom() }
                                kotlinx.coroutines.delay(260)
                                animatedMessageIds = animatedMessageIds - sent.id
                                true
                            } else {
                                removeOptimisticMessage(tempBackendId)
                                animatedMessageIds = animatedMessageIds - tempBackendId
                                val errorMessage = when {
                                    response == null -> "Could not send message"
                                    response.code() == 401 -> "Session expired. Please sign in again."
                                    else -> response.apiErrorMessage("Could not send message")
                                }
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                                false
                            }
                        },
                        replyToMessageId = replyingToMessage?.backendId,
                        sendEnabled = conversationId.isNotBlank(),
                        conversationId = conversationId,
                        isGroup = false,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
                    scope.launch {
                        val res = runCatching {
                            if (isMuted) chatRepository.unmuteConversation(conversationId) else chatRepository.muteConversation(conversationId, durationMinutes = 60)
                        }.getOrNull()
                        if (res?.isSuccessful == true) {
                            isMuted = !isMuted
                        }
                    }
                },
                onReport = {
                    val uid = remoteProfile?.id ?: return@ChatInfoScreen
                    scope.launch { runCatching { chatRepository.reportUser(uid, "inappropriate") } }
                },
                onBlock = {
                    val uid = remoteProfile?.id ?: return@ChatInfoScreen
                    scope.launch { runCatching { chatRepository.blockUser(uid) } }
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
                        onValueChange = { searchQuery = it; searchError = null },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type keywords…") }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (searchLoading) "Searching…" else "Search",
                        color = accentBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            if (searchQuery.trim().isBlank()) return@clickable
                            scope.launch {
                                searchLoading = true
                                searchError = null
                                val res = runCatching { chatRepository.searchConversationMessages(conversationId, searchQuery.trim(), 1, 30) }.getOrNull()
                                if (res?.isSuccessful == true) {
                                    val mapped = res.body()?.data.orEmpty().map { api ->
                                        MessageData(
                                            id = api.id.hashCode(),
                                            backendId = api.id,
                                            isMe = api.sender?.id == currentUser?.id,
                                            senderId = api.sender?.id,
                                            text = api.text ?: "",
                                            timestamp = runCatching { api.createdAt?.let { java.time.Instant.parse(it).toEpochMilli() } }.getOrNull()
                                                ?: System.currentTimeMillis(),
                                            senderName = api.sender?.username,
                                            messageType = api.messageType,
                                            media = api.media?.let { m -> ChatMediaUi(url = m.url, type = api.messageType) },
                                            replyPreview = api.replyToMessage?.text,
                                            status = if (api.seenBy.any { it == currentUser?.id }) MessageStatus.READ else MessageStatus.SENT,
                                            reactions = api.reactions.map { reaction ->
                                                MessageReactionUi(
                                                    userId = reaction.user?.id,
                                                    username = reaction.user?.username,
                                                    fullName = reaction.user?.fullName,
                                                    avatar = reaction.user?.avatar,
                                                    emoji = reaction.emoji
                                                )
                                            }
                                        )
                                    }
                                    searchResults = mapped
                                } else {
                                    searchError = res?.message()?.ifBlank { "Search failed" } ?: "Search failed"
                                }
                                searchLoading = false
                            }
                        }
                    )
                    if (!searchError.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(searchError!!, color = Color.Red.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(searchResults, key = { it.backendId ?: it.id }) { msg ->
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
                        scope.launch {
                            runCatching { chatRepository.updateReaction(backendId, emoji) }
                                .onSuccess { response ->
                                    val updated = response.body()?.data
                                    if (response.isSuccessful && updated != null) {
                                        messages = mergeMessages(messages, listOf(updated.toUiMessage(currentUser?.id)))
                                    }
                                }
                        }
                    },
                    onForward = {
                        if (backendId != null) {
                            forwardSourceMessage = selectedMessage
                            selectedMessageForActions = null
                            showForwardPicker = true
                        }
                    },
                    onPinToggle = {
                        scope.launch {
                            runCatching {
                                if (conversationDetail?.pinnedMessage?.id == backendId) {
                                    chatRepository.unpinMessage(conversationId)
                                } else {
                                    chatRepository.pinMessage(conversationId, backendId)
                                }
                            }.onSuccess { response ->
                                val updatedConversation = response.body()?.data
                                if (response.isSuccessful && updatedConversation != null) {
                                    conversationDetail = updatedConversation
                                }
                            }
                        }
                    },
                    onEditMessage = {
                        if (selectedMessage.isMe && selectedMessage.messageType == "text" && selectedMessage.text.isNotBlank()) {
                            editingMessage = selectedMessage
                            replyingToMessage = null
                            messageText = selectedMessage.text
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
                        scope.launch {
                            runCatching { chatRepository.removeReaction(backendId) }
                                .onSuccess { response ->
                                    val updated = response.body()?.data
                                    if (response.isSuccessful && updated != null) {
                                        messages = mergeMessages(messages, listOf(updated.toUiMessage(currentUser?.id)))
                                    }
                                }
                        }
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
                    val scopeValue = if (deleteForEveryone) "everyone" else "self"
                    scope.launch {
                        runCatching { chatRepository.deleteMessage(targetBackendId, scopeValue) }
                            .onSuccess { response ->
                                if (response.isSuccessful) {
                                    val result = response.body()?.data
                                    if (result?.deletedForEveryone == true) {
                                        result.message?.let { messages = mergeMessages(messages, listOf(it.toUiMessage(currentUser?.id))) }
                                    } else {
                                        messages = messages.filterNot { it.backendId == targetBackendId }
                                    }
                                }
                            }
                    }
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
}

@Composable
fun MessageBubble(
    message: MessageData,
    isDarkMode: Boolean,
    currentUserId: String?,
    animateFromInput: Boolean,
    highlighted: Boolean = false,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
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

    var runInputAnimation by remember(message.backendId, animateFromInput) { mutableStateOf(animateFromInput) }
    val entryTranslation by animateFloatAsState(
        targetValue = if (runInputAnimation) 56f else 0f,
        animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
        label = "message_entry_translation"
    )
    val entryScale by animateFloatAsState(
        targetValue = if (runInputAnimation) 0.96f else 1f,
        animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
        label = "message_entry_scale"
    )
    val entryAlpha by animateFloatAsState(
        targetValue = if (runInputAnimation) 0.55f else 1f,
        animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
        label = "message_entry_alpha"
    )
    LaunchedEffect(message.backendId, animateFromInput) {
        if (animateFromInput) runInputAnimation = false
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
                        Text(
                            text = "This message was deleted",
                            color = textColor.copy(alpha = 0.62f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                    if (!message.replyPreview.isNullOrBlank()) {
                            Text(
                                text = message.replyPreview,
                                color = textColor.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .border(0.5.dp, textColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    if (message.forwardedFromMessageId != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Forwarded message",
                            color = textColor.copy(alpha = 0.52f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    ChatMessageContent(
                        text = message.text,
                        messageType = message.messageType,
                        media = message.media,
                        isDarkMode = isDarkMode,
                        preferLightContent = isMe
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
                        Text(
                            text = "edited",
                            color = textColor.copy(alpha = 0.45f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(text = timeString, color = textColor.copy(alpha = 0.5f), fontSize = 10.sp)
                    if (isMe) {
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = if (message.status == MessageStatus.READ) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = null,
                            tint = if (message.status == MessageStatus.READ) Color(0xFF4CAF50) else textColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
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
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PushPin, null, tint = Color(0xFF00A3FF), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text, color = contentColor.copy(alpha = 0.65f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun ActionRow(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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

    if (anchorBounds == null) return

    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val panelWidthPx = with(density) { 300.dp.roundToPx() }
    val panelHeightGuessPx = with(density) { (expanded).let { if (it) 340.dp else 260.dp }.roundToPx() }
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
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    ) {
            Surface(
                modifier = Modifier
                .offset(x = with(density) { x.toDp() }, y = with(density) { y.toDp() })
                .width(300.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)),
            shape = RoundedCornerShape(24.dp),
            color = if (isDarkMode) Color(0xFF1B1B1D) else Color.White,
            border = BorderStroke(0.7.dp, if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    onDismiss()
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = contentColor.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.08f)),
                        modifier = Modifier.size(40.dp).clickable { expanded = !expanded }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.72f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = expanded, enter = fadeIn(tween(120)) + expandVertically(tween(120)), exit = fadeOut(tween(120)) + shrinkVertically(tween(120))) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val extra = listOf("🔥", "👏", "😁", "😎", "💯", "🎉")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            extra.forEach { emoji ->
                                Surface(
                                    shape = CircleShape,
                                    color = contentColor.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.08f)),
                                    modifier = Modifier.weight(1f).clickable {
                                        onReactionSelected(emoji)
                                        onDismiss()
                                    }
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(text = emoji, fontSize = 17.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = contentColor.copy(alpha = 0.08f))

                ActionRow("Reply", Icons.AutoMirrored.Filled.Reply, contentColor) {
                    onReply()
                    onDismiss()
                }
                ActionRow("Copy", Icons.Default.ContentCopy, contentColor) {
                    onCopy()
                    onDismiss()
                }
                if (canForward) {
                    ActionRow("Forward", Icons.Default.Send, contentColor) {
                        onForward()
                        onDismiss()
                    }
                }
                if (canPin) {
                    ActionRow(if (isPinned) "Unpin" else "Pin", Icons.Default.PushPin, contentColor) {
                        onPinToggle()
                        onDismiss()
                    }
                }
                if (canEdit) {
                    ActionRow("Edit", Icons.Default.Edit, contentColor) {
                        onEditMessage()
                        onDismiss()
                    }
                }
                val readAtDisplay = formatTimeLabel(onReadAt)
                if (!readAtDisplay.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = contentColor.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Read at $readAtDisplay", color = contentColor.copy(alpha = 0.72f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (currentReactionEmoji != null) {
                    ActionRow("Remove reaction", Icons.Default.RemoveCircle, contentColor.copy(alpha = 0.9f)) {
                        onRemoveReaction()
                        onDismiss()
                    }
                }
                if (isOwnMessage && onDeleteMessage != null) {
                    ActionRow("Delete", Icons.Default.Delete, Color.Red.copy(alpha = 0.92f)) {
                        onDeleteMessage()
                        onDismiss()
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

private fun MessageData.mergeFrom(other: MessageData): MessageData {
    val otherText = other.text.takeIf { it.isNotBlank() }
    val otherReply = other.replyPreview?.takeIf { it.isNotBlank() }
    return copy(
        text = otherText ?: text,
        isMe = isMe || other.isMe,
        senderId = other.senderId ?: senderId,
        timestamp = minOf(timestamp, other.timestamp).takeIf { it > 0 } ?: other.timestamp,
        status = if (status == MessageStatus.READ || other.status == MessageStatus.READ) MessageStatus.READ else other.status,
        senderName = other.senderName ?: senderName,
        messageType = if (other.messageType.isNotBlank()) other.messageType else messageType,
        media = other.media ?: media,
        replyPreview = otherReply ?: replyPreview,
        reactions = if (other.reactions.isNotEmpty()) other.reactions else reactions,
        forwardedFromMessageId = other.forwardedFromMessageId ?: forwardedFromMessageId,
        forwardedFromSenderId = other.forwardedFromSenderId ?: forwardedFromSenderId,
        forwardedFromConversationId = other.forwardedFromConversationId ?: forwardedFromConversationId,
        forwardedAt = other.forwardedAt ?: forwardedAt,
        readAt = other.readAt ?: readAt,
        editedAt = other.editedAt ?: editedAt,
        deletedGloballyAt = other.deletedGloballyAt ?: deletedGloballyAt
    )
}

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
                    targetState = isTyping,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "status"
                ) { typing ->
                    Text(
                        text = if (typing) "typing..." else "@$username",
                        color = Color(0xFF007AFF),
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

    val backgroundBrush = if (isDarkMode) {
        Color.White.copy(0.12f)
    } else {
        Color.Black.copy(0.06f)
    }
    
    val borderColor = if (isDarkMode) Color.White.copy(0.15f) else Color.Black.copy(0.1f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(backgroundBrush)
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

enum class MessageStatus { SENT, READ }

data class MessageReactionUi(
    val userId: String?,
    val username: String?,
    val fullName: String?,
    val avatar: String?,
    val emoji: String
)

data class MessageData(
    val id: Int,
    val backendId: String? = null,
    val text: String,
    val isMe: Boolean,
    val senderId: String? = null,
    var isNew: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val senderName: String? = null,
    val messageType: String = "text",
    val media: ChatMediaUi? = null,
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
    val seen = currentUserId != null && seenBy.any { it == currentUserId }
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
        backendId = id,
        text = messageTextValue,
        isMe = isMine,
        senderId = senderId,
        timestamp = createdAt.toEpochMillis(),
        status = if (seen) MessageStatus.READ else MessageStatus.SENT,
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
