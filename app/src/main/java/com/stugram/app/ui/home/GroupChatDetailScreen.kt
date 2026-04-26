@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.stugram.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import android.util.Log
import org.json.JSONObject
import java.util.UUID
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.GroupMemberModel
import com.stugram.app.data.repository.ChatLocalCache
import com.stugram.app.data.repository.ChatPendingOutbox
import com.stugram.app.data.repository.GroupChatRepository
import com.stugram.app.data.repository.MessagesInboxCache
import com.stugram.app.data.repository.PendingChatEnvelope
import com.stugram.app.domain.chat.ChatMessageSnapshot
import com.stugram.app.domain.chat.ChatReducer
import com.stugram.app.domain.chat.MessageSource
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.core.messaging.ForwardDraft
import com.stugram.app.core.messaging.ForwardDraftStore
import com.stugram.app.core.messaging.ChatOutboxScheduler
import com.stugram.app.ui.theme.PremiumBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import com.stugram.app.core.socket.ChatSocketManager
import com.stugram.app.core.notification.PushNotificationBus
import com.stugram.app.core.notification.ForegroundChatRegistry
import java.util.*

@Composable
fun GroupChatDetailScreen(
    groupId: String,
    groupName: String,
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNavigateToChat: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onNavigateToGroupChat: (String, String) -> Unit = { _, _ -> },
    onNavigateToPost: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context.applicationContext) }
    val chatSocketManager = remember { ChatSocketManager.getInstance(tokenManager) }
    val groupRepository = remember {
        RetrofitClient.init(context.applicationContext)
        GroupChatRepository()
    }
    val currentUser by tokenManager.currentUser.collectAsState(initial = null)
    val presenceMap by chatSocketManager.presenceState.collectAsState()
    val forwardDraft by ForwardDraftStore.draft.collectAsState(initial = null)
    var groupRefreshNonce by remember { mutableIntStateOf(0) }
    var groupDetail by remember(groupId, groupRefreshNonce) {
        mutableStateOf<com.stugram.app.data.remote.model.GroupConversationModel?>(null)
    }
    var groupMembers by remember(groupId, groupRefreshNonce) { mutableStateOf(emptyList<GroupMemberModel>()) }
    LaunchedEffect(groupId, groupRefreshNonce) {
        groupDetail = runCatching { groupRepository.getGroupChatDetail(groupId).body()?.data }.getOrNull()
        groupMembers = runCatching { groupRepository.getGroupMembers(groupId, page = 1, limit = 100).body()?.data.orEmpty() }.getOrDefault(emptyList())
    }
    val backgroundColor = if (isDarkMode) Color.Black else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val accentBlue = Color(0xFF00A3FF)
    
    val glassBorder = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(0.08f)

    val headerHeight = 110.dp
    val headerContentHeight = 52.dp
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pinnedBannerTop = statusBarTop + 64.dp
    val onlineMembersCount = remember(groupMembers, presenceMap, currentUser?.id) {
        groupMembers.count { member ->
            member.user.id != currentUser?.id && presenceMap[member.user.id]?.isOnline == true
        }
    }

    val draftKey = remember(groupId) { "group:$groupId" }
    var messageText by remember(groupId) {
        mutableStateOf(ChatPendingOutbox.loadDraft(context, draftKey))
    }
    var showGroupInfo by remember { mutableStateOf(false) }
    var showSearchModal by remember { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }
    var typingUser by remember { mutableStateOf<String?>(null) }
    var replyingToMessage by remember(groupId) { mutableStateOf<GroupMessageData?>(null) }
    var selectedMessageForActions by remember(groupId) { mutableStateOf<GroupMessageData?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchLoading by remember { mutableStateOf(false) }
    var searchResults by remember(groupId) { mutableStateOf<List<GroupMessageData>>(emptyList()) }
    var showForwardPicker by remember(groupId) { mutableStateOf(false) }
    var forwardSourceMessage by remember(groupId) { mutableStateOf<GroupMessageData?>(null) }
    var showDeleteDialogFor by remember(groupId) { mutableStateOf<GroupMessageData?>(null) }
    var deleteForEveryone by remember(groupId) { mutableStateOf(false) }
    var highlightedMessageId by remember(groupId) { mutableStateOf<String?>(null) }
    var messages by remember(groupId) { mutableStateOf(listOf<GroupMessageData>()) }
    var messageBounds by remember(groupId) { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var page by remember(groupId) { mutableIntStateOf(1) }
    var totalPages by remember(groupId) { mutableIntStateOf(1) }
    var isInitialLoading by remember(groupId) { mutableStateOf(false) }
    var isLoadingMore by remember(groupId) { mutableStateOf(false) }
    var loadError by remember(groupId) { mutableStateOf<String?>(null) }
    var unseenNewMessages by remember(groupId) { mutableIntStateOf(0) }
    var sendingLocalIds by remember(groupId) { mutableStateOf(setOf<String>()) }
    val recentSendFingerprints = remember(groupId) { LinkedHashMap<String, Long>() }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isAtLatest by remember(messages, listState) {
        derivedStateOf {
            messages.isEmpty() || listState.firstVisibleItemIndex <= 1
        }
    }

    fun mergeGroupMessages(
        existing: List<GroupMessageData>,
        incoming: List<GroupMessageData>,
        incomingSource: MessageSource = MessageSource.SocketEvent
    ): List<GroupMessageData> {
        val byBackendId = LinkedHashMap<String, GroupMessageData>(existing.size + incoming.size)
        val aliasToKey = LinkedHashMap<String, String>(existing.size + incoming.size)
        fun put(msg: GroupMessageData) {
            val aliases = listOfNotNull(msg.localId, msg.backendId).distinct()
            if (aliases.isEmpty()) return
            val existingKey = aliases.firstNotNullOfOrNull { alias ->
                aliasToKey[alias] ?: alias.takeIf { byBackendId.containsKey(it) }
            }
            val prev = existingKey?.let { byBackendId.remove(it) }
            val merged = if (prev == null) msg else prev.mergeFrom(msg, incomingSource)
            val key = merged.backendId ?: merged.localId ?: aliases.first()
            byBackendId[key] = merged
            (aliases + listOfNotNull(merged.localId, merged.backendId)).distinct().forEach { alias ->
                aliasToKey[alias] = key
            }
        }
        existing.forEach(::put)
        incoming.forEach(::put)
        return byBackendId.values.sortedWith(compareByDescending<GroupMessageData> { it.timestamp }.thenByDescending { it.backendId ?: it.localId ?: "" })
    }

    fun updateLocalMessage(localId: String, transform: (GroupMessageData) -> GroupMessageData) {
        messages = messages.map { message ->
            if (message.localId == localId || message.backendId == localId) transform(message) else message
        }
    }

    fun removeOptimisticMessage(localId: String) {
        messages = messages.filterNot { it.localId == localId || it.backendId == localId }
    }

    fun acceptGroupOutgoingFingerprint(fingerprint: String, windowMs: Long = 1_500L): Boolean {
        val now = System.currentTimeMillis()
        val iterator = recentSendFingerprints.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > windowMs) {
                iterator.remove()
            }
        }
        val previous = recentSendFingerprints[fingerprint]
        if (previous != null && now - previous <= windowMs) {
            Log.d("ChatReliability", "group_send_deduped groupId=$groupId source=fingerprint_guard timestamp=$now")
            return false
        }
        recentSendFingerprints[fingerprint] = now
        return true
    }

    fun finalizeOptimisticMessage(localId: String, sentMessage: GroupMessageData) {
        removeOptimisticMessage(localId)
        messages = mergeGroupMessages(messages, listOf(sentMessage), MessageSource.HttpResponse)
    }

    fun buildOptimisticMessage(
        localId: String,
        text: String,
        senderName: String,
        senderAvatar: String? = null,
        messageType: String = "text",
        media: ChatMediaUi? = null,
        replyPreview: String? = null,
        pendingPayload: PendingMessagePayload? = null,
        retryCount: Int = 0
    ): GroupMessageData {
        return GroupMessageData(
            id = localId.hashCode(),
            localId = localId,
            backendId = null,
            text = text,
            senderName = senderName,
            senderAvatar = senderAvatar,
            isMe = true,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            errorReason = null,
            retryCount = retryCount,
            messageType = messageType,
            media = media,
            pendingPayload = pendingPayload,
            replyPreview = replyPreview
        )
    }

    fun mergePersistedPendingMessages() {
        val pending = ChatPendingOutbox.loadGroup(context, groupId)
            .map { it.toGroupUiMessage(currentUser?.fullName ?: currentUser?.username ?: groupName, currentUser?.avatar) }
        if (pending.isNotEmpty()) {
            messages = mergeGroupMessages(messages, pending, MessageSource.LocalOptimistic)
        }
    }

    fun restoreCachedMessages() {
        val cached = ChatLocalCache.loadGroup(context, groupId)
            .map { it.toUiGroupMessage(currentUser?.id) }
        if (cached.isNotEmpty()) {
            messages = mergeGroupMessages(messages, cached, MessageSource.LocalCache)
        }
    }

    suspend fun sendPendingGroupMessage(message: GroupMessageData) {
        val localId = message.localId ?: message.backendId ?: return
        val payload = message.pendingPayload ?: return
        if (sendingLocalIds.contains(localId)) {
            Log.d(
                "ChatReliability",
                "group_send_deduped groupId=$groupId clientId=$localId timestamp=${System.currentTimeMillis()}"
            )
            return
        }
        sendingLocalIds = sendingLocalIds + localId
        ChatPendingOutbox.upsertGroup(context, groupId, localId, payload.toOutboxPayload())
        updateLocalMessage(localId) {
            it.copy(
                status = MessageStatus.SENDING,
                errorReason = null,
                retryCount = it.retryCount + 1
            )
        }

        var sendFailure: Throwable? = null
        val response = when {
            payload.mediaUri != null && payload.mimeType != null -> {
                runCatching {
                    groupRepository.sendGroupMediaMessage(
                        context = context,
                        groupId = groupId,
                        uri = android.net.Uri.parse(payload.mediaUri),
                        mimeType = payload.mimeType,
                        replyToMessageId = payload.replyToMessageId,
                        messageTypeOverride = payload.messageTypeOverride,
                        clientId = localId
                    )
                }.onFailure { sendFailure = it }.getOrNull()
            }
            payload.text != null -> {
                runCatching {
                    groupRepository.sendGroupMessage(
                        groupId = groupId,
                        request = com.stugram.app.data.remote.model.SendChatMessageRequest(
                            text = payload.text,
                            replyToMessageId = payload.replyToMessageId,
                            clientId = localId
                        )
                    )
                }.onFailure { sendFailure = it }.getOrNull()
            }
            else -> null
        }

        val sent = response?.body()?.data
        if (response?.isSuccessful == true && sent != null) {
            val confirmed = if (sent.clientId.isNullOrBlank()) sent.copy(clientId = localId) else sent
            ChatPendingOutbox.remove(context, localId)
            ChatLocalCache.upsertGroup(context, groupId, listOf(confirmed), MessageSource.HttpResponse)
            finalizeOptimisticMessage(localId, confirmed.toUiGroupMessage(currentUser?.id))
            scope.launch { listState.animateScrollToItem(0) }
        } else {
            val errorMessage = when {
                response == null -> sendFailure.chatSendErrorMessage()
                response.code() == 401 -> "Session expired. Please sign in again."
                else -> response.apiErrorMessage("Could not send message")
            }
            val status = if (response.shouldQueueForRetry()) MessageStatus.QUEUED else MessageStatus.FAILED
            Log.w(
                "ChatReliability",
                "group_send_result groupId=$groupId clientId=$localId status=$status httpCode=${response?.code()} retryable=${response.shouldQueueForRetry()} errorCategory=${sendFailure.chatSendErrorCategory()} timestamp=${System.currentTimeMillis()}"
            )
            if (status == MessageStatus.FAILED) {
                ChatPendingOutbox.remove(context, localId)
            } else {
                ChatPendingOutbox.markRetry(context, localId)
                ChatOutboxScheduler.schedule(context)
            }
            updateLocalMessage(localId) {
                it.copy(
                    status = status,
                    errorReason = errorMessage
                )
            }
        }
        sendingLocalIds = sendingLocalIds - localId
    }

    suspend fun flushPendingGroupMessages() {
        val pending = ChatPendingOutbox.loadGroup(context, groupId)
        if (pending.isEmpty()) return
        val senderName = currentUser?.fullName ?: currentUser?.username ?: groupName
        messages = mergeGroupMessages(messages, pending.map { it.toGroupUiMessage(senderName, currentUser?.avatar) }, MessageSource.LocalOptimistic)
        pending.forEach { envelope ->
            sendPendingGroupMessage(envelope.toGroupUiMessage(senderName, currentUser?.avatar))
        }
    }

    var lastTypingSentAt by remember { mutableLongStateOf(0L) }
    fun handleTyping(newText: String) {
        if (groupId.isNotBlank()) {
            if (newText.isBlank()) {
                chatSocketManager.sendTypingStop(groupId = groupId)
                lastTypingSentAt = 0L
            } else {
                val now = System.currentTimeMillis()
                if (now - lastTypingSentAt > 3000) {
                    chatSocketManager.sendTypingStart(groupId = groupId)
                    lastTypingSentAt = now
                }
            }
        }
    }

    LaunchedEffect(messageText, groupId) {
        if (groupId.isNotBlank() && messageText.isNotBlank()) {
            delay(3500)
            if (messageText.isNotBlank()) {
                chatSocketManager.sendTypingStop(groupId = groupId)
                lastTypingSentAt = 0L
            }
        }
    }

    DisposableEffect(groupId) {
        ForegroundChatRegistry.activeGroupId = groupId
        onDispose {
            if (ForegroundChatRegistry.activeGroupId == groupId) {
                ForegroundChatRegistry.activeGroupId = null
            }
            if (groupId.isNotBlank()) {
                chatSocketManager.sendTypingStop(groupId = groupId)
            }
        }
    }

    suspend fun loadMessages(targetPage: Int, append: Boolean) {
        if (groupId.isBlank()) {
            messages = emptyList()
            loadError = null
            return
        }
        MessagesInboxCache.clearGroupUnread(groupId)
        if (append) isLoadingMore = true else isInitialLoading = true
        loadError = null
        runCatching {
            groupRepository.getGroupMessages(groupId, targetPage, 30)
        }.onSuccess { response ->
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val newItems = body.data
                    .map { it.toUiGroupMessage(currentUser?.id) }
                    .filter { it.text.isNotBlank() || it.media != null || !it.replyPreview.isNullOrBlank() }
                ChatLocalCache.upsertGroup(context, groupId, body.data, MessageSource.PaginationLoad)
                messages = mergeGroupMessages(messages, newItems, MessageSource.PaginationLoad)
                page = body.meta?.page ?: targetPage
                totalPages = body.meta?.totalPages ?: 1
                newItems.filter { !it.isMe && it.status != MessageStatus.SEEN }.forEach { item ->
                    item.backendId?.let { messageId ->
                        runCatching { groupRepository.markGroupMessageSeen(groupId, messageId) }
                    }
                }
                MessagesInboxCache.clearGroupUnread(groupId)
            } else {
                loadError = response.apiErrorMessage("Could not load group chat")
            }
        }.onFailure {
            loadError = it.message
        }
        isInitialLoading = false
        isLoadingMore = false
    }

    LaunchedEffect(groupId, currentUser?.id) {
        page = 1
        totalPages = 1
        restoreCachedMessages()
        mergePersistedPendingMessages()
        loadMessages(targetPage = 1, append = false)
        
        if (groupId.isNotBlank()) {
            chatSocketManager.connect()
            chatSocketManager.joinGroup(groupId)
            MessagesInboxCache.clearGroupUnread(groupId)
            flushPendingGroupMessages()
        }
    }

    val activeForwardDraft = forwardDraft?.takeIf { it.targetGroupId == groupId }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.connectionEvents.collect { event ->
            if (event is com.stugram.app.core.socket.SocketConnectionEvent.Reconnected) {
                page = 1
                totalPages = 1
                flushPendingGroupMessages()
                loadMessages(targetPage = 1, append = false)
                groupRefreshNonce += 1
            }
        }
    }

    LaunchedEffect(messageText, draftKey) {
        ChatPendingOutbox.saveDraft(context, draftKey, messageText)
    }

    LaunchedEffect(isAtLatest, messages.size) {
        if (isAtLatest) unseenNewMessages = 0
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect
        
        chatSocketManager.groupMessages.collect { (socketGroupId, socketMessage) ->
            if (socketGroupId == groupId) {
                val wasAtLatest = isAtLatest
                val uiMessage = socketMessage.toUiGroupMessage(currentUser?.id)
                ChatLocalCache.upsertGroup(context, groupId, listOf(socketMessage), MessageSource.SocketEvent)
                // Prevent duplicates
                messages = mergeGroupMessages(messages, listOf(uiMessage))
                if (uiMessage.isMe || wasAtLatest) {
                    scope.launch { listState.animateScrollToItem(0) }
                } else {
                    unseenNewMessages += 1
                }
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupConversationUpdates.collect { updatedGroup ->
            if (updatedGroup.id == groupId) {
                groupRefreshNonce += 1
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.messageDeliveredEvents.collect { event ->
            if (event.groupId == groupId) {
                val messageId = event.messageId ?: return@collect
                messages = messages.map { message ->
                    if (message.backendId == messageId && messageStatusRank(message.status) < messageStatusRank(MessageStatus.DELIVERED)) {
                        message.copy(status = MessageStatus.DELIVERED)
                    } else {
                        message
                    }
                }
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMessageSeenEvents.collect { event ->
            if (event.groupId == groupId) {
                ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                messages = mergeGroupMessages(messages, listOf(event.message.toUiGroupMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMessageReactionEvents.collect { event ->
            if (event.groupId == groupId) {
                ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                messages = mergeGroupMessages(messages, listOf(event.message.toUiGroupMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMessageEditedEvents.collect { event ->
            if (event.groupId == groupId) {
                ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                messages = mergeGroupMessages(messages, listOf(event.message.toUiGroupMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMessageForwardedEvents.collect { event ->
            if (event.groupId == groupId) {
                ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                messages = mergeGroupMessages(messages, listOf(event.message.toUiGroupMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMessageDeletedForEveryoneEvents.collect { event ->
            if (event.groupId == groupId) {
                ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                messages = mergeGroupMessages(messages, listOf(event.message.toUiGroupMessage(currentUser?.id)))
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMessagePinnedEvents.collect { event ->
            if (event.groupId == groupId) {
                groupRefreshNonce += 1
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMessageUnpinnedEvents.collect { event ->
            if (event.groupId == groupId) {
                groupRefreshNonce += 1
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMessageDeletedEvents.collect { event ->
            if (event.groupId == groupId) {
                ChatLocalCache.removeGroup(context, groupId, event.messageId)
                messages = messages.filterNot { it.backendId == event.messageId }
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMemberAddedEvents.collect { event ->
            if (event.groupId == groupId) {
                groupRefreshNonce += 1
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        chatSocketManager.groupMemberRemovedEvents.collect { event ->
            if (event.groupId == groupId) {
                groupRefreshNonce += 1
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect
        
        chatSocketManager.typingEvents.collect { event ->
            if (event.groupId == groupId) {
                isTyping = event.isTyping
                typingUser = if (event.isTyping) event.username else null
            }
        }
    }

    LaunchedEffect(groupId) {
        if (groupId.isBlank()) return@LaunchedEffect

        PushNotificationBus.events.collect { event ->
            if (event.groupId == groupId) {
                // Active group already receives socket updates; skip duplicate reloads.
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, isLoadingMore, page, totalPages) {
        if (!isInitialLoading && !isLoadingMore && page < totalPages && listState.firstVisibleItemIndex >= messages.lastIndex - 2 && messages.isNotEmpty()) {
            loadMessages(targetPage = page + 1, append = true)
        }
    }

    LaunchedEffect(searchQuery, showSearchModal) {
        if (!showSearchModal) {
            searchResults = emptyList()
            searchLoading = false
            return@LaunchedEffect
        }
        val trimmed = searchQuery.trim()
        if (trimmed.isBlank()) {
            searchResults = emptyList()
            searchLoading = false
            return@LaunchedEffect
        }

        searchLoading = true
        delay(250)
        val response = runCatching { groupRepository.searchGroupMessages(groupId, trimmed, 1, 30) }.getOrNull()
        searchResults = if (response?.isSuccessful == true) {
            response.body()?.data.orEmpty().map { it.toUiGroupMessage(currentUser?.id) }
        } else {
            emptyList()
        }
        searchLoading = false
    }

    val unseenIncomingGroupMessageKey = remember(messages) {
        messages
            .filter { !it.isMe && it.status != MessageStatus.SEEN }
            .mapNotNull { it.backendId }
            .distinct()
            .joinToString("|")
    }

    LaunchedEffect(unseenIncomingGroupMessageKey) {
        if (unseenIncomingGroupMessageKey.isNotBlank()) {
            delay(180)
            unseenIncomingGroupMessageKey.split("|")
                .filter { it.isNotBlank() }
                .forEach { messageId ->
                    runCatching { groupRepository.markGroupMessageSeen(groupId, messageId) }
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        val clipboardManager = LocalClipboardManager.current
        val pinnedMessage = groupDetail?.pinnedMessage
        val firstUnreadIndex = remember(messages, currentUser?.id) {
            messages.indexOfFirst { !it.isMe && it.status != MessageStatus.SEEN }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (pinnedMessage != null) pinnedBannerTop + 46.dp else headerHeight + 8.dp,
                        bottom = 12.dp
                    )
                ) {
                    if (isInitialLoading && messages.isEmpty()) {
                        item("loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = accentBlue)
                            }
                        }
                    } else if (messages.isEmpty()) {
                        item("empty") {
                            ChatEmptyState(
                                title = "No messages yet",
                                subtitle = loadError ?: "Start the group conversation with the message field below.",
                                isDarkMode = isDarkMode
                            )
                        }
                    }
                    itemsIndexed(messages, key = { _, msg -> msg.stableKeyVm }) { index, message ->
                        val isSameDay = if (index > 0) {
                            isSameDay(message.timestamp, messages[index - 1].timestamp)
                        } else false

                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (index == 0 || !isSameDay) {
                                GroupDateHeader(message.timestamp, isDarkMode)
                            }
                            if (index == firstUnreadIndex) {
                                UnreadDividerChip(isDarkMode = isDarkMode)
                            }
                            GroupMessageBubble(
                                message,
                                isDarkMode,
                                currentUser?.id,
                                accentBlue,
                                highlighted = message.backendId != null && message.backendId == highlightedMessageId,
                                onRetry = {
                                    scope.launch { sendPendingGroupMessage(message) }
                                },
                                onTap = { },
                                onLongPress = {
                                    selectedMessageForActions = message
                                },
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
                        .padding(bottom = 24.dp)
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
                                    listState.animateScrollToItem(0)
                                    unseenNewMessages = 0
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
                        AppBanner(
                            imageModel = groupDetail?.avatar,
                            title = groupDetail?.name ?: groupName,
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
                            .height(headerContentHeight)
                    ) {
                        TelegramIosGlass(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(44.dp),
                            shape = CircleShape,
                            isDarkMode = isDarkMode,
                            onClick = onBack,
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = contentColor, modifier = Modifier.size(22.dp))
                        }

                        TelegramIosGlass(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .wrapContentWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            isDarkMode = isDarkMode,
                            onClick = { showGroupInfo = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = groupDetail?.name ?: groupName,
                                    color = contentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isTyping) {
                                        if (!typingUser.isNullOrBlank()) "$typingUser is typing..." else "Someone is typing..."
                                    } else {
                                        buildString {
                                            append("${groupDetail?.membersCount ?: 0} members")
                                            if (onlineMembersCount > 0) {
                                                append(" • $onlineMembersCount online")
                                            }
                                        }
                                    },
                                    color = if (isTyping || onlineMembersCount > 0) Color(0xFF00C853) else accentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        TelegramIosGlass(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(44.dp),
                            shape = CircleShape,
                            isDarkMode = isDarkMode,
                            onClick = { showGroupInfo = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Groups, null, tint = contentColor.copy(0.8f), modifier = Modifier.size(24.dp))
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

            // --- INPUT AREA ---
            RichChatComposer(
                isDarkMode = isDarkMode,
                textValue = messageText,
                onTextValueChange = {
                    messageText = it
                    handleTyping(it)
                },
                replyPreviewText = when {
                    activeForwardDraft != null -> buildString {
                        append("Forwarding")
                        activeForwardDraft.sourceSenderName?.takeIf { it.isNotBlank() }?.let { append(" from $it") }
                        activeForwardDraft.sourceText?.takeIf { it.isNotBlank() }?.let { append(": ${it.take(48)}") }
                    }
                    !replyingToMessage?.text.isNullOrBlank() -> replyingToMessage?.text
                    else -> replyingToMessage?.replyPreview
                },
                onCancelReply = {
                    if (activeForwardDraft != null) {
                        ForwardDraftStore.clear()
                    } else {
                        replyingToMessage = null
                    }
                },
                onSendText = {
                    if (activeForwardDraft != null) {
                        val sourceMessageId = activeForwardDraft.sourceMessageId
                        if (sourceMessageId.isNotBlank()) {
                            scope.launch {
                                val response = runCatching {
                                    groupRepository.forwardGroupMessage(
                                        groupId = groupId,
                                        sourceMessageId = sourceMessageId,
                                        comment = messageText.trim().takeIf { it.isNotBlank() }
                                    )
                                }.getOrNull()
                                val sent = response?.body()?.data
                                if (response?.isSuccessful == true && sent != null) {
                                    messages = mergeGroupMessages(messages, listOf(sent.toUiGroupMessage(currentUser?.id)))
                                    messageText = ""
                                    ForwardDraftStore.clear()
                                    scope.launch { listState.animateScrollToItem(0) }
                                } else {
                                    Toast.makeText(context, response?.message().orEmpty().ifBlank { "Could not forward message" }, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else if (messageText.isNotBlank() && groupId.isNotBlank()) {
                        val text = messageText.trim()
                        val replyTargetId = replyingToMessage?.backendId
                        if (!acceptGroupOutgoingFingerprint("text:${replyTargetId.orEmpty()}:$text")) return@RichChatComposer
                        val localId = "local-${UUID.randomUUID()}"
                        val optimistic = buildOptimisticMessage(
                            localId = localId,
                            text = text,
                            senderName = currentUser?.fullName ?: currentUser?.username ?: groupName,
                            senderAvatar = currentUser?.avatar,
                            replyPreview = replyingToMessage?.text?.takeIf { value -> value.isNotBlank() } ?: replyingToMessage?.replyPreview,
                            pendingPayload = PendingMessagePayload(
                                text = text,
                                replyToMessageId = replyTargetId
                            )
                        )
                        messages = mergeGroupMessages(messages, listOf(optimistic), MessageSource.LocalOptimistic)
                        messageText = ""
                        replyingToMessage = null
                        scope.launch { listState.animateScrollToItem(0) }
                        scope.launch { sendPendingGroupMessage(optimistic) }
                    }
                },
                onSendMedia = { uri, mimeType, messageTypeOverride, replyId ->
                    if (!acceptGroupOutgoingFingerprint("media:${replyId.orEmpty()}:$uri:$mimeType")) return@RichChatComposer true
                    val localId = "local-${UUID.randomUUID()}"
                    val optimisticMessageType = messageTypeOverride ?: if (mimeType.startsWith("video")) "video" else "image"
                    val optimistic = buildOptimisticMessage(
                        localId = localId,
                        text = "",
                        senderName = currentUser?.fullName ?: currentUser?.username ?: groupName,
                        senderAvatar = currentUser?.avatar,
                        messageType = optimisticMessageType,
                        media = ChatMediaUi(
                            url = uri.toString(),
                            type = optimisticMessageType,
                            fileName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                                ?: if (mimeType.startsWith("video")) "Video" else "Image",
                            mimeType = mimeType
                        ),
                        replyPreview = replyingToMessage?.text?.takeIf { value -> value.isNotBlank() } ?: replyingToMessage?.replyPreview,
                        pendingPayload = PendingMessagePayload(
                            mediaUri = uri.toString(),
                            mimeType = mimeType,
                            replyToMessageId = replyId,
                            messageTypeOverride = messageTypeOverride
                        )
                    )
                    messages = mergeGroupMessages(messages, listOf(optimistic), MessageSource.LocalOptimistic)
                    replyingToMessage = null
                    scope.launch { listState.animateScrollToItem(0) }
                    scope.launch { sendPendingGroupMessage(optimistic) }
                    true
                },
                onSendStructured = { structuredText, replyId ->
                    if (!acceptGroupOutgoingFingerprint("structured:${replyId.orEmpty()}:$structuredText")) return@RichChatComposer true
                    val localId = "local-${UUID.randomUUID()}"
                    val optimistic = buildOptimisticMessage(
                        localId = localId,
                        text = structuredText,
                        senderName = currentUser?.fullName ?: currentUser?.username ?: groupName,
                        senderAvatar = currentUser?.avatar,
                        replyPreview = replyingToMessage?.text?.takeIf { value -> value.isNotBlank() } ?: replyingToMessage?.replyPreview,
                        pendingPayload = PendingMessagePayload(
                            text = structuredText,
                            replyToMessageId = replyId
                        )
                    )
                    messages = mergeGroupMessages(messages, listOf(optimistic), MessageSource.LocalOptimistic)
                    replyingToMessage = null
                    scope.launch { listState.animateScrollToItem(0) }
                    scope.launch { sendPendingGroupMessage(optimistic) }
                    true
                },
                replyToMessageId = replyingToMessage?.backendId,
                sendEnabled = groupId.isNotBlank(),
                conversationId = groupId,
                isGroup = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        AnimatedVisibility(visible = showGroupInfo, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()) {
            GroupInfoScreen(
                groupName = groupDetail?.name ?: groupName,
                groupAvatar = groupDetail?.avatar,
                membersCount = groupDetail?.membersCount ?: groupMembers.size,
                members = groupMembers,
                isDarkMode = isDarkMode,
                onThemeChange = onThemeChange,
                onBack = { showGroupInfo = false },
                onSearchClick = { showGroupInfo = false; showSearchModal = true },
                onLeaveGroup = {
                    scope.launch {
                        val res = runCatching { groupRepository.leaveGroup(groupId) }.getOrNull()
                        if (res?.isSuccessful == true) {
                            onBack()
                        }
                    }
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
                    isPinned = groupDetail?.pinnedMessage?.id == backendId,
                    canEdit = selectedMessage.isMe && selectedMessage.messageType == "text" && selectedMessage.deletedGloballyAt == null,
                    canForward = selectedMessage.deletedGloballyAt == null,
                    canPin = selectedMessage.deletedGloballyAt == null,
                    anchorBounds = anchor,
                    onDismiss = { selectedMessageForActions = null },
                    onReactionSelected = { emoji ->
                        scope.launch {
                            runCatching { groupRepository.updateGroupMessageReaction(groupId, backendId, emoji) }
                                .onSuccess { response ->
                                    val updated = response.body()?.data
                                    if (response.isSuccessful && updated != null) {
                                        messages = mergeGroupMessages(messages, listOf(updated.toUiGroupMessage(currentUser?.id)))
                                    }
                                }
                        }
                    },
                    onForward = {
                        forwardSourceMessage = selectedMessage
                        selectedMessageForActions = null
                        showForwardPicker = true
                    },
                    onPinToggle = {
                        scope.launch {
                            runCatching {
                                if (groupDetail?.pinnedMessage?.id == backendId) {
                                    groupRepository.unpinGroupMessage(groupId)
                                } else {
                                    groupRepository.pinGroupMessage(groupId, backendId)
                                }
                            }.onSuccess { response ->
                                val updatedGroup = response.body()?.data
                                if (response.isSuccessful && updatedGroup != null) {
                                    groupRefreshNonce += 1
                                }
                            }
                        }
                    },
                    onEditMessage = {
                        if (selectedMessage.isMe && selectedMessage.messageType == "text" && selectedMessage.text.isNotBlank()) {
                            messageText = selectedMessage.text
                            replyingToMessage = null
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
                            runCatching { groupRepository.removeGroupMessageReaction(groupId, backendId) }
                                .onSuccess { response ->
                                    val updated = response.body()?.data
                                    if (response.isSuccessful && updated != null) {
                                        messages = mergeGroupMessages(messages, listOf(updated.toUiGroupMessage(currentUser?.id)))
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
                        runCatching { groupRepository.deleteGroupMessage(groupId, targetBackendId, scopeValue) }
                            .onSuccess { response ->
                                if (response.isSuccessful) {
                                    val result = response.body()?.data
                                    if (result?.deletedForEveryone == true) {
                                        result.message?.let { messages = mergeGroupMessages(messages, listOf(it.toUiGroupMessage(currentUser?.id))) }
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
                                sourceGroupId = groupId,
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
                                sourceGroupId = groupId,
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

        if (showSearchModal) {
            ModalBottomSheet(
                onDismissRequest = { showSearchModal = false },
                containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray.copy(0.4f)) }
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                    Text("Search in group", fontWeight = FontWeight.Black, color = contentColor)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
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
                                val res = runCatching { groupRepository.searchGroupMessages(groupId, searchQuery.trim(), 1, 30) }.getOrNull()
                                searchResults = if (res?.isSuccessful == true) {
                                    res.body()?.data.orEmpty().map { it.toUiGroupMessage(currentUser?.id) }
                                } else {
                                    emptyList()
                                }
                                searchLoading = false
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(searchResults, key = { it.backendId ?: it.id.toString() }) { msg ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(msg.senderName, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 12.sp)
                                Text(msg.text, color = contentColor.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 2)
                            }
                            HorizontalDivider(color = contentColor.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(
    message: GroupMessageData,
    isDarkMode: Boolean,
    currentUserId: String?,
    accentBlue: Color,
    highlighted: Boolean = false,
    onRetry: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onNavigateToPost: (String) -> Unit = {},
    onGloballyPositioned: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {}
) {
    val alignment = if (message.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isMe) PremiumBlue else (if (isDarkMode) Color(0xFF262626) else Color(0xFFF0F0F0))
    val textColor = if (message.isMe) Color.White else (if (isDarkMode) Color.White else Color.Black)
    val shape = if (message.isMe) { RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp) } else { RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp) }
    val highlightedBorder = if (highlighted) BorderStroke(1.2.dp, Color(0xFF00A3FF).copy(alpha = 0.72f)) else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onGloballyPositioned(onGloballyPositioned)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress
            ),
        horizontalAlignment = alignment
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start) {
            if (!message.isMe) {
                AppAvatar(
                    imageModel = message.senderAvatar,
                    name = message.senderName,
                    username = message.senderName,
                    modifier = Modifier.size(28.dp),
                    isDarkMode = isDarkMode,
                    fontSize = 11.sp
                )
                Spacer(Modifier.width(6.dp))
            }
            Column(horizontalAlignment = alignment) {
                if (!message.isMe) { 
                    Text(message.senderName, color = (if (isDarkMode) Color.White else Color.Black).copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) 
                }
                Surface(color = bubbleColor, shape = shape, border = highlightedBorder) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (message.deletedGloballyAt != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                icon = Icons.Default.Reply,
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
                                Icon(Icons.Default.NearMe, null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
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
                        preferLightContent = message.isMe,
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
                                isMe = message.isMe,
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MessageStatusFooter(
                                status = message.status,
                                timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                                isMe = message.isMe,
                                textColor = textColor,
                                accentColor = PremiumBlue,
                                onRetry = onRetry,
                                isGroupMessage = true
                            )
                        }
                        if (message.status == MessageStatus.FAILED && !message.errorReason.isNullOrBlank()) {
                            Text(
                                text = message.errorReason,
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupInfoScreen(
    groupName: String, 
    groupAvatar: String?,
    membersCount: Int,
    members: List<GroupMemberModel>,
    isDarkMode: Boolean, 
    onThemeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onLeaveGroup: () -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val accentBlue = Color(0xFF00A3FF)
    Column(modifier = Modifier.fillMaxSize().background(backgroundColor).statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            AppAvatar(
                imageModel = groupAvatar,
                name = groupName,
                username = groupName,
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
                text = groupName,
                color = contentColor,
                fontSize = if (isInfoNameExpanded) 24.sp else if (groupName.length > 15) 18.sp else 22.sp,
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

            Text(text = "$membersCount members", color = accentBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoActionButton(Icons.Default.Search, "Search", isDarkMode, onClick = onSearchClick)
                InfoActionButton(Icons.AutoMirrored.Filled.ExitToApp, "Leave", isDarkMode, onClick = onLeaveGroup)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                SettingsListItem(Icons.Default.Groups, "Members", "$membersCount people in this group", contentColor)
                SettingsListItem(Icons.AutoMirrored.Filled.ExitToApp, "Leave group", "Exit this group chat", Color.Red, onClick = onLeaveGroup)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(
                    text = "Members",
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (members.isEmpty()) {
                    Text(
                        text = "No members available right now.",
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    members.forEach { member ->
                        GroupMemberRow(member = member, isDarkMode = isDarkMode)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GroupMemberRow(member: GroupMemberModel, isDarkMode: Boolean) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppAvatar(
            imageModel = member.user.avatar,
            name = member.user.fullName,
            username = member.user.username,
            modifier = Modifier.size(42.dp),
            isDarkMode = isDarkMode,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.user.fullName.takeIf { it.isNotBlank() } ?: member.user.username,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "@${member.user.username}",
                color = contentColor.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        if (member.role.isNotBlank()) {
            Text(
                text = member.role.replaceFirstChar { it.uppercase() },
                color = Color(0xFF00A3FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun ChatMessageModel.toUiGroupMessage(currentUserId: String?): GroupMessageData {
    val isMine = currentUserId != null && sender?.id == currentUserId
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
    return GroupMessageData(
        id = id.hashCode(),
        localId = clientId,
        backendId = id,
        text = messageTextValue,
        senderName = sender?.fullName?.takeIf { it.isNotBlank() } ?: sender?.username ?: "Unknown member",
        senderAvatar = sender?.avatar,
        isMe = isMine,
        timestamp = createdAt.toGroupEpochMillis(),
        status = when {
            seen -> MessageStatus.SEEN
            deliveredAt != null -> MessageStatus.DELIVERED
            else -> MessageStatus.SENT
        },
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

private fun String?.toGroupEpochMillis(): Long {
    if (this.isNullOrBlank()) return System.currentTimeMillis()
    return runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
}

data class GroupMessageData(
    val id: Int,
    val localId: String? = null,
    val backendId: String? = null,
    val text: String,
    val senderName: String,
    val senderAvatar: String? = null,
    val isMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val errorReason: String? = null,
    val retryCount: Int = 0,
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

private fun PendingChatEnvelope.toGroupUiMessage(senderName: String, senderAvatar: String?): GroupMessageData {
    val mediaType = payload.messageTypeOverride ?: when {
        payload.mimeType?.startsWith("video") == true -> "video"
        payload.mimeType?.startsWith("audio") == true -> "voice"
        payload.mediaUri != null -> "image"
        else -> "text"
    }
    return GroupMessageData(
        id = localId.hashCode(),
        localId = localId,
        backendId = null,
        text = payload.text.orEmpty(),
        senderName = senderName,
        senderAvatar = senderAvatar,
        isMe = true,
        timestamp = createdAt,
        status = if (terminalError.isNullOrBlank()) MessageStatus.QUEUED else MessageStatus.FAILED,
        errorReason = terminalError,
        retryCount = retryCount,
        messageType = mediaType,
        media = payload.mediaUri?.let {
            ChatMediaUi(
                url = it,
                type = mediaType,
                fileName = if (mediaType == "video") "Video" else if (mediaType == "voice") "Voice message" else "Photo",
                mimeType = payload.mimeType
            )
        },
        pendingPayload = payload.toPendingMessagePayload()
    )
}

private fun GroupMessageData.mergeFrom(other: GroupMessageData, incomingSource: MessageSource = MessageSource.SocketEvent): GroupMessageData {
    val result = ChatReducer.merge(
        toReducerSnapshot(MessageSource.LocalCache),
        other.toReducerSnapshot(incomingSource)
    )
    val reduced = result.message
    val otherIsServer = other.backendId != null
    return copy(
        localId = reduced.localId,
        backendId = reduced.backendId,
        text = if (result.textFromIncoming) other.text.takeIf { it.isNotBlank() } ?: text else text,
        senderName = other.senderName.ifBlank { senderName },
        senderAvatar = other.senderAvatar ?: senderAvatar,
        isMe = isMe || other.isMe,
        timestamp = reduced.timestamp,
        status = reduced.status.toUiStatus(),
        errorReason = reduced.errorReason,
        retryCount = reduced.retryCount,
        messageType = if (other.messageType.isNotBlank()) other.messageType else messageType,
        media = other.media ?: media,
        pendingPayload = other.pendingPayload ?: pendingPayload,
        replyPreview = if (result.textFromIncoming) other.replyPreview ?: replyPreview else replyPreview,
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

private fun GroupMessageData.toReducerSnapshot(source: MessageSource): ChatMessageSnapshot =
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

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return f.format(Date(t1)) == f.format(Date(t2))
}

@Composable
private fun GroupDateHeader(timestamp: Long, isDarkMode: Boolean) {
    val dateString = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isDarkMode) Color.Black.copy(0.45f) else Color.White.copy(0.72f),
            border = BorderStroke(0.5.dp, if (isDarkMode) Color.White.copy(0.10f) else Color.Black.copy(0.06f))
        ) {
            Text(
                text = dateString,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = if (isDarkMode) Color.White.copy(0.6f) else Color.Black.copy(0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
