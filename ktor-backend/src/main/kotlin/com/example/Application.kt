package com.example

import com.example.profile.database.DatabaseFactory
import com.example.profile.database.RedisFactory
import com.example.profile.plugins.*
import com.example.profile.repository.*
import com.example.profile.routes.*
import com.example.profile.service.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    // Initialize Databases
    DatabaseFactory.init()
    RedisFactory.init()

    // Configure Plugins
    configureSecurity()
    configureStatusPages()
    configureValidation()
    configureRateLimit()
    
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // Dependency Injection (Manual for simplicity, use Koin for larger apps)
    val jwtConfig = JwtConfig("secret") // Use env var in prod
    
    val authRepository = AuthRepository()
    val authService = AuthService(authRepository, jwtConfig)
    
    val profileRepository = ProfileRepository()
    val profileService = ProfileService(profileRepository)
    
    val notificationRepository = NotificationRepository()
    val notificationService = NotificationService(notificationRepository)
    
    val mediaService = S3MediaService() // Or CloudinaryMediaService()

    // Routes
    routing {
        authRoutes(authService)
        profileRoutes(profileService)
        notificationRoutes(notificationService)
        mediaRoutes(mediaService)
    }
}
