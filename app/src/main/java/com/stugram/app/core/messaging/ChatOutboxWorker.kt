package com.stugram.app.core.messaging

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stugram.app.core.observability.ChatReliabilityLogger
import com.stugram.app.core.observability.ChatReliabilityMetrics
import com.stugram.app.data.local.chat.ChatRoomStore
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.SendChatMessageRequest
import com.stugram.app.data.repository.ChatPendingOutbox
import com.stugram.app.data.repository.ChatRepository
import com.stugram.app.data.repository.GroupChatRepository
import com.stugram.app.domain.chat.ChatMediaStager
import java.io.File
import java.util.concurrent.TimeUnit

class ChatOutboxWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        RetrofitClient.init(applicationContext)
        val pending = ChatPendingOutbox.loadPending(applicationContext)
        val workerRunId = id.toString()
        val oldestAgeMs = pending.minOfOrNull { System.currentTimeMillis() - it.createdAt } ?: 0L
        ChatReliabilityMetrics.setGauge("chat_pending_queue_depth", pending.size.toLong())
        ChatReliabilityMetrics.setGauge("chat_pending_oldest_age_ms", oldestAgeMs)
        if (pending.isEmpty()) {
            ChatReliabilityLogger.info("chat_outbox_flush_skipped", mapOf("reason" to "empty", "workerRunId" to workerRunId))
            return Result.success()
        }
        ChatReliabilityLogger.info("chat_outbox_flush_started", mapOf("pendingCount" to pending.size, "runAttemptCount" to runAttemptCount, "workerRunId" to workerRunId))

        val chatRepository = ChatRepository()
        val groupRepository = GroupChatRepository()
        var retriableFailure = false
        val now = System.currentTimeMillis()

        pending.forEach { envelope ->
            val store = ChatRoomStore.get(applicationContext)
            val nextAttemptAt = envelope.nextAttemptAt
            if (envelope.status == "SENDING" && now - envelope.createdAt < RECENT_SENDING_GRACE_MS) {
                retriableFailure = true
                ChatReliabilityLogger.info("chat_send_skipped_inflight", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "workerRunId" to workerRunId))
                return@forEach
            }
            if (nextAttemptAt != null && nextAttemptAt > now) {
                retriableFailure = true
                ChatReliabilityLogger.info("chat_send_skipped_until_retry", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "nextAttemptAt" to nextAttemptAt, "workerRunId" to workerRunId))
                return@forEach
            }

            if (store.hasConfirmedMessage(envelope.scope, envelope.targetId, envelope.localId)) {
                ChatPendingOutbox.remove(applicationContext, envelope.localId)
                ChatMediaStager.cleanup(applicationContext, envelope.localId)
                ChatReliabilityLogger.info("chat_send_duplicate_resolved", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "workerRunId" to workerRunId))
                ChatReliabilityLogger.info("chat_pending_removed", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "reason" to "already_confirmed", "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_duplicate_resolved_total", mapOf("targetType" to envelope.scope))
                return@forEach
            }

            val stagedMediaPath = envelope.payload.mediaLocalPath
            val hasMedia = envelope.payload.mimeType != null && (envelope.payload.mediaLocalPath != null || envelope.payload.mediaUri != null)
            if (hasMedia && stagedMediaPath.isNullOrBlank()) {
                ChatPendingOutbox.markTerminalFailureAndRemove(
                    applicationContext,
                    envelope.localId,
                    ERROR_MEDIA_NOT_STAGED,
                    "The selected media file was not staged for retry."
                )
                ChatMediaStager.cleanup(applicationContext, envelope.localId)
                ChatReliabilityLogger.warn("chat_send_failed_terminal", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "errorCode" to ERROR_MEDIA_NOT_STAGED, "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_send_failed_terminal_total", mapOf("targetType" to envelope.scope, "reason" to ERROR_MEDIA_NOT_STAGED))
                return@forEach
            }
            if (!stagedMediaPath.isNullOrBlank() && !File(stagedMediaPath).exists()) {
                ChatPendingOutbox.markTerminalFailureAndRemove(
                    applicationContext,
                    envelope.localId,
                    ERROR_MEDIA_FILE_MISSING,
                    "The selected media file is no longer available."
                )
                ChatMediaStager.cleanup(applicationContext, envelope.localId)
                ChatReliabilityLogger.warn("chat_media_file_missing", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "errorCode" to ERROR_MEDIA_FILE_MISSING, "workerRunId" to workerRunId))
                ChatReliabilityLogger.warn("chat_send_failed_terminal", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "errorCode" to ERROR_MEDIA_FILE_MISSING, "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_media_upload_failure_total", mapOf("targetType" to envelope.scope))
                ChatReliabilityMetrics.increment("chat_send_failed_terminal_total", mapOf("targetType" to envelope.scope, "reason" to ERROR_MEDIA_FILE_MISSING))
                return@forEach
            }

            ChatPendingOutbox.markSending(applicationContext, envelope.localId)
            ChatReliabilityLogger.info("chat_send_started", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "retryCount" to envelope.retryCount, "workerRunId" to workerRunId))
            ChatReliabilityMetrics.increment("chat_send_attempt_total", mapOf("targetType" to envelope.scope, "source" to "worker"))
            val response = runCatching {
                val payload = envelope.payload
                if (envelope.scope == "group") {
                    if (payload.mediaLocalPath != null && payload.mimeType != null) {
                        groupRepository.sendStagedGroupMediaMessage(
                            groupId = envelope.targetId,
                            file = File(payload.mediaLocalPath),
                            mimeType = payload.mimeType,
                            replyToMessageId = payload.replyToMessageId,
                            messageTypeOverride = payload.messageTypeOverride,
                            clientId = envelope.localId,
                            displayName = payload.originalDisplayName
                        )
                    } else {
                        groupRepository.sendGroupMessage(
                            groupId = envelope.targetId,
                            request = SendChatMessageRequest(
                                text = payload.text,
                                replyToMessageId = payload.replyToMessageId,
                                clientId = envelope.localId
                            )
                        )
                    }
                } else {
                    if (payload.mediaLocalPath != null && payload.mimeType != null) {
                        chatRepository.sendStagedMediaMessage(
                            conversationId = envelope.targetId,
                            file = File(payload.mediaLocalPath),
                            mimeType = payload.mimeType,
                            replyToMessageId = payload.replyToMessageId,
                            messageTypeOverride = payload.messageTypeOverride,
                            clientId = envelope.localId,
                            displayName = payload.originalDisplayName
                        )
                    } else {
                        chatRepository.sendMessage(
                            conversationId = envelope.targetId,
                            request = SendChatMessageRequest(
                                text = payload.text,
                                replyToMessageId = payload.replyToMessageId,
                                clientId = envelope.localId
                            )
                        )
                    }
                }
            }.getOrNull()

            if (response?.isSuccessful == true) {
                val confirmed = response.body()?.data
                if (confirmed == null) {
                    val next = nextBackoffAt(envelope.retryCount)
                    ChatPendingOutbox.markRetry(applicationContext, envelope.localId, next, ERROR_EMPTY_SUCCESS)
                    retriableFailure = true
                    ChatReliabilityLogger.warn("chat_send_failed_retryable", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "errorCode" to ERROR_EMPTY_SUCCESS, "nextAttemptAt" to next, "workerRunId" to workerRunId))
                    ChatReliabilityMetrics.increment("chat_send_failed_retryable_total", mapOf("targetType" to envelope.scope, "reason" to ERROR_EMPTY_SUCCESS))
                    return@forEach
                }
                // Production reliability assumption: a server 2xx is only considered locally complete
                // after the confirmed server message and pending-row deletion commit in the same Room transaction.
                store.confirmPendingSuccess(
                    scope = envelope.scope,
                    targetId = envelope.targetId,
                    localId = envelope.localId,
                    model = confirmed
                )
                ChatMediaStager.cleanup(applicationContext, envelope.localId)
                ChatReliabilityLogger.info("chat_send_success", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "backendMessageId" to confirmed.id, "workerRunId" to workerRunId))
                ChatReliabilityLogger.info("chat_pending_removed", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "reason" to "confirmed_success", "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_send_success_total", mapOf("targetType" to envelope.scope, "source" to "worker"))
            } else if (response == null) {
                val next = nextBackoffAt(envelope.retryCount)
                ChatPendingOutbox.markRetry(applicationContext, envelope.localId, next, ERROR_NETWORK)
                retriableFailure = true
                ChatReliabilityLogger.warn("chat_send_failed_retryable", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "errorCode" to ERROR_NETWORK, "nextAttemptAt" to next, "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_send_failed_retryable_total", mapOf("targetType" to envelope.scope, "reason" to ERROR_NETWORK))
                ChatReliabilityMetrics.increment("chat_outbox_retry_total", mapOf("targetType" to envelope.scope))
            } else if (response.code() == 429) {
                val next = retryAfterMillis(response.headers()["Retry-After"]) ?: nextBackoffAt(envelope.retryCount)
                ChatPendingOutbox.markRetry(applicationContext, envelope.localId, next, ERROR_RATE_LIMITED)
                retriableFailure = true
                ChatReliabilityLogger.warn("chat_rate_limited", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to 429, "errorCode" to ERROR_RATE_LIMITED, "nextAttemptAt" to next, "workerRunId" to workerRunId))
                ChatReliabilityLogger.warn("chat_send_failed_retryable", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to 429, "errorCode" to ERROR_RATE_LIMITED, "nextAttemptAt" to next, "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_send_failed_retryable_total", mapOf("targetType" to envelope.scope, "reason" to ERROR_RATE_LIMITED))
                ChatReliabilityMetrics.increment("chat_429_total", mapOf("targetType" to envelope.scope))
            } else if (response.code() == 408 || response.code() in 500..599) {
                val next = nextBackoffAt(envelope.retryCount)
                ChatPendingOutbox.markRetry(applicationContext, envelope.localId, next, ERROR_TRANSIENT_SERVER)
                retriableFailure = true
                ChatReliabilityLogger.warn("chat_send_failed_retryable", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to response.code(), "errorCode" to ERROR_TRANSIENT_SERVER, "nextAttemptAt" to next, "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_send_failed_retryable_total", mapOf("targetType" to envelope.scope, "reason" to ERROR_TRANSIENT_SERVER))
                if (response.code() in 500..599) {
                    ChatReliabilityMetrics.increment("chat_5xx_total", mapOf("targetType" to envelope.scope))
                }
            } else if (response.code() == 401) {
                ChatPendingOutbox.markTerminalFailureAndRemove(
                    applicationContext,
                    envelope.localId,
                    ERROR_AUTH_FAILED,
                    "Authentication expired. Please sign in again."
                )
                ChatMediaStager.cleanup(applicationContext, envelope.localId)
                ChatReliabilityLogger.warn("chat_auth_refresh_failed", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to 401, "errorCode" to ERROR_AUTH_FAILED, "workerRunId" to workerRunId))
                ChatReliabilityLogger.warn("chat_send_failed_terminal", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to 401, "errorCode" to ERROR_AUTH_FAILED, "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_auth_refresh_failure_total", mapOf("targetType" to envelope.scope))
                ChatReliabilityMetrics.increment("chat_send_failed_terminal_total", mapOf("targetType" to envelope.scope, "reason" to ERROR_AUTH_FAILED))
            } else if (response.code() == 403) {
                val errorCode = classifyForbidden(response.errorBody()?.string())
                ChatPendingOutbox.markTerminalFailureAndRemove(
                    applicationContext,
                    envelope.localId,
                    errorCode,
                    terminalMessageFor(errorCode)
                )
                ChatMediaStager.cleanup(applicationContext, envelope.localId)
                ChatReliabilityLogger.warn("chat_send_failed_terminal", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to 403, "errorCode" to errorCode, "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_send_failed_terminal_total", mapOf("targetType" to envelope.scope, "reason" to errorCode))
            } else if (response.code() == 404 || response.code() == 413 || response.code() == 415) {
                val errorCode = when (response.code()) {
                    404 -> ERROR_TARGET_NOT_FOUND
                    413 -> ERROR_PAYLOAD_TOO_LARGE
                    else -> ERROR_UNSUPPORTED_MEDIA
                }
                ChatPendingOutbox.markTerminalFailureAndRemove(
                    applicationContext,
                    envelope.localId,
                    errorCode,
                    terminalMessageFor(errorCode)
                )
                ChatMediaStager.cleanup(applicationContext, envelope.localId)
                ChatReliabilityLogger.warn("chat_send_failed_terminal", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to response.code(), "errorCode" to errorCode, "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_send_failed_terminal_total", mapOf("targetType" to envelope.scope, "reason" to errorCode))
            } else {
                // Permanent validation/auth errors stay visible in the open chat but should not loop in background.
                ChatPendingOutbox.markTerminalFailureAndRemove(
                    applicationContext,
                    envelope.localId,
                    ERROR_TERMINAL_FAILURE,
                    "Could not send message. Please review and retry."
                )
                ChatMediaStager.cleanup(applicationContext, envelope.localId)
                ChatReliabilityLogger.warn("chat_send_failed_terminal", mapOf("targetType" to envelope.scope, "targetId" to envelope.targetId, "localId" to envelope.localId, "clientId" to envelope.localId, "httpStatus" to response.code(), "errorCode" to ERROR_TERMINAL_FAILURE, "workerRunId" to workerRunId))
                ChatReliabilityMetrics.increment("chat_send_failed_terminal_total", mapOf("targetType" to envelope.scope, "reason" to ERROR_TERMINAL_FAILURE))
            }
        }

        ChatReliabilityMetrics.setGauge("chat_pending_queue_depth", ChatPendingOutbox.loadPending(applicationContext).size.toLong())
        return if (retriableFailure) Result.retry() else Result.success()
    }

    private fun nextBackoffAt(retryCount: Int): Long {
        val safeRetryCount = retryCount.coerceIn(0, 6)
        val delayMs = TimeUnit.SECONDS.toMillis(30) * (1L shl safeRetryCount)
        return System.currentTimeMillis() + delayMs.coerceAtMost(TimeUnit.MINUTES.toMillis(30))
    }

    private fun retryAfterMillis(raw: String?): Long? {
        val seconds = raw?.trim()?.toLongOrNull() ?: return null
        return System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds.coerceAtLeast(0))
    }

    private fun classifyForbidden(errorBody: String?): String {
        val normalized = errorBody.orEmpty().lowercase()
        return when {
            "blocked" in normalized -> ERROR_BLOCKED_USER
            "removed" in normalized && "group" in normalized -> ERROR_REMOVED_FROM_GROUP
            "member" in normalized && "group" in normalized -> ERROR_REMOVED_FROM_GROUP
            "banned" in normalized -> ERROR_BANNED_USER
            else -> ERROR_FORBIDDEN
        }
    }

    private fun terminalMessageFor(errorCode: String): String = when (errorCode) {
        ERROR_BLOCKED_USER -> "You cannot send this message because this conversation is blocked."
        ERROR_REMOVED_FROM_GROUP -> "You cannot send this message because you are no longer in this group."
        ERROR_BANNED_USER -> "You cannot send this message because this account is restricted."
        ERROR_TARGET_NOT_FOUND -> "This conversation is no longer available."
        ERROR_PAYLOAD_TOO_LARGE -> "This media file is too large to send."
        ERROR_UNSUPPORTED_MEDIA -> "This media format is not supported."
        ERROR_MEDIA_FILE_MISSING -> "The selected media file is no longer available."
        ERROR_MEDIA_NOT_STAGED -> "The selected media file was not staged for retry."
        ERROR_AUTH_FAILED -> "Authentication expired. Please sign in again."
        else -> "Could not send message. Please review and retry."
    }

    private companion object {
        const val ERROR_NETWORK = "NETWORK_ERROR"
        const val ERROR_TRANSIENT_SERVER = "TRANSIENT_SERVER_ERROR"
        const val ERROR_RATE_LIMITED = "RATE_LIMITED"
        const val ERROR_AUTH_FAILED = "AUTH_FAILED"
        const val ERROR_BLOCKED_USER = "BLOCKED_USER"
        const val ERROR_REMOVED_FROM_GROUP = "REMOVED_FROM_GROUP"
        const val ERROR_BANNED_USER = "BANNED_USER"
        const val ERROR_FORBIDDEN = "FORBIDDEN"
        const val ERROR_TARGET_NOT_FOUND = "TARGET_NOT_FOUND"
        const val ERROR_PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
        const val ERROR_UNSUPPORTED_MEDIA = "UNSUPPORTED_MEDIA"
        const val ERROR_MEDIA_FILE_MISSING = "MEDIA_FILE_MISSING"
        const val ERROR_MEDIA_NOT_STAGED = "MEDIA_NOT_STAGED"
        const val ERROR_EMPTY_SUCCESS = "EMPTY_SUCCESS_RESPONSE"
        const val ERROR_TERMINAL_FAILURE = "TERMINAL_FAILURE"
        const val RECENT_SENDING_GRACE_MS = 45_000L
    }
}

object ChatOutboxScheduler {
    private const val UNIQUE_WORK_NAME = "stugram_chat_outbox_flush"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<ChatOutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(UNIQUE_WORK_NAME)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
