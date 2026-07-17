package com.changeyourlife.cyl.data.remote.ai

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class AiImageInputDto(
    val dataUrl: String = "",
    val textContent: String = "",
    val mimeType: String = "",
    val name: String = "",
    val sizeBytes: Long = 0,
    val kind: String = "image",
)

@Serializable
data class ChatRequestDto(
    val messages: List<ChatMessageDto>,
    val images: List<AiImageInputDto> = emptyList(),
)

@Serializable
data class ChatResponseDto(
    val content: String
)

@Serializable
data class AiStatusResponseDto(
    val mode: String = "",
    val provider: String = "",
    val model: String = "",
    val apiKeyConfigured: Boolean = false,
    val visionPipelineVersion: String = "",
    val visionMaxImageDimension: Int = 0,
    val visionMaxImageBytes: Int = 0,
    val lmStudioVisionModels: String = "",
)

@Serializable
data class AiDiagnosticsDto(
    val phase: String = "",
    val imageCount: Int = 0,
    val textFileCount: Int = 0,
    val visionAttempted: Boolean = false,
    val visionProvider: String = "",
    val visionModel: String = "",
    val visionStatus: String = "",
    val visionPipelineVersion: String = "",
    val webSearchAttempted: Boolean = false,
    val webSearchProvider: String = "",
    val webSearchStatus: String = "",
    val webSearchResultCount: Int = 0,
    val warning: String = "",
)

// Chat-with-actions: Gemini JSON-mode endpoint for reliable action detection
@Serializable
data class ChatWithActionsRequestDto(
    val messages: List<ChatMessageDto>,
    val pages: List<AiPageContextDto> = emptyList(),
    val tasks: List<AiTaskContextDto> = emptyList(),
    val clientDate: String = "",
    val clientTimezone: String = "",
    val images: List<AiImageInputDto> = emptyList(),
    val webSearchEnabled: Boolean = false,
    val webSearchQuery: String = "",
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

@Serializable
data class AiActionDto(
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
    val diagnostics: AiDiagnosticsDto = AiDiagnosticsDto(),
)

@Serializable
data class AiChatActionsJobAcceptedResponseDto(
    val jobId: String = "",
    val status: String = "",
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
    val phase: String = "",
    val diagnostics: AiDiagnosticsDto = AiDiagnosticsDto(),
)

@Serializable
data class AiChatActionsJobStatusResponseDto(
    val jobId: String = "",
    val status: String = "",
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
    val result: ChatWithActionsResponseDto? = null,
    val error: String = "",
    val phase: String = "",
    val diagnostics: AiDiagnosticsDto = AiDiagnosticsDto(),
)
