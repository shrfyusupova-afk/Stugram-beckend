package com.example.profile.service

import java.io.File
import java.util.*

interface MediaService {
    suspend fun uploadFile(file: File, folder: String): String
}

class S3MediaService : MediaService {
    override suspend fun uploadFile(file: File, folder: String): String {
        // Mock implementation for S3 upload
        val fileName = "${UUID.randomUUID()}-${file.name}"
        return "https://my-bucket.s3.amazonaws.com/$folder/$fileName"
    }
}

class CloudinaryMediaService : MediaService {
    override suspend fun uploadFile(file: File, folder: String): String {
        // Mock implementation for Cloudinary upload
        return "https://res.cloudinary.com/demo/image/upload/${UUID.randomUUID()}"
    }
}
