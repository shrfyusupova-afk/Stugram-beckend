package com.stugram.app.ui.home

import com.google.gson.Gson
import com.stugram.app.data.local.chat.MessageEntity
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.MessageReactionModel
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.repository.PendingChatEnvelope
import retrofit2.Response

private val viewModelMapperGson = Gson()

internal val MessageData.stableKeyVm: String
    get() = localId?.let { "local:$it" }
        ?: backendId?.let { "backend:$it" }
        ?: "legacy:$id"

internal val GroupMessageData.stableKeyVm: String
    get() = localId?.let { "local:$it" }
        ?: backendId?.let { "backend:$it" }
        ?: "legacy:$id"

internal fun MessageEntity.toDirectUiMessageVm(currentUserId: String?): MessageData? {
    val model = rawJson?.let { runCatching { viewModelMapperGson.fromJson(it, ChatMessageModel::class.java) }.getOrNull() }
        ?: return null
    return model.toDirectUiMessageVm(currentUserId, this)
}

internal fun MessageEntity.toGroupUiMessageVm(currentUserId: String?): GroupMessageData? {
    val model = rawJson?.let { runCatching { viewModelMapperGson.fromJson(it, ChatMessageModel::class.java) }.getOrNull() }
        ?: return null
    return model.toGroupUiMessageVm(currentUserId, this)
}

internal fun PendingChatEnvelope.toDirectPendingUiMessageVm(currentUser: ProfileModel?): MessageData {
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
        pendingPayload = payload.toPendingMessagePayloadVm()
    )
}

internal fun PendingChatEnvelope.toGroupPendingUiMessageVm(senderName: String, senderAvatar: String?): GroupMessageData {
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
        pendingPayload = payload.toPendingMessagePayloadVm()
    )
}

internal fun mergeDirectVmMessages(
    confirmed: List<MessageData>,
    pending: List<MessageData>
): List<MessageData> {
    val byKey = LinkedHashMap<String, MessageData>()
    val aliasToKey = LinkedHashMap<String, String>()
    fun put(msg: MessageData) {
        val aliases = listOfNotNull(msg.localId, msg.backendId).distinct()
        if (aliases.isEmpty()) return
        val existingKey = aliases.firstNotNullOfOrNull { alias ->
            aliasToKey[alias] ?: alias.takeIf { byKey.containsKey(it) }
        }
        val existing = existingKey?.let { byKey[it] }
        val merged = existing?.mergeVmMessage(msg) ?: msg
        val key = merged.backendId ?: merged.localId ?: aliases.first()
        if (existingKey != null && existingKey != key) {
            byKey.remove(existingKey)
        }
        byKey[key] = merged
        (aliases + listOfNotNull(merged.localId, merged.backendId)).distinct().forEach { alias ->
            aliasToKey[alias] = key
        }
    }
    confirmed.forEach(::put)
    pending.forEach(::put)
    return byKey.values.sortedWith(compareBy<MessageData> { it.timestamp }.thenBy { it.backendId ?: it.localId ?: "" })
}

internal fun mergeGroupVmMessages(
    confirmed: List<GroupMessageData>,
    pending: List<GroupMessageData>
): List<GroupMessageData> {
    val byKey = LinkedHashMap<String, GroupMessageData>()
    val aliasToKey = LinkedHashMap<String, String>()
    fun put(msg: GroupMessageData) {
        val aliases = listOfNotNull(msg.localId, msg.backendId).distinct()
        if (aliases.isEmpty()) return
        val existingKey = aliases.firstNotNullOfOrNull { alias ->
            aliasToKey[alias] ?: alias.takeIf { byKey.containsKey(it) }
        }
        val existing = existingKey?.let { byKey[it] }
        val merged = existing?.mergeVmMessage(msg) ?: msg
        val key = merged.backendId ?: merged.localId ?: aliases.first()
        if (existingKey != null && existingKey != key) {
            byKey.remove(existingKey)
        }
        byKey[key] = merged
        (aliases + listOfNotNull(merged.localId, merged.backendId)).distinct().forEach { alias ->
            aliasToKey[alias] = key
        }
    }
    confirmed.forEach(::put)
    pending.forEach(::put)
    return byKey.values.sortedWith(compareByDescending<GroupMessageData> { it.timestamp }.thenByDescending { it.backendId ?: it.localId ?: "" })
}

internal fun retrofit2.Response<*>?.chatApiErrorMessage(defaultMessage: String): String {
    val response = this ?: return defaultMessage
    val body = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
    if (body.isNotBlank()) {
        val parsed = runCatching { org.json.JSONObject(body).optString("message") }.getOrNull()?.trim().orEmpty()
        if (parsed.isNotBlank()) return parsed
        return body.trim().takeIf { it.isNotBlank() } ?: response.message().ifBlank { defaultMessage }
    }
    return response.message().ifBlank { defaultMessage }
}

internal fun Response<*>?.shouldQueueForRetryVm(): Boolean {
    val response = this ?: return true
    return response.code() == 408 || response.code() == 429 || response.code() in 500..599
}

internal fun Throwable?.chatSendErrorMessageVm(): String = when (this) {
    is java.net.SocketTimeoutException -> "Network timeout. Queued for retry."
    is java.net.UnknownHostException -> "No internet connection. Queued for retry."
    is java.net.ConnectException -> "Could not reach server. Queued for retry."
    else -> "Waiting for connection. Queued for retry."
}

internal fun Throwable?.chatSendErrorCategoryVm(): String = when (this) {
    is java.net.SocketTimeoutException -> "timeout"
    is java.net.UnknownHostException -> "dns_or_offline"
    is java.net.ConnectException -> "connect_failed"
    null -> "no_http_response"
    else -> this::class.java.simpleName.ifBlank { "unknown" }
}

internal fun com.stugram.app.data.repository.PendingChatPayload.toPendingMessagePayloadVm(): PendingMessagePayload =
    PendingMessagePayload(
        text = text,
        replyToMessageId = replyToMessageId,
        mediaUri = mediaUri,
        mimeType = mimeType,
        messageTypeOverride = messageTypeOverride
    )

private fun MessageData.mergeVmMessage(other: MessageData): MessageData =
    copy(
        localId = other.localId ?: localId,
        backendId = other.backendId ?: backendId,
        text = if (other.text.isNotBlank()) other.text else text,
        isMe = isMe || other.isMe,
        senderId = other.senderId ?: senderId,
        timestamp = minOf(timestamp, other.timestamp),
        status = if (other.status.ordinal >= status.ordinal) other.status else status,
        errorReason = other.errorReason ?: errorReason,
        retryCount = maxOf(retryCount, other.retryCount),
        senderName = other.senderName ?: senderName,
        messageType = if (other.messageType.isNotBlank()) other.messageType else messageType,
        media = other.media ?: media,
        pendingPayload = other.pendingPayload ?: pendingPayload,
        replyPreview = other.replyPreview ?: replyPreview,
        reactions = if (other.reactions.isNotEmpty()) other.reactions else reactions,
        forwardedFromMessageId = other.forwardedFromMessageId ?: forwardedFromMessageId,
        forwardedFromSenderId = other.forwardedFromSenderId ?: forwardedFromSenderId,
        forwardedFromConversationId = other.forwardedFromConversationId ?: forwardedFromConversationId,
        forwardedAt = other.forwardedAt ?: forwardedAt,
        readAt = other.readAt ?: readAt,
        editedAt = other.editedAt ?: editedAt,
        deletedGloballyAt = other.deletedGloballyAt ?: deletedGloballyAt
    )

private fun GroupMessageData.mergeVmMessage(other: GroupMessageData): GroupMessageData =
    copy(
        localId = other.localId ?: localId,
        backendId = other.backendId ?: backendId,
        text = if (other.text.isNotBlank()) other.text else text,
        senderName = other.senderName.ifBlank { senderName },
        senderAvatar = other.senderAvatar ?: senderAvatar,
        isMe = isMe || other.isMe,
        timestamp = maxOf(timestamp, other.timestamp),
        status = if (other.status.ordinal >= status.ordinal) other.status else status,
        errorReason = other.errorReason ?: errorReason,
        retryCount = maxOf(retryCount, other.retryCount),
        messageType = if (other.messageType.isNotBlank()) other.messageType else messageType,
        media = other.media ?: media,
        pendingPayload = other.pendingPayload ?: pendingPayload,
        replyPreview = other.replyPreview ?: replyPreview,
        reactions = if (other.reactions.isNotEmpty()) other.reactions else reactions,
        forwardedFromMessageId = other.forwardedFromMessageId ?: forwardedFromMessageId,
        forwardedFromSenderId = other.forwardedFromSenderId ?: forwardedFromSenderId,
        forwardedFromConversationId = other.forwardedFromConversationId ?: forwardedFromConversationId,
        forwardedAt = other.forwardedAt ?: forwardedAt,
        readAt = other.readAt ?: readAt,
        editedAt = other.editedAt ?: editedAt,
        deletedGloballyAt = other.deletedGloballyAt ?: deletedGloballyAt
    )

private fun ChatMessageModel.toDirectUiMessageVm(currentUserId: String?, entity: MessageEntity): MessageData {
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
    val modelStatus = when {
        seen -> MessageStatus.SEEN
        deliveredAt != null -> MessageStatus.DELIVERED
        else -> MessageStatus.SENT
    }
    return MessageData(
        id = id.hashCode(),
        localId = clientId,
        backendId = id,
        text = entity.text.ifBlank { text.orEmpty() },
        isMe = isMine,
        senderId = senderId,
        timestamp = createdAt.toEpochMillisVm(),
        status = strongestUiStatus(modelStatus, entity.status),
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
        readAt = readAt,
        editedAt = editedAt,
        deletedGloballyAt = deletedForEveryoneAt
    )
}

private fun ChatMessageModel.toGroupUiMessageVm(currentUserId: String?, entity: MessageEntity): GroupMessageData {
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
    val modelStatus = when {
        seen -> MessageStatus.SEEN
        deliveredAt != null -> MessageStatus.DELIVERED
        else -> MessageStatus.SENT
    }
    return GroupMessageData(
        id = id.hashCode(),
        localId = clientId,
        backendId = id,
        text = entity.text.ifBlank { text.orEmpty() },
        senderName = sender?.fullName ?: sender?.username ?: "Unknown",
        senderAvatar = sender?.avatar,
        isMe = isMine,
        timestamp = createdAt.toEpochMillisVm(),
        status = strongestUiStatus(modelStatus, entity.status),
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
        readAt = readAt,
        editedAt = editedAt,
        deletedGloballyAt = deletedForEveryoneAt
    )
}

private fun String?.toEpochMillisVm(): Long {
    if (isNullOrBlank()) return System.currentTimeMillis()
    return runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
}

private fun strongestUiStatus(modelStatus: MessageStatus, entityStatus: String): MessageStatus {
    val stored = runCatching { com.stugram.app.domain.chat.ChatMessageStatus.valueOf(entityStatus).toUiStatus() }
        .getOrDefault(modelStatus)
    if (stored == MessageStatus.SEEN && modelStatus != MessageStatus.SEEN) {
        return modelStatus
    }
    return if (chatUiStatusRank(stored) >= chatUiStatusRank(modelStatus)) stored else modelStatus
}

private fun chatUiStatusRank(status: MessageStatus): Int = when (status) {
    MessageStatus.QUEUED -> 0
    MessageStatus.SENDING -> 1
    MessageStatus.FAILED -> 1
    MessageStatus.SENT -> 2
    MessageStatus.DELIVERED -> 3
    MessageStatus.SEEN -> 4
}
