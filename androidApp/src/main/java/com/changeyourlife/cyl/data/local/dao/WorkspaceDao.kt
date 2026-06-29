package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import com.changeyourlife.cyl.data.local.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY updatedAt DESC")
    fun observeWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getWorkspace(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspaces ORDER BY updatedAt DESC")
    suspend fun getWorkspaces(): List<WorkspaceEntity>

    @Query(
        """
        SELECT * FROM workspaces
        WHERE syncStatus != :syncedStatus OR lastSyncedAt = 0
        ORDER BY updatedAt ASC
        """,
    )
    suspend fun getWorkspacesNeedingSync(syncedStatus: String = SyncStatus.Synced): List<WorkspaceEntity>

    @Upsert
    suspend fun upsertWorkspace(workspace: WorkspaceEntity)
}
