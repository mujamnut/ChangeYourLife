package com.changeyourlife.cyl.data.local.mapper

import com.changeyourlife.cyl.data.local.entity.AiActionLogEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import com.changeyourlife.cyl.domain.model.AiActionLog

fun AiActionLogEntity.toDomain(): AiActionLog {
    return AiActionLog(
        auditId = auditId,
        requestMessageId = requestMessageId,
        responseMessageId = responseMessageId,
        sessionId = sessionId,
        workspaceId = workspaceId,
        mode = mode,
        provider = provider,
        model = model,
        schemaName = schemaName,
        schemaVersion = schemaVersion,
        proposedActionsJson = proposedActionsJson,
        executedActionsJson = executedActionsJson,
        validationIssuesJson = validationIssuesJson,
        executionMessagesJson = executionMessagesJson,
        undoCommandsJson = undoCommandsJson,
        undoState = undoState,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun AiActionLog.toEntity(
    syncStatus: String = SyncStatus.PendingPush,
    remoteUpdatedAt: Long = 0L,
    lastSyncedAt: Long = 0L,
): AiActionLogEntity {
    return AiActionLogEntity(
        auditId = auditId,
        requestMessageId = requestMessageId,
        responseMessageId = responseMessageId,
        sessionId = sessionId,
        workspaceId = workspaceId,
        mode = mode,
        provider = provider,
        model = model,
        schemaName = schemaName,
        schemaVersion = schemaVersion,
        proposedActionsJson = proposedActionsJson,
        executedActionsJson = executedActionsJson,
        validationIssuesJson = validationIssuesJson,
        executionMessagesJson = executionMessagesJson,
        undoCommandsJson = undoCommandsJson,
        undoState = undoState,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
        remoteUpdatedAt = remoteUpdatedAt,
        lastSyncedAt = lastSyncedAt,
    )
}
