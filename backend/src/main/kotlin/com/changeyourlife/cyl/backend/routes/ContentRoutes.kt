package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import com.changeyourlife.cyl.backend.model.ErrorResponse
import com.changeyourlife.cyl.backend.model.sync.PageListResponse
import com.changeyourlife.cyl.backend.model.sync.PageSyncDto
import com.changeyourlife.cyl.backend.model.sync.WorkspaceListResponse
import com.changeyourlife.cyl.backend.model.sync.WorkspaceSyncDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.contentRoutes(contentRepository: ContentRepository) {
    authenticate("auth-jwt") {
        route("/api/v1") {
            route("/workspaces") {
                get {
                    val userId = call.requireUserId() ?: return@get
                    val includeDeleted = call.request.queryParameters["includeDeleted"].toBooleanFlag()
                    val workspaces = contentRepository.listWorkspaces(
                        userId = userId,
                        includeDeleted = includeDeleted,
                    )
                    call.respond(
                        WorkspaceListResponse(
                            workspaces = workspaces.map { it.toDto() },
                        ),
                    )
                }

                put("/{id}") {
                    val userId = call.requireUserId() ?: return@put
                    val id = call.parameters["id"] ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing workspace id."),
                    )
                    val request = call.receive<WorkspaceSyncDto>()
                    if (request.id != id) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Workspace id mismatch."))
                        return@put
                    }
                    val validationError = request.validate()
                    if (validationError != null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                        return@put
                    }
                    val saved = contentRepository.upsertWorkspace(request.toRecord(userId))
                    if (saved == null) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Workspace is not accessible."))
                        return@put
                    }
                    call.respond(saved.toDto())
                }

                delete("/{id}") {
                    val userId = call.requireUserId() ?: return@delete
                    val id = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing workspace id."),
                    )
                    val deleted = contentRepository.softDeleteWorkspace(
                        userId = userId,
                        workspaceId = id,
                        deletedAt = System.currentTimeMillis(),
                    )
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Workspace not found."))
                    }
                }
            }

            route("/pages") {
                get {
                    val userId = call.requireUserId() ?: return@get
                    val workspaceId = call.request.queryParameters["workspaceId"]
                    if (workspaceId.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("workspaceId is required."))
                        return@get
                    }
                    val includeDeleted = call.request.queryParameters["includeDeleted"].toBooleanFlag()
                    val pages = contentRepository.listPages(
                        userId = userId,
                        workspaceId = workspaceId,
                        includeDeleted = includeDeleted,
                    )
                    call.respond(PageListResponse(pages = pages.map { it.toDto() }))
                }

                get("/{id}") {
                    val userId = call.requireUserId() ?: return@get
                    val id = call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val includeDeleted = call.request.queryParameters["includeDeleted"].toBooleanFlag()
                    val page = contentRepository.getPage(
                        userId = userId,
                        pageId = id,
                        includeDeleted = includeDeleted,
                    )
                    if (page == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Page not found."))
                    } else {
                        call.respond(page.toDto())
                    }
                }

                put("/{id}") {
                    val userId = call.requireUserId() ?: return@put
                    val id = call.parameters["id"] ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val request = call.receive<PageSyncDto>()
                    if (request.id != id) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Page id mismatch."))
                        return@put
                    }
                    val validationError = request.validate()
                    if (validationError != null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                        return@put
                    }
                    val saved = contentRepository.upsertPage(
                        userId = userId,
                        page = request.toRecord(),
                    )
                    if (saved == null) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Page workspace is not accessible."))
                        return@put
                    }
                    call.respond(saved.toDto())
                }

                post("/{id}/restore") {
                    val userId = call.requireUserId() ?: return@post
                    val id = call.parameters["id"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val restored = contentRepository.restorePage(
                        userId = userId,
                        pageId = id,
                        restoredAt = System.currentTimeMillis(),
                    )
                    if (restored) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Page not found."))
                    }
                }

                delete("/{id}") {
                    val userId = call.requireUserId() ?: return@delete
                    val id = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val deleted = contentRepository.softDeletePage(
                        userId = userId,
                        pageId = id,
                        deletedAt = System.currentTimeMillis(),
                    )
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Page not found."))
                    }
                }

                delete("/{id}/permanent") {
                    val userId = call.requireUserId() ?: return@delete
                    val id = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val deleted = contentRepository.deletePagePermanently(
                        userId = userId,
                        pageId = id,
                    )
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Page not found."))
                    }
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireUserId(): String? {
    val userId = principal<JWTPrincipal>()?.payload?.subject
    if (userId.isNullOrBlank()) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing user identity."))
        return null
    }
    return userId
}

private fun String?.toBooleanFlag(): Boolean {
    return equals("true", ignoreCase = true) || this == "1"
}

private fun WorkspaceSyncDto.validate(): String? {
    return when {
        id.isBlank() -> "Workspace id is required."
        name.isBlank() -> "Workspace name is required."
        else -> null
    }
}

private fun PageSyncDto.validate(): String? {
    return when {
        id.isBlank() -> "Page id is required."
        workspaceId.isBlank() -> "workspaceId is required."
        title.isBlank() -> "Page title is required."
        else -> null
    }
}

private fun WorkspaceRecord.toDto(): WorkspaceSyncDto {
    return WorkspaceSyncDto(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

private fun WorkspaceSyncDto.toRecord(userId: String): WorkspaceRecord {
    return WorkspaceRecord(
        id = id,
        userId = userId,
        name = name.trim(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

private fun PageRecord.toDto(): PageSyncDto {
    return PageSyncDto(
        id = id,
        workspaceId = workspaceId,
        parentPageId = parentPageId,
        title = title,
        content = content,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

private fun PageSyncDto.toRecord(): PageRecord {
    return PageRecord(
        id = id,
        workspaceId = workspaceId,
        parentPageId = parentPageId,
        title = title.trim(),
        content = content,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}
