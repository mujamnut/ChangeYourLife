package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.AiActionLog
import com.changeyourlife.cyl.domain.model.AiActionUndoState
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.deleteCreatedPageUndo
import com.changeyourlife.cyl.domain.model.restorePageSnapshotsUndo
import com.changeyourlife.cyl.domain.repository.AiActionLogRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyAiActionUndoUseCaseTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun appliesSavedTableUndoPayloadAndMarksLogApplied() = runBlocking {
        val nameColumn = PageTableColumn(id = "column-name", name = "Name")
        val amountColumn = PageTableColumn(
            id = "column-amount",
            name = "Amount",
            type = PageTableColumnType.Number,
        )
        val originalTable = PageTable(
            title = "Budget",
            columns = listOf(nameColumn, amountColumn),
            rows = emptyList(),
        )
        val currentTable = originalTable.copy(
            rows = listOf(
                PageTableRow(
                    id = "row-makeup",
                    cells = mapOf(nameColumn.id to "makeup", amountColumn.id to "29"),
                ),
            ),
        )
        val page = pageWithTable(currentTable)
        val pageRepository = FakePageRepository(page)
        val actionLogRepository = FakeAiActionLogRepository(
            log = actionLog(
                undoCommandsJson = json.encodeToString(
                    listOf(
                        AiUndoCommandSummary(
                            actionIndex = 0,
                            commandType = "ReplaceTable",
                            targetType = "Table",
                            targetId = "block-table",
                            blockId = "block-table",
                            table = originalTable,
                        ),
                    ),
                ),
            ),
        )
        val useCase = ApplyAiActionUndoUseCase(
            aiActionLogRepository = actionLogRepository,
            pageRepository = pageRepository,
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = useCase(auditId = "audit-1", pageId = "page-1")

        assertTrue(result.changed)
        assertEquals("Undone 1 AI change.", result.message)
        val updatedPage = requireNotNull(pageRepository.getPage("page-1"))
        val updatedDocument = PageContentCodec.decodeDocument(updatedPage.content)
        assertEquals(emptyList<PageTableRow>(), updatedDocument.blocks.single().table.rows)
        assertEquals(AiActionUndoState.Applied, requireNotNull(actionLogRepository.getByAuditId("audit-1")).undoState)
    }

    @Test
    fun rejectsAlreadyAppliedUndoLog() = runBlocking {
        val useCase = ApplyAiActionUndoUseCase(
            aiActionLogRepository = FakeAiActionLogRepository(
                actionLog(undoState = AiActionUndoState.Applied),
            ),
            pageRepository = FakePageRepository(pageWithTable(PageTable(title = "Budget"))),
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = useCase(auditId = "audit-1", pageId = "page-1")

        assertEquals(false, result.changed)
        assertEquals("That AI action has already been undone.", result.message)
    }

    @Test
    fun restoresPermanentlyDeletedPageFromSavedSnapshot() = runBlocking {
        val deletedPage = pageWithTable(PageTable(title = "Budget")).copy(
            title = "Original title",
            parentPageId = "parent-1",
            deletedAt = 3000L,
            revision = 7L,
        )
        val pageRepository = FakePageRepository(initialPage = null)
        val actionLogRepository = FakeAiActionLogRepository(
            actionLog(
                undoCommandsJson = json.encodeToString(
                    listOf(
                        restorePageSnapshotsUndo(
                            actionIndex = 0,
                            pages = listOf(deletedPage),
                        ),
                    ),
                ),
            ),
        )
        val useCase = ApplyAiActionUndoUseCase(
            aiActionLogRepository = actionLogRepository,
            pageRepository = pageRepository,
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = useCase(auditId = "audit-1", pageId = "")

        assertTrue(result.changed)
        assertEquals(deletedPage, pageRepository.getPage(deletedPage.id))
        assertEquals(AiActionUndoState.Applied, requireNotNull(actionLogRepository.getByAuditId("audit-1")).undoState)
    }

    @Test
    fun removesPageCreatedByAi() = runBlocking {
        val createdPage = pageWithTable(PageTable(title = "Budget"))
        val pageRepository = FakePageRepository(createdPage)
        val actionLogRepository = FakeAiActionLogRepository(
            actionLog(
                undoCommandsJson = json.encodeToString(
                    listOf(
                        deleteCreatedPageUndo(
                            actionIndex = 0,
                            pageId = createdPage.id,
                        ),
                    ),
                ),
            ),
        )
        val useCase = ApplyAiActionUndoUseCase(
            aiActionLogRepository = actionLogRepository,
            pageRepository = pageRepository,
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = useCase(auditId = "audit-1", pageId = "")

        assertTrue(result.changed)
        assertEquals(null, pageRepository.getPage(createdPage.id))
        assertEquals(listOf(createdPage.id), pageRepository.permanentlyDeletedPageIds)
    }

    @Test
    fun restoresPreviousPageTitleAndParentSnapshot() = runBlocking {
        val previousPage = pageWithTable(PageTable(title = "Budget")).copy(
            title = "Before",
            parentPageId = "parent-before",
        )
        val currentPage = previousPage.copy(
            title = "After",
            parentPageId = "parent-after",
            updatedAt = previousPage.updatedAt + 100L,
        )
        val pageRepository = FakePageRepository(currentPage)
        val actionLogRepository = FakeAiActionLogRepository(
            actionLog(
                undoCommandsJson = json.encodeToString(
                    listOf(
                        restorePageSnapshotsUndo(
                            actionIndex = 0,
                            pages = listOf(previousPage),
                        ),
                    ),
                ),
            ),
        )
        val useCase = ApplyAiActionUndoUseCase(
            aiActionLogRepository = actionLogRepository,
            pageRepository = pageRepository,
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = useCase(auditId = "audit-1", pageId = currentPage.id)

        assertTrue(result.changed)
        assertEquals(previousPage, pageRepository.getPage(previousPage.id))
    }

    private fun pageWithTable(table: PageTable): Page {
        return Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget Tracker",
            content = PageContentCodec.encodeDocument(
                PageBlockDocument(
                    blocks = listOf(
                        PageBlock(
                            id = "block-table",
                            type = PageBlockType.DatabaseTable,
                            table = table,
                        ),
                    ),
                ),
            ),
            sortOrder = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            deletedAt = null,
        )
    }

    private fun actionLog(
        undoCommandsJson: String = "[]",
        undoState: String = AiActionUndoState.Available,
    ): AiActionLog {
        return AiActionLog(
            auditId = "audit-1",
            requestMessageId = "request-1",
            responseMessageId = "response-1",
            sessionId = "session-1",
            workspaceId = "workspace-1",
            mode = "Edit",
            provider = "openrouter",
            model = "openai/gpt-oss-20b:free",
            schemaName = "CYL_ACTION_SCHEMA",
            schemaVersion = 2,
            proposedActionsJson = "[]",
            executedActionsJson = "[]",
            validationIssuesJson = "[]",
            executionMessagesJson = "[]",
            undoCommandsJson = undoCommandsJson,
            undoState = undoState,
            createdAt = 2000L,
        )
    }

    private class FakeAiActionLogRepository(
        log: AiActionLog,
    ) : AiActionLogRepository {
        private val logs = linkedMapOf(log.auditId to log)

        override suspend fun upsert(log: AiActionLog) {
            logs[log.auditId] = log
        }

        override suspend fun getByAuditId(auditId: String): AiActionLog? {
            return logs[auditId]
        }

        override fun observeBySession(sessionId: String): Flow<List<AiActionLog>> {
            return flowOf(logs.values.filter { log -> log.sessionId == sessionId })
        }
    }

    private class FakePageRepository(
        initialPage: Page?,
    ) : PageRepository {
        private val pages = linkedMapOf<String, Page>().apply {
            initialPage?.let { page -> put(page.id, page) }
        }
        val permanentlyDeletedPageIds = mutableListOf<String>()

        override fun observePages(workspaceId: String): Flow<List<Page>> =
            flowOf(pages.values.filter { page -> page.workspaceId == workspaceId && page.deletedAt == null })
        override fun observeChildPages(parentPageId: String): Flow<List<Page>> = flowOf(emptyList())
        override fun observeRecentPages(limit: Int): Flow<List<Page>> = flowOf(pages.values.take(limit))
        override fun observeRecentPages(workspaceId: String, limit: Int): Flow<List<Page>> =
            flowOf(pages.values.filter { page -> page.workspaceId == workspaceId }.take(limit))
        override fun observeDeletedPages(workspaceId: String): Flow<List<Page>> =
            flowOf(pages.values.filter { page -> page.workspaceId == workspaceId && page.deletedAt != null })
        override fun observePage(pageId: String): Flow<Page?> = flowOf(pages[pageId])
        override fun observePageSyncState(pageId: String): Flow<PageSyncState> = flowOf(PageSyncState())
        override fun observePageCount(): Flow<Int> = flowOf(pages.size)
        override fun observePageCount(workspaceId: String): Flow<Int> =
            flowOf(pages.values.count { page -> page.workspaceId == workspaceId })
        override suspend fun getPage(pageId: String): Page? = pages[pageId]
        override suspend fun upsertPage(page: Page) {
            pages[page.id] = page
        }

        override suspend fun updateBlockText(pageId: String, blockId: String, text: String): Boolean = false
        override suspend fun updateBlock(pageId: String, block: PageBlock): Boolean = false
        override suspend fun updatePropertyValue(pageId: String, propertyId: String, propertyName: String, value: String): Boolean = false
        override suspend fun updateTableCellValue(pageId: String, rowId: String, columnId: String, value: String): Boolean = false
        override suspend fun updateTable(pageId: String, tableBlockId: String, table: PageTable): Boolean = false
        override suspend fun updateTableColumn(pageId: String, tableBlockId: String, column: PageTableColumn): Boolean = false
        override suspend fun addBlock(pageId: String, block: PageBlock, parentBlockId: String, afterBlockId: String, targetIndex: Int?): Boolean = false
        override suspend fun deleteBlock(pageId: String, blockId: String): Boolean = false
        override suspend fun moveBlock(pageId: String, blockId: String, targetIndex: Int): Boolean = false
        override suspend fun addProperty(pageId: String, property: PageProperty, targetIndex: Int?): Boolean = false
        override suspend fun deleteProperty(pageId: String, propertyId: String): Boolean = false
        override suspend fun moveProperty(pageId: String, propertyId: String, targetIndex: Int): Boolean = false
        override suspend fun addTableColumn(pageId: String, tableBlockId: String, column: PageTableColumn, cellValues: Map<String, String>, targetIndex: Int?): Boolean = false
        override suspend fun deleteTableColumn(pageId: String, tableBlockId: String, columnId: String): Boolean = false
        override suspend fun moveTableColumn(pageId: String, tableBlockId: String, columnId: String, targetIndex: Int): Boolean = false
        override suspend fun addTableRow(pageId: String, tableBlockId: String, row: PageTableRow, targetIndex: Int?): Boolean = false
        override suspend fun updateTableRow(pageId: String, tableBlockId: String, row: PageTableRow): Boolean = false
        override suspend fun deleteTableRow(pageId: String, tableBlockId: String, rowId: String): Boolean = false
        override suspend fun moveTableRow(pageId: String, tableBlockId: String, rowId: String, targetIndex: Int): Boolean = false
        override suspend fun createPage(workspaceId: String, title: String, content: String, parentPageId: String?): Page = error("Not used")
        override suspend fun deletePage(pageId: String) {
            pages[pageId]?.let { page -> pages[pageId] = page.copy(deletedAt = 1L) }
        }
        override suspend fun restorePage(pageId: String) {
            pages[pageId]?.let { page -> pages[pageId] = page.copy(deletedAt = null) }
        }
        override suspend fun deletePagePermanently(pageId: String) {
            pages.remove(pageId)
            permanentlyDeletedPageIds += pageId
        }
        override suspend fun keepLocalPageConflict(pageId: String) = Unit
        override suspend fun useRemotePageConflict(pageId: String) = Unit
    }
}
