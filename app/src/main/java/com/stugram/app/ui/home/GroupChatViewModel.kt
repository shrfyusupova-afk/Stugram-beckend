package com.stugram.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stugram.app.core.messaging.ChatOutboxScheduler
import com.stugram.app.core.observability.ChatReliabilityLogger
import com.stugram.app.core.observability.ChatReliabilityMetrics
import com.stugram.app.core.socket.ChatSocketManager
import com.stugram.app.core.socket.SocketConnectionEvent
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.local.chat.ChatRoomStore
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.SendChatMessageRequest
import com.stugram.app.data.repository.ChatLocalCache
import com.stugram.app.data.repository.ChatPendingOutbox
import com.stugram.app.data.repository.GroupChatRepository
import com.stugram.app.data.repository.PendingChatEnvelope
import com.stugram.app.data.repository.PendingChatPayload
import com.stugram.app.domain.chat.ChatDomainEvent
import com.stugram.app.domain.chat.ChatMediaStager
import com.stugram.app.domain.chat.MessageSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GroupChatViewModel(
    application: Application,
    private val groupId: String,
    private val initialGroupTitle: String
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val tokenManager = TokenManager(context)
    private val store = ChatRoomStore.get(context)
    private val groupRepository: GroupChatRepository
    private val socketManager: ChatSocketManager

    private val composer = MutableStateFlow(ChatPendingOutbox.loadDraft(context, draftKey()))
    private val connectionState = MutableStateFlow(ChatConnectionState.Unknown)
    private val typingUsers = MutableStateFlow<List<String>>(emptyList())
    private val isInitialLoading = MutableStateFlow(false)
    private val isLoadingMore = MutableStateFlow(false)
    private val hasMore = MutableStateFlow(true)
    private val visibleWindowSize = MutableStateFlow(120)
    private val page = MutableStateFlow(1)
    private val totalPages = MutableStateFlow(1)
    private val errorState = MutableStateFlow<ChatError?>(null)
    private val groupTitle = MutableStateFlow(initialGroupTitle)
    private val memberCount = MutableStateFlow(0)
    private var typingStopJob: Job? = null
    private var lastTypingSentAt = 0L
    private var initialized = false
    private val sendingLocalIds = mutableSetOf<String>()

    private val currentUserFlow = tokenManager.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val uiState: StateFlow<GroupChatUiState>

    init {
        RetrofitClient.init(context)
        groupRepository = GroupChatRepository()
        socketManager = ChatSocketManager.getInstance(tokenManager)
        socketManager.connect()
        if (groupId.isNotBlank()) {
            socketManager.joinGroup(groupId)
        }
        bindSocketCollectors()
        val messageWindowFlow = visibleWindowSize.flatMapLatest { limit ->
            store.observeMessages("group", groupId, limit)
        }
        val roomState = combine(
            messageWindowFlow,
            ChatPendingOutbox.observeGroup(context, groupId),
            currentUserFlow
        ) { messages, pending, currentUser ->
            GroupRoomBundle(messages, pending, currentUser)
        }
        val transientPartA = combine(
            composer,
            connectionState,
            typingUsers,
            isInitialLoading
        ) { composerText, connection, typing, initialLoading ->
            GroupTransientPartA(composerText, connection, typing, initialLoading)
        }
        val transientPartB = combine(
            isLoadingMore,
            hasMore,
            errorState,
            groupTitle,
            memberCount
        ) { loadingMore, more, error, title, members ->
            GroupTransientPartB(loadingMore, more, error, title, members)
        }
        val transientState = combine(transientPartA, transientPartB) { a, b ->
            GroupTransientBundle(a.composerText, a.connectionState, a.typingUsers, a.isInitialLoading, b.isLoadingMore, b.hasMore, b.error, b.groupTitle, b.memberCount)
        }
        uiState = combine(roomState, transientState) { room, transient ->
            val confirmed = room.messages.mapNotNull { it.toGroupUiMessageVm(room.currentUser?.id) }
            val senderName = room.currentUser?.fullName ?: room.currentUser?.username ?: transient.groupTitle
            val pendingUi = room.pending.map { it.toGroupPendingUiMessageVm(senderName, room.currentUser?.avatar) }
            GroupChatUiState(
                groupId = groupId,
                messages = mergeGroupVmMessages(confirmed, pendingUi),
                isInitialLoading = transient.isInitialLoading,
                isLoadingMore = transient.isLoadingMore,
                hasMore = transient.hasMore,
                error = transient.error,
                connectionState = transient.connectionState,
                pendingCount = room.pending.count { it.terminalError.isNullOrBlank() },
                composerText = transient.composerText,
                typingUsers = transient.typingUsers,
                canSend = true,
                groupTitle = transient.groupTitle,
                memberCount = transient.memberCount
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            GroupChatUiState(groupId = groupId, groupTitle = initialGroupTitle)
        )
        onAction(GroupChatAction.InitialLoad)
    }

    fun onAction(action: GroupChatAction) {
        when (action) {
            GroupChatAction.InitialLoad -> {
                if (initialized) return
                initialized = true
                viewModelScope.launch {
                    refreshGroupMetadata()
                    reconcilePendingMessages()
                    syncReplayEvents()
                    loadMessages(1, append = false)
                    flushPendingMessages()
                }
            }
            GroupChatAction.LoadMore -> {
                viewModelScope.launch {
                    if (isLoadingMore.value || isInitialLoading.value) return@launch
                    visibleWindowSize.value += 60
                    val loadedCount = store.countMessages("group", groupId)
                    val shouldFetchOlderPage = loadedCount < visibleWindowSize.value && page.value < totalPages.value
                    if (shouldFetchOlderPage) {
                        loadMessages(page.value + 1, append = true)
                    } else {
                        hasMore.value = loadedCount > visibleWindowSize.value || page.value < totalPages.value
                    }
                }
            }
            is GroupChatAction.ComposerChanged -> {
                composer.value = action.value
                ChatPendingOutbox.saveDraft(context, draftKey(), action.value)
                handleTyping(action.value)
            }
            is GroupChatAction.SendText -> {
                if (action.text.isBlank()) return
                viewModelScope.launch {
                    val localId = UUID.randomUUID().toString()
                    ChatReliabilityLogger.info("chat_send_enqueued", mapOf("targetType" to "group", "targetId" to groupId, "localId" to localId, "clientId" to localId))
                    val payload = PendingChatPayload(
                        text = action.text,
                        replyToMessageId = action.replyToId
                    )
                    ChatPendingOutbox.upsertGroup(context, groupId, localId, payload)
                    composer.value = ""
                    ChatPendingOutbox.clearDraft(context, draftKey())
                    sendEnvelope(ChatPendingOutbox.loadGroup(context, groupId).first { it.localId == localId })
                }
            }
            is GroupChatAction.SendMedia -> {
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
                    ChatReliabilityLogger.info("chat_send_enqueued", mapOf("targetType" to "group", "targetId" to groupId, "localId" to localId, "clientId" to localId))
                    ChatPendingOutbox.upsertGroup(context, groupId, localId, payload)
                    sendEnvelope(ChatPendingOutbox.loadGroup(context, groupId).first { it.localId == localId })
                }
            }
            is GroupChatAction.Retry -> {
                viewModelScope.launch {
                    ChatPendingOutbox.loadGroup(context, groupId)
                        .firstOrNull { it.localId == action.localId }
                        ?.let { sendEnvelope(it) }
                }
            }
            GroupChatAction.MarkVisibleMessagesSeen -> {
                viewModelScope.launch {
                    uiState.value.messages.filter { !it.isMe && it.status != MessageStatus.SEEN }
                        .mapNotNull { it.backendId }
                        .distinct()
                        .forEach { backendId ->
                            runCatching { groupRepository.markGroupMessageSeen(groupId, backendId) }
                        }
                }
            }
            GroupChatAction.Reconnect -> {
                viewModelScope.launch {
                    syncReplayEvents()
                    flushPendingMessages()
                    refreshGroupMetadata()
                    loadMessages(1, append = false)
                }
            }
        }
    }

    fun applyServerMessage(message: com.stugram.app.data.remote.model.ChatMessageModel, source: MessageSource = MessageSource.HttpResponse) {
        viewModelScope.launch {
            ChatLocalCache.upsertGroup(context, groupId, listOf(message), source)
        }
    }

    fun removeMessage(messageId: String) {
        viewModelScope.launch {
            ChatLocalCache.removeGroup(context, groupId, messageId)
        }
    }

    fun refreshMetadata() {
        viewModelScope.launch { refreshGroupMetadata() }
    }

    private fun bindSocketCollectors() {
        viewModelScope.launch {
            socketManager.groupMessages.collect { (targetGroupId, message) ->
                if (targetGroupId == groupId) {
                    ChatLocalCache.upsertGroup(context, groupId, listOf(message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.groupMessageSeenEvents.collect { event ->
                if (event.groupId == groupId) {
                    ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.groupMessageReactionEvents.collect { event ->
                if (event.groupId == groupId) {
                    ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.groupMessageEditedEvents.collect { event ->
                if (event.groupId == groupId) {
                    ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.groupMessageForwardedEvents.collect { event ->
                if (event.groupId == groupId) {
                    ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.groupMessageDeletedEvents.collect { event ->
                if (event.groupId == groupId) {
                    ChatLocalCache.removeGroup(context, groupId, event.messageId)
                }
            }
        }
        viewModelScope.launch {
            socketManager.groupMessageDeletedForEveryoneEvents.collect { event ->
                if (event.groupId == groupId) {
                    ChatLocalCache.upsertGroup(context, groupId, listOf(event.message), MessageSource.SocketEvent)
                }
            }
        }
        viewModelScope.launch {
            socketManager.messageDeliveredEvents.collect { event ->
                if (event.groupId == groupId) {
                    val messageId = event.messageId ?: return@collect
                    store.applyDelivered(
                        scope = "group",
                        targetId = groupId,
                        messageId = messageId,
                        deliveredAt = event.deliveredAt.toEpochMillisOrNowGroupVm()
                    )
                }
            }
        }
        viewModelScope.launch {
            socketManager.groupMemberAddedEvents.collect { event ->
                if (event.groupId == groupId) refreshGroupMetadata()
            }
        }
        viewModelScope.launch {
            socketManager.groupMemberRemovedEvents.collect { event ->
                if (event.groupId == groupId) refreshGroupMetadata()
            }
        }
        viewModelScope.launch {
            socketManager.groupConversationUpdates.collect { update ->
                if (update.id == groupId) {
                    groupTitle.value = update.name
                }
            }
        }
        viewModelScope.launch {
            socketManager.typingEvents.collect { event ->
                if (event.groupId == groupId) {
                    val current = typingUsers.value.toMutableList()
                    if (event.isTyping) {
                        if (event.username !in current) current += event.username
                    } else {
                        current.remove(event.username)
                    }
                    typingUsers.value = current
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
                if (event is SocketConnectionEvent.Reconnected) {
                    onAction(GroupChatAction.Reconnect)
                }
            }
        }
    }

    private suspend fun refreshGroupMetadata() {
        if (groupId.isBlank()) return
        val detail = runCatching { groupRepository.getGroupChatDetail(groupId).body()?.data }.getOrNull()
        val members = runCatching { groupRepository.getGroupMembers(groupId, 1, 100).body()?.data.orEmpty() }.getOrDefault(emptyList())
        detail?.let { groupTitle.value = it.name }
        memberCount.value = members.size
    }

    private suspend fun loadMessages(targetPage: Int, append: Boolean) {
        if (groupId.isBlank()) return
        ChatReliabilityLogger.info("chat_load_more_started", mapOf("targetType" to "group", "targetId" to groupId, "targetPage" to targetPage, "append" to append))
        if (append) isLoadingMore.value = true else isInitialLoading.value = true
        errorState.value = null
        val response = runCatching { groupRepository.getGroupMessages(groupId, targetPage, 30) }.getOrNull()
        if (response?.isSuccessful == true) {
            val body = response.body()
            val data = body?.data.orEmpty()
            ChatLocalCache.upsertGroup(context, groupId, data, MessageSource.PaginationLoad)
            page.value = body?.meta?.page ?: targetPage
            totalPages.value = body?.meta?.totalPages ?: 1
            val totalLoaded = store.countMessages("group", groupId)
            hasMore.value = totalLoaded > visibleWindowSize.value || page.value < totalPages.value
            errorState.value = null
            ChatReliabilityLogger.info("chat_load_more_succeeded", mapOf("targetType" to "group", "targetId" to groupId, "targetPage" to page.value, "loadedCount" to data.size, "hasMore" to hasMore.value))
        } else {
            errorState.value = ChatError(response.chatApiErrorMessage("Could not load group chat"))
            ChatReliabilityLogger.warn("chat_load_more_failed", mapOf("targetType" to "group", "targetId" to groupId, "targetPage" to targetPage, "httpStatus" to response?.code(), "errorCode" to errorState.value?.code))
        }
        isInitialLoading.value = false
        isLoadingMore.value = false
    }

    private suspend fun syncReplayEvents() {
        if (groupId.isBlank()) return
        val after = store.currentCursor("group", groupId)
        ChatReliabilityLogger.info("chat_replay_sync_started", mapOf("targetType" to "group", "targetId" to groupId, "fromCursor" to after))
        ChatReliabilityMetrics.increment("chat_replay_sync_request_total", mapOf("targetType" to "group", "source" to "android"))
        runCatching { groupRepository.getGroupEvents(groupId, after, 100) }
            .onSuccess { events ->
                events.forEach { event ->
                    store.applyEvent("group", groupId, event)
                }
                val toCursor = events.lastOrNull()?.sequenceForUi() ?: after
                ChatReliabilityLogger.info("chat_replay_sync_succeeded", mapOf("targetType" to "group", "targetId" to groupId, "fromCursor" to after, "toCursor" to toCursor, "eventCount" to events.size))
                ChatReliabilityMetrics.increment("chat_replay_sync_event_count", mapOf("targetType" to "group", "source" to "android"), events.size.toLong())
            }
            .onFailure { error ->
                ChatReliabilityLogger.warn("chat_replay_sync_failed", mapOf("targetType" to "group", "targetId" to groupId, "fromCursor" to after, "errorCode" to (error.message ?: error::class.java.simpleName)))
                ChatReliabilityMetrics.increment("chat_replay_sync_failure_total", mapOf("targetType" to "group"))
            }
    }

    private suspend fun reconcilePendingMessages() {
        ChatPendingOutbox.loadGroup(context, groupId).forEach { pending ->
            if (store.hasConfirmedMessage("group", groupId, pending.localId)) {
                ChatPendingOutbox.remove(context, pending.localId)
                ChatReliabilityLogger.info("chat_send_duplicate_resolved", mapOf("targetType" to "group", "targetId" to groupId, "localId" to pending.localId, "clientId" to pending.localId, "source" to "reconcile"))
                ChatReliabilityLogger.info("chat_pending_removed", mapOf("targetType" to "group", "targetId" to groupId, "localId" to pending.localId, "reason" to "already_confirmed"))
            }
        }
    }

    private suspend fun flushPendingMessages() {
        ChatPendingOutbox.loadGroup(context, groupId).forEach { sendEnvelope(it) }
    }

    private suspend fun sendEnvelope(envelope: PendingChatEnvelope) {
        if (!sendingLocalIds.add(envelope.localId)) {
            ChatReliabilityLogger.info("chat_send_duplicate_resolved", mapOf("targetType" to "group", "targetId" to groupId, "localId" to envelope.localId, "clientId" to envelope.localId, "source" to "inflight_guard"))
            return
        }
        try {
        if (store.hasConfirmedMessage("group", groupId, envelope.localId)) {
            ChatPendingOutbox.remove(context, envelope.localId)
            ChatReliabilityLogger.info("chat_send_duplicate_resolved", mapOf("targetType" to "group", "targetId" to groupId, "localId" to envelope.localId, "clientId" to envelope.localId, "source" to "viewmodel"))
            ChatReliabilityLogger.info("chat_pending_removed", mapOf("targetType" to "group", "targetId" to groupId, "localId" to envelope.localId, "reason" to "already_confirmed"))
            return
        }
        ChatPendingOutbox.markSending(context, envelope.localId)
        ChatReliabilityLogger.info("chat_send_started", mapOf("targetType" to "group", "targetId" to groupId, "localId" to envelope.localId, "clientId" to envelope.localId, "retryCount" to envelope.retryCount))
        val payload = envelope.payload
        var sendFailure: Throwable? = null
        val response = when {
            payload.mediaLocalPath != null && payload.mimeType != null -> {
                val stagedFile = File(payload.mediaLocalPath)
                if (!stagedFile.exists()) {
                    ChatPendingOutbox.markTerminalFailureAndRemove(
                        context,
                        envelope.localId,
                        "MEDIA_FILE_MISSING",
                        "The selected media file is no longer available."
                    )
                    ChatMediaStager.cleanup(context, envelope.localId)
                    ChatReliabilityLogger.warn("chat_media_file_missing", mapOf("targetType" to "group", "targetId" to groupId, "localId" to envelope.localId, "clientId" to envelope.localId, "errorCode" to "MEDIA_FILE_MISSING"))
                    return
                }
                runCatching {
                    groupRepository.sendStagedGroupMediaMessage(
                        groupId = groupId,
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
                    groupRepository.sendGroupMessage(
                        groupId = groupId,
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
            store.confirmPendingSuccess("group", groupId, envelope.localId, sent)
            ChatPendingOutbox.remove(context, envelope.localId)
            ChatMediaStager.cleanup(context, envelope.localId)
            errorState.value = null
            ChatReliabilityLogger.info("chat_send_success", mapOf("targetType" to "group", "targetId" to groupId, "localId" to envelope.localId, "clientId" to envelope.localId, "backendMessageId" to sent.id))
        } else {
            val errorMessage = when {
                response == null -> sendFailure.chatSendErrorMessageVm()
                response.code() == 401 -> "Session expired. Please sign in again."
                else -> response.chatApiErrorMessage("Could not send message")
            }
            val status = if (response.shouldQueueForRetryVm()) MessageStatus.QUEUED else MessageStatus.FAILED
            if (response?.code() == 429) {
                ChatReliabilityLogger.warn("chat_rate_limited", mapOf("targetType" to "group", "targetId" to groupId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to 429, "errorCode" to "RATE_LIMITED"))
            }
            if (response?.code() == 401) {
                ChatReliabilityLogger.warn("chat_auth_refresh_failed", mapOf("targetType" to "group", "targetId" to groupId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to 401, "errorCode" to "AUTH_FAILED"))
            }
            ChatReliabilityLogger.warn(
                if (status == MessageStatus.FAILED) "chat_send_failed_terminal" else "chat_send_failed_retryable",
                mapOf("targetType" to "group", "targetId" to groupId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to response?.code(), "errorCode" to sendFailure.chatSendErrorCategoryVm())
            )
            if (status == MessageStatus.FAILED) {
                ChatPendingOutbox.markTerminalFailureAndRemove(context, envelope.localId, "TERMINAL_FAILURE", errorMessage)
                ChatMediaStager.cleanup(context, envelope.localId)
            } else {
                ChatPendingOutbox.markRetry(context, envelope.localId)
                ChatOutboxScheduler.schedule(context)
            }
        }
        } finally {
            sendingLocalIds.remove(envelope.localId)
        }
    }

    private fun handleTyping(text: String) {
        if (groupId.isBlank()) return
        if (text.isBlank()) {
            socketManager.sendTypingStop(groupId = groupId)
            lastTypingSentAt = 0L
            typingStopJob?.cancel()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt > 3000) {
            socketManager.sendTypingStart(groupId = groupId)
            lastTypingSentAt = now
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(3500)
            if (composer.value.isNotBlank()) {
                socketManager.sendTypingStop(groupId = groupId)
                lastTypingSentAt = 0L
            }
        }
    }

    override fun onCleared() {
        if (groupId.isNotBlank()) {
            socketManager.sendTypingStop(groupId = groupId)
        }
        super.onCleared()
    }

    private fun draftKey(): String = "group:$groupId"

    companion object {
        fun factory(
            application: Application,
            groupId: String,
            groupTitle: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GroupChatViewModel(application, groupId, groupTitle) as T
        }
    }
}

private fun String?.toEpochMillisOrNowGroupVm(): Long {
    if (isNullOrBlank()) return System.currentTimeMillis()
    return runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
}

private data class GroupRoomBundle(
    val messages: List<com.stugram.app.data.local.chat.MessageEntity>,
    val pending: List<com.stugram.app.data.repository.PendingChatEnvelope>,
    val currentUser: com.stugram.app.data.remote.model.ProfileModel?
)

private data class GroupTransientBundle(
    val composerText: String,
    val connectionState: ChatConnectionState,
    val typingUsers: List<String>,
    val isInitialLoading: Boolean,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val error: ChatError?,
    val groupTitle: String,
    val memberCount: Int
)

private data class GroupTransientPartA(
    val composerText: String,
    val connectionState: ChatConnectionState,
    val typingUsers: List<String>,
    val isInitialLoading: Boolean
)

private data class GroupTransientPartB(
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val error: ChatError?,
    val groupTitle: String,
    val memberCount: Int
)

private fun ChatDomainEvent.sequenceForUi(): Long? = when (this) {
    is ChatDomainEvent.ServerMessageCreated -> serverSequence ?: message.serverSequence
    is ChatDomainEvent.ServerMessageEdited -> serverSequence
    is ChatDomainEvent.ServerMessageDeleted -> deleteSequence
    is ChatDomainEvent.ServerReactionSnapshot -> serverSequence
    is ChatDomainEvent.ServerSeenUpdated -> serverSequence
    else -> null
}
