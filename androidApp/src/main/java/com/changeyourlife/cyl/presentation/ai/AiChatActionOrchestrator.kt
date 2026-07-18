package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.ChatActionMetadata
import com.changeyourlife.cyl.domain.model.ChatActionMetadataItem
import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatActionResult
import com.changeyourlife.cyl.domain.repository.AiDiagnostics
import java.util.Locale
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
        val backendValidationIssues = backendResult.validationIssues.map { issue ->
            val trace = issue.actionIndex
                ?.let { index -> proposedActions.getOrNull(index)?.let { action -> AiActionExecutionRegistry.trace(index, action) } }
            ChatActionValidationMetadata(
                actionIndex = issue.actionIndex,
                actionType = trace?.actionType.orEmpty(),
                actionDomain = trace?.domain?.id.orEmpty(),
                field = issue.field,
                code = issue.code,
                message = issue.message,
            )
        }
        val backendRejectedActionIndexes = backendValidationIssues
            .mapNotNull { issue -> issue.actionIndex }
            .toSet()
        val actionDecision = AiActionExecutionPolicy.decide(
            backendActions = proposedActions,
        )
        val actionsToExecute = actionDecision.executableCandidates
            .filterNot { candidate -> candidate.originalIndex in backendRejectedActionIndexes }
        val rejectedActionIndexes = (
            actionDecision.validationIssues +
                backendValidationIssues
            )
            .mapNotNull { issue -> issue.actionIndex }
            .toSet()
        val actionResults = if (actionsToExecute.isEmpty()) {
            AiActionExecutionResult()
        } else {
            executeActions(workspaceId, scopedTargetPage, actionsToExecute)
        }
        val appliedActionIndexes = actionResults.executedActionIndexes
            .asSequence()
            .distinct()
            .filter { index -> index in proposedActions.indices }
            .filterNot { index -> index in rejectedActionIndexes }
            .toList()
        val finalValidationIssues = backendValidationIssues +
            actionDecision.validationIssues +
            actionResults.validationIssues.filterNot { issue ->
                issue.actionIndex != null && issue.actionIndex in rejectedActionIndexes
            }
        val assistantReply = when {
            proposedActions.isNotEmpty() && appliedActionIndexes.isEmpty() ->
                prompt.executionFailureSummary()

            proposedActions.isNotEmpty() && appliedActionIndexes.size < proposedActions.size ->
                prompt.partialExecutionSummary()

            recoveredMarkdownActions.isNotEmpty() ->
                "Siap - saya tukar jadual itu kepada data CYL."

            else -> backendReply
        }
        val diagnosticMessages = backendResult.diagnostics.toUserDiagnosticMessages()
        val visibleExecutionMessages = actionResults.messages.visibleInChat()
        val visibleValidationMessages = finalValidationIssues.toUserVisibleMessages(prompt)
        val replyWithResults = listOf(
            assistantReply,
            diagnosticMessages.joinToString("\n"),
            visibleValidationMessages.joinToString("\n"),
            visibleExecutionMessages.joinToString("\n"),
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
                mode = "Smart",
                schemaName = backendResult.schemaName,
                schemaVersion = backendResult.schemaVersion,
                proposedActions = proposedActions.mapIndexed { index, action -> action.toMetadataItem(index) },
                executedActions = appliedActionIndexes
                    .map { index -> proposedActions[index].toMetadataItem(index) },
                executionMessages = diagnosticMessages + actionResults.messages,
                validationIssues = finalValidationIssues,
            ),
        )
    }
}

private fun List<String>.visibleInChat(): List<String> {
    return filter { message ->
        val normalized = message.trimStart()
        !normalized.startsWith("Done:", ignoreCase = true) &&
            !normalized.startsWith("Failed ", ignoreCase = true) &&
            !normalized.startsWith("Rejected ", ignoreCase = true)
    }
}

private fun List<ChatActionValidationMetadata>.toUserVisibleMessages(
    prompt: String,
): List<String> {
    val useMalay = prompt.prefersMalayExecutionText()
    return asSequence()
        .distinctBy { issue -> "${issue.code}:${issue.field}:${issue.actionIndex}" }
        .map { issue ->
            when (issue.code) {
                "target_not_found" -> issue.field.targetNotFoundMessage(useMalay)
                "ambiguous_target" -> if (useMalay) {
                    "Lebih daripada satu sasaran sepadan. Nyatakan row atau nilai yang lebih khusus."
                } else {
                    "More than one target matched. Specify a more precise row or value."
                }
                "unsupported_action_type" -> if (useMalay) {
                    "Tindakan itu belum disokong."
                } else {
                    "That action is not supported yet."
                }
                "required" -> if (useMalay) {
                    "Arahan itu belum mempunyai maklumat yang mencukupi."
                } else {
                    "The request does not contain enough information."
                }
                else -> if (useMalay) {
                    "Perubahan itu tidak dapat disimpan."
                } else {
                    "The change could not be saved."
                }
            }
        }
        .distinct()
        .take(2)
        .toList()
}

private fun String.targetNotFoundMessage(useMalay: Boolean): String {
    val target = when {
        contains("row", ignoreCase = true) -> if (useMalay) "Row" else "Row"
        contains("table", ignoreCase = true) -> if (useMalay) "Database" else "Database"
        contains("column", ignoreCase = true) || contains("property", ignoreCase = true) ->
            if (useMalay) "Property" else "Property"
        contains("block", ignoreCase = true) -> if (useMalay) "Block" else "Block"
        contains("targetTitle", ignoreCase = true) -> if (useMalay) "Page" else "Page"
        else -> if (useMalay) "Sasaran" else "Target"
    }
    return if (useMalay) {
        "$target yang dimaksudkan tidak ditemui."
    } else {
        "$target could not be found."
    }
}

private fun String.executionFailureSummary(): String {
    return if (prefersMalayExecutionText()) {
        "Saya belum dapat membuat perubahan itu."
    } else {
        "I couldn't make that change."
    }
}

private fun String.partialExecutionSummary(): String {
    return if (prefersMalayExecutionText()) {
        "Sebahagian perubahan sudah dibuat."
    } else {
        "Some changes were completed."
    }
}

private fun String.prefersMalayExecutionText(): Boolean {
    val words = lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9]+"))
        .filter(String::isNotBlank)
        .toSet()
    return words.any { word -> word in MalayExecutionWords }
}

private val MalayExecutionWords = setOf(
    "awak",
    "baris",
    "buat",
    "buang",
    "bulan",
    "dalam",
    "dan",
    "hapus",
    "ini",
    "padam",
    "saya",
    "tambah",
    "tolong",
    "tukar",
    "ubah",
)

private fun AiDiagnostics.toUserDiagnosticMessages(): List<String> {
    if (!hasAttachmentContext) return emptyList()
    val attachmentParts = buildList {
        if (imageCount > 0) add("$imageCount image${if (imageCount == 1) "" else "s"}")
        if (textFileCount > 0) add("$textFileCount file${if (textFileCount == 1) "" else "s"}")
    }.joinToString(" + ")
    val visionPart = when {
        !visionAttempted -> ""
        visionStatus.equals("succeeded", ignoreCase = true) -> {
            val model = visionModel.ifBlank { visionProvider.ifBlank { "vision model" } }
            "Vision: read by $model"
        }
        visionStatus.isNotBlank() -> "Vision: $visionStatus"
        else -> "Vision: attempted"
    }
    return listOf(
        listOf(
            "Attachment: $attachmentParts".takeIf { attachmentParts.isNotBlank() },
            visionPart.takeIf { it.isNotBlank() },
        )
            .filterNotNull()
            .joinToString(" | "),
    ).filter { message -> message.isNotBlank() }
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
