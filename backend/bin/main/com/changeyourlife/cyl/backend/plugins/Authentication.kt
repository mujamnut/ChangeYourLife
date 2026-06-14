package com.changeyourlife.cyl.backend.plugins

import com.changeyourlife.cyl.backend.config.JwtConfig
import com.changeyourlife.cyl.backend.service.JwtService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt

fun Application.configureAuthentication(jwtConfig: JwtConfig) {
    val jwtService = JwtService(jwtConfig)

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtConfig.realm
            verifier(jwtService.verifier())
            validate { credential ->
                if (credential.payload.audience.contains(jwtConfig.audience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

