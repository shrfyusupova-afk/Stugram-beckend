package com.stugram.app.domain.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StagedChatMedia(
    val localPath: String,
    val mimeType: String,
    val fileSize: Long,
    val displayName: String? = null,
    val thumbnailPath: String? = null
)

object ChatMediaStager {
    private const val ROOT_DIR = "chat_media"
    private const val ORIGINAL_FILE_NAME = "original"

    suspend fun copyToPrivateStorage(
        context: Context,
        source: Uri,
        clientId: String,
        providedMimeType: String? = null
    ): StagedChatMedia = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        val displayName = source.displayName(appContext)
        val mimeType = providedMimeType
            ?.takeIf { it.isNotBlank() }
            ?: resolver.getType(source)
            ?: "application/octet-stream"
        val targetDir = clientDirectory(appContext, clientId)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        if (!targetDir.mkdirs() && !targetDir.exists()) {
            throw IOException("Cannot create chat media staging directory")
        }
        val target = File(targetDir, ORIGINAL_FILE_NAME)
        resolver.openInputStream(source)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Cannot open selected media")

        if (!target.exists() || target.length() <= 0L) {
            target.delete()
            throw IOException("Selected media is empty or unavailable")
        }

        StagedChatMedia(
            localPath = target.absolutePath,
            mimeType = mimeType,
            fileSize = target.length(),
            displayName = displayName
        )
    }

    fun clientDirectory(context: Context, clientId: String): File =
        File(File(context.applicationContext.filesDir, ROOT_DIR), clientId)

    fun cleanup(context: Context, clientId: String) {
        runCatching {
            clientDirectory(context, clientId).deleteRecursively()
        }
    }

    private fun Uri.displayName(context: Context): String? {
        if (scheme == "file") return path?.let(::File)?.name
        return context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }
}
