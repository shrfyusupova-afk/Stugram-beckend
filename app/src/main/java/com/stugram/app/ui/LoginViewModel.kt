package com.stugram.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.ApiResult
import com.stugram.app.data.remote.model.AuthPayload
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.DeletePushTokenRequest
import com.stugram.app.data.remote.model.RegisterPushTokenRequest
import com.stugram.app.data.repository.AuthRepository
import com.stugram.app.data.repository.DeviceRepository
import com.google.firebase.messaging.FirebaseMessaging
import android.provider.Settings
import android.util.Log
import com.stugram.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application.applicationContext)
    private val authRepository = AuthRepository(tokenManager)
    private val deviceRepository = DeviceRepository()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        RetrofitClient.init(application.applicationContext)
    }

    fun onUsernameChange(username: String) {
        updateForm { copy(identityOrUsername = username, validationError = null) }
    }

    fun onPasswordChange(password: String) {
        updateForm { copy(password = password, validationError = null) }
    }

    fun login() {
        val validationError = validate(_uiState.value.form)
        if (validationError != null) {
            _uiState.value = LoginUiState.Error(
                form = _uiState.value.form.copy(validationError = validationError),
                message = validationError
            )
            return
        }

        viewModelScope.launch {
            try {
                val currentForm = _uiState.value.form.copy(validationError = null)
                _uiState.value = LoginUiState.Loading(currentForm)

                when (val result = authRepository.login(currentForm.identityOrUsername.trim(), currentForm.password)) {
                    is ApiResult.Success -> {
                        val payload = result.data
                        if (payload == null) {
                            _uiState.value = LoginUiState.Error(currentForm, "Login response was empty")
                        } else {
                            _uiState.value = LoginUiState.Success(currentForm, payload)
                            registerPushToken()
                        }
                    }

                    is ApiResult.Error -> {
                        _uiState.value = LoginUiState.Error(currentForm, result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    _uiState.value.form,
                    "Connection error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun loginWithGoogle() {
        // Token acquisition happens in UI layer (CredentialManager); this function is kept
        // for backwards-compat but will show a clear message if used directly.
        _uiState.value = LoginUiState.Error(_uiState.value.form, "Google login is not initialized")
    }

    fun loginWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            val currentForm = _uiState.value.form.copy(validationError = null)
            _uiState.value = LoginUiState.Loading(currentForm)
            when (val result = authRepository.googleLogin(idToken)) {
                is ApiResult.Success -> {
                    val payload = result.data
                    if (payload == null) {
                        _uiState.value = LoginUiState.Error(currentForm, "Google login response was empty")
                    } else {
                        _uiState.value = LoginUiState.Success(currentForm, payload)
                        registerPushToken()
                    }
                }

                is ApiResult.Error -> _uiState.value = LoginUiState.Error(currentForm, result.message)
            }
        }
    }

    fun requestPasswordReset(identity: String) {
        viewModelScope.launch {
            val currentForm = _uiState.value.form.copy(validationError = null)
            _uiState.value = LoginUiState.Loading(currentForm)
            when (val result = authRepository.forgotPassword(identity.trim())) {
                is ApiResult.Success -> {
                    val tokenHint = result.data?.resetToken
                    val message = if (!tokenHint.isNullOrBlank()) {
                        "Reset token (dev): $tokenHint"
                    } else {
                        "Password reset instructions sent (if account exists)."
                    }
                    _uiState.value = LoginUiState.Error(
                        currentForm.copy(showPasswordResetModal = false),
                        message
                    )
                }
                is ApiResult.Error -> _uiState.value = LoginUiState.Error(currentForm, result.message)
            }
        }
    }

    fun resetPassword(token: String, newPassword: String) {
        viewModelScope.launch {
            val currentForm = _uiState.value.form.copy(validationError = null)
            _uiState.value = LoginUiState.Loading(currentForm)
            when (val result = authRepository.resetPassword(token.trim(), newPassword)) {
                is ApiResult.Success -> {
                    _uiState.value = LoginUiState.Error(
                        currentForm.copy(showPasswordResetModal = false),
                        "Password reset successful. Please sign in."
                    )
                }
                is ApiResult.Error -> _uiState.value = LoginUiState.Error(currentForm, result.message)
            }
        }
    }

    fun togglePasswordResetModal(show: Boolean) {
        updateForm { copy(showPasswordResetModal = show) }
    }

    fun showInlineError(message: String) {
        _uiState.value = LoginUiState.Error(_uiState.value.form, message)
    }

    fun refreshSession() {
        viewModelScope.launch {
            try {
                val currentForm = _uiState.value.form
                _uiState.value = LoginUiState.Loading(currentForm)
                when (val result = authRepository.refreshToken()) {
                    is ApiResult.Success -> {
                        val payload = result.data
                        if (payload == null) {
                            _uiState.value = LoginUiState.Error(currentForm, "Session refresh failed")
                        } else {
                            _uiState.value = LoginUiState.Success(currentForm, payload)
                        }
                    }

                    is ApiResult.Error -> _uiState.value = LoginUiState.Error(currentForm, result.message)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    _uiState.value.form,
                    "Session refresh error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                // Delete push token from backend before logging out
                deletePushToken()
                
                when (val result = authRepository.logout()) {
                    is ApiResult.Success -> _uiState.value = LoginUiState.Idle()
                    is ApiResult.Error -> _uiState.value = LoginUiState.Error(_uiState.value.form, result.message)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    _uiState.value.form,
                    "Logout error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    private suspend fun deletePushToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val deviceId = Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                Settings.Secure.ANDROID_ID
            )
            deviceRepository.deletePushToken(DeletePushTokenRequest(token, deviceId))
        } catch (e: Exception) {
            Log.e("LoginViewModel", "Failed to delete push token during logout", e)
        }
    }

    private fun registerPushToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("LoginViewModel", "Fetching FCM registration token failed", task.exception)
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
                    Log.e("LoginViewModel", "Failed to register push token", e)
                }
            }
        }
    }

    private fun validate(form: LoginFormState): String? {
        if (form.identityOrUsername.isBlank()) return "Enter your username or email"
        if (form.password.isBlank()) return "Enter your password"
        if (form.password.length < 6) return "Password must be at least 6 characters"
        return null
    }

    private fun updateForm(transform: LoginFormState.() -> LoginFormState) {
        val updatedForm = _uiState.value.form.transform()
        _uiState.value = when (val current = _uiState.value) {
            is LoginUiState.Loading -> current.copy(form = updatedForm)
            is LoginUiState.Success -> LoginUiState.Idle(updatedForm)
            is LoginUiState.Error -> LoginUiState.Idle(updatedForm)
            is LoginUiState.Idle -> current.copy(form = updatedForm)
        }
    }
}

data class LoginFormState(
    val identityOrUsername: String = "",
    val password: String = "",
    val validationError: String? = null,
    val showPasswordResetModal: Boolean = false
)

sealed interface LoginUiState {
    val form: LoginFormState

    data class Idle(
        override val form: LoginFormState = LoginFormState()
    ) : LoginUiState

    data class Loading(
        override val form: LoginFormState
    ) : LoginUiState

    data class Success(
        override val form: LoginFormState,
        val payload: AuthPayload
    ) : LoginUiState

    data class Error(
        override val form: LoginFormState,
        val message: String
    ) : LoginUiState
}

val LoginUiState.username: String
    get() = form.identityOrUsername

val LoginUiState.password: String
    get() = form.password

val LoginUiState.error: String?
    get() = when (this) {
        is LoginUiState.Error -> message
        else -> form.validationError
    }

val LoginUiState.isLoading: Boolean
    get() = this is LoginUiState.Loading

val LoginUiState.isSuccess: Boolean
    get() = this is LoginUiState.Success

val LoginUiState.showPasswordResetModal: Boolean
    get() = form.showPasswordResetModal

val LoginUiState.currentUser: ProfileModel?
    get() = (this as? LoginUiState.Success)?.payload?.user
