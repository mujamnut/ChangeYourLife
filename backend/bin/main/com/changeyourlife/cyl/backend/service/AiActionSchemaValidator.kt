package com.changeyourlife.cyl.backend.service

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
            val normalizedType = action.type.normalizedActionType()
            val contract = AiActionContract.forType(normalizedType)
            when {
                normalizedType.isBlank() -> {
                    issues += actionIssue(
                        actionIndex = index,
                        field = "type",
                        code = "missing_action_type",
                        message = "Action type is required.",
                    )
                }
                contract == null -> {
                    issues += actionIssue(
                        actionIndex = index,
                        field = "type",
                        code = "unsupported_action_type",
                        message = "Unsupported CYL action type: $normalizedType.",
                    )
                }
                else -> {
                    val normalizedAction = action.copy(type = normalizedType)
                    val contractIssues = contract.validate(actionIndex = index, action = normalizedAction)
                    if (contractIssues.isEmpty()) {
                        validActions += normalizedAction
                    } else {
                        issues += contractIssues
                    }
                }
            }
        }

        return Result(actions = validActions, issues = issues)
    }
}

private sealed interface AiActionContract {
    val types: Set<String>
    val allowedFields: Set<String>

    fun validate(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> {
        val presentFields = action.presentFields()
        val unexpectedFields = presentFields - allowedFields
        return buildList {
            if (unexpectedFields.isNotEmpty()) {
                add(
                    actionIssue(
                        actionIndex = actionIndex,
                        field = unexpectedFields.sorted().joinToString(","),
                        code = "unexpected_action_fields",
                        message = "Action ${action.type} included fields that do not belong to this contract: ${unexpectedFields.sorted().joinToString(", ")}.",
                    ),
                )
            }
            addAll(requiredFieldIssues(actionIndex = actionIndex, action = action))
        }
    }

    fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> = emptyList()

    data object PageTitle : AiActionContract {
        override val types = setOf("RENAME_CURRENT_PAGE", "RENAME_PAGE")
        override val allowedFields = PageTarget + setOf("title", "value", "content")

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> =
            action.requireAny(actionIndex, "title", "Rename page action needs a new title.", action.title, action.value, action.content)
    }

    data object PageUpdate : AiActionContract {
        override val types = setOf("UPDATE_PAGE")
        override val allowedFields = PageTarget + setOf("title", "content")
    }

    data object PageCreate : AiActionContract {
        override val types = setOf("CREATE_PAGE", "CREATE_SUBPAGE")
        override val allowedFields = PageTarget + TableShape + setOf("title", "content", "moduleType", "targetIndex")

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> =
            action.requireAny(
                actionIndex = actionIndex,
                field = "title",
                message = "Create page action needs title, content, tableTitle, or moduleType.",
                action.title,
                action.content,
                action.tableTitle,
                action.moduleType,
            )
    }

    data object BlockCreate : AiActionContract {
        override val types = setOf("APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK")
        override val allowedFields = PageTarget + BlockPayload + TableShape + setOf("targetIndex")

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> {
            val normalizedBlockType = action.blockType.normalizedActionType()
            return when {
                normalizedBlockType == "DIVIDER" -> emptyList()
                normalizedBlockType in setOf("MEDIAFILE", "MEDIA_FILE") && action.mediaUri.isNotBlank() -> emptyList()
                normalizedBlockType in setOf("MEDIAFILE", "MEDIA_FILE") -> {
                    listOf(missingField(actionIndex, "mediaUri", "Media/file block needs mediaUri."))
                }
                action.content.isNotBlank() || action.title.isNotBlank() -> emptyList()
                else -> listOf(missingField(actionIndex, "content", "Block content is required unless the block is a divider."))
            }
        }
    }

    data object DeleteAllBlocks : AiActionContract {
        override val types = setOf("DELETE_ALL_BLOCKS")
        override val allowedFields = PageTarget
    }

    data object BlockTarget : AiActionContract {
        override val types = setOf("DELETE_BLOCK", "UPDATE_BLOCK", "EDIT_BLOCK", "UPDATE_TODO", "CHECK_BLOCK", "UNCHECK_BLOCK")
        override val allowedFields = PageTarget + BlockTargetFields + BlockPayload + setOf("value")

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> = buildList {
            if (!action.hasAny(action.blockId, action.blockText, action.content, action.title)) {
                add(missingField(actionIndex, "blockId", "${action.type} needs blockId, blockText, content, or title."))
            }
            if (action.type in setOf("UPDATE_BLOCK", "EDIT_BLOCK") && !action.hasAny(action.content, action.value)) {
                add(missingField(actionIndex, "content", "Update block action needs replacement content."))
            }
        }
    }

    data object RichTextFormat : AiActionContract {
        override val types = setOf("FORMAT_BLOCK_TEXT")
        override val allowedFields = PageTarget + BlockTargetFields + setOf(
            "textToFormat",
            "format",
            "linkUrl",
            "color",
            "highlight",
            "rangeStart",
            "rangeEnd",
            "value",
        )

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> = buildList {
            if (!action.hasAny(action.blockId, action.blockText, action.title, action.content)) {
                add(missingField(actionIndex, "blockId", "Format text action needs blockId, blockText, content, or title."))
            }
            if (!action.hasAny(action.textToFormat, action.value, action.content) && action.rangeStart == null && action.rangeEnd == null) {
                add(missingField(actionIndex, "textToFormat", "Format text action needs textToFormat/value/content or rangeStart/rangeEnd."))
            }
            if (!action.hasAny(action.format, action.linkUrl, action.color, action.highlight)) {
                add(missingField(actionIndex, "format", "Format text action needs format, linkUrl, color, or highlight."))
            }
        }
    }

    data object Property : AiActionContract {
        override val types = setOf("ADD_PROPERTY", "UPDATE_PROPERTY", "DELETE_PROPERTY")
        override val allowedFields = PageTarget + setOf("title", "propertyName", "propertyType", "value", "content", "targetIndex")

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> =
            action.requireAny(actionIndex, "propertyName", "Property action needs propertyName or title.", action.propertyName, action.title)
    }

    data object TableCreate : AiActionContract {
        override val types = setOf("CREATE_DATABASE", "CREATE_TABLE")
        override val allowedFields = PageTarget + TableShape + setOf("title", "content", "moduleType", "targetIndex")

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> =
            action.requireAny(
                actionIndex = actionIndex,
                field = "tableTitle",
                message = "Create table action needs tableTitle, title, or content.",
                action.tableTitle,
                action.title,
                action.content,
            )
    }

    data object TableTitle : AiActionContract {
        override val types = setOf("RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE")
        override val allowedFields = TableTarget + setOf("title", "value", "content", "newColumnName")

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> =
            action.requireAny(actionIndex, "title", "Rename table action needs a new title.", action.title, action.value, action.content, action.newColumnName)
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

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> = buildList {
            if (!action.hasAny(action.columnId, action.columnName, action.propertyName, action.title)) {
                add(missingField(actionIndex, "columnName", "Column action needs columnId, columnName, propertyName, or title."))
            }
            if (action.type in setOf("RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN") && !action.hasAny(action.newColumnName, action.value, action.content)) {
                add(missingField(actionIndex, "newColumnName", "Rename column action needs newColumnName, value, or content."))
            }
            if (action.type in setOf("REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN") && action.targetIndex == null) {
                add(missingField(actionIndex, "targetIndex", "Move column action needs targetIndex."))
            }
            if (action.type == "UPDATE_FORMULA_COLUMN" && !action.hasAny(action.formula, action.value, action.content)) {
                add(missingField(actionIndex, "formula", "Formula column action needs formula, value, or content."))
            }
        }
    }

    data object TableRow : AiActionContract {
        override val types = setOf("ADD_TABLE_ROW", "DELETE_TABLE_ROW", "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW", "REORDER_TABLE_ROW", "MOVE_TABLE_ROW")
        override val allowedFields = TableTarget + RowTarget + setOf("title", "value", "content", "cellValues", "tableRows", "targetIndex")

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> = when (action.type) {
            "ADD_TABLE_ROW" -> {
                val hasCellValue = action.cellValues.values.any { value -> value.isNotBlank() }
                val hasTableRowValue = action.tableRows.any { row ->
                    row.values.any { value -> value.isNotBlank() }
                }
                if (action.hasAny(action.rowTitle, action.title, action.content) || hasCellValue || hasTableRowValue) {
                    emptyList()
                } else {
                    listOf(missingField(actionIndex, "rowTitle", "Add row action needs rowTitle, title, content, cellValues, or tableRows."))
                }
            }
            "DELETE_TABLE_ROW" -> {
                action.requireAny(actionIndex, "rowTitle", "Delete row action needs rowId, rowTitle, or title.", action.rowId, action.rowTitle, action.title)
            }
            "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW" -> buildList {
                if (!action.hasAny(action.rowId, action.rowTitle, action.title)) {
                    add(missingField(actionIndex, "rowTitle", "Update row action needs rowId, rowTitle, or title."))
                }
                if (!action.hasAny(action.newRowTitle, action.value, action.content) && action.cellValues.isEmpty()) {
                    add(missingField(actionIndex, "value", "Update row action needs newRowTitle, value, content, or cellValues."))
                }
            }
            "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" -> buildList {
                if (!action.hasAny(action.rowId, action.rowTitle, action.title)) {
                    add(missingField(actionIndex, "rowTitle", "Move row action needs rowId, rowTitle, or title."))
                }
                if (action.targetIndex == null) add(missingField(actionIndex, "targetIndex", "Move row action needs targetIndex."))
            }
            else -> emptyList()
        }
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

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> =
            action.requireAny(actionIndex, "rowTitle", "Row page block action needs rowId, rowTitle, targetTitle, or title.", action.rowId, action.rowTitle, action.targetTitle, action.title)
    }

    data object TableCell : AiActionContract {
        override val types = setOf("UPDATE_TABLE_CELL")
        override val allowedFields = TableTarget + RowTarget + ColumnTarget + setOf("title", "propertyName", "value", "content", "cellValues")

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> = buildList {
            if (!action.hasAny(action.rowId, action.rowTitle, action.title)) add(missingField(actionIndex, "rowTitle", "Cell update needs rowId, rowTitle, or title."))
            if (!action.hasAny(action.columnId, action.columnName, action.propertyName)) add(missingField(actionIndex, "columnName", "Cell update needs columnId, columnName, or propertyName."))
            if (!action.hasAny(action.value, action.content) && action.cellValues.isEmpty()) {
                add(missingField(actionIndex, "value", "Cell update needs value, content, or cellValues."))
            }
        }
    }

    data object TableView : AiActionContract {
        override val types = setOf("CHANGE_TABLE_VIEW", "SET_TABLE_VIEW", "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG")
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

        override fun requiredFieldIssues(actionIndex: Int, action: AiService.AiActionItem): List<AiActionValidationIssue> {
            if (action.type in setOf("CLEAR_TABLE_SORT", "CLEAR_TABLE_FILTER", "CLEAR_TABLE_GROUP")) return emptyList()
            return action.requireAny(
                actionIndex = actionIndex,
                field = "columnName",
                message = "Table rule action needs a target column.",
                action.columnId,
                action.columnName,
                action.propertyName,
                action.title,
                action.groupByColumnId,
                action.groupByColumnName,
            )
        }
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
            DeleteAllBlocks,
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

private fun AiService.AiActionItem.presentFields(): Set<String> = buildSet {
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

private fun AiService.AiActionItem.requireAny(
    actionIndex: Int,
    field: String,
    message: String,
    vararg values: String?,
): List<AiActionValidationIssue> =
    if (hasAny(*values)) emptyList() else listOf(missingField(actionIndex, field, message))

private fun AiService.AiActionItem.hasAny(vararg values: String?): Boolean =
    values.any { value -> !value.isNullOrBlank() }

private fun missingField(actionIndex: Int, field: String, message: String): AiActionValidationIssue =
    actionIssue(actionIndex = actionIndex, field = field, code = "missing_required_field", message = message)

private fun actionIssue(actionIndex: Int, field: String, code: String, message: String): AiActionValidationIssue =
    AiActionValidationIssue(actionIndex = actionIndex, field = field, code = code, message = message)

private fun String.normalizedActionType(): String =
    trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')

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
