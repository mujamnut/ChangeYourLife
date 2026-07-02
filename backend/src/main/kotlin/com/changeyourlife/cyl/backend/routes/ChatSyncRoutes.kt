package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.domain.ChatMessageRecord
import com.changeyourlife.cyl.backend.domain.ChatSessionRecord
import com.changeyourlife.cyl.backend.domain.ChatSyncRepository
import com.changeyourlife.cyl.backend.model.ErrorResponse
import com.changeyourlife.cyl.backend.model.sync.ChatMessageListResponse
import com.changeyourlife.cyl.backend.model.sync.ChatMessageSyncDto
import com.changeyourlife.cyl.backend.model.sync.ChatSessionListResponse
import com.changeyourlife.cyl.backend.model.sync.ChatSessionSyncDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.chatSyncRoutes(chatSyncRepository: ChatSyncRepository) {
    authenticate("auth-jwt") {
        route("/api/v1") {
            route("/chat-sessions") {
                get {
                    val userId = call.requireUserId() ?: return@get
                    val scopeId = call.request.queryParameters["scopeId"]
                    if (scopeId.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("scopeId is required."))
                        return@get
                    }
                    val updatedAfter = call.request.queryParameters["updatedAfter"]?.toLongOrNull() ?: 0L
                    val sessions = chatSyncRepository.listSessions(
                        userId = userId,
                        scopeId = scopeId,
                        updatedAfter = updatedAfter,
                    )
                    call.respond(ChatSessionListResponse(sessions = sessions.map { it.toDto() }))
                }

                put("/{id}") {
                    val userId = call.requireUserId() ?: return@put
                    val id = call.parameters["id"] ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing chat session id."),
                    )
                    val request = call.receive<ChatSessionSyncDto>()
                    if (request.id != id) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Chat session id mismatch."))
                        return@put
                    }
                    val validationError = request.validate()
                    if (validationError != null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                        return@put
                    }
                    val saved = chatSyncRepository.upsertSession(
                        userId = userId,
                        session = request.toRecord(userId),
                    )
                    if (saved == null) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Chat session is not accessible."))
                    } else {
                        call.respond(saved.toDto())
                    }
                }
            }

            route("/chat-sessions/{sessionId}/messages") {
                get {
                    val userId = call.requireUserId() ?: return@get
                    val sessionId = call.parameters["sessionId"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing chat session id."),
                    )
                    val updatedAfter = call.request.queryParameters["updatedAfter"]?.toLongOrNull() ?: 0L
                    val messages = chatSyncRepository.listMessages(
                        userId = userId,
                        sessionId = sessionId,
                        updatedAfter = updatedAfter,
                    )
                    call.respond(ChatMessageListResponse(messages = messages.map { it.toDto() }))
                }
            }

            route("/chat-messages") {
                put("/{id}") {
                    val userId = call.requireUserId() ?: return@put
                    val id = call.parameters["id"] ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing chat message id."),
                    )
                    val request = call.receive<ChatMessageSyncDto>()
                    if (request.id != id) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Chat message id mismatch."))
                        return@put
                    }
                    val validationError = request.validate()
                    if (validationError != null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                        return@put
                    }
                    val saved = chatSyncRepository.upsertMessage(
                        userId = userId,
                        message = request.toRecord(userId),
                    )
                    if (saved == null) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Chat message session is not accessible."))
                    } else {
                        call.respond(saved.toDto())
                    }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.requireUserId(): String? {
    val userId = principal<JWTPrincipal>()?.payload?.subject
    if (userId.isNullOrBlank()) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing user identity."))
        return null
    }
    return userId
}

private fun ChatSessionSyncDto.validate(): String? {
    return when {
        id.isBlank() -> "Chat session id is required."
        scopeId.isBlank() -> "scopeId is required."
        title.isBlank() -> "Chat session title is required."
        createdAt <= 0L -> "createdAt must be greater than 0."
        updatedAt <= 0L -> "updatedAt must be greater than 0."
        else -> null
    }
}

private fun ChatMessageSyncDto.validate(): String? {
    return when {
        id.isBlank() -> "Chat message id is required."
        sessionId.isBlank() -> "sessionId is required."
        scopeId.isBlank() -> "scopeId is required."
        role.isBlank() -> "role is required."
        role !in setOf("user", "assistant", "system") -> "role must be user, assistant, or system."
        createdAt <= 0L -> "createdAt must be greater than 0."
        updatedAt <= 0L -> "updatedAt must be greater than 0."
        else -> null
    }
}

private fun ChatSessionRecord.toDto(): ChatSessionSyncDto {
    return ChatSessionSyncDto(
        id = id,
        scopeId = scopeId,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

private fun ChatSessionSyncDto.toRecord(userId: String): ChatSessionRecord {
    return ChatSessionRecord(
        id = id,
        userId = userId,
        scopeId = scopeId,
        title = title.trim(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

private fun ChatMessageRecord.toDto(): ChatMessageSyncDto {
    return ChatMessageSyncDto(
        id = id,
        sessionId = sessionId,
        scopeId = scopeId,
        role = role,
        content = content,
        pageLinksJson = pageLinksJson,
        actionMetadataJson = actionMetadataJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun ChatMessageSyncDto.toRecord(userId: String): ChatMessageRecord {
    return ChatMessageRecord(
        id = id,
        userId = userId,
        sessionId = sessionId,
        scopeId = scopeId,
        role = role,
        content = content,
        pageLinksJson = pageLinksJson,
        actionMetadataJson = actionMetadataJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
