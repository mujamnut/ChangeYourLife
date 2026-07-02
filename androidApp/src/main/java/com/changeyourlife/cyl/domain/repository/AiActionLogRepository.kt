package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.AiActionLog
import kotlinx.coroutines.flow.Flow

interface AiActionLogRepository {
    suspend fun upsert(log: AiActionLog)
    suspend fun getByAuditId(auditId: String): AiActionLog?
    fun observeBySession(sessionId: String): Flow<List<AiActionLog>>
}
