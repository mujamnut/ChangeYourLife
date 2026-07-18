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
        val visibleValidationMessages = finalValidationIssues.toUserVisibleMessages(prompt)
        val needsClarification = proposedActions.isNotEmpty() &&
            appliedActionIndexes.isEmpty() &&
            finalValidationIssues.isNotEmpty() &&
            finalValidationIssues.all(ChatActionValidationMetadata::requiresClarification)
        val assistantReply = when {
            needsClarification -> ""

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
            when (issue.code.trim().lowercase(Locale.ROOT)) {
                "target_not_found" -> issue.field.targetNotFoundMessage(useMalay)
                "target_page_not_found" -> "targetTitle".targetNotFoundMessage(useMalay)
                "ambiguous_target", "target_page_ambiguous" ->
                    issue.field.ambiguousTargetMessage(useMalay)
                "target_page_required" ->
                    "targetTitle".missingRequiredMessage(useMalay)
                "unsupported_action_type" -> if (useMalay) {
                    "Tindakan itu belum disokong."
                } else {
                    "That action is not supported yet."
                }
                "required", "missing_required_action_fields", "missing_required_field" ->
                    issue.field.missingRequiredMessage(useMalay)
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

private fun ChatActionValidationMetadata.requiresClarification(): Boolean {
    return code.trim().lowercase(Locale.ROOT) in ClarificationIssueCodes
}

private fun String.missingRequiredMessage(useMalay: Boolean): String {
    return when {
        contains("row", ignoreCase = true) -> if (useMalay) {
            "Nyatakan row yang dimaksudkan, contohnya nama transaksi."
        } else {
            "Specify the row, for example its transaction name."
        }

        contains("column", ignoreCase = true) || contains("property", ignoreCase = true) -> if (useMalay) {
            "Nyatakan column atau property bagi cell itu."
        } else {
            "Specify the cell's column or property."
        }

        contains("value", ignoreCase = true) ||
            contains("content", ignoreCase = true) ||
            contains("cellValues", ignoreCase = true) -> if (useMalay) {
            "Nyatakan nilai baharu untuk cell itu."
        } else {
            "Specify the new value for that cell."
        }

        contains("filterQuery", ignoreCase = true) -> if (useMalay) {
            "Nyatakan nilai cell yang hendak dipadankan."
        } else {
            "Specify the cell value to match."
        }

        contains("table", ignoreCase = true) -> if (useMalay) {
            "Nyatakan database yang dimaksudkan."
        } else {
            "Specify the database."
        }

        contains("targetTitle", ignoreCase = true) ||
            contains("page", ignoreCase = true) -> if (useMalay) {
            "Nyatakan page yang hendak diubah."
        } else {
            "Specify the page to change."
        }

        else -> if (useMalay) {
            "Arahan itu belum mempunyai maklumat yang mencukupi."
        } else {
            "The request does not contain enough information."
        }
    }
}

private fun String.ambiguousTargetMessage(useMalay: Boolean): String {
    return when {
        contains("row", ignoreCase = true) -> if (useMalay) {
            "Lebih daripada satu row sepadan. Nyatakan nama transaksi atau row yang tepat."
        } else {
            "More than one row matched. Specify the exact transaction or row."
        }

        contains("table", ignoreCase = true) -> if (useMalay) {
            "Lebih daripada satu database sepadan. Nyatakan nama database yang tepat."
        } else {
            "More than one database matched. Specify the exact database."
        }

        contains("column", ignoreCase = true) || contains("property", ignoreCase = true) -> if (useMalay) {
            "Lebih daripada satu property sepadan. Nyatakan nama column yang tepat."
        } else {
            "More than one property matched. Specify the exact column."
        }

        contains("targetTitle", ignoreCase = true) ||
            contains("page", ignoreCase = true) -> if (useMalay) {
            "Lebih daripada satu page sepadan. Nyatakan nama page yang tepat."
        } else {
            "More than one page matched. Specify the exact page."
        }

        else -> if (useMalay) {
            "Lebih daripada satu sasaran sepadan. Nyatakan row, column, atau database dengan lebih khusus."
        } else {
            "More than one target matched. Specify the row, column, or database more precisely."
        }
    }
}

private fun String.targetNotFoundMessage(useMalay: Boolean): String {
    return when {
        contains("row", ignoreCase = true) -> if (useMalay) {
            "Row yang dimaksudkan tidak ditemui. Nyatakan nama transaksi atau row yang tepat."
        } else {
            "The row could not be found. Specify the exact transaction or row."
        }

        contains("table", ignoreCase = true) -> if (useMalay) {
            "Database yang dimaksudkan tidak ditemui. Nyatakan nama database yang tepat."
        } else {
            "The database could not be found. Specify the exact database."
        }

        contains("column", ignoreCase = true) || contains("property", ignoreCase = true) -> if (useMalay) {
            "Property yang dimaksudkan tidak ditemui. Nyatakan nama column yang tepat."
        } else {
            "The property could not be found. Specify the exact column."
        }

        contains("filterQuery", ignoreCase = true) -> if (useMalay) {
            "Tiada cell dengan nilai itu ditemui dalam column yang dipilih."
        } else {
            "No cell with that value was found in the selected column."
        }

        contains("block", ignoreCase = true) -> if (useMalay) {
            "Block yang dimaksudkan tidak ditemui."
        } else {
            "The block could not be found."
        }

        contains("targetTitle", ignoreCase = true) ||
            contains("page", ignoreCase = true) -> if (useMalay) {
            "Page yang dimaksudkan tidak ditemui. Nyatakan nama page yang tepat."
        } else {
            "The page could not be found. Specify the exact page."
        }

        else -> if (useMalay) {
            "Sasaran yang dimaksudkan tidak ditemui."
        } else {
            "The target could not be found."
        }
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

private val ClarificationIssueCodes = setOf(
    "ambiguous_target",
    "missing_required_action_fields",
    "missing_required_field",
    "required",
    "target_not_found",
    "target_page_ambiguous",
    "target_page_not_found",
    "target_page_required",
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
