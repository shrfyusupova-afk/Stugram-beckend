package com.example.profile.routes

import com.example.profile.models.*
import com.example.profile.service.ProfileService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.profileRoutes(service: ProfileService) {
    authenticate("auth-jwt") {
        route("/profile") {
            
            get("/{username}") {
                val principal = call.principal<JWTPrincipal>()
                val currentUserId = principal!!.payload.getClaim("userId").asInt()
                val targetUsername = call.parameters["username"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val profile = service.fetchProfile(currentUserId, targetUsername)
                
                if (profile != null) {
                    call.respond(BaseResponse(true, profile))
                } else {
                    call.respond(HttpStatusCode.NotFound, BaseResponse<Unit>(false, message = "User not found"))
                }
            }

            patch("/edit") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()
                val username = principal.payload.getClaim("username").asString()
                val request = call.receive<EditProfileRequest>()
                val success = service.updateUserInfo(userId, username, request)
                call.respond(BaseResponse(success, message = if (success) "Updated" else "Failed"))
            }

            get("/{username}/posts") {
                val username = call.parameters["username"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                
                val posts = service.fetchPosts(username, "POST", page, limit)
                call.respond(BaseResponse(true, posts))
            }

            get("/{username}/reels") {
                val username = call.parameters["username"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                
                val reels = service.fetchPosts(username, "REEL", page, limit)
                call.respond(BaseResponse(true, reels))
            }

            post("/follow/{targetId}") {
                val followerId = 1 // Mock from JWT
                val targetId = call.parameters["targetId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val targetUsername = call.request.queryParameters["username"] ?: "" 
                val message = service.toggleFollow(followerId, targetId, targetUsername)
                call.respond(BaseResponse(true, data = message))
            }

            get("/follow-requests") {
                val userId = 1 // Mock from JWT
                val requests = repository.getFollowRequests(userId) // Directly using repo for brevity or add to service
                call.respond(BaseResponse(true, requests))
            }

            post("/follow-requests/{followerId}/accept") {
                val userId = 1 // Mock from JWT
                val followerId = call.parameters["followerId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val success = repository.handleFollowRequest(followerId, userId, true)
                call.respond(BaseResponse(success))
            }

            post("/follow-requests/{followerId}/reject") {
                val userId = 1 // Mock from JWT
                val followerId = call.parameters["followerId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val success = repository.handleFollowRequest(followerId, userId, false)
                call.respond(BaseResponse(success))
            }

            get("/search") {
                val query = call.request.queryParameters["q"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val results = service.search(query)
                call.respond(BaseResponse(true, results))
            }

            get("/check-username") {
                val username = call.request.queryParameters["username"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val available = service.checkUsername(username)
                call.respond(BaseResponse(true, mapOf("available" to available)))
            }
        }
    }
}
