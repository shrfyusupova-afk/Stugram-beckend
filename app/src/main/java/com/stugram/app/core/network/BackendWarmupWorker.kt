package com.stugram.app.core.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stugram.app.BuildConfig
import com.stugram.app.core.observability.ChatReliabilityLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class BackendWarmupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(resolveLiveUrl())
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    setRequestProperty("User-Agent", "StuGram-Android-Warmup")
                }
                try {
                    val code = connection.responseCode
                    if (code in 200..499) Result.success() else Result.retry()
                } finally {
                    connection.disconnect()
                }
            }.getOrElse {
                Result.retry()
            }
        }
    }

    private fun resolveLiveUrl(): String {
        val base = BuildConfig.API_BASE_URL
            .removeSuffix("/")
            .removeSuffix("/api/v1")
            .removeSuffix("/api")
        return "$base/livez"
    }
}

object BackendWarmupScheduler {
    private const val ONE_TIME_WORK = "stugram_backend_warmup_once"
    private const val PERIODIC_WORK = "stugram_backend_warmup_periodic"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediate = OneTimeWorkRequestBuilder<BackendWarmupWorker>()
            .setConstraints(constraints)
            .build()

        val periodic = PeriodicWorkRequestBuilder<BackendWarmupWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, immediate)
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }
}

object BackendKeepAlive {
    private const val FOREGROUND_INTERVAL_MS = 120_000L
    private const val MIN_MANUAL_PING_GAP_MS = 25_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var job: Job? = null

    @Volatile
    private var lastPingAtMs: Long = 0L

    fun start(context: Context) {
        BackendWarmupScheduler.schedule(context.applicationContext)
        if (job?.isActive == true) return

        job = scope.launch {
            ping("foreground_start", force = true)
            while (isActive) {
                delay(FOREGROUND_INTERVAL_MS)
                ping("foreground_loop", force = true)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun pingSoon(reason: String) {
        scope.launch {
            ping(reason, force = false)
        }
    }

    private suspend fun ping(reason: String, force: Boolean) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!force && now - lastPingAtMs < MIN_MANUAL_PING_GAP_MS) return@withContext
            lastPingAtMs = now

            runCatching {
                val connection = (URL(resolveLiveUrl()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    setRequestProperty("User-Agent", "StuGram-Android-Foreground")
                }
                try {
                    val code = connection.responseCode
                    if (code !in 200..499) {
                        ChatReliabilityLogger.warn(
                            "backend_keepalive_unhealthy",
                            mapOf("reason" to reason, "httpStatus" to code)
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error ->
                ChatReliabilityLogger.warn(
                    "backend_keepalive_failed",
                    mapOf("reason" to reason, "errorName" to error::class.java.simpleName)
                )
            }
        }
    }

    private fun resolveLiveUrl(): String {
        val base = BuildConfig.API_BASE_URL
            .removeSuffix("/")
            .removeSuffix("/api/v1")
            .removeSuffix("/api")
        return "$base/livez"
    }
}
