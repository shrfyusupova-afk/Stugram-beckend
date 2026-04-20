package com.stugram.app.data.remote

import com.stugram.app.core.storage.TokenManager
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
        if (responseCount(response) >= 2) return null

        return try {
            val refreshToken = runBlocking { tokenManager.getRefreshToken() } ?: return null
            val refreshApi = RetrofitClient.createRefreshApi()
            val refreshResponse = runBlocking {
                refreshApi.refreshToken(RefreshTokenRequest(refreshToken))
            }

            val body = refreshResponse.body()?.data ?: return null
            runBlocking {
                val currentUser = tokenManager.getCurrentUser()
                if (currentUser != null) {
                    tokenManager.saveSession(currentUser, body.accessToken, body.refreshToken)
                } else {
                    tokenManager.saveTokens(body.accessToken, body.refreshToken)
                }
            }

            response.request.newBuilder()
                .header("Authorization", "Bearer ${body.accessToken}")
                .build()
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
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
}
