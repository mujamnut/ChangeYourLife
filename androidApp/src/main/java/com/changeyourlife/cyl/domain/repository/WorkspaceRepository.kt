package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    fun observeWorkspaces(): Flow<List<Workspace>>

    fun observeActiveWorkspaceId(): Flow<String?>

    suspend fun ensureDefaultWorkspace()

    suspend fun getActiveWorkspaceId(): String?

    suspend fun setActiveWorkspace(workspaceId: String)

    suspend fun createWorkspace(name: String): Workspace

    suspend fun upsertWorkspace(workspace: Workspace)
}
