package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.AiActionLogDao
import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
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
    private val aiActionLogDao: AiActionLogDao,
    private val chatMessageDao: ChatMessageDao,
    private val syncTombstoneDao: SyncTombstoneDao,
    private val backgroundSyncQueue: BackgroundSyncQueue,
) : SyncStatusRepository {
    override fun observeOverview(): Flow<SyncOverview> {
        val pendingCount = combine(
            workspaceDao.observeWorkspacesNeedingSyncCount(),
            pageDao.observePagesNeedingSyncCount(),
            aiActionLogDao.observeLogsNeedingSyncCount(),
            chatMessageDao.observeSessionsNeedingSyncCount(),
            chatMessageDao.observeMessagesNeedingSyncCount(),
            syncTombstoneDao.observePendingTombstoneCount(),
        ) { counts ->
            counts.sum()
        }
        val conflictCount = combine(
            workspaceDao.observeWorkspaceConflictCount(),
            pageDao.observePageConflictCount(),
            aiActionLogDao.observeLogConflictCount(),
            chatMessageDao.observeSessionConflictCount(),
            chatMessageDao.observeMessageConflictCount(),
        ) { counts ->
            counts.sum()
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
