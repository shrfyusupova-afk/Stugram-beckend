package com.stugram.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.ApiResult
import com.stugram.app.data.remote.model.RegisterRequest
import com.stugram.app.data.remote.model.UpdateProfileRequest
import com.stugram.app.data.repository.AuthRepository
import com.stugram.app.data.repository.NotificationRepository
import com.stugram.app.data.repository.PostRepository
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.data.repository.SearchRepository
import com.stugram.app.data.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class IntegrationViewModel(
    private val appContext: Context
) : ViewModel() {
    private val tokenManager = TokenManager(appContext)
    private val authRepository = AuthRepository(tokenManager)
    private val profileRepository = ProfileRepository()
    private val postRepository = PostRepository()
    private val storyRepository = StoryRepository()
    private val notificationRepository = NotificationRepository()
    private val searchRepository = SearchRepository()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status

    init {
        RetrofitClient.init(appContext)
    }

    fun login(identityOrUsername: String, password: String) {
        viewModelScope.launch {
            _status.value = "Logging in..."
            _status.value = when (val result = authRepository.login(identityOrUsername, password)) {
                is ApiResult.Success -> result.message
                is ApiResult.Error -> result.message
            }
        }
    }

    fun register(request: RegisterRequest) {
        viewModelScope.launch {
            _status.value = "Registering..."
            _status.value = when (val result = authRepository.register(request)) {
                is ApiResult.Success -> result.message
                is ApiResult.Error -> result.message
            }
        }
    }

    fun loadProfile(username: String) {
        viewModelScope.launch {
            _status.value = "Loading profile..."
            val response = profileRepository.getProfile(username)
            _status.value = response.body()?.data?.fullName ?: "Profile load failed"
        }
    }

    fun updateProfile(fullName: String, bio: String) {
        viewModelScope.launch {
            val response = profileRepository.updateProfile(
                UpdateProfileRequest(fullName = fullName, bio = bio)
            )
            _status.value = response.body()?.message ?: "Update failed"
        }
    }

    fun createPost(mediaUris: List<Uri>) {
        viewModelScope.launch {
            val response = postRepository.createPost(
                context = appContext,
                mediaUris = mediaUris,
                caption = "Sample caption",
                location = "Tashkent",
                hashtags = listOf("android", "stugram")
            )
            _status.value = response.body()?.message ?: "Create post failed"
        }
    }

    fun createStory(mediaUri: Uri) {
        viewModelScope.launch {
            val response = storyRepository.createStory(appContext, mediaUri, "Story caption")
            _status.value = response.body()?.message ?: "Create story failed"
        }
    }

    fun loadNotifications(page: Int = 1) {
        viewModelScope.launch {
            val response = notificationRepository.getNotifications(page = page, limit = 20)
            _status.value = "Notifications: ${response.body()?.data?.size ?: 0}"
        }
    }

    fun searchUsers(query: String, page: Int = 1) {
        viewModelScope.launch {
            val response = searchRepository.searchUsers(query, page, 20)
            _status.value = "Users found: ${response.body()?.data?.size ?: 0}"
        }
    }
}
