package com.stugram.app.data.repository

import com.stugram.app.BuildConfig
import com.stugram.app.data.remote.RetrofitClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class SupportRepository {
    private val supportApi get() = RetrofitClient.supportApi

    suspend fun createProblemTicket(
        category: String,
        subject: String,
        description: String,
        deviceInfo: String
    ) = supportApi.createSupportTicket(
        category = category.toRequestBody("text/plain".toMediaType()),
        subject = subject.toRequestBody("text/plain".toMediaType()),
        description = description.toRequestBody("text/plain".toMediaType()),
        appVersion = BuildConfig.VERSION_NAME.toRequestBody("text/plain".toMediaType()),
        deviceInfo = deviceInfo.toRequestBody("text/plain".toMediaType())
    )

    suspend fun getMySupportTickets(page: Int = 1, limit: Int = 20) =
        supportApi.getMySupportTickets(page, limit)
}
