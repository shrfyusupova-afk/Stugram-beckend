package com.stugram.app.core.observability

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val sensitiveKeyPattern = Regex("(token|authorization|otp|password|secret|cookie|body|text|messageText|mediaUrl|url)", RegexOption.IGNORE_CASE)

data class ChatReliabilityEvent(
    val level: String,
    val event: String,
    val fields: Map<String, Any?>
)

interface ChatReliabilitySink {
    fun emit(entry: ChatReliabilityEvent)
}

object ChatReliabilityLogger {
    @Volatile
    var sink: ChatReliabilitySink = AndroidChatReliabilitySink

    fun info(event: String, fields: Map<String, Any?> = emptyMap()) {
        sink.emit(ChatReliabilityEvent("INFO", event, sanitize(fields)))
    }

    fun warn(event: String, fields: Map<String, Any?> = emptyMap()) {
        sink.emit(ChatReliabilityEvent("WARN", event, sanitize(fields)))
    }

    fun error(event: String, fields: Map<String, Any?> = emptyMap()) {
        sink.emit(ChatReliabilityEvent("ERROR", event, sanitize(fields)))
    }

    private fun sanitize(fields: Map<String, Any?>): Map<String, Any?> =
        fields.mapValues { (key, value) ->
            if (sensitiveKeyPattern.containsMatchIn(key)) {
                "[REDACTED]"
            } else {
                sanitizeValue(value)
            }
        }

    private fun sanitizeValue(value: Any?): Any? = when (value) {
        null -> null
        is Number, is Boolean -> value
        is String -> value.take(256)
        is Map<*, *> -> value.entries.associate { (nestedKey, nestedValue) ->
            val safeKey = nestedKey?.toString().orEmpty()
            safeKey to if (sensitiveKeyPattern.containsMatchIn(safeKey)) "[REDACTED]" else sanitizeValue(nestedValue)
        }
        is Iterable<*> -> value.map { sanitizeValue(it) }
        else -> value.toString().take(256)
    }
}

object AndroidChatReliabilitySink : ChatReliabilitySink {
    override fun emit(entry: ChatReliabilityEvent) {
        val rendered = buildString {
            append(entry.event)
            if (entry.fields.isNotEmpty()) {
                append(" ")
                append(
                    entry.fields.entries.joinToString(" ") { (key, value) ->
                        "$key=${value ?: "null"}"
                    }
                )
            }
        }
        when (entry.level) {
            "WARN" -> Log.w("ChatReliability", rendered)
            "ERROR" -> Log.e("ChatReliability", rendered)
            else -> Log.i("ChatReliability", rendered)
        }
    }
}

object ChatReliabilityMetrics {
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val gauges = ConcurrentHashMap<String, AtomicLong>()

    fun increment(name: String, labels: Map<String, String> = emptyMap(), amount: Long = 1L) {
        counters.computeIfAbsent(buildKey(name, labels)) { AtomicLong(0L) }.addAndGet(amount)
    }

    fun setGauge(name: String, value: Long, labels: Map<String, String> = emptyMap()) {
        gauges.computeIfAbsent(buildKey(name, labels)) { AtomicLong(0L) }.set(value)
    }

    fun snapshot(): Map<String, Long> =
        buildMap {
            counters.forEach { (key, value) -> put("counter:$key", value.get()) }
            gauges.forEach { (key, value) -> put("gauge:$key", value.get()) }
        }

    fun resetForTest() {
        counters.clear()
        gauges.clear()
    }

    private fun buildKey(name: String, labels: Map<String, String>): String {
        val normalizedLabels = labels.entries.sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        return if (normalizedLabels.isBlank()) name else "$name|$normalizedLabels"
    }
}
