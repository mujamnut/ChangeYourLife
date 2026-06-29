package com.changeyourlife.cyl.presentation.ai

data class AiChatMessage(
    val id: String = "",
    val role: String,
    val content: String,
    val pageLinks: List<AiChatPageLink> = emptyList(),
    val actionMetadata: AiChatActionMetadata? = null,
)

data class AiChatPageLink(
    val pageId: String,
    val title: String,
    val targetType: String = "",
    val targetId: String = "",
)

data class AiChatActionMetadata(
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

data class AiChatActionMetadataItem(
    val type: String,
    val target: String = "",
)

data class AiChatActionValidationIssue(
    val actionIndex: Int? = null,
    val field: String = "",
    val code: String = "",
    val message: String = "",
)

fun List<AiChatMessage>.toRoleContentPairs(): List<Pair<String, String>> {
    return map { message -> message.role to message.content }
}
