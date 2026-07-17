package com.changeyourlife.cyl.domain.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val pageLinks: List<ChatPageLink>,
    val attachments: List<ChatMessageAttachment> = emptyList(),
    val actionMetadata: ChatActionMetadata? = null,
    val createdAt: Long,
)

data class ChatMessageAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val kind: String,
    val sizeBytes: Long,
    val dataUrl: String = "",
    val textContent: String = "",
    val previewDataUrl: String = "",
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
    val auditId: String = "",
    val requestMessageId: String = "",
    val executedAt: Long = 0L,
    val provider: String = "",
    val model: String = "",
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
    val actionIndex: Int? = null,
)

data class ChatActionValidationMetadata(
    val actionIndex: Int? = null,
    val actionType: String = "",
    val actionDomain: String = "",
    val field: String = "",
    val code: String = "",
    val message: String = "",
)
