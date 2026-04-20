package com.stugram.app.data.remote

import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.StoryModel
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface MediaApi {
    @Multipart
    @POST("profiles/me/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<BaseResponse<ProfileModel>>

    @Multipart
    @POST("profiles/me/banner")
    suspend fun uploadBanner(
        @Part banner: MultipartBody.Part
    ): Response<BaseResponse<ProfileModel>>

    @Multipart
    @POST("posts")
    suspend fun createPost(
        @Part media: List<MultipartBody.Part>,
        @Part("caption") caption: RequestBody?,
        @Part("location") location: RequestBody?,
        @Part hashtags: List<MultipartBody.Part>
    ): Response<BaseResponse<PostModel>>

    @Multipart
    @POST("stories")
    suspend fun createStory(
        @Part media: MultipartBody.Part,
        @Part("caption") caption: RequestBody?
    ): Response<BaseResponse<StoryModel>>
}
