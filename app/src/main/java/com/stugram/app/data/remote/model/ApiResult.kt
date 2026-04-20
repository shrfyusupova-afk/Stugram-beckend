package com.stugram.app.data.remote.model

sealed interface ApiResult<out T> {
    data class Success<T>(
        val data: T?,
        val message: String
    ) : ApiResult<T>

    data class Error(
        val message: String,
        val code: Int? = null
    ) : ApiResult<Nothing>
}
