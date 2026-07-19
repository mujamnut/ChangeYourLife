package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
import com.changeyourlife.cyl.aicontract.AiActionWire
import com.changeyourlife.cyl.aicontract.AiTableColumnWire
import com.changeyourlife.cyl.backend.model.ai.AiActionValidationIssue

class AiActionSchemaValidator {
    data class Result(
        val actions: List<AiService.AiActionItem>,
        val issues: List<AiActionValidationIssue>,
    )

    fun validate(actions: List<AiService.AiActionItem>): Result {
        val validActions = mutableListOf<AiService.AiActionItem>()
        val issues = mutableListOf<AiActionValidationIssue>()

        actions.forEachIndexed { index, action ->
            val result = AiActionContractSchema.parse(
                actionIndex = index,
                payload = action.toContractWire(),
            )
            issues += result.issues.map { issue ->
                AiActionValidationIssue(
                    // Invalid actions are omitted from the response action list. Keeping
                    // their original index would make the client reject a different,
                    // valid action after the list is compacted.
                    actionIndex = null,
                    field = issue.field,
                    code = issue.code,
                    message = issue.message,
                )
            }
            if (result.isValid) {
                validActions += action.copy(type = result.normalizedPayload.type)
            }
        }

        return Result(actions = validActions, issues = issues)
    }
}

internal fun AiService.AiActionItem.toContractWire(): AiActionWire = AiActionWire(
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
    propertyType = propertyType,
    value = value,
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
    rowTitle = rowTitle,
    newRowTitle = newRowTitle,
    rowBlockId = rowBlockId,
    targetIndex = targetIndex,
    cellValues = cellValues,
    tableColumns = tableColumns.map { column ->
        AiTableColumnWire(
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
    tableRows = tableRows,
    delayMinutes = delayMinutes,
)
