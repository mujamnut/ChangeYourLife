package com.changeyourlife.cyl.data.sync

import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.PageContentDao
import com.changeyourlife.cyl.data.local.dao.WorkspaceDao
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import com.changeyourlife.cyl.data.local.entity.WorkspaceEntity
import com.changeyourlife.cyl.data.local.mapper.toContentProjection
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.data.local.session.WorkspaceSelectionStore
import com.changeyourlife.cyl.data.remote.sync.PageBlockCreateRequestDto
import com.changeyourlife.cyl.data.remote.sync.PageBlockPatchRequestDto
import com.changeyourlife.cyl.data.remote.sync.PageElementPositionPatchRequestDto
import com.changeyourlife.cyl.data.remote.sync.PagePropertyCreateRequestDto
import com.changeyourlife.cyl.data.remote.sync.PagePropertyValuePatchRequestDto
import com.changeyourlife.cyl.data.remote.sync.PageSyncDto
import com.changeyourlife.cyl.data.remote.sync.PageTableColumnCreateRequestDto
import com.changeyourlife.cyl.data.remote.sync.PageTableColumnPatchRequestDto
import com.changeyourlife.cyl.data.remote.sync.PageTableCellValuePatchRequestDto
import com.changeyourlife.cyl.data.remote.sync.PageTablePatchRequestDto
import com.changeyourlife.cyl.data.remote.sync.PageTableRowCreateRequestDto
import com.changeyourlife.cyl.data.remote.sync.PageTableRowPatchRequestDto
import com.changeyourlife.cyl.data.remote.sync.SyncApi
import com.changeyourlife.cyl.data.remote.sync.WorkspaceSyncDto
import com.changeyourlife.cyl.data.remote.sync.toDomain as remoteToDomain
import com.changeyourlife.cyl.data.remote.sync.toSyncDto
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableRow
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class SessionSyncCoordinator @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val pageDao: PageDao,
    private val pageContentDao: PageContentDao,
    private val syncApi: SyncApi,
    private val tokenStore: AuthTokenStore,
    private val selectionStore: WorkspaceSelectionStore,
) {
    suspend fun syncAfterAuth() {
        val header = authHeader() ?: return

        val remoteWorkspaces = runCatching {
            syncApi.listWorkspaces(header).workspaces
        }.onFailure(::handleSyncFailure).getOrNull().orEmpty()

        remoteWorkspaces
            .filter { workspace -> workspace.deletedAt == null }
            .forEach { workspace -> mergeWorkspace(workspace) }

        val workspaceIds = (
            workspaceDao.getWorkspaces().map { workspace -> workspace.id } +
                remoteWorkspaces.filter { workspace -> workspace.deletedAt == null }.map { workspace -> workspace.id }
            ).distinct()

        if (selectionStore.activeWorkspaceId.value.isNullOrBlank()) {
            workspaceIds.firstOrNull()?.let(selectionStore::setActiveWorkspaceId)
        }

        workspaceIds.forEach { workspaceId ->
            refreshPages(workspaceId = workspaceId, includeDeleted = true)
        }

        pushPendingWorkspaces()
        pushPendingPages()
    }

    suspend fun refreshWorkspaces() {
        val header = authHeader() ?: return
        runCatching {
            syncApi.listWorkspaces(header).workspaces
        }.onSuccess { workspaces ->
            workspaces
                .filter { workspace -> workspace.deletedAt == null }
                .forEach { workspace -> mergeWorkspace(workspace) }
        }.onFailure(::handleSyncFailure)
    }

    suspend fun refreshPages(workspaceId: String, includeDeleted: Boolean) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.listPages(
                authorization = header,
                workspaceId = workspaceId,
                includeDeleted = includeDeleted,
            ).pages
        }.onSuccess { pages ->
            pages.forEach { page -> mergePage(page) }
        }.onFailure(::handleSyncFailure)
    }

    suspend fun refreshPage(pageId: String, includeDeleted: Boolean) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.getPage(
                authorization = header,
                id = pageId,
                includeDeleted = includeDeleted,
            )
        }.onSuccess { page ->
            mergePage(page)
        }.onFailure(::handleSyncFailure)
    }

    suspend fun pushWorkspace(workspace: WorkspaceEntity) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.upsertWorkspace(
                authorization = header,
                id = workspace.id,
                workspace = workspace.toDomain().toSyncDto(),
            )
        }.onSuccess { remoteWorkspace ->
            workspaceDao.upsertWorkspace(
                remoteWorkspace.toSyncedEntity(
                    previous = workspace,
                    now = System.currentTimeMillis(),
                ),
            )
        }.onFailure(::handleSyncFailure)
    }

    suspend fun pushPage(page: PageEntity) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.upsertPage(
                authorization = header,
                id = page.id,
                page = page.toDomain().toSyncDto(),
            )
        }.onSuccess { remotePage ->
            persistPage(
                remotePage.toSyncedEntity(
                    previous = page,
                    now = System.currentTimeMillis(),
                ),
            )
        }.onFailure(::handleSyncFailure)
    }

    suspend fun pushPageBlockText(
        page: PageEntity,
        blockId: String,
        text: String,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.updatePageBlockText(
                authorization = header,
                id = page.id,
                blockId = blockId,
                request = PageBlockPatchRequestDto(text = text),
            )
        }.onSuccess { remotePage ->
            persistPage(
                remotePage.toSyncedEntity(
                    previous = page,
                    now = System.currentTimeMillis(),
                ),
            )
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageBlockPatch(
        page: PageEntity,
        block: PageBlock,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.updatePageBlockText(
                authorization = header,
                id = page.id,
                blockId = block.id,
                request = PageBlockPatchRequestDto(
                    text = block.text,
                    richTextSpans = block.richTextSpans,
                    mediaAttachments = block.mediaAttachments,
                    isChecked = block.isChecked,
                ),
            )
        }.onSuccess { remotePage ->
            persistPage(
                remotePage.toSyncedEntity(
                    previous = page,
                    now = System.currentTimeMillis(),
                ),
            )
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPagePropertyValue(
        page: PageEntity,
        propertyId: String,
        propertyName: String,
        value: String,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.updatePagePropertyValue(
                authorization = header,
                id = page.id,
                request = PagePropertyValuePatchRequestDto(
                    propertyId = propertyId,
                    propertyName = propertyName,
                    value = value,
                ),
            )
        }.onSuccess { remotePage ->
            persistPage(
                remotePage.toSyncedEntity(
                    previous = page,
                    now = System.currentTimeMillis(),
                ),
            )
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTableCellValue(
        page: PageEntity,
        rowId: String,
        columnId: String,
        value: String,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.updatePageTableCellValue(
                authorization = header,
                id = page.id,
                request = PageTableCellValuePatchRequestDto(
                    rowId = rowId,
                    columnId = columnId,
                    value = value,
                ),
            )
        }.onSuccess { remotePage ->
            persistPage(
                remotePage.toSyncedEntity(
                    previous = page,
                    now = System.currentTimeMillis(),
                ),
            )
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTablePatch(
        page: PageEntity,
        tableBlockId: String,
        table: PageTable,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.updatePageTable(
                authorization = header,
                id = page.id,
                tableBlockId = tableBlockId,
                request = table.toPatchRequestDto(),
            )
        }.onSuccess { remotePage ->
            persistPage(
                remotePage.toSyncedEntity(
                    previous = page,
                    now = System.currentTimeMillis(),
                ),
            )
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTableColumnPatch(
        page: PageEntity,
        tableBlockId: String,
        column: PageTableColumn,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.updatePageTableColumn(
                authorization = header,
                id = page.id,
                tableBlockId = tableBlockId,
                columnId = column.id,
                request = column.toPatchRequestDto(),
            )
        }.onSuccess { remotePage ->
            persistPage(
                remotePage.toSyncedEntity(
                    previous = page,
                    now = System.currentTimeMillis(),
                ),
            )
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageBlockCreate(
        page: PageEntity,
        block: PageBlock,
        parentBlockId: String,
        afterBlockId: String,
        targetIndex: Int?,
    ) {
        if (!block.canUseGranularCreate()) {
            pushPage(page)
            return
        }
        val header = authHeader() ?: return
        runCatching {
            syncApi.addPageBlock(
                authorization = header,
                id = page.id,
                request = PageBlockCreateRequestDto(
                    blockId = block.id,
                    type = block.type.name,
                    text = block.text,
                    parentBlockId = parentBlockId,
                    afterBlockId = afterBlockId,
                    targetIndex = targetIndex,
                ),
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageBlockDelete(
        page: PageEntity,
        blockId: String,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.deletePageBlock(
                authorization = header,
                id = page.id,
                blockId = blockId,
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageBlockMove(
        page: PageEntity,
        blockId: String,
        targetIndex: Int,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.movePageBlock(
                authorization = header,
                id = page.id,
                blockId = blockId,
                request = PageElementPositionPatchRequestDto(targetIndex = targetIndex),
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPagePropertyCreate(
        page: PageEntity,
        property: PageProperty,
        targetIndex: Int?,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.addPageProperty(
                authorization = header,
                id = page.id,
                request = PagePropertyCreateRequestDto(
                    propertyId = property.id,
                    name = property.name,
                    type = property.type.name,
                    value = property.value,
                    targetIndex = targetIndex,
                ),
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPagePropertyDelete(
        page: PageEntity,
        propertyId: String,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.deletePageProperty(
                authorization = header,
                id = page.id,
                propertyId = propertyId,
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPagePropertyMove(
        page: PageEntity,
        propertyId: String,
        targetIndex: Int,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.movePageProperty(
                authorization = header,
                id = page.id,
                propertyId = propertyId,
                request = PageElementPositionPatchRequestDto(targetIndex = targetIndex),
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTableColumnCreate(
        page: PageEntity,
        tableBlockId: String,
        column: PageTableColumn,
        cellValues: Map<String, String>,
        targetIndex: Int?,
    ) {
        if (!column.canUseGranularCreate()) {
            pushPage(page)
            return
        }
        val header = authHeader() ?: return
        runCatching {
            syncApi.addPageTableColumn(
                authorization = header,
                id = page.id,
                tableBlockId = tableBlockId,
                request = PageTableColumnCreateRequestDto(
                    columnId = column.id,
                    name = column.name,
                    type = column.type.name,
                    cellValues = cellValues,
                    targetIndex = targetIndex,
                ),
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTableColumnDelete(
        page: PageEntity,
        tableBlockId: String,
        columnId: String,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.deletePageTableColumn(
                authorization = header,
                id = page.id,
                tableBlockId = tableBlockId,
                columnId = columnId,
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTableColumnMove(
        page: PageEntity,
        tableBlockId: String,
        columnId: String,
        targetIndex: Int,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.movePageTableColumn(
                authorization = header,
                id = page.id,
                tableBlockId = tableBlockId,
                columnId = columnId,
                request = PageElementPositionPatchRequestDto(targetIndex = targetIndex),
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTableRowCreate(
        page: PageEntity,
        tableBlockId: String,
        row: PageTableRow,
        targetIndex: Int?,
    ) {
        if (row.blocks.isNotEmpty()) {
            pushPage(page)
            return
        }
        val header = authHeader() ?: return
        runCatching {
            syncApi.addPageTableRow(
                authorization = header,
                id = page.id,
                tableBlockId = tableBlockId,
                request = PageTableRowCreateRequestDto(
                    rowId = row.id,
                    cells = row.cells,
                    targetIndex = targetIndex,
                ),
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTableRowPatch(
        page: PageEntity,
        tableBlockId: String,
        row: PageTableRow,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.updatePageTableRow(
                authorization = header,
                id = page.id,
                tableBlockId = tableBlockId,
                rowId = row.id,
                request = PageTableRowPatchRequestDto(blocks = row.blocks),
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTableRowDelete(
        page: PageEntity,
        tableBlockId: String,
        rowId: String,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.deletePageTableRow(
                authorization = header,
                id = page.id,
                tableBlockId = tableBlockId,
                rowId = rowId,
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun pushPageTableRowMove(
        page: PageEntity,
        tableBlockId: String,
        rowId: String,
        targetIndex: Int,
    ) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.movePageTableRow(
                authorization = header,
                id = page.id,
                tableBlockId = tableBlockId,
                rowId = rowId,
                request = PageElementPositionPatchRequestDto(targetIndex = targetIndex),
            )
        }.onSuccess { remotePage ->
            persistPage(remotePage.toSyncedEntity(previous = page, now = System.currentTimeMillis()))
        }.onFailure { error ->
            handleSyncFailure(error)
            pushPage(page)
        }
    }

    suspend fun deletePage(pageId: String) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.deletePage(header, pageId)
        }.onSuccess {
            markPageSynced(pageId)
        }.onFailure(::handleSyncFailure)
    }

    suspend fun restorePage(pageId: String) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.restorePage(header, pageId)
        }.onSuccess {
            markPageSynced(pageId)
        }.onFailure(::handleSyncFailure)
    }

    suspend fun deletePagePermanently(pageId: String) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.deletePagePermanently(header, pageId)
        }.onFailure(::handleSyncFailure)
    }

    suspend fun keepLocalPageConflict(pageId: String) {
        val page = pageDao.getPage(pageId) ?: return
        val pendingPage = page.copy(syncStatus = SyncStatus.PendingPush)
        persistPage(pendingPage)
        pushPage(pendingPage)
    }

    suspend fun useRemotePageConflict(pageId: String) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.getPage(
                authorization = header,
                id = pageId,
                includeDeleted = true,
            )
        }.onSuccess { remotePage ->
            persistPage(
                remotePage.toSyncedEntity(
                    previous = pageDao.getPage(pageId),
                    now = System.currentTimeMillis(),
                ),
            )
        }.onFailure(::handleSyncFailure)
    }

    private suspend fun pushPendingWorkspaces() {
        workspaceDao.getWorkspacesNeedingSync()
            .filterNot { workspace -> workspace.syncStatus == SyncStatus.Conflict }
            .forEach { workspace -> pushWorkspace(workspace.copy(syncStatus = SyncStatus.PendingPush)) }
    }

    private suspend fun pushPendingPages() {
        pageDao.getPagesNeedingSync()
            .filterNot { page -> page.syncStatus == SyncStatus.Conflict }
            .forEach { page -> pushPage(page.copy(syncStatus = SyncStatus.PendingPush)) }
    }

    private suspend fun mergeWorkspace(remoteWorkspace: WorkspaceSyncDto) {
        val localWorkspace = workspaceDao.getWorkspace(remoteWorkspace.id)
        val now = System.currentTimeMillis()
        when {
            localWorkspace == null -> {
                workspaceDao.upsertWorkspace(remoteWorkspace.toSyncedEntity(previous = null, now = now))
            }

            localWorkspace.syncStatus == SyncStatus.PendingPush &&
                localWorkspace.remoteUpdatedAt > 0 &&
                remoteWorkspace.updatedAt > localWorkspace.remoteUpdatedAt &&
                remoteWorkspace.updatedAt != localWorkspace.updatedAt -> {
                workspaceDao.upsertWorkspace(localWorkspace.copy(syncStatus = SyncStatus.Conflict))
            }

            remoteWorkspace.updatedAt >= localWorkspace.updatedAt ||
                localWorkspace.syncStatus == SyncStatus.Synced -> {
                workspaceDao.upsertWorkspace(remoteWorkspace.toSyncedEntity(previous = localWorkspace, now = now))
            }

            else -> {
                workspaceDao.upsertWorkspace(localWorkspace.copy(syncStatus = SyncStatus.PendingPush))
                pushWorkspace(localWorkspace.copy(syncStatus = SyncStatus.PendingPush))
            }
        }
    }

    private suspend fun mergePage(remotePage: PageSyncDto) {
        val localPage = pageDao.getPage(remotePage.id)
        val now = System.currentTimeMillis()
        when {
            localPage == null -> {
                persistPage(remotePage.toSyncedEntity(previous = null, now = now))
            }

            localPage.syncStatus == SyncStatus.PendingPush &&
                localPage.remoteUpdatedAt > 0 &&
                remotePage.updatedAt > localPage.remoteUpdatedAt &&
                remotePage.updatedAt != localPage.updatedAt -> {
                persistPage(localPage.copy(syncStatus = SyncStatus.Conflict))
            }

            remotePage.updatedAt >= localPage.updatedAt ||
                localPage.syncStatus == SyncStatus.Synced -> {
                persistPage(remotePage.toSyncedEntity(previous = localPage, now = now))
            }

            else -> {
                persistPage(localPage.copy(syncStatus = SyncStatus.PendingPush))
                pushPage(localPage.copy(syncStatus = SyncStatus.PendingPush))
            }
        }
    }

    private suspend fun markPageSynced(pageId: String) {
        val page = pageDao.getPage(pageId) ?: return
        persistPage(
            page.copy(
                syncStatus = SyncStatus.Synced,
                remoteUpdatedAt = page.updatedAt,
                lastSyncedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun WorkspaceSyncDto.toSyncedEntity(
        previous: WorkspaceEntity?,
        now: Long,
    ): WorkspaceEntity {
        return this.remoteToDomain()
            .toEntity()
            .copy(
                syncStatus = SyncStatus.Synced,
                remoteUpdatedAt = updatedAt,
                lastSyncedAt = now,
                createdAt = previous?.createdAt ?: createdAt,
            )
    }

    private fun PageSyncDto.toSyncedEntity(
        previous: PageEntity?,
        now: Long,
    ): PageEntity {
        return this.remoteToDomain()
            .toEntity()
            .copy(
                syncStatus = SyncStatus.Synced,
                remoteUpdatedAt = updatedAt,
                lastSyncedAt = now,
                createdAt = previous?.createdAt ?: createdAt,
            )
    }

    private suspend fun persistPage(page: PageEntity) {
        pageDao.upsertPage(page)
        page.toContentProjection()?.let { projection ->
            pageContentDao.replacePageContentProjection(
                pageId = page.id,
                blocks = projection.blocks,
                properties = projection.properties,
                tables = projection.tables,
                columns = projection.columns,
                rows = projection.rows,
                cells = projection.cells,
            )
        }
    }

    private fun authHeader(): String? {
        return tokenStore.token.value?.takeIf { token -> token.isNotBlank() }?.let { token -> "Bearer $token" }
    }

    private fun handleSyncFailure(error: Throwable) {
        if (error is HttpException && error.code() == 401) {
            tokenStore.clearToken()
        }
    }

    private fun PageBlock.canUseGranularCreate(): Boolean {
        return type != PageBlockType.DatabaseTable &&
            richTextSpans.isEmpty() &&
            mediaAttachments.isEmpty() &&
            children.isEmpty() &&
            !isChecked
    }

    private fun PageTableColumn.canUseGranularCreate(): Boolean {
        return this == PageTableColumn(
            id = id,
            name = name,
            type = type,
        )
    }

    private fun PageTable.toPatchRequestDto(): PageTablePatchRequestDto {
        return PageTablePatchRequestDto(
            title = title,
            view = view.name,
            calendarDateColumnId = viewConfig.calendarDateColumnId,
            timelineStartColumnId = viewConfig.timelineStartColumnId,
            timelineEndColumnId = viewConfig.timelineEndColumnId,
            dashboardMetricColumnId = viewConfig.dashboardMetricColumnId,
            dashboardGroupColumnId = viewConfig.dashboardGroupColumnId,
            sortColumnId = sort.columnId,
            sortDirection = sort.direction.name,
            filterColumnId = filter.columnId,
            filterQuery = filter.query,
            groupByColumnId = groupByColumnId,
        )
    }

    private fun PageTableColumn.toPatchRequestDto(): PageTableColumnPatchRequestDto {
        return PageTableColumnPatchRequestDto(
            name = name,
            type = type.name,
            dateFormat = dateFormat.name,
            timeFormat = timeFormat.name,
            dateReminder = dateReminder.name,
            timezoneLabel = timezoneLabel,
            formula = formula,
            relationTargetTableId = relationTargetTableId,
            rollupRelationColumnId = rollupRelationColumnId,
            rollupTargetColumnId = rollupTargetColumnId,
            rollupAggregation = rollupAggregation.name,
        )
    }
}
