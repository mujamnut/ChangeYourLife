package com.changeyourlife.cyl.backend.model.ai

import com.changeyourlife.cyl.aicontract.AiActionWire
import com.changeyourlife.cyl.aicontract.AiAttachmentInputWire
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

typealias AiAttachmentInput = AiAttachmentInputWire

typealias AiImageInput = AiAttachmentInput

@Serializable
data class ChatRequest(
    val messages: List<ChatMessage>,
    val images: List<AiAttachmentInput> = emptyList(),
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
    val retrievalScope: AiRetrievalScope = AiRetrievalScope(),
    val pages: List<AiPageContext> = emptyList(),
    val tasks: List<AiTaskContext> = emptyList(),
    val clientDate: String = "",
    val clientTimezone: String = "",
    val images: List<AiAttachmentInput> = emptyList(),
    val webSearchEnabled: Boolean = false,
    val webSearchQuery: String = "",
)

@Serializable
data class AiRetrievalScope(
    val workspaceId: String = "",
    val mode: String = "Workspace",
    val currentPageId: String = "",
    val explicitPageIds: List<String> = emptyList(),
    val retrievedPageIds: List<String> = emptyList(),
    val includeTasks: Boolean = false,
)

@Serializable
data class AiPageContext(
    val id: String,
    val title: String,
    val workspaceId: String = "",
    val access: String = "Target",
    val blocks: List<AiBlockContext> = emptyList(),
    val totalBlockCount: Int = blocks.size,
    val isFocused: Boolean = false,
    val contextComplete: Boolean = true,
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
    val isChecked: Boolean? = null,
    val tableColumns: List<AiTableColumnContext> = emptyList(),
    val tableRows: List<AiTableRowContext> = emptyList(),
    val totalRowCount: Int = tableRows.size,
    val contextComplete: Boolean = true,
)

@Serializable
data class AiTableColumnContext(
    val id: String,
    val name: String,
    val type: String,
    val config: String = "",
)

@Serializable
data class AiTableRowContext(
    val id: String,
    val title: String = "",
    val cells: List<AiTableCellContext> = emptyList(),
    val totalBlockCount: Int = 0,
)

@Serializable
data class AiTableCellContext(
    val columnId: String,
    val columnName: String,
    val value: String = "",
)

@Serializable
data class AiTaskContext(
    val id: String,
    val title: String,
    val workspaceId: String = "",
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
