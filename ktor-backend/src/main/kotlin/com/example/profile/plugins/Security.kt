package com.example.profile.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureSecurity() {
    val jwtAudience = "social-media-audience"
    val jwtDomain = "https://jwt-provider-domain.com/"
    val jwtRealm = "ktor sample app"
    val jwtSecret = "secret" // Use environment variable in production

    authentication {
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtDomain)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { defaultScheme, realm ->
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
}

class JwtConfig(val secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val issuer = "https://jwt-provider-domain.com/"
    val audience = "social-media-audience"

    fun generateToken(userId: Int, username: String): String = JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim("userId", userId)
        .withClaim("username", username)
        .sign(algorithm)
}
