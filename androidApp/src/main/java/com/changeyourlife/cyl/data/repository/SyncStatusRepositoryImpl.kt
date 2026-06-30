package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.SyncTombstoneDao
import com.changeyourlife.cyl.data.local.dao.WorkspaceDao
import com.changeyourlife.cyl.data.sync.BackgroundSyncQueue
import com.changeyourlife.cyl.domain.model.SyncOverview
import com.changeyourlife.cyl.domain.repository.SyncStatusRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SyncStatusRepositoryImpl @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val pageDao: PageDao,
    private val syncTombstoneDao: SyncTombstoneDao,
    private val backgroundSyncQueue: BackgroundSyncQueue,
) : SyncStatusRepository {
    override fun observeOverview(): Flow<SyncOverview> {
        val pendingCount = combine(
            workspaceDao.observeWorkspacesNeedingSyncCount(),
            pageDao.observePagesNeedingSyncCount(),
            syncTombstoneDao.observePendingTombstoneCount(),
        ) { pendingWorkspaces, pendingPages, pendingTombstones ->
            pendingWorkspaces + pendingPages + pendingTombstones
        }
        val conflictCount = combine(
            workspaceDao.observeWorkspaceConflictCount(),
            pageDao.observePageConflictCount(),
        ) { workspaceConflicts, pageConflicts ->
            workspaceConflicts + pageConflicts
        }
        return combine(
            pendingCount,
            conflictCount,
            backgroundSyncQueue.state,
        ) { pending, conflicts, runState ->
            SyncOverview(
                pendingCount = pending,
                conflictCount = conflicts,
                isSyncing = runState.isSyncing,
                lastErrorMessage = runState.lastErrorMessage,
                lastCompletedAt = runState.lastCompletedAt,
            )
        }
    }

    override fun retryNow() {
        backgroundSyncQueue.retryPendingSoon()
    }
}
