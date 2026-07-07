package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.remote.ai.AiActionDto
import com.changeyourlife.cyl.data.remote.ai.AiTableColumnDto
import com.changeyourlife.cyl.data.remote.ai.ChatWithActionsResponseDto
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

        val actions = response.actions.mapIndexed { index, dto ->
            val normalizedType = dto.type.normalizedAiActionType()
            AiActionContract.forType(normalizedType)
                ?.validate(actionIndex = index, action = dto, normalizedType = normalizedType)
                ?.let(validationIssues::add)
            dto.toDomainAction(normalizedType)
        }

        return ChatActionResult(
            reply = response.reply,
            schemaName = response.schemaName,
            schemaVersion = response.schemaVersion,
            validationIssues = validationIssues,
            actions = actions,
        )
    }
}

private sealed interface AiActionContract {
    val types: Set<String>
    val allowedFields: Set<String>
    val requiredAny: Set<String>
        get() = emptySet()

    fun validate(actionIndex: Int, action: AiActionDto, normalizedType: String): ChatActionValidationIssue? {
        val presentFields = action.presentFields()
        val unexpectedFields = presentFields - allowedFields
        if (unexpectedFields.isNotEmpty()) {
            return ChatActionValidationIssue(
                actionIndex = actionIndex,
                field = unexpectedFields.sorted().joinToString(","),
                code = "UNEXPECTED_ACTION_FIELDS",
                message = "Skipped $normalizedType because it included fields that do not belong to this action: ${unexpectedFields.sorted().joinToString(", ")}.",
            )
        }
        if (requiredAny.isNotEmpty() && presentFields.intersect(requiredAny).isEmpty()) {
            return ChatActionValidationIssue(
                actionIndex = actionIndex,
                field = requiredAny.sorted().joinToString("|"),
                code = "MISSING_REQUIRED_ACTION_FIELDS",
                message = "Skipped $normalizedType because it is missing one of: ${requiredAny.sorted().joinToString(", ")}.",
            )
        }
        return null
    }

    data object PageTitle : AiActionContract {
        override val types = setOf("RENAME_CURRENT_PAGE", "RENAME_PAGE")
        override val allowedFields = PageTarget + setOf("title", "value", "content")
        override val requiredAny = setOf("title", "value", "content")
    }

    data object PageUpdate : AiActionContract {
        override val types = setOf("UPDATE_PAGE")
        override val allowedFields = PageTarget + setOf("title", "content")
    }

    data object PageCreate : AiActionContract {
        override val types = setOf("CREATE_PAGE", "CREATE_SUBPAGE")
        override val allowedFields = PageTarget + TableShape + setOf("title", "content", "moduleType", "targetIndex")
    }

    data object BlockCreate : AiActionContract {
        override val types = setOf("APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK")
        override val allowedFields = BlockPayload + TableShape + setOf("targetIndex")
    }

    data object BlockTarget : AiActionContract {
        override val types = setOf(
            "DELETE_ALL_BLOCKS",
            "DELETE_BLOCK",
            "UPDATE_BLOCK",
            "EDIT_BLOCK",
            "UPDATE_TODO",
            "CHECK_BLOCK",
            "UNCHECK_BLOCK",
        )
        override val allowedFields = BlockTargetFields + BlockPayload
    }

    data object RichTextFormat : AiActionContract {
        override val types = setOf("FORMAT_BLOCK_TEXT")
        override val allowedFields = BlockTargetFields + setOf(
            "textToFormat",
            "format",
            "linkUrl",
            "color",
            "highlight",
            "rangeStart",
            "rangeEnd",
        )
        override val requiredAny = setOf("textToFormat", "blockText", "blockId", "rangeStart")
    }

    data object Property : AiActionContract {
        override val types = setOf("ADD_PROPERTY", "UPDATE_PROPERTY", "DELETE_PROPERTY")
        override val allowedFields = PageTarget + setOf("title", "propertyName", "propertyType", "value", "content", "targetIndex")
    }

    data object TableCreate : AiActionContract {
        override val types = setOf("CREATE_DATABASE", "CREATE_TABLE")
        override val allowedFields = PageTarget + TableShape + setOf("title", "content", "moduleType", "targetIndex")
    }

    data object TableTitle : AiActionContract {
        override val types = setOf("RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE")
        override val allowedFields = TableTarget + setOf("title", "value", "content", "newColumnName")
        override val requiredAny = setOf("title", "value", "content", "newColumnName")
    }

    data object TableColumn : AiActionContract {
        override val types = setOf(
            "ADD_TABLE_COLUMN",
            "DELETE_TABLE_COLUMN",
            "RENAME_TABLE_COLUMN",
            "UPDATE_TABLE_COLUMN",
            "UPDATE_TABLE_COLUMN_TYPE",
            "CHANGE_TABLE_COLUMN_TYPE",
            "SET_TABLE_COLUMN_TYPE",
            "UPDATE_TABLE_COLUMN_CONFIG",
            "SET_TABLE_COLUMN_CONFIG",
            "UPDATE_FORMULA_COLUMN",
            "UPDATE_RELATION_COLUMN",
            "UPDATE_ROLLUP_COLUMN",
            "REORDER_TABLE_COLUMN",
            "MOVE_TABLE_COLUMN",
        )
        override val allowedFields = TableTarget + ColumnTarget + ColumnConfig + setOf(
            "title",
            "propertyName",
            "propertyType",
            "value",
            "content",
            "targetIndex",
        )
    }

    data object TableRow : AiActionContract {
        override val types = setOf(
            "ADD_TABLE_ROW",
            "DELETE_TABLE_ROW",
            "UPDATE_TABLE_ROW",
            "RENAME_TABLE_ROW",
            "REORDER_TABLE_ROW",
            "MOVE_TABLE_ROW",
        )
        override val allowedFields = TableTarget + RowTarget + setOf("title", "value", "content", "cellValues", "targetIndex")
    }

    data object RowBlock : AiActionContract {
        override val types = setOf(
            "ADD_ROW_PAGE_BLOCK",
            "APPEND_ROW_PAGE_BLOCK",
            "ADD_TABLE_ROW_BLOCK",
            "UPDATE_ROW_PAGE_BLOCK",
            "EDIT_ROW_PAGE_BLOCK",
            "UPDATE_TABLE_ROW_BLOCK",
            "CHECK_ROW_PAGE_BLOCK",
            "UNCHECK_ROW_PAGE_BLOCK",
            "DELETE_ROW_PAGE_BLOCK",
            "DELETE_TABLE_ROW_BLOCK",
        )
        override val allowedFields = TableTarget + RowTarget + BlockTargetFields + BlockPayload + setOf("rowBlockId", "targetIndex")
    }

    data object TableCell : AiActionContract {
        override val types = setOf("UPDATE_TABLE_CELL")
        override val allowedFields = TableTarget + RowTarget + ColumnTarget + setOf("title", "propertyName", "value", "content", "cellValues")
    }

    data object TableView : AiActionContract {
        override val types = setOf(
            "CHANGE_TABLE_VIEW",
            "SET_TABLE_VIEW",
            "SET_TABLE_VIEW_CONFIG",
            "CONFIGURE_TABLE_VIEW",
            "UPDATE_TABLE_VIEW_CONFIG",
        )
        override val allowedFields = TableTarget + ColumnTarget + ViewConfig + setOf("title", "tableView", "value", "content")
    }

    data object TableSortFilterGroup : AiActionContract {
        override val types = setOf(
            "SORT_TABLE",
            "SET_TABLE_SORT",
            "CLEAR_TABLE_SORT",
            "FILTER_TABLE",
            "SET_TABLE_FILTER",
            "CLEAR_TABLE_FILTER",
            "GROUP_TABLE",
            "SET_TABLE_GROUP",
            "CLEAR_TABLE_GROUP",
        )
        override val allowedFields = TableTarget + ColumnTarget + setOf(
            "title",
            "propertyName",
            "value",
            "content",
            "sortDirection",
            "filterQuery",
            "groupByColumnId",
            "groupByColumnName",
        )
    }

    data object TaskReminder : AiActionContract {
        override val types = setOf("CREATE_TASK", "CREATE_REMINDER")
        override val allowedFields = TableTarget + RowTarget + setOf("title", "content", "value", "cellValues", "delayMinutes", "targetIndex")
    }

    companion object {
        private val all = listOf(
            PageTitle,
            PageUpdate,
            PageCreate,
            BlockCreate,
            BlockTarget,
            RichTextFormat,
            Property,
            TableCreate,
            TableTitle,
            TableColumn,
            TableRow,
            RowBlock,
            TableCell,
            TableView,
            TableSortFilterGroup,
            TaskReminder,
        )

        fun forType(type: String): AiActionContract? = all.firstOrNull { contract -> type in contract.types }
    }
}

private fun AiActionDto.presentFields(): Set<String> = buildSet {
    if (title.isNotBlank()) add("title")
    if (targetTitle.isNotBlank()) add("targetTitle")
    if (content.isNotBlank()) add("content")
    if (blockType.isNotBlank()) add("blockType")
    if (blockId.isNotBlank()) add("blockId")
    if (blockText.isNotBlank()) add("blockText")
    if (textToFormat.isNotBlank()) add("textToFormat")
    if (format.isNotBlank()) add("format")
    if (linkUrl.isNotBlank()) add("linkUrl")
    if (color.isNotBlank()) add("color")
    if (highlight.isNotBlank()) add("highlight")
    if (rangeStart != null) add("rangeStart")
    if (rangeEnd != null) add("rangeEnd")
    if (mediaUri.isNotBlank()) add("mediaUri")
    if (mediaName.isNotBlank()) add("mediaName")
    if (mediaMimeType.isNotBlank()) add("mediaMimeType")
    if (mediaSizeBytes > 0) add("mediaSizeBytes")
    if (isChecked != null) add("isChecked")
    if (propertyName.isNotBlank()) add("propertyName")
    if (propertyType.isNotBlank() && propertyType != "Text") add("propertyType")
    if (value.isNotBlank()) add("value")
    if (moduleType.isNotBlank()) add("moduleType")
    if (tableTitle.isNotBlank()) add("tableTitle")
    if (tableView.isNotBlank() && tableView != "Table") add("tableView")
    if (calendarDateColumnId.isNotBlank()) add("calendarDateColumnId")
    if (calendarDateColumnName.isNotBlank()) add("calendarDateColumnName")
    if (timelineStartColumnId.isNotBlank()) add("timelineStartColumnId")
    if (timelineStartColumnName.isNotBlank()) add("timelineStartColumnName")
    if (timelineEndColumnId.isNotBlank()) add("timelineEndColumnId")
    if (timelineEndColumnName.isNotBlank()) add("timelineEndColumnName")
    if (dashboardMetricColumnId.isNotBlank()) add("dashboardMetricColumnId")
    if (dashboardMetricColumnName.isNotBlank()) add("dashboardMetricColumnName")
    if (dashboardGroupColumnId.isNotBlank()) add("dashboardGroupColumnId")
    if (dashboardGroupColumnName.isNotBlank()) add("dashboardGroupColumnName")
    if (columnId.isNotBlank()) add("columnId")
    if (columnName.isNotBlank()) add("columnName")
    if (newColumnName.isNotBlank()) add("newColumnName")
    if (columnType.isNotBlank() && columnType != "Text") add("columnType")
    if (options.isNotEmpty()) add("options")
    if (formula.isNotBlank()) add("formula")
    if (relationTargetTableId.isNotBlank()) add("relationTargetTableId")
    if (relationTargetTableTitle.isNotBlank()) add("relationTargetTableTitle")
    if (rollupRelationColumnId.isNotBlank()) add("rollupRelationColumnId")
    if (rollupRelationColumnName.isNotBlank()) add("rollupRelationColumnName")
    if (rollupTargetColumnId.isNotBlank()) add("rollupTargetColumnId")
    if (rollupTargetColumnName.isNotBlank()) add("rollupTargetColumnName")
    if (rollupAggregation.isNotBlank()) add("rollupAggregation")
    if (sortDirection.isNotBlank() && sortDirection != "Ascending") add("sortDirection")
    if (filterQuery.isNotBlank()) add("filterQuery")
    if (groupByColumnId.isNotBlank()) add("groupByColumnId")
    if (groupByColumnName.isNotBlank()) add("groupByColumnName")
    if (rowId.isNotBlank()) add("rowId")
    if (rowTitle.isNotBlank()) add("rowTitle")
    if (newRowTitle.isNotBlank()) add("newRowTitle")
    if (rowBlockId.isNotBlank()) add("rowBlockId")
    if (targetIndex != null) add("targetIndex")
    if (cellValues.isNotEmpty()) add("cellValues")
    if (tableColumns.isNotEmpty()) add("tableColumns")
    if (tableRows.isNotEmpty()) add("tableRows")
    if (delayMinutes != null) add("delayMinutes")
}

private fun AiActionDto.toDomainAction(normalizedType: String): ChatAction {
    return ChatAction(
        type = normalizedType,
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
        tableColumns = tableColumns.map { column -> column.toDomainColumn() },
        tableRows = tableRows,
        delayMinutes = delayMinutes,
    )
}

private fun AiTableColumnDto.toDomainColumn(): ChatTableColumn {
    return ChatTableColumn(
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
}

private fun String.normalizedAiActionType(): String {
    return trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')
}

private val PageTarget = setOf("targetTitle")
private val TableTarget = setOf("targetTitle", "tableTitle")
private val TableShape = setOf("tableTitle", "tableView", "tableColumns", "tableRows")
private val BlockTargetFields = setOf("title", "content", "blockId", "blockText")
private val BlockPayload = setOf(
    "title",
    "content",
    "blockType",
    "isChecked",
    "mediaUri",
    "mediaName",
    "mediaMimeType",
    "mediaSizeBytes",
)
private val ColumnTarget = setOf("columnId", "columnName", "propertyName")
private val ColumnConfig = setOf(
    "newColumnName",
    "columnType",
    "propertyType",
    "options",
    "formula",
    "relationTargetTableId",
    "relationTargetTableTitle",
    "rollupRelationColumnId",
    "rollupRelationColumnName",
    "rollupTargetColumnId",
    "rollupTargetColumnName",
    "rollupAggregation",
)
private val RowTarget = setOf("rowId", "rowTitle", "newRowTitle")
private val ViewConfig = setOf(
    "calendarDateColumnId",
    "calendarDateColumnName",
    "timelineStartColumnId",
    "timelineStartColumnName",
    "timelineEndColumnId",
    "timelineEndColumnName",
    "dashboardMetricColumnId",
    "dashboardMetricColumnName",
    "dashboardGroupColumnId",
    "dashboardGroupColumnName",
)
