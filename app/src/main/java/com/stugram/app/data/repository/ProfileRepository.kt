package com.stugram.app.data.repository

import android.content.Context
import android.net.Uri
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.ProfileQuickSummaryModel
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.UpdateProfileRequest
import com.stugram.app.data.remote.model.UsernameAvailabilityResponse
import com.stugram.app.data.remote.model.AccountProfileItemModel
import com.stugram.app.data.remote.model.AuthPayload
import com.stugram.app.data.remote.model.AddStoryToHighlightRequest
import com.stugram.app.data.remote.model.CreateProfileRequest
import com.stugram.app.data.remote.model.CreateProfileHighlightRequest
import com.stugram.app.data.remote.model.DeleteProfileHighlightResult
import com.stugram.app.data.remote.model.ProfileHighlightModel
import com.stugram.app.data.remote.model.SwitchProfileRequest
import com.stugram.app.data.remote.model.UpdateProfileHighlightRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response

class ProfileRepository {
    companion object {
        private val highlightCache = linkedMapOf<String, List<ProfileHighlightModel>>()
    }

    private val profileApi get() = RetrofitClient.profileApi
    private val mediaApi get() = RetrofitClient.mediaApi
    private val authApi get() = RetrofitClient.authApi

    suspend fun getCurrentProfile(): Response<BaseResponse<ProfileModel>> =
        profileApi.getCurrentProfile()

    suspend fun getProfile(username: String): Response<BaseResponse<ProfileModel>> =
        profileApi.getProfile(username)

    suspend fun getProfileSummary(username: String): Response<BaseResponse<ProfileQuickSummaryModel>> =
        profileApi.getProfileSummary(username)

    suspend fun getProfileReels(username: String, page: Int = 1, limit: Int = 20): Response<PaginatedResponse<PostModel>> =
        profileApi.getProfileReels(username, page, limit)

    suspend fun getProfileTaggedPosts(username: String, page: Int = 1, limit: Int = 20): Response<PaginatedResponse<PostModel>> =
        profileApi.getProfileTaggedPosts(username, page, limit)

    suspend fun getMyProfileHighlights(cacheKey: String, force: Boolean = false): Response<BaseResponse<List<ProfileHighlightModel>>> {
        if (!force) {
            highlightCache[cacheKey]?.let { cached ->
                return Response.success(BaseResponse(success = true, message = "Cached highlights", data = cached, meta = null))
            }
        }
        val response = profileApi.getMyHighlights()
        if (response.isSuccessful) {
            response.body()?.data?.let { highlightCache[cacheKey] = it }
        }
        return response
    }

    suspend fun getProfileHighlights(username: String, force: Boolean = false): Response<BaseResponse<List<ProfileHighlightModel>>> {
        val cacheKey = "user:$username"
        if (!force) {
            highlightCache[cacheKey]?.let { cached ->
                return Response.success(BaseResponse(success = true, message = "Cached highlights", data = cached, meta = null))
            }
        }
        val response = profileApi.getProfileHighlights(username)
        if (response.isSuccessful) {
            response.body()?.data?.let { highlightCache[cacheKey] = it }
        }
        return response
    }

    suspend fun createProfileHighlight(cacheKeys: List<String>, request: CreateProfileHighlightRequest): Response<BaseResponse<ProfileHighlightModel>> {
        val response = profileApi.createProfileHighlight(request)
        if (response.isSuccessful) invalidateHighlightCache(*cacheKeys.toTypedArray())
        return response
    }

    suspend fun updateProfileHighlight(cacheKeys: List<String>, highlightId: String, request: UpdateProfileHighlightRequest): Response<BaseResponse<ProfileHighlightModel>> {
        val response = profileApi.updateProfileHighlight(highlightId, request)
        if (response.isSuccessful) invalidateHighlightCache(*cacheKeys.toTypedArray())
        return response
    }

    suspend fun deleteProfileHighlight(cacheKeys: List<String>, highlightId: String): Response<BaseResponse<DeleteProfileHighlightResult>> {
        val response = profileApi.deleteProfileHighlight(highlightId)
        if (response.isSuccessful) invalidateHighlightCache(*cacheKeys.toTypedArray())
        return response
    }

    suspend fun addStoryToHighlight(
        cacheKeys: List<String>,
        highlightId: String,
        request: AddStoryToHighlightRequest
    ): Response<BaseResponse<ProfileHighlightModel>> {
        val response = profileApi.addStoryToHighlight(highlightId, request)
        if (response.isSuccessful) invalidateHighlightCache(*cacheKeys.toTypedArray())
        return response
    }

    suspend fun removeStoryFromHighlight(
        cacheKeys: List<String>,
        highlightId: String,
        storyId: String
    ): Response<BaseResponse<DeleteProfileHighlightResult>> {
        val response = profileApi.removeStoryFromHighlight(highlightId, storyId)
        if (response.isSuccessful) invalidateHighlightCache(*cacheKeys.toTypedArray())
        return response
    }

    fun invalidateHighlightCache(vararg keys: String) {
        if (keys.isEmpty()) {
            highlightCache.clear()
            return
        }
        keys.forEach { key -> highlightCache.remove(key) }
    }

    suspend fun updateProfile(request: UpdateProfileRequest): Response<BaseResponse<ProfileModel>> =
        profileApi.updateProfile(request)

    suspend fun checkUsernameAvailability(username: String): Response<BaseResponse<UsernameAvailabilityResponse>> =
        profileApi.checkUsername(username)

    suspend fun getProfileSuggestions(page: Int = 1, limit: Int = 20): Response<PaginatedResponse<ProfileSummary>> =
        profileApi.getProfileSuggestions(page, limit)

    suspend fun getMyProfilesAll(): Response<BaseResponse<List<AccountProfileItemModel>>> =
        profileApi.getMyProfilesAll()

    suspend fun switchProfile(profileId: String): Response<BaseResponse<AuthPayload>> =
        authApi.switchProfile(SwitchProfileRequest(profileId))

    suspend fun createProfile(request: CreateProfileRequest): Response<BaseResponse<AuthPayload>> =
        profileApi.createProfile(request)

    suspend fun uploadAvatar(context: Context, uri: Uri): Response<BaseResponse<ProfileModel>> {
        val mimeType = context.contentResolver.getType(uri) ?: "image/*"
        val file = context.copyUriToTempFile(uri, "avatar", mimeType)
        return try {
            val requestFile = file.asRequestBody(mimeType.toMediaType())
            val avatarPart = MultipartBody.Part.createFormData("avatar", file.name, requestFile)
            mediaApi.uploadAvatar(avatarPart)
        } finally {
            file.delete()
        }
    }

    suspend fun uploadBanner(context: Context, uri: Uri): Response<BaseResponse<ProfileModel>> {
        val mimeType = context.contentResolver.getType(uri) ?: "image/*"
        val file = context.copyUriToTempFile(uri, "banner", mimeType)
        return try {
            val requestFile = file.asRequestBody(mimeType.toMediaType())
            val bannerPart = MultipartBody.Part.createFormData("banner", file.name, requestFile)
            mediaApi.uploadBanner(bannerPart)
        } finally {
            file.delete()
        }
    }
}
