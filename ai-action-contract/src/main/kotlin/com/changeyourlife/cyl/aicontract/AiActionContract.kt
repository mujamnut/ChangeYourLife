package com.changeyourlife.cyl.aicontract

import kotlinx.serialization.Serializable

const val CYL_ACTION_SCHEMA_NAME = "CYL_ACTION_SCHEMA"
const val CYL_ACTION_SCHEMA_VERSION = 2

@Serializable
data class AiTableColumnWire(
    val name: String = "",
    val type: String = "Text",
    val options: List<String> = emptyList(),
    val dateFormat: String = "",
    val timeFormat: String = "",
    val dateReminder: String = "",
    val timezoneLabel: String = "",
    val formula: String = "",
    val relationTargetTableId: String = "",
    val rollupRelationColumnName: String = "",
    val rollupTargetColumnName: String = "",
    val rollupAggregation: String = "",
)

/**
 * Backward-compatible flat wire payload. Consumers must parse it through
 * [AiActionContractSchema] before execution.
 */
@Serializable
data class AiActionWire(
    val type: String = "",
    val title: String = "",
    val targetTitle: String = "",
    val content: String = "",
    val blockType: String = "",
    val blockId: String = "",
    val blockText: String = "",
    val textToFormat: String = "",
    val format: String = "",
    val linkUrl: String = "",
    val color: String = "",
    val highlight: String = "",
    val rangeStart: Int? = null,
    val rangeEnd: Int? = null,
    val mediaUri: String = "",
    val mediaName: String = "",
    val mediaMimeType: String = "",
    val mediaSizeBytes: Long = 0,
    val isChecked: Boolean? = null,
    val propertyName: String = "",
    val propertyType: String = "Text",
    val value: String = "",
    val moduleType: String = "",
    val tableTitle: String = "",
    val tableView: String = "Table",
    val calendarDateColumnId: String = "",
    val calendarDateColumnName: String = "",
    val timelineStartColumnId: String = "",
    val timelineStartColumnName: String = "",
    val timelineEndColumnId: String = "",
    val timelineEndColumnName: String = "",
    val dashboardMetricColumnId: String = "",
    val dashboardMetricColumnName: String = "",
    val dashboardGroupColumnId: String = "",
    val dashboardGroupColumnName: String = "",
    val columnId: String = "",
    val columnName: String = "",
    val newColumnName: String = "",
    val columnType: String = "Text",
    val options: List<String> = emptyList(),
    val formula: String = "",
    val relationTargetTableId: String = "",
    val relationTargetTableTitle: String = "",
    val rollupRelationColumnId: String = "",
    val rollupRelationColumnName: String = "",
    val rollupTargetColumnId: String = "",
    val rollupTargetColumnName: String = "",
    val rollupAggregation: String = "",
    val sortDirection: String = "Ascending",
    val filterQuery: String = "",
    val groupByColumnId: String = "",
    val groupByColumnName: String = "",
    val rowId: String = "",
    val rowTitle: String = "",
    val newRowTitle: String = "",
    val rowBlockId: String = "",
    val targetIndex: Int? = null,
    val cellValues: Map<String, String> = emptyMap(),
    val tableColumns: List<AiTableColumnWire> = emptyList(),
    val tableRows: List<Map<String, String>> = emptyList(),
    val delayMinutes: Long? = null,
)

enum class AiActionDomain(val wireValue: String) {
    Page("page"),
    Block("block"),
    Property("property"),
    Database("database"),
    Column("column"),
    Row("row"),
    RowContent("row_content"),
    Cell("cell"),
    Task("task"),
    Reminder("reminder"),
}

sealed interface CylAiAction {
    val type: String
    val targetTitle: String
    val domain: AiActionDomain

    data class Page(
        override val type: String,
        override val targetTitle: String,
        val title: String,
        val content: String,
        val moduleType: String,
        val tableTitle: String,
        val tableView: String,
        val tableColumns: List<AiTableColumnWire>,
        val tableRows: List<Map<String, String>>,
        val targetIndex: Int?,
    ) : CylAiAction {
        override val domain = AiActionDomain.Page
    }

    data class Block(
        override val type: String,
        override val targetTitle: String,
        val title: String,
        val content: String,
        val value: String,
        val blockType: String,
        val blockId: String,
        val blockText: String,
        val textToFormat: String,
        val format: String,
        val linkUrl: String,
        val color: String,
        val highlight: String,
        val rangeStart: Int?,
        val rangeEnd: Int?,
        val mediaUri: String,
        val mediaName: String,
        val mediaMimeType: String,
        val mediaSizeBytes: Long,
        val isChecked: Boolean?,
        val tableTitle: String,
        val tableView: String,
        val tableColumns: List<AiTableColumnWire>,
        val tableRows: List<Map<String, String>>,
        val targetIndex: Int?,
    ) : CylAiAction {
        override val domain = AiActionDomain.Block
    }

    data class Property(
        override val type: String,
        override val targetTitle: String,
        val title: String,
        val propertyName: String,
        val propertyType: String,
        val value: String,
        val content: String,
        val targetIndex: Int?,
    ) : CylAiAction {
        override val domain = AiActionDomain.Property
    }

    data class Database(
        override val type: String,
        override val targetTitle: String,
        val title: String,
        val content: String,
        val value: String,
        val moduleType: String,
        val tableTitle: String,
        val tableView: String,
        val tableColumns: List<AiTableColumnWire>,
        val tableRows: List<Map<String, String>>,
        val targetIndex: Int?,
        val newColumnName: String,
        val columnId: String,
        val columnName: String,
        val propertyName: String,
        val calendarDateColumnId: String,
        val calendarDateColumnName: String,
        val timelineStartColumnId: String,
        val timelineStartColumnName: String,
        val timelineEndColumnId: String,
        val timelineEndColumnName: String,
        val dashboardMetricColumnId: String,
        val dashboardMetricColumnName: String,
        val dashboardGroupColumnId: String,
        val dashboardGroupColumnName: String,
        val sortDirection: String,
        val filterQuery: String,
        val groupByColumnId: String,
        val groupByColumnName: String,
    ) : CylAiAction {
        override val domain = AiActionDomain.Database
    }

    data class Column(
        override val type: String,
        override val targetTitle: String,
        val tableTitle: String,
        val title: String,
        val propertyName: String,
        val propertyType: String,
        val value: String,
        val content: String,
        val columnId: String,
        val columnName: String,
        val newColumnName: String,
        val columnType: String,
        val options: List<String>,
        val formula: String,
        val relationTargetTableId: String,
        val relationTargetTableTitle: String,
        val rollupRelationColumnId: String,
        val rollupRelationColumnName: String,
        val rollupTargetColumnId: String,
        val rollupTargetColumnName: String,
        val rollupAggregation: String,
        val targetIndex: Int?,
    ) : CylAiAction {
        override val domain = AiActionDomain.Column
    }

    data class Row(
        override val type: String,
        override val targetTitle: String,
        val tableTitle: String,
        val title: String,
        val rowId: String,
        val rowTitle: String,
        val newRowTitle: String,
        val value: String,
        val content: String,
        val cellValues: Map<String, String>,
        val tableRows: List<Map<String, String>>,
        val targetIndex: Int?,
    ) : CylAiAction {
        override val domain = AiActionDomain.Row
    }

    data class RowContent(
        override val type: String,
        override val targetTitle: String,
        val tableTitle: String,
        val title: String,
        val rowId: String,
        val rowTitle: String,
        val rowBlockId: String,
        val content: String,
        val blockType: String,
        val blockId: String,
        val blockText: String,
        val mediaUri: String,
        val mediaName: String,
        val mediaMimeType: String,
        val mediaSizeBytes: Long,
        val isChecked: Boolean?,
        val targetIndex: Int?,
    ) : CylAiAction {
        override val domain = AiActionDomain.RowContent
    }

    data class Cell(
        override val type: String,
        override val targetTitle: String,
        val tableTitle: String,
        val title: String,
        val rowId: String,
        val rowTitle: String,
        val columnId: String,
        val columnName: String,
        val propertyName: String,
        val value: String,
        val content: String,
        val cellValues: Map<String, String>,
        val filterQuery: String,
    ) : CylAiAction {
        override val domain = AiActionDomain.Cell
    }

    data class Task(
        override val type: String,
        override val targetTitle: String,
        val tableTitle: String,
        val title: String,
        val rowId: String,
        val rowTitle: String,
        val newRowTitle: String,
        val content: String,
        val value: String,
        val cellValues: Map<String, String>,
        val delayMinutes: Long?,
        val targetIndex: Int?,
    ) : CylAiAction {
        override val domain = AiActionDomain.Task
    }

    data class Reminder(
        override val type: String,
        override val targetTitle: String,
        val tableTitle: String,
        val title: String,
        val rowId: String,
        val rowTitle: String,
        val newRowTitle: String,
        val content: String,
        val value: String,
        val cellValues: Map<String, String>,
        val delayMinutes: Long?,
        val targetIndex: Int?,
    ) : CylAiAction {
        override val domain = AiActionDomain.Reminder
    }
}

data class AiActionContractIssue(
    val actionIndex: Int? = null,
    val field: String,
    val code: String,
    val message: String,
)

data class AiActionContractResult(
    val action: CylAiAction?,
    val normalizedPayload: AiActionWire,
    val issues: List<AiActionContractIssue>,
) {
    val isValid: Boolean
        get() = action != null && issues.isEmpty()
}

object AiActionContractSchema {
    private data class ContractSpec(
        val domain: AiActionDomain,
        val types: Set<String>,
        val allowedFields: Set<String>,
    )

    private val specs = listOf(
        ContractSpec(
            domain = AiActionDomain.Page,
            types = setOf("RENAME_CURRENT_PAGE", "RENAME_PAGE"),
            allowedFields = PageTarget + setOf("title", "value", "content"),
        ),
        ContractSpec(
            domain = AiActionDomain.Page,
            types = setOf("UPDATE_PAGE"),
            allowedFields = PageTarget + setOf("title", "content"),
        ),
        ContractSpec(
            domain = AiActionDomain.Page,
            types = setOf("CREATE_PAGE", "CREATE_SUBPAGE"),
            allowedFields = PageTarget + TableShape + setOf("title", "content", "moduleType", "targetIndex"),
        ),
        ContractSpec(
            domain = AiActionDomain.Block,
            types = setOf("APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK"),
            allowedFields = PageTarget + BlockPayload + TableShape + setOf("targetIndex"),
        ),
        ContractSpec(
            domain = AiActionDomain.Block,
            types = setOf("DELETE_ALL_BLOCKS"),
            allowedFields = PageTarget,
        ),
        ContractSpec(
            domain = AiActionDomain.Block,
            types = setOf("DELETE_BLOCK", "UPDATE_BLOCK", "EDIT_BLOCK", "UPDATE_TODO", "CHECK_BLOCK", "UNCHECK_BLOCK"),
            allowedFields = PageTarget + BlockTargetFields + BlockPayload + setOf("value"),
        ),
        ContractSpec(
            domain = AiActionDomain.Block,
            types = setOf("FORMAT_BLOCK_TEXT"),
            allowedFields = PageTarget + BlockTargetFields + setOf(
                "textToFormat",
                "format",
                "linkUrl",
                "color",
                "highlight",
                "rangeStart",
                "rangeEnd",
                "value",
            ),
        ),
        ContractSpec(
            domain = AiActionDomain.Property,
            types = setOf("ADD_PROPERTY", "UPDATE_PROPERTY", "DELETE_PROPERTY"),
            allowedFields = PageTarget + setOf("title", "propertyName", "propertyType", "value", "content", "targetIndex"),
        ),
        ContractSpec(
            domain = AiActionDomain.Database,
            types = setOf("CREATE_DATABASE", "CREATE_TABLE"),
            allowedFields = PageTarget + TableShape + setOf("title", "content", "moduleType", "targetIndex"),
        ),
        ContractSpec(
            domain = AiActionDomain.Database,
            types = setOf("RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE"),
            allowedFields = TableTarget + setOf("title", "value", "content", "newColumnName"),
        ),
        ContractSpec(
            domain = AiActionDomain.Column,
            types = setOf(
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
            ),
            allowedFields = TableTarget + ColumnTarget + ColumnConfig + setOf(
                "title",
                "propertyName",
                "propertyType",
                "value",
                "content",
                "targetIndex",
            ),
        ),
        ContractSpec(
            domain = AiActionDomain.Row,
            types = setOf(
                "ADD_TABLE_ROW",
                "DELETE_TABLE_ROW",
                "UPDATE_TABLE_ROW",
                "RENAME_TABLE_ROW",
                "REORDER_TABLE_ROW",
                "MOVE_TABLE_ROW",
            ),
            allowedFields = TableTarget + RowTarget + setOf("title", "value", "content", "cellValues", "tableRows", "targetIndex"),
        ),
        ContractSpec(
            domain = AiActionDomain.RowContent,
            types = setOf(
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
            ),
            allowedFields = TableTarget + RowTarget + BlockTargetFields + BlockPayload + setOf("rowBlockId", "targetIndex"),
        ),
        ContractSpec(
            domain = AiActionDomain.Cell,
            types = setOf("UPDATE_TABLE_CELL", "CLEAR_TABLE_CELL", "CLEAR_TABLE_CELLS"),
            allowedFields = TableTarget + RowTarget + ColumnTarget + setOf(
                "title",
                "propertyName",
                "value",
                "content",
                "cellValues",
                "filterQuery",
            ),
        ),
        ContractSpec(
            domain = AiActionDomain.Database,
            types = setOf(
                "CHANGE_TABLE_VIEW",
                "SET_TABLE_VIEW",
                "SET_TABLE_VIEW_CONFIG",
                "CONFIGURE_TABLE_VIEW",
                "UPDATE_TABLE_VIEW_CONFIG",
            ),
            allowedFields = TableTarget + ColumnTarget + ViewConfig + setOf("title", "tableView", "value", "content"),
        ),
        ContractSpec(
            domain = AiActionDomain.Database,
            types = setOf(
                "SORT_TABLE",
                "SET_TABLE_SORT",
                "CLEAR_TABLE_SORT",
                "FILTER_TABLE",
                "SET_TABLE_FILTER",
                "CLEAR_TABLE_FILTER",
                "GROUP_TABLE",
                "SET_TABLE_GROUP",
                "CLEAR_TABLE_GROUP",
            ),
            allowedFields = TableTarget + ColumnTarget + setOf(
                "title",
                "propertyName",
                "value",
                "content",
                "sortDirection",
                "filterQuery",
                "groupByColumnId",
                "groupByColumnName",
            ),
        ),
        ContractSpec(
            domain = AiActionDomain.Task,
            types = setOf("CREATE_TASK"),
            allowedFields = TableTarget + RowTarget + setOf("title", "content", "value", "cellValues", "delayMinutes", "targetIndex"),
        ),
        ContractSpec(
            domain = AiActionDomain.Reminder,
            types = setOf("CREATE_REMINDER"),
            allowedFields = TableTarget + RowTarget + setOf("title", "content", "value", "cellValues", "delayMinutes", "targetIndex"),
        ),
    )

    val supportedTypes: Set<String> = specs.flatMapTo(linkedSetOf()) { spec -> spec.types }

    val supportedTableColumnTypes: Set<String> = linkedSetOf(
        "Text",
        "Number",
        "Select",
        "MultiSelect",
        "Status",
        "Date",
        "FilesMedia",
        "Checkbox",
        "Formula",
        "Relation",
        "Rollup",
    )

    /**
     * Renders the model-facing action catalog from the same specs used by runtime validation.
     * Keeping this here prevents backend prompts from advertising actions or fields that
     * Android cannot parse and execute.
     */
    fun promptInstructions(): String = buildString {
        appendLine("$CYL_ACTION_SCHEMA_NAME version $CYL_ACTION_SCHEMA_VERSION")
        appendLine("Every action object must include type and may only include fields listed for that action group.")
        appendLine("Supported action groups:")
        specs.forEach { spec ->
            append("- ")
            append(spec.domain.wireValue)
            append(": ")
            append(spec.types.sorted().joinToString(", "))
            appendLine()
            append("  allowed fields: ")
            appendLine(spec.allowedFields.sorted().joinToString(", "))
        }
        append("Supported table column types: ")
        appendLine(supportedTableColumnTypes.joinToString(", "))
        appendLine("Do not invent action types, field names, or table column types outside this contract.")
    }.trimEnd()

    fun domainFor(type: String): AiActionDomain? {
        val normalizedType = normalizeType(type)
        return specs.firstOrNull { spec -> normalizedType in spec.types }?.domain
    }

    fun parse(actionIndex: Int?, payload: AiActionWire): AiActionContractResult {
        val normalizedType = normalizeType(payload.type)
        val normalizedPayload = payload.copy(type = normalizedType)
        if (normalizedType.isBlank()) {
            return invalid(
                actionIndex = actionIndex,
                payload = normalizedPayload,
                field = "type",
                code = "missing_action_type",
                message = "Action type is required.",
            )
        }
        val spec = specs.firstOrNull { candidate -> normalizedType in candidate.types }
            ?: return invalid(
                actionIndex = actionIndex,
                payload = normalizedPayload,
                field = "type",
                code = "unsupported_action_type",
                message = "Unsupported CYL action type: $normalizedType.",
            )

        val issues = buildList {
            val unexpectedFields = normalizedPayload.presentFields() - spec.allowedFields
            if (unexpectedFields.isNotEmpty()) {
                add(
                    AiActionContractIssue(
                        actionIndex = actionIndex,
                        field = unexpectedFields.sorted().joinToString(","),
                        code = "unexpected_action_fields",
                        message = "Action $normalizedType included fields that do not belong to this contract: ${unexpectedFields.sorted().joinToString(", ")}.",
                    ),
                )
            }
            addAll(normalizedPayload.requiredFieldIssues(actionIndex))
        }
        return AiActionContractResult(
            action = spec.domain.toAction(normalizedPayload),
            normalizedPayload = normalizedPayload,
            issues = issues,
        )
    }

    fun normalizeType(value: String): String =
        value.trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')

    private fun invalid(
        actionIndex: Int?,
        payload: AiActionWire,
        field: String,
        code: String,
        message: String,
    ): AiActionContractResult = AiActionContractResult(
        action = null,
        normalizedPayload = payload,
        issues = listOf(
            AiActionContractIssue(
                actionIndex = actionIndex,
                field = field,
                code = code,
                message = message,
            ),
        ),
    )
}

private fun AiActionDomain.toAction(payload: AiActionWire): CylAiAction = when (this) {
    AiActionDomain.Page -> CylAiAction.Page(
        type = payload.type,
        targetTitle = payload.targetTitle,
        title = payload.title,
        content = payload.content,
        moduleType = payload.moduleType,
        tableTitle = payload.tableTitle,
        tableView = payload.tableView,
        tableColumns = payload.tableColumns,
        tableRows = payload.tableRows,
        targetIndex = payload.targetIndex,
    )
    AiActionDomain.Block -> CylAiAction.Block(
        type = payload.type,
        targetTitle = payload.targetTitle,
        title = payload.title,
        content = payload.content,
        value = payload.value,
        blockType = payload.blockType,
        blockId = payload.blockId,
        blockText = payload.blockText,
        textToFormat = payload.textToFormat,
        format = payload.format,
        linkUrl = payload.linkUrl,
        color = payload.color,
        highlight = payload.highlight,
        rangeStart = payload.rangeStart,
        rangeEnd = payload.rangeEnd,
        mediaUri = payload.mediaUri,
        mediaName = payload.mediaName,
        mediaMimeType = payload.mediaMimeType,
        mediaSizeBytes = payload.mediaSizeBytes,
        isChecked = payload.isChecked,
        tableTitle = payload.tableTitle,
        tableView = payload.tableView,
        tableColumns = payload.tableColumns,
        tableRows = payload.tableRows,
        targetIndex = payload.targetIndex,
    )
    AiActionDomain.Property -> CylAiAction.Property(
        type = payload.type,
        targetTitle = payload.targetTitle,
        title = payload.title,
        propertyName = payload.propertyName,
        propertyType = payload.propertyType,
        value = payload.value,
        content = payload.content,
        targetIndex = payload.targetIndex,
    )
    AiActionDomain.Database -> CylAiAction.Database(
        type = payload.type,
        targetTitle = payload.targetTitle,
        title = payload.title,
        content = payload.content,
        value = payload.value,
        moduleType = payload.moduleType,
        tableTitle = payload.tableTitle,
        tableView = payload.tableView,
        tableColumns = payload.tableColumns,
        tableRows = payload.tableRows,
        targetIndex = payload.targetIndex,
        newColumnName = payload.newColumnName,
        columnId = payload.columnId,
        columnName = payload.columnName,
        propertyName = payload.propertyName,
        calendarDateColumnId = payload.calendarDateColumnId,
        calendarDateColumnName = payload.calendarDateColumnName,
        timelineStartColumnId = payload.timelineStartColumnId,
        timelineStartColumnName = payload.timelineStartColumnName,
        timelineEndColumnId = payload.timelineEndColumnId,
        timelineEndColumnName = payload.timelineEndColumnName,
        dashboardMetricColumnId = payload.dashboardMetricColumnId,
        dashboardMetricColumnName = payload.dashboardMetricColumnName,
        dashboardGroupColumnId = payload.dashboardGroupColumnId,
        dashboardGroupColumnName = payload.dashboardGroupColumnName,
        sortDirection = payload.sortDirection,
        filterQuery = payload.filterQuery,
        groupByColumnId = payload.groupByColumnId,
        groupByColumnName = payload.groupByColumnName,
    )
    AiActionDomain.Column -> CylAiAction.Column(
        type = payload.type,
        targetTitle = payload.targetTitle,
        tableTitle = payload.tableTitle,
        title = payload.title,
        propertyName = payload.propertyName,
        propertyType = payload.propertyType,
        value = payload.value,
        content = payload.content,
        columnId = payload.columnId,
        columnName = payload.columnName,
        newColumnName = payload.newColumnName,
        columnType = payload.columnType,
        options = payload.options,
        formula = payload.formula,
        relationTargetTableId = payload.relationTargetTableId,
        relationTargetTableTitle = payload.relationTargetTableTitle,
        rollupRelationColumnId = payload.rollupRelationColumnId,
        rollupRelationColumnName = payload.rollupRelationColumnName,
        rollupTargetColumnId = payload.rollupTargetColumnId,
        rollupTargetColumnName = payload.rollupTargetColumnName,
        rollupAggregation = payload.rollupAggregation,
        targetIndex = payload.targetIndex,
    )
    AiActionDomain.Row -> CylAiAction.Row(
        type = payload.type,
        targetTitle = payload.targetTitle,
        tableTitle = payload.tableTitle,
        title = payload.title,
        rowId = payload.rowId,
        rowTitle = payload.rowTitle,
        newRowTitle = payload.newRowTitle,
        value = payload.value,
        content = payload.content,
        cellValues = payload.cellValues,
        tableRows = payload.tableRows,
        targetIndex = payload.targetIndex,
    )
    AiActionDomain.RowContent -> CylAiAction.RowContent(
        type = payload.type,
        targetTitle = payload.targetTitle,
        tableTitle = payload.tableTitle,
        title = payload.title,
        rowId = payload.rowId,
        rowTitle = payload.rowTitle,
        rowBlockId = payload.rowBlockId,
        content = payload.content,
        blockType = payload.blockType,
        blockId = payload.blockId,
        blockText = payload.blockText,
        mediaUri = payload.mediaUri,
        mediaName = payload.mediaName,
        mediaMimeType = payload.mediaMimeType,
        mediaSizeBytes = payload.mediaSizeBytes,
        isChecked = payload.isChecked,
        targetIndex = payload.targetIndex,
    )
    AiActionDomain.Cell -> CylAiAction.Cell(
        type = payload.type,
        targetTitle = payload.targetTitle,
        tableTitle = payload.tableTitle,
        title = payload.title,
        rowId = payload.rowId,
        rowTitle = payload.rowTitle,
        columnId = payload.columnId,
        columnName = payload.columnName,
        propertyName = payload.propertyName,
        value = payload.value,
        content = payload.content,
        cellValues = payload.cellValues,
        filterQuery = payload.filterQuery,
    )
    AiActionDomain.Task -> CylAiAction.Task(
        type = payload.type,
        targetTitle = payload.targetTitle,
        tableTitle = payload.tableTitle,
        title = payload.title,
        rowId = payload.rowId,
        rowTitle = payload.rowTitle,
        newRowTitle = payload.newRowTitle,
        content = payload.content,
        value = payload.value,
        cellValues = payload.cellValues,
        delayMinutes = payload.delayMinutes,
        targetIndex = payload.targetIndex,
    )
    AiActionDomain.Reminder -> CylAiAction.Reminder(
        type = payload.type,
        targetTitle = payload.targetTitle,
        tableTitle = payload.tableTitle,
        title = payload.title,
        rowId = payload.rowId,
        rowTitle = payload.rowTitle,
        newRowTitle = payload.newRowTitle,
        content = payload.content,
        value = payload.value,
        cellValues = payload.cellValues,
        delayMinutes = payload.delayMinutes,
        targetIndex = payload.targetIndex,
    )
}

private fun AiActionWire.requiredFieldIssues(actionIndex: Int?): List<AiActionContractIssue> = buildList {
    fun requireAny(field: String, message: String, vararg values: String?) {
        if (values.none { value -> !value.isNullOrBlank() }) {
            add(missingField(actionIndex, field, message))
        }
    }

    when (type) {
        "RENAME_CURRENT_PAGE", "RENAME_PAGE" ->
            requireAny("title", "Rename page action needs a new title.", title, value, content)

        "CREATE_PAGE", "CREATE_SUBPAGE" ->
            requireAny(
                "title",
                "Create page action needs title, content, tableTitle, or moduleType.",
                title,
                content,
                tableTitle,
                moduleType,
            )

        "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> {
            val normalizedBlockType = AiActionContractSchema.normalizeType(blockType)
            when {
                normalizedBlockType == "DIVIDER" -> Unit
                normalizedBlockType in setOf("MEDIAFILE", "MEDIA_FILE") && mediaUri.isBlank() ->
                    add(missingField(actionIndex, "mediaUri", "Media/file block needs mediaUri."))
                content.isBlank() && title.isBlank() ->
                    add(missingField(actionIndex, "content", "Block content is required unless the block is a divider."))
            }
        }

        "DELETE_BLOCK", "UPDATE_BLOCK", "EDIT_BLOCK", "UPDATE_TODO", "CHECK_BLOCK", "UNCHECK_BLOCK" -> {
            requireAny("blockId", "$type needs blockId, blockText, content, or title.", blockId, blockText, content, title)
            if (type in setOf("UPDATE_BLOCK", "EDIT_BLOCK")) {
                requireAny("content", "Update block action needs replacement content.", content, value)
            }
        }

        "FORMAT_BLOCK_TEXT" -> {
            requireAny("blockId", "Format text action needs blockId, blockText, content, or title.", blockId, blockText, content, title)
            if (textToFormat.isBlank() && value.isBlank() && content.isBlank() && rangeStart == null && rangeEnd == null) {
                add(missingField(actionIndex, "textToFormat", "Format text action needs textToFormat/value/content or rangeStart/rangeEnd."))
            }
            requireAny("format", "Format text action needs format, linkUrl, color, or highlight.", format, linkUrl, color, highlight)
        }

        "ADD_PROPERTY", "UPDATE_PROPERTY", "DELETE_PROPERTY" ->
            requireAny("propertyName", "Property action needs propertyName or title.", propertyName, title)

        "CREATE_DATABASE", "CREATE_TABLE" ->
            requireAny("tableTitle", "Create table action needs tableTitle, title, or content.", tableTitle, title, content)

        "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" ->
            requireAny("title", "Rename table action needs a new title.", title, value, content, newColumnName)

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
        -> {
            requireAny("columnName", "Column action needs columnId, columnName, propertyName, or title.", columnId, columnName, propertyName, title)
            if (type in setOf("RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN")) {
                requireAny("newColumnName", "Rename column action needs newColumnName, value, or content.", newColumnName, value, content)
            }
            if (type in setOf("REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN") && targetIndex == null) {
                add(missingField(actionIndex, "targetIndex", "Move column action needs targetIndex."))
            }
            if (type == "UPDATE_FORMULA_COLUMN") {
                requireAny("formula", "Formula column action needs formula, value, or content.", formula, value, content)
            }
        }

        "ADD_TABLE_ROW" -> {
            val hasCellValue = cellValues.values.any { cellValue -> cellValue.isNotBlank() }
            val hasTableRowValue = tableRows.any { row -> row.values.any { cellValue -> cellValue.isNotBlank() } }
            if (rowTitle.isBlank() && title.isBlank() && content.isBlank() && !hasCellValue && !hasTableRowValue) {
                add(missingField(actionIndex, "rowTitle", "Add row action needs rowTitle, title, content, cellValues, or tableRows."))
            }
        }

        "DELETE_TABLE_ROW" ->
            requireAny("rowTitle", "Delete row action needs rowId, rowTitle, or title.", rowId, rowTitle, title)

        "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW" -> {
            requireAny("rowTitle", "Update row action needs rowId, rowTitle, or title.", rowId, rowTitle, title)
            if (newRowTitle.isBlank() && value.isBlank() && content.isBlank() && cellValues.isEmpty()) {
                add(missingField(actionIndex, "value", "Update row action needs newRowTitle, value, content, or cellValues."))
            }
        }

        "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" -> {
            requireAny("rowTitle", "Move row action needs rowId, rowTitle, or title.", rowId, rowTitle, title)
            if (targetIndex == null) add(missingField(actionIndex, "targetIndex", "Move row action needs targetIndex."))
        }

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
        -> requireAny("rowTitle", "Row page block action needs rowId, rowTitle, targetTitle, or title.", rowId, rowTitle, targetTitle, title)

        "UPDATE_TABLE_CELL", "CLEAR_TABLE_CELL", "CLEAR_TABLE_CELLS" -> {
            if (type != "CLEAR_TABLE_CELLS") {
                requireAny("rowTitle", "Cell update needs rowId, rowTitle, or title.", rowId, rowTitle, title)
            }
            requireAny("columnName", "Cell update needs columnId, columnName, or propertyName.", columnId, columnName, propertyName)
            if (type == "UPDATE_TABLE_CELL" && value.isBlank() && content.isBlank() && cellValues.isEmpty()) {
                add(missingField(actionIndex, "value", "Cell update needs value, content, or cellValues."))
            }
            if (type == "CLEAR_TABLE_CELLS") {
                requireAny("filterQuery", "Bulk cell clear needs a value to match.", filterQuery, value, rowTitle, content)
            }
        }

        "SORT_TABLE", "SET_TABLE_SORT",
        "FILTER_TABLE", "SET_TABLE_FILTER",
        "GROUP_TABLE", "SET_TABLE_GROUP",
        -> requireAny(
            "columnName",
            "Table rule action needs a target column.",
            columnId,
            columnName,
            propertyName,
            title,
            groupByColumnId,
            groupByColumnName,
        )

        "CREATE_TASK" ->
            requireAny(
                "title",
                "Create task action needs title, rowTitle, content, value, or a task cell value.",
                title,
                rowTitle,
                content,
                value,
                cellValues.taskLikeValue(),
            )

        "CREATE_REMINDER" -> {
            requireAny(
                "title",
                "Create reminder action needs title, rowTitle, content, value, or a task cell value.",
                title,
                rowTitle,
                content,
                value,
                cellValues.taskLikeValue(),
            )
            val hasDateValue = cellValues.entries.any { (key, cellValue) ->
                key.normalizedHumanKey() in ReminderDateFieldNames && cellValue.isNotBlank()
            }
            if (delayMinutes == null && !hasDateValue) {
                add(
                    missingField(
                        actionIndex = actionIndex,
                        field = "delayMinutes|cellValues.date",
                        message = "Create reminder action needs a positive delayMinutes or a date/time value in cellValues.",
                    ),
                )
            } else if (delayMinutes != null && delayMinutes <= 0) {
                add(
                    AiActionContractIssue(
                        actionIndex = actionIndex,
                        field = "delayMinutes",
                        code = "invalid_field_value",
                        message = "Reminder delayMinutes must be greater than zero.",
                    ),
                )
            }
        }
    }
}

private fun Map<String, String>.taskLikeValue(): String? =
    entries.firstOrNull { (key, value) ->
        key.normalizedHumanKey() in TaskTitleFieldNames && value.isNotBlank()
    }?.value

private fun String.normalizedHumanKey(): String =
    trim().lowercase().replace('_', ' ').replace('-', ' ')

private fun missingField(actionIndex: Int?, field: String, message: String): AiActionContractIssue =
    AiActionContractIssue(
        actionIndex = actionIndex,
        field = field,
        code = "missing_required_field",
        message = message,
    )

private fun AiActionWire.presentFields(): Set<String> = buildSet {
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
private val TaskTitleFieldNames = setOf("task", "name", "title", "item", "reminder")
private val ReminderDateFieldNames = setOf("date", "due date", "deadline", "time", "reminder")
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
