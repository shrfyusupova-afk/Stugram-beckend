package com.stugram.app.core.upload

import android.content.Context
import android.net.Uri
import androidx.work.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

enum class UploadStatus { QUEUED, UPLOADING, SUCCESS, FAILED }

data class UploadState(
    val id: UUID,
    val status: UploadStatus,
    val progress: Float = 0f
)

class UploadManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueueMediaUpload(
        conversationId: String,
        uri: Uri,
        mimeType: String,
        isGroup: Boolean = false,
        messageTypeOverride: String? = null,
        replyToMessageId: String? = null,
        tempFile: File? = null
    ): UUID {
        val data = workDataOf(
            "conversationId" to conversationId,
            "uri" to uri.toString(),
            "mimeType" to mimeType,
            "isGroup" to isGroup,
            "messageTypeOverride" to messageTypeOverride,
            "replyToMessageId" to replyToMessageId,
            "tempFilePath" to tempFile?.absolutePath
        )

        val uploadWork = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .addTag("upload_$conversationId")
            .build()

        workManager.enqueue(uploadWork)
        return uploadWork.id
    }

    fun getUploadStatesForConversation(conversationId: String): Flow<List<UploadState>> {
        return workManager.getWorkInfosByTagFlow("upload_$conversationId").map { list ->
            list.map { info ->
                UploadState(
                    id = info.id,
                    status = when (info.state) {
                        WorkInfo.State.ENQUEUED -> UploadStatus.QUEUED
                        WorkInfo.State.RUNNING -> UploadStatus.UPLOADING
                        WorkInfo.State.SUCCEEDED -> UploadStatus.SUCCESS
                        else -> UploadStatus.FAILED
                    }
                )
            }
        }
    }

    fun retryUpload(id: UUID) {
        workManager.enqueueUniqueWork(
            id.toString(),
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MediaUploadWorker>()
                .build()
        )
    }

    fun cancelUpload(id: UUID) {
        workManager.cancelWorkById(id)
    }
}
