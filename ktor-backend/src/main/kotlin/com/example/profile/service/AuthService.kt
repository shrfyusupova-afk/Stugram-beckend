package com.example.profile.service

import com.example.profile.models.AuthResponse
import com.example.profile.models.UsersTable
import com.example.profile.plugins.JwtConfig
import com.example.profile.repository.AuthRepository
import org.mindrot.jbcrypt.BCrypt

class AuthService(
    private val repository: AuthRepository,
    private val jwtConfig: JwtConfig
) {
    suspend fun register(username: String, email: String, password: String): AuthResponse? {
        if (repository.userExists(username, email)) return null
        
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
        val userId = repository.createUser(username, email, passwordHash)
        
        val token = jwtConfig.generateToken(userId, username)
        return AuthResponse(token, "refresh-token-placeholder", username)
    }

    suspend fun login(username: String, password: String): AuthResponse? {
        val user = repository.findUserByUsername(username) ?: return null
        val hash = user[UsersTable.passwordHash]
        val userId = user[UsersTable.id]
        
        if (BCrypt.checkpw(password, hash)) {
            val token = jwtConfig.generateToken(userId, username)
            return AuthResponse(token, "refresh-token-placeholder", username)
        }
        return null
    }
}
