package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.ChatActionMetadata
import com.changeyourlife.cyl.domain.model.ChatActionMetadataItem
import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatActionResult
import java.util.UUID

object AiChatActionOrchestrator {
    suspend fun orchestrate(
        workspaceId: String,
        scopedTargetPage: Page?,
        prompt: String,
        backendResult: ChatActionResult,
        auditId: String = UUID.randomUUID().toString(),
        requestMessageId: String = "",
        executedAt: Long = System.currentTimeMillis(),
        provider: String = "",
        model: String = "",
        executeActions: suspend (
            workspaceId: String,
            scopedTargetPage: Page?,
            actions: List<AiActionExecutionCandidate>,
        ) -> AiActionExecutionResult,
    ): AiChatActionOrchestrationResult {
        val backendReply = backendResult.reply.ifBlank {
            "I received your message, but the AI returned an empty reply."
        }
        val recoveredMarkdownActions = if (backendResult.actions.isEmpty()) {
            AiMarkdownTableActionRecovery.recover(
                prompt = prompt,
                reply = backendResult.reply,
                targetPageTitle = scopedTargetPage?.title,
            )
        } else {
            emptyList()
        }
        val proposedActions = backendResult.actions.ifEmpty { recoveredMarkdownActions }
        val actionDecision = AiActionExecutionPolicy.decide(
            backendActions = proposedActions,
        )
        val actionsToExecute = actionDecision.executableCandidates
        val rejectedActionIndexes = (
            actionDecision.validationIssues +
                backendResult.validationIssues.map { issue ->
                    ChatActionValidationMetadata(
                        actionIndex = issue.actionIndex,
                        field = issue.field,
                        code = issue.code,
                        message = issue.message,
                    )
                }
            )
            .mapNotNull { issue -> issue.actionIndex }
            .toSet()
        val actionResults = if (actionsToExecute.isEmpty()) {
            AiActionExecutionResult()
        } else {
            executeActions(workspaceId, scopedTargetPage, actionsToExecute)
        }
        val assistantReply = when {
            recoveredMarkdownActions.isNotEmpty() ->
                "Siap - saya tukar jadual itu kepada data CYL."

            else -> backendReply
        }
        val replyWithResults = listOf(
            assistantReply,
            actionResults.messages.joinToString("\n"),
        )
            .filter { message -> message.isNotBlank() }
            .joinToString("\n\n")
        val appliedActionIndexes = actionResults.executedActionIndexes
            .asSequence()
            .distinct()
            .filter { index -> index in proposedActions.indices }
            .filterNot { index -> index in rejectedActionIndexes }
            .toList()

        return AiChatActionOrchestrationResult(
            reply = replyWithResults,
            pageLinks = actionResults.pageLinks,
            undoCommands = actionResults.undoCommands,
            actionMetadata = ChatActionMetadata(
                auditId = auditId,
                requestMessageId = requestMessageId,
                executedAt = executedAt,
                provider = provider,
                model = model,
                mode = "Smart",
                schemaName = backendResult.schemaName,
                schemaVersion = backendResult.schemaVersion,
                proposedActions = proposedActions.mapIndexed { index, action -> action.toMetadataItem(index) },
                executedActions = appliedActionIndexes
                    .map { index -> proposedActions[index].toMetadataItem(index) },
                executionMessages = actionResults.messages,
                validationIssues = backendResult.validationIssues.map { issue ->
                    ChatActionValidationMetadata(
                        actionIndex = issue.actionIndex,
                        field = issue.field,
                        code = issue.code,
                        message = issue.message,
                    )
                } + actionDecision.validationIssues + actionResults.validationIssues.filterNot { issue ->
                    issue.actionIndex != null && issue.actionIndex in rejectedActionIndexes
                },
            ),
        )
    }
}

data class AiChatActionOrchestrationResult(
    val reply: String,
    val pageLinks: List<AiChatPageLink>,
    val actionMetadata: ChatActionMetadata,
    val undoCommands: List<AiUndoCommandSummary> = emptyList(),
)

private fun ChatAction.toMetadataItem(actionIndex: Int): ChatActionMetadataItem {
    val target = listOf(
        targetTitle,
        title,
        tableTitle,
        rowTitle,
        newRowTitle,
        columnName,
        newColumnName,
        propertyName,
        blockText,
        content,
    )
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .distinct()
        .joinToString(" / ")
        .take(120)
    return ChatActionMetadataItem(
        type = type,
        target = target,
        actionIndex = actionIndex,
    )
}
