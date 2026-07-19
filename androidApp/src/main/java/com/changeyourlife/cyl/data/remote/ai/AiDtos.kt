package com.changeyourlife.cyl.data.remote.ai

import com.changeyourlife.cyl.aicontract.CYL_ACTION_SCHEMA_NAME
import com.changeyourlife.cyl.aicontract.CYL_ACTION_SCHEMA_VERSION
import kotlinx.serialization.Serializable

typealias AiActionDto = com.changeyourlife.cyl.aicontract.AiActionWire
typealias AiTableColumnDto = com.changeyourlife.cyl.aicontract.AiTableColumnWire

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
