package com.changeyourlife.cyl.backend.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText

fun Application.configureHTTP() {
    val logger = environment.log

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled request failure", cause)
            call.respondText(
                text = "Internal server error",
                status = io.ktor.http.HttpStatusCode.InternalServerError,
            )
        }
    }
}
