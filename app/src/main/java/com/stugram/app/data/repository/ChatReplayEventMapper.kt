package com.stugram.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.ChatReplayEventModel
import com.stugram.app.domain.chat.ChatDomainEvent
import com.stugram.app.domain.chat.ChatMessageSnapshot
import com.stugram.app.domain.chat.ChatMessageStatus
import com.stugram.app.domain.chat.MessageSource
import java.time.Instant

internal object ChatReplayEventMapper {
    private val gson = Gson()

    fun toDomainEvent(event: ChatReplayEventModel): ChatDomainEvent? {
        return when (event.type) {
            "message.created" -> event.payload
                ?.getAsJsonObjectOrNull("message")
                ?.let { gson.fromJson(it, ChatMessageModel::class.java) }
                ?.let { message ->
                    ChatDomainEvent.ServerMessageCreated(
                        message = message.toReplaySnapshot(event.sequence),
                        serverSequence = event.sequence
                    )
                }

            "message.edited" -> {
                val messageId = event.messageId ?: event.payload.stringOrNull("messageId") ?: return null
                ChatDomainEvent.ServerMessageEdited(
                    messageId = messageId,
                    text = event.payload.stringOrNull("text").orEmpty(),
                    editVersion = event.payload.intOrNull("editVersion") ?: event.sequence.toIntSafe(),
                    serverSequence = event.sequence
                )
            }

            "message.deleted" -> {
                val messageId = event.messageId ?: event.payload.stringOrNull("messageId") ?: return null
                ChatDomainEvent.ServerMessageDeleted(
                    messageId = messageId,
                    deleteSequence = event.sequence
                )
            }

            "message.reactions" -> {
                val messageId = event.messageId ?: event.payload.stringOrNull("messageId") ?: return null
                ChatDomainEvent.ServerReactionSnapshot(
                    messageId = messageId,
                    reactionsJson = event.payload?.get("reactions")?.toString() ?: "[]",
                    reactionVersion = event.payload.intOrNull("reactionVersion") ?: event.sequence.toIntSafe(),
                    serverSequence = event.sequence
                )
            }

            "message.seen" -> {
                val messageId = event.messageId ?: event.payload.stringOrNull("messageId") ?: return null
                ChatDomainEvent.ServerSeenUpdated(
                    messageId = messageId,
                    seenAt = event.payload.stringOrNull("seenAt")?.toEpochMillisOrNull() ?: System.currentTimeMillis(),
                    deliveryVersion = event.payload.intOrNull("seenVersion") ?: event.sequence.toIntSafe(),
                    serverSequence = event.sequence
                )
            }

            else -> null
        }
    }

    private fun ChatMessageModel.toReplaySnapshot(sequence: Long): ChatMessageSnapshot {
        val safeSeenBy = runCatching { seenBy }.getOrNull().orEmpty()
        val safeReactions = runCatching { reactions }.getOrNull().orEmpty()

        return ChatMessageSnapshot(
            localId = clientId,
            clientId = clientId,
            backendId = id,
            rawJson = replayRawJson(sequence),
            text = text.orEmpty(),
            status = when {
                safeSeenBy.isNotEmpty() || readAt != null -> ChatMessageStatus.SEEN
                deliveredAt != null -> ChatMessageStatus.DELIVERED
                else -> ChatMessageStatus.SENT
            },
            source = MessageSource.ReconnectReconciliation,
            timestamp = createdAt.toEpochMillisOrNull() ?: System.currentTimeMillis(),
            editedAt = editedAt.toEpochMillisOrNull(),
            deletedAt = deletedForEveryoneAt.toEpochMillisOrNull(),
            reactions = safeReactions.map { "${it.user?.id.orEmpty()}:${it.emoji}" },
            hasReactionSnapshot = true,
            serverSequence = sequence,
            editVersion = editVersion ?: if (editedAt != null) sequence.toIntSafe() else 0,
            reactionVersion = reactionVersion ?: if (safeReactions.isNotEmpty()) sequence.toIntSafe() else 0,
            deliveryVersion = deliveryVersion ?: when {
                safeSeenBy.isNotEmpty() || readAt != null -> sequence.toIntSafe()
                deliveredAt != null -> 1
                else -> 0
            },
            deleteSequence = deleteSequence ?: deletedForEveryoneAt.toEpochMillisOrNull()
        )
    }

    private fun JsonObject?.getAsJsonObjectOrNull(name: String): JsonObject? =
        this?.get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun ChatMessageModel.replayRawJson(sequence: Long): String {
        val raw = gson.toJsonTree(this).asJsonObject
        raw.addProperty("serverSequence", sequence)
        return raw.toString()
    }

    private fun JsonObject?.stringOrNull(name: String): String? =
        this?.get(name)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject?.intOrNull(name: String): Int? =
        this?.get(name)?.takeIf { !it.isJsonNull }?.asInt

    private fun Long.toIntSafe(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private fun String?.toEpochMillisOrNull(): Long? =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}
