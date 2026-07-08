package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.model.ai.ChatRequest
import com.changeyourlife.cyl.backend.model.ai.ChatResponse
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsRequest
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import com.changeyourlife.cyl.backend.model.ai.AiAction
import com.changeyourlife.cyl.backend.model.ai.AiTableColumn
import com.changeyourlife.cyl.backend.service.AiService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import com.changeyourlife.cyl.backend.model.ai.AiStatusResponse
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.aiRoutes(aiService: AiService) {
    route("/ai") {
        get("/status") {
            call.respond(
                AiStatusResponse(
                    mode = if (aiService.isMockMode) "sandbox" else "live",
                    provider = aiService.activeProvider,
                    model = aiService.activeModel,
                    apiKeyConfigured = !aiService.isMockMode,
                    apiKeyLength = aiService.apiKeyLength,
                    apiKeyInspect = aiService.apiKeyInspect
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
                    )
                }
                call.respond(
                    ChatWithActionsResponse(
                        reply = result.reply,
                        validationIssues = result.validationIssues,
                        actions = result.actions.map {
                            AiAction(
                                type = it.type,
                                title = it.title,
                                targetTitle = it.targetTitle,
                                content = it.content,
                                blockType = it.blockType,
                                blockId = it.blockId,
                                blockText = it.blockText,
                                textToFormat = it.textToFormat,
                                format = it.format,
                                linkUrl = it.linkUrl,
                                color = it.color,
                                highlight = it.highlight,
                                rangeStart = it.rangeStart,
                                rangeEnd = it.rangeEnd,
                                mediaUri = it.mediaUri,
                                mediaName = it.mediaName,
                                mediaMimeType = it.mediaMimeType,
                                mediaSizeBytes = it.mediaSizeBytes,
                                isChecked = it.isChecked,
                                propertyName = it.propertyName,
                                propertyType = it.propertyType,
                                value = it.value,
                                moduleType = it.moduleType,
                                tableTitle = it.tableTitle,
                                tableView = it.tableView,
                                calendarDateColumnId = it.calendarDateColumnId,
                                calendarDateColumnName = it.calendarDateColumnName,
                                timelineStartColumnId = it.timelineStartColumnId,
                                timelineStartColumnName = it.timelineStartColumnName,
                                timelineEndColumnId = it.timelineEndColumnId,
                                timelineEndColumnName = it.timelineEndColumnName,
                                dashboardMetricColumnId = it.dashboardMetricColumnId,
                                dashboardMetricColumnName = it.dashboardMetricColumnName,
                                dashboardGroupColumnId = it.dashboardGroupColumnId,
                                dashboardGroupColumnName = it.dashboardGroupColumnName,
                                columnId = it.columnId,
                                columnName = it.columnName,
                                newColumnName = it.newColumnName,
                                columnType = it.columnType,
                                options = it.options,
                                formula = it.formula,
                                relationTargetTableId = it.relationTargetTableId,
                                relationTargetTableTitle = it.relationTargetTableTitle,
                                rollupRelationColumnId = it.rollupRelationColumnId,
                                rollupRelationColumnName = it.rollupRelationColumnName,
                                rollupTargetColumnId = it.rollupTargetColumnId,
                                rollupTargetColumnName = it.rollupTargetColumnName,
                                rollupAggregation = it.rollupAggregation,
                                sortDirection = it.sortDirection,
                                filterQuery = it.filterQuery,
                                groupByColumnId = it.groupByColumnId,
                                groupByColumnName = it.groupByColumnName,
                                rowId = it.rowId,
                                rowTitle = it.rowTitle,
                                newRowTitle = it.newRowTitle,
                                rowBlockId = it.rowBlockId,
                                targetIndex = it.targetIndex,
                                cellValues = it.cellValues,
                                tableColumns = it.tableColumns.map { column ->
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
                                tableRows = it.tableRows,
                                delayMinutes = it.delayMinutes,
                            )
                        }
                    )
                )
            }

        }
    }
}
