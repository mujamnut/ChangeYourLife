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
        mode: AiChatMode,
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
            actions: List<ChatAction>,
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
            mode = mode,
            backendActions = proposedActions,
        )
        val actionsToExecute = actionDecision.executableActions
        val skippedActionIndexes = actionDecision.validationIssues
            .mapNotNull { issue -> issue.actionIndex }
            .toSet()
        val actionResults = if (actionsToExecute.isEmpty()) {
            AiActionExecutionResult()
        } else {
            executeActions(workspaceId, scopedTargetPage, actionsToExecute)
        }
        val assistantReply = when {
            mode == AiChatMode.Planning && proposedActions.isNotEmpty() ->
                "Saya nampak arahan untuk ubah app, tapi mode sekarang Planning. Tukar ke Edit atau Auto untuk apply perubahan ini."

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
                mode = mode.name,
                schemaName = backendResult.schemaName,
                schemaVersion = backendResult.schemaVersion,
                proposedActions = proposedActions.mapIndexed { index, action -> action.toMetadataItem(index) },
                executedActions = if (mode == AiChatMode.Planning) {
                    emptyList()
                } else {
                    proposedActions.mapIndexedNotNull { index, action ->
                        action.takeIf { index !in skippedActionIndexes }?.toMetadataItem(index)
                    }
                },
                executionMessages = actionResults.messages,
                validationIssues = backendResult.validationIssues.map { issue ->
                    ChatActionValidationMetadata(
                        actionIndex = issue.actionIndex,
                        field = issue.field,
                        code = issue.code,
                        message = issue.message,
                    )
                } + actionDecision.validationIssues + actionResults.validationIssues,
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
