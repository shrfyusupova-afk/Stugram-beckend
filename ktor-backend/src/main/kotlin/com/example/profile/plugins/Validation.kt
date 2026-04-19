package com.example.profile.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import com.example.profile.routes.AuthRequest

fun Application.configureValidation() {
    install(RequestValidation) {
        validate<AuthRequest> { request ->
            if (request.username.length < 3) {
                ValidationResult.Invalid("Username must be at least 3 characters long")
            } else if (request.password.length < 6) {
                ValidationResult.Invalid("Password must be at least 6 characters long")
            } else {
                ValidationResult.Valid
            }
        }
    }
}
