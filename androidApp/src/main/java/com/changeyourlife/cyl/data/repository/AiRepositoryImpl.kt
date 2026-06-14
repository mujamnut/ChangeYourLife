package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.data.remote.ai.AiApi
import com.changeyourlife.cyl.data.remote.ai.AiBlockContextDto
import com.changeyourlife.cyl.data.remote.ai.AiPageContextDto
import com.changeyourlife.cyl.data.remote.ai.AiTaskContextDto
import com.changeyourlife.cyl.data.remote.ai.ChatMessageDto
import com.changeyourlife.cyl.data.remote.ai.ChatRequestDto
import com.changeyourlife.cyl.data.remote.ai.ChatWithActionsRequestDto
import com.changeyourlife.cyl.data.remote.ai.GeneratePlanRequestDto
import com.changeyourlife.cyl.data.remote.ai.GenerateTasksRequestDto
import com.changeyourlife.cyl.data.remote.ai.SummarizeRequestDto
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.repository.AiRepository
import com.changeyourlife.cyl.domain.repository.AiPageContext
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatActionResult
import com.changeyourlife.cyl.domain.repository.ChatTableColumn
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiRepositoryImpl @Inject constructor(
    private val aiApi: AiApi,
    private val tokenStore: AuthTokenStore
) : AiRepository {

    private suspend fun getAuthHeader(): String {
        val token = tokenStore.token.value ?: error("No active auth session.")
        return "Bearer $token"
    }

    override suspend fun chat(messages: List<Pair<String, String>>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val header = getAuthHeader()
            val request = ChatRequestDto(
                messages = messages.map { ChatMessageDto(role = it.first, content = it.second) }
            )
            val response = aiApi.chat(header, request)
            response.content
        }
    }

    override suspend fun chatWithActions(
        messages: List<Pair<String, String>>,
        pages: List<AiPageContext>,
        tasks: List<Pair<String, String>>,
    ): Result<ChatActionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val header = getAuthHeader()
            val request = ChatWithActionsRequestDto(
                messages = messages.map { ChatMessageDto(role = it.first, content = it.second) },
                pages = pages.map { page ->
                    AiPageContextDto(
                        id = page.id,
                        title = page.title,
                        blocks = page.blocks.map { block ->
                            AiBlockContextDto(
                                id = block.id,
                                type = block.type,
                                text = block.text,
                                path = block.path,
                                tableTitle = block.tableTitle,
                                tableBlockId = block.tableBlockId,
                                rowId = block.rowId,
                                rowTitle = block.rowTitle,
                                rowBlockId = block.rowBlockId,
                                isChecked = block.isChecked,
                            )
                        },
                    )
                },
                tasks = tasks.map { AiTaskContextDto(id = it.first, title = it.second) },
            )
            val response = aiApi.chatWithActions(header, request)
            ChatActionResult(
                reply = response.reply,
                actions = response.actions.map {
                    ChatAction(
                        type = it.type.normalizedChatActionType(),
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
                            ChatTableColumn(
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
        }
    }

    override suspend fun summarize(content: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val header = getAuthHeader()
            val request = SummarizeRequestDto(content = content)
            val response = aiApi.summarize(header, request)
            response.summary
        }
    }

    override suspend fun generateTasks(content: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val header = getAuthHeader()
            val request = GenerateTasksRequestDto(content = content)
            val response = aiApi.generateTasks(header, request)
            response.tasks
        }
    }

    override suspend fun generatePlan(prompt: String): Result<PageBlockDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val header = getAuthHeader()
            val request = GeneratePlanRequestDto(prompt = prompt)
            val response = aiApi.generatePlan(header, request)
            PageBlockCodec.decodeDocument(response.planJson)
        }
    }
}

private fun String.normalizedChatActionType(): String {
    return trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')
}
