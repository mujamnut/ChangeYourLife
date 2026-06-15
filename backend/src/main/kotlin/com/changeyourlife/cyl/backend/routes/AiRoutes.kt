package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.model.ai.ChatRequest
import com.changeyourlife.cyl.backend.model.ai.ChatResponse
import com.changeyourlife.cyl.backend.model.ai.GeneratePlanRequest
import com.changeyourlife.cyl.backend.model.ai.GeneratePlanResponse
import com.changeyourlife.cyl.backend.model.ai.GenerateTasksRequest
import com.changeyourlife.cyl.backend.model.ai.GenerateTasksResponse
import com.changeyourlife.cyl.backend.model.ai.SummarizeRequest
import com.changeyourlife.cyl.backend.model.ai.SummarizeResponse
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
                val reply = withContext(Dispatchers.IO) { aiService.chat(request.messages) }
                call.respond(ChatResponse(content = reply))
            }

            post("/chat-actions") {
                val request = call.receive<ChatWithActionsRequest>()
                val result = withContext(Dispatchers.IO) { aiService.chatWithActions(request.messages, request.pages, request.tasks) }
                call.respond(
                    ChatWithActionsResponse(
                        reply = result.reply,
                        actions = result.actions.map {
                            AiAction(
                                type = it.type,
                                title = it.title,
                                targetTitle = it.targetTitle,
                                content = it.content,
                                blockType = it.blockType,
                                blockId = it.blockId,
                                blockText = it.blockText,
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

            post("/summarize") {
                val request = call.receive<SummarizeRequest>()
                val summary = withContext(Dispatchers.IO) { aiService.summarize(request.content) }
                call.respond(SummarizeResponse(summary = summary))
            }

            post("/generate-tasks") {
                val request = call.receive<GenerateTasksRequest>()
                val tasks = withContext(Dispatchers.IO) { aiService.generateTasks(request.content) }
                call.respond(GenerateTasksResponse(tasks = tasks))
            }

            post("/generate-plan") {
                val request = call.receive<GeneratePlanRequest>()
                val plan = withContext(Dispatchers.IO) { aiService.generatePlan(request.prompt) }
                call.respond(GeneratePlanResponse(planJson = plan))
            }
        }
    }
}
