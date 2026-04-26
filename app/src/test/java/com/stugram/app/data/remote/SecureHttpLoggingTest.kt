package com.stugram.app.data.remote

import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureHttpLoggingTest {
    @Test
    fun releaseLoggingDisablesHttpLogs() {
        val interceptor = SecureHttpLogging.createInterceptor(isDebug = false)

        assertEquals(HttpLoggingInterceptor.Level.NONE, interceptor.level)
    }

    @Test
    fun debugLoggingAvoidsBodiesAndRedactsSensitiveValues() {
        val interceptor = SecureHttpLogging.createInterceptor(isDebug = true)
        val redacted = SecureHttpLogging.redact(
            """Authorization: Bearer raw-token accessToken:"abc" refreshToken:"def" otp:"123456" https://res.cloudinary.com/demo/private?signature=secret"""
        )

        assertEquals(HttpLoggingInterceptor.Level.BASIC, interceptor.level)
        assertFalse(redacted.contains("raw-token"))
        assertFalse(redacted.contains("abc"))
        assertFalse(redacted.contains("def"))
        assertFalse(redacted.contains("123456"))
        assertTrue(redacted.contains("██"))
    }
}
