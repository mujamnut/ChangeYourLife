package com.changeyourlife.cyl.data.remote.ai

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequestDto(
    val messages: List<ChatMessageDto>
)

@Serializable
data class ChatResponseDto(
    val content: String
)

@Serializable
data class SummarizeRequestDto(
    val content: String
)

@Serializable
data class SummarizeResponseDto(
    val summary: String
)

@Serializable
data class GenerateTasksRequestDto(
    val content: String
)

@Serializable
data class GenerateTasksResponseDto(
    val tasks: List<String>
)

@Serializable
data class GeneratePlanRequestDto(
    val prompt: String,
    val type: String = "general"
)

@Serializable
data class GeneratePlanResponseDto(
    val planJson: String
)

@Serializable
data class AiStatusResponseDto(
    val mode: String = "",
    val provider: String = "",
    val model: String = "",
    val apiKeyConfigured: Boolean = false,
)

// Chat-with-actions: Gemini JSON-mode endpoint for reliable action detection
@Serializable
data class ChatWithActionsRequestDto(
    val messages: List<ChatMessageDto>,
    val pages: List<AiPageContextDto> = emptyList(),
    val tasks: List<AiTaskContextDto> = emptyList(),
    val clientDate: String = "",
    val clientTimezone: String = "",
)

@Serializable
data class AiPageContextDto(
    val id: String,
    val title: String,
    val blocks: List<AiBlockContextDto> = emptyList()
)

@Serializable
data class AiBlockContextDto(
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
data class AiTaskContextDto(
    val id: String,
    val title: String
)

@Serializable
data class AiTableColumnDto(
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
data class AiActionDto(
    val type: String = "",
    val title: String = "",
    val targetTitle: String = "",
    val content: String = "",
    val blockType: String = "",
    val blockId: String = "",
    val blockText: String = "",
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
    val tableColumns: List<AiTableColumnDto> = emptyList(),
    val tableRows: List<Map<String, String>> = emptyList(),
    val delayMinutes: Long? = null
)

@Serializable
data class AiActionValidationIssueDto(
    val actionIndex: Int? = null,
    val field: String = "",
    val code: String = "",
    val message: String = "",
)

@Serializable
data class ChatWithActionsResponseDto(
    val reply: String,
    val actions: List<AiActionDto> = emptyList(),
    val schemaName: String = "",
    val schemaVersion: Int = 1,
    val validationIssues: List<AiActionValidationIssueDto> = emptyList(),
)
