package com.stugram.app.ui.home

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.BlockedUserModel
import com.stugram.app.data.remote.ChangePasswordRequest
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository = SettingsRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val searchRepository: SearchRepository = SearchRepository(),
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

    private val _userProfile = MutableStateFlow<ProfileModel?>(null)
    val userProfile: StateFlow<ProfileModel?> = _userProfile.asStateFlow()

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
                    _userProfile.value = response.body()?.data
                }
            } catch (e: Exception) {}
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
                }
            } catch (e: Exception) {
                // Rollback or show error
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
                    }
                }
            } catch (_: Exception) { }
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
                    _hiddenWords.value = response.body()?.data ?: emptyList()
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
                    _hiddenWords.value = response.body()?.data ?: words
                }
            } catch (e: Exception) {}
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            when (authRepository.logout()) {
                is ApiResult.Success -> onComplete()
                is ApiResult.Error -> {
                    // Keep the current session intact if backend logout fails.
                }
            }
        }
    }
}
