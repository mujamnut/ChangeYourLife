package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_action_logs",
    indices = [
        Index(value = ["sessionId", "createdAt"]),
        Index(value = ["requestMessageId"]),
        Index(value = ["responseMessageId"]),
        Index(value = ["workspaceId", "createdAt"]),
        Index(value = ["workspaceId", "updatedAt"]),
        Index(value = ["syncStatus"]),
    ],
)
data class AiActionLogEntity(
    @PrimaryKey val auditId: String,
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
    val syncStatus: String = SyncStatus.PendingPush,
    val remoteUpdatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L,
)
