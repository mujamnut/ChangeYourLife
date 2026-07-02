package com.changeyourlife.cyl.backend.domain

data class ChatSessionRecord(
    val id: String,
    val userId: String,
    val scopeId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

data class ChatMessageRecord(
    val id: String,
    val userId: String,
    val sessionId: String,
    val scopeId: String,
    val role: String,
    val content: String,
    val pageLinksJson: String,
    val actionMetadataJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

interface ChatSyncRepository {
    suspend fun listSessions(
        userId: String,
        scopeId: String,
        updatedAfter: Long = 0L,
    ): List<ChatSessionRecord>

    suspend fun upsertSession(userId: String, session: ChatSessionRecord): ChatSessionRecord?

    suspend fun listMessages(
        userId: String,
        sessionId: String,
        updatedAfter: Long = 0L,
    ): List<ChatMessageRecord>

    suspend fun upsertMessage(userId: String, message: ChatMessageRecord): ChatMessageRecord?
}
