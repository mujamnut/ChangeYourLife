package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.AiActionLog
import com.changeyourlife.cyl.domain.model.AiActionUndoState
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.ChatActionMetadata
import com.changeyourlife.cyl.domain.model.ChatActionMetadataItem
import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AiActionLogFactory {
    private val json = Json { encodeDefaults = false }

    fun fromMetadata(
        sessionId: String,
        workspaceId: String,
        responseMessageId: String,
        metadata: ChatActionMetadata,
        undoCommands: List<AiUndoCommandSummary> = emptyList(),
    ): AiActionLog? {
        if (!metadata.hasLoggableDetails()) return null
        val createdAt = metadata.executedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        return AiActionLog(
            auditId = metadata.auditId.ifBlank { return null },
            requestMessageId = metadata.requestMessageId,
            responseMessageId = responseMessageId,
            sessionId = sessionId,
            workspaceId = workspaceId,
            mode = metadata.mode,
            provider = metadata.provider,
            model = metadata.model,
            schemaName = metadata.schemaName,
            schemaVersion = metadata.schemaVersion,
            proposedActionsJson = json.encodeToString(metadata.proposedActions.map { it.toDto() }),
            executedActionsJson = json.encodeToString(metadata.executedActions.map { it.toDto() }),
            validationIssuesJson = json.encodeToString(metadata.validationIssues.map { it.toDto() }),
            executionMessagesJson = json.encodeToString(metadata.executionMessages),
            undoCommandsJson = json.encodeToString(undoCommands),
            undoState = if (undoCommands.isNotEmpty()) {
                AiActionUndoState.Available
            } else if (metadata.executedActions.isNotEmpty()) {
                AiActionUndoState.PendingCommandLink
            } else {
                AiActionUndoState.NotAvailable
            },
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }
}

private fun ChatActionMetadata.hasLoggableDetails(): Boolean {
    return proposedActions.isNotEmpty() ||
        executedActions.isNotEmpty() ||
        validationIssues.isNotEmpty() ||
        executionMessages.isNotEmpty()
}

@Serializable
private data class ActionItemDto(
    val type: String = "",
    val target: String = "",
    val actionIndex: Int? = null,
)

@Serializable
private data class ValidationIssueDto(
    val actionIndex: Int? = null,
    val actionType: String = "",
    val actionDomain: String = "",
    val field: String = "",
    val code: String = "",
    val message: String = "",
)

private fun ChatActionMetadataItem.toDto(): ActionItemDto {
    return ActionItemDto(
        type = type,
        target = target,
        actionIndex = actionIndex,
    )
}

private fun ChatActionValidationMetadata.toDto(): ValidationIssueDto {
    return ValidationIssueDto(
        actionIndex = actionIndex,
        actionType = actionType,
        actionDomain = actionDomain,
        field = field,
        code = code,
        message = message,
    )
}
