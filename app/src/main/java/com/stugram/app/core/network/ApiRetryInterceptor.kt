package com.stugram.app.core.network

import com.stugram.app.core.observability.ChatReliabilityLogger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import kotlin.math.min

class ApiRetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = prepareRequest(chain.request())
        var attempt = 1
        var lastException: IOException? = null

        while (attempt <= MAX_ATTEMPTS) {
            try {
                val response = chain.proceed(request)
                if (!shouldRetryResponse(request, response, attempt)) {
                    return response
                }

                val delayMs = retryDelayMs(response, attempt)
                ChatReliabilityLogger.warn(
                    "api_request_retry",
                    mapOf(
                        "path" to request.url.encodedPath,
                        "method" to request.method,
                        "attempt" to attempt,
                        "httpStatus" to response.code,
                        "delayMs" to delayMs
                    )
                )
                response.close()
                sleepBeforeRetry(delayMs)
            } catch (error: IOException) {
                if (!canRetryRequest(request, attempt)) throw error
                lastException = error
                val delayMs = retryDelayMs(null, attempt)
                ChatReliabilityLogger.warn(
                    "api_request_retry_io",
                    mapOf(
                        "path" to request.url.encodedPath,
                        "method" to request.method,
                        "attempt" to attempt,
                        "errorName" to error::class.java.simpleName,
                        "delayMs" to delayMs
                    )
                )
                sleepBeforeRetry(delayMs)
            }

            attempt += 1
            request = request.newBuilder()
                .header(RETRY_ATTEMPT_HEADER, attempt.toString())
                .build()
        }

        lastException?.let { throw it }
        return chain.proceed(request)
    }

    private fun prepareRequest(request: Request): Request {
        val builder = request.newBuilder()
            .header(CLIENT_HEADER, "android")

        if (request.header(RETRY_ATTEMPT_HEADER).isNullOrBlank()) {
            builder.header(RETRY_ATTEMPT_HEADER, "1")
        }

        if (request.method in IDEMPOTENT_KEY_METHODS && request.header(IDEMPOTENCY_HEADER).isNullOrBlank()) {
            builder.header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
        }

        return builder.build()
    }

    private fun shouldRetryResponse(request: Request, response: Response, attempt: Int): Boolean {
        if (response.code !in RETRYABLE_STATUS_CODES) return false
        return canRetryRequest(request, attempt)
    }

    private fun canRetryRequest(request: Request, attempt: Int): Boolean {
        if (attempt >= MAX_ATTEMPTS) return false
        val method = request.method.uppercase()
        if (method in SAFE_METHODS) return true
        if (method !in IDEMPOTENT_KEY_METHODS) return false
        if (request.header(IDEMPOTENCY_HEADER).isNullOrBlank()) return false
        val body = request.body ?: return true
        return !body.isOneShot() && !body.isDuplex()
    }

    private fun retryDelayMs(response: Response?, attempt: Int): Long {
        val retryAfterSeconds = response?.header("Retry-After")
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
        if (retryAfterSeconds != null) {
            return min(retryAfterSeconds * 1_000L, MAX_RETRY_AFTER_MS)
        }
        return when (attempt) {
            1 -> 450L
            2 -> 1_200L
            else -> 2_400L
        }
    }

    private fun sleepBeforeRetry(delayMs: Long) {
        if (delayMs <= 0L) return
        try {
            Thread.sleep(delayMs)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        private const val MAX_ATTEMPTS = 3
        private const val MAX_RETRY_AFTER_MS = 3_000L
        private const val CLIENT_HEADER = "X-Stugram-Client"
        private const val IDEMPOTENCY_HEADER = "Idempotency-Key"
        private const val RETRY_ATTEMPT_HEADER = "X-Stugram-Retry-Attempt"

        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")
        private val IDEMPOTENT_KEY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        private val RETRYABLE_STATUS_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
    }
}
