package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<BaseResponse<OtpData>>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<BaseResponse<VerifyOtpData>>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<BaseResponse<AuthPayload>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<BaseResponse<AuthPayload>>

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshTokenRequest): Response<BaseResponse<LogoutData>>

    @POST("auth/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<BaseResponse<AuthPayload>>

    @POST("auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): Response<BaseResponse<AuthPayload>>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<BaseResponse<ForgotPasswordData>>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<BaseResponse<ResetPasswordData>>

    @POST("auth/switch-profile")
    suspend fun switchProfile(@Body request: SwitchProfileRequest): Response<BaseResponse<AuthPayload>>
}
