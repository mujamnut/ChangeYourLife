package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.domain.AiSkillRecord
import com.changeyourlife.cyl.backend.domain.AiSkillSyncRepository
import com.changeyourlife.cyl.backend.model.ErrorResponse
import com.changeyourlife.cyl.backend.model.sync.AiSkillListResponse
import com.changeyourlife.cyl.backend.model.sync.AiSkillSyncDto
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

fun Route.aiSkillSyncRoutes(aiSkillSyncRepository: AiSkillSyncRepository) {
    authenticate("auth-jwt") {
        route("/api/v1/ai-skills") {
            get {
                val userId = call.requireSkillUserId() ?: return@get
                val workspaceId = call.request.queryParameters["workspaceId"]
                if (workspaceId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("workspaceId is required."))
                    return@get
                }
                val includeDeleted = call.request.queryParameters["includeDeleted"].toBooleanFlag()
                val skills = aiSkillSyncRepository.list(
                    userId = userId,
                    workspaceId = workspaceId,
                    includeDeleted = includeDeleted,
                )
                call.respond(AiSkillListResponse(skills = skills.map(AiSkillRecord::toDto)))
            }

            put("/{id}") {
                val userId = call.requireSkillUserId() ?: return@put
                val skillId = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Missing skill id."),
                )
                val request = call.receive<AiSkillSyncDto>()
                if (request.id != skillId) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Skill id mismatch."))
                    return@put
                }
                val validationError = request.validate()
                if (validationError != null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                    return@put
                }
                val saved = aiSkillSyncRepository.upsert(
                    userId = userId,
                    skill = request.toRecord(),
                )
                if (saved == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("AI skill is not accessible."))
                } else {
                    call.respond(saved.toDto())
                }
            }
        }
    }
}

private suspend fun ApplicationCall.requireSkillUserId(): String? {
    val userId = principal<JWTPrincipal>()?.payload?.subject
    if (userId.isNullOrBlank()) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing user identity."))
        return null
    }
    return userId
}

private fun AiSkillSyncDto.validate(): String? {
    return when {
        id.isBlank() -> "id is required."
        workspaceId.isBlank() -> "workspaceId is required."
        name.isBlank() -> "name is required."
        name.length > MaxSkillNameChars -> "name must be at most $MaxSkillNameChars characters."
        whenToUse.isBlank() -> "whenToUse is required."
        whenToUse.length > MaxSkillWhenChars -> "whenToUse must be at most $MaxSkillWhenChars characters."
        instructions.isBlank() -> "instructions are required."
        instructions.length > MaxSkillInstructionsChars ->
            "instructions must be at most $MaxSkillInstructionsChars characters."
        createdAt <= 0L -> "createdAt must be greater than 0."
        updatedAt < createdAt -> "updatedAt must not be earlier than createdAt."
        deletedAt != null && deletedAt <= 0L -> "deletedAt must be greater than 0."
        else -> null
    }
}

private fun AiSkillRecord.toDto(): AiSkillSyncDto = AiSkillSyncDto(
    id = id,
    workspaceId = workspaceId,
    name = name,
    whenToUse = whenToUse,
    instructions = instructions,
    isEnabled = isEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun AiSkillSyncDto.toRecord(): AiSkillRecord = AiSkillRecord(
    id = id,
    workspaceId = workspaceId,
    name = name.trim(),
    whenToUse = whenToUse.trim(),
    instructions = instructions.trim(),
    isEnabled = isEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun String?.toBooleanFlag(): Boolean {
    return equals("true", ignoreCase = true) || this == "1"
}

private const val MaxSkillNameChars = 64
private const val MaxSkillWhenChars = 320
private const val MaxSkillInstructionsChars = 2_000
