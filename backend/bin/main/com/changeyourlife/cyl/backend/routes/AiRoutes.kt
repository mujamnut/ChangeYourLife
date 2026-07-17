package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.model.ai.ChatRequest
import com.changeyourlife.cyl.backend.model.ai.ChatResponse
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsRequest
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import com.changeyourlife.cyl.backend.model.ai.AiChatActionsJobAcceptedResponse
import com.changeyourlife.cyl.backend.model.ai.AiChatActionsJobStatusResponse
import com.changeyourlife.cyl.backend.model.ai.AiAction
import com.changeyourlife.cyl.backend.model.ai.AiTableColumn
import com.changeyourlife.cyl.backend.domain.AiChatActionsJob
import com.changeyourlife.cyl.backend.service.AiJobService
import com.changeyourlife.cyl.backend.service.AiService
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
import io.ktor.server.routing.post
import com.changeyourlife.cyl.backend.model.ai.AiStatusResponse
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.aiRoutes(
    aiService: AiService,
    aiJobService: AiJobService,
) {
    route("/ai") {
        get("/status") {
            call.respond(
                AiStatusResponse(
                    mode = if (aiService.isMockMode) "sandbox" else "live",
                    provider = aiService.activeProvider,
                    model = aiService.activeModel,
                    apiKeyConfigured = !aiService.isMockMode,
                    apiKeyLength = aiService.apiKeyLength,
                    apiKeyInspect = aiService.apiKeyInspect,
                    visionPipelineVersion = aiService.visionPipelineVersion,
                    visionMaxImageDimension = aiService.visionMaxImageDimension,
                    visionMaxImageBytes = aiService.visionMaxImageBytes,
                    lmStudioVisionModels = aiService.lmStudioVisionModelLabel,
                )
            )
        }
    }

    authenticate("auth-jwt") {
        route("/ai") {
            post("/chat") {
                val request = call.receive<ChatRequest>()
                val reply = withContext(Dispatchers.IO) {
                    aiService.chat(
                        messages = request.messages,
                        images = request.images,
                    )
                }
                call.respond(ChatResponse(content = reply))
            }

            post("/chat-actions") {
                val request = call.receive<ChatWithActionsRequest>()
                val result = withContext(Dispatchers.IO) {
                    aiService.chatWithActions(
                        messages = request.messages,
                        pages = request.pages,
                        tasks = request.tasks,
                        clientDate = request.clientDate,
                        clientTimezone = request.clientTimezone,
                        images = request.images,
                        webSearchEnabled = request.webSearchEnabled,
                        webSearchQuery = request.webSearchQuery,
                    )
                }
                call.respond(result.toResponse())
            }

            post("/chat-actions/jobs") {
                val userId = call.requireUserId() ?: return@post
                val request = call.receive<ChatWithActionsRequest>()
                val job = aiJobService.createChatActionsJob(
                    ownerId = userId,
                    diagnostics = aiService.initialDiagnosticsFor(request.images),
                ) { progress ->
                    aiService.chatWithActions(
                        messages = request.messages,
                        pages = request.pages,
                        tasks = request.tasks,
                        clientDate = request.clientDate,
                        clientTimezone = request.clientTimezone,
                        images = request.images,
                        webSearchEnabled = request.webSearchEnabled,
                        webSearchQuery = request.webSearchQuery,
                        progress = progress,
                    ).toResponse()
                }
                call.respond(HttpStatusCode.Accepted, job.toAcceptedResponse())
            }

            get("/chat-actions/jobs/{jobId}") {
                val userId = call.requireUserId() ?: return@get
                val jobId = call.parameters["jobId"].orEmpty()
                if (jobId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing jobId."))
                    return@get
                }
                val job = aiJobService.getChatActionsJob(ownerId = userId, jobId = jobId)
                if (job == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "AI job not found."))
                    return@get
                }
                call.respond(job.toStatusResponse())
            }

        }
    }
}

private suspend fun ApplicationCall.requireUserId(): String? {
    val userId = principal<JWTPrincipal>()?.payload?.subject
    if (userId.isNullOrBlank()) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authenticated user."))
        return null
    }
    return userId
}

private fun AiChatActionsJob.toAcceptedResponse(): AiChatActionsJobAcceptedResponse =
    AiChatActionsJobAcceptedResponse(
        jobId = jobId,
        status = status.wireValue,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        phase = phase,
        diagnostics = diagnostics,
    )

private fun AiChatActionsJob.toStatusResponse(): AiChatActionsJobStatusResponse =
    AiChatActionsJobStatusResponse(
        jobId = jobId,
        status = status.wireValue,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        result = result,
        error = error,
        phase = phase,
        diagnostics = diagnostics,
    )

private fun AiService.AiActionResult.toResponse(): ChatWithActionsResponse =
    ChatWithActionsResponse(
        reply = reply,
        validationIssues = validationIssues,
        actions = actions.map { action ->
            AiAction(
                type = action.type,
                title = action.title,
                targetTitle = action.targetTitle,
                content = action.content,
                blockType = action.blockType,
                blockId = action.blockId,
                blockText = action.blockText,
                textToFormat = action.textToFormat,
                format = action.format,
                linkUrl = action.linkUrl,
                color = action.color,
                highlight = action.highlight,
                rangeStart = action.rangeStart,
                rangeEnd = action.rangeEnd,
                mediaUri = action.mediaUri,
                mediaName = action.mediaName,
                mediaMimeType = action.mediaMimeType,
                mediaSizeBytes = action.mediaSizeBytes,
                isChecked = action.isChecked,
                propertyName = action.propertyName,
                propertyType = action.propertyType,
                value = action.value,
                moduleType = action.moduleType,
                tableTitle = action.tableTitle,
                tableView = action.tableView,
                calendarDateColumnId = action.calendarDateColumnId,
                calendarDateColumnName = action.calendarDateColumnName,
                timelineStartColumnId = action.timelineStartColumnId,
                timelineStartColumnName = action.timelineStartColumnName,
                timelineEndColumnId = action.timelineEndColumnId,
                timelineEndColumnName = action.timelineEndColumnName,
                dashboardMetricColumnId = action.dashboardMetricColumnId,
                dashboardMetricColumnName = action.dashboardMetricColumnName,
                dashboardGroupColumnId = action.dashboardGroupColumnId,
                dashboardGroupColumnName = action.dashboardGroupColumnName,
                columnId = action.columnId,
                columnName = action.columnName,
                newColumnName = action.newColumnName,
                columnType = action.columnType,
                options = action.options,
                formula = action.formula,
                relationTargetTableId = action.relationTargetTableId,
                relationTargetTableTitle = action.relationTargetTableTitle,
                rollupRelationColumnId = action.rollupRelationColumnId,
                rollupRelationColumnName = action.rollupRelationColumnName,
                rollupTargetColumnId = action.rollupTargetColumnId,
                rollupTargetColumnName = action.rollupTargetColumnName,
                rollupAggregation = action.rollupAggregation,
                sortDirection = action.sortDirection,
                filterQuery = action.filterQuery,
                groupByColumnId = action.groupByColumnId,
                groupByColumnName = action.groupByColumnName,
                rowId = action.rowId,
                rowTitle = action.rowTitle,
                newRowTitle = action.newRowTitle,
                rowBlockId = action.rowBlockId,
                targetIndex = action.targetIndex,
                cellValues = action.cellValues,
                tableColumns = action.tableColumns.map { column ->
                    AiTableColumn(
                        name = column.name,
                        type = column.type,
                        options = column.options,
                        dateFormat = column.dateFormat,
                        timeFormat = column.timeFormat,
                        dateReminder = column.dateReminder,
                        timezoneLabel = column.timezoneLabel,
                        formula = column.formula,
                        relationTargetTableId = column.relationTargetTableId,
                        rollupRelationColumnName = column.rollupRelationColumnName,
                        rollupTargetColumnName = column.rollupTargetColumnName,
                        rollupAggregation = column.rollupAggregation,
                    )
                },
                tableRows = action.tableRows,
                delayMinutes = action.delayMinutes,
            )
        },
        diagnostics = diagnostics,
    )
