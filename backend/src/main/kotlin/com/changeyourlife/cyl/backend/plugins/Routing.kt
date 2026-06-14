package com.changeyourlife.cyl.backend.plugins

import com.changeyourlife.cyl.backend.domain.UserRepository
import com.changeyourlife.cyl.backend.model.HealthResponse
import com.changeyourlife.cyl.backend.routes.aiRoutes
import com.changeyourlife.cyl.backend.routes.authRoutes
import com.changeyourlife.cyl.backend.service.AiService
import com.changeyourlife.cyl.backend.service.JwtService
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting(
    userRepository: UserRepository,
    jwtService: JwtService,
    databaseConfigured: Boolean,
    aiService: AiService,
) {
    routing {
        get("/") {
            call.respondText("ChangeYourLife API")
        }

        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    service = "cyl-backend",
                    database = if (databaseConfigured) "configured" else "in-memory",
                ),
            )
        }

        authRoutes(
            userRepository = userRepository,
            jwtService = jwtService,
        )

        aiRoutes(
            aiService = aiService,
        )
    }
}
