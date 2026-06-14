package com.changeyourlife.cyl.backend.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.changeyourlife.cyl.backend.config.JwtConfig
import com.changeyourlife.cyl.backend.domain.UserAccount
import java.util.Date

class JwtService(
    private val config: JwtConfig,
) {
    private val algorithm = Algorithm.HMAC256(config.secret)

    fun generateToken(user: UserAccount): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(user.id)
            .withClaim("email", user.email)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + config.expiresInMillis))
            .sign(algorithm)
    }

    fun verifier() = JWT
        .require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()
}

