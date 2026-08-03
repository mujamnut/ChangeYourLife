package com.changeyourlife.cyl.data.remote.ai

import com.changeyourlife.cyl.aicontract.CYL_ACTION_SCHEMA_NAME
import com.changeyourlife.cyl.aicontract.CYL_ACTION_SCHEMA_VERSION
import com.changeyourlife.cyl.aicontract.AiAttachmentInputWire
import kotlinx.serialization.Serializable

typealias AiActionDto = com.changeyourlife.cyl.aicontract.AiActionWire
typealias AiTableColumnDto = com.changeyourlife.cyl.aicontract.AiTableColumnWire

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

typealias AiAttachmentInputDto = AiAttachmentInputWire

typealias AiImageInputDto = AiAttachmentInputDto

@Serializable
data class ChatRequestDto(
    val messages: List<ChatMessageDto>,
    val images: List<AiAttachmentInputDto> = emptyList(),
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
    val pdfFileCount: Int = 0,
    val pdfPageCount: Int = 0,
    val pdfExtractionStatus: String = "",
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
    val retrievalScope: AiRetrievalScopeDto = AiRetrievalScopeDto(),
    val pages: List<AiPageContextDto> = emptyList(),
    val tasks: List<AiTaskContextDto> = emptyList(),
    val clientDate: String = "",
    val clientTimezone: String = "",
    val images: List<AiAttachmentInputDto> = emptyList(),
    val webSearchEnabled: Boolean = false,
    val webSearchQuery: String = "",
)

@Serializable
data class AiRetrievalScopeDto(
    val workspaceId: String = "",
    val mode: String = "Workspace",
    val currentPageId: String = "",
    val explicitPageIds: List<String> = emptyList(),
    val retrievedPageIds: List<String> = emptyList(),
    val includeTasks: Boolean = false,
)

@Serializable
data class AiPageContextDto(
    val id: String,
    val title: String,
    val workspaceId: String = "",
    val access: String = "Metadata",
    val blocks: List<AiBlockContextDto> = emptyList(),
    val totalBlockCount: Int = blocks.size,
    val isFocused: Boolean = false,
    val contextComplete: Boolean = true,
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
    val isChecked: Boolean? = null,
    val tableColumns: List<AiTableColumnContextDto> = emptyList(),
    val tableRows: List<AiTableRowContextDto> = emptyList(),
    val totalRowCount: Int = tableRows.size,
    val contextComplete: Boolean = true,
)

@Serializable
data class AiTableColumnContextDto(
    val id: String,
    val name: String,
    val type: String,
    val config: String = "",
)

@Serializable
data class AiTableRowContextDto(
    val id: String,
    val title: String = "",
    val cells: List<AiTableCellContextDto> = emptyList(),
    val totalBlockCount: Int = 0,
)

@Serializable
data class AiTableCellContextDto(
    val columnId: String,
    val columnName: String,
    val value: String = "",
)

@Serializable
data class AiTaskContextDto(
    val id: String,
    val title: String,
    val workspaceId: String = "",
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
    val schemaName: String = CYL_ACTION_SCHEMA_NAME,
    val schemaVersion: Int = CYL_ACTION_SCHEMA_VERSION,
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
