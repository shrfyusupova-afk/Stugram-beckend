package com.stugram.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(isDarkMode: Boolean, accentBlue: Color, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotMessage by remember { mutableStateOf("Sending reset instructions...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Change Password", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            
            OutlinedTextField(
                value = oldPass,
                onValueChange = { oldPass = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Current password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue, unfocusedBorderColor = contentColor.copy(0.1f)),
                shape = RoundedCornerShape(12.dp)
            )
            
            TextButton(
                onClick = {
                    showForgotDialog = true
                    forgotMessage = "Sending reset instructions..."
                    viewModel.forgotPassword { success, message ->
                        forgotMessage = if (success) {
                            message ?: "Password reset instructions were sent if this account supports password reset."
                        } else {
                            message ?: "Could not start password reset."
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Forgot password?", color = accentBlue, fontSize = 13.sp)
            }
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = newPass,
                onValueChange = { newPass = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue, unfocusedBorderColor = contentColor.copy(0.1f)),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPass,
                onValueChange = { confirmPass = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm new password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue, unfocusedBorderColor = contentColor.copy(0.1f)),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = {
                    if (newPass != confirmPass) {
                        errorMessage = "Passwords do not match"
                        return@Button
                    }
                    isLoading = true
                    viewModel.changePassword(oldPass, newPass) { success, error ->
                        isLoading = false
                        if (success) {
                            onBack()
                        } else {
                            errorMessage = error ?: "Failed to change password"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading && oldPass.isNotEmpty() && newPass.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Update Password", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            confirmButton = { TextButton(onClick = { showForgotDialog = false }) { Text("OK") } },
            title = { Text("Reset Password") },
            text = { Text(forgotMessage) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorAuthScreen(isDarkMode: Boolean, accentBlue: Color, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    var isEnabled by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("2FA Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Icon(Icons.Rounded.Security, null, tint = accentBlue, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(24.dp))
            Text("Two-Factor Authentication", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = contentColor)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add an extra layer of protection to your account. You'll be asked for a code every time you sign in from a new device.",
                color = Color.Gray,
                fontSize = 14.sp
            )
            
            Spacer(Modifier.height(32.dp))
            
            Surface(
                onClick = { isEnabled = !isEnabled },
                color = contentColor.copy(0.03f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Enable 2FA", fontWeight = FontWeight.Bold, color = contentColor)
                        Text(if (isEnabled) "Currently enabled" else "Currently disabled", color = if (isEnabled) Color.Green else Color.Gray, fontSize = 12.sp)
                    }
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentBlue))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginActivityScreen(isDarkMode: Boolean, accentBlue: Color, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    
    val logins by viewModel.loginSessions.collectAsState()
    val loginError by viewModel.loginSessionsError.collectAsState()

    fun deviceLabel(userAgent: String?, deviceId: String?): String {
        val ua = (userAgent ?: "").lowercase()
        return when {
            ua.contains("android") -> "Android"
            ua.contains("iphone") || ua.contains("ios") -> "iPhone"
            ua.contains("mac os") -> "Mac"
            ua.contains("windows") -> "Windows"
            ua.contains("linux") -> "Linux"
            deviceId != null -> "Device ${deviceId.take(6)}"
            else -> "Unknown device"
        }
    }

    fun timeLabel(value: String?): String = value?.replace("T", " ")?.substringBefore(".") ?: "Unknown time"

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Login activity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = contentColor, navigationIconContentColor = contentColor)
            )
        }
    ) { padding ->
        if (!loginError.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load sessions", color = Color.Red.copy(alpha = 0.85f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(loginError!!, color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.loadAll() }) { Text("Retry", color = accentBlue) }
                }
            }
        } else if (logins.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No active sessions found", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Devices logged into your account", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))
                }
                items(logins) { login ->
                    val label = deviceLabel(login.userAgent, login.deviceId)
                    val isCurrent = login.isCurrent
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (label.contains("android", ignoreCase = true) || label.contains("iphone", ignoreCase = true)) Icons.Rounded.Smartphone else Icons.Rounded.Laptop,
                            null,
                            tint = if (isCurrent) Color.Green else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(label, fontWeight = FontWeight.Bold, color = contentColor)
                            val ip = login.ipAddress?.takeIf { it.isNotBlank() } ?: "Unknown IP"
                            val last = if (isCurrent) "Active now" else timeLabel(login.lastUsedAt)
                            Text("$ip • $last", color = Color.Gray, fontSize = 12.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        if (!isCurrent) {
                            IconButton(onClick = { viewModel.revokeSession(login.sessionId) }) {
                                Icon(Icons.Default.Close, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = contentColor.copy(0.05f))
                }
            }
        }
    }
}
