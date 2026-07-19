package com.changeyourlife.cyl.backend.model.ai

import com.changeyourlife.cyl.aicontract.AiActionWire
import com.changeyourlife.cyl.aicontract.AiTableColumnWire
import kotlinx.serialization.Serializable

const val CYL_ACTION_SCHEMA_NAME = com.changeyourlife.cyl.aicontract.CYL_ACTION_SCHEMA_NAME
const val CYL_ACTION_SCHEMA_VERSION = com.changeyourlife.cyl.aicontract.CYL_ACTION_SCHEMA_VERSION

typealias AiAction = AiActionWire
typealias AiTableColumn = AiTableColumnWire

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class AiImageInput(
    val dataUrl: String = "",
    val textContent: String = "",
    val mimeType: String = "",
    val name: String = "",
    val sizeBytes: Long = 0,
    val kind: String = "image",
)

@Serializable
data class ChatRequest(
    val messages: List<ChatMessage>,
    val images: List<AiImageInput> = emptyList(),
)

@Serializable
data class ChatResponse(
    val content: String
)

@Serializable
data class AiStatusResponse(
    val mode: String,
    val provider: String,
    val model: String,
    val visionPipelineVersion: String = "",
    val visionMaxImageDimension: Int = 0,
    val visionMaxImageBytes: Int = 0,
    val lmStudioVisionModels: String = "",
)

@Serializable
data class AiDiagnostics(
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

@Serializable
data class ChatWithActionsRequest(
    val messages: List<ChatMessage>,
    val pages: List<AiPageContext> = emptyList(),
    val tasks: List<AiTaskContext> = emptyList(),
    val clientDate: String = "",
    val clientTimezone: String = "",
    val images: List<AiImageInput> = emptyList(),
    val webSearchEnabled: Boolean = false,
    val webSearchQuery: String = "",
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
    val diagnostics: AiDiagnostics = AiDiagnostics(),
)

@Serializable
data class AiChatActionsJobAcceptedResponse(
    val jobId: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val phase: String = "",
    val diagnostics: AiDiagnostics = AiDiagnostics(),
)

@Serializable
data class AiChatActionsJobStatusResponse(
    val jobId: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val result: ChatWithActionsResponse? = null,
    val error: String = "",
    val phase: String = "",
    val diagnostics: AiDiagnostics = AiDiagnostics(),
)
