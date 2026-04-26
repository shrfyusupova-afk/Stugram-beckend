package com.stugram.app.data.repository

import android.content.Context
import android.net.Uri
import com.stugram.app.data.remote.DeleteResult
import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.BaseResponse
import com.stugram.app.data.remote.model.PaginatedResponse
import com.stugram.app.data.remote.model.PostModel
import com.stugram.app.data.remote.model.UpdatePostRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class PostRepository {
    private val postApi get() = RetrofitClient.postApi
    private val mediaApi get() = RetrofitClient.mediaApi

    suspend fun createPost(
        context: Context,
        mediaUris: List<Uri>,
        caption: String?,
        location: String?,
        hashtags: List<String>
    ): Response<BaseResponse<PostModel>> {
        val preparedFiles = mediaUris.mapIndexed { index, uri ->
            val mimeType = context.contentResolver.getType(uri) ?: "image/*"
            val file = context.copyUriToTempFile(uri, "post_$index", mimeType)
            file to normalizeMediaUploadMimeType(mimeType, file.name)
        }
        return try {
            val mediaParts = preparedFiles.map { (file, mimeType) ->
                MultipartBody.Part.createFormData(
                    "media",
                    file.name,
                    file.asRequestBody(mimeType.toMediaType())
                )
            }
            val captionPart = caption?.toRequestBody("text/plain".toMediaType())
            val locationPart = location?.toRequestBody("text/plain".toMediaType())
            // Multer exposes a single repeated multipart field as a String; duplicating
            // one tag keeps the deployed backend validator receiving an Array.
            val multipartHashtags = when (hashtags.size) {
                0 -> emptyList()
                1 -> listOf(hashtags.first(), hashtags.first())
                else -> hashtags
            }
            val hashtagParts = multipartHashtags.map {
                MultipartBody.Part.createFormData("hashtags", it)
            }
            mediaApi.createPost(mediaParts, captionPart, locationPart, hashtagParts)
        } finally {
            preparedFiles.forEach { (file, _) -> file.delete() }
        }
    }

    suspend fun updatePost(postId: String, request: UpdatePostRequest) =
        postApi.updatePost(postId, request)

    suspend fun deletePost(postId: String): Response<BaseResponse<DeleteResult>> =
        postApi.deletePost(postId)

    suspend fun getPost(postId: String): Response<BaseResponse<PostModel>> =
        postApi.getPost(postId)

    suspend fun getUserPosts(username: String, page: Int, limit: Int): Response<PaginatedResponse<PostModel>> =
        postApi.getUserPosts(username, page, limit)

    suspend fun getFeed(page: Int, limit: Int): Response<PaginatedResponse<PostModel>> =
        postApi.getFeed(page, limit)

}
