package com.changeyourlife.cyl.domain.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val pageLinks: List<ChatPageLink>,
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
