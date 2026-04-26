package com.stugram.app.data.repository

import java.io.File

private val mp4LikeExtensions = setOf("mp4", "m4v", "3gp", "3gpp", "3g2", "3gpp2")

internal fun normalizeMediaUploadMimeType(declaredMimeType: String?, fileName: String): String {
    val normalized = declaredMimeType
        ?.substringBefore(";")
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }

    val extension = File(fileName).extension.lowercase()

    return when {
        normalized in setOf("image/jpeg", "image/png", "image/webp") -> normalized!!
        normalized in setOf("video/mp4", "video/quicktime", "video/webm") -> normalized!!
        normalized == "video/x-m4v" || normalized == "video/3gpp" || normalized == "video/3gpp2" -> "video/mp4"
        normalized == "video/x-matroska" -> "video/webm"
        normalized?.startsWith("video/") == true && extension == "webm" -> "video/webm"
        normalized?.startsWith("video/") == true && extension == "mov" -> "video/quicktime"
        normalized?.startsWith("video/") == true -> "video/mp4"
        normalized == "application/octet-stream" && extension == "webm" -> "video/webm"
        normalized == "application/octet-stream" && extension == "mov" -> "video/quicktime"
        normalized == "application/octet-stream" && extension in mp4LikeExtensions -> "video/mp4"
        normalized != null -> normalized
        extension == "webm" -> "video/webm"
        extension == "mov" -> "video/quicktime"
        extension in mp4LikeExtensions -> "video/mp4"
        else -> "application/octet-stream"
    }
}
