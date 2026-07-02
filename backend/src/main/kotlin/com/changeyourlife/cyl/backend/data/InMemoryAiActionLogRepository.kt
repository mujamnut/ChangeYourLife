package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.AiActionLogRecord
import com.changeyourlife.cyl.backend.domain.AiActionLogRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryAiActionLogRepository : AiActionLogRepository {
    private val actionLogsByKey = ConcurrentHashMap<String, AiActionLogRecord>()

    override suspend fun listActionLogs(
        userId: String,
        workspaceId: String,
        updatedAfter: Long,
    ): List<AiActionLogRecord> {
        return actionLogsByKey.values
            .asSequence()
            .filter { log -> log.userId == userId }
            .filter { log -> log.workspaceId == workspaceId }
            .filter { log -> log.updatedAt > updatedAfter }
            .sortedBy { log -> log.updatedAt }
            .toList()
    }

    override suspend fun upsertActionLog(userId: String, actionLog: AiActionLogRecord): AiActionLogRecord? {
        if (actionLog.userId != userId) return null
        actionLogsByKey[actionLog.key] = actionLog
        return actionLog
    }
}

private val AiActionLogRecord.key: String
    get() = "$userId:$auditId"
