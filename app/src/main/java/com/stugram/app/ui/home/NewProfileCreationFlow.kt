package com.stugram.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.core.masterdata.UzbekistanAdminData
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.CreateProfileRequest
import com.stugram.app.data.remote.model.RegisterRequest
import com.stugram.app.data.repository.AuthRepository
import com.stugram.app.data.repository.FollowRepository
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.data.repository.UploadOutcome
import com.stugram.app.data.repository.toUploadOutcome
import com.stugram.app.ui.auth.components.PremiumButton
import com.stugram.app.ui.auth.components.PremiumTextField
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class NewProfileStep {
    Username,
    FullName,
    Type,
    StudentDetails,
    ContactChoice,
    DifferentContactIdentity,
    DifferentContactOtp,
    Password,
    Media,
    Suggestions,
    Welcome
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun NewProfileCreationFlow(
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    RetrofitClient.init(context.applicationContext)

    val tokenManager = remember { TokenManager(context.applicationContext) }
    val authRepository = remember { AuthRepository(tokenManager) }
    val profileRepository = remember { ProfileRepository() }
    val followRepository = remember { FollowRepository() }

    val accentBlue = Color(0xFF00A3FF)
    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glass = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)

    var step by remember { mutableStateOf(NewProfileStep.Username) }
    var error by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Step state
    var username by remember { mutableStateOf("") }
    var usernameAvailable by remember { mutableStateOf(false) }
    var usernameChecking by remember { mutableStateOf(false) }
    var usernameJob: Job? by remember { mutableStateOf(null) }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    var profileType by remember { mutableStateOf("student") } // student|blogger
    val regions = remember { UzbekistanAdminData.regions(context.applicationContext) }
    fun districtsFor(region: String): List<String> = UzbekistanAdminData.districts(context.applicationContext, region)
    fun schoolsFor(region: String, district: String): List<String> =
        UzbekistanAdminData.schools(context.applicationContext, region, district)
    var region by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }

    var contactChoice by remember { mutableStateOf("same") } // same|different
    var identity by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var bannerUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingBanner by remember { mutableStateOf(false) }

    val pickAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        avatarUri = uri
    }
    val pickBannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        bannerUri = uri
    }

    var suggested by remember { mutableStateOf<List<RecommendedProfile>>(emptyList()) }
    var followedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var followBusyIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun validatePasswordFields(): String? {
        if (password.length < 8) return "Password must be at least 8 characters"
        if (password != confirmPassword) return "Passwords do not match"
        return null
    }

    fun startUsernameCheck(value: String) {
        val raw = value.trim().removePrefix("@")
        username = raw
        usernameJob?.cancel()
        usernameAvailable = false
        error = null
        if (raw.length < 3) {
            usernameChecking = false
            return
        }
        usernameChecking = true
        usernameJob = scope.launch {
            delay(450)
            val res = runCatching { profileRepository.checkUsernameAvailability(raw) }.getOrNull()
            usernameAvailable = res?.isSuccessful == true && res.body()?.data?.available == true
            usernameChecking = false
        }
    }

    suspend fun fetchSuggestions() {
        val res = runCatching { profileRepository.getProfileSuggestions(page = 1, limit = 12) }.getOrNull()
        if (res?.isSuccessful == true) {
            suggested = res.body()?.data?.map {
                RecommendedProfile(
                    id = it.id.hashCode(),
                    name = it.fullName,
                    image = it.avatar,
                    username = it.username,
                    backendId = it.id
                )
            }.orEmpty()
        }
    }

    suspend fun finishAndWelcome(name: String) {
        step = NewProfileStep.Welcome
        delay(400)
        onFinished()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    when (step) {
                        NewProfileStep.Username -> onDismiss()
                        NewProfileStep.FullName -> step = NewProfileStep.Username
                        NewProfileStep.Type -> step = NewProfileStep.FullName
                        NewProfileStep.StudentDetails -> step = NewProfileStep.Type
                        NewProfileStep.ContactChoice -> step = if (profileType == "student") NewProfileStep.StudentDetails else NewProfileStep.Type
                        NewProfileStep.DifferentContactIdentity -> step = NewProfileStep.ContactChoice
                        NewProfileStep.DifferentContactOtp -> step = NewProfileStep.DifferentContactIdentity
                        NewProfileStep.Password -> step = if (contactChoice == "different") NewProfileStep.DifferentContactOtp else NewProfileStep.ContactChoice
                        NewProfileStep.Media -> step = NewProfileStep.Password
                        NewProfileStep.Suggestions -> step = NewProfileStep.Media
                        NewProfileStep.Welcome -> onDismiss()
                    }
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = contentColor) }
                Spacer(Modifier.width(6.dp))
                Text("Create profile", color = contentColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = contentColor.copy(alpha = 0.7f)) }
            }

            error?.let {
                Text(it, color = Color.Red.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(180)) with fadeOut(tween(180)) },
                label = "new_profile_steps"
            ) { s ->
                when (s) {
                    NewProfileStep.Username -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Choose a username", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            PremiumTextField(
                                value = username,
                                onValueChange = { startUsernameCheck(it) },
                                label = "Username",
                                placeholder = "yourname",
                                leadingIcon = Icons.Rounded.Person,
                                isError = !usernameChecking && username.isNotBlank() && !usernameAvailable
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (usernameChecking) {
                                    CircularProgressIndicator(color = accentBlue, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Checking...", color = Color.Gray, fontSize = 12.sp)
                                } else if (username.isNotBlank() && username.length >= 3) {
                                    Icon(Icons.Default.CheckCircle, null, tint = if (usernameAvailable) Color.Green else Color.Red.copy(alpha = 0.8f))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (usernameAvailable) "Available" else "Taken", color = if (usernameAvailable) Color.Green else Color.Red.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                            }
                            PremiumButton(
                                text = "Next",
                                onClick = { step = NewProfileStep.FullName },
                                isLoading = false
                            )
                            LaunchedEffect(usernameAvailable) {}
                        }
                    }
                    NewProfileStep.FullName -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Your name", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it; error = null },
                                label = { Text("First name") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue),
                                shape = RoundedCornerShape(14.dp)
                            )
                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { lastName = it; error = null },
                                label = { Text("Last name") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue),
                                shape = RoundedCornerShape(14.dp)
                            )
                            PremiumButton(
                                text = "Next",
                                onClick = {
                                    if (firstName.isBlank() || lastName.isBlank()) {
                                        error = "First and last name are required"
                                    } else {
                                        step = NewProfileStep.Type
                                    }
                                },
                                isLoading = false
                            )
                        }
                    }
                    NewProfileStep.Type -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Profile type", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                TypeCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Student",
                                    subtitle = "School / education context",
                                    selected = profileType == "student",
                                    isDarkMode = isDarkMode,
                                    onClick = { profileType = "student"; error = null }
                                )
                                TypeCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Blogger",
                                    subtitle = "Personal posts, no school required",
                                    selected = profileType == "blogger",
                                    isDarkMode = isDarkMode,
                                    onClick = { profileType = "blogger"; error = null }
                                )
                            }
                            PremiumButton(
                                text = "Next",
                                onClick = {
                                    step = if (profileType == "student") NewProfileStep.StudentDetails else NewProfileStep.ContactChoice
                                },
                                isLoading = false
                            )
                        }
                    }
                    NewProfileStep.StudentDetails -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Student details", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            SimpleDropDown("Region", regions, region, onSelected = { region = it; district = ""; school = "" }, accentBlue, isDarkMode)
                            SimpleDropDown("District", districtsFor(region), district, onSelected = { district = it; school = "" }, accentBlue, isDarkMode)
                            SimpleDropDown("School", schoolsFor(region, district), school, onSelected = { school = it; error = null }, accentBlue, isDarkMode)
                            PremiumButton(
                                text = "Next",
                                onClick = {
                                    if (region.isBlank() || district.isBlank() || school.isBlank()) {
                                        error = "Region, district and school are required"
                                    } else {
                                        step = NewProfileStep.ContactChoice
                                    }
                                },
                                isLoading = false
                            )
                        }
                    }
                    NewProfileStep.ContactChoice -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Link this profile", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text(
                                "Will you create this new profile using the same email/phone as the first profile, or a different one?",
                                color = contentColor.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = { contactChoice = "same"; step = NewProfileStep.Password },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                            ) { Text("Same", fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = { contactChoice = "different"; step = NewProfileStep.DifferentContactIdentity },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, accentBlue),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentBlue)
                            ) { Text("Different", fontWeight = FontWeight.Bold) }
                        }
                    }
                    NewProfileStep.DifferentContactIdentity -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("New contact", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            OutlinedTextField(
                                value = identity,
                                onValueChange = { identity = it; error = null },
                                label = { Text("Email or phone") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue)
                            )
                            PremiumButton(
                                text = "Send code",
                                isLoading = isBusy,
                                onClick = {
                                    if (identity.isBlank()) {
                                        error = "Enter email or phone"
                                        return@PremiumButton
                                    }
                                    isBusy = true
                                    error = null
                                    scope.launch {
                                        val res = authRepository.sendOtp(identity.trim(), purpose = "register")
                                        isBusy = false
                                        when (res) {
                                            is com.stugram.app.data.remote.model.ApiResult.Success -> step = NewProfileStep.DifferentContactOtp
                                            is com.stugram.app.data.remote.model.ApiResult.Error -> error = res.message
                                        }
                                    }
                                }
                            )
                        }
                    }
                    NewProfileStep.DifferentContactOtp -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Verify", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            OutlinedTextField(
                                value = otp,
                                onValueChange = { if (it.length <= 6) otp = it },
                                label = { Text("OTP code") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue)
                            )
                            PremiumButton(
                                text = "Verify",
                                isLoading = isBusy,
                                onClick = {
                                    if (otp.length != 6) {
                                        error = "Enter a 6-digit code"
                                        return@PremiumButton
                                    }
                                    isBusy = true
                                    error = null
                                    scope.launch {
                                        val res = authRepository.verifyOtp(identity.trim(), otp, purpose = "register")
                                        isBusy = false
                                        when (res) {
                                            is com.stugram.app.data.remote.model.ApiResult.Success -> step = NewProfileStep.Password
                                            is com.stugram.app.data.remote.model.ApiResult.Error -> error = res.message
                                        }
                                    }
                                }
                            )
                        }
                    }
                    NewProfileStep.Password -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Set a password", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; error = null },
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue)
                            )
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; error = null },
                                label = { Text("Confirm password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue)
                            )
                            PremiumButton(
                                text = "Next",
                                isLoading = isBusy,
                                onClick = {
                                    val v = validatePasswordFields()
                                    if (v != null) {
                                        error = v
                                    } else {
                                        isBusy = true
                                        error = null
                                        scope.launch {
                                            try {
                                                if (contactChoice == "same") {
                                                    val response = profileRepository.createProfile(
                                                        CreateProfileRequest(
                                                            username = username,
                                                            firstName = firstName,
                                                            lastName = lastName,
                                                            type = profileType,
                                                            region = if (profileType == "student") region else null,
                                                            district = if (profileType == "student") district else null,
                                                            school = if (profileType == "student") school else null,
                                                            password = password
                                                        )
                                                    )
                                                    val auth = response.body()?.data
                                                    if (response.isSuccessful && auth != null) {
                                                        tokenManager.saveSession(auth.user, auth.accessToken, auth.refreshToken)
                                                        step = NewProfileStep.Media
                                                    } else {
                                                        error = response.body()?.message ?: response.message().ifBlank { "Profile creation failed" }
                                                    }
                                                } else {
                                                    // Different contact: register new account identity (OTP verified already)
                                                    val response = authRepository.register(
                                                        RegisterRequest(
                                                            identity = identity.trim(),
                                                            otp = otp,
                                                            fullName = "${firstName.trim()} ${lastName.trim()}".trim(),
                                                            username = username,
                                                            password = password,
                                                            type = profileType,
                                                            region = if (profileType == "student") region else null,
                                                            district = if (profileType == "student") district else null,
                                                            school = if (profileType == "student") school else null,
                                                            bio = null,
                                                            isPrivateAccount = false
                                                        )
                                                    )
                                                    when (response) {
                                                        is com.stugram.app.data.remote.model.ApiResult.Success -> {
                                                            val payload = response.data
                                                            if (payload != null) {
                                                                tokenManager.addAccountSession(payload.user, payload.accessToken, payload.refreshToken)
                                                                step = NewProfileStep.Media
                                                            } else {
                                                                error = "Registration response was empty"
                                                            }
                                                        }
                                                        is com.stugram.app.data.remote.model.ApiResult.Error -> error = response.message
                                                    }
                                                }
                                            } finally {
                                                isBusy = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    NewProfileStep.Media -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Profile media", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("Pick a banner and a profile picture. Uploads use your existing endpoints.", color = contentColor.copy(alpha = 0.7f), fontSize = 13.sp)

                            Surface(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = glass,
                                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            pickBannerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Image, null, tint = contentColor.copy(alpha = 0.5f))
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            bannerUri?.toString()?.takeIf { it.isNotBlank() } ?: "Pick banner",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = glass,
                                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            pickAvatarLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        avatarUri?.toString()?.takeIf { it.isNotBlank() } ?: "Pick avatar",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    val uri = avatarUri ?: return@OutlinedButton
                                    if (isUploadingAvatar) return@OutlinedButton
                                    scope.launch {
                                        isUploadingAvatar = true
                                        try {
                                            when (val outcome = profileRepository.uploadAvatar(context, uri).toUploadOutcome("Avatar upload failed")) {
                                                is UploadOutcome.Success -> {
                                                    tokenManager.updateCurrentUser(outcome.data)
                                                    avatarUri = null
                                                    error = null
                                                }
                                                is UploadOutcome.Failure -> {
                                                    error = outcome.message
                                                }
                                            }
                                        } catch (e: Exception) {
                                            error = e.localizedMessage ?: "Avatar upload failed"
                                        }
                                        isUploadingAvatar = false
                                    }
                                },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, accentBlue)
                                ) {
                                    if (isUploadingAvatar) {
                                        CircularProgressIndicator(color = accentBlue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    } else {
                                        Text("Upload avatar", color = accentBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            OutlinedButton(
                                onClick = {
                                    val uri = bannerUri ?: return@OutlinedButton
                                    if (isUploadingBanner) return@OutlinedButton
                                    scope.launch {
                                        isUploadingBanner = true
                                        try {
                                            when (val outcome = profileRepository.uploadBanner(context, uri).toUploadOutcome("Banner upload failed")) {
                                                is UploadOutcome.Success -> {
                                                    tokenManager.updateCurrentUser(outcome.data)
                                                    bannerUri = null
                                                    error = null
                                                }
                                                is UploadOutcome.Failure -> {
                                                    error = outcome.message
                                                }
                                            }
                                        } catch (e: Exception) {
                                            error = e.localizedMessage ?: "Banner upload failed"
                                        }
                                        isUploadingBanner = false
                                    }
                                },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, accentBlue)
                                ) {
                                    if (isUploadingBanner) {
                                        CircularProgressIndicator(color = accentBlue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    } else {
                                        Text("Upload banner", color = accentBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            PremiumButton(
                                text = "Continue",
                                isLoading = isUploadingAvatar || isUploadingBanner,
                                onClick = { step = NewProfileStep.Suggestions }
                            )
                        }
                    }
                    NewProfileStep.Suggestions -> {
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(Unit) { fetchSuggestions() }
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Suggested follows", color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Spacer(Modifier.height(10.dp))
                            if (suggested.isEmpty()) {
                                Text("No suggestions right now.", color = Color.Gray)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f, fill = false)) {
                                    items(suggested, key = { it.backendId ?: it.username }) { profile ->
                                        val id = profile.backendId ?: return@items
                                        val isFollowed = followedIds.contains(id)
                                        val busy = followBusyIds.contains(id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(glass)
                                                .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AppAvatar(
                                                imageModel = profile.image,
                                                name = profile.name,
                                                username = profile.username,
                                                modifier = Modifier.size(44.dp).border(1.dp, accentBlue.copy(0.25f), RoundedCornerShape(22.dp)),
                                                isDarkMode = isDarkMode,
                                                fontSize = 16.sp
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(profile.name, color = contentColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("@${profile.username}", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Button(
                                                onClick = {
                                                    if (busy) return@Button
                                                    followBusyIds = followBusyIds + id
                                                    scope.launch {
                                                        val res = runCatching {
                                                            if (isFollowed) followRepository.unfollowUser(id) else followRepository.followUser(id)
                                                        }.getOrNull()
                                                        if (res?.isSuccessful == true) {
                                                            followedIds = if (isFollowed) followedIds - id else followedIds + id
                                                        }
                                                        followBusyIds = followBusyIds - id
                                                    }
                                                },
                                                enabled = !busy,
                                                colors = ButtonDefaults.buttonColors(containerColor = if (isFollowed) Color.Transparent else accentBlue),
                                                border = if (isFollowed) BorderStroke(1.dp, accentBlue) else null,
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp)
                                            ) {
                                                Text(if (isFollowed) "Following" else "Follow", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isFollowed) accentBlue else Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { step = NewProfileStep.Welcome; scope.launch { finishAndWelcome("$firstName $lastName") } },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, accentBlue)
                                ) { Text("Skip", fontWeight = FontWeight.Bold, color = accentBlue) }
                                Button(
                                    onClick = { step = NewProfileStep.Welcome; scope.launch { finishAndWelcome("$firstName $lastName") } },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                                ) { Text("Done", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    NewProfileStep.Welcome -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                accentBlue.copy(alpha = 0.18f),
                                                glass
                                            )
                                        )
                                    )
                                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Welcome, $firstName", color = contentColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Your profile is ready.", color = contentColor.copy(alpha = 0.75f), fontSize = 13.sp)
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = onFinished,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) { Text("Go to Home", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val accentBlue = Color(0xFF00A3FF)
    val bg = if (isDarkMode) Color.White.copy(alpha = if (selected) 0.12f else 0.06f) else Color.Black.copy(alpha = if (selected) 0.06f else 0.03f)
    val border = if (selected) accentBlue.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.2f)
    val content = if (isDarkMode) Color.White else Color.Black

    Column(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Text(title, color = content, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = content.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropDown(
    label: String,
    items: List<String>,
    value: String,
    onSelected: (String) -> Unit,
    accentBlue: Color,
    isDarkMode: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val contentColor = if (isDarkMode) Color.White else Color.Black
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBlue),
            shape = RoundedCornerShape(14.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = contentColor) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}
