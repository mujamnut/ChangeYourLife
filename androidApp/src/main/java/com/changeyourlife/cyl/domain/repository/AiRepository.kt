package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.PageBlockDocument

interface AiRepository {
    suspend fun status(): Result<AiStatus>
    suspend fun chat(messages: List<Pair<String, String>>): Result<String>
    suspend fun chatWithActions(
        messages: List<Pair<String, String>>,
        pages: List<AiPageContext> = emptyList(),
        tasks: List<Pair<String, String>> = emptyList(),
        clientDate: String = "",
        clientTimezone: String = "",
    ): Result<ChatActionResult>
    suspend fun summarize(content: String): Result<String>
    suspend fun generateTasks(content: String): Result<List<String>>
    suspend fun generatePlan(prompt: String): Result<PageBlockDocument>
}

data class AiStatus(
    val mode: String = "",
    val provider: String = "",
    val model: String = "",
    val apiKeyConfigured: Boolean = false,
)

data class ChatActionResult(
    val reply: String,
    val actions: List<ChatAction> = emptyList(),
    val schemaName: String = "",
    val schemaVersion: Int = 1,
    val validationIssues: List<ChatActionValidationIssue> = emptyList(),
)

data class ChatActionValidationIssue(
    val actionIndex: Int? = null,
    val field: String = "",
    val code: String = "",
    val message: String = "",
)

data class AiPageContext(
    val id: String,
    val title: String,
    val blocks: List<AiBlockContext> = emptyList()
)

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

data class ChatTableColumn(
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

data class ChatAction(
    val type: String,
    val title: String,
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
    val tableColumns: List<ChatTableColumn> = emptyList(),
    val tableRows: List<Map<String, String>> = emptyList(),
    val delayMinutes: Long? = null
)
