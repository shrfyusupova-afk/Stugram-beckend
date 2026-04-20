package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.ProfileQuickSummaryModel
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.remote.model.UpdateProfileRequest
import com.stugram.app.data.remote.model.UsernameAvailabilityResponse
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.AccountProfileItemModel
import com.stugram.app.data.remote.model.CreateProfileRequest
import com.stugram.app.data.remote.model.AuthPayload
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface ProfileApi {
    @GET("profiles/me")
    suspend fun getCurrentProfile(): Response<BaseResponse<ProfileModel>>

    @GET("profiles/{username}")
    suspend fun getProfile(@Path("username") username: String): Response<BaseResponse<ProfileModel>>

    @GET("profiles/{username}/summary")
    suspend fun getProfileSummary(@Path("username") username: String): Response<BaseResponse<ProfileQuickSummaryModel>>

    @PATCH("profiles/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<BaseResponse<ProfileModel>>

    @GET("profiles/check-username")
    suspend fun checkUsername(@Query("username") username: String): Response<BaseResponse<UsernameAvailabilityResponse>>

    @GET("profiles/suggestions")
    suspend fun getProfileSuggestions(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ProfileSummary>>

    @GET("profiles/me/all")
    suspend fun getMyProfilesAll(): Response<BaseResponse<List<AccountProfileItemModel>>>

    @POST("profiles")
    suspend fun createProfile(@Body request: CreateProfileRequest): Response<BaseResponse<AuthPayload>>
}
