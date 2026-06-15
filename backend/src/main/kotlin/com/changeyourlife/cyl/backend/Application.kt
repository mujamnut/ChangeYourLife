package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.config.AppConfig
import com.changeyourlife.cyl.backend.data.InMemoryUserRepository
import com.changeyourlife.cyl.backend.data.PostgresUserRepository
import com.changeyourlife.cyl.backend.domain.UserRepository
import com.changeyourlife.cyl.backend.plugins.configureAuthentication
import com.changeyourlife.cyl.backend.plugins.configureDatabase
import com.changeyourlife.cyl.backend.plugins.configureHTTP
import com.changeyourlife.cyl.backend.plugins.configureMonitoring
import com.changeyourlife.cyl.backend.plugins.configureRouting
import com.changeyourlife.cyl.backend.plugins.configureSerialization
import com.changeyourlife.cyl.backend.service.AiService
import com.changeyourlife.cyl.backend.service.JwtService
import io.ktor.server.application.Application
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    val developmentMode = System.getenv("KTOR_DEVELOPMENT")?.toBooleanStrictOrNull() ?: true

    embeddedServer(
        factory = Netty,
        rootConfig = serverConfig {
            this.developmentMode = developmentMode
            watchPaths = if (developmentMode) listOf("classes", "resources") else emptyList()
            module {
                module()
            }
        },
        configure = {
            connectors.add(
                EngineConnectorBuilder().apply {
                    this.host = host
                    this.port = port
                },
            )
        },
    ).start(wait = true)
}

fun Application.module(
    appConfig: AppConfig = AppConfig.fromEnvironment(),
    userRepositoryOverride: UserRepository? = null,
) {
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureAuthentication(appConfig.jwt)

    val dataSource = configureDatabase(appConfig.database)
    val userRepository = userRepositoryOverride
        ?: dataSource?.let { PostgresUserRepository(it) }
        ?: InMemoryUserRepository()

    val aiService = AiService(
        glmApiKey = appConfig.glmApiKey,
        geminiApiKey = appConfig.geminiApiKey,
        openRouterApiKey = appConfig.openRouterApiKey,
        openRouterModel = appConfig.openRouterModel,
    )
    environment.log.info(
        "AI provider initialized: provider=${aiService.activeProvider}, model=${aiService.activeModel}, apiKeyConfigured=${!aiService.isMockMode}",
    )

    configureRouting(
        userRepository = userRepository,
        jwtService = JwtService(appConfig.jwt),
        databaseConfigured = dataSource != null,
        aiService = aiService,
    )
}
