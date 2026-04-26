package com.stugram.app.data.remote

import com.stugram.app.core.storage.TokenManager
import com.stugram.app.core.socket.ChatSocketManager
import com.stugram.app.core.observability.ChatReliabilityLogger
import com.stugram.app.core.observability.ChatReliabilityMetrics
import com.stugram.app.data.remote.model.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

class TokenAuthenticator(
    private val tokenManager: TokenManager
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            ChatReliabilityLogger.warn("chat_auth_refresh_failed", mapOf("errorCode" to "AUTH_RETRY_LOOP_GUARD", "httpStatus" to response.code))
            ChatReliabilityMetrics.increment("chat_auth_refresh_failure_total", mapOf("reason" to "retry_loop_guard"))
            return null
        }

        return try {
            synchronized(refreshLock) {
                val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
                val latestAccessToken = runBlocking { tokenManager.getAccessToken() }
                if (!latestAccessToken.isNullOrBlank() && latestAccessToken != failedToken) {
                    return@synchronized response.request.newBuilder()
                        .header("Authorization", "Bearer $latestAccessToken")
                        .build()
                }

                val refreshToken = runBlocking { tokenManager.getRefreshToken() } ?: return@synchronized clearAuthAndReturnNull("missing_refresh_token")
                val refreshApi = RetrofitClient.createRefreshApi()
                val refreshResponse = runBlocking {
                    refreshApi.refreshToken(RefreshTokenRequest(refreshToken))
                }

                if (!refreshResponse.isSuccessful) {
                    val reason = "refresh_http_${refreshResponse.code()}"
                    return@synchronized if (refreshResponse.code() in setOf(400, 401, 403)) {
                        clearAuthAndReturnNull(reason)
                    } else {
                        keepAuthAndReturnNull(reason, refreshResponse.code())
                    }
                }

                val body = refreshResponse.body()?.data ?: return@synchronized keepAuthAndReturnNull("refresh_response_empty", refreshResponse.code())
                val currentUser = runBlocking { tokenManager.getCurrentUser() }
                if (currentUser == null) {
                    return@synchronized clearAuthAndReturnNull("missing_current_user")
                }
                runBlocking {
                    tokenManager.saveSession(currentUser, body.accessToken, body.refreshToken)
                }

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${body.accessToken}")
                    .build()
            }
        } catch (_: IOException) {
            keepAuthAndReturnNull("refresh_io_exception")
        } catch (_: Exception) {
            keepAuthAndReturnNull("refresh_exception")
        }
    }

    private fun keepAuthAndReturnNull(reason: String, httpStatus: Int? = null): Request? {
        ChatReliabilityLogger.warn("chat_auth_refresh_failed", mapOf("errorCode" to reason, "httpStatus" to httpStatus))
        ChatReliabilityMetrics.increment("chat_auth_refresh_failure_total", mapOf("reason" to reason))
        return null
    }

    private fun clearAuthAndReturnNull(reason: String): Request? {
        ChatReliabilityLogger.warn("chat_auth_refresh_failed", mapOf("errorCode" to reason))
        ChatReliabilityMetrics.increment("chat_auth_refresh_failure_total", mapOf("reason" to reason))
        runCatching {
            runBlocking { tokenManager.clearSession() }
            ChatSocketManager.resetAuthenticatedSession()
        }
        return null
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    private companion object {
        private val refreshLock = Any()
    }
}
