package com.stugram.app.data.repository

import android.content.Context
import android.net.Uri
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.ProfileQuickSummaryModel
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.UpdateProfileRequest
import com.stugram.app.data.remote.model.UsernameAvailabilityResponse
import com.stugram.app.data.remote.model.AccountProfileItemModel
import com.stugram.app.data.remote.model.AuthPayload
import com.stugram.app.data.remote.model.CreateProfileRequest
import com.stugram.app.data.remote.model.SwitchProfileRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response

class ProfileRepository {
    private val profileApi get() = RetrofitClient.profileApi
    private val mediaApi get() = RetrofitClient.mediaApi
    private val authApi get() = RetrofitClient.authApi

    suspend fun getCurrentProfile(): Response<BaseResponse<ProfileModel>> =
        profileApi.getCurrentProfile()

    suspend fun getProfile(username: String): Response<BaseResponse<ProfileModel>> =
        profileApi.getProfile(username)

    suspend fun getProfileSummary(username: String): Response<BaseResponse<ProfileQuickSummaryModel>> =
        profileApi.getProfileSummary(username)

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
