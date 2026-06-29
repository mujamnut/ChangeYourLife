package com.changeyourlife.cyl.domain.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val pageLinks: List<ChatPageLink>,
    val actionMetadata: ChatActionMetadata? = null,
    val createdAt: Long,
)

data class ChatSession(
    val id: String,
    val scopeId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ChatPageLink(
    val pageId: String,
    val title: String,
    val targetType: String = "",
    val targetId: String = "",
)

data class ChatActionMetadata(
    val mode: String = "",
    val schemaName: String = "",
    val schemaVersion: Int = 1,
    val proposedActions: List<ChatActionMetadataItem> = emptyList(),
    val executedActions: List<ChatActionMetadataItem> = emptyList(),
    val executionMessages: List<String> = emptyList(),
    val validationIssues: List<ChatActionValidationMetadata> = emptyList(),
)

data class ChatActionMetadataItem(
    val type: String,
    val target: String = "",
)

data class ChatActionValidationMetadata(
    val actionIndex: Int? = null,
    val field: String = "",
    val code: String = "",
    val message: String = "",
)
