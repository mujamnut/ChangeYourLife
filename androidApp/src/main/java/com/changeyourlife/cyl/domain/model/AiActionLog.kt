package com.changeyourlife.cyl.domain.model

data class AiActionLog(
    val auditId: String,
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
    val updatedAt: Long = createdAt,
)

object AiActionUndoState {
    const val NotAvailable = "NotAvailable"
    const val PendingCommandLink = "PendingCommandLink"
    const val Available = "Available"
    const val Applied = "Applied"
}
