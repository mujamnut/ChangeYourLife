package com.changeyourlife.cyl.backend.model.ai

import kotlinx.serialization.Serializable

const val CYL_ACTION_SCHEMA_NAME = "CYL_ACTION_SCHEMA"
const val CYL_ACTION_SCHEMA_VERSION = 1

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val messages: List<ChatMessage>
)

@Serializable
data class ChatResponse(
    val content: String
)

@Serializable
data class SummarizeRequest(
    val content: String
)

@Serializable
data class SummarizeResponse(
    val summary: String
)

@Serializable
data class GenerateTasksRequest(
    val content: String
)

@Serializable
data class GenerateTasksResponse(
    val tasks: List<String>
)

@Serializable
data class GeneratePlanRequest(
    val prompt: String,
    val type: String = "general"
)

@Serializable
data class GeneratePlanResponse(
    val planJson: String
)

@Serializable
data class AiStatusResponse(
    val mode: String,
    val provider: String,
    val model: String,
    val apiKeyConfigured: Boolean,
    val apiKeyLength: Int,
    val apiKeyInspect: String
)

@Serializable
data class ChatWithActionsRequest(
    val messages: List<ChatMessage>,
    val pages: List<AiPageContext> = emptyList(),
    val tasks: List<AiTaskContext> = emptyList(),
    val clientDate: String = "",
    val clientTimezone: String = "",
)

@Serializable
data class AiPageContext(
    val id: String,
    val title: String,
    val blocks: List<AiBlockContext> = emptyList()
)

@Serializable
data class AiBlockContext(
    val id: String,
    val type: String,
    val text: String = "",
    val path: String = "",
    val tableTitle: String = "",
    val tableBlockId: String = "",
    val rowId: String = "",
    val rowTitle: String = "",
    val rowBlockId: String = "",
    val isChecked: Boolean? = null
)

@Serializable
data class AiTaskContext(
    val id: String,
    val title: String
)

@Serializable
data class AiTableColumn(
    val name: String = "",
    val type: String = "Text",
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

@Serializable
data class AiAction(
    val type: String,
    val title: String,
    val targetTitle: String = "",
    val content: String = "",
    val blockType: String = "",
    val blockId: String = "",
    val blockText: String = "",
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
    val tableColumns: List<AiTableColumn> = emptyList(),
    val tableRows: List<Map<String, String>> = emptyList(),
    val delayMinutes: Long? = null
)

@Serializable
data class AiActionValidationIssue(
    val actionIndex: Int? = null,
    val field: String = "",
    val code: String,
    val message: String,
)

@Serializable
data class ChatWithActionsResponse(
    val reply: String,
    val actions: List<AiAction> = emptyList(),
    val schemaName: String = CYL_ACTION_SCHEMA_NAME,
    val schemaVersion: Int = CYL_ACTION_SCHEMA_VERSION,
    val validationIssues: List<AiActionValidationIssue> = emptyList(),
)
