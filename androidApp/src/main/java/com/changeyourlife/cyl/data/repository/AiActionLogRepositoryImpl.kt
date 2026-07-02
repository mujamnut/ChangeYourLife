package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.AiActionLogDao
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.domain.model.AiActionLog
import com.changeyourlife.cyl.domain.repository.AiActionLogRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiActionLogRepositoryImpl @Inject constructor(
    private val aiActionLogDao: AiActionLogDao,
) : AiActionLogRepository {
    override suspend fun upsert(log: AiActionLog) {
        aiActionLogDao.upsert(log.toEntity())
    }

    override suspend fun getByAuditId(auditId: String): AiActionLog? {
        return aiActionLogDao.getByAuditId(auditId)?.toDomain()
    }

    override fun observeBySession(sessionId: String): Flow<List<AiActionLog>> {
        return aiActionLogDao.observeBySession(sessionId)
            .map { logs -> logs.map { it.toDomain() } }
    }
}
