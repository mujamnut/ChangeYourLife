package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.AiSkillEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AiSkillDao {
    @Query(
        """
        SELECT * FROM ai_skills
        WHERE workspaceId = :workspaceId AND deletedAt IS NULL
        ORDER BY isEnabled DESC, name COLLATE NOCASE ASC
        """,
    )
    fun observeActiveByWorkspace(workspaceId: String): Flow<List<AiSkillEntity>>

    @Query("SELECT * FROM ai_skills WHERE id = :skillId LIMIT 1")
    suspend fun getByIdIncludingDeleted(skillId: String): AiSkillEntity?

    @Query(
        """
        SELECT * FROM ai_skills
        WHERE workspaceId = :workspaceId
        ORDER BY updatedAt ASC
        """,
    )
    suspend fun getByWorkspaceIncludingDeleted(workspaceId: String): List<AiSkillEntity>

    @Query(
        """
        SELECT * FROM ai_skills
        WHERE (syncStatus != :syncedStatus OR lastSyncedAt = 0)
        AND syncStatus != :conflictStatus
        ORDER BY updatedAt ASC
        """,
    )
    suspend fun getSkillsNeedingSync(
        syncedStatus: String = SyncStatus.Synced,
        conflictStatus: String = SyncStatus.Conflict,
    ): List<AiSkillEntity>

    @Query("SELECT COUNT(*) FROM ai_skills WHERE syncStatus != :syncedStatus OR lastSyncedAt = 0")
    fun observeSkillsNeedingSyncCount(syncedStatus: String = SyncStatus.Synced): Flow<Int>

    @Query("SELECT COUNT(*) FROM ai_skills WHERE syncStatus = :conflictStatus")
    fun observeSkillConflictCount(conflictStatus: String = SyncStatus.Conflict): Flow<Int>

    @Upsert
    suspend fun upsert(skill: AiSkillEntity)

    @Upsert
    suspend fun upsertAll(skills: List<AiSkillEntity>)

    @Query(
        """
        UPDATE ai_skills
        SET deletedAt = :deletedAt,
            updatedAt = :deletedAt,
            syncStatus = :pendingStatus
        WHERE workspaceId = :workspaceId AND id = :skillId AND deletedAt IS NULL
        """,
    )
    suspend fun softDelete(
        workspaceId: String,
        skillId: String,
        deletedAt: Long,
        pendingStatus: String = SyncStatus.PendingPush,
    ): Int

    @Query(
        """
        UPDATE ai_skills
        SET isEnabled = :enabled,
            updatedAt = :updatedAt,
            syncStatus = :pendingStatus
        WHERE workspaceId = :workspaceId AND id = :skillId AND deletedAt IS NULL
        """,
    )
    suspend fun setEnabled(
        workspaceId: String,
        skillId: String,
        enabled: Boolean,
        updatedAt: Long,
        pendingStatus: String = SyncStatus.PendingPush,
    ): Int
}
