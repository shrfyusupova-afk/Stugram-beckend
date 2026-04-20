package com.stugram.app.ui.auth.register

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.BuildConfig
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.ApiResult
import com.stugram.app.data.remote.model.AuthPayload
import com.stugram.app.data.remote.model.RegisterPushTokenRequest
import com.stugram.app.data.remote.model.RegisterRequest
import com.stugram.app.data.repository.AuthRepository
import com.stugram.app.data.repository.DeviceRepository
import com.stugram.app.ui.home.RecommendedProfile
import com.google.firebase.messaging.FirebaseMessaging
import android.provider.Settings
import android.util.Log
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.UpdateProfileRequest
import com.stugram.app.data.remote.model.UsernameAvailabilityResponse
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.data.repository.FollowRepository
import com.stugram.app.core.masterdata.UzbekistanAdminData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

class RegisterViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>().applicationContext
    private val tokenManager = TokenManager(application.applicationContext)
    private val authRepository = AuthRepository(tokenManager)
    private val profileRepository = ProfileRepository()
    private val followRepository = FollowRepository()
    private val deviceRepository = DeviceRepository()
    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle())
    val uiState = _uiState.asStateFlow()

    private var usernameCheckJob: Job? = null
    private var otpCountdownJob: Job? = null

    init {
        RetrofitClient.init(appContext)
    }

    val regions = UzbekistanAdminData.regions(appContext)

    fun getDistricts(region: String): List<String> {
        return UzbekistanAdminData.districts(appContext, region)
    }

    fun getSchools(region: String, district: String): List<String> {
        return UzbekistanAdminData.schools(appContext, region, district)
    }

    val grades = (1..11).map { it.toString() }
    val groups = listOf("A", "B", "D", "E")

    private val onboardingStartStep = 7
    private val onboardingTotalSteps = 4

    fun onIdentityChange(identity: String) {
        updateForm { copy(identity = identity, validationError = null) }
    }

    fun onOtpChange(otp: String) {
        if (otp.length <= 6) {
            updateForm { copy(otp = otp, validationError = null) }
        }
    }

    fun sendOtp() {
        val form = _uiState.value.form
        val validationError = validateIdentity(form.identity)
        if (validationError != null) {
            _uiState.value = RegisterUiState.Error(
                form = form.copy(validationError = validationError),
                message = validationError
            )
            return
        }

        viewModelScope.launch {
            try {
                val currentForm = form.copy(validationError = null)
                _uiState.value = RegisterUiState.Loading(currentForm)
                when (val result = authRepository.sendOtp(currentForm.identity.trim(), purpose = "register")) {
                    is ApiResult.Success -> {
                        startOtpCountdown()
                        _uiState.value = RegisterUiState.Idle(currentForm.copy(currentStep = 2, otpResendSecondsRemaining = 60))
                    }

                    is ApiResult.Error -> {
                        _uiState.value = RegisterUiState.Error(currentForm, result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(form, "Connection error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun resendOtp() {
        val form = _uiState.value.form
        if (form.otpResendSecondsRemaining > 0) return
        sendOtp()
    }

    private fun startOtpCountdown() {
        otpCountdownJob?.cancel()
        otpCountdownJob = viewModelScope.launch {
            var remaining = 60
            while (remaining >= 0) {
                updateForm { copy(otpResendSecondsRemaining = remaining) }
                delay(1000)
                remaining -= 1
            }
        }
    }

    fun loginWithGoogle() {
        // Registration Google login entrypoint removed: real Google auth is handled
        // via CredentialManager flow on the main Login screen.
        _uiState.value = RegisterUiState.Error(_uiState.value.form, "Google login is available on the Login screen.")
    }

    fun verifyOtp() {
        val form = _uiState.value.form
        val validationError = validateOtp(form.otp)
        if (validationError != null) {
            _uiState.value = RegisterUiState.Error(
                form = form.copy(validationError = validationError),
                message = validationError
            )
            return
        }

        viewModelScope.launch {
            try {
                val currentForm = form.copy(validationError = null)
                _uiState.value = RegisterUiState.Loading(currentForm)
                when (val result = authRepository.verifyOtp(currentForm.identity.trim(), currentForm.otp, purpose = "register")) {
                    is ApiResult.Success -> {
                        _uiState.value = RegisterUiState.Idle(
                            currentForm.copy(currentStep = 3)
                        )
                    }

                    is ApiResult.Error -> {
                        _uiState.value = RegisterUiState.Error(currentForm, result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(form, "Verification error: ${e.localizedMessage}")
            }
        }
    }

    fun updateField(field: RegisterField) {
        updateForm {
            when (field) {
                is RegisterField.FullName -> copy(fullName = field.value, validationError = null)
                is RegisterField.Username -> {
                    val username = field.value.removePrefix("@")
                    if (username.isEmpty() || username.all { it.isLetterOrDigit() || it in "._" }) {
                        checkUsernameAvailability(username)
                        copy(
                            username = field.value,
                            validationError = null,
                            usernameStatus = when {
                                username.length < 3 -> UsernameStatus.Idle
                                username.equals(originalUsername, ignoreCase = true) -> UsernameStatus.Available
                                else -> UsernameStatus.Checking
                            }
                        )
                    } else this
                }
                is RegisterField.Region -> copy(selectedRegion = field.value, selectedDistrict = "", selectedSchool = "")
                is RegisterField.District -> copy(selectedDistrict = field.value, selectedSchool = "")
                is RegisterField.School -> copy(selectedSchool = field.value)
                is RegisterField.ProfileType -> copy(profileType = field.value, validationError = null)
                is RegisterField.Grade -> copy(grade = field.value)
                is RegisterField.Group -> copy(group = field.value)
                is RegisterField.Gender -> copy(gender = field.value)
                is RegisterField.BirthDate -> copy(birthDay = field.day, birthMonth = field.month, birthYear = field.year)
                is RegisterField.Password -> copy(password = field.value, validationError = null)
                is RegisterField.ConfirmPassword -> copy(confirmPassword = field.value, validationError = null)
                is RegisterField.ProfileImage -> copy(profileImageUri = field.uri)
                is RegisterField.BannerImage -> copy(bannerImageUri = field.uri)
            }
        }
    }

    fun nextStep() {
        val form = _uiState.value.form
        val validationError = when (form.currentStep) {
            3 -> validateBasicInfo(form)
            5 -> if (form.profileType == "student") validateRegionInfo(form) else null
            6 -> validatePassword(form)
            else -> null
        }
        if (validationError != null) {
            _uiState.value = RegisterUiState.Error(form.copy(validationError = validationError), validationError)
            return
        }
        val next = when (form.currentStep) {
            4 -> if (form.profileType == "blogger") 6 else 5
            else -> form.currentStep + 1
        }
        _uiState.value = RegisterUiState.Idle(form.copy(currentStep = next, validationError = null))
    }

    fun register() {
        val form = _uiState.value.form
        val validationError = validateRegister(form)
        if (validationError != null) {
            _uiState.value = RegisterUiState.Error(form.copy(validationError = validationError), validationError)
            return
        }

        viewModelScope.launch {
            try {
                val currentForm = form.copy(validationError = null)
                _uiState.value = RegisterUiState.Loading(currentForm)
                val request = RegisterRequest(
                    identity = currentForm.identity.trim(),
                    otp = currentForm.otp,
                    password = currentForm.password,
                    fullName = currentForm.fullName.trim(),
                    username = currentForm.username.trim().removePrefix("@"),
                    type = currentForm.profileType,
                    region = if (currentForm.profileType == "student") currentForm.selectedRegion else null,
                    district = if (currentForm.profileType == "student") currentForm.selectedDistrict else null,
                    school = if (currentForm.profileType == "student") currentForm.selectedSchool else null,
                    birthday = currentForm.toBirthdayIsoOrNull(),
                    grade = currentForm.grade.ifBlank { null },
                    group = currentForm.group.ifBlank { null },
                    bio = "",
                    isPrivateAccount = currentForm.isPrivateAccount
                )

                when (val result = authRepository.register(request)) {
                    is ApiResult.Success -> {
                        val payload = result.data
                        if (payload == null) {
                            _uiState.value = RegisterUiState.Error(currentForm, "Registration response was empty")
                        } else {
                            // After registration, update tokens and proceed to Step 6 (Onboarding)
                            tokenManager.saveSession(payload.user, payload.accessToken, payload.refreshToken)
                            
                            _uiState.value = RegisterUiState.Idle(
                                currentForm.copy(
                                    currentStep = onboardingStartStep,
                                    username = payload.user.username,
                                    originalUsername = payload.user.username,
                                    usernameStatus = UsernameStatus.Available
                                )
                            )
                            registerPushToken()
                            fetchRecommendations()
                        }
                    }

                    is ApiResult.Error -> {
                        _uiState.value = RegisterUiState.Error(currentForm, result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(form, "Registration error: ${e.localizedMessage}")
            }
        }
    }

    private fun registerPushToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("RegisterViewModel", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            val deviceId = Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                Settings.Secure.ANDROID_ID
            )

            viewModelScope.launch {
                try {
                    deviceRepository.registerPushToken(
                        RegisterPushTokenRequest(
                            token = token,
                            deviceId = deviceId,
                            appVersion = BuildConfig.VERSION_NAME
                        )
                    )
                } catch (e: Exception) {
                    Log.e("RegisterViewModel", "Failed to register push token", e)
                }
            }
        }
    }

    private fun fetchRecommendations() {
        viewModelScope.launch {
            updateForm { copy(isRecommendationsLoading = true) }
            try {
                val response = profileRepository.getProfileSuggestions(page = 1, limit = 12)
                if (response.isSuccessful) {
                    val users = response.body()?.data ?: emptyList()
                    val profiles = users.map { 
                        RecommendedProfile(
                            id = it.id.hashCode(),
                            name = it.fullName,
                            image = it.avatar,
                            username = it.username,
                            backendId = it.id
                        )
                    }
                    updateForm { copy(recommendedProfiles = profiles, isRecommendationsLoading = false) }
                } else {
                    updateForm { copy(isRecommendationsLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("RegisterViewModel", "Failed to fetch recommendations", e)
                updateForm { copy(isRecommendationsLoading = false) }
            }
        }
    }

    fun completeRegistration() {
        viewModelScope.launch {
            if (_uiState.value is RegisterUiState.Success) return@launch

            val user = tokenManager.getCurrentUser()
            val accessToken = tokenManager.getAccessToken().orEmpty()
            val refreshToken = tokenManager.getRefreshToken().orEmpty()

            if (user == null || accessToken.isBlank() || refreshToken.isBlank()) {
                _uiState.value = RegisterUiState.Error(
                    _uiState.value.form,
                    "Session not found. Please log in again."
                )
                return@launch
            }

            _uiState.value = RegisterUiState.Success(
                _uiState.value.form,
                AuthPayload(
                    user = user,
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            )
        }
    }

    fun prevStep() {
        val form = _uiState.value.form
        if (form.currentStep > 1) {
            _uiState.value = RegisterUiState.Idle(
                form.copy(currentStep = form.currentStep - 1, validationError = null)
            )
        }
    }

    private fun checkUsernameAvailability(username: String) {
        usernameCheckJob?.cancel()
        if (username.length < 3) {
            updateForm { copy(usernameStatus = UsernameStatus.Idle) }
            return
        }
        if (username.equals(_uiState.value.form.originalUsername, ignoreCase = true)) {
            updateForm { copy(usernameStatus = UsernameStatus.Available) }
            return
        }

        usernameCheckJob = viewModelScope.launch {
            delay(500) // Debounce
            try {
                val response = profileRepository.checkUsernameAvailability(username)
                if (response.isSuccessful) {
                    val available = response.body()?.data?.available == true
                    updateForm { 
                        copy(usernameStatus = if (available) UsernameStatus.Available else UsernameStatus.Taken) 
                    }
                } else {
                    updateForm { copy(usernameStatus = UsernameStatus.Idle) }
                }
            } catch (e: Exception) {
                updateForm { copy(usernameStatus = UsernameStatus.Idle) }
            }
        }
    }

    fun uploadProfileImage() {
        val uri = _uiState.value.form.profileImageUri ?: return
        viewModelScope.launch {
            updateForm { copy(isUploadingProfile = true) }
            try {
                val response = profileRepository.uploadAvatar(getApplication<Application>(), uri)
                if (response.isSuccessful) {
                    response.body()?.data?.let { user ->
                        tokenManager.updateCurrentUser(user)
                    }
                } else {
                    _uiState.value = RegisterUiState.Error(_uiState.value.form, "Profile photo upload failed")
                }
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(_uiState.value.form, e.localizedMessage ?: "Profile photo upload failed")
            } finally {
                updateForm { copy(isUploadingProfile = false) }
            }
        }
    }

    fun uploadBannerImage() {
        val uri = _uiState.value.form.bannerImageUri ?: return
        viewModelScope.launch {
            updateForm { copy(isUploadingBanner = true) }
            try {
                val response = profileRepository.uploadBanner(getApplication<Application>(), uri)
                if (response.isSuccessful) {
                    response.body()?.data?.let { user ->
                        tokenManager.updateCurrentUser(user)
                    }
                } else {
                    _uiState.value = RegisterUiState.Error(_uiState.value.form, "Banner upload failed")
                }
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(_uiState.value.form, e.localizedMessage ?: "Banner upload failed")
            } finally {
                updateForm { copy(isUploadingBanner = false) }
            }
        }
    }

    fun toggleFollowUser(userId: String) {
        viewModelScope.launch {
            try {
                if (_uiState.value.form.followLoadingIds.contains(userId)) return@launch
                updateForm { copy(followLoadingIds = followLoadingIds + userId) }
                val isFollowed = _uiState.value.form.followedUserIds.contains(userId)
                val response = if (isFollowed) {
                    followRepository.unfollowUser(userId)
                } else {
                    followRepository.followUser(userId)
                }
                if (response.isSuccessful) {
                    updateForm {
                        copy(
                            followedUserIds = if (isFollowed) followedUserIds - userId else followedUserIds + userId,
                            followLoadingIds = followLoadingIds - userId
                        )
                    }
                } else {
                    updateForm { copy(followLoadingIds = followLoadingIds - userId) }
                }
            } catch (_: Exception) {
                updateForm { copy(followLoadingIds = followLoadingIds - userId) }
            }
        }
    }

    fun skipOnboardingStep() {
        val step = _uiState.value.form.currentStep
        if (step == onboardingStartStep + onboardingTotalSteps - 1) {
            // Step 10: suggestions
            completeRegistration()
        } else if (step in onboardingStartStep..(onboardingStartStep + onboardingTotalSteps - 2)) {
            // Steps 7-9
            nextStep()
        }
    }

    fun continueFromOnboarding() {
        val form = _uiState.value.form
        when (form.currentStep) {
            7 -> if (!form.isUploadingProfile) nextStep()
            8 -> if (!form.isUploadingBanner) nextStep()
            9 -> saveOnboardingUsername()
            10 -> completeRegistration()
            else -> nextStep()
        }
    }

    private fun saveOnboardingUsername() {
        val form = _uiState.value.form
        val normalizedUsername = form.username.trim().removePrefix("@").lowercase()
        val validationError = validateOnboardingUsername(form)
        if (validationError != null) {
            _uiState.value = RegisterUiState.Error(form.copy(validationError = validationError), validationError)
            return
        }

        if (normalizedUsername == form.originalUsername.lowercase()) {
            nextStep()
            return
        }

        viewModelScope.launch {
            updateForm { copy(isSavingUsername = true) }
            try {
                val response = profileRepository.updateProfile(
                    UpdateProfileRequest(username = normalizedUsername)
                )
                if (response.isSuccessful) {
                    val updatedUser = response.body()?.data
                    if (updatedUser != null) {
                        tokenManager.updateCurrentUser(updatedUser)
                    }
                    updateForm {
                        copy(
                            originalUsername = normalizedUsername,
                            username = normalizedUsername,
                            usernameStatus = UsernameStatus.Available,
                            isSavingUsername = false
                        )
                    }
                    nextStep()
                } else {
                    _uiState.value = RegisterUiState.Error(form, "Could not save username")
                    updateForm { copy(isSavingUsername = false) }
                }
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(form, e.localizedMessage ?: "Could not save username")
                updateForm { copy(isSavingUsername = false) }
            }
        }
    }

    private fun validateIdentity(identity: String): String? {
        val normalized = identity.trim()
        val emailPattern = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        val phonePattern = "^\\+998\\d{9}$".toRegex()
        return when {
            normalized.isBlank() -> "Enter your email or phone number"
            !normalized.matches(emailPattern) && !normalized.matches(phonePattern) ->
                "Enter a valid email or a phone number starting with +998"
            else -> null
        }
    }

    private fun validateOtp(otp: String): String? {
        return when {
            otp.isBlank() -> "Enter the verification code"
            otp.length != 6 -> "Enter a 6-digit code"
            !otp.all(Char::isDigit) -> "The code must contain only digits"
            else -> null
        }
    }

    private fun validateBasicInfo(form: RegisterFormState): String? {
        return when {
            form.fullName.trim().length < 3 -> "Full name must be at least 3 characters"
            form.username.trim().removePrefix("@").length < 3 -> "Username must be at least 3 characters"
            form.usernameStatus == UsernameStatus.Taken -> "Username is already taken"
            else -> null
        }
    }

    private fun validateOnboardingUsername(form: RegisterFormState): String? {
        val username = form.username.trim().removePrefix("@")
        return when {
            username.length < 3 -> "Username must be at least 3 characters"
            !username.all { it.isLetterOrDigit() || it in "._" } -> "Username can only contain letters, numbers, . and _"
            form.usernameStatus == UsernameStatus.Checking -> "Please wait for username check"
            form.usernameStatus == UsernameStatus.Taken -> "Choose another username"
            else -> null
        }
    }

    private fun validateRegionInfo(form: RegisterFormState): String? {
        return when {
            form.selectedRegion.isBlank() -> "Select a region"
            form.selectedDistrict.isBlank() -> "Select a district"
            form.selectedSchool.isBlank() -> "Select a school"
            else -> null
        }
    }

    private fun validatePassword(form: RegisterFormState): String? {
        return when {
            form.password.length < 6 -> "Password must be at least 6 characters"
            form.confirmPassword != form.password -> "Passwords do not match"
            else -> null
        }
    }

    private fun validateRegister(form: RegisterFormState): String? {
        return validateIdentity(form.identity)
            ?: validateOtp(form.otp)
            ?: validateBasicInfo(form)
            ?: validatePassword(form)
    }

    private fun RegisterFormState.toBirthdayIsoOrNull(): String? {
        val day = birthDay.trim().toIntOrNull() ?: return null
        val month = birthMonth.trim().toIntOrNull() ?: return null
        val year = birthYear.trim().toIntOrNull() ?: return null
        return runCatching {
            java.time.LocalDate.of(year, month, day).toString()
        }.getOrNull()
    }

    private fun updateForm(transform: RegisterFormState.() -> RegisterFormState) {
        val updatedForm = _uiState.value.form.transform()
        _uiState.value = when (val current = _uiState.value) {
            is RegisterUiState.Loading -> current.copy(form = updatedForm)
            is RegisterUiState.Success -> RegisterUiState.Idle(updatedForm)
            is RegisterUiState.Error -> RegisterUiState.Idle(updatedForm)
            is RegisterUiState.Idle -> current.copy(form = updatedForm)
        }
    }
}

sealed class RegisterField {
    data class FullName(val value: String) : RegisterField()
    data class Username(val value: String) : RegisterField()
    data class Region(val value: String) : RegisterField()
    data class District(val value: String) : RegisterField()
    data class School(val value: String) : RegisterField()
    data class Grade(val value: String) : RegisterField()
    data class Group(val value: String) : RegisterField()
    data class Gender(val value: String) : RegisterField()
    data class BirthDate(val day: String, val month: String, val year: String) : RegisterField()
    data class Password(val value: String) : RegisterField()
    data class ConfirmPassword(val value: String) : RegisterField()
    data class ProfileType(val value: String) : RegisterField()
    data class ProfileImage(val uri: Uri?) : RegisterField()
    data class BannerImage(val uri: Uri?) : RegisterField()
}

data class RegisterFormState(
    val currentStep: Int = 1,
    val identity: String = "",
    val otp: String = "",
    val fullName: String = "",
    val username: String = "",
    val selectedRegion: String = "",
    val selectedDistrict: String = "",
    val selectedSchool: String = "",
    val grade: String = "",
    val group: String = "",
    val gender: String = "",
    val birthDay: String = "",
    val birthMonth: String = "",
    val birthYear: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val profileType: String = "student",
    val profileImageUri: Uri? = null,
    val bannerImageUri: Uri? = null,
    val originalUsername: String = "",
    val usernameStatus: UsernameStatus = UsernameStatus.Idle,
    val isUploadingProfile: Boolean = false,
    val isUploadingBanner: Boolean = false,
    val isSavingUsername: Boolean = false,
    val isPrivateAccount: Boolean = false,
    val isRecommendationsLoading: Boolean = false,
    val followedUserIds: Set<String> = emptySet(),
    val followLoadingIds: Set<String> = emptySet(),
    val recommendedProfiles: List<RecommendedProfile> = emptyList(),
    val validationError: String? = null,
    val otpResendSecondsRemaining: Int = 0
)

enum class UsernameStatus {
    Idle, Checking, Available, Taken
}

sealed interface RegisterUiState {
    val form: RegisterFormState

    data class Idle(
        override val form: RegisterFormState = RegisterFormState()
    ) : RegisterUiState

    data class Loading(
        override val form: RegisterFormState
    ) : RegisterUiState

    data class Success(
        override val form: RegisterFormState,
        val payload: AuthPayload
    ) : RegisterUiState

    data class Error(
        override val form: RegisterFormState,
        val message: String
    ) : RegisterUiState
}

val RegisterUiState.currentStep: Int
    get() = form.currentStep

val RegisterUiState.identity: String
    get() = form.identity

val RegisterUiState.otp: String
    get() = form.otp

val RegisterUiState.fullName: String
    get() = form.fullName

val RegisterUiState.username: String
    get() = form.username

val RegisterUiState.selectedRegion: String
    get() = form.selectedRegion

val RegisterUiState.selectedDistrict: String
    get() = form.selectedDistrict

val RegisterUiState.selectedSchool: String
    get() = form.selectedSchool

val RegisterUiState.grade: String
    get() = form.grade

val RegisterUiState.group: String
    get() = form.group

val RegisterUiState.gender: String
    get() = form.gender

val RegisterUiState.birthDay: String
    get() = form.birthDay

val RegisterUiState.birthMonth: String
    get() = form.birthMonth

val RegisterUiState.birthYear: String
    get() = form.birthYear

val RegisterUiState.password: String
    get() = form.password

val RegisterUiState.confirmPassword: String
    get() = form.confirmPassword

val RegisterUiState.profileImageUri: Uri?
    get() = form.profileImageUri

val RegisterUiState.bannerImageUri: Uri?
    get() = form.bannerImageUri

val RegisterUiState.recommendedProfiles: List<RecommendedProfile>
    get() = form.recommendedProfiles

val RegisterUiState.isLoading: Boolean
    get() = this is RegisterUiState.Loading

val RegisterUiState.error: String?
    get() = when (this) {
        is RegisterUiState.Error -> message
        else -> form.validationError
    }

val RegisterUiState.isSuccess: Boolean
    get() = this is RegisterUiState.Success
