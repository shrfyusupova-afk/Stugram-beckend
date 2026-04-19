package com.stugram.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.AccountProfileItemModel
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.ui.auth.components.LoadingOverlay
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherModal(
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onSwitched: () -> Unit,
    onAddProfile: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val accentBlue = Color(0xFF00A3FF)
    val sheetColor = if (isDarkMode) Color(0xFF121212) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glass = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)

    val sessions by tokenManager.sessions.collectAsState(initial = emptyList())
    val activeSessionId by tokenManager.activeSessionId.collectAsState(initial = null)
    var isSwitching by remember { mutableStateOf(false) }

    // Fetch profiles under active account for display (optional; safe if endpoint is unavailable yet)
    val profileRepository = remember {
        RetrofitClient.init(context.applicationContext)
        ProfileRepository()
    }
    var profiles by remember { mutableStateOf<List<AccountProfileItemModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        val response = runCatching { profileRepository.getMyProfilesAll() }.getOrNull()
        if (response?.isSuccessful == true) {
            profiles = response.body()?.data.orEmpty()
        } else {
            error = response?.message()?.ifBlank { "Could not load profiles" } ?: "Could not load profiles"
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetColor,
        modifier = Modifier.fillMaxHeight(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = contentColor.copy(alpha = 0.25f)) }
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SwapHoriz, null, tint = accentBlue)
                Spacer(Modifier.width(10.dp))
                Text("Switch profile", color = contentColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }

            Spacer(Modifier.height(12.dp))

            if (sessions.isNotEmpty()) {
                Text("Accounts", color = contentColor.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val isActive = session.id == activeSessionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(glass)
                                .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                                .clickable {
                                    scope.launch {
                                        isSwitching = true
                                        val startedAt = System.currentTimeMillis()
                                        val ok = tokenManager.switchToSession(session.id)
                                        if (ok) {
                                            val elapsed = System.currentTimeMillis() - startedAt
                                            if (elapsed < 2800) delay(2800 - elapsed)
                                            onSwitched()
                                        } else {
                                            isSwitching = false
                                        }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppAvatar(
                                imageModel = session.user.avatar,
                                name = session.user.fullName,
                                username = session.user.username,
                                modifier = Modifier.size(44.dp).border(1.dp, accentBlue.copy(0.25f), CircleShape),
                                isDarkMode = isDarkMode,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.user.fullName, color = contentColor, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("@${session.user.username}", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                            }
                            if (isActive) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color.Green.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Profiles", color = contentColor.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = accentBlue, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
                !error.isNullOrBlank() -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = Color.Red.copy(alpha = 0.85f), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Retry", color = accentBlue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                            scope.launch {
                                isLoading = true
                                error = null
                                val response = runCatching { profileRepository.getMyProfilesAll() }.getOrNull()
                                if (response?.isSuccessful == true) {
                                    profiles = response.body()?.data.orEmpty()
                                } else {
                                    error = response?.message()?.ifBlank { "Could not load profiles" } ?: "Could not load profiles"
                                }
                                isLoading = false
                            }
                        })
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(glass, Color.Transparent)
                                        )
                                    )
                                    .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                                    .clickable {
                                        scope.launch {
                                            isSwitching = true
                                            val startedAt = System.currentTimeMillis()
                                            val result = runCatching { profileRepository.switchProfile(profile.id) }.getOrNull()
                                            val auth = result?.body()?.data
                                            if (result?.isSuccessful == true && auth != null) {
                                                tokenManager.saveSession(auth.user, auth.accessToken, auth.refreshToken)
                                                val elapsed = System.currentTimeMillis() - startedAt
                                                if (elapsed < 2800) delay(2800 - elapsed)
                                                onSwitched()
                                            } else {
                                                isSwitching = false
                                            }
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppAvatar(
                                    imageModel = profile.avatar,
                                    name = profile.fullName,
                                    username = profile.username,
                                    modifier = Modifier.size(44.dp).border(1.dp, accentBlue.copy(0.25f), CircleShape),
                                    isDarkMode = isDarkMode,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.fullName, color = contentColor, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text("@${profile.username} • ${profile.type}", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }

                        item(key = "add_profile") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(glass)
                                    .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                                    .clickable { onAddProfile() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(accentBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, null, tint = accentBlue)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("+ Add Profile", color = contentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

                Spacer(Modifier.height(16.dp))
            }

            if (isSwitching) {
                LoadingOverlay(fullScreen = true, message = "Switching profile")
            }
        }
    }
}
