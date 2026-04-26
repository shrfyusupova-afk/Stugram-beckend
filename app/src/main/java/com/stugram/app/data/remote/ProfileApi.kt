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
import com.stugram.app.data.remote.model.AddStoryToHighlightRequest
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.ProfileHighlightModel
import com.stugram.app.data.remote.model.CreateProfileHighlightRequest
import com.stugram.app.data.remote.model.UpdateProfileHighlightRequest
import com.stugram.app.data.remote.model.DeleteProfileHighlightResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    @GET("profiles/{username}/reels")
    suspend fun getProfileReels(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<PostModel>>

    @GET("profiles/{username}/tagged")
    suspend fun getProfileTaggedPosts(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<PostModel>>

    @GET("highlights/me")
    suspend fun getMyHighlights(): Response<BaseResponse<List<ProfileHighlightModel>>>

    @GET("highlights/{username}")
    suspend fun getProfileHighlights(
        @Path("username") username: String
    ): Response<BaseResponse<List<ProfileHighlightModel>>>

    @POST("highlights")
    suspend fun createProfileHighlight(
        @Body request: CreateProfileHighlightRequest
    ): Response<BaseResponse<ProfileHighlightModel>>

    @PATCH("highlights/{highlightId}")
    suspend fun updateProfileHighlight(
        @Path("highlightId") highlightId: String,
        @Body request: UpdateProfileHighlightRequest
    ): Response<BaseResponse<ProfileHighlightModel>>

    @DELETE("highlights/{highlightId}")
    suspend fun deleteProfileHighlight(
        @Path("highlightId") highlightId: String
    ): Response<BaseResponse<DeleteProfileHighlightResult>>

    @POST("highlights/{highlightId}/stories")
    suspend fun addStoryToHighlight(
        @Path("highlightId") highlightId: String,
        @Body request: AddStoryToHighlightRequest
    ): Response<BaseResponse<ProfileHighlightModel>>

    @DELETE("highlights/{highlightId}/stories/{storyId}")
    suspend fun removeStoryFromHighlight(
        @Path("highlightId") highlightId: String,
        @Path("storyId") storyId: String
    ): Response<BaseResponse<DeleteProfileHighlightResult>>

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
