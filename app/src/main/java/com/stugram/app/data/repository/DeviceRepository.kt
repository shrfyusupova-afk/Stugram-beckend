package com.stugram.app.data.repository

import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.DeletePushTokenRequest
import com.stugram.app.data.remote.model.RegisterPushTokenRequest
import com.stugram.app.data.remote.model.SimpleFlagData
import retrofit2.Response

class DeviceRepository {
    private val deviceApi get() = RetrofitClient.deviceApi

    suspend fun registerPushToken(request: RegisterPushTokenRequest): Response<BaseResponse<SimpleFlagData>> =
        deviceApi.registerPushToken(request)

    suspend fun deletePushToken(request: DeletePushTokenRequest): Response<BaseResponse<SimpleFlagData>> =
        deviceApi.deletePushToken(request)
}
