package com.example.profile.routes

import com.example.profile.models.BaseResponse
import com.example.profile.service.NotificationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.notificationRoutes(service: NotificationService) {
    authenticate("auth-jwt") {
        route("/notifications") {
            get {
                val userId = 1 // Mock from JWT
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                
                val notifications = service.getNotifications(userId, page, limit)
                call.respond(BaseResponse(true, notifications))
            }

            post("/read") {
                val userId = 1 // Mock from JWT
                service.markAllAsRead(userId)
                call.respond(BaseResponse(true, message = "Notifications marked as read"))
            }
        }
    }
}
