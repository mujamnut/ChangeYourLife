package com.changeyourlife.cyl.presentation.ai

import androidx.compose.runtime.Immutable

@Immutable
data class AiChatMessage(
    val id: String = "",
    val role: String,
    val content: String,
    val pageLinks: List<AiChatPageLink> = emptyList(),
    val attachments: List<AiChatAttachment> = emptyList(),
    val actionMetadata: AiChatActionMetadata? = null,
)

@Immutable
data class AiChatAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val kind: String,
    val sizeBytes: Long,
    val previewDataUrl: String = "",
)

@Immutable
data class AiChatPageLink(
    val pageId: String,
    val title: String,
    val targetType: String = "",
    val targetId: String = "",
)

@Immutable
data class AiChatActionMetadata(
    val auditId: String = "",
    val requestMessageId: String = "",
    val undoState: String = "",
    val executedAt: Long = 0L,
    val provider: String = "",
    val model: String = "",
    val mode: String = "",
    val schemaName: String = "",
    val schemaVersion: Int = 1,
    val proposedActions: List<AiChatActionMetadataItem> = emptyList(),
    val executedActions: List<AiChatActionMetadataItem> = emptyList(),
    val executionMessages: List<String> = emptyList(),
    val validationIssues: List<AiChatActionValidationIssue> = emptyList(),
) {
    val hasDetails: Boolean
        get() = proposedActions.isNotEmpty() ||
            executedActions.isNotEmpty() ||
            executionMessages.isNotEmpty() ||
            validationIssues.isNotEmpty()
}

@Immutable
data class AiChatActionMetadataItem(
    val type: String,
    val target: String = "",
    val actionIndex: Int? = null,
)

@Immutable
data class AiChatActionValidationIssue(
    val actionIndex: Int? = null,
    val field: String = "",
    val code: String = "",
    val message: String = "",
)

fun List<AiChatMessage>.toRoleContentPairs(): List<Pair<String, String>> {
    return map { message -> message.role to message.content }
}
