package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.data.PageContentJsonMutator
import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import com.changeyourlife.cyl.backend.model.ErrorResponse
import com.changeyourlife.cyl.backend.model.sync.PageBlockCreateRequest
import com.changeyourlife.cyl.backend.model.sync.PageListResponse
import com.changeyourlife.cyl.backend.model.sync.PageBlockPatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageElementPositionPatchRequest
import com.changeyourlife.cyl.backend.model.sync.PagePropertyCreateRequest
import com.changeyourlife.cyl.backend.model.sync.PagePropertyValuePatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageSyncDto
import com.changeyourlife.cyl.backend.model.sync.PageTableColumnCreateRequest
import com.changeyourlife.cyl.backend.model.sync.PageTableColumnPatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageTableCellValuePatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageTablePatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageTableRowCreateRequest
import com.changeyourlife.cyl.backend.model.sync.PageTableRowPatchRequest
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
import io.ktor.server.routing.patch
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

                post("/{id}/blocks") {
                    val userId = call.requireUserId() ?: return@post
                    val id = call.parameters["id"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val request = call.receive<PageBlockCreateRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page block parent not found.",
                    ) { content ->
                        PageContentJsonMutator.addBlock(
                            content = content,
                            blockId = request.blockId,
                            type = request.type,
                            text = request.text,
                            parentBlockId = request.parentBlockId,
                            afterBlockId = request.afterBlockId,
                            targetIndex = request.targetIndex,
                        )
                    }
                }

                delete("/{id}/blocks/{blockId}") {
                    val userId = call.requireUserId() ?: return@delete
                    val id = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val blockId = call.parameters["blockId"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing block id."),
                    )
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page block not found.",
                    ) { content ->
                        PageContentJsonMutator.deleteBlock(
                            content = content,
                            blockId = blockId,
                        )
                    }
                }

                patch("/{id}/blocks/{blockId}/position") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val blockId = call.parameters["blockId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing block id."),
                    )
                    val request = call.receive<PageElementPositionPatchRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page block not found.",
                    ) { content ->
                        PageContentJsonMutator.moveBlock(
                            content = content,
                            blockId = blockId,
                            targetIndex = request.targetIndex,
                        )
                    }
                }

                patch("/{id}/blocks/{blockId}") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val blockId = call.parameters["blockId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing block id."),
                    )
                    val request = call.receive<PageBlockPatchRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page block not found.",
                    ) { content ->
                        PageContentJsonMutator.updateBlock(
                            content = content,
                            blockId = blockId,
                            text = request.text,
                            richTextSpans = request.richTextSpans,
                            mediaAttachments = request.mediaAttachments,
                            isChecked = request.isChecked,
                        )
                    }
                }

                post("/{id}/properties") {
                    val userId = call.requireUserId() ?: return@post
                    val id = call.parameters["id"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val request = call.receive<PagePropertyCreateRequest>()
                    if (request.name.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Property name is required."))
                        return@post
                    }
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page not found.",
                    ) { content ->
                        PageContentJsonMutator.addProperty(
                            content = content,
                            propertyId = request.propertyId,
                            name = request.name.trim(),
                            type = request.type,
                            value = request.value,
                            targetIndex = request.targetIndex,
                        )
                    }
                }

                patch("/{id}/properties") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val request = call.receive<PagePropertyValuePatchRequest>()
                    if (request.propertyId.isBlank() && request.propertyName.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("propertyId or propertyName is required."))
                        return@patch
                    }
                    val saved = contentRepository.updatePagePropertyValue(
                        userId = userId,
                        pageId = id,
                        propertyId = request.propertyId,
                        propertyName = request.propertyName,
                        value = request.value,
                        updatedAt = System.currentTimeMillis(),
                    )
                    if (saved == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Page property not found."))
                    } else {
                        call.respond(saved.toDto())
                    }
                }

                delete("/{id}/properties/{propertyId}") {
                    val userId = call.requireUserId() ?: return@delete
                    val id = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val propertyId = call.parameters["propertyId"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing property id."),
                    )
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page property not found.",
                    ) { content ->
                        PageContentJsonMutator.deleteProperty(
                            content = content,
                            propertyId = propertyId,
                            propertyName = "",
                        )
                    }
                }

                patch("/{id}/properties/{propertyId}/position") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val propertyId = call.parameters["propertyId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing property id."),
                    )
                    val request = call.receive<PageElementPositionPatchRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page property not found.",
                    ) { content ->
                        PageContentJsonMutator.moveProperty(
                            content = content,
                            propertyId = propertyId,
                            propertyName = "",
                            targetIndex = request.targetIndex,
                        )
                    }
                }

                patch("/{id}/tables/{tableBlockId}") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val tableBlockId = call.parameters["tableBlockId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing table block id."),
                    )
                    val request = call.receive<PageTablePatchRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page table not found.",
                    ) { content ->
                        PageContentJsonMutator.updateTable(
                            content = content,
                            tableBlockId = tableBlockId,
                            title = request.title,
                            view = request.view,
                            calendarDateColumnId = request.calendarDateColumnId,
                            timelineStartColumnId = request.timelineStartColumnId,
                            timelineEndColumnId = request.timelineEndColumnId,
                            dashboardMetricColumnId = request.dashboardMetricColumnId,
                            dashboardGroupColumnId = request.dashboardGroupColumnId,
                            sortColumnId = request.sortColumnId,
                            sortDirection = request.sortDirection,
                            filterColumnId = request.filterColumnId,
                            filterQuery = request.filterQuery,
                            filterOperator = request.filterOperator,
                            groupByColumnId = request.groupByColumnId,
                        )
                    }
                }

                post("/{id}/tables/{tableBlockId}/columns") {
                    val userId = call.requireUserId() ?: return@post
                    val id = call.parameters["id"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val tableBlockId = call.parameters["tableBlockId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing table block id."),
                    )
                    val request = call.receive<PageTableColumnCreateRequest>()
                    if (request.name.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Column name is required."))
                        return@post
                    }
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page table not found.",
                    ) { content ->
                        PageContentJsonMutator.addTableColumn(
                            content = content,
                            tableBlockId = tableBlockId,
                            columnId = request.columnId,
                            name = request.name.trim(),
                            type = request.type,
                            config = request.config,
                            cellValues = request.cellValues,
                            targetIndex = request.targetIndex,
                        )
                    }
                }

                patch("/{id}/tables/{tableBlockId}/columns/{columnId}") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val tableBlockId = call.parameters["tableBlockId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing table block id."),
                    )
                    val columnId = call.parameters["columnId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing column id."),
                    )
                    val request = call.receive<PageTableColumnPatchRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page table column not found.",
                    ) { content ->
                        PageContentJsonMutator.updateTableColumn(
                            content = content,
                            tableBlockId = tableBlockId,
                            columnId = columnId,
                            name = request.name,
                            type = request.type,
                            config = request.config,
                            dateFormat = request.dateFormat,
                            timeFormat = request.timeFormat,
                            dateReminder = request.dateReminder,
                            timezoneLabel = request.timezoneLabel,
                            formula = request.formula,
                            relationTargetTableId = request.relationTargetTableId,
                            rollupRelationColumnId = request.rollupRelationColumnId,
                            rollupTargetColumnId = request.rollupTargetColumnId,
                            rollupAggregation = request.rollupAggregation,
                        )
                    }
                }

                delete("/{id}/tables/{tableBlockId}/columns/{columnId}") {
                    val userId = call.requireUserId() ?: return@delete
                    val id = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val tableBlockId = call.parameters["tableBlockId"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing table block id."),
                    )
                    val columnId = call.parameters["columnId"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing column id."),
                    )
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page table column not found.",
                    ) { content ->
                        PageContentJsonMutator.deleteTableColumn(
                            content = content,
                            tableBlockId = tableBlockId,
                            columnId = columnId,
                        )
                    }
                }

                patch("/{id}/tables/{tableBlockId}/columns/{columnId}/position") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val tableBlockId = call.parameters["tableBlockId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing table block id."),
                    )
                    val columnId = call.parameters["columnId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing column id."),
                    )
                    val request = call.receive<PageElementPositionPatchRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page table column not found.",
                    ) { content ->
                        PageContentJsonMutator.moveTableColumn(
                            content = content,
                            tableBlockId = tableBlockId,
                            columnId = columnId,
                            targetIndex = request.targetIndex,
                        )
                    }
                }

                post("/{id}/tables/{tableBlockId}/rows") {
                    val userId = call.requireUserId() ?: return@post
                    val id = call.parameters["id"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val tableBlockId = call.parameters["tableBlockId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing table block id."),
                    )
                    val request = call.receive<PageTableRowCreateRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page table not found.",
                    ) { content ->
                        PageContentJsonMutator.addTableRow(
                            content = content,
                            tableBlockId = tableBlockId,
                            rowId = request.rowId,
                            cells = request.cells,
                            cellValues = request.cellValues,
                            metadata = request.metadata,
                            targetIndex = request.targetIndex,
                        )
                    }
                }

                patch("/{id}/tables/{tableBlockId}/rows/{rowId}") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val tableBlockId = call.parameters["tableBlockId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing table block id."),
                    )
                    val rowId = call.parameters["rowId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing row id."),
                    )
                    val request = call.receive<PageTableRowPatchRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page table row not found.",
                    ) { content ->
                        PageContentJsonMutator.updateTableRow(
                            content = content,
                            tableBlockId = tableBlockId,
                            rowId = rowId,
                            blocks = request.blocks,
                            metadata = request.metadata,
                        )
                    }
                }

                delete("/{id}/tables/{tableBlockId}/rows/{rowId}") {
                    val userId = call.requireUserId() ?: return@delete
                    val id = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val tableBlockId = call.parameters["tableBlockId"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing table block id."),
                    )
                    val rowId = call.parameters["rowId"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing row id."),
                    )
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page table row not found.",
                    ) { content ->
                        PageContentJsonMutator.deleteTableRow(
                            content = content,
                            tableBlockId = tableBlockId,
                            rowId = rowId,
                        )
                    }
                }

                patch("/{id}/tables/{tableBlockId}/rows/{rowId}/position") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val tableBlockId = call.parameters["tableBlockId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing table block id."),
                    )
                    val rowId = call.parameters["rowId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing row id."),
                    )
                    val request = call.receive<PageElementPositionPatchRequest>()
                    call.respondPageContentMutation(
                        contentRepository = contentRepository,
                        userId = userId,
                        pageId = id,
                        notFoundMessage = "Page table row not found.",
                    ) { content ->
                        PageContentJsonMutator.moveTableRow(
                            content = content,
                            tableBlockId = tableBlockId,
                            rowId = rowId,
                            targetIndex = request.targetIndex,
                        )
                    }
                }

                patch("/{id}/table-cells") {
                    val userId = call.requireUserId() ?: return@patch
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing page id."),
                    )
                    val request = call.receive<PageTableCellValuePatchRequest>()
                    if (request.rowId.isBlank() || request.columnId.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("rowId and columnId are required."))
                        return@patch
                    }
                    val saved = contentRepository.updatePageTableCellValue(
                        userId = userId,
                        pageId = id,
                        rowId = request.rowId,
                        columnId = request.columnId,
                        value = request.value,
                        valueJson = request.valueJson,
                        updatedAt = System.currentTimeMillis(),
                    )
                    if (saved == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Page table cell not found."))
                    } else {
                        call.respond(saved.toDto())
                    }
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

private suspend fun io.ktor.server.application.ApplicationCall.respondPageContentMutation(
    contentRepository: ContentRepository,
    userId: String,
    pageId: String,
    notFoundMessage: String,
    transform: (String) -> String?,
) {
    val page = contentRepository.getPage(
        userId = userId,
        pageId = pageId,
        includeDeleted = false,
    )
    if (page == null) {
        respond(HttpStatusCode.NotFound, ErrorResponse("Page not found."))
        return
    }

    val updatedContent = transform(page.content)
    if (updatedContent == null) {
        respond(HttpStatusCode.NotFound, ErrorResponse(notFoundMessage))
        return
    }

    val saved = contentRepository.upsertPage(
        userId = userId,
        page = page.copy(
            content = updatedContent,
            updatedAt = System.currentTimeMillis(),
        ),
    )
    if (saved == null) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Page workspace is not accessible."))
    } else {
        respond(saved.toDto())
    }
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
