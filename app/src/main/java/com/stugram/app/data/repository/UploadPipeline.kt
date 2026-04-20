package com.stugram.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.stugram.app.data.remote.model.BaseResponse
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class UploadFailureKind {
    Validation,
    Network,
    Backend,
    Cloud,
    Unknown
}

sealed class UploadOutcome<out T> {
    data class Success<T>(
        val data: T,
        val message: String? = null
    ) : UploadOutcome<T>()

    data class Failure(
        val kind: UploadFailureKind,
        val message: String,
        val code: Int? = null,
        val details: String? = null
    ) : UploadOutcome<Nothing>()
}

suspend fun Context.copyUriToTempFile(
    uri: Uri,
    prefix: String,
    mimeType: String? = null
): File = withContext(Dispatchers.IO) {
    if (uri.scheme == "file") {
        return@withContext requireNotNull(uri.path) { "Cannot resolve file uri" }.let(::File)
    }

    val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    val extension = displayName?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?: mimeType?.substringAfterLast('/', "bin")
            ?.takeIf { it.isNotBlank() && it != "*" }
        ?: "bin"

    val file = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.$extension")
    contentResolver.openInputStream(uri)?.use { inputStream ->
        FileOutputStream(file).use { output ->
            inputStream.copyTo(output)
        }
    } ?: throw IOException("Cannot open uri")
    file
}

fun <T> Response<BaseResponse<T>>.toUploadOutcome(
    fallbackMessage: String,
    cloudFailureMessage: String? = null
): UploadOutcome<T> {
    val body = body()
    val rawErrorBody = runCatching { errorBody()?.string() }.getOrNull()
    if (isSuccessful && body?.data != null) {
        return UploadOutcome.Success(
            data = body.data,
            message = body.message
        )
    }

    val code = code()
    val message = when {
        !body?.message.isNullOrBlank() -> body!!.message
        !rawErrorBody.isNullOrBlank() -> rawErrorBody
        code in 400..499 -> fallbackMessage
        code in 500..599 -> cloudFailureMessage ?: fallbackMessage
        else -> fallbackMessage
    }

    val normalizedErrorText = listOfNotNull(rawErrorBody, body?.message, cloudFailureMessage, fallbackMessage)
        .joinToString(" ")
        .lowercase()
    val kind = when {
        code in 400..499 -> if (code == 413) UploadFailureKind.Validation else UploadFailureKind.Backend
        code in 500..599 && (normalizedErrorText.contains("cloud") || normalizedErrorText.contains("storage") || normalizedErrorText.contains("upload")) ->
            UploadFailureKind.Cloud
        code in 500..599 -> UploadFailureKind.Backend
        else -> UploadFailureKind.Unknown
    }

    return UploadOutcome.Failure(
        kind = kind,
        message = message.ifBlank { fallbackMessage },
        code = code
    )
}

fun Throwable.toUploadOutcome(fallbackMessage: String): UploadOutcome.Failure {
    val kind = when (this) {
        is IOException -> UploadFailureKind.Network
        else -> UploadFailureKind.Unknown
    }
    return UploadOutcome.Failure(
        kind = kind,
        message = localizedMessage?.takeIf { it.isNotBlank() } ?: fallbackMessage,
        details = this::class.java.simpleName
    )
}
