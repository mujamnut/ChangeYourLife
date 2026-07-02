package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.AiActionLogDao
import com.changeyourlife.cyl.data.local.entity.AiActionLogEntity
import com.changeyourlife.cyl.domain.model.AiActionLog
import com.changeyourlife.cyl.domain.model.AiActionUndoState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiActionLogRepositoryImplTest {
    @Test
    fun actionLogSurvivesRepositoryRecreation() = runBlocking {
        val dao = FakeAiActionLogDao()
        val firstRepository = AiActionLogRepositoryImpl(dao)
        val log = AiActionLog(
            auditId = "audit-1",
            requestMessageId = "user-message-1",
            responseMessageId = "assistant-message-1",
            sessionId = "session-1",
            workspaceId = "workspace-1",
            mode = "Edit",
            provider = "openrouter",
            model = "openai/gpt-oss-20b:free",
            schemaName = "CYL_ACTION_SCHEMA",
            schemaVersion = 2,
            proposedActionsJson = """[{"type":"ADD_TABLE_ROW","target":"Budget","actionIndex":0}]""",
            executedActionsJson = """[{"type":"ADD_TABLE_ROW","target":"Budget","actionIndex":0}]""",
            validationIssuesJson = "[]",
            executionMessagesJson = """["Done"]""",
            undoCommandsJson = """[{"actionIndex":0,"commandType":"DeleteBlock","targetType":"Block","targetId":"block-1"}]""",
            undoState = AiActionUndoState.PendingCommandLink,
            createdAt = 2000L,
        )

        firstRepository.upsert(log)

        val recreatedRepository = AiActionLogRepositoryImpl(dao)
        assertEquals(log, recreatedRepository.getByAuditId("audit-1"))
        assertEquals(listOf(log), recreatedRepository.observeBySession("session-1").first())
        assertNull(recreatedRepository.getByAuditId("missing"))
    }

    @Test
    fun observeBySessionSortsByCreatedAt() = runBlocking {
        val dao = FakeAiActionLogDao()
        val repository = AiActionLogRepositoryImpl(dao)
        repository.upsert(log(auditId = "audit-2", createdAt = 3000L))
        repository.upsert(log(auditId = "audit-1", createdAt = 1000L))

        val logs = repository.observeBySession("session-1").first()

        assertEquals(listOf("audit-1", "audit-2"), logs.map { it.auditId })
    }

    private fun log(auditId: String, createdAt: Long): AiActionLog {
        return AiActionLog(
            auditId = auditId,
            requestMessageId = "user-$auditId",
            responseMessageId = "assistant-$auditId",
            sessionId = "session-1",
            workspaceId = "workspace-1",
            mode = "Edit",
            provider = "openrouter",
            model = "openai/gpt-oss-20b:free",
            schemaName = "CYL_ACTION_SCHEMA",
            schemaVersion = 1,
            proposedActionsJson = "[]",
            executedActionsJson = "[]",
            validationIssuesJson = "[]",
            executionMessagesJson = "[]",
            undoCommandsJson = "[]",
            undoState = AiActionUndoState.NotAvailable,
            createdAt = createdAt,
        )
    }

    private class FakeAiActionLogDao : AiActionLogDao {
        private val logs = linkedMapOf<String, AiActionLogEntity>()

        override fun observeBySession(sessionId: String): Flow<List<AiActionLogEntity>> {
            return flowOf(
                logs.values
                    .filter { log -> log.sessionId == sessionId }
                    .sortedBy { log -> log.createdAt },
            )
        }

        override suspend fun getByAuditId(auditId: String): AiActionLogEntity? {
            return logs[auditId]
        }

        override suspend fun getByWorkspace(workspaceId: String): List<AiActionLogEntity> {
            return logs.values
                .filter { log -> log.workspaceId == workspaceId }
                .sortedBy { log -> log.updatedAt }
        }

        override suspend fun getLogsNeedingSync(
            syncedStatus: String,
            conflictStatus: String,
        ): List<AiActionLogEntity> {
            return logs.values
                .filter { log -> (log.syncStatus != syncedStatus || log.lastSyncedAt == 0L) && log.syncStatus != conflictStatus }
                .sortedBy { log -> log.updatedAt }
        }

        override fun observeLogsNeedingSyncCount(syncedStatus: String): Flow<Int> {
            return flowOf(logs.values.count { log -> log.syncStatus != syncedStatus || log.lastSyncedAt == 0L })
        }

        override fun observeLogConflictCount(conflictStatus: String): Flow<Int> {
            return flowOf(logs.values.count { log -> log.syncStatus == conflictStatus })
        }

        override suspend fun upsert(log: AiActionLogEntity) {
            logs[log.auditId] = log
        }
    }
}
