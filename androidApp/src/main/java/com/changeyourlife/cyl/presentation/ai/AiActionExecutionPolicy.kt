package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.repository.ChatAction

object AiActionExecutionPolicy {
    fun decide(
        backendActions: List<ChatAction>,
        destructiveActionsConfirmed: Boolean = false,
    ): AiActionExecutionDecision {
        val validationIssues = mutableListOf<ChatActionValidationMetadata>()
        val confirmationIndexes = if (destructiveActionsConfirmed) {
            emptySet()
        } else {
            AiDestructiveActionPolicy.confirmationIndexes(backendActions)
        }
        val confirmationCandidates = mutableListOf<AiActionExecutionCandidate>()
        val executableCandidates = backendActions.mapIndexedNotNull { index, action ->
            when {
                action.type.isBlank() -> {
                    validationIssues += ChatActionValidationMetadata(
                        actionIndex = index,
                        field = "type",
                        code = "MISSING_ACTION_TYPE",
                        message = "Skipped action because the action type was empty.",
                    )
                    null
                }

                action.isUnsafeQualitativeRename() -> {
                    validationIssues += ChatActionValidationMetadata(
                        actionIndex = index,
                        field = "title",
                        code = "UNSAFE_QUALITATIVE_RENAME",
                        message = "Skipped rename because the requested name was only a vague descriptor.",
                    )
                    null
                }

                else -> {
                    val candidate = AiActionExecutionCandidate(
                        originalIndex = index,
                        action = action,
                    )
                    if (index in confirmationIndexes) {
                        confirmationCandidates += candidate
                        validationIssues += ChatActionValidationMetadata(
                            actionIndex = index,
                            actionType = action.type,
                            actionDomain = AiActionExecutionRegistry.domainFor(action.type).id,
                            field = "confirmation",
                            code = DestructiveConfirmationRequiredCode,
                            message = "Explicit user confirmation is required before destructive data removal.",
                        )
                    }
                    candidate
                }
            }
        }
        return AiActionExecutionDecision(
            executableCandidates = executableCandidates,
            validationIssues = validationIssues,
            confirmationCandidates = confirmationCandidates,
        )
    }
}

data class AiActionExecutionDecision(
    val executableCandidates: List<AiActionExecutionCandidate>,
    val validationIssues: List<ChatActionValidationMetadata> = emptyList(),
    val confirmationCandidates: List<AiActionExecutionCandidate> = emptyList(),
) {
    val executableActions: List<ChatAction> = executableCandidates.map { candidate -> candidate.action }
}

data class AiActionExecutionCandidate(
    val originalIndex: Int,
    val action: ChatAction,
)

private fun ChatAction.isUnsafeQualitativeRename(): Boolean {
    val normalizedType = type.trim().uppercase()
    if (normalizedType !in setOf("RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE")) return false
    val requestedTitle = title
        .ifBlank { value }
        .ifBlank { content }
        .ifBlank { newColumnName }
    return requestedTitle.isQualitativeTableTitleRequest()
}

private fun String.isQualitativeTableTitleRequest(): Boolean {
    val normalized = lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    if (normalized.isBlank()) return true
    val descriptorWords = setOf(
        "sesuai",
        "sensuai",
        "sesual",
        "sensual",
        "appropriate",
        "suitable",
        "better",
        "nice",
        "good",
        "bagus",
        "kemas",
        "cantik",
        "proper",
        "short",
        "simple",
        "ringkas",
        "pendek",
    )
    val connectorWords = setOf("dan", "and", "yang", "baru", "new")
    val meaningfulWords = normalized.split(Regex("\\s+"))
        .filterNot { word -> word in connectorWords }
    if (meaningfulWords.isEmpty()) return true
    return meaningfulWords.all { word -> word in descriptorWords }
}
