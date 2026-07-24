package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
import com.changeyourlife.cyl.data.remote.ai.AiActionDto
import com.changeyourlife.cyl.data.remote.ai.AiTableColumnDto
import com.changeyourlife.cyl.data.remote.ai.ChatWithActionsResponseDto
import com.changeyourlife.cyl.domain.repository.AiDiagnostics
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatActionResult
import com.changeyourlife.cyl.domain.repository.ChatActionValidationIssue
import com.changeyourlife.cyl.domain.repository.ChatTableColumn

internal object AiActionContractMapper {
    fun toDomain(response: ChatWithActionsResponseDto): ChatActionResult {
        val validationIssues = response.validationIssues.map { issue ->
            ChatActionValidationIssue(
                actionIndex = issue.actionIndex,
                field = issue.field,
                code = issue.code,
                message = issue.message,
            )
        }.toMutableList()

        val actions = response.actions.mapIndexed { index, payload ->
            val parsed = AiActionContractSchema.parse(
                actionIndex = index,
                payload = payload,
            )
            validationIssues += parsed.issues.map { issue ->
                ChatActionValidationIssue(
                    actionIndex = issue.actionIndex,
                    field = issue.field,
                    code = issue.code.uppercase(),
                    message = issue.message,
                )
            }
            parsed.normalizedPayload.toDomainAction()
        }

        return ChatActionResult(
            reply = response.reply,
            schemaName = response.schemaName,
            schemaVersion = response.schemaVersion,
            validationIssues = validationIssues,
            actions = actions,
            diagnostics = AiDiagnostics(
                phase = response.diagnostics.phase,
                imageCount = response.diagnostics.imageCount,
                textFileCount = response.diagnostics.textFileCount,
                visionAttempted = response.diagnostics.visionAttempted,
                visionProvider = response.diagnostics.visionProvider,
                visionModel = response.diagnostics.visionModel,
                visionStatus = response.diagnostics.visionStatus,
                visionPipelineVersion = response.diagnostics.visionPipelineVersion,
                webSearchAttempted = response.diagnostics.webSearchAttempted,
                webSearchProvider = response.diagnostics.webSearchProvider,
                webSearchStatus = response.diagnostics.webSearchStatus,
                webSearchResultCount = response.diagnostics.webSearchResultCount,
                warning = response.diagnostics.warning,
            ),
        )
    }
}

private fun AiActionDto.toDomainAction(): ChatAction = ChatAction(
    type = type,
    title = title,
    targetTitle = targetTitle,
    content = content,
    blockType = blockType,
    blockId = blockId,
    blockText = blockText,
    textToFormat = textToFormat,
    format = format,
    linkUrl = linkUrl,
    color = color,
    highlight = highlight,
    rangeStart = rangeStart,
    rangeEnd = rangeEnd,
    mediaUri = mediaUri,
    mediaName = mediaName,
    mediaMimeType = mediaMimeType,
    mediaSizeBytes = mediaSizeBytes,
    isChecked = isChecked,
    propertyName = propertyName,
    newPropertyName = newPropertyName,
    propertyType = propertyType,
    value = value,
    moveDirection = moveDirection,
    parentPageId = parentPageId,
    parentPageTitle = parentPageTitle,
    sourcePageId = sourcePageId,
    sourcePageTitle = sourcePageTitle,
    sourceTableBlockId = sourceTableBlockId,
    sourceTableTitle = sourceTableTitle,
    moduleType = moduleType,
    tableTitle = tableTitle,
    tableView = tableView,
    calendarDateColumnId = calendarDateColumnId,
    calendarDateColumnName = calendarDateColumnName,
    timelineStartColumnId = timelineStartColumnId,
    timelineStartColumnName = timelineStartColumnName,
    timelineEndColumnId = timelineEndColumnId,
    timelineEndColumnName = timelineEndColumnName,
    dashboardMetricColumnId = dashboardMetricColumnId,
    dashboardMetricColumnName = dashboardMetricColumnName,
    dashboardGroupColumnId = dashboardGroupColumnId,
    dashboardGroupColumnName = dashboardGroupColumnName,
    columnId = columnId,
    columnName = columnName,
    newColumnName = newColumnName,
    columnType = columnType,
    options = options,
    formula = formula,
    relationTargetTableId = relationTargetTableId,
    relationTargetTableTitle = relationTargetTableTitle,
    rollupRelationColumnId = rollupRelationColumnId,
    rollupRelationColumnName = rollupRelationColumnName,
    rollupTargetColumnId = rollupTargetColumnId,
    rollupTargetColumnName = rollupTargetColumnName,
    rollupAggregation = rollupAggregation,
    sortDirection = sortDirection,
    filterQuery = filterQuery,
    groupByColumnId = groupByColumnId,
    groupByColumnName = groupByColumnName,
    rowId = rowId,
    rowIds = rowIds,
    rowTitle = rowTitle,
    newRowTitle = newRowTitle,
    rowBlockId = rowBlockId,
    targetIndex = targetIndex,
    cellValues = cellValues,
    tableColumns = tableColumns.map(AiTableColumnDto::toDomainColumn),
    tableRows = tableRows,
    delayMinutes = delayMinutes,
)

private fun AiTableColumnDto.toDomainColumn(): ChatTableColumn = ChatTableColumn(
    name = name,
    type = type,
    options = options,
    dateFormat = dateFormat,
    timeFormat = timeFormat,
    dateReminder = dateReminder,
    timezoneLabel = timezoneLabel,
    formula = formula,
    relationTargetTableId = relationTargetTableId,
    rollupRelationColumnName = rollupRelationColumnName,
    rollupTargetColumnName = rollupTargetColumnName,
    rollupAggregation = rollupAggregation,
)
