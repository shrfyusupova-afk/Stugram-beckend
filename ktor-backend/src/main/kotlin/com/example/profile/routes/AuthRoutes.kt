package com.example.profile.routes

import com.example.profile.models.BaseResponse
import com.example.profile.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(val username: String, val password: String, val email: String? = null)

fun Route.authRoutes(service: AuthService) {
    route("/auth") {
        post("/register") {
            val request = call.receive<AuthRequest>()
            if (request.email == null) {
                return@post call.respond(HttpStatusCode.BadRequest, BaseResponse<Unit>(false, message = "Email required"))
            }
            val response = service.register(request.username, request.email, request.password)
            if (response != null) {
                call.respond(BaseResponse(true, response))
            } else {
                call.respond(HttpStatusCode.Conflict, BaseResponse<Unit>(false, message = "User already exists"))
            }
        }

        post("/login") {
            val request = call.receive<AuthRequest>()
            val response = service.login(request.username, request.password)
            if (response != null) {
                call.respond(BaseResponse(true, response))
            } else {
                call.respond(HttpStatusCode.Unauthorized, BaseResponse<Unit>(false, message = "Invalid credentials"))
            }
        }
    }
}
