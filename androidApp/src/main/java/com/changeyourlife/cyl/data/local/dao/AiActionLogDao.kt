package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.AiActionLogEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AiActionLogDao {
    @Query(
        """
        SELECT * FROM ai_action_logs
        WHERE sessionId = :sessionId
        ORDER BY createdAt ASC
        """,
    )
    fun observeBySession(sessionId: String): Flow<List<AiActionLogEntity>>

    @Query("SELECT * FROM ai_action_logs WHERE auditId = :auditId LIMIT 1")
    suspend fun getByAuditId(auditId: String): AiActionLogEntity?

    @Query(
        """
        SELECT * FROM ai_action_logs
        WHERE workspaceId = :workspaceId
        ORDER BY updatedAt ASC
        """,
    )
    suspend fun getByWorkspace(workspaceId: String): List<AiActionLogEntity>

    @Query(
        """
        SELECT * FROM ai_action_logs
        WHERE (syncStatus != :syncedStatus OR lastSyncedAt = 0)
        AND syncStatus != :conflictStatus
        ORDER BY updatedAt ASC
        """,
    )
    suspend fun getLogsNeedingSync(
        syncedStatus: String = SyncStatus.Synced,
        conflictStatus: String = SyncStatus.Conflict,
    ): List<AiActionLogEntity>

    @Query("SELECT COUNT(*) FROM ai_action_logs WHERE syncStatus != :syncedStatus OR lastSyncedAt = 0")
    fun observeLogsNeedingSyncCount(syncedStatus: String = SyncStatus.Synced): Flow<Int>

    @Query("SELECT COUNT(*) FROM ai_action_logs WHERE syncStatus = :conflictStatus")
    fun observeLogConflictCount(conflictStatus: String = SyncStatus.Conflict): Flow<Int>

    @Upsert
    suspend fun upsert(log: AiActionLogEntity)
}
