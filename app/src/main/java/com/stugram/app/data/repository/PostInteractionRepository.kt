package com.stugram.app.data.repository

import com.stugram.app.data.remote.RetrofitClient
import com.stugram.app.data.remote.model.CommentModel
import com.stugram.app.data.remote.model.CreateCommentRequest
import com.stugram.app.data.remote.model.LikeActionData
import com.stugram.app.data.remote.model.PostInteractionHistoryItem
import com.stugram.app.data.remote.model.SavedPostActionData

class PostInteractionRepository {
    private val likeApi get() = RetrofitClient.likeApi
    private val commentApi get() = RetrofitClient.commentApi
    private val postApi get() = RetrofitClient.postApi

    suspend fun likePost(postId: String) = likeApi.likePost(postId)

    suspend fun unlikePost(postId: String) = likeApi.unlikePost(postId)

    suspend fun savePost(postId: String) = postApi.savePost(postId)

    suspend fun unsavePost(postId: String) = postApi.unsavePost(postId)

    suspend fun getLikedPosts(page: Int = 1, limit: Int = 100) = likeApi.getLikedPosts(page, limit)

    suspend fun getSavedPosts(page: Int = 1, limit: Int = 100) = postApi.getSavedPosts(page, limit)

    suspend fun getComments(postId: String, page: Int = 1, limit: Int = 50) = commentApi.getComments(postId, page, limit)

    suspend fun addComment(postId: String, content: String, parentCommentId: String? = null) =
        commentApi.addComment(postId, CreateCommentRequest(content = content, parentCommentId = parentCommentId))
}
