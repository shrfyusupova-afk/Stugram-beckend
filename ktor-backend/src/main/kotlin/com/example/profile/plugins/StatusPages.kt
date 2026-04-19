package com.example.profile.plugins

import com.example.profile.models.BaseResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                BaseResponse<Unit>(false, message = cause.localizedMessage ?: "An unexpected error occurred")
            )
        }
        
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(status, BaseResponse<Unit>(false, message = "Unauthorized access"))
        }

        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, BaseResponse<Unit>(false, message = "Resource not found"))
        }
    }
}
