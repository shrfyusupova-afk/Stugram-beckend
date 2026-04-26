package com.stugram.app.domain.chat

data class ChatMergeResult(
    val message: ChatMessageSnapshot,
    val textFromIncoming: Boolean,
    val reactionsFromIncoming: Boolean,
    val deleteFromIncoming: Boolean,
    val editFromIncoming: Boolean,
    val errorFromIncoming: Boolean
)

enum class ChatMessageStatus {
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    SEEN,
    FAILED
}

enum class MessageSource {
    LocalOptimistic,
    LocalRetry,
    LocalCache,
    HttpResponse,
    SocketEvent,
    ReconnectReconciliation,
    PaginationLoad
}

data class ChatMessageSnapshot(
    val localId: String? = null,
    val clientId: String? = null,
    val backendId: String? = null,
    val rawJson: String? = null,
    val text: String = "",
    val status: ChatMessageStatus = ChatMessageStatus.SENT,
    val source: MessageSource = MessageSource.LocalCache,
    val timestamp: Long = 0L,
    val editedAt: Long? = null,
    val deletedAt: Long? = null,
    val reactions: List<String> = emptyList(),
    val hasReactionSnapshot: Boolean = false,
    val serverSequence: Long? = null,
    val editVersion: Int = 0,
    val reactionVersion: Int = 0,
    val deliveryVersion: Int = 0,
    val deleteSequence: Long? = null,
    val errorReason: String? = null,
    val retryCount: Int = 0
) {
    val stableId: String?
        get() = clientId?.takeIf { it.isNotBlank() }
            ?: backendId?.takeIf { it.isNotBlank() }
            ?: localId?.takeIf { it.isNotBlank() }

    val isDeleted: Boolean
        get() = deleteSequence != null || deletedAt != null
}

sealed interface ChatDomainEvent {
    data class LocalQueued(val message: ChatMessageSnapshot) : ChatDomainEvent

    data class LocalSending(val clientId: String) : ChatDomainEvent

    data class LocalFailed(val clientId: String, val error: String?) : ChatDomainEvent

    data class ServerMessageCreated(
        val message: ChatMessageSnapshot,
        val serverSequence: Long?
    ) : ChatDomainEvent

    data class ServerMessageEdited(
        val messageId: String,
        val text: String,
        val editVersion: Int,
        val serverSequence: Long?
    ) : ChatDomainEvent

    data class ServerMessageDeleted(
        val messageId: String,
        val deleteSequence: Long
    ) : ChatDomainEvent

    data class ServerReactionSnapshot(
        val messageId: String,
        val reactionsJson: String,
        val reactionVersion: Int,
        val serverSequence: Long?
    ) : ChatDomainEvent

    data class ServerDeliveredUpdated(
        val messageId: String,
        val deliveredAt: Long,
        val deliveryVersion: Int,
        val serverSequence: Long? = null
    ) : ChatDomainEvent

    data class ServerSeenUpdated(
        val messageId: String,
        val seenAt: Long,
        val deliveryVersion: Int,
        val serverSequence: Long? = null
    ) : ChatDomainEvent
}

object ChatReducer {
    fun reduce(
        current: List<ChatMessageSnapshot>,
        event: ChatDomainEvent
    ): List<ChatMessageSnapshot> {
        val index = current.indexOfFirst { matchesEvent(it, event) }
        val updated = when (event) {
            is ChatDomainEvent.LocalQueued -> applyLocalQueued(current, index, event)
            is ChatDomainEvent.LocalSending -> applyLocalSending(current, index, event)
            is ChatDomainEvent.LocalFailed -> applyLocalFailed(current, index, event)
            is ChatDomainEvent.ServerMessageCreated -> applyServerMessageCreated(current, index, event)
            is ChatDomainEvent.ServerMessageEdited -> applyServerMessageEdited(current, index, event)
            is ChatDomainEvent.ServerMessageDeleted -> applyServerMessageDeleted(current, index, event)
            is ChatDomainEvent.ServerReactionSnapshot -> applyServerReactionSnapshot(current, index, event)
            is ChatDomainEvent.ServerDeliveredUpdated -> applyServerDeliveredUpdated(current, index, event)
            is ChatDomainEvent.ServerSeenUpdated -> applyServerSeenUpdated(current, index, event)
        }
        return updated.dedupe().sortedWith(messageOrdering())
    }

    fun reduce(
        current: List<ChatMessageSnapshot>,
        incoming: ChatMessageSnapshot
    ): List<ChatMessageSnapshot> = reduce(current, incoming.toDomainEvent())

    fun merge(current: ChatMessageSnapshot, incoming: ChatMessageSnapshot): ChatMergeResult {
        val mergedList = reduce(listOf(current), incoming.toDomainEvent())
        val merged = mergedList.firstOrNull { sameLogicalMessage(it, current) || sameLogicalMessage(it, incoming) } ?: current
        return ChatMergeResult(
            message = merged,
            textFromIncoming = merged.text != current.text,
            reactionsFromIncoming = merged.reactions != current.reactions,
            deleteFromIncoming = merged.deleteSequence != current.deleteSequence,
            editFromIncoming = merged.editVersion != current.editVersion || merged.editedAt != current.editedAt,
            errorFromIncoming = merged.errorReason != current.errorReason
        )
    }

    fun sameLogicalMessage(a: ChatMessageSnapshot, b: ChatMessageSnapshot): Boolean {
        val aClient = a.clientId?.takeIf { it.isNotBlank() } ?: a.localId?.takeIf { it.isNotBlank() }
        val bClient = b.clientId?.takeIf { it.isNotBlank() } ?: b.localId?.takeIf { it.isNotBlank() }
        if (aClient != null && bClient != null && aClient == bClient) return true

        val aBackend = a.backendId?.takeIf { it.isNotBlank() }
        val bBackend = b.backendId?.takeIf { it.isNotBlank() }
        if (aBackend != null && bBackend != null && aBackend == bBackend) return true

        val aLocal = a.localId?.takeIf { it.isNotBlank() }
        val bLocal = b.localId?.takeIf { it.isNotBlank() }
        return aLocal != null && bLocal != null && aLocal == bLocal
    }

    fun statusRank(status: ChatMessageStatus): Int = when (status) {
        ChatMessageStatus.QUEUED -> 0
        ChatMessageStatus.SENDING -> 1
        ChatMessageStatus.FAILED -> 1
        ChatMessageStatus.SENT -> 2
        ChatMessageStatus.DELIVERED -> 3
        ChatMessageStatus.SEEN -> 4
    }

    private fun applyLocalQueued(
        current: List<ChatMessageSnapshot>,
        index: Int,
        event: ChatDomainEvent.LocalQueued
    ): List<ChatMessageSnapshot> {
        if (index < 0) return current + event.message.copy(status = ChatMessageStatus.QUEUED, source = MessageSource.LocalOptimistic)
        val existing = current[index]
        val updated = if (statusRank(existing.status) >= statusRank(ChatMessageStatus.SENT)) {
            existing
        } else {
            existing.copy(
                localId = existing.localId ?: event.message.localId,
                clientId = existing.clientId ?: event.message.clientId,
                rawJson = existing.rawJson ?: event.message.rawJson,
                text = if (existing.text.isBlank()) event.message.text else existing.text,
                status = ChatMessageStatus.QUEUED,
                retryCount = maxOf(existing.retryCount, event.message.retryCount)
            )
        }
        return current.updated(index, updated)
    }

    private fun applyLocalSending(
        current: List<ChatMessageSnapshot>,
        index: Int,
        event: ChatDomainEvent.LocalSending
    ): List<ChatMessageSnapshot> {
        if (index < 0) return current
        val existing = current[index]
        if (statusRank(existing.status) >= statusRank(ChatMessageStatus.SENT)) return current
        return current.updated(
            index,
            existing.copy(
                status = ChatMessageStatus.SENDING,
                source = MessageSource.LocalRetry,
                errorReason = null
            )
        )
    }

    private fun applyLocalFailed(
        current: List<ChatMessageSnapshot>,
        index: Int,
        event: ChatDomainEvent.LocalFailed
    ): List<ChatMessageSnapshot> {
        if (index < 0) return current
        val existing = current[index]
        if (statusRank(existing.status) >= statusRank(ChatMessageStatus.SENT)) return current
        return current.updated(
            index,
            existing.copy(
                status = ChatMessageStatus.FAILED,
                source = MessageSource.LocalRetry,
                errorReason = event.error ?: existing.errorReason
            )
        )
    }

    private fun applyServerMessageCreated(
        current: List<ChatMessageSnapshot>,
        index: Int,
        event: ChatDomainEvent.ServerMessageCreated
    ): List<ChatMessageSnapshot> {
        val incoming = event.message.copy(
            source = event.message.source.serverAuthoritativeVariant(),
            serverSequence = event.serverSequence ?: event.message.serverSequence
        )
        if (index < 0) return current + incoming

        val existing = current[index]
        if (shouldIgnoreServerEvent(existing, incoming.serverSequence)) return current

        val updated = existing.copy(
            localId = mergeLocalId(existing, incoming),
            clientId = mergeClientId(existing, incoming),
            backendId = incoming.backendId ?: existing.backendId,
            rawJson = incoming.rawJson ?: existing.rawJson,
            text = when {
                existing.isDeleted -> existing.text
                incoming.text.isNotBlank() && shouldApplyText(existing, incoming) -> incoming.text
                existing.text.isBlank() && incoming.text.isNotBlank() -> incoming.text
                else -> existing.text
            },
            status = mergeStatus(existing, incoming),
            source = strongerSource(existing.source, incoming.source),
            timestamp = mergeTimestamp(existing, incoming),
            editedAt = if (shouldApplyEdit(existing, incoming)) incoming.editedAt ?: existing.editedAt else existing.editedAt,
            deletedAt = existing.deletedAt ?: incoming.deletedAt,
            reactions = if (shouldApplyReactions(existing, incoming)) incoming.reactions else existing.reactions,
            hasReactionSnapshot = existing.hasReactionSnapshot || incoming.hasReactionSnapshot,
            serverSequence = maxOfNullable(existing.serverSequence, incoming.serverSequence),
            editVersion = maxOf(existing.editVersion, incoming.editVersion),
            reactionVersion = maxOf(existing.reactionVersion, incoming.reactionVersion),
            deliveryVersion = maxOf(existing.deliveryVersion, incoming.deliveryVersion),
            deleteSequence = maxOfNullable(existing.deleteSequence, incoming.deleteSequence),
            errorReason = if (incoming.status != ChatMessageStatus.FAILED) null else incoming.errorReason ?: existing.errorReason,
            retryCount = maxOf(existing.retryCount, incoming.retryCount)
        )
        return current.updated(index, updated)
    }

    private fun applyServerMessageEdited(
        current: List<ChatMessageSnapshot>,
        index: Int,
        event: ChatDomainEvent.ServerMessageEdited
    ): List<ChatMessageSnapshot> {
        if (index < 0) return current
        val existing = current[index]
        if (existing.deleteSequence != null && existing.deleteSequence >= (event.serverSequence ?: Long.MIN_VALUE)) return current
        if (event.serverSequence != null && existing.serverSequence != null && event.serverSequence < existing.serverSequence) return current
        if (event.editVersion < existing.editVersion) return current

        val updated = existing.copy(
            text = if (existing.isDeleted) existing.text else event.text,
            editedAt = maxOfNullable(existing.editedAt, event.serverSequence),
            editVersion = maxOf(existing.editVersion, event.editVersion),
            serverSequence = maxOfNullable(existing.serverSequence, event.serverSequence)
        )
        return current.updated(index, updated)
    }

    private fun applyServerMessageDeleted(
        current: List<ChatMessageSnapshot>,
        index: Int,
        event: ChatDomainEvent.ServerMessageDeleted
    ): List<ChatMessageSnapshot> {
        if (index < 0) {
            return current + ChatMessageSnapshot(
                backendId = event.messageId,
                source = MessageSource.SocketEvent,
                deletedAt = event.deleteSequence,
                deleteSequence = event.deleteSequence,
                serverSequence = event.deleteSequence
            )
        }
        val existing = current[index]
        if (existing.deleteSequence != null && existing.deleteSequence >= event.deleteSequence) return current
        val updated = existing.copy(
            deletedAt = existing.deletedAt ?: event.deleteSequence,
            deleteSequence = event.deleteSequence,
            serverSequence = maxOfNullable(existing.serverSequence, event.deleteSequence),
            status = mergeStatus(existing, existing.copy(status = existing.status)),
            errorReason = null
        )
        return current.updated(index, updated)
    }

    private fun applyServerReactionSnapshot(
        current: List<ChatMessageSnapshot>,
        index: Int,
        event: ChatDomainEvent.ServerReactionSnapshot
    ): List<ChatMessageSnapshot> {
        if (index < 0) return current
        val existing = current[index]
        if (existing.deleteSequence != null && existing.deleteSequence >= (event.serverSequence ?: Long.MIN_VALUE)) return current
        if (event.serverSequence != null && existing.serverSequence != null && event.serverSequence < existing.serverSequence) return current
        if (event.reactionVersion < existing.reactionVersion) return current

        val updated = existing.copy(
            reactions = parseReactions(event.reactionsJson),
            hasReactionSnapshot = true,
            reactionVersion = maxOf(existing.reactionVersion, event.reactionVersion),
            serverSequence = maxOfNullable(existing.serverSequence, event.serverSequence)
        )
        return current.updated(index, updated)
    }

    private fun applyServerSeenUpdated(
        current: List<ChatMessageSnapshot>,
        index: Int,
        event: ChatDomainEvent.ServerSeenUpdated
    ): List<ChatMessageSnapshot> {
        if (index < 0) return current
        val existing = current[index]
        if (existing.deleteSequence != null && existing.deleteSequence >= (event.serverSequence ?: Long.MIN_VALUE)) return current
        if (event.serverSequence != null && existing.serverSequence != null && event.serverSequence < existing.serverSequence) return current
        if (event.deliveryVersion < existing.deliveryVersion) return current
        val updated = existing.copy(
            status = if (statusRank(existing.status) >= statusRank(ChatMessageStatus.SEEN)) existing.status else ChatMessageStatus.SEEN,
            deliveryVersion = maxOf(existing.deliveryVersion, event.deliveryVersion),
            serverSequence = maxOfNullable(existing.serverSequence, event.serverSequence)
        )
        return current.updated(index, updated)
    }

    private fun applyServerDeliveredUpdated(
        current: List<ChatMessageSnapshot>,
        index: Int,
        event: ChatDomainEvent.ServerDeliveredUpdated
    ): List<ChatMessageSnapshot> {
        if (index < 0) return current
        val existing = current[index]
        if (existing.deleteSequence != null && existing.deleteSequence >= (event.serverSequence ?: Long.MIN_VALUE)) return current
        if (event.serverSequence != null && existing.serverSequence != null && event.serverSequence < existing.serverSequence) return current
        if (event.deliveryVersion < existing.deliveryVersion) return current
        val updated = existing.copy(
            status = if (statusRank(existing.status) >= statusRank(ChatMessageStatus.DELIVERED)) {
                existing.status
            } else {
                ChatMessageStatus.DELIVERED
            },
            deliveryVersion = maxOf(existing.deliveryVersion, event.deliveryVersion),
            serverSequence = maxOfNullable(existing.serverSequence, event.serverSequence ?: event.deliveredAt)
        )
        return current.updated(index, updated)
    }

    private fun matchesEvent(message: ChatMessageSnapshot, event: ChatDomainEvent): Boolean = when (event) {
        is ChatDomainEvent.LocalQueued -> sameLogicalMessage(message, event.message)
        is ChatDomainEvent.LocalSending -> matchesClientIdentity(message, event.clientId)
        is ChatDomainEvent.LocalFailed -> matchesClientIdentity(message, event.clientId)
        is ChatDomainEvent.ServerMessageCreated -> sameLogicalMessage(message, event.message)
        is ChatDomainEvent.ServerMessageEdited -> message.backendId == event.messageId
        is ChatDomainEvent.ServerMessageDeleted -> message.backendId == event.messageId
        is ChatDomainEvent.ServerReactionSnapshot -> message.backendId == event.messageId
        is ChatDomainEvent.ServerDeliveredUpdated -> message.backendId == event.messageId
        is ChatDomainEvent.ServerSeenUpdated -> message.backendId == event.messageId
    }

    private fun matchesClientIdentity(message: ChatMessageSnapshot, identity: String): Boolean =
        message.clientId == identity || message.localId == identity || message.backendId == identity

    private fun shouldIgnoreServerEvent(current: ChatMessageSnapshot, incomingSequence: Long?): Boolean {
        if (current.deleteSequence != null && (incomingSequence == null || incomingSequence <= current.deleteSequence)) return true
        if (incomingSequence != null && current.serverSequence != null && incomingSequence < current.serverSequence) return true
        return false
    }

    private fun shouldApplyText(current: ChatMessageSnapshot, incoming: ChatMessageSnapshot): Boolean {
        if (current.isDeleted) return false
        if (incoming.serverSequence != null && current.serverSequence != null && incoming.serverSequence < current.serverSequence) return false
        if (incoming.editVersion > current.editVersion) return true
        if (incoming.serverSequence != null && current.serverSequence != null) return incoming.serverSequence >= current.serverSequence
        if (incoming.editedAt != null && current.editedAt != null) return incoming.editedAt >= current.editedAt
        return incoming.source.priority() >= current.source.priority()
    }

    private fun shouldApplyEdit(current: ChatMessageSnapshot, incoming: ChatMessageSnapshot): Boolean =
        !current.isDeleted && (incoming.editVersion > current.editVersion ||
            (incoming.editVersion == current.editVersion && incoming.editedAt != null && incoming.editedAt >= (current.editedAt ?: Long.MIN_VALUE)))

    private fun shouldApplyReactions(current: ChatMessageSnapshot, incoming: ChatMessageSnapshot): Boolean {
        if (!incoming.hasReactionSnapshot) return false
        if (current.isDeleted) return false
        if (incoming.serverSequence != null && current.serverSequence != null && incoming.serverSequence < current.serverSequence) return false
        return incoming.reactionVersion >= current.reactionVersion
    }

    private fun mergeStatus(current: ChatMessageSnapshot, incoming: ChatMessageSnapshot): ChatMessageStatus = when {
        current.status == ChatMessageStatus.SEEN || incoming.status == ChatMessageStatus.SEEN -> ChatMessageStatus.SEEN
        current.status == ChatMessageStatus.DELIVERED || incoming.status == ChatMessageStatus.DELIVERED -> ChatMessageStatus.DELIVERED
        statusRank(current.status) >= statusRank(ChatMessageStatus.SENT) && incoming.status == ChatMessageStatus.FAILED -> current.status
        statusRank(current.status) >= statusRank(ChatMessageStatus.SENT) &&
            (incoming.status == ChatMessageStatus.SENDING || incoming.status == ChatMessageStatus.QUEUED) -> current.status
        incoming.source.isServerAuthoritative() && incoming.status != ChatMessageStatus.QUEUED && incoming.status != ChatMessageStatus.SENDING -> incoming.status
        current.status == ChatMessageStatus.FAILED && incoming.status == ChatMessageStatus.SENDING -> ChatMessageStatus.SENDING
        current.status == ChatMessageStatus.QUEUED && incoming.status == ChatMessageStatus.SENDING -> ChatMessageStatus.SENDING
        statusRank(incoming.status) >= statusRank(current.status) -> incoming.status
        else -> current.status
    }

    private fun MessageSource.priority(): Int = when (this) {
        MessageSource.LocalOptimistic -> 10
        MessageSource.LocalRetry -> 20
        MessageSource.LocalCache -> 30
        MessageSource.PaginationLoad -> 70
        MessageSource.HttpResponse -> 80
        MessageSource.SocketEvent -> 90
        MessageSource.ReconnectReconciliation -> 100
    }

    private fun MessageSource.isServerAuthoritative(): Boolean =
        this == MessageSource.HttpResponse ||
            this == MessageSource.SocketEvent ||
            this == MessageSource.ReconnectReconciliation ||
            this == MessageSource.PaginationLoad

    private fun MessageSource.serverAuthoritativeVariant(): MessageSource = when (this) {
        MessageSource.LocalOptimistic,
        MessageSource.LocalRetry,
        MessageSource.LocalCache -> MessageSource.HttpResponse
        else -> this
    }

    private fun strongerSource(a: MessageSource, b: MessageSource): MessageSource =
        if (b.priority() >= a.priority()) b else a

    private fun mergeTimestamp(current: ChatMessageSnapshot, incoming: ChatMessageSnapshot): Long {
        if (current.timestamp <= 0L) return incoming.timestamp
        if (incoming.timestamp <= 0L) return current.timestamp
        return minOf(current.timestamp, incoming.timestamp)
    }

    private fun mergeLocalId(current: ChatMessageSnapshot, incoming: ChatMessageSnapshot): String? =
        current.localId ?: incoming.localId?.takeIf { incoming.backendId == null }

    private fun mergeClientId(current: ChatMessageSnapshot, incoming: ChatMessageSnapshot): String? =
        current.clientId ?: incoming.clientId ?: incoming.localId

    private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private fun List<ChatMessageSnapshot>.dedupe(): List<ChatMessageSnapshot> =
        fold(emptyList()) { acc, item ->
            val index = acc.indexOfFirst { sameLogicalMessage(it, item) }
            if (index < 0) {
                acc + item
            } else {
                acc.toMutableList().also { existing ->
                    existing[index] = merge(existing[index], item).message
                }
            }
        }

    private fun messageOrdering(): Comparator<ChatMessageSnapshot> =
        compareBy<ChatMessageSnapshot> { it.serverSequence ?: Long.MAX_VALUE }
            .thenBy { it.timestamp }
            .thenBy { it.stableId.orEmpty() }

    private fun ChatMessageSnapshot.toDomainEvent(): ChatDomainEvent = when (source) {
        MessageSource.LocalOptimistic -> ChatDomainEvent.LocalQueued(copy(status = ChatMessageStatus.QUEUED))
        MessageSource.LocalRetry -> if (status == ChatMessageStatus.FAILED) {
            ChatDomainEvent.LocalFailed(clientId ?: localId.orEmpty(), errorReason)
        } else {
            ChatDomainEvent.LocalSending(clientId ?: localId.orEmpty())
        }
        MessageSource.LocalCache,
        MessageSource.HttpResponse,
        MessageSource.SocketEvent,
        MessageSource.ReconnectReconciliation,
        MessageSource.PaginationLoad -> ChatDomainEvent.ServerMessageCreated(this, serverSequence)
    }

    private fun List<ChatMessageSnapshot>.updated(index: Int, item: ChatMessageSnapshot): List<ChatMessageSnapshot> =
        toMutableList().also { it[index] = item }

    private fun parseReactions(reactionsJson: String): List<String> {
        val trimmed = reactionsJson.trim()
        if (trimmed.isBlank() || trimmed == "[]") return emptyList()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return trimmed.split("|").map { it.trim() }.filter { it.isNotBlank() }
        }
        val body = trimmed.removePrefix("[").removeSuffix("]").trim()
        if (body.isBlank()) return emptyList()
        return body
            .split(",")
            .map { it.trim().removePrefix("\"").removeSuffix("\"") }
            .filter { it.isNotBlank() }
    }
}
