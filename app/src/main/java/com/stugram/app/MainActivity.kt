package com.stugram.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.stugram.app.navigation.AuthNavGraph
import com.stugram.app.navigation.Screen
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.ui.theme.MyApplicationTheme
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    private var navController: NavHostController? = null
    private var lastHandledRoutingKey: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("FCM", "Notification permission granted")
        } else {
            Log.d("FCM", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        printSHA1()
        RetrofitClient.init(applicationContext)
        enableEdgeToEdge()
        askNotificationPermission()
        
        setContent {
            val systemTheme = isSystemInDarkTheme()
            val isDarkMode = rememberSaveable { mutableStateOf(systemTheme) }
            val currentNavController = rememberNavController()
            navController = currentNavController

            MyApplicationTheme(darkTheme = isDarkMode.value) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthNavGraph(
                        navController = currentNavController,
                        isDarkMode = isDarkMode.value,
                        onThemeChange = { isDarkMode.value = it }
                    )
                }
            }

            LaunchedEffect(intent) {
                handleIntent(intent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val type = it.getStringExtra("type") ?: it.getStringExtra("notificationType")
            val targetId = it.getStringExtra("targetId") ?: it.getStringExtra("id")
            val title = it.getStringExtra("title") ?: it.getStringExtra("userName") ?: "User"
            val username = it.getStringExtra("username") ?: it.getStringExtra("userName")

            Log.d("NotificationRouting", "Handling intent: type=$type, targetId=$targetId, title=$title")

            val routingKey = listOfNotNull(type, targetId, title).joinToString("|")
            if (routingKey == lastHandledRoutingKey) return

            if (!type.isNullOrBlank() && !targetId.isNullOrBlank()) {
                lastHandledRoutingKey = routingKey
                when (type) {
                    "chat", "message" -> {
                        navController?.navigate(Screen.ChatDetail.createRoute(targetId, title.ifBlank { username ?: "Chat" }))
                    }
                    "group_chat" -> {
                        navController?.navigate(Screen.GroupChatDetail.createRoute(targetId, title.ifBlank { "Group" }))
                    }
                    "post", "like", "comment" -> {
                        navController?.navigate(Screen.PostDetail.createRoute(targetId))
                    }
                    "follow", "follow_request" -> {
                        // For now just navigate home or to a specific profile if we have it
                        // Could be Screen.Profile.createRoute(targetId) if it existed
                    }
                }
            } else {
                Log.w("NotificationRouting", "Skipping routing due to missing fields: type=$type targetId=$targetId")
            }
        }
    }

    private fun printSHA1() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                if (signingInfo != null) {
                    val signatures = if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                    if (signatures != null) {
                        for (signature in signatures) {
                            val md = MessageDigest.getInstance("SHA")
                            md.update(signature.toByteArray())
                            val sha1 = md.digest().joinToString(":") { "%02X".format(it) }
                            Log.d("MY_SHA1", "SHA-1: $sha1")
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.forEach { signature ->
                    val md = MessageDigest.getInstance("SHA")
                    md.update(signature.toByteArray())
                    val sha1 = md.digest().joinToString(":") { "%02X".format(it) }
                    Log.d("MY_SHA1", "SHA-1: $sha1")
                }
            }
        } catch (e: Exception) {
            Log.e("MY_SHA1", "Error printing SHA-1", e)
        }
    }
}
