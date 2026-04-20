package com.stugram.app.core.nativemedia

import android.net.Uri

data class VideoCompressionRequest(
    val sourceUri: Uri,
    val mimeType: String,
    val maxWidth: Int = 1280,
    val maxHeight: Int = 720,
    val maxBitrate: Int = 2_500_000,
    val frameRate: Int = 30
)

interface VideoCompressionGateway {
    suspend fun prepare(request: VideoCompressionRequest): Uri
}
