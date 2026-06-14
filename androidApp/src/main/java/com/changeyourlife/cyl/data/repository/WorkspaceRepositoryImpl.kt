package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.core.constants.CylDefaults
import com.changeyourlife.cyl.data.local.dao.WorkspaceDao
import com.changeyourlife.cyl.data.local.entity.WorkspaceEntity
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.data.local.session.WorkspaceSelectionStore
import com.changeyourlife.cyl.domain.model.Workspace
import com.changeyourlife.cyl.domain.repository.WorkspaceRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkspaceRepositoryImpl @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val selectionStore: WorkspaceSelectionStore,
) : WorkspaceRepository {
    override fun observeWorkspaces(): Flow<List<Workspace>> {
        return workspaceDao.observeWorkspaces()
            .map { workspaces -> workspaces.map { it.toDomain() } }
    }

    override fun observeActiveWorkspaceId(): Flow<String?> {
        return selectionStore.activeWorkspaceId
    }

    override suspend fun ensureDefaultWorkspace() {
        val existing = workspaceDao.getWorkspace(CylDefaults.DefaultWorkspaceId)
        if (existing == null) {
            val now = System.currentTimeMillis()
            workspaceDao.upsertWorkspace(
                WorkspaceEntity(
                    id = CylDefaults.DefaultWorkspaceId,
                    name = CylDefaults.DefaultWorkspaceName,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        if (selectionStore.activeWorkspaceId.value.isNullOrBlank()) {
            selectionStore.setActiveWorkspaceId(CylDefaults.DefaultWorkspaceId)
        }
    }

    override suspend fun getActiveWorkspaceId(): String? {
        return selectionStore.activeWorkspaceId.value
    }

    override suspend fun setActiveWorkspace(workspaceId: String) {
        val workspace = workspaceDao.getWorkspace(workspaceId) ?: return
        selectionStore.setActiveWorkspaceId(workspace.id)
    }

    override suspend fun createWorkspace(name: String): Workspace {
        val now = System.currentTimeMillis()
        val workspace = Workspace(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = now,
            updatedAt = now,
        )
        workspaceDao.upsertWorkspace(
            workspace.toEntity(),
        )
        selectionStore.setActiveWorkspaceId(workspace.id)
        return workspace
    }

    override suspend fun upsertWorkspace(workspace: Workspace) {
        workspaceDao.upsertWorkspace(workspace.toEntity())
    }
}
