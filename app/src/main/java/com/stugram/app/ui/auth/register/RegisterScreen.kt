package com.stugram.app.ui.auth.register

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stugram.app.ui.auth.components.AuthDropdownField
import com.stugram.app.ui.auth.components.LoadingOverlay
import com.stugram.app.ui.auth.components.OtpInputField
import com.stugram.app.ui.auth.components.PremiumButton
import com.stugram.app.ui.auth.components.PremiumTextField
import com.stugram.app.ui.home.AppAvatar
import com.stugram.app.ui.home.AppBanner
import com.stugram.app.ui.home.RecommendedProfile
import com.stugram.app.ui.theme.PremiumTextPrimary
import com.stugram.app.ui.theme.PremiumTextSecondary

@Composable
fun RegisterContent(
    viewModel: RegisterViewModel,
    uiState: RegisterUiState,
    onNavigateToLogin: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val accentBlue = Color(0xFF00A3FF)
    val successGreen = Color(0xFF00C853)
    val onboardingStep = (uiState.currentStep - 6).coerceIn(1, 4)
    val isOnboarding = uiState.currentStep >= 7

    val profilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.updateField(RegisterField.ProfileImage(uri))
                viewModel.uploadProfileImage()
            }
        }
    )

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.updateField(RegisterField.BannerImage(uri))
                viewModel.uploadBannerImage()
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isOnboarding) {
            OnboardingStepHeader(
                step = onboardingStep,
                totalSteps = 4,
                title = when (uiState.currentStep) {
                    7 -> "Add a profile photo"
                    8 -> "Add a banner"
                    9 -> "Choose your username"
                    else -> "Follow people you know"
                },
                subtitle = when (uiState.currentStep) {
                    7 -> "A real photo helps people recognize you right away."
                    8 -> "Give your profile a clean cover image. You can skip this too."
                    9 -> "Let's make sure your username is valid and available before you enter the app."
                    else -> "Follow a few real accounts now, or skip and do it later."
                }
            )
        }

        when (uiState.currentStep) {
            1 -> {
                Text("Create Account", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                Spacer(modifier = Modifier.height(24.dp))
                PremiumTextField(
                    value = uiState.identity,
                    onValueChange = viewModel::onIdentityChange,
                    label = "Email or Phone",
                    placeholder = "example@gmail.com / +998...",
                    leadingIcon = Icons.Default.Email,
                    isError = uiState.error != null
                )
                Spacer(modifier = Modifier.height(24.dp))
                PremiumButton(text = "Send Code", onClick = viewModel::sendOtp, isLoading = uiState.isLoading)
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onNavigateToLogin) {
                    Text("Already have an account? Log in", color = PremiumTextSecondary)
                }
            }

            2 -> {
                Text("Verification", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Enter the code sent to ${uiState.identity}",
                    color = PremiumTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                if (uiState.form.otpResendSecondsRemaining > 0) {
                    Text(
                        text = "Resend available in ${uiState.form.otpResendSecondsRemaining}s",
                        color = PremiumTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                } else {
                    Text(
                        text = "You can resend the code now.",
                        color = PremiumTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                OtpInputField(otpText = uiState.otp, onOtpTextChange = { code, _ -> viewModel.onOtpChange(code) })
                Spacer(modifier = Modifier.height(32.dp))
                PremiumButton(text = "Verify", onClick = viewModel::verifyOtp, isLoading = uiState.isLoading)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.prevStep() }) {
                        Text("Go Back", color = PremiumTextSecondary)
                    }
                    TextButton(
                        onClick = viewModel::resendOtp,
                        enabled = uiState.form.otpResendSecondsRemaining <= 0 && !uiState.isLoading
                    ) {
                        Text("Resend", color = accentBlue)
                    }
                }
            }

            3 -> {
                Text("Basic Info", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                Spacer(modifier = Modifier.height(24.dp))
                PremiumTextField(
                    value = uiState.fullName,
                    onValueChange = { viewModel.updateField(RegisterField.FullName(it)) },
                    label = "Full Name",
                    placeholder = "Your full name",
                    leadingIcon = Icons.Default.Person
                )
                Spacer(modifier = Modifier.height(12.dp))
                PremiumTextField(
                    value = uiState.username,
                    onValueChange = { viewModel.updateField(RegisterField.Username(it)) },
                    label = "Username",
                    placeholder = "@username",
                    leadingIcon = Icons.Default.AlternateEmail,
                    supportingText = { UsernameStatusText(uiState.form.usernameStatus, accentBlue, successGreen) }
                )
                Spacer(modifier = Modifier.height(24.dp))
                PremiumButton(
                    text = "Next",
                    onClick = { viewModel.nextStep() },
                    enabled = uiState.form.usernameStatus != UsernameStatus.Taken && uiState.username.length >= 3
                )
            }

            4 -> {
                Text("Profile Type", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Choose Student or Blogger. Student requires school details.", color = PremiumTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    TypeChip(
                        modifier = Modifier.weight(1f),
                        title = "Student",
                        selected = uiState.form.profileType == "student",
                        accentBlue = accentBlue,
                        onClick = { viewModel.updateField(RegisterField.ProfileType("student")) }
                    )
                    TypeChip(
                        modifier = Modifier.weight(1f),
                        title = "Blogger",
                        selected = uiState.form.profileType == "blogger",
                        accentBlue = accentBlue,
                        onClick = { viewModel.updateField(RegisterField.ProfileType("blogger")) }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                PremiumButton(text = "Next", onClick = { viewModel.nextStep() })
            }

            5 -> {
                Text("Location and School", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        AuthDropdownField(
                            value = uiState.selectedRegion,
                            onValueChange = { viewModel.updateField(RegisterField.Region(it)) },
                            label = "Region",
                            options = viewModel.regions
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AuthDropdownField(
                            value = uiState.selectedDistrict,
                            onValueChange = { viewModel.updateField(RegisterField.District(it)) },
                            label = "District",
                            options = viewModel.getDistricts(uiState.selectedRegion)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                AuthDropdownField(
                    value = uiState.selectedSchool,
                    onValueChange = { viewModel.updateField(RegisterField.School(it)) },
                    label = "School / University",
                    options = viewModel.getSchools(uiState.selectedRegion, uiState.selectedDistrict),
                    leadingIcon = Icons.Default.School
                )
                Spacer(modifier = Modifier.height(24.dp))
                PremiumButton(text = "Next", onClick = { viewModel.nextStep() })
            }

            6 -> {
                Text("Security", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                Spacer(modifier = Modifier.height(24.dp))
                PremiumTextField(
                    value = uiState.password,
                    onValueChange = { viewModel.updateField(RegisterField.Password(it)) },
                    label = "Password",
                    placeholder = "At least 8 characters",
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = Icons.Default.Lock,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                PremiumTextField(
                    value = uiState.confirmPassword,
                    onValueChange = { viewModel.updateField(RegisterField.ConfirmPassword(it)) },
                    label = "Confirm Password",
                    placeholder = "Enter your password again",
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = Icons.Default.LockReset
                )
                Spacer(modifier = Modifier.height(24.dp))
                PremiumButton(text = "Create account", onClick = viewModel::register, isLoading = uiState.isLoading)
            }

            7 -> {
                PhotoOnboardingStep(
                    imageModel = uiState.profileImageUri,
                    title = uiState.fullName.ifBlank { uiState.username.ifBlank { "Your profile" } },
                    username = uiState.username.removePrefix("@"),
                    accentBlue = accentBlue,
                    isUploading = uiState.form.isUploadingProfile,
                    onPick = {
                        profilePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onContinue = viewModel::continueFromOnboarding,
                    onSkip = viewModel::skipOnboardingStep
                )
            }

            8 -> {
                BannerOnboardingStep(
                    imageModel = uiState.bannerImageUri,
                    title = uiState.fullName.ifBlank { uiState.username.ifBlank { "Your banner" } },
                    accentBlue = accentBlue,
                    isUploading = uiState.form.isUploadingBanner,
                    onPick = {
                        bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onContinue = viewModel::continueFromOnboarding,
                    onSkip = viewModel::skipOnboardingStep
                )
            }

            9 -> {
                UsernameOnboardingStep(
                    username = uiState.username.removePrefix("@"),
                    usernameStatus = uiState.form.usernameStatus,
                    originalUsername = uiState.form.originalUsername,
                    isSaving = uiState.form.isSavingUsername,
                    accentBlue = accentBlue,
                    successGreen = successGreen,
                    onUsernameChange = { viewModel.updateField(RegisterField.Username(it.removePrefix("@"))) },
                    onContinue = viewModel::continueFromOnboarding,
                    onSkip = viewModel::skipOnboardingStep
                )
            }

            10 -> {
                SuggestionsOnboardingStep(
                    profiles = uiState.recommendedProfiles,
                    followedUserIds = uiState.form.followedUserIds,
                    followLoadingIds = uiState.form.followLoadingIds,
                    isLoading = uiState.form.isRecommendationsLoading,
                    accentBlue = accentBlue,
                    onFollowToggle = viewModel::toggleFollowUser,
                    onFinish = viewModel::continueFromOnboarding,
                    onSkip = viewModel::skipOnboardingStep
                )
            }
        }

        uiState.error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = it, color = Color.Red, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RowScope.TypeChip(
    modifier: Modifier = Modifier,
    title: String,
    selected: Boolean,
    accentBlue: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) accentBlue else Color.White.copy(alpha = 0.05f)
    val border = if (selected) Color.Transparent else Color.White.copy(alpha = 0.1f)
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun OnboardingStepHeader(
    step: Int,
    totalSteps: Int,
    title: String,
    subtitle: String
) {
    val accentBlue = Color(0xFF00A3FF)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Onboarding", color = PremiumTextSecondary, fontSize = 12.sp)
                Text("Step $step of $totalSteps", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { step / totalSteps.toFloat() },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)),
                color = accentBlue,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, color = PremiumTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = PremiumTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun PhotoOnboardingStep(
    imageModel: Any?,
    title: String,
    username: String,
    accentBlue: Color,
    isUploading: Boolean,
    onPick: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppAvatar(
                imageModel = imageModel,
                name = title,
                username = username,
                modifier = Modifier
                    .size(136.dp)
                    .border(2.dp, accentBlue, CircleShape)
                    .clickable(onClick = onPick),
                borderColor = accentBlue
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(title, color = PremiumTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (username.isBlank()) "Choose a clear photo for your profile." else "@$username",
                color = PremiumTextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onPick,
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Image, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose photo")
            }
            Spacer(modifier = Modifier.height(18.dp))
            PremiumButton(
                text = if (imageModel != null) "Continue" else "Continue without photo",
                onClick = onContinue,
                isLoading = isUploading,
                enabled = !isUploading
            )
            TextButton(onClick = onSkip, enabled = !isUploading) {
                Text("Skip for now", color = accentBlue)
            }
        }
    }
}

@Composable
private fun BannerOnboardingStep(
    imageModel: Any?,
    title: String,
    accentBlue: Color,
    isUploading: Boolean,
    onPick: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            AppBanner(
                imageModel = imageModel,
                title = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onPick),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Banner", color = PremiumTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Keep it simple and clean. You can always update this later from Edit Profile.",
                color = PremiumTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onPick,
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Image, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (imageModel != null) "Change banner" else "Choose banner")
            }
            Spacer(modifier = Modifier.height(18.dp))
            PremiumButton(
                text = if (imageModel != null) "Continue" else "Continue without banner",
                onClick = onContinue,
                isLoading = isUploading,
                enabled = !isUploading
            )
            TextButton(onClick = onSkip, enabled = !isUploading) {
                Text("Skip for now", color = accentBlue)
            }
        }
    }
}

@Composable
private fun UsernameOnboardingStep(
    username: String,
    usernameStatus: UsernameStatus,
    originalUsername: String,
    isSaving: Boolean,
    accentBlue: Color,
    successGreen: Color,
    onUsernameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            PremiumTextField(
                value = username,
                onValueChange = { onUsernameChange(it.removePrefix("@")) },
                label = "Username",
                placeholder = "username",
                leadingIcon = Icons.Default.AlternateEmail,
                supportingText = { UsernameStatusText(usernameStatus, accentBlue, successGreen) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (originalUsername.isNotBlank()) "Current handle: @$originalUsername" else "Choose a memorable handle.",
                color = PremiumTextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            PremiumButton(
                text = "Continue",
                onClick = onContinue,
                isLoading = isSaving,
                enabled = !isSaving && username.length >= 3 && usernameStatus != UsernameStatus.Taken && usernameStatus != UsernameStatus.Checking
            )
            TextButton(onClick = onSkip, enabled = !isSaving) {
                Text("Keep current username", color = accentBlue)
            }
        }
    }
}

@Composable
private fun SuggestionsOnboardingStep(
    profiles: List<RecommendedProfile>,
    followedUserIds: Set<String>,
    followLoadingIds: Set<String>,
    isLoading: Boolean,
    accentBlue: Color,
    onFollowToggle: (String) -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentBlue)
                }
            } else if (profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoAwesome, null, tint = accentBlue)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No suggestions right now", color = PremiumTextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("You can start exploring the app and follow people later.", color = PremiumTextSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(profiles, key = { it.backendId ?: it.id }) { profile ->
                        RecommendationRow(
                            profile = profile,
                            accentBlue = accentBlue,
                            isFollowed = profile.backendId != null && followedUserIds.contains(profile.backendId),
                            isLoading = profile.backendId != null && followLoadingIds.contains(profile.backendId),
                            onFollow = { backendId ->
                                onFollowToggle(backendId)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            PremiumButton(text = "Finish", onClick = onFinish)
            TextButton(onClick = onSkip) {
                Text("Skip for now", color = accentBlue)
            }
        }
    }
}

@Composable
private fun UsernameStatusText(
    status: UsernameStatus,
    accentBlue: Color,
    successGreen: Color
) {
    when (status) {
        UsernameStatus.Checking -> Text("Checking availability...", color = accentBlue, fontSize = 11.sp)
        UsernameStatus.Available -> Text("Username is available", color = successGreen, fontSize = 11.sp)
        UsernameStatus.Taken -> Text("Username is already taken", color = Color.Red, fontSize = 11.sp)
        UsernameStatus.Idle -> Text("Use letters, numbers, dots or underscores.", color = PremiumTextSecondary, fontSize = 11.sp)
    }
}

@Composable
fun RecommendationRow(
    profile: RecommendedProfile,
    accentBlue: Color,
    isFollowed: Boolean,
    isLoading: Boolean,
    onFollow: (String) -> Unit
) {
    Surface(
        color = Color.White.copy(0.05f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppAvatar(
                imageModel = profile.image,
                name = profile.name,
                username = profile.username,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("@${profile.username}", color = Color.Gray, fontSize = 12.sp)
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = accentBlue
                )
            } else {
                Button(
                    onClick = { profile.backendId?.let(onFollow) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isFollowed) Color.Transparent else accentBlue),
                    border = if (isFollowed) BorderStroke(1.dp, accentBlue) else null,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(34.dp),
                    enabled = profile.backendId != null
                ) {
                    if (isFollowed) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (isFollowed) "Following" else "Follow", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}
