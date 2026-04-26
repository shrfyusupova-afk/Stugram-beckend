package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface SupportApi {
    @Multipart
    @POST("support/problems")
    suspend fun createSupportTicket(
        @Part("category") category: RequestBody,
        @Part("subject") subject: RequestBody,
        @Part("description") description: RequestBody,
        @Part("appVersion") appVersion: RequestBody?,
        @Part("deviceInfo") deviceInfo: RequestBody?
    ): Response<BaseResponse<SupportTicketModel>>

    @GET("support/problems/me")
    suspend fun getMySupportTickets(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<com.stugram.app.data.remote.model.PaginatedResponse<SupportTicketModel>>
}

data class SupportTicketModel(
    val _id: String,
    val category: String,
    val subject: String,
    val description: String,
    val status: String,
    val appVersion: String? = null,
    val deviceInfo: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
