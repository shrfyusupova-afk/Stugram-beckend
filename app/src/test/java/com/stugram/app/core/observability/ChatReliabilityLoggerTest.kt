package com.stugram.app.core.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ChatReliabilityLoggerTest {
    private val captured = mutableListOf<ChatReliabilityEvent>()

    @Before
    fun setUp() {
        ChatReliabilityMetrics.resetForTest()
        ChatReliabilityLogger.sink = object : ChatReliabilitySink {
            override fun emit(entry: ChatReliabilityEvent) {
                captured += entry
            }
        }
    }

    @Test
    fun structuredLogsRedactSensitiveFields() {
        ChatReliabilityLogger.info(
            "chat_send_started",
            mapOf(
                "clientId" to "client-1",
                "targetId" to "conv-1",
                "text" to "secret body",
                "accessToken" to "abc",
                "otpCode" to "123456"
            )
        )

        assertEquals(1, captured.size)
        val entry = captured.single()
        assertEquals("chat_send_started", entry.event)
        assertEquals("client-1", entry.fields["clientId"])
        assertEquals("conv-1", entry.fields["targetId"])
        assertEquals("[REDACTED]", entry.fields["text"])
        assertEquals("[REDACTED]", entry.fields["accessToken"])
        assertEquals("[REDACTED]", entry.fields["otpCode"])
        assertFalse(entry.fields.values.contains("secret body"))
    }

    @Test
    fun metricsSnapshotIncludesCountersAndGauges() {
        ChatReliabilityMetrics.increment("chat_send_success_total", mapOf("targetType" to "direct"))
        ChatReliabilityMetrics.setGauge("chat_pending_queue_depth", 4)

        val snapshot = ChatReliabilityMetrics.snapshot()
        assertEquals(1L, snapshot["counter:chat_send_success_total|targetType=direct"])
        assertEquals(4L, snapshot["gauge:chat_pending_queue_depth"])
    }
}
