package com.changeyourlife.cyl.aicontract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val CYL_ACTION_SCHEMA_NAME = "CYL_ACTION_SCHEMA"
const val CYL_ACTION_SCHEMA_VERSION = 4

@Serializable
data class AiTableColumnWire(
    val name: String = "",
    val type: String = "Text",
    val options: List<String> = emptyList(),
    val dateFormat: String = "",
    val timeFormat: String = "",
    val dateReminder: String = "",
    val timezoneLabel: String = "",
    val isHidden: Boolean? = null,
    val isRequired: Boolean? = null,
    val wrapContent: Boolean? = null,
    val widthDp: Int? = null,
    val defaultValue: String = "",
    val description: String = "",
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
    val mediaId: String = "",
    val isChecked: Boolean? = null,
    val propertyName: String = "",
    val newPropertyName: String = "",
    val propertyType: String = "",
    val value: String = "",
    val moveDirection: String = "",
    val parentPageId: String = "",
    val parentPageTitle: String = "",
    val sourcePageId: String = "",
    val sourcePageTitle: String = "",
    val sourceTableBlockId: String = "",
    val sourceTableTitle: String = "",
    val moduleType: String = "",
    val tableTitle: String = "",
    val tableView: String = "",
    val viewId: String = "",
    val viewName: String = "",
    val newViewName: String = "",
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
    val columnType: String = "",
    val options: List<String> = emptyList(),
    val optionId: String = "",
    val optionName: String = "",
    val newOptionName: String = "",
    val optionColor: String = "",
    val dateFormat: String = "",
    val timeFormat: String = "",
    val dateReminder: String = "",
    val timezoneLabel: String = "",
    val isHidden: Boolean? = null,
    val isRequired: Boolean? = null,
    val wrapContent: Boolean? = null,
    val widthDp: Int? = null,
    val defaultValue: String = "",
    val clearDefaultValue: Boolean? = null,
    val description: String = "",
    val clearDescription: Boolean? = null,
    val formula: String = "",
    val relationTargetTableId: String = "",
    val relationTargetTableTitle: String = "",
    val rollupRelationColumnId: String = "",
    val rollupRelationColumnName: String = "",
    val rollupTargetColumnId: String = "",
    val rollupTargetColumnName: String = "",
    val rollupAggregation: String = "",
    val sortDirection: String = "",
    val filterQuery: String = "",
    val filterOperator: String = "",
    val groupByColumnId: String = "",
    val groupByColumnName: String = "",
    val rowId: String = "",
    val rowIds: List<String> = emptyList(),
    val rowTitle: String = "",
    val newRowTitle: String = "",
    val rowBlockId: String = "",
    val targetIndex: Int? = null,
    val cellValues: Map<String, String> = emptyMap(),
    val relationRowIds: List<String> = emptyList(),
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
        val parentPageId: String,
        val parentPageTitle: String,
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
        val moveDirection: String,
    ) : CylAiAction {
        override val domain = AiActionDomain.Block
    }

    data class Property(
        override val type: String,
        override val targetTitle: String,
        val title: String,
        val propertyName: String,
        val newPropertyName: String,
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
        val viewId: String,
        val viewName: String,
        val newViewName: String,
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
        val filterOperator: String,
        val groupByColumnId: String,
        val groupByColumnName: String,
        val sourcePageId: String,
        val sourcePageTitle: String,
        val sourceTableBlockId: String,
        val sourceTableTitle: String,
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
        val optionId: String,
        val optionName: String,
        val newOptionName: String,
        val optionColor: String,
        val dateFormat: String,
        val timeFormat: String,
        val dateReminder: String,
        val timezoneLabel: String,
        val isHidden: Boolean?,
        val isRequired: Boolean?,
        val wrapContent: Boolean?,
        val widthDp: Int?,
        val defaultValue: String,
        val clearDefaultValue: Boolean?,
        val description: String,
        val clearDescription: Boolean?,
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
        val rowIds: List<String>,
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
        val rowIds: List<String>,
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
        val rowIds: List<String>,
        val rowTitle: String,
        val columnId: String,
        val columnName: String,
        val propertyName: String,
        val value: String,
        val content: String,
        val cellValues: Map<String, String>,
        val filterQuery: String,
        val relationRowIds: List<String>,
        val mediaId: String,
        val mediaUri: String,
        val mediaName: String,
        val mediaMimeType: String,
        val mediaSizeBytes: Long,
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
        val columnId: String,
        val columnName: String,
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
            domain = AiActionDomain.Page,
            types = setOf("MOVE_PAGE"),
            allowedFields = PageTarget + setOf("parentPageId", "parentPageTitle"),
        ),
        ContractSpec(
            domain = AiActionDomain.Page,
            types = setOf("DUPLICATE_PAGE"),
            allowedFields = PageTarget + setOf("title"),
        ),
        ContractSpec(
            domain = AiActionDomain.Page,
            types = setOf("TRASH_PAGE", "RESTORE_PAGE", "DELETE_PAGE_PERMANENTLY"),
            allowedFields = PageTarget,
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
            types = setOf("MOVE_BLOCK"),
            allowedFields = PageTarget + BlockTargetFields + setOf("targetIndex", "moveDirection"),
        ),
        ContractSpec(
            domain = AiActionDomain.Block,
            types = setOf("INDENT_BLOCK", "OUTDENT_BLOCK", "DUPLICATE_BLOCK"),
            allowedFields = PageTarget + BlockTargetFields + setOf("targetIndex"),
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
            domain = AiActionDomain.Property,
            types = setOf("RENAME_PROPERTY", "DUPLICATE_PROPERTY"),
            allowedFields = PageTarget + setOf("title", "propertyName", "newPropertyName", "targetIndex"),
        ),
        ContractSpec(
            domain = AiActionDomain.Property,
            types = setOf("MOVE_PROPERTY"),
            allowedFields = PageTarget + setOf("title", "propertyName", "targetIndex"),
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
            domain = AiActionDomain.Database,
            types = setOf("DUPLICATE_DATABASE"),
            allowedFields = TableTarget + setOf("title", "targetIndex"),
        ),
        ContractSpec(
            domain = AiActionDomain.Database,
            types = setOf("ATTACH_TABLE_DATA_SOURCE"),
            allowedFields = TableTarget + setOf(
                "sourcePageId",
                "sourcePageTitle",
                "sourceTableBlockId",
                "sourceTableTitle",
            ),
        ),
        ContractSpec(
            domain = AiActionDomain.Database,
            types = setOf("CLEAR_TABLE_DATA_SOURCE"),
            allowedFields = TableTarget,
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
                "UPDATE_TABLE_DATE_CONFIG",
                "UPDATE_FORMULA_COLUMN",
                "UPDATE_RELATION_COLUMN",
                "UPDATE_ROLLUP_COLUMN",
                "ADD_TABLE_COLUMN_OPTION",
                "UPDATE_TABLE_COLUMN_OPTION",
                "DELETE_TABLE_COLUMN_OPTION",
                "REORDER_TABLE_COLUMN",
                "MOVE_TABLE_COLUMN",
                "DUPLICATE_TABLE_COLUMN",
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
                "DUPLICATE_TABLE_ROW",
                "DELETE_TABLE_ROWS",
                "UPDATE_TABLE_ROWS",
            ),
            allowedFields = TableTarget + RowTarget + setOf(
                "title",
                "value",
                "content",
                "cellValues",
                "tableRows",
                "targetIndex",
                "rowIds",
                "columnId",
                "columnName",
                "propertyName",
                "filterQuery",
            ),
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
            types = setOf(
                "UPDATE_TABLE_CELL",
                "CLEAR_TABLE_CELL",
                "CLEAR_TABLE_CELLS",
                "SET_RELATION_CELL",
                "CLEAR_RELATION_CELL",
                "ADD_MEDIA_CELL",
                "REMOVE_MEDIA_CELL",
                "CLEAR_MEDIA_CELL",
            ),
            allowedFields = TableTarget + RowTarget + ColumnTarget + setOf(
                "title",
                "propertyName",
                "value",
                "content",
                "cellValues",
                "filterQuery",
                "relationRowIds",
                "mediaId",
                "mediaUri",
                "mediaName",
                "mediaMimeType",
                "mediaSizeBytes",
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
                "CREATE_TABLE_SAVED_VIEW",
                "RENAME_TABLE_SAVED_VIEW",
                "DELETE_TABLE_SAVED_VIEW",
                "ACTIVATE_TABLE_SAVED_VIEW",
            ),
            allowedFields = TableTarget + ViewConfig + setOf(
                "title",
                "tableView",
                "viewId",
                "viewName",
                "newViewName",
                "value",
                "content",
                "sortDirection",
                "filterQuery",
                "filterOperator",
                "columnId",
                "columnName",
                "propertyName",
                "groupByColumnId",
                "groupByColumnName",
            ),
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
                "filterOperator",
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
            types = setOf("CREATE_REMINDER", "CANCEL_REMINDER", "RESCHEDULE_REMINDER", "COMPLETE_REMINDER"),
            allowedFields = TableTarget + RowTarget + ColumnTarget + setOf(
                "title",
                "content",
                "value",
                "cellValues",
                "delayMinutes",
                "targetIndex",
            ),
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

    val supportedTableViews: Set<String> = linkedSetOf(
        "Table",
        "List",
        "Board",
        "Calendar",
        "Gallery",
        "Timeline",
        "Dashboard",
    )

    val supportedFilterOperators: Set<String> = linkedSetOf(
        "Contains",
        "NotContains",
        "Equals",
        "NotEquals",
        "IsEmpty",
        "IsNotEmpty",
        "GreaterThan",
        "GreaterThanOrEqual",
        "LessThan",
        "LessThanOrEqual",
        "Before",
        "After",
        "OnOrBefore",
        "OnOrAfter",
    )

    val supportedDateFormats: Set<String> = linkedSetOf(
        "DayMonthYear",
        "MonthDayYear",
        "YearMonthDay",
    )

    val supportedTimeFormats: Set<String> = linkedSetOf(
        "Hidden",
        "TwelveHour",
        "TwentyFourHour",
    )

    val supportedDateReminders: Set<String> = linkedSetOf(
        "None",
        "AtTimeOfEvent",
        "FiveMinutesBefore",
        "TenMinutesBefore",
        "FifteenMinutesBefore",
        "ThirtyMinutesBefore",
        "OneHourBefore",
        "TwoHoursBefore",
        "OnDayOfEvent",
        "OneDayBefore",
        "TwoDaysBefore",
        "OneWeekBefore",
    )

    val supportedOptionColors: Set<String> = linkedSetOf(
        "Gray",
        "Red",
        "Orange",
        "Yellow",
        "Green",
        "Blue",
        "Purple",
        "Pink",
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
        append("Supported table views: ")
        appendLine(supportedTableViews.joinToString(", "))
        append("Supported filter operators: ")
        appendLine(supportedFilterOperators.joinToString(", "))
        append("Supported date formats: ")
        appendLine(supportedDateFormats.joinToString(", "))
        append("Supported time formats: ")
        appendLine(supportedTimeFormats.joinToString(", "))
        append("Supported date reminders: ")
        appendLine(supportedDateReminders.joinToString(", "))
        append("Supported option colors: ")
        appendLine(supportedOptionColors.joinToString(", "))
        appendLine("Do not invent action types, field names, or table column types outside this contract.")
    }.trimEnd()

    /**
     * Provider-facing JSON Schema for the complete assistant envelope.
     *
     * Runtime validation remains authoritative. This schema narrows generation before
     * decoding and is shared by native function calling and json_schema responses.
     */
    fun structuredResponseJsonSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("reply"))
                add(JsonPrimitive("actions"))
            },
        )
        put(
            "properties",
            buildJsonObject {
                put(
                    "reply",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Concise user-facing reply in the user's language.")
                    },
                )
                put(
                    "actions",
                    buildJsonObject {
                        put("type", "array")
                        put("maxItems", 64)
                        put(
                            "items",
                            buildJsonObject {
                                put(
                                    "oneOf",
                                    buildJsonArray {
                                        specs.forEach { spec -> add(spec.toJsonSchema()) }
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

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

    private fun ContractSpec.toJsonSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("type"))
            },
        )
        put(
            "properties",
            buildJsonObject {
                put("type", stringSchema(types))
                allowedFields.sorted().forEach { field ->
                    put(field, actionFieldJsonSchema(field))
                }
            },
        )
    }

    private fun actionFieldJsonSchema(field: String): JsonObject = when (field) {
        "isChecked",
        "isHidden",
        "isRequired",
        "wrapContent",
        "clearDefaultValue",
        "clearDescription",
        -> primitiveSchema("boolean")

        "rangeStart",
        "rangeEnd",
        "targetIndex",
        -> primitiveSchema("integer")

        "widthDp" -> buildJsonObject {
            put("type", "integer")
            put("minimum", 56)
            put("maximum", 640)
        }

        "mediaSizeBytes",
        "delayMinutes",
        -> buildJsonObject {
            put("type", "integer")
            put("minimum", 0)
        }

        "options",
        "rowIds",
        "relationRowIds",
        -> stringArraySchema()

        "cellValues" -> stringMapSchema()
        "tableRows" -> buildJsonObject {
            put("type", "array")
            put(
                "items",
                stringMapSchema(),
            )
        }

        "tableColumns" -> buildJsonObject {
            put("type", "array")
            put("items", tableColumnJsonSchema())
        }

        "columnType" -> stringSchema(supportedTableColumnTypes)
        "tableView" -> stringSchema(supportedTableViews)
        "filterOperator" -> stringSchema(supportedFilterOperators)
        "dateFormat" -> stringSchema(supportedDateFormats)
        "timeFormat" -> stringSchema(supportedTimeFormats)
        "dateReminder" -> stringSchema(supportedDateReminders)
        "optionColor" -> stringSchema(supportedOptionColors)
        else -> primitiveSchema("string")
    }

    private fun tableColumnJsonSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("name"))
            },
        )
        put(
            "properties",
            buildJsonObject {
                put("name", primitiveSchema("string"))
                put("type", stringSchema(supportedTableColumnTypes))
                put("options", stringArraySchema())
                put("dateFormat", stringSchema(supportedDateFormats))
                put("timeFormat", stringSchema(supportedTimeFormats))
                put("dateReminder", stringSchema(supportedDateReminders))
                put("timezoneLabel", primitiveSchema("string"))
                put("isHidden", primitiveSchema("boolean"))
                put("isRequired", primitiveSchema("boolean"))
                put("wrapContent", primitiveSchema("boolean"))
                put(
                    "widthDp",
                    buildJsonObject {
                        put("type", "integer")
                        put("minimum", 56)
                        put("maximum", 640)
                    },
                )
                put("defaultValue", primitiveSchema("string"))
                put("description", primitiveSchema("string"))
                put("formula", primitiveSchema("string"))
                put("relationTargetTableId", primitiveSchema("string"))
                put("rollupRelationColumnName", primitiveSchema("string"))
                put("rollupTargetColumnName", primitiveSchema("string"))
                put("rollupAggregation", primitiveSchema("string"))
            },
        )
    }

    private fun primitiveSchema(type: String): JsonObject = buildJsonObject {
        put("type", type)
    }

    private fun stringSchema(values: Set<String>): JsonObject = buildJsonObject {
        put("type", "string")
        put(
            "enum",
            buildJsonArray {
                values.sorted().forEach { value -> add(JsonPrimitive(value)) }
            },
        )
    }

    private fun stringArraySchema(): JsonObject = buildJsonObject {
        put("type", "array")
        put("items", primitiveSchema("string"))
    }

    private fun stringMapSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", primitiveSchema("string"))
    }
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
        parentPageId = payload.parentPageId,
        parentPageTitle = payload.parentPageTitle,
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
        moveDirection = payload.moveDirection,
    )
    AiActionDomain.Property -> CylAiAction.Property(
        type = payload.type,
        targetTitle = payload.targetTitle,
        title = payload.title,
        propertyName = payload.propertyName,
        newPropertyName = payload.newPropertyName,
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
        viewId = payload.viewId,
        viewName = payload.viewName,
        newViewName = payload.newViewName,
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
        filterOperator = payload.filterOperator,
        groupByColumnId = payload.groupByColumnId,
        groupByColumnName = payload.groupByColumnName,
        sourcePageId = payload.sourcePageId,
        sourcePageTitle = payload.sourcePageTitle,
        sourceTableBlockId = payload.sourceTableBlockId,
        sourceTableTitle = payload.sourceTableTitle,
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
        optionId = payload.optionId,
        optionName = payload.optionName,
        newOptionName = payload.newOptionName,
        optionColor = payload.optionColor,
        dateFormat = payload.dateFormat,
        timeFormat = payload.timeFormat,
        dateReminder = payload.dateReminder,
        timezoneLabel = payload.timezoneLabel,
        isHidden = payload.isHidden,
        isRequired = payload.isRequired,
        wrapContent = payload.wrapContent,
        widthDp = payload.widthDp,
        defaultValue = payload.defaultValue,
        clearDefaultValue = payload.clearDefaultValue,
        description = payload.description,
        clearDescription = payload.clearDescription,
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
        rowIds = payload.rowIds,
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
        rowIds = payload.rowIds,
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
        rowIds = payload.rowIds,
        rowTitle = payload.rowTitle,
        columnId = payload.columnId,
        columnName = payload.columnName,
        propertyName = payload.propertyName,
        value = payload.value,
        content = payload.content,
        cellValues = payload.cellValues,
        filterQuery = payload.filterQuery,
        relationRowIds = payload.relationRowIds,
        mediaId = payload.mediaId,
        mediaUri = payload.mediaUri,
        mediaName = payload.mediaName,
        mediaMimeType = payload.mediaMimeType,
        mediaSizeBytes = payload.mediaSizeBytes,
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
        columnId = payload.columnId,
        columnName = payload.columnName,
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

    fun validateChoice(field: String, value: String, allowedValues: Set<String>) {
        if (value.isBlank() || value.normalizedContractValue() in allowedValues) return
        add(
            invalidField(
                actionIndex = actionIndex,
                field = field,
                message = "Unsupported $field value: $value.",
            ),
        )
    }

    validateChoice("columnType", columnType, SupportedTableColumnTypeKeys)
    validateChoice("tableView", tableView, SupportedTableViewKeys)
    validateChoice("sortDirection", sortDirection, SupportedSortDirectionKeys)
    validateChoice("filterOperator", filterOperator, SupportedFilterOperatorKeys)
    validateChoice("dateFormat", dateFormat, SupportedDateFormatKeys)
    validateChoice("timeFormat", timeFormat, SupportedTimeFormatKeys)
    validateChoice("dateReminder", dateReminder, SupportedDateReminderKeys)
    validateChoice("optionColor", optionColor, SupportedOptionColorKeys)
    validateChoice("rollupAggregation", rollupAggregation, SupportedRollupAggregationKeys)

    if (widthDp != null && widthDp != 0 && widthDp !in 72..360) {
        add(
            invalidField(
                actionIndex = actionIndex,
                field = "widthDp",
                message = "Column widthDp must be 0 (automatic) or between 72 and 360.",
            ),
        )
    }
    if (mediaSizeBytes < 0) {
        add(
            invalidField(
                actionIndex = actionIndex,
                field = "mediaSizeBytes",
                message = "Media size cannot be negative.",
            ),
        )
    }
    tableColumns.forEachIndexed { columnIndex, column ->
        addAll(
            validateTableColumnWire(
                actionIndex = actionIndex,
                columnIndex = columnIndex,
                column = column,
            ),
        )
    }
    val duplicateColumnName = tableColumns
        .map { column -> column.name.trim() }
        .filter(String::isNotBlank)
        .groupBy { name -> name.lowercase() }
        .entries
        .firstOrNull { (_, names) -> names.size > 1 }
        ?.value
        ?.firstOrNull()
    if (duplicateColumnName != null) {
        add(
            invalidField(
                actionIndex = actionIndex,
                field = "tableColumns",
                message = "Duplicate table column: $duplicateColumnName.",
            ),
        )
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

        "MOVE_PAGE" -> {
            if (parentPageId.isBlank() && parentPageTitle.isBlank()) {
                // A blank parent explicitly means move the page to workspace root.
            }
        }

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

        "MOVE_BLOCK", "INDENT_BLOCK", "OUTDENT_BLOCK", "DUPLICATE_BLOCK" -> {
            requireAny("blockId", "$type needs blockId, blockText, content, or title.", blockId, blockText, content, title)
            if (type == "MOVE_BLOCK" && targetIndex == null && moveDirection.isBlank()) {
                add(missingField(actionIndex, "targetIndex|moveDirection", "Move block needs targetIndex or moveDirection."))
            }
        }

        "FORMAT_BLOCK_TEXT" -> {
            requireAny("blockId", "Format text action needs blockId, blockText, content, or title.", blockId, blockText, content, title)
            if (textToFormat.isBlank() && value.isBlank() && content.isBlank() && rangeStart == null && rangeEnd == null) {
                add(missingField(actionIndex, "textToFormat", "Format text action needs textToFormat/value/content or rangeStart/rangeEnd."))
            }
            requireAny("format", "Format text action needs format, linkUrl, color, or highlight.", format, linkUrl, color, highlight)
        }

        "ADD_PROPERTY", "DELETE_PROPERTY" ->
            requireAny("propertyName", "Property action needs propertyName or title.", propertyName, title)

        "UPDATE_PROPERTY" -> {
            requireAny("propertyName", "Property action needs propertyName or title.", propertyName, title)
            requireAny(
                "value",
                "Update property needs propertyType, value, or content.",
                propertyType,
                value,
                content,
            )
        }

        "RENAME_PROPERTY" -> {
            requireAny("propertyName", "Rename property needs propertyName or title.", propertyName, title)
            requireAny("newPropertyName", "Rename property needs newPropertyName, value, or content.", newPropertyName, value, content)
        }

        "MOVE_PROPERTY" -> {
            requireAny("propertyName", "Move property needs propertyName or title.", propertyName, title)
            if (targetIndex == null) add(missingField(actionIndex, "targetIndex", "Move property needs targetIndex."))
        }

        "DUPLICATE_PROPERTY" ->
            requireAny("propertyName", "Duplicate property needs propertyName or title.", propertyName, title)

        "CREATE_DATABASE", "CREATE_TABLE" ->
            requireAny("tableTitle", "Create table action needs tableTitle, title, or content.", tableTitle, title, content)

        "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" ->
            requireAny("title", "Rename table action needs a new title.", title, value, content, newColumnName)

        "ATTACH_TABLE_DATA_SOURCE" ->
            requireAny(
                "sourcePageId",
                "Attach data source needs sourcePageId or sourcePageTitle.",
                sourcePageId,
                sourcePageTitle,
            )

        "CREATE_TABLE_SAVED_VIEW" -> {
            requireAny("viewName", "Create saved view action needs viewName, title, value, or content.", viewName, title, value, content)
            val hasFilter = filterQuery.isNotBlank() || filterOperator.isNotBlank()
            val hasSort = sortDirection.isNotBlank()
            if ((hasFilter || hasSort) &&
                columnId.isBlank() &&
                columnName.isBlank() &&
                propertyName.isBlank()
            ) {
                add(
                    missingField(
                        actionIndex,
                        "columnName",
                        "Saved view sort/filter configuration needs a target column.",
                    ),
                )
            }
            if (hasFilter &&
                filterOperator.normalizedContractValue() !in QuerylessFilterOperatorKeys &&
                filterQuery.isBlank()
            ) {
                add(
                    missingField(
                        actionIndex,
                        "filterQuery",
                        "Saved view filter needs filterQuery unless the operator is IsEmpty or IsNotEmpty.",
                    ),
                )
            }
        }

        "RENAME_TABLE_SAVED_VIEW" -> {
            requireAny("viewId", "Rename saved view action needs viewId or viewName.", viewId, viewName)
            requireAny("newViewName", "Rename saved view action needs newViewName, value, or content.", newViewName, value, content)
        }

        "DELETE_TABLE_SAVED_VIEW", "ACTIVATE_TABLE_SAVED_VIEW" ->
            requireAny("viewId", "$type needs viewId or viewName.", viewId, viewName)

        "ADD_TABLE_COLUMN",
        "DELETE_TABLE_COLUMN",
        "RENAME_TABLE_COLUMN",
        "UPDATE_TABLE_COLUMN",
        "UPDATE_TABLE_COLUMN_TYPE",
        "CHANGE_TABLE_COLUMN_TYPE",
        "SET_TABLE_COLUMN_TYPE",
        "UPDATE_TABLE_COLUMN_CONFIG",
        "SET_TABLE_COLUMN_CONFIG",
        "UPDATE_TABLE_DATE_CONFIG",
        "UPDATE_FORMULA_COLUMN",
        "UPDATE_RELATION_COLUMN",
        "UPDATE_ROLLUP_COLUMN",
        "ADD_TABLE_COLUMN_OPTION",
        "UPDATE_TABLE_COLUMN_OPTION",
        "DELETE_TABLE_COLUMN_OPTION",
        "REORDER_TABLE_COLUMN",
        "MOVE_TABLE_COLUMN",
        "DUPLICATE_TABLE_COLUMN",
        -> {
            requireAny("columnName", "Column action needs columnId, columnName, propertyName, or title.", columnId, columnName, propertyName, title)
            if (type in setOf("RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN")) {
                requireAny("newColumnName", "Rename column action needs newColumnName, value, or content.", newColumnName, value, content)
            }
            if (type in setOf(
                    "UPDATE_TABLE_COLUMN_TYPE",
                    "CHANGE_TABLE_COLUMN_TYPE",
                    "SET_TABLE_COLUMN_TYPE",
                )
            ) {
                requireAny(
                    "columnType",
                    "Column type action needs columnType, propertyType, value, or content.",
                    columnType,
                    propertyType,
                    value,
                    content,
                )
            }
            if (type in setOf("REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN") && targetIndex == null) {
                add(missingField(actionIndex, "targetIndex", "Move column action needs targetIndex."))
            }
            if (type == "UPDATE_FORMULA_COLUMN") {
                requireAny("formula", "Formula column action needs formula, value, or content.", formula, value, content)
            }
            if (type == "UPDATE_RELATION_COLUMN") {
                requireAny(
                    "relationTargetTableId",
                    "Relation column action needs relationTargetTableId or relationTargetTableTitle.",
                    relationTargetTableId,
                    relationTargetTableTitle,
                )
            }
            if (type == "UPDATE_ROLLUP_COLUMN" &&
                rollupRelationColumnId.isBlank() &&
                rollupRelationColumnName.isBlank() &&
                rollupTargetColumnId.isBlank() &&
                rollupTargetColumnName.isBlank() &&
                rollupAggregation.isBlank()
            ) {
                add(
                    missingField(
                        actionIndex,
                        "rollupConfig",
                        "Rollup column action needs a relation column, target column, or aggregation.",
                    ),
                )
            }
            if (type == "UPDATE_TABLE_DATE_CONFIG" &&
                dateFormat.isBlank() &&
                timeFormat.isBlank() &&
                dateReminder.isBlank() &&
                timezoneLabel.isBlank()
            ) {
                add(
                    missingField(
                        actionIndex,
                        "dateFormat|timeFormat|dateReminder|timezoneLabel",
                        "Date config action needs at least one date configuration value.",
                    ),
                )
            }
            if (type in setOf("UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG") &&
                options.isEmpty() &&
                isHidden == null &&
                isRequired == null &&
                wrapContent == null &&
                widthDp == null &&
                defaultValue.isBlank() &&
                clearDefaultValue != true &&
                description.isBlank() &&
                clearDescription != true
            ) {
                add(
                    missingField(
                        actionIndex,
                        "columnConfig",
                        "Column config action needs at least one setting to update.",
                    ),
                )
            }
            if (type == "ADD_TABLE_COLUMN_OPTION") {
                requireAny("optionName", "Add option action needs optionName, value, or content.", optionName, value, content)
            }
            if (type == "UPDATE_TABLE_COLUMN_OPTION") {
                requireAny("optionId", "Update option action needs optionId or optionName.", optionId, optionName)
                if (newOptionName.isBlank() && optionColor.isBlank()) {
                    add(
                        missingField(
                            actionIndex,
                            "newOptionName|optionColor",
                            "Update option action needs a new name or color.",
                        ),
                    )
                }
            }
            if (type == "DELETE_TABLE_COLUMN_OPTION") {
                requireAny("optionId", "Delete option action needs optionId or optionName.", optionId, optionName)
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

        "DUPLICATE_TABLE_ROW" ->
            requireAny("rowTitle", "Duplicate row needs rowId, rowTitle, or title.", rowId, rowTitle, title)

        "DELETE_TABLE_ROWS", "UPDATE_TABLE_ROWS" -> {
            val hasExplicitRows = rowIds.any(String::isNotBlank)
            val hasCondition = filterQuery.isNotBlank() &&
                listOf(columnId, columnName, propertyName).any(String::isNotBlank)
            if (!hasExplicitRows && !hasCondition) {
                add(
                    missingField(
                        actionIndex,
                        "rowIds|column+filterQuery",
                        "$type needs explicit rowIds or a column and filterQuery condition.",
                    ),
                )
            }
            if (type == "UPDATE_TABLE_ROWS" && cellValues.isEmpty()) {
                add(missingField(actionIndex, "cellValues", "Bulk row update needs cellValues."))
            }
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

        "UPDATE_TABLE_CELL",
        "CLEAR_TABLE_CELL",
        "CLEAR_TABLE_CELLS",
        "SET_RELATION_CELL",
        "CLEAR_RELATION_CELL",
        "ADD_MEDIA_CELL",
        "REMOVE_MEDIA_CELL",
        "CLEAR_MEDIA_CELL",
        -> {
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
            if (type == "SET_RELATION_CELL" && relationRowIds.none(String::isNotBlank)) {
                add(missingField(actionIndex, "relationRowIds", "Relation cell action needs relationRowIds."))
            }
            if (type == "ADD_MEDIA_CELL") {
                requireAny("mediaUri", "Add media cell action needs mediaUri.", mediaUri)
            }
            if (type == "REMOVE_MEDIA_CELL") {
                requireAny("mediaId", "Remove media cell action needs mediaId, mediaUri, or mediaName.", mediaId, mediaUri, mediaName)
            }
        }

        "SORT_TABLE", "SET_TABLE_SORT",
        "FILTER_TABLE", "SET_TABLE_FILTER",
        "GROUP_TABLE", "SET_TABLE_GROUP",
        -> {
            requireAny(
                "columnName",
                "Table rule action needs a target column.",
                columnId,
                columnName,
                propertyName,
                title,
                groupByColumnId,
                groupByColumnName,
            )
            if (type in setOf("FILTER_TABLE", "SET_TABLE_FILTER") &&
                filterOperator.normalizedContractValue() !in QuerylessFilterOperatorKeys &&
                filterQuery.isBlank() &&
                value.isBlank() &&
                content.isBlank()
            ) {
                add(
                    missingField(
                        actionIndex,
                        "filterQuery",
                        "Filter action needs filterQuery unless the operator is IsEmpty or IsNotEmpty.",
                    ),
                )
            }
        }

        "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG" -> {
            val hasGenericViewColumn = tableView.normalizedContractValue() in setOf(
                "calendar",
                "timeline",
                "dashboard",
                "chart",
                "charts",
            ) && listOf(columnId, columnName, propertyName).any(String::isNotBlank)
            if (!hasGenericViewColumn &&
                calendarDateColumnId.isBlank() &&
                calendarDateColumnName.isBlank() &&
                timelineStartColumnId.isBlank() &&
                timelineStartColumnName.isBlank() &&
                timelineEndColumnId.isBlank() &&
                timelineEndColumnName.isBlank() &&
                dashboardMetricColumnId.isBlank() &&
                dashboardMetricColumnName.isBlank() &&
                dashboardGroupColumnId.isBlank() &&
                dashboardGroupColumnName.isBlank()
            ) {
                add(
                    missingField(
                        actionIndex,
                        "viewConfig",
                        "View config action needs at least one view column setting.",
                    ),
                )
            }
        }

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

        "CREATE_REMINDER", "RESCHEDULE_REMINDER" -> {
            requireAny(
                "title",
                "$type needs title, rowTitle, content, value, or a task cell value.",
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
                        message = "$type needs a positive delayMinutes or a date/time value in cellValues.",
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

        "CANCEL_REMINDER", "COMPLETE_REMINDER" ->
            requireAny("rowTitle", "$type needs rowId, rowTitle, or title.", rowId, rowTitle, title)
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

private fun invalidField(actionIndex: Int?, field: String, message: String): AiActionContractIssue =
    AiActionContractIssue(
        actionIndex = actionIndex,
        field = field,
        code = "invalid_field_value",
        message = message,
    )

private fun validateTableColumnWire(
    actionIndex: Int?,
    columnIndex: Int,
    column: AiTableColumnWire,
): List<AiActionContractIssue> = buildList {
    val fieldPrefix = "tableColumns[$columnIndex]"
    val normalizedType = column.type.normalizedContractValue()

    if (column.name.isBlank()) {
        add(missingField(actionIndex, "$fieldPrefix.name", "Table column name is required."))
    }
    if (normalizedType !in SupportedTableColumnTypeKeys) {
        add(
            invalidField(
                actionIndex,
                "$fieldPrefix.type",
                "Unsupported table column type: ${column.type}.",
            ),
        )
    }
    if (column.widthDp != null && column.widthDp != 0 && column.widthDp !in 72..360) {
        add(
            invalidField(
                actionIndex,
                "$fieldPrefix.widthDp",
                "Column widthDp must be 0 (automatic) or between 72 and 360.",
            ),
        )
    }

    fun validateChoice(field: String, value: String, allowedValues: Set<String>) {
        if (value.isBlank() || value.normalizedContractValue() in allowedValues) return
        add(
            invalidField(
                actionIndex,
                "$fieldPrefix.$field",
                "Unsupported $field value: $value.",
            ),
        )
    }

    validateChoice("dateFormat", column.dateFormat, SupportedDateFormatKeys)
    validateChoice("timeFormat", column.timeFormat, SupportedTimeFormatKeys)
    validateChoice("dateReminder", column.dateReminder, SupportedDateReminderKeys)
    validateChoice("rollupAggregation", column.rollupAggregation, SupportedRollupAggregationKeys)

    val duplicateOption = column.options
        .map(String::trim)
        .filter(String::isNotBlank)
        .groupBy { option -> option.lowercase() }
        .entries
        .firstOrNull { (_, options) -> options.size > 1 }
        ?.value
        ?.firstOrNull()
    if (duplicateOption != null) {
        add(
            invalidField(
                actionIndex,
                "$fieldPrefix.options",
                "Duplicate table option: $duplicateOption.",
            ),
        )
    }
    if (column.options.isNotEmpty() && normalizedType !in SelectColumnTypeKeys) {
        add(
            invalidField(
                actionIndex,
                "$fieldPrefix.options",
                "Options are only valid for Select, MultiSelect, or Status columns.",
            ),
        )
    }
    if (
        listOf(column.dateFormat, column.timeFormat, column.dateReminder, column.timezoneLabel)
            .any(String::isNotBlank) &&
        normalizedType != "date"
    ) {
        add(
            invalidField(
                actionIndex,
                "$fieldPrefix.dateFormat",
                "Date configuration is only valid for Date columns.",
            ),
        )
    }
}

private fun String.normalizedContractValue(): String =
    trim().lowercase().replace(Regex("[^a-z0-9]+"), "")

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
    if (mediaId.isNotBlank()) add("mediaId")
    if (isChecked != null) add("isChecked")
    if (propertyName.isNotBlank()) add("propertyName")
    if (newPropertyName.isNotBlank()) add("newPropertyName")
    if (propertyType.isNotBlank()) add("propertyType")
    if (value.isNotBlank()) add("value")
    if (moveDirection.isNotBlank()) add("moveDirection")
    if (parentPageId.isNotBlank()) add("parentPageId")
    if (parentPageTitle.isNotBlank()) add("parentPageTitle")
    if (sourcePageId.isNotBlank()) add("sourcePageId")
    if (sourcePageTitle.isNotBlank()) add("sourcePageTitle")
    if (sourceTableBlockId.isNotBlank()) add("sourceTableBlockId")
    if (sourceTableTitle.isNotBlank()) add("sourceTableTitle")
    if (moduleType.isNotBlank()) add("moduleType")
    if (tableTitle.isNotBlank()) add("tableTitle")
    if (tableView.isNotBlank() && tableView != "Table") add("tableView")
    if (viewId.isNotBlank()) add("viewId")
    if (viewName.isNotBlank()) add("viewName")
    if (newViewName.isNotBlank()) add("newViewName")
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
    if (columnType.isNotBlank()) add("columnType")
    if (options.isNotEmpty()) add("options")
    if (optionId.isNotBlank()) add("optionId")
    if (optionName.isNotBlank()) add("optionName")
    if (newOptionName.isNotBlank()) add("newOptionName")
    if (optionColor.isNotBlank()) add("optionColor")
    if (dateFormat.isNotBlank()) add("dateFormat")
    if (timeFormat.isNotBlank()) add("timeFormat")
    if (dateReminder.isNotBlank()) add("dateReminder")
    if (timezoneLabel.isNotBlank()) add("timezoneLabel")
    if (isHidden != null) add("isHidden")
    if (isRequired != null) add("isRequired")
    if (wrapContent != null) add("wrapContent")
    if (widthDp != null) add("widthDp")
    if (defaultValue.isNotBlank()) add("defaultValue")
    if (clearDefaultValue != null) add("clearDefaultValue")
    if (description.isNotBlank()) add("description")
    if (clearDescription != null) add("clearDescription")
    if (formula.isNotBlank()) add("formula")
    if (relationTargetTableId.isNotBlank()) add("relationTargetTableId")
    if (relationTargetTableTitle.isNotBlank()) add("relationTargetTableTitle")
    if (rollupRelationColumnId.isNotBlank()) add("rollupRelationColumnId")
    if (rollupRelationColumnName.isNotBlank()) add("rollupRelationColumnName")
    if (rollupTargetColumnId.isNotBlank()) add("rollupTargetColumnId")
    if (rollupTargetColumnName.isNotBlank()) add("rollupTargetColumnName")
    if (rollupAggregation.isNotBlank()) add("rollupAggregation")
    if (sortDirection.isNotBlank()) add("sortDirection")
    if (filterQuery.isNotBlank()) add("filterQuery")
    if (filterOperator.isNotBlank()) add("filterOperator")
    if (groupByColumnId.isNotBlank()) add("groupByColumnId")
    if (groupByColumnName.isNotBlank()) add("groupByColumnName")
    if (rowId.isNotBlank()) add("rowId")
    if (rowIds.isNotEmpty()) add("rowIds")
    if (rowTitle.isNotBlank()) add("rowTitle")
    if (newRowTitle.isNotBlank()) add("newRowTitle")
    if (rowBlockId.isNotBlank()) add("rowBlockId")
    if (targetIndex != null) add("targetIndex")
    if (cellValues.isNotEmpty()) add("cellValues")
    if (relationRowIds.isNotEmpty()) add("relationRowIds")
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
    "optionId",
    "optionName",
    "newOptionName",
    "optionColor",
    "dateFormat",
    "timeFormat",
    "dateReminder",
    "timezoneLabel",
    "isHidden",
    "isRequired",
    "wrapContent",
    "widthDp",
    "defaultValue",
    "clearDefaultValue",
    "description",
    "clearDescription",
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
private val SupportedTableColumnTypeKeys = setOf(
    "text",
    "number",
    "select",
    "multiselect",
    "status",
    "date",
    "filesmedia",
    "checkbox",
    "formula",
    "relation",
    "rollup",
)
private val SelectColumnTypeKeys = setOf("select", "multiselect", "status")
private val SupportedTableViewKeys = setOf(
    "table",
    "list",
    "board",
    "kanban",
    "calendar",
    "gallery",
    "timeline",
    "dashboard",
    "chart",
    "charts",
)
private val SupportedSortDirectionKeys = setOf(
    "ascending",
    "asc",
    "atoz",
    "oldest",
    "lowest",
    "smallest",
    "up",
    "descending",
    "desc",
    "ztoa",
    "newest",
    "latest",
    "highest",
    "largest",
    "down",
)
private val SupportedFilterOperatorKeys = setOf(
    "contains",
    "contain",
    "includes",
    "notcontains",
    "doesnotcontain",
    "excludes",
    "equals",
    "equal",
    "is",
    "eq",
    "notequals",
    "isnot",
    "neq",
    "isempty",
    "empty",
    "blank",
    "isnotempty",
    "notempty",
    "notblank",
    "greaterthan",
    "greater",
    "morethan",
    "above",
    "greaterthanorequal",
    "greaterthanorequals",
    "atleast",
    "gte",
    "lessthan",
    "less",
    "below",
    "lessthanorequal",
    "lessthanorequals",
    "atmost",
    "lte",
    "before",
    "after",
    "onorbefore",
    "beforeorequal",
    "onorafter",
    "afterorequal",
)
private val QuerylessFilterOperatorKeys = setOf(
    "isempty",
    "empty",
    "blank",
    "isnotempty",
    "notempty",
    "notblank",
)
private val SupportedDateFormatKeys = setOf(
    "daymonthyear",
    "ddmmyyyy",
    "monthdayyear",
    "mmddyyyy",
    "yearmonthday",
    "yyyymmdd",
    "iso",
)
private val SupportedTimeFormatKeys = setOf(
    "hidden",
    "none",
    "off",
    "twelvehour",
    "12hour",
    "twentyfourhour",
    "24hour",
)
private val SupportedDateReminderKeys = setOf(
    "none",
    "off",
    "attimeofevent",
    "attime",
    "eventtime",
    "fiveminutesbefore",
    "5minutesbefore",
    "5minbefore",
    "tenminutesbefore",
    "10minutesbefore",
    "10minbefore",
    "fifteenminutesbefore",
    "15minutesbefore",
    "15minbefore",
    "thirtyminutesbefore",
    "30minutesbefore",
    "30minbefore",
    "onehourbefore",
    "1hourbefore",
    "twohoursbefore",
    "2hoursbefore",
    "ondayofevent",
    "sameday",
    "eventday",
    "onedaybefore",
    "1daybefore",
    "twodaysbefore",
    "2daysbefore",
    "oneweekbefore",
    "1weekbefore",
)
private val SupportedOptionColorKeys = setOf(
    "gray",
    "red",
    "orange",
    "yellow",
    "green",
    "blue",
    "purple",
    "pink",
)
private val SupportedRollupAggregationKeys = setOf(
    "count",
    "sum",
    "total",
    "average",
    "avg",
    "mean",
    "min",
    "minimum",
    "lowest",
    "max",
    "maximum",
    "highest",
)
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
