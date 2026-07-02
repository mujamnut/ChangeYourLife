package com.changeyourlife.cyl.backend.domain

data class AiActionLogRecord(
    val auditId: String,
    val userId: String,
    val requestMessageId: String,
    val responseMessageId: String,
    val sessionId: String,
    val workspaceId: String,
    val mode: String,
    val provider: String,
    val model: String,
    val schemaName: String,
    val schemaVersion: Int,
    val proposedActionsJson: String,
    val executedActionsJson: String,
    val validationIssuesJson: String,
    val executionMessagesJson: String,
    val undoCommandsJson: String,
    val undoState: String,
    val createdAt: Long,
    val updatedAt: Long,
)

interface AiActionLogRepository {
    suspend fun listActionLogs(
        userId: String,
        workspaceId: String,
        updatedAfter: Long = 0L,
    ): List<AiActionLogRecord>

    suspend fun upsertActionLog(userId: String, actionLog: AiActionLogRecord): AiActionLogRecord?
}
