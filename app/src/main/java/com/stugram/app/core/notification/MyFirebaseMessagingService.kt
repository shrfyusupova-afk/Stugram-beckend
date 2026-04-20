package com.stugram.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stugram.app.BuildConfig
import com.stugram.app.MainActivity
import com.stugram.app.R
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.model.RegisterPushTokenRequest
import com.stugram.app.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val deviceRepository = DeviceRepository()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        sendTokenToBackend(token)
    }

    private fun sendTokenToBackend(token: String) {
        val tokenManager = TokenManager(applicationContext)
        scope.launch {
            val user = tokenManager.getCurrentUser()
            if (user != null) {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                try {
                    deviceRepository.registerPushToken(
                        RegisterPushTokenRequest(
                            token = token,
                            deviceId = deviceId,
                            appVersion = BuildConfig.VERSION_NAME
                        )
                    )
                    Log.i("FCM", "Push token registered for user=${user.id} deviceId=$deviceId")
                } catch (e: Exception) {
                    Log.e("FCM", "Error registering token", e)
                }
            } else {
                Log.i("FCM", "Skipping token registration because no active user is available yet")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "From: ${remoteMessage.from}")

        val data = remoteMessage.data
        val pushEvent = PushNotificationEvent(
            type = data["type"] ?: data["notificationType"],
            conversationId = data["conversationId"],
            groupId = data["groupId"],
            messageId = data["messageId"],
            targetId = data["targetId"]
        )
        if (pushEvent.conversationId != null || pushEvent.groupId != null) {
            PushNotificationBus.emit(pushEvent)
        }

        val isForeground = runCatching {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }.getOrDefault(false)
        val activeConversationId = ForegroundChatRegistry.activeConversationId
        val activeGroupId = ForegroundChatRegistry.activeGroupId
        val isRelevantOpenChat =
            (pushEvent.conversationId != null && pushEvent.conversationId == activeConversationId) ||
                (pushEvent.groupId != null && pushEvent.groupId == activeGroupId)

        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")
            if (!isForeground || !isRelevantOpenChat) {
                showNotification(remoteMessage)
            }
            return
        }

        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
            if (!isForeground || !isRelevantOpenChat) {
                showNotification(remoteMessage)
            }
        }
    }

    private fun showNotification(remoteMessage: RemoteMessage) {
        val channelId = "default_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            remoteMessage.data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Notification"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.appicon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
