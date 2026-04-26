package com.stugram.app.ui.home

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.BlockedUserModel
import com.stugram.app.data.remote.ChangePasswordRequest
import com.stugram.app.data.remote.HiddenWordsSettingsModel
import com.stugram.app.data.remote.LoginSessionModel
import com.stugram.app.data.remote.NotificationSettingsModel
import com.stugram.app.data.remote.SearchHistoryItem
import com.stugram.app.data.remote.UserSettingsModel
import com.stugram.app.data.remote.model.ApiResult
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.UpdateProfileRequest
import com.stugram.app.data.repository.AuthRepository
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.data.repository.SearchRepository
import com.stugram.app.data.repository.SettingsRepository
import com.stugram.app.data.repository.SupportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository = SettingsRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val searchRepository: SearchRepository = SearchRepository(),
    private val supportRepository: SupportRepository = SupportRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val tokenManager: TokenManager? = null
) : ViewModel() {

    private val _settings = MutableStateFlow<UserSettingsModel?>(null)
    val settings: StateFlow<UserSettingsModel?> = _settings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<BlockedUserModel>>(emptyList())
    val blockedUsers: StateFlow<List<BlockedUserModel>> = _blockedUsers.asStateFlow()

    private val _loginSessions = MutableStateFlow<List<LoginSessionModel>>(emptyList())
    val loginSessions: StateFlow<List<LoginSessionModel>> = _loginSessions.asStateFlow()

    private val _loginSessionsError = MutableStateFlow<String?>(null)
    val loginSessionsError: StateFlow<String?> = _loginSessionsError.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryItem>> = _searchHistory.asStateFlow()

    private val _hiddenWords = MutableStateFlow<List<String>>(emptyList())
    val hiddenWords: StateFlow<List<String>> = _hiddenWords.asStateFlow()

    private val _hiddenWordsSettings = MutableStateFlow(HiddenWordsSettingsModel())
    val hiddenWordsSettings: StateFlow<HiddenWordsSettingsModel> = _hiddenWordsSettings.asStateFlow()

    private val _userProfile = MutableStateFlow<ProfileModel?>(null)
    val userProfile: StateFlow<ProfileModel?> = _userProfile.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _supportTickets = MutableStateFlow<List<com.stugram.app.data.remote.SupportTicketModel>>(emptyList())
    val supportTickets: StateFlow<List<com.stugram.app.data.remote.SupportTicketModel>> = _supportTickets.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        fetchSettings()
        fetchBlockedUsers()
        fetchLoginSessions()
        fetchSearchHistory()
        fetchHiddenWords()
        fetchUserProfile()
        fetchSupportTickets()
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            try {
                val cached = tokenManager?.getCurrentUser()
                if (cached != null) {
                    _userProfile.value = cached
                }

                val response = runCatching { profileRepository.getCurrentProfile() }.getOrNull()
                if (response?.isSuccessful == true) {
                    response.body()?.data?.let { fresh ->
                        _userProfile.value = fresh
                        tokenManager?.updateCurrentUser(fresh)
                    }
                } else if (cached != null) {
                    val fallback = profileRepository.getProfile(cached.username)
                    if (fallback.isSuccessful) {
                        fallback.body()?.data?.let { _userProfile.value = it }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun updateProfile(fullName: String? = null, username: String? = null, bio: String? = null) {
        viewModelScope.launch {
            try {
                val request = UpdateProfileRequest(fullName = fullName, username = username, bio = bio)
                val response = profileRepository.updateProfile(request)
                if (response.isSuccessful) {
                    response.body()?.data?.let { updated ->
                        _userProfile.value = updated
                        tokenManager?.updateCurrentUser(updated)
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Profile update failed"
            }
        }
    }

    fun changePassword(current: String, new: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = settingsRepository.changePassword(ChangePasswordRequest(current, new))
                if (response.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, response.message())
                }
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    private fun fetchSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = settingsRepository.getSettings()
                if (response.isSuccessful) {
                    _settings.value = response.body()?.data
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSetting(key: String, value: Any) {
        viewModelScope.launch {
            try {
                val response = settingsRepository.updateSettings(mapOf(key to value))
                if (response.isSuccessful) {
                    _settings.value = response.body()?.data
                    _statusMessage.value = "Setting updated"
                } else {
                    _statusMessage.value = response.message().ifBlank { "Setting update failed" }
                }
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Setting update failed"
            }
        }
    }

    fun updateNotificationSetting(key: String, value: Boolean) {
        val currentNotifications = _settings.value?.notifications ?: NotificationSettingsModel()
        val newNotifications = when (key) {
            "likes" -> currentNotifications.copy(likes = value)
            "comments" -> currentNotifications.copy(comments = value)
            "followRequests" -> currentNotifications.copy(followRequests = value)
            "messages" -> currentNotifications.copy(messages = value)
            else -> currentNotifications
        }
        viewModelScope.launch {
            try {
                val response = settingsRepository.updateNotificationSettings(newNotifications)
                if (response.isSuccessful) {
                    val updated = response.body()?.data
                    if (updated != null) {
                        val current = _settings.value
                        if (current != null) {
                            _settings.value = current.copy(notifications = updated)
                        }
                        _statusMessage.value = "Notification setting updated"
                    }
                } else {
                    _statusMessage.value = response.message().ifBlank { "Notification update failed" }
                }
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Notification update failed"
            }
        }
    }

    fun updateAllNotificationSettings(enabled: Boolean) {
        val next = NotificationSettingsModel(
            likes = enabled,
            comments = enabled,
            followRequests = enabled,
            messages = enabled,
            mentions = enabled,
            system = enabled
        )
        viewModelScope.launch {
            try {
                val response = settingsRepository.updateNotificationSettings(next)
                if (response.isSuccessful) {
                    val updated = response.body()?.data ?: next
                    _settings.value = (_settings.value ?: UserSettingsModel()).copy(notifications = updated)
                    _statusMessage.value = if (enabled) "Push notifications enabled" else "Push notifications paused"
                } else {
                    _statusMessage.value = response.message().ifBlank { "Notification update failed" }
                }
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Notification update failed"
            }
        }
    }

    private fun fetchBlockedUsers() {
        viewModelScope.launch {
            try {
                val response = settingsRepository.getBlockedUsers()
                if (response.isSuccessful) {
                    _blockedUsers.value = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {}
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            try {
                val response = settingsRepository.unblockUser(userId)
                if (response.isSuccessful) {
                    _blockedUsers.value = _blockedUsers.value.filter { it._id != userId }
                }
            } catch (e: Exception) {}
        }
    }

    private fun fetchLoginSessions() {
        viewModelScope.launch {
            try {
                _loginSessionsError.value = null
                val response = settingsRepository.getLoginSessions()
                if (response.isSuccessful) {
                    _loginSessions.value = response.body()?.data ?: emptyList()
                } else {
                    _loginSessionsError.value = response.message().ifBlank { "Could not load sessions" }
                }
            } catch (e: Exception) {
                _loginSessionsError.value = e.localizedMessage ?: "Could not load sessions"
            }
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val response = settingsRepository.revokeSession(sessionId)
                if (response.isSuccessful) {
                    _loginSessions.value = _loginSessions.value.filter { it.sessionId != sessionId }
                } else {
                    _loginSessionsError.value = response.message().ifBlank { "Could not revoke session" }
                }
            } catch (e: Exception) {
                _loginSessionsError.value = e.localizedMessage ?: "Could not revoke session"
            }
        }
    }

    private fun fetchSearchHistory() {
        viewModelScope.launch {
            try {
                val response = searchRepository.getSearchHistory(1, 100)
                if (response.isSuccessful) {
                    _searchHistory.value = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {}
        }
    }

    fun deleteHistoryItem(historyId: String) {
        viewModelScope.launch {
            try {
                val response = searchRepository.deleteSearchHistoryItem(historyId)
                if (response.isSuccessful) {
                    _searchHistory.value = _searchHistory.value.filter { it._id != historyId }
                }
            } catch (e: Exception) {}
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                val response = searchRepository.clearSearchHistory()
                if (response.isSuccessful) {
                    _searchHistory.value = emptyList()
                }
            } catch (e: Exception) {}
        }
    }

    private fun fetchHiddenWords() {
        viewModelScope.launch {
            try {
                val response = settingsRepository.getHiddenWords()
                if (response.isSuccessful) {
                    val updated = response.body()?.data ?: HiddenWordsSettingsModel()
                    _hiddenWordsSettings.value = updated
                    _hiddenWords.value = updated.words
                }
            } catch (e: Exception) {}
        }
    }

    fun addHiddenWord(word: String) {
        if (word.isBlank() || _hiddenWords.value.contains(word)) return
        val newList = _hiddenWords.value + word
        updateHiddenWordsOnBackend(newList)
    }

    fun removeHiddenWord(word: String) {
        val newList = _hiddenWords.value - word
        updateHiddenWordsOnBackend(newList)
    }

    private fun updateHiddenWordsOnBackend(words: List<String>) {
        viewModelScope.launch {
            try {
                val response = settingsRepository.updateHiddenWords(words)
                if (response.isSuccessful) {
                    val updated = response.body()?.data ?: HiddenWordsSettingsModel(words = words)
                    _hiddenWordsSettings.value = updated
                    _hiddenWords.value = updated.words
                    _statusMessage.value = "Hidden words updated"
                } else {
                    _statusMessage.value = response.message().ifBlank { "Hidden words update failed" }
                }
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Hidden words update failed"
            }
        }
    }

    fun updateHiddenWordsSetting(
        hideComments: Boolean? = null,
        hideMessages: Boolean? = null,
        hideStoryReplies: Boolean? = null
    ) {
        val current = _hiddenWordsSettings.value
        val next = current.copy(
            hideComments = hideComments ?: current.hideComments,
            hideMessages = hideMessages ?: current.hideMessages,
            hideStoryReplies = hideStoryReplies ?: current.hideStoryReplies
        )
        viewModelScope.launch {
            try {
                val response = settingsRepository.updateHiddenWords(next)
                if (response.isSuccessful) {
                    val updated = response.body()?.data ?: next
                    _hiddenWordsSettings.value = updated
                    _hiddenWords.value = updated.words
                    _statusMessage.value = "Hidden words filters updated"
                } else {
                    _statusMessage.value = response.message().ifBlank { "Hidden words update failed" }
                }
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Hidden words update failed"
            }
        }
    }

    fun forgotPassword(onResult: (Boolean, String?) -> Unit) {
        val identity = _userProfile.value?.identity
        if (identity.isNullOrBlank()) {
            onResult(false, "No email or phone identity found for this account")
            return
        }
        viewModelScope.launch {
            when (val result = authRepository.forgotPassword(identity)) {
                is ApiResult.Success -> onResult(true, result.message)
                is ApiResult.Error -> onResult(false, result.message)
            }
        }
    }

    fun fetchSupportTickets() {
        viewModelScope.launch {
            try {
                val response = supportRepository.getMySupportTickets()
                if (response.isSuccessful) {
                    _supportTickets.value = response.body()?.data ?: emptyList()
                }
            } catch (_: Exception) { }
        }
    }

    fun createSupportTicket(subject: String, description: String, onResult: (Boolean, String?) -> Unit) {
        if (subject.trim().length < 3 || description.trim().length < 10) {
            onResult(false, "Subject must be at least 3 characters and description at least 10 characters")
            return
        }
        viewModelScope.launch {
            try {
                val response = supportRepository.createProblemTicket(
                    category = "bug",
                    subject = subject.trim(),
                    description = description.trim(),
                    deviceInfo = "Android closed-alpha settings screen"
                )
                if (response.isSuccessful) {
                    fetchSupportTickets()
                    onResult(true, response.body()?.message ?: "Support ticket created")
                } else {
                    onResult(false, response.message().ifBlank { "Support ticket failed" })
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Support ticket failed")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            when (authRepository.logout()) {
                is ApiResult.Success -> onComplete()
                is ApiResult.Error -> {
                    _statusMessage.value = "Logout failed. Please try again."
                }
            }
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }
}
