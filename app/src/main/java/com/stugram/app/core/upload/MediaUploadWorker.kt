package com.stugram.app.core.upload

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.repository.ChatRepository
import com.stugram.app.data.repository.GroupChatRepository
import java.io.File

class MediaUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val conversationId = inputData.getString("conversationId") ?: return Result.failure()
        val uriString = inputData.getString("uri") ?: return Result.failure()
        val mimeType = inputData.getString("mimeType") ?: return Result.failure()
        val messageTypeOverride = inputData.getString("messageTypeOverride")
        val replyToMessageId = inputData.getString("replyToMessageId")
        val isGroup = inputData.getBoolean("isGroup", false)
        val tempFilePath = inputData.getString("tempFilePath")

        val uri = Uri.parse(uriString)
        
        RetrofitClient.init(applicationContext)
        
        return try {
            val response = if (isGroup) {
                val groupRepo = GroupChatRepository()
                groupRepo.sendGroupMediaMessage(
                    applicationContext,
                    conversationId,
                    uri,
                    mimeType,
                    replyToMessageId,
                    messageTypeOverride
                )
            } else {
                val chatRepo = ChatRepository()
                chatRepo.sendMediaMessage(
                    applicationContext,
                    conversationId,
                    uri,
                    mimeType,
                    replyToMessageId,
                    messageTypeOverride
                )
            }

            if (response.isSuccessful) {
                // Cleanup temp file if it was created specifically for this upload
                tempFilePath?.let { File(it).delete() }
                Result.success()
            } else {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
