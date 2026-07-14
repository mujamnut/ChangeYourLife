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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    if (id.isBlank()) return "Chat message id is required."
    if (sessionId.isBlank()) return "sessionId is required."
    if (scopeId.isBlank()) return "scopeId is required."
    if (role.isBlank()) return "role is required."
    if (role !in setOf("user", "assistant", "system")) {
        return "role must be user, assistant, or system."
    }
    if (attachmentsJson.length > MaxChatAttachmentsJsonLength) return "attachmentsJson is too large."
    attachmentsJson.validateChatAttachmentsJson()?.let { return it }
    if (createdAt <= 0L) return "createdAt must be greater than 0."
    if (updatedAt <= 0L) return "updatedAt must be greater than 0."
    return null
}

private fun String.validateChatAttachmentsJson(): String? {
    val attachments = runCatching { Json.parseToJsonElement(this).jsonArray }
        .getOrElse { return "attachmentsJson must be a JSON array." }
    if (attachments.size > MaxChatAttachmentCount) {
        return "A chat message supports at most $MaxChatAttachmentCount attachments."
    }
    attachments.forEachIndexed { index, element ->
        val attachment = runCatching { element.jsonObject }
            .getOrElse { return "Attachment $index must be a JSON object." }
        val id = runCatching { attachment["id"]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
        val name = runCatching { attachment["name"]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
        val kind = runCatching { attachment["kind"]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
        val preview = runCatching {
            attachment["previewDataUrl"]?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty()
        if (id.isBlank()) return "Attachment $index id is required."
        if (name.isBlank()) return "Attachment $index name is required."
        if (kind !in setOf("image", "text")) return "Attachment $index kind is invalid."
        if (preview.length > MaxChatAttachmentPreviewLength) {
            return "Attachment $index preview is too large."
        }
        if (preview.isNotBlank() && !preview.startsWith("data:image/")) {
            return "Attachment $index preview must be an image data URL."
        }
    }
    return null
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
        attachmentsJson = attachmentsJson,
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
        attachmentsJson = attachmentsJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private const val MaxChatAttachmentsJsonLength = 700_000
private const val MaxChatAttachmentCount = 4
private const val MaxChatAttachmentPreviewLength = 150_000
