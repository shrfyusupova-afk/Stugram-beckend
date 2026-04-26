package com.stugram.app.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stugram.app.core.messaging.ChatOutboxScheduler
import com.stugram.app.core.messaging.ForwardDraft
import com.stugram.app.core.messaging.ForwardDraftStore
import com.stugram.app.core.observability.ChatReliabilityLogger
import com.stugram.app.core.observability.ChatReliabilityMetrics
import com.stugram.app.core.socket.ChatSocketManager
import com.stugram.app.core.socket.SocketConnectionEvent
import com.stugram.app.core.socket.UserPresenceState
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.local.chat.ChatRoomStore
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.DirectConversationModel
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.SendChatMessageRequest
import com.stugram.app.data.repository.ChatPendingOutbox
import com.stugram.app.data.repository.ChatRepository
import com.stugram.app.data.repository.PendingChatEnvelope
import com.stugram.app.data.repository.PendingChatPayload
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.domain.chat.ChatMediaStager
import com.stugram.app.domain.chat.ChatDomainEvent
import com.stugram.app.domain.chat.MessageSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.LinkedHashMap
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DirectChatViewModel(
    application: Application,
    private val conversationId: String,
    private val userName: String,
    private val isRequest: Boolean
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val tokenManager = TokenManager(context)
    private val store = ChatRoomStore.get(context)
    private val chatRepository: ChatRepository
    private val profileRepository: ProfileRepository
    private val socketManager: ChatSocketManager

    private val composer = MutableStateFlow(ChatPendingOutbox.loadDraft(context, draftKey()))
    private val connectionState = MutableStateFlow(ChatConnectionState.Unknown)
    private val typingState = MutableStateFlow<ChatTypingState>(ChatTypingState.Idle)
    private val isInitialLoading = MutableStateFlow(false)
    private val isLoadingMore = MutableStateFlow(false)
    private val hasMore = MutableStateFlow(true)
    private val visibleWindowSize = MutableStateFlow(120)
    private val page = MutableStateFlow(1)
    private val totalPages = MutableStateFlow(1)
    private val errorState = MutableStateFlow<ChatError?>(null)
    private val remoteProfile = MutableStateFlow<ProfileModel?>(null)
    private val conversationDetail = MutableStateFlow<DirectConversationModel?>(null)
    private val remotePresence = MutableStateFlow<UserPresenceState?>(null)
    private val isMuted = MutableStateFlow(false)
    private val searchResults = MutableStateFlow<List<MessageData>>(emptyList())
    private val searchLoading = MutableStateFlow(false)
    private val searchError = MutableStateFlow<String?>(null)
    private var typingStopJob: Job? = null
    private var lastTypingSentAt = 0L
    private var initialized = false
    private val sendingLocalIds = mutableSetOf<String>()
    private val recentSendFingerprints = LinkedHashMap<String, Long>()
    private var recoveryJob: Job? = null
    private var realtimeRecoveryJob: Job? = null
    private var lastRecoveryRefreshAt = 0L

    private val currentUserFlow = tokenManager.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )
    private val forwardDraftFlow = ForwardDraftStore.draft.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val uiState: StateFlow<DirectChatUiState>

    init {
        RetrofitClient.init(context)
        chatRepository = ChatRepository()
        profileRepository = ProfileRepository()
        socketManager = ChatSocketManager.getInstance(tokenManager)
        socketManager.connect()
        if (!isRequest && conversationId.isNotBlank()) {
            socketManager.joinConversation(conversationId)
        }
        bindSocketCollectors()
        startRealtimeRecoveryWatchdog()
        val messageWindowFlow = visibleWindowSize.flatMapLatest { limit ->
            store.observeMessages("direct", conversationId, limit)
        }
        val roomState = combine(
            messageWindowFlow,
            ChatPendingOutbox.observeDirect(context, conversationId),
            currentUserFlow
        ) { messages, pending, currentUser ->
            DirectRoomBundle(messages, pending, currentUser)
        }
        val transientPartA = combine(
            composer,
            connectionState,
            typingState,
            isInitialLoading
        ) { composerText, connection, typing, initialLoading ->
            DirectTransientPartA(composerText, connection, typing, initialLoading)
        }
        val transientPartB1 = combine(
            isLoadingMore,
            hasMore,
            errorState,
            remoteProfile,
            conversationDetail
        ) { loadingMore, more, error, profile, detail ->
            DirectTransientPartB1(loadingMore, more, error, profile, detail)
        }
        val transientPartB2a = combine(
            remotePresence,
            isMuted,
            searchResults
        ) { presence, muted, search ->
            DirectTransientPartB2a(presence, muted, search)
        }
        val transientPartB2b = combine(
            searchLoading,
            searchError,
            forwardDraftFlow
        ) { searchBusy, searchFailure, forwardDraft ->
            DirectTransientPartB2b(searchBusy, searchFailure, forwardDraft)
        }
        val transientPartB2 = combine(transientPartB2a, transientPartB2b) { a, b ->
            DirectTransientPartB2(
                remotePresence = a.remotePresence,
                isMuted = a.isMuted,
                searchResults = a.searchResults,
                searchLoading = b.searchLoading,
                searchError = b.searchError,
                forwardDraft = b.forwardDraft
            )
        }
        val transientState = combine(transientPartA, transientPartB1, transientPartB2) { a, b1, b2 ->
            DirectTransientBundle(
                composerText = a.composerText,
                connectionState = a.connectionState,
                typingState = a.typingState,
                isInitialLoading = a.isInitialLoading,
                isLoadingMore = b1.isLoadingMore,
                hasMore = b1.hasMore,
                error = b1.error,
                remoteProfile = b1.remoteProfile,
                conversationDetail = b1.conversationDetail,
                remotePresence = b2.remotePresence,
                isMuted = b2.isMuted,
                searchResults = b2.searchResults,
                searchLoading = b2.searchLoading,
                searchError = b2.searchError,
                forwardDraft = b2.forwardDraft
            )
        }
        uiState = combine(roomState, transientState) { room, transient ->
            val confirmed = room.messages.mapNotNull { it.toDirectUiMessageVm(room.currentUser?.id) }
            val pendingUi = room.pending.map { it.toDirectPendingUiMessageVm(room.currentUser) }
            DirectChatUiState(
                targetId = conversationId,
                messages = mergeDirectVmMessages(confirmed, pendingUi),
                isInitialLoading = transient.isInitialLoading,
                isLoadingMore = transient.isLoadingMore,
                hasMore = transient.hasMore,
                error = transient.error,
                connectionState = transient.connectionState,
                pendingCount = room.pending.count { it.terminalError.isNullOrBlank() },
                composerText = transient.composerText,
                typingState = transient.typingState,
                canSend = !isRequest,
                sendDisabledReason = if (isRequest) "Chat is not active yet." else null,
                currentUser = room.currentUser,
                remoteProfile = transient.remoteProfile,
                remotePresence = transient.remotePresence,
                conversationDetail = transient.conversationDetail,
                isMuted = transient.isMuted,
                searchResults = transient.searchResults,
                searchLoading = transient.searchLoading,
                searchError = transient.searchError,
                forwardDraft = transient.forwardDraft
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DirectChatUiState(targetId = conversationId, canSend = !isRequest)
        )
        onAction(DirectChatAction.InitialLoad)
    }

    fun onAction(action: DirectChatAction) {
        when (action) {
            DirectChatAction.InitialLoad -> {
                if (initialized) return
                initialized = true
                viewModelScope.launch {
                    refreshMetadata()
                    reconcilePendingMessages()
                    syncReplayEvents()
                    loadMessages(1, append = false)
                    flushPendingMessages()
                }
            }
            DirectChatAction.LoadMore -> {
                viewModelScope.launch {
                    if (isLoadingMore.value || isInitialLoading.value) return@launch
                    visibleWindowSize.value += 60
                    val loadedCount = store.countMessages("direct", conversationId)
                    val shouldFetchOlderPage = loadedCount < visibleWindowSize.value && page.value < totalPages.value
                    if (shouldFetchOlderPage) {
                        loadMessages(page.value + 1, append = true)
                    } else {
                        hasMore.value = loadedCount > visibleWindowSize.value || page.value < totalPages.value
                    }
                }
            }
            is DirectChatAction.ComposerChanged -> {
                composer.value = action.value
                ChatPendingOutbox.saveDraft(context, draftKey(), action.value)
                handleTyping(action.value)
            }
            is DirectChatAction.SendText -> {
                val text = action.text.trim()
                if (text.isBlank() || isRequest) return
                if (!acceptOutgoingFingerprint("text:${action.replyToId.orEmpty()}:$text")) return
                composer.value = ""
                ChatPendingOutbox.clearDraft(context, draftKey())
                viewModelScope.launch {
                    val localId = UUID.randomUUID().toString()
                    ChatReliabilityLogger.info("chat_send_enqueued", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to localId, "clientId" to localId))
                    val payload = PendingChatPayload(
                        text = text,
                        replyToMessageId = action.replyToId
                    )
                    store.upsertPending("direct", conversationId, localId, payload)
                    sendEnvelope(store.loadPending("direct", conversationId).first { it.localId == localId })
                }
            }
            is DirectChatAction.SendMedia -> {
                if (isRequest) return
                if (!acceptOutgoingFingerprint("media:${action.replyToId.orEmpty()}:${action.uri}:${action.mimeType}")) return
                viewModelScope.launch {
                    val localId = UUID.randomUUID().toString()
                    val staged = runCatching {
                        ChatMediaStager.copyToPrivateStorage(
                            context = context,
                            source = action.uri,
                            clientId = localId,
                            providedMimeType = action.mimeType
                        )
                    }.getOrElse {
                        errorState.value = ChatError("Could not prepare media for sending.", "MEDIA_STAGE_FAILED")
                        return@launch
                    }
                    val payload = PendingChatPayload(
                        replyToMessageId = action.replyToId,
                        mediaUri = action.uri.toString(),
                        mediaLocalPath = staged.localPath,
                        mimeType = staged.mimeType,
                        fileSize = staged.fileSize,
                        originalDisplayName = staged.displayName
                    )
                    ChatReliabilityLogger.info("chat_send_enqueued", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to localId, "clientId" to localId))
                    store.upsertPending("direct", conversationId, localId, payload)
                    sendEnvelope(store.loadPending("direct", conversationId).first { it.localId == localId })
                }
            }
            is DirectChatAction.Retry -> {
                viewModelScope.launch {
                    store.loadPending("direct", conversationId)
                        .firstOrNull { it.localId == action.localId }
                        ?.let { sendEnvelope(it) }
                }
            }
            DirectChatAction.MarkVisibleMessagesSeen -> {
                viewModelScope.launch {
                    uiState.value.messages.filter { !it.isMe && it.status != MessageStatus.SEEN }
                        .mapNotNull { it.backendId }
                        .distinct()
                        .forEach { backendId ->
                            runCatching { chatRepository.markMessageSeen(backendId) }
                        }
                }
            }
            DirectChatAction.Reconnect -> {
                scheduleRecoveryRefresh(reason = "manual_reconnect", delayMs = 0L, force = true)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchError.value = null
        if (query.isBlank()) {
            searchResults.value = emptyList()
            searchLoading.value = false
        }
    }

    fun searchMessages(query: String) {
        viewModelScope.launch {
            val trimmed = query.trim()
            if (trimmed.isBlank()) {
                searchResults.value = emptyList()
                searchLoading.value = false
                searchError.value = null
                return@launch
            }
            searchLoading.value = true
            searchError.value = null
            val res = runCatching { chatRepository.searchConversationMessages(conversationId, trimmed, 1, 30) }.getOrNull()
            if (res?.isSuccessful == true) {
                searchResults.value = res.body()?.data.orEmpty().map { api ->
                    MessageData(
                        id = api.id.hashCode(),
                        backendId = api.id,
                        isMe = api.sender?.id == currentUserFlow.value?.id,
                        senderId = api.sender?.id,
                        text = api.text ?: "",
                        timestamp = runCatching { api.createdAt?.let { java.time.Instant.parse(it).toEpochMilli() } }.getOrNull()
                            ?: System.currentTimeMillis(),
                        senderName = api.sender?.username,
                        messageType = api.messageType,
                        media = api.media?.let { m -> ChatMediaUi(url = m.url, type = api.messageType) },
                        replyPreview = api.replyToMessage?.text,
                        status = if (api.seenBy.any { it == currentUserFlow.value?.id }) MessageStatus.SEEN else MessageStatus.SENT,
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
            } else {
                searchResults.value = emptyList()
                searchError.value = res?.message()?.ifBlank { "Search failed" } ?: "Search failed"
            }
            searchLoading.value = false
        }
    }

    fun updateReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            runCatching { chatRepository.updateReaction(messageId, emoji) }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                ?.data
                ?.let { upsertRemoteMessages(listOf(it), MessageSource.HttpResponse) }
        }
    }

    fun removeReaction(messageId: String) {
        viewModelScope.launch {
            runCatching { chatRepository.removeReaction(messageId) }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                ?.data
                ?.let { upsertRemoteMessages(listOf(it), MessageSource.HttpResponse) }
        }
    }

    fun editMessage(messageId: String, text: String, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val response = runCatching { chatRepository.editMessage(messageId, text) }.getOrNull()
            val updated = response?.body()?.data
            if (response?.isSuccessful == true && updated != null) {
                upsertRemoteMessages(listOf(updated), MessageSource.HttpResponse)
                onComplete(true, null)
            } else {
                onComplete(false, response.chatApiErrorMessage("Could not edit message"))
            }
        }
    }

    fun forwardMessage(sourceMessageId: String, comment: String?, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val response = runCatching {
                chatRepository.forwardMessage(conversationId = conversationId, sourceMessageId = sourceMessageId, comment = comment)
            }.getOrNull()
            val sent = response?.body()?.data
            if (response?.isSuccessful == true && sent != null) {
                ForwardDraftStore.clear()
                upsertRemoteMessages(listOf(sent), MessageSource.HttpResponse)
                onComplete(true, null)
            } else {
                onComplete(false, response?.message().orEmpty().ifBlank { "Could not forward message" })
            }
        }
    }

    fun deleteMessage(messageId: String, deleteForEveryone: Boolean, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val scope = if (deleteForEveryone) "everyone" else "self"
            val response = runCatching { chatRepository.deleteMessage(messageId, scope) }.getOrNull()
            if (response?.isSuccessful == true) {
                val result = response.body()?.data
                if (result?.deletedForEveryone == true) {
                    result.message?.let { upsertRemoteMessages(listOf(it), MessageSource.HttpResponse) }
                } else {
                    store.applyDelete("direct", conversationId, messageId)
                }
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    fun togglePin(messageId: String) {
        viewModelScope.launch {
            val response = runCatching {
                if (conversationDetail.value?.pinnedMessage?.id == messageId) chatRepository.unpinMessage(conversationId)
                else chatRepository.pinMessage(conversationId, messageId)
            }.getOrNull()
            val updatedConversation = response?.body()?.data
            if (response?.isSuccessful == true && updatedConversation != null) {
                conversationDetail.value = updatedConversation
            }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            val response = runCatching {
                if (isMuted.value) chatRepository.unmuteConversation(conversationId)
                else chatRepository.muteConversation(conversationId, durationMinutes = 60)
            }.getOrNull()
            if (response?.isSuccessful == true) {
                isMuted.value = !isMuted.value
            }
        }
    }

    fun reportUser() {
        val userId = remoteProfile.value?.id ?: return
        viewModelScope.launch { runCatching { chatRepository.reportUser(userId, "inappropriate") } }
    }

    fun blockUser() {
        val userId = remoteProfile.value?.id ?: return
        viewModelScope.launch { runCatching { chatRepository.blockUser(userId) } }
    }

    fun applyServerMessage(message: ChatMessageModel, source: MessageSource = MessageSource.HttpResponse) {
        viewModelScope.launch {
            upsertRemoteMessages(listOf(message), source)
        }
    }

    fun removeMessage(messageId: String) {
        viewModelScope.launch {
            store.applyDelete("direct", conversationId, messageId)
        }
    }

    private suspend fun upsertRemoteMessages(messages: List<ChatMessageModel>, source: MessageSource) {
        if (conversationId.isBlank() || messages.isEmpty()) return
        messages.forEach { message ->
            store.applyRemoteMessage("direct", conversationId, message, source)
        }
    }

    private fun bindSocketCollectors() {
        viewModelScope.launch {
            socketManager.newMessages.collect { message ->
                if (message.conversation == conversationId) {
                    upsertRemoteMessages(listOf(message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.messageSeenEvents.collect { event ->
                if (event.conversationId == conversationId) {
                    upsertRemoteMessages(listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.messageReactionEvents.collect { event ->
                if (event.conversationId == conversationId) {
                    upsertRemoteMessages(listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.messageEditedEvents.collect { event ->
                if (event.conversationId == conversationId) {
                    upsertRemoteMessages(listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.messageForwardedEvents.collect { event ->
                if (event.conversationId == conversationId) {
                    upsertRemoteMessages(listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.messageDeletedEvents.collect { event ->
                if (event.conversationId == conversationId) {
                    store.applyDelete("direct", conversationId, event.messageId)
                }
            }
        }
        viewModelScope.launch {
            socketManager.messageDeletedForEveryoneEvents.collect { event ->
                if (event.conversationId == conversationId) {
                    upsertRemoteMessages(listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.messageDeliveredEvents.collect { event ->
                if (event.conversationId == conversationId) {
                    val messageId = event.messageId ?: return@collect
                    store.applyDelivered(
                        scope = "direct",
                        targetId = conversationId,
                        messageId = messageId,
                        deliveredAt = event.deliveredAt.toEpochMillisOrNowVm()
                    )
                }
            }
        }
        viewModelScope.launch {
            socketManager.typingEvents.collect { event ->
                if (event.conversationId == conversationId) {
                    typingState.value = if (event.isTyping) ChatTypingState.Direct(event.username) else ChatTypingState.Idle
                }
            }
        }
        viewModelScope.launch {
            socketManager.connectionEvents.collect { event ->
                connectionState.value = when (event) {
                    SocketConnectionEvent.Connected -> ChatConnectionState.Connected
                    SocketConnectionEvent.Reconnected -> ChatConnectionState.Reconnected
                    SocketConnectionEvent.Disconnected -> ChatConnectionState.Disconnected
                }
                when (event) {
                    SocketConnectionEvent.Connected,
                    SocketConnectionEvent.Reconnected -> scheduleRecoveryRefresh(
                        reason = if (event == SocketConnectionEvent.Reconnected) "socket_reconnected" else "socket_connected",
                        delayMs = 150L
                    )
                    SocketConnectionEvent.Disconnected -> {
                        socketManager.ensureConnected()
                        scheduleRecoveryRefresh(reason = "socket_disconnected", delayMs = 1_500L)
                    }
                }
            }
        }
        viewModelScope.launch {
            socketManager.presenceState.collect { presenceMap ->
                val remoteUserId = remoteProfile.value?.id ?: conversationDetail.value?.otherParticipant?.id
                remotePresence.value = remoteUserId?.let { presenceMap[it] }
            }
        }
    }

    private suspend fun loadMessages(targetPage: Int, append: Boolean) {
        if (conversationId.isBlank() || isRequest) return
        ChatReliabilityLogger.info("chat_load_more_started", mapOf("targetType" to "direct", "targetId" to conversationId, "targetPage" to targetPage, "append" to append))
        if (append) isLoadingMore.value = true else isInitialLoading.value = true
        errorState.value = null
        val response = runCatching { chatRepository.getConversationMessages(conversationId, targetPage, 30) }.getOrNull()
        if (response?.isSuccessful == true) {
            val body = response.body()
            val data = body?.data.orEmpty()
            upsertRemoteMessages(data, MessageSource.PaginationLoad)
            page.value = body?.meta?.page ?: targetPage
            totalPages.value = body?.meta?.totalPages ?: 1
            val totalLoaded = store.countMessages("direct", conversationId)
            hasMore.value = totalLoaded > visibleWindowSize.value || page.value < totalPages.value
            errorState.value = null
            ChatReliabilityLogger.info("chat_load_more_succeeded", mapOf("targetType" to "direct", "targetId" to conversationId, "targetPage" to page.value, "loadedCount" to data.size, "hasMore" to hasMore.value))
        } else {
            errorState.value = ChatError(response.chatApiErrorMessage("Could not load conversation"))
            ChatReliabilityLogger.warn("chat_load_more_failed", mapOf("targetType" to "direct", "targetId" to conversationId, "targetPage" to targetPage, "httpStatus" to response?.code(), "errorCode" to errorState.value?.code))
        }
        isInitialLoading.value = false
        isLoadingMore.value = false
    }

    private suspend fun syncReplayEvents() {
        if (conversationId.isBlank() || isRequest) return
        val after = store.currentCursor("direct", conversationId)
        ChatReliabilityLogger.info("chat_replay_sync_started", mapOf("targetType" to "direct", "targetId" to conversationId, "fromCursor" to after))
        ChatReliabilityMetrics.increment("chat_replay_sync_request_total", mapOf("targetType" to "direct", "source" to "android"))
        runCatching { chatRepository.getDirectEvents(conversationId, after, 100) }
            .onSuccess { events ->
                events.forEach { event ->
                    store.applyEvent("direct", conversationId, event)
                }
                val toCursor = events.lastOrNull()?.sequenceForUi() ?: after
                ChatReliabilityLogger.info("chat_replay_sync_succeeded", mapOf("targetType" to "direct", "targetId" to conversationId, "fromCursor" to after, "toCursor" to toCursor, "eventCount" to events.size))
                ChatReliabilityMetrics.increment("chat_replay_sync_event_count", mapOf("targetType" to "direct", "source" to "android"), events.size.toLong())
            }
            .onFailure { error ->
                ChatReliabilityLogger.warn("chat_replay_sync_failed", mapOf("targetType" to "direct", "targetId" to conversationId, "fromCursor" to after, "errorCode" to (error.message ?: error::class.java.simpleName)))
                ChatReliabilityMetrics.increment("chat_replay_sync_failure_total", mapOf("targetType" to "direct"))
            }
    }

    private suspend fun refreshMetadata() {
        if (userName.isNotBlank()) {
            remoteProfile.value = runCatching { profileRepository.getProfile(userName).body()?.data }.getOrNull()
        }
        conversationDetail.value = runCatching { chatRepository.getConversationDetail(conversationId).body()?.data }.getOrNull()
        val remoteUserId = remoteProfile.value?.id ?: conversationDetail.value?.otherParticipant?.id
        remotePresence.value = remoteUserId?.let { socketManager.presenceState.value[it] }
    }

    private suspend fun reconcilePendingMessages() {
        store.loadPending("direct", conversationId).forEach { pending ->
            if (store.hasConfirmedMessage("direct", conversationId, pending.localId)) {
                store.removePending(pending.localId)
                ChatReliabilityLogger.info("chat_send_duplicate_resolved", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to pending.localId, "clientId" to pending.localId, "source" to "reconcile"))
                ChatReliabilityLogger.info("chat_pending_removed", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to pending.localId, "reason" to "already_confirmed"))
            }
        }
    }

    private suspend fun flushPendingMessages() {
        store.loadPending("direct", conversationId).forEach { sendEnvelope(it) }
    }

    private fun startRealtimeRecoveryWatchdog() {
        if (isRequest || conversationId.isBlank()) return
        if (realtimeRecoveryJob?.isActive == true) return
        realtimeRecoveryJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                if (!socketManager.isConnected()) {
                    connectionState.value = ChatConnectionState.Disconnected
                    ChatReliabilityLogger.warn(
                        "chat_realtime_watchdog_disconnected",
                        mapOf("targetType" to "direct", "targetId" to conversationId)
                    )
                    socketManager.ensureConnected()
                    socketManager.joinConversation(conversationId)
                    scheduleRecoveryRefresh(reason = "watchdog_disconnected", delayMs = 1_500L)
                } else if (store.loadPending("direct", conversationId).isNotEmpty()) {
                    scheduleRecoveryRefresh(reason = "watchdog_pending", delayMs = 0L)
                }
            }
        }
    }

    private fun scheduleRecoveryRefresh(reason: String, delayMs: Long, force: Boolean = false) {
        if (isRequest || conversationId.isBlank()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRecoveryRefreshAt < 4_000L) return
        if (recoveryJob?.isActive == true) return
        lastRecoveryRefreshAt = now
        recoveryJob = viewModelScope.launch {
            delay(delayMs)
            ChatReliabilityLogger.info(
                "chat_realtime_recovery_refresh_started",
                mapOf("targetType" to "direct", "targetId" to conversationId, "reason" to reason)
            )
            socketManager.ensureConnected()
            socketManager.joinConversation(conversationId)
            refreshMetadata()
            syncReplayEvents()
            reconcilePendingMessages()
            flushPendingMessages()
            if (reason != "watchdog_pending" || uiState.value.messages.isEmpty()) {
                loadMessages(1, append = false)
            }
            ChatReliabilityLogger.info(
                "chat_realtime_recovery_refresh_finished",
                mapOf("targetType" to "direct", "targetId" to conversationId, "reason" to reason)
            )
        }
    }

    private suspend fun sendEnvelope(envelope: PendingChatEnvelope) {
        if (!sendingLocalIds.add(envelope.localId)) {
            ChatReliabilityLogger.info("chat_send_duplicate_resolved", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to envelope.localId, "clientId" to envelope.localId, "source" to "inflight_guard"))
            return
        }
        try {
        if (store.hasConfirmedMessage("direct", conversationId, envelope.localId)) {
            store.removePending(envelope.localId)
            ChatReliabilityLogger.info("chat_send_duplicate_resolved", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to envelope.localId, "clientId" to envelope.localId, "source" to "viewmodel"))
            ChatReliabilityLogger.info("chat_pending_removed", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to envelope.localId, "reason" to "already_confirmed"))
            return
        }
        store.markPendingSending(envelope.localId)
        ChatReliabilityLogger.info("chat_send_started", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to envelope.localId, "clientId" to envelope.localId, "retryCount" to envelope.retryCount))
        val payload = envelope.payload
        var sendFailure: Throwable? = null
        val response = when {
            payload.mediaLocalPath != null && payload.mimeType != null -> {
                val stagedFile = File(payload.mediaLocalPath)
                if (!stagedFile.exists()) {
                    store.markTerminalFailureAndRemove(
                        envelope.localId,
                        "MEDIA_FILE_MISSING",
                        "The selected media file is no longer available."
                    )
                    ChatMediaStager.cleanup(context, envelope.localId)
                    ChatReliabilityLogger.warn("chat_media_file_missing", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to envelope.localId, "clientId" to envelope.localId, "errorCode" to "MEDIA_FILE_MISSING"))
                    return
                }
                runCatching {
                    chatRepository.sendStagedMediaMessage(
                        conversationId = conversationId,
                        file = stagedFile,
                        mimeType = payload.mimeType,
                        replyToMessageId = payload.replyToMessageId,
                        messageTypeOverride = payload.messageTypeOverride,
                        clientId = envelope.localId,
                        displayName = payload.originalDisplayName
                    )
                }.onFailure { sendFailure = it }.getOrNull()
            }
            payload.text != null -> {
                runCatching {
                    chatRepository.sendMessage(
                        conversationId = conversationId,
                        request = SendChatMessageRequest(
                            text = payload.text,
                            replyToMessageId = payload.replyToMessageId,
                            clientId = envelope.localId
                        )
                    )
                }.onFailure { sendFailure = it }.getOrNull()
            }
            else -> null
        }

        val sent = response?.body()?.data
        if (response?.isSuccessful == true && sent != null) {
            store.confirmPendingSuccess("direct", conversationId, envelope.localId, sent)
            ChatMediaStager.cleanup(context, envelope.localId)
            errorState.value = null
            ChatReliabilityLogger.info("chat_send_success", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to envelope.localId, "clientId" to envelope.localId, "backendMessageId" to sent.id))
        } else {
            val errorMessage = when {
                response == null -> sendFailure.chatSendErrorMessageVm()
                response.code() == 401 -> "Session expired. Please sign in again."
                else -> response.chatApiErrorMessage("Could not send message")
            }
            val status = if (response.shouldQueueForRetryVm()) MessageStatus.QUEUED else MessageStatus.FAILED
            if (response?.code() == 429) {
                ChatReliabilityLogger.warn("chat_rate_limited", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to 429, "errorCode" to "RATE_LIMITED"))
            }
            if (response?.code() == 401) {
                ChatReliabilityLogger.warn("chat_auth_refresh_failed", mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to 401, "errorCode" to "AUTH_FAILED"))
            }
            ChatReliabilityLogger.warn(
                if (status == MessageStatus.FAILED) "chat_send_failed_terminal" else "chat_send_failed_retryable",
                mapOf("targetType" to "direct", "targetId" to conversationId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to response?.code(), "errorCode" to sendFailure.chatSendErrorCategoryVm())
            )
            if (status == MessageStatus.FAILED) {
                store.markTerminalFailureAndRemove(envelope.localId, "TERMINAL_FAILURE", errorMessage)
                ChatMediaStager.cleanup(context, envelope.localId)
            } else {
                store.markPendingRetry(envelope.localId)
                ChatOutboxScheduler.schedule(context)
            }
        }
        } finally {
            sendingLocalIds.remove(envelope.localId)
        }
    }

    private fun handleTyping(text: String) {
        if (isRequest || conversationId.isBlank()) return
        if (text.isBlank()) {
            socketManager.sendTypingStop(conversationId = conversationId)
            lastTypingSentAt = 0L
            typingStopJob?.cancel()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt > 3000) {
            socketManager.sendTypingStart(conversationId = conversationId)
            lastTypingSentAt = now
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(3500)
            if (composer.value.isNotBlank()) {
                socketManager.sendTypingStop(conversationId = conversationId)
                lastTypingSentAt = 0L
            }
        }
    }

    override fun onCleared() {
        if (!isRequest && conversationId.isNotBlank()) {
            socketManager.sendTypingStop(conversationId = conversationId)
        }
        super.onCleared()
    }

    private fun acceptOutgoingFingerprint(fingerprint: String, windowMs: Long = 1_500L): Boolean {
        val now = System.currentTimeMillis()
        val iterator = recentSendFingerprints.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > windowMs) {
                iterator.remove()
            }
        }
        val previous = recentSendFingerprints[fingerprint]
        if (previous != null && now - previous <= windowMs) {
            ChatReliabilityLogger.info(
                "chat_send_duplicate_resolved",
                mapOf(
                    "targetType" to "direct",
                    "targetId" to conversationId,
                    "source" to "fingerprint_guard"
                )
            )
            return false
        }
        recentSendFingerprints[fingerprint] = now
        return true
    }

    private fun draftKey(): String = "direct:$conversationId"

    companion object {
        fun factory(
            application: Application,
            conversationId: String,
            isRequest: Boolean
        ): ViewModelProvider.Factory = factory(application, conversationId, "", isRequest)

        fun factory(
            application: Application,
            conversationId: String,
            userName: String,
            isRequest: Boolean
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DirectChatViewModel(application, conversationId, userName, isRequest) as T
        }
    }
}

private fun String?.toEpochMillisOrNowVm(): Long {
    if (isNullOrBlank()) return System.currentTimeMillis()
    return runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
}

private data class DirectRoomBundle(
    val messages: List<com.stugram.app.data.local.chat.MessageEntity>,
    val pending: List<com.stugram.app.data.repository.PendingChatEnvelope>,
    val currentUser: com.stugram.app.data.remote.model.ProfileModel?
)

private data class DirectTransientBundle(
    val composerText: String,
    val connectionState: ChatConnectionState,
    val typingState: ChatTypingState,
    val isInitialLoading: Boolean,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val error: ChatError?,
    val remoteProfile: ProfileModel?,
    val conversationDetail: DirectConversationModel?,
    val remotePresence: UserPresenceState?,
    val isMuted: Boolean,
    val searchResults: List<MessageData>,
    val searchLoading: Boolean,
    val searchError: String?,
    val forwardDraft: ForwardDraft?
)

private data class DirectTransientPartA(
    val composerText: String,
    val connectionState: ChatConnectionState,
    val typingState: ChatTypingState,
    val isInitialLoading: Boolean
)

private data class DirectTransientPartB1(
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val error: ChatError?,
    val remoteProfile: ProfileModel?,
    val conversationDetail: DirectConversationModel?
)

private data class DirectTransientPartB2(
    val remotePresence: UserPresenceState?,
    val isMuted: Boolean,
    val searchResults: List<MessageData>,
    val searchLoading: Boolean,
    val searchError: String?,
    val forwardDraft: ForwardDraft?
)

private data class DirectTransientPartB2a(
    val remotePresence: UserPresenceState?,
    val isMuted: Boolean,
    val searchResults: List<MessageData>
)

private data class DirectTransientPartB2b(
    val searchLoading: Boolean,
    val searchError: String?,
    val forwardDraft: ForwardDraft?
)

private fun ChatDomainEvent.sequenceForUi(): Long? = when (this) {
    is ChatDomainEvent.ServerMessageCreated -> serverSequence ?: message.serverSequence
    is ChatDomainEvent.ServerMessageEdited -> serverSequence
    is ChatDomainEvent.ServerMessageDeleted -> deleteSequence
    is ChatDomainEvent.ServerReactionSnapshot -> serverSequence
    is ChatDomainEvent.ServerSeenUpdated -> serverSequence
    else -> null
}
