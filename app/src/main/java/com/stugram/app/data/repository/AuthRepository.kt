package com.stugram.app.data.repository

import android.content.Context
import android.net.Uri
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import kotlinx.coroutines.withContext
import java.io.IOException

class AuthRepository(
    private val tokenManager: TokenManager? = null
) {
    private val authApi get() = RetrofitClient.authApi
    private val gson = Gson()

    suspend fun sendOtp(identity: String, purpose: String = "register"): ApiResult<OtpData> {
        return runRequest {
            authApi.sendOtp(SendOtpRequest(identity = identity, purpose = purpose)).toApiResult()
        }
    }

    suspend fun verifyOtp(identity: String, otp: String, purpose: String = "register"): ApiResult<VerifyOtpData> {
        return runRequest {
            authApi.verifyOtp(VerifyOtpRequest(identity = identity, otp = otp, purpose = purpose)).toApiResult()
        }
    }

    suspend fun register(request: RegisterRequest): ApiResult<AuthPayload> {
        return runRequest {
            val response = authApi.register(request)
            val result = response.toApiResult()
            if (result is ApiResult.Success) {
                result.data?.let { payload ->
                    tokenManager?.saveSession(payload.user, payload.accessToken, payload.refreshToken)
                }
            }
            result
        }
    }

    suspend fun login(identityOrUsername: String, password: String): ApiResult<AuthPayload> {
        return runRequest {
            val response = authApi.login(LoginRequest(identityOrUsername, password))
            val result = response.toApiResult()
            if (result is ApiResult.Success) {
                result.data?.let { payload ->
                    tokenManager?.saveSession(payload.user, payload.accessToken, payload.refreshToken)
                }
            }
            result
        }
    }

    suspend fun logout(): ApiResult<LogoutData> {
        return runRequest {
            val refreshToken = tokenManager?.getRefreshToken().orEmpty()
            val response = authApi.logout(RefreshTokenRequest(refreshToken))
            val result = response.toApiResult()
            if (result is ApiResult.Success) {
                // Multi-account hardening: logout should only remove the active session.
                tokenManager?.removeActiveSession()
            }
            result
        }
    }

    suspend fun refreshToken(): ApiResult<AuthPayload> {
        return runRequest {
            val refreshToken = tokenManager?.getRefreshToken().orEmpty()
            val response = authApi.refreshToken(RefreshTokenRequest(refreshToken))
            val result = response.toApiResult()
            if (result is ApiResult.Success) {
                result.data?.let { payload ->
                    tokenManager?.saveSession(payload.user, payload.accessToken, payload.refreshToken)
                }
            }
            result
        }
    }

    suspend fun googleLogin(idToken: String): ApiResult<AuthPayload> {
        return runRequest {
            val response = authApi.googleLogin(GoogleLoginRequest(idToken))
            val result = response.toApiResult()
            if (result is ApiResult.Success) {
                result.data?.let { payload ->
                    tokenManager?.saveSession(payload.user, payload.accessToken, payload.refreshToken)
                }
            }
            result
        }
    }

    suspend fun forgotPassword(identity: String): ApiResult<ForgotPasswordData> {
        return runRequest {
            authApi.forgotPassword(ForgotPasswordRequest(identity = identity)).toApiResult()
        }
    }

    suspend fun resetPassword(token: String, newPassword: String): ApiResult<ResetPasswordData> {
        return runRequest {
            authApi.resetPassword(ResetPasswordRequest(token = token, password = newPassword)).toApiResult()
        }
    }

    suspend fun uploadImage(context: Context, uri: Uri): UploadOutcome<ProfileModel> {
        val mimeType = context.contentResolver.getType(uri) ?: "image/*"
        val file = withContext(kotlinx.coroutines.Dispatchers.IO) { context.copyUriToTempFile(uri, "auth_avatar", mimeType) }
        return try {
            val requestFile = file.asRequestBody(mimeType.toMediaType())
            val avatarPart = MultipartBody.Part.createFormData("avatar", file.name, requestFile)
            RetrofitClient.mediaApi.uploadAvatar(avatarPart).toUploadOutcome("Avatar upload failed")
        } catch (error: Exception) {
            error.toUploadOutcome("Avatar upload failed")
        } finally {
            file.delete()
        }
    }

    private fun <T> Response<BaseResponse<T>>.toApiResult(): ApiResult<T> {
        val body = body()
        if (isSuccessful && body != null) {
            return ApiResult.Success(
                data = body.data,
                message = body.message
            )
        }

        val errorMessage = parseErrorMessage(errorBody()?.string(), body?.message)
        return ApiResult.Error(
            message = errorMessage,
            code = code()
        )
    }

    private fun parseErrorMessage(rawBody: String?, fallback: String? = null): String {
        if (rawBody.isNullOrBlank()) return fallback ?: "An unknown error occurred"

        return runCatching {
            val type = object : TypeToken<BaseResponse<Any>>() {}.type
            gson.fromJson<BaseResponse<Any>>(rawBody, type)?.message
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: fallback
            ?: "An unknown error occurred"
    }

    private suspend fun <T> runRequest(block: suspend () -> ApiResult<T>): ApiResult<T> {
        return try {
            block()
        } catch (exception: Exception) {
            ApiResult.Error(
                message = exception.toReadableMessage(),
                code = null
            )
        }
    }

    private fun Exception.toReadableMessage(): String {
        return when (this) {
            is IOException -> "Could not connect to the server. Check your internet connection or backend address."
            else -> localizedMessage?.takeIf { it.isNotBlank() } ?: "An unknown error occurred"
        }
    }
}
