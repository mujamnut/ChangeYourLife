package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.core.constants.CylDefaults
import com.changeyourlife.cyl.data.local.dao.WorkspaceDao
import com.changeyourlife.cyl.data.local.entity.WorkspaceEntity
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.data.local.session.WorkspaceSelectionStore
import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.data.remote.sync.SyncApi
import com.changeyourlife.cyl.data.remote.sync.toDomain as syncToDomain
import com.changeyourlife.cyl.data.remote.sync.toSyncDto
import com.changeyourlife.cyl.domain.model.Workspace
import com.changeyourlife.cyl.domain.repository.WorkspaceRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import retrofit2.HttpException

class WorkspaceRepositoryImpl @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val selectionStore: WorkspaceSelectionStore,
    private val syncApi: SyncApi,
    private val tokenStore: AuthTokenStore,
) : WorkspaceRepository {
    override fun observeWorkspaces(): Flow<List<Workspace>> {
        return workspaceDao.observeWorkspaces()
            .onStart { refreshWorkspaces() }
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
            pushWorkspace(
                Workspace(
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
        pushWorkspace(workspace)
        selectionStore.setActiveWorkspaceId(workspace.id)
        return workspace
    }

    override suspend fun upsertWorkspace(workspace: Workspace) {
        workspaceDao.upsertWorkspace(workspace.toEntity())
        pushWorkspace(workspace)
    }

    private suspend fun refreshWorkspaces() {
        val header = authHeader() ?: return
        runCatching {
            syncApi.listWorkspaces(header).workspaces
        }.onSuccess { remoteWorkspaces ->
            remoteWorkspaces
                .filter { workspace -> workspace.deletedAt == null }
                .map { workspace -> workspace.syncToDomain().toEntity() }
                .forEach { workspace -> workspaceDao.upsertWorkspace(workspace) }
        }.onFailure(::handleSyncFailure)
    }

    private suspend fun pushWorkspace(workspace: Workspace) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.upsertWorkspace(
                authorization = header,
                id = workspace.id,
                workspace = workspace.toSyncDto(),
            )
        }.onFailure(::handleSyncFailure)
    }

    private fun authHeader(): String? {
        return tokenStore.token.value?.takeIf { it.isNotBlank() }?.let { token -> "Bearer $token" }
    }

    private fun handleSyncFailure(error: Throwable) {
        if (error is HttpException && error.code() == 401) {
            tokenStore.clearToken()
        }
    }
}
