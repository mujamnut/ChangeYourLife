package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.PageContentDao
import com.changeyourlife.cyl.data.local.dao.SyncTombstoneDao
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import com.changeyourlife.cyl.data.local.entity.SyncTombstoneEntity
import com.changeyourlife.cyl.data.local.entity.SyncTombstoneType
import com.changeyourlife.cyl.data.local.mapper.toContentProjection
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.data.local.mapper.toDocument
import com.changeyourlife.cyl.data.sync.BackgroundSyncQueue
import com.changeyourlife.cyl.data.sync.SessionSyncCoordinator
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageSyncStatus
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.repository.PageRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val pageRepositoryJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class PageRepositoryImpl @Inject constructor(
    private val pageDao: PageDao,
    private val pageContentDao: PageContentDao,
    private val syncTombstoneDao: SyncTombstoneDao,
    private val sessionSyncCoordinator: SessionSyncCoordinator,
    private val backgroundSyncQueue: BackgroundSyncQueue,
) : PageRepository {
    override fun observePages(workspaceId: String): Flow<List<Page>> {
        return pageDao.observePages(workspaceId)
            .onStart {
                backgroundSyncQueue.enqueue("refreshPages:$workspaceId", persistForRetry = false) {
                    refreshPages(workspaceId, includeDeleted = false)
                    pushPendingChanges()
                }
            }
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observeChildPages(parentPageId: String): Flow<List<Page>> {
        return pageDao.observeChildPages(parentPageId)
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observeRecentPages(limit: Int): Flow<List<Page>> {
        return pageDao.observeRecentPages(limit)
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observeRecentPages(workspaceId: String, limit: Int): Flow<List<Page>> {
        return pageDao.observeRecentPages(workspaceId, limit)
            .onStart {
                backgroundSyncQueue.enqueue("refreshRecentPages:$workspaceId", persistForRetry = false) {
                    refreshPages(workspaceId, includeDeleted = false)
                    pushPendingChanges()
                }
            }
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observeDeletedPages(workspaceId: String): Flow<List<Page>> {
        return pageDao.observeDeletedPages(workspaceId)
            .onStart {
                backgroundSyncQueue.enqueue("refreshDeletedPages:$workspaceId", persistForRetry = false) {
                    refreshPages(workspaceId, includeDeleted = true)
                    pushPendingChanges()
                }
            }
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observePage(pageId: String): Flow<Page?> {
        return pageDao.observePage(pageId)
            .onStart {
                backgroundSyncQueue.enqueue("refreshPage:$pageId", persistForRetry = false) {
                    refreshPage(pageId, includeDeleted = false)
                    pushPendingChanges()
                }
            }
            .map { page -> page?.toDomain() }
    }

    override fun observePageSyncState(pageId: String): Flow<PageSyncState> {
        return pageDao.observePageIncludingDeleted(pageId)
            .map { page ->
                PageSyncState(
                    status = page?.syncStatus.toPageSyncStatus(),
                    remoteUpdatedAt = page?.remoteUpdatedAt ?: 0L,
                    lastSyncedAt = page?.lastSyncedAt ?: 0L,
                )
            }
    }

    override fun observePageCount(): Flow<Int> {
        return pageDao.observePageCount()
    }

    override fun observePageCount(workspaceId: String): Flow<Int> {
        return pageDao.observePageCount(workspaceId)
    }

    override suspend fun getPage(pageId: String): Page? {
        return pageDao.getPage(pageId)?.toDomain()
    }

    override suspend fun upsertPage(page: Page) {
        val entity = page.toEntity().copy(syncStatus = SyncStatus.PendingPush)
        persistPage(entity)
        backgroundSyncQueue.enqueue("pushPage:${entity.id}") { pushPage(entity) }
    }

    override suspend fun updateBlockText(
        pageId: String,
        blockId: String,
        text: String,
    ): Boolean {
        return mutateProjectedPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageBlockText(
                    page = page,
                    blockId = blockId,
                    text = text,
                )
            },
        ) { updatedAt ->
            pageContentDao.updateBlockText(
                pageId = pageId,
                blockId = blockId,
                text = text,
                updatedAt = updatedAt,
            ) > 0
        }
    }

    override suspend fun updateBlock(
        pageId: String,
        block: PageBlock,
    ): Boolean {
        if (block.id.isBlank()) return false
        return mutateProjectedPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageBlockPatch(
                    page = page,
                    block = block,
                )
            },
        ) { updatedAt ->
            pageContentDao.updateBlockContent(
                pageId = pageId,
                blockId = block.id,
                text = block.text,
                richTextJson = pageRepositoryJson.encodeToString(block.richTextSpans),
                mediaJson = pageRepositoryJson.encodeToString(block.mediaAttachments),
                isChecked = block.isChecked,
                updatedAt = updatedAt,
            ) > 0
        }
    }

    override suspend fun updatePropertyValue(
        pageId: String,
        propertyId: String,
        propertyName: String,
        value: String,
    ): Boolean {
        if (propertyId.isBlank() && propertyName.isBlank()) return false
        return mutateProjectedPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPagePropertyValue(
                    page = page,
                    propertyId = propertyId,
                    propertyName = propertyName,
                    value = value,
                )
            },
        ) { updatedAt ->
            pageContentDao.updatePropertyValue(
                pageId = pageId,
                propertyId = propertyId,
                propertyName = propertyName,
                value = value,
                updatedAt = updatedAt,
            ) > 0
        }
    }

    override suspend fun updateTableCellValue(
        pageId: String,
        rowId: String,
        columnId: String,
        value: String,
    ): Boolean {
        return mutateProjectedPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTableCellValue(
                    page = page,
                    rowId = rowId,
                    columnId = columnId,
                    value = value,
                )
            },
        ) { updatedAt ->
            pageContentDao.updateTableCellValue(
                rowId = rowId,
                columnId = columnId,
                value = value,
                updatedAt = updatedAt,
            ) > 0
        }
    }

    override suspend fun updateTable(
        pageId: String,
        tableBlockId: String,
        table: PageTable,
    ): Boolean {
        if (tableBlockId.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTablePatch(
                    page = page,
                    tableBlockId = tableBlockId,
                    table = table,
                )
            },
        ) { document ->
            document.updateTableBlock(tableBlockId) { block ->
                block.copy(
                    table = block.table.copy(
                        title = table.title,
                        view = table.view,
                        viewConfig = table.viewConfig,
                        sort = table.sort,
                        filter = table.filter,
                        groupByColumnId = table.groupByColumnId,
                    ),
                )
            }
        }
    }

    override suspend fun updateTableColumn(
        pageId: String,
        tableBlockId: String,
        column: PageTableColumn,
    ): Boolean {
        if (tableBlockId.isBlank() || column.id.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTableColumnPatch(
                    page = page,
                    tableBlockId = tableBlockId,
                    column = column,
                )
            },
        ) { document ->
            document.updateTableBlock(tableBlockId) { block ->
                if (block.table.columns.none { existing -> existing.id == column.id }) return@updateTableBlock null
                block.copy(
                    table = block.table.copy(
                        columns = block.table.columns.map { existing ->
                            if (existing.id == column.id) column else existing
                        },
                    ),
                )
            }
        }
    }

    override suspend fun addBlock(
        pageId: String,
        block: PageBlock,
        parentBlockId: String,
        afterBlockId: String,
        targetIndex: Int?,
    ): Boolean {
        val blockToAdd = block.withStableId()
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageBlockCreate(
                    page = page,
                    block = blockToAdd,
                    parentBlockId = parentBlockId,
                    afterBlockId = afterBlockId,
                    targetIndex = targetIndex,
                )
            },
        ) { document ->
            val result = document.blocks.addBlock(
                block = blockToAdd,
                parentBlockId = parentBlockId,
                afterBlockId = afterBlockId,
                targetIndex = targetIndex,
            )
            result.takeIf { it.changed }?.let { document.copy(blocks = it.blocks) }
        }
    }

    override suspend fun deleteBlock(
        pageId: String,
        blockId: String,
    ): Boolean {
        if (blockId.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageBlockDelete(page = page, blockId = blockId)
            },
        ) { document ->
            val result = document.blocks.deleteBlockById(blockId)
            result.takeIf { it.changed }?.let {
                document.copy(
                    blocks = it.blocks.ifEmpty {
                        listOf(PageContentCodec.newBlock(PageBlockType.Text))
                    },
                )
            }
        }
    }

    override suspend fun moveBlock(
        pageId: String,
        blockId: String,
        targetIndex: Int,
    ): Boolean {
        if (blockId.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageBlockMove(
                    page = page,
                    blockId = blockId,
                    targetIndex = targetIndex,
                )
            },
        ) { document ->
            val result = document.blocks.moveBlockById(blockId, targetIndex)
            result.takeIf { it.changed }?.let { document.copy(blocks = it.blocks) }
        }
    }

    override suspend fun addProperty(
        pageId: String,
        property: PageProperty,
        targetIndex: Int?,
    ): Boolean {
        val propertyToAdd = property.withStableId()
        if (propertyToAdd.name.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPagePropertyCreate(
                    page = page,
                    property = propertyToAdd,
                    targetIndex = targetIndex,
                )
            },
        ) { document ->
            document.copy(
                properties = document.properties.insertElement(
                    element = propertyToAdd,
                    targetIndex = targetIndex,
                    idSelector = PageProperty::id,
                ),
            )
        }
    }

    override suspend fun deleteProperty(
        pageId: String,
        propertyId: String,
    ): Boolean {
        if (propertyId.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPagePropertyDelete(page = page, propertyId = propertyId)
            },
        ) { document ->
            if (document.properties.none { property -> property.id == propertyId }) return@mutateDocumentPage null
            document.copy(properties = document.properties.filterNot { property -> property.id == propertyId })
        }
    }

    override suspend fun moveProperty(
        pageId: String,
        propertyId: String,
        targetIndex: Int,
    ): Boolean {
        if (propertyId.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPagePropertyMove(
                    page = page,
                    propertyId = propertyId,
                    targetIndex = targetIndex,
                )
            },
        ) { document ->
            val moved = document.properties.moveElement(propertyId, targetIndex, PageProperty::id)
            if (moved === document.properties) null else document.copy(properties = moved)
        }
    }

    override suspend fun addTableColumn(
        pageId: String,
        tableBlockId: String,
        column: PageTableColumn,
        cellValues: Map<String, String>,
        targetIndex: Int?,
    ): Boolean {
        val columnToAdd = column.withStableId()
        if (tableBlockId.isBlank() || columnToAdd.name.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTableColumnCreate(
                    page = page,
                    tableBlockId = tableBlockId,
                    column = columnToAdd,
                    cellValues = cellValues,
                    targetIndex = targetIndex,
                )
            },
        ) { document ->
            document.updateTableBlock(tableBlockId) { block ->
                block.copy(
                    table = block.table.copy(
                        columns = block.table.columns.insertElement(
                            element = columnToAdd,
                            targetIndex = targetIndex,
                            idSelector = PageTableColumn::id,
                        ),
                        rows = block.table.rows.map { row ->
                            row.copy(cells = row.cells + (columnToAdd.id to cellValues[row.id].orEmpty()))
                        },
                    ),
                )
            }
        }
    }

    override suspend fun deleteTableColumn(
        pageId: String,
        tableBlockId: String,
        columnId: String,
    ): Boolean {
        if (tableBlockId.isBlank() || columnId.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTableColumnDelete(
                    page = page,
                    tableBlockId = tableBlockId,
                    columnId = columnId,
                )
            },
        ) { document ->
            document.updateTableBlock(tableBlockId) { block ->
                if (block.table.columns.none { column -> column.id == columnId }) return@updateTableBlock null
                block.copy(table = block.table.withoutColumn(columnId))
            }
        }
    }

    override suspend fun moveTableColumn(
        pageId: String,
        tableBlockId: String,
        columnId: String,
        targetIndex: Int,
    ): Boolean {
        if (tableBlockId.isBlank() || columnId.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTableColumnMove(
                    page = page,
                    tableBlockId = tableBlockId,
                    columnId = columnId,
                    targetIndex = targetIndex,
                )
            },
        ) { document ->
            document.updateTableBlock(tableBlockId) { block ->
                val moved = block.table.columns.moveElement(columnId, targetIndex, PageTableColumn::id)
                if (moved === block.table.columns) null else block.copy(table = block.table.copy(columns = moved))
            }
        }
    }

    override suspend fun addTableRow(
        pageId: String,
        tableBlockId: String,
        row: PageTableRow,
        targetIndex: Int?,
    ): Boolean {
        if (tableBlockId.isBlank()) return false
        val rowToAdd = row.withStableId()
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTableRowCreate(
                    page = page,
                    tableBlockId = tableBlockId,
                    row = rowToAdd,
                    targetIndex = targetIndex,
                )
            },
        ) { document ->
            document.updateTableBlock(tableBlockId) { block ->
                val normalizedRow = rowToAdd.withCellsForColumns(block.table.columns)
                block.copy(
                    table = block.table.copy(
                        rows = block.table.rows.insertElement(
                            element = normalizedRow,
                            targetIndex = targetIndex,
                            idSelector = PageTableRow::id,
                        ),
                    ),
                )
            }
        }
    }

    override suspend fun updateTableRow(
        pageId: String,
        tableBlockId: String,
        row: PageTableRow,
    ): Boolean {
        if (tableBlockId.isBlank() || row.id.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTableRowPatch(
                    page = page,
                    tableBlockId = tableBlockId,
                    row = row,
                )
            },
        ) { document ->
            document.updateTableBlock(tableBlockId) { block ->
                if (block.table.rows.none { existing -> existing.id == row.id }) return@updateTableBlock null
                block.copy(
                    table = block.table.copy(
                        rows = block.table.rows.map { existing ->
                            if (existing.id == row.id) row.withCellsForColumns(block.table.columns) else existing
                        },
                    ),
                )
            }
        }
    }

    override suspend fun deleteTableRow(
        pageId: String,
        tableBlockId: String,
        rowId: String,
    ): Boolean {
        if (tableBlockId.isBlank() || rowId.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTableRowDelete(
                    page = page,
                    tableBlockId = tableBlockId,
                    rowId = rowId,
                )
            },
        ) { document ->
            document.updateTableBlock(tableBlockId) { block ->
                if (block.table.rows.none { row -> row.id == rowId }) return@updateTableBlock null
                block.copy(table = block.table.copy(rows = block.table.rows.filterNot { row -> row.id == rowId }))
            }
        }
    }

    override suspend fun moveTableRow(
        pageId: String,
        tableBlockId: String,
        rowId: String,
        targetIndex: Int,
    ): Boolean {
        if (tableBlockId.isBlank() || rowId.isBlank()) return false
        return mutateDocumentPage(
            pageId = pageId,
            remoteSync = { page ->
                sessionSyncCoordinator.pushPageTableRowMove(
                    page = page,
                    tableBlockId = tableBlockId,
                    rowId = rowId,
                    targetIndex = targetIndex,
                )
            },
        ) { document ->
            document.updateTableBlock(tableBlockId) { block ->
                val moved = block.table.rows.moveElement(rowId, targetIndex, PageTableRow::id)
                if (moved === block.table.rows) null else block.copy(table = block.table.copy(rows = moved))
            }
        }
    }

    override suspend fun createPage(
        workspaceId: String,
        title: String,
        content: String,
        parentPageId: String?,
    ): Page {
        val now = System.currentTimeMillis()
        val page = Page(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            parentPageId = parentPageId,
            title = title,
            content = content,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        val entity = page.toEntity().copy(syncStatus = SyncStatus.PendingPush)
        persistPage(entity)
        backgroundSyncQueue.enqueue("createPage:${entity.id}") { pushPage(entity) }
        return page
    }

    override suspend fun deletePage(pageId: String) {
        pageDao.softDeletePageTree(
            pageId = pageId,
            deletedAt = System.currentTimeMillis(),
        )
        backgroundSyncQueue.enqueue("deletePage:$pageId") { this.deletePage(pageId) }
    }

    override suspend fun restorePage(pageId: String) {
        pageDao.restorePageTree(
            pageId = pageId,
            restoredAt = System.currentTimeMillis(),
        )
        backgroundSyncQueue.enqueue("restorePage:$pageId") { this.restorePage(pageId) }
    }

    override suspend fun deletePagePermanently(pageId: String) {
        val now = System.currentTimeMillis()
        syncTombstoneDao.upsertTombstone(
            SyncTombstoneEntity(
                id = "${SyncTombstoneType.PagePermanentDelete}:$pageId",
                entityType = SyncTombstoneType.PagePermanentDelete,
                entityId = pageId,
                createdAt = now,
            ),
        )
        pageDao.deletePageTreePermanently(pageId)
        backgroundSyncQueue.enqueue("deletePagePermanently:$pageId") {
            this.deletePagePermanently(pageId)
        }
    }

    override suspend fun keepLocalPageConflict(pageId: String) {
        sessionSyncCoordinator.keepLocalPageConflict(pageId)
    }

    override suspend fun useRemotePageConflict(pageId: String) {
        sessionSyncCoordinator.useRemotePageConflict(pageId)
    }

    private fun String?.toPageSyncStatus(): PageSyncStatus {
        return when (this) {
            SyncStatus.PendingPush -> PageSyncStatus.PendingPush
            SyncStatus.Conflict -> PageSyncStatus.Conflict
            else -> PageSyncStatus.Synced
        }
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

    private suspend fun mutateProjectedPage(
        pageId: String,
        remoteSync: suspend (PageEntity) -> Unit,
        mutation: suspend (updatedAt: Long) -> Boolean,
    ): Boolean {
        val currentPage = pageDao.getPage(pageId) ?: return false
        ensureProjectionForPage(currentPage)

        val updatedAt = System.currentTimeMillis()
        if (!mutation(updatedAt)) return false

        val updatedDocument = pageContentDao.getPageContentSnapshot(pageId).toDocument()
        val updatedPage = currentPage.copy(
            content = PageContentCodec.encodeDocument(updatedDocument),
            updatedAt = updatedAt,
            syncStatus = SyncStatus.PendingPush,
        )
        persistPage(updatedPage)
        backgroundSyncQueue.enqueue("mutateProjectedPage:$pageId") {
            remoteSync(updatedPage)
        }
        return true
    }

    private suspend fun mutateDocumentPage(
        pageId: String,
        remoteSync: suspend (PageEntity) -> Unit,
        mutation: (PageBlockDocument) -> PageBlockDocument?,
    ): Boolean {
        val currentPage = pageDao.getPage(pageId) ?: return false
        val currentDocument = PageContentCodec.decodeDocument(currentPage.content)
        val updatedDocument = mutation(currentDocument) ?: return false
        val updatedAt = System.currentTimeMillis()
        val updatedPage = currentPage.copy(
            content = PageContentCodec.encodeDocument(updatedDocument),
            updatedAt = updatedAt,
            syncStatus = SyncStatus.PendingPush,
        )
        persistPage(updatedPage)
        backgroundSyncQueue.enqueue("mutateDocumentPage:$pageId") {
            remoteSync(updatedPage)
        }
        return true
    }

    private suspend fun ensureProjectionForPage(page: PageEntity) {
        if (pageContentDao.getBlocks(page.id).isNotEmpty()) return
        persistPage(page)
    }

    private fun PageBlock.withStableId(): PageBlock {
        return if (id.isBlank()) copy(id = UUID.randomUUID().toString()) else this
    }

    private fun PageProperty.withStableId(): PageProperty {
        return if (id.isBlank()) copy(id = UUID.randomUUID().toString()) else this
    }

    private fun PageTableColumn.withStableId(): PageTableColumn {
        return if (id.isBlank()) copy(id = UUID.randomUUID().toString()) else this
    }

    private fun PageTableRow.withStableId(): PageTableRow {
        return if (id.isBlank()) copy(id = UUID.randomUUID().toString()) else this
    }

    private fun List<PageBlock>.addBlock(
        block: PageBlock,
        parentBlockId: String,
        afterBlockId: String,
        targetIndex: Int?,
    ): BlockListMutation {
        if (parentBlockId.isBlank()) {
            return BlockListMutation(
                blocks = insertElementAfter(
                    element = block,
                    afterElementId = afterBlockId,
                    targetIndex = targetIndex,
                    idSelector = PageBlock::id,
                ),
                changed = true,
            )
        }

        var changed = false
        val updatedBlocks = map { existing ->
            if (existing.id == parentBlockId) {
                changed = true
                existing.copy(
                    children = existing.children.insertElementAfter(
                        element = block,
                        afterElementId = afterBlockId,
                        targetIndex = targetIndex,
                        idSelector = PageBlock::id,
                    ),
                )
            } else {
                val childResult = existing.children.addBlock(
                    block = block,
                    parentBlockId = parentBlockId,
                    afterBlockId = afterBlockId,
                    targetIndex = targetIndex,
                )
                if (childResult.changed) {
                    changed = true
                    existing.copy(children = childResult.blocks)
                } else {
                    existing
                }
            }
        }
        return BlockListMutation(blocks = updatedBlocks, changed = changed)
    }

    private fun List<PageBlock>.deleteBlockById(blockId: String): BlockListMutation {
        var changed = false
        val updatedBlocks = mapNotNull { block ->
            if (block.id == blockId) {
                changed = true
                null
            } else {
                val childResult = block.children.deleteBlockById(blockId)
                if (childResult.changed) {
                    changed = true
                    block.copy(children = childResult.blocks)
                } else {
                    block
                }
            }
        }
        return BlockListMutation(blocks = updatedBlocks, changed = changed)
    }

    private fun List<PageBlock>.moveBlockById(blockId: String, targetIndex: Int): BlockListMutation {
        val currentIndex = indexOfFirst { block -> block.id == blockId }
        if (currentIndex >= 0) {
            return BlockListMutation(
                blocks = moveElementAt(currentIndex, targetIndex),
                changed = true,
            )
        }

        var changed = false
        val updatedBlocks = map { block ->
            val childResult = block.children.moveBlockById(blockId, targetIndex)
            if (childResult.changed) {
                changed = true
                block.copy(children = childResult.blocks)
            } else {
                block
            }
        }
        return BlockListMutation(blocks = updatedBlocks, changed = changed)
    }

    private fun PageBlockDocument.updateTableBlock(
        tableBlockId: String,
        transform: (PageBlock) -> PageBlock?,
    ): PageBlockDocument? {
        val result = blocks.updateTableBlock(tableBlockId, transform)
        return result.takeIf { it.changed }?.let { copy(blocks = it.blocks) }
    }

    private fun List<PageBlock>.updateTableBlock(
        tableBlockId: String,
        transform: (PageBlock) -> PageBlock?,
    ): BlockListMutation {
        var changed = false
        val updatedBlocks = map { block ->
            if (block.id == tableBlockId && block.type == PageBlockType.DatabaseTable) {
                val updated = transform(block)
                if (updated != null) {
                    changed = true
                    updated
                } else {
                    block
                }
            } else {
                val childResult = block.children.updateTableBlock(tableBlockId, transform)
                if (childResult.changed) {
                    changed = true
                    block.copy(children = childResult.blocks)
                } else {
                    block
                }
            }
        }
        return BlockListMutation(blocks = updatedBlocks, changed = changed)
    }

    private fun PageTable.withoutColumn(columnId: String): PageTable {
        return copy(
            columns = columns
                .filterNot { column -> column.id == columnId }
                .map { column -> column.withoutColumnReference(columnId) },
            rows = rows.map { row -> row.copy(cells = row.cells - columnId) },
            sort = if (sort.columnId == columnId) PageTableSort() else sort,
            filter = if (filter.columnId == columnId) PageTableFilter() else filter,
            groupByColumnId = groupByColumnId.takeUnless { it == columnId }.orEmpty(),
            viewConfig = viewConfig.withoutColumn(columnId),
        )
    }

    private fun PageTableColumn.withoutColumnReference(columnId: String): PageTableColumn {
        return copy(
            relationTargetTableId = relationTargetTableId.takeUnless { it == columnId }.orEmpty(),
            rollupRelationColumnId = rollupRelationColumnId.takeUnless { it == columnId }.orEmpty(),
            rollupTargetColumnId = rollupTargetColumnId.takeUnless { it == columnId }.orEmpty(),
        )
    }

    private fun PageTableViewConfig.withoutColumn(columnId: String): PageTableViewConfig {
        return copy(
            calendarDateColumnId = calendarDateColumnId.takeUnless { it == columnId }.orEmpty(),
            timelineStartColumnId = timelineStartColumnId.takeUnless { it == columnId }.orEmpty(),
            timelineEndColumnId = timelineEndColumnId.takeUnless { it == columnId }.orEmpty(),
            dashboardMetricColumnId = dashboardMetricColumnId.takeUnless { it == columnId }.orEmpty(),
            dashboardGroupColumnId = dashboardGroupColumnId.takeUnless { it == columnId }.orEmpty(),
        )
    }

    private fun PageTableRow.withCellsForColumns(columns: List<PageTableColumn>): PageTableRow {
        return copy(
            cells = columns.associate { column -> column.id to cells[column.id].orEmpty() },
        )
    }

    private fun <T> List<T>.insertElement(
        element: T,
        targetIndex: Int?,
        idSelector: (T) -> String,
    ): List<T> {
        return insertElementAfter(
            element = element,
            afterElementId = "",
            targetIndex = targetIndex,
            idSelector = idSelector,
        )
    }

    private fun <T> List<T>.insertElementAfter(
        element: T,
        afterElementId: String,
        targetIndex: Int?,
        idSelector: (T) -> String,
    ): List<T> {
        val index = when {
            targetIndex != null -> targetIndex.coerceIn(0, size)
            afterElementId.isNotBlank() -> {
                val afterIndex = indexOfFirst { item -> idSelector(item) == afterElementId }
                if (afterIndex >= 0) afterIndex + 1 else size
            }
            else -> size
        }
        return toMutableList().apply { add(index, element) }
    }

    private fun <T> List<T>.moveElement(
        itemId: String,
        targetIndex: Int,
        idSelector: (T) -> String,
    ): List<T> {
        val currentIndex = indexOfFirst { item -> idSelector(item) == itemId }
        if (currentIndex < 0) return this
        return moveElementAt(currentIndex, targetIndex)
    }

    private fun <T> List<T>.moveElementAt(
        currentIndex: Int,
        targetIndex: Int,
    ): List<T> {
        val mutable = toMutableList()
        val item = mutable.removeAt(currentIndex)
        mutable.add(targetIndex.coerceIn(0, mutable.size), item)
        return mutable
    }
}

private data class BlockListMutation(
    val blocks: List<PageBlock>,
    val changed: Boolean,
)
