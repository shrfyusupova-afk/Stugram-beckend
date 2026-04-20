package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.DeletePushTokenRequest
import com.stugram.app.data.remote.model.RegisterPushTokenRequest
import com.stugram.app.data.remote.model.SimpleFlagData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

interface DeviceApi {
    @POST("devices/push-token")
    suspend fun registerPushToken(@Body request: RegisterPushTokenRequest): Response<BaseResponse<SimpleFlagData>>

    @HTTP(method = "DELETE", path = "devices/push-token", hasBody = true)
    suspend fun deletePushToken(@Body request: DeletePushTokenRequest): Response<BaseResponse<SimpleFlagData>>
}
