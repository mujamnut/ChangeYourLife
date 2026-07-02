package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.domain.AiActionLogRecord
import com.changeyourlife.cyl.backend.domain.AiActionLogRepository
import com.changeyourlife.cyl.backend.model.ErrorResponse
import com.changeyourlife.cyl.backend.model.sync.AiActionLogListResponse
import com.changeyourlife.cyl.backend.model.sync.AiActionLogSyncDto
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

fun Route.aiActionLogRoutes(aiActionLogRepository: AiActionLogRepository) {
    authenticate("auth-jwt") {
        route("/api/v1/ai-action-logs") {
            get {
                val userId = call.requireUserId() ?: return@get
                val workspaceId = call.request.queryParameters["workspaceId"]
                if (workspaceId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("workspaceId is required."))
                    return@get
                }
                val updatedAfter = call.request.queryParameters["updatedAfter"]?.toLongOrNull() ?: 0L
                val actionLogs = aiActionLogRepository.listActionLogs(
                    userId = userId,
                    workspaceId = workspaceId,
                    updatedAfter = updatedAfter,
                )
                call.respond(AiActionLogListResponse(actionLogs = actionLogs.map { it.toDto() }))
            }

            put("/{auditId}") {
                val userId = call.requireUserId() ?: return@put
                val auditId = call.parameters["auditId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Missing audit id."),
                )
                val request = call.receive<AiActionLogSyncDto>()
                if (request.auditId != auditId) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Audit id mismatch."))
                    return@put
                }
                val validationError = request.validate()
                if (validationError != null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                    return@put
                }
                val saved = aiActionLogRepository.upsertActionLog(
                    userId = userId,
                    actionLog = request.toRecord(userId),
                )
                if (saved == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("AI action log is not accessible."))
                } else {
                    call.respond(saved.toDto())
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

private fun AiActionLogSyncDto.validate(): String? {
    return when {
        auditId.isBlank() -> "auditId is required."
        requestMessageId.isBlank() -> "requestMessageId is required."
        responseMessageId.isBlank() -> "responseMessageId is required."
        sessionId.isBlank() -> "sessionId is required."
        workspaceId.isBlank() -> "workspaceId is required."
        schemaName.isBlank() -> "schemaName is required."
        schemaVersion <= 0 -> "schemaVersion must be greater than 0."
        undoState.isBlank() -> "undoState is required."
        createdAt <= 0L -> "createdAt must be greater than 0."
        updatedAt <= 0L -> "updatedAt must be greater than 0."
        else -> null
    }
}

private fun AiActionLogRecord.toDto(): AiActionLogSyncDto {
    return AiActionLogSyncDto(
        auditId = auditId,
        requestMessageId = requestMessageId,
        responseMessageId = responseMessageId,
        sessionId = sessionId,
        workspaceId = workspaceId,
        mode = mode,
        provider = provider,
        model = model,
        schemaName = schemaName,
        schemaVersion = schemaVersion,
        proposedActionsJson = proposedActionsJson,
        executedActionsJson = executedActionsJson,
        validationIssuesJson = validationIssuesJson,
        executionMessagesJson = executionMessagesJson,
        undoCommandsJson = undoCommandsJson,
        undoState = undoState,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun AiActionLogSyncDto.toRecord(userId: String): AiActionLogRecord {
    return AiActionLogRecord(
        auditId = auditId,
        userId = userId,
        requestMessageId = requestMessageId,
        responseMessageId = responseMessageId,
        sessionId = sessionId,
        workspaceId = workspaceId,
        mode = mode,
        provider = provider,
        model = model,
        schemaName = schemaName,
        schemaVersion = schemaVersion,
        proposedActionsJson = proposedActionsJson,
        executedActionsJson = executedActionsJson,
        validationIssuesJson = validationIssuesJson,
        executionMessagesJson = executionMessagesJson,
        undoCommandsJson = undoCommandsJson,
        undoState = undoState,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
