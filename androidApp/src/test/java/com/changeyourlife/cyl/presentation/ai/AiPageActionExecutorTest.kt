package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.domain.usecase.ApplyEditorCommandUseCase
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPageActionExecutorTest {
    @Test
    fun executesFormatBlockTextAsRichTextSpanWithUndoCommand() = runBlocking {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "block-note",
                    type = PageBlockType.Text,
                    text = "Jaga ayam setiap pagi",
                ),
            ),
        )
        val page = Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Penjagaan Ayam",
            content = PageBlockCodec.encodeDocument(document),
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 1000,
            deletedAt = null,
        )
        val executor = AiPageActionExecutor(
            pageRepository = FakePageRepository(page, document),
            taskRepository = FakeTaskRepository(),
            reminderRepository = FakeReminderRepository(),
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = executor.executeOnPage(
            page = page,
            title = page.title,
            document = document,
            actions = listOf(
                ChatAction(
                    type = "FORMAT_BLOCK_TEXT",
                    title = "",
                    blockId = "block-note",
                    textToFormat = "ayam",
                    format = "Bold",
                ),
            ),
        )

        assertEquals(
            listOf(PageTextSpan(start = 5, end = 9, bold = true)),
            requireNotNull(result.updatedDocument).blocks.single().richTextSpans,
        )
        assertTrue(result.undoCommands.isNotEmpty())
    }

    @Test
    fun executesAddDeleteAndReorderWithGranularPageRepositoryCalls() = runBlocking {
        val nameColumn = PageTableColumn(id = "column-name", name = "Name")
        val amountColumn = PageTableColumn(
            id = "column-amount",
            name = "Amount",
            type = PageTableColumnType.Number,
        )
        val tableBlock = PageBlock(
            id = "block-table",
            type = PageBlockType.DatabaseTable,
            table = PageTable(
                title = "Budget",
                columns = listOf(nameColumn, amountColumn),
                rows = listOf(
                    PageTableRow(
                        id = "row-food",
                        cells = mapOf(
                            nameColumn.id to "Food",
                            amountColumn.id to "4",
                        ),
                    ),
                    PageTableRow(
                        id = "row-fuel",
                        cells = mapOf(
                            nameColumn.id to "Fuel",
                            amountColumn.id to "5",
                        ),
                    ),
                ),
            ),
        )
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "block-note",
                    type = PageBlockType.Text,
                    text = "Old note",
                ),
                tableBlock,
            ),
        )
        val page = Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget Tracker",
            content = PageBlockCodec.encodeDocument(document),
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 1000,
            deletedAt = null,
        )
        val pageRepository = FakePageRepository(page, document)
        val executor = AiPageActionExecutor(
            pageRepository = pageRepository,
            taskRepository = FakeTaskRepository(),
            reminderRepository = FakeReminderRepository(),
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = executor.executeOnPage(
            page = page,
            title = page.title,
            document = document,
            actions = listOf(
                ChatAction(
                    type = "ADD_BLOCK",
                    title = "AI note",
                    content = "AI note",
                    blockType = "Text",
                    targetIndex = 2,
                ),
                ChatAction(
                    type = "DELETE_BLOCK",
                    title = "Old note",
                    blockId = "block-note",
                ),
                ChatAction(
                    type = "MOVE_TABLE_COLUMN",
                    title = "Amount",
                    tableTitle = "Budget",
                    columnName = "Amount",
                    targetIndex = 1,
                ),
                ChatAction(
                    type = "MOVE_TABLE_ROW",
                    title = "Fuel",
                    tableTitle = "Budget",
                    rowId = "row-fuel",
                    rowTitle = "Fuel",
                    targetIndex = 1,
                ),
            ),
        )

        assertEquals(1, pageRepository.addBlockCalls.size)
        assertEquals(1, pageRepository.deleteBlockCalls.size)
        assertEquals(1, pageRepository.moveTableColumnCalls.size)
        assertEquals(1, pageRepository.moveTableRowCalls.size)
        assertEquals(0, pageRepository.upsertPageCalls)
        assertNull(result.updatedDocument)
        assertEquals("page-1", result.pageLinks.single().pageId)

        val refreshedDocument = PageBlockCodec.decodeDocument(requireNotNull(pageRepository.getPage("page-1")).content)
        assertEquals(listOf("Amount", "Name"), refreshedDocument.table.columns.map { column -> column.name })
        assertEquals(listOf("Fuel", "Food"), refreshedDocument.table.rows.map { row -> row.cells[nameColumn.id] })
        assertEquals(listOf("AI note", ""), refreshedDocument.blocks.map { block -> block.text })
    }

    @Test
    fun addsMalayExpenseRowToExistingTable() = runBlocking {
        val nameColumn = PageTableColumn(id = "column-name", name = "Name")
        val amountColumn = PageTableColumn(
            id = "column-amount",
            name = "Amount",
            type = PageTableColumnType.Number,
        )
        val dateColumn = PageTableColumn(
            id = "column-date",
            name = "Date",
            type = PageTableColumnType.Date,
        )
        val notesColumn = PageTableColumn(id = "column-notes", name = "Notes")
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "block-table",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(
                        title = "Budget",
                        columns = listOf(nameColumn, amountColumn, dateColumn, notesColumn),
                    ),
                ),
            ),
        )
        val page = Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget Tracker",
            content = PageBlockCodec.encodeDocument(document),
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 1000,
            deletedAt = null,
        )
        val pageRepository = FakePageRepository(page, document)
        val executor = AiPageActionExecutor(
            pageRepository = pageRepository,
            taskRepository = FakeTaskRepository(),
            reminderRepository = FakeReminderRepository(),
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = executor.executeOnPage(
            page = page,
            title = page.title,
            document = document,
            actions = listOf(
                ChatAction(
                    type = "ADD_TABLE_ROW",
                    title = "makeup",
                    tableTitle = "Budget",
                    rowTitle = "makeup",
                    cellValues = mapOf(
                        "Name" to "makeup",
                        "Amount" to "29",
                        "Date" to "2026-06-30",
                        "Notes" to "29 ringgit harini makeup",
                    ),
                ),
            ),
        )

        assertEquals(0, result.validationIssues.size)
        val row = requireNotNull(result.updatedDocument).table.rows.single()
        assertEquals("makeup", row.cells[nameColumn.id])
        assertEquals("29", row.cells[amountColumn.id])
        assertEquals("2026-06-30", row.cells[dateColumn.id])
        assertEquals("29 ringgit harini makeup", row.cells[notesColumn.id])
        assertEquals(1, result.undoCommands.size)
        assertEquals(0, result.undoCommands.single().actionIndex)
        assertEquals("ReplaceTable", result.undoCommands.single().commandType)
        assertEquals("block-table", result.undoCommands.single().targetId)
        assertEquals("Budget", result.undoCommands.single().table?.title)
        assertEquals(0, result.undoCommands.single().table?.rows?.size)
    }

    @Test
    fun addsMediaBlockWithAttachmentPayload() = runBlocking {
        val document = PageBlockDocument()
        val page = Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Receipts",
            content = PageBlockCodec.encodeDocument(document),
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 1000,
            deletedAt = null,
        )
        val pageRepository = FakePageRepository(page, document)
        val executor = AiPageActionExecutor(
            pageRepository = pageRepository,
            taskRepository = FakeTaskRepository(),
            reminderRepository = FakeReminderRepository(),
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = executor.executeOnPage(
            page = page,
            title = page.title,
            document = document,
            actions = listOf(
                ChatAction(
                    type = "ADD_BLOCK",
                    title = "Receipt",
                    blockType = "MediaFile",
                    mediaUri = "content://receipts/receipt.png",
                    mediaName = "receipt.png",
                    mediaMimeType = "image/png",
                    mediaSizeBytes = 1234,
                ),
            ),
        )

        assertEquals(emptyList<AiPageActionValidationIssue>(), result.validationIssues)
        assertEquals(1, pageRepository.addBlockCalls.size)
        assertNull(result.updatedDocument)

        val refreshedDocument = PageBlockCodec.decodeDocument(requireNotNull(pageRepository.getPage("page-1")).content)
        val block = refreshedDocument.blocks.single()
        val attachment = block.mediaAttachments.single()
        assertEquals(PageBlockType.MediaFile, block.type)
        assertEquals("Receipt", block.text)
        assertEquals("content://receipts/receipt.png", attachment.uri)
        assertEquals("receipt.png", attachment.name)
        assertEquals("image/png", attachment.mimeType)
        assertEquals(1234L, attachment.sizeBytes)
    }

    @Test
    fun updatesFormulaColumnFromValuePayload() = runBlocking {
        val amountColumn = PageTableColumn(
            id = "column-amount",
            name = "Amount",
            type = PageTableColumnType.Number,
        )
        val totalColumn = PageTableColumn(
            id = "column-total",
            name = "Total",
            type = PageTableColumnType.Formula,
        )
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "block-table",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(
                        title = "Budget",
                        columns = listOf(amountColumn, totalColumn),
                        rows = listOf(
                            PageTableRow(
                                id = "row-food",
                                cells = mapOf(amountColumn.id to "4"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val page = Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget Tracker",
            content = PageBlockCodec.encodeDocument(document),
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 1000,
            deletedAt = null,
        )
        val executor = AiPageActionExecutor(
            pageRepository = FakePageRepository(page, document),
            taskRepository = FakeTaskRepository(),
            reminderRepository = FakeReminderRepository(),
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = executor.executeOnPage(
            page = page,
            title = page.title,
            document = document,
            actions = listOf(
                ChatAction(
                    type = "UPDATE_FORMULA_COLUMN",
                    title = "Total",
                    tableTitle = "Budget",
                    columnName = "Total",
                    value = "{Amount} * 2",
                ),
            ),
        )

        assertEquals(emptyList<AiPageActionValidationIssue>(), result.validationIssues)
        val updatedTotalColumn = requireNotNull(result.updatedDocument)
            .table
            .columns
            .single { column -> column.id == totalColumn.id }
        assertEquals("{Amount} * 2", updatedTotalColumn.formula)
    }

    @Test
    fun rejectsMissingSemanticTargetsBeforeMutatingPage() = runBlocking {
        val nameColumn = PageTableColumn(id = "column-name", name = "Name")
        val dateColumn = PageTableColumn(
            id = "column-date",
            name = "Date",
            type = PageTableColumnType.Date,
        )
        val tableBlock = PageBlock(
            id = "block-table",
            type = PageBlockType.DatabaseTable,
            table = PageTable(
                title = "Budget",
                columns = listOf(nameColumn, dateColumn),
                rows = listOf(
                    PageTableRow(
                        id = "row-food",
                        cells = mapOf(
                            nameColumn.id to "Food",
                            dateColumn.id to "2026-06-30",
                        ),
                    ),
                ),
            ),
        )
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "block-note",
                    type = PageBlockType.Text,
                    text = "Old note",
                ),
                tableBlock,
            ),
        )
        val page = Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget Tracker",
            content = PageBlockCodec.encodeDocument(document),
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 1000,
            deletedAt = null,
        )
        val pageRepository = FakePageRepository(page, document)
        val executor = AiPageActionExecutor(
            pageRepository = pageRepository,
            taskRepository = FakeTaskRepository(),
            reminderRepository = FakeReminderRepository(),
            applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
        )

        val result = executor.executeOnPage(
            page = page,
            title = page.title,
            document = document,
            actions = listOf(
                ChatAction(
                    type = "MOVE_TABLE_COLUMN",
                    title = "Missing amount",
                    tableTitle = "Budget",
                    columnName = "Missing amount",
                    targetIndex = 1,
                ),
                ChatAction(
                    type = "UPDATE_BLOCK",
                    title = "Missing block",
                    blockText = "Missing block",
                    content = "New note",
                ),
                ChatAction(
                    type = "SET_TABLE_VIEW_CONFIG",
                    title = "Budget dashboard",
                    tableTitle = "Budget",
                    dashboardMetricColumnName = "Missing amount",
                ),
                ChatAction(
                    type = "UPDATE_FORMULA_COLUMN",
                    title = "Name",
                    tableTitle = "Budget",
                    columnName = "Name",
                    formula = "{Amount} + 1",
                ),
                ChatAction(
                    type = "UPDATE_FORMULA_COLUMN",
                    title = "Name",
                    tableTitle = "Budget",
                    columnName = "Name",
                    formula = "{Date} +",
                ),
                ChatAction(
                    type = "UPDATE_FORMULA_COLUMN",
                    title = "Name",
                    tableTitle = "Budget",
                    columnName = "Name",
                    formula = "{Name} + 1",
                ),
                ChatAction(
                    type = "UPDATE_RELATION_COLUMN",
                    title = "Name",
                    tableTitle = "Budget",
                    columnName = "Name",
                    relationTargetTableTitle = "Projects",
                ),
                ChatAction(
                    type = "UPDATE_ROLLUP_COLUMN",
                    title = "Name",
                    tableTitle = "Budget",
                    columnName = "Name",
                    rollupTargetColumnName = "Amount",
                ),
                ChatAction(
                    type = "UPDATE_TABLE_CELL",
                    title = "Date",
                    tableTitle = "Budget",
                    rowTitle = "Food",
                    columnName = "Date",
                    value = "tomorrow",
                ),
                ChatAction(
                    type = "ADD_TABLE_ROW",
                    title = "Fuel",
                    tableTitle = "Budget",
                    cellValues = mapOf(
                        "Name" to "Fuel",
                        "Date" to "soon",
                    ),
                ),
                ChatAction(
                    type = "CREATE_REMINDER",
                    title = "Call Ali",
                ),
                ChatAction(
                    type = "ADD_BLOCK",
                    title = "Receipt",
                    blockType = "MediaFile",
                ),
                ChatAction(
                    type = "DELETE_ROW_PAGE_BLOCK",
                    title = "Missing row note",
                    tableTitle = "Budget",
                    rowTitle = "Food",
                    blockText = "Missing row note",
                ),
            ),
        )

        assertEquals(13, result.validationIssues.size)
        assertEquals("columnName", result.validationIssues[0].field)
        assertEquals("blockText", result.validationIssues[1].field)
        assertEquals("dashboardMetricColumnName", result.validationIssues[2].field)
        assertEquals("formula", result.validationIssues[3].field)
        assertEquals("formula", result.validationIssues[4].field)
        assertEquals("formula", result.validationIssues[5].field)
        assertEquals("relationTargetTableTitle", result.validationIssues[6].field)
        assertEquals("relationTargetTableTitle", result.validationIssues[7].field)
        assertEquals("value", result.validationIssues[8].field)
        assertEquals("cellValues.Date", result.validationIssues[9].field)
        assertEquals("cellValues.date", result.validationIssues[10].field)
        assertEquals("mediaUri", result.validationIssues[11].field)
        assertEquals("rowBlockId", result.validationIssues[12].field)
        assertEquals(0, pageRepository.moveTableColumnCalls.size)
        assertEquals(0, pageRepository.upsertPageCalls)
        assertNull(result.updatedDocument)
        assertEquals(
            PageBlockCodec.encodeDocument(document),
            requireNotNull(pageRepository.getPage("page-1")).content,
        )
    }

    private val PageBlockDocument.table: PageTable
        get() = blocks.single { block -> block.type == PageBlockType.DatabaseTable }.table

    private class FakePageRepository(
        private var page: Page,
        private var document: PageBlockDocument,
    ) : PageRepository {
        val addBlockCalls = mutableListOf<String>()
        val deleteBlockCalls = mutableListOf<String>()
        val moveTableColumnCalls = mutableListOf<MoveCall>()
        val moveTableRowCalls = mutableListOf<MoveCall>()
        var upsertPageCalls = 0

        override fun observePages(workspaceId: String): Flow<List<Page>> = flowOf(listOf(page))

        override fun observeChildPages(parentPageId: String): Flow<List<Page>> = flowOf(emptyList())

        override fun observeRecentPages(limit: Int): Flow<List<Page>> = flowOf(listOf(page))

        override fun observeRecentPages(workspaceId: String, limit: Int): Flow<List<Page>> = flowOf(listOf(page))

        override fun observeDeletedPages(workspaceId: String): Flow<List<Page>> = flowOf(emptyList())

        override fun observePage(pageId: String): Flow<Page?> = flowOf(page.takeIf { it.id == pageId })

        override fun observePageSyncState(pageId: String): Flow<PageSyncState> = flowOf(PageSyncState())

        override fun observePageCount(): Flow<Int> = flowOf(1)

        override fun observePageCount(workspaceId: String): Flow<Int> = flowOf(1)

        override suspend fun getPage(pageId: String): Page? = page.takeIf { it.id == pageId }

        override suspend fun upsertPage(page: Page) {
            upsertPageCalls += 1
            this.page = page
            document = PageBlockCodec.decodeDocument(page.content)
        }

        override suspend fun updateBlockText(pageId: String, blockId: String, text: String): Boolean = false

        override suspend fun updateBlock(pageId: String, block: PageBlock): Boolean = false

        override suspend fun updatePropertyValue(
            pageId: String,
            propertyId: String,
            propertyName: String,
            value: String,
        ): Boolean = false

        override suspend fun updateTableCellValue(
            pageId: String,
            rowId: String,
            columnId: String,
            value: String,
        ): Boolean = false

        override suspend fun updateTable(pageId: String, tableBlockId: String, table: PageTable): Boolean = false

        override suspend fun updateTableColumn(
            pageId: String,
            tableBlockId: String,
            column: PageTableColumn,
        ): Boolean = false

        override suspend fun addBlock(
            pageId: String,
            block: PageBlock,
            parentBlockId: String,
            afterBlockId: String,
            targetIndex: Int?,
        ): Boolean {
            addBlockCalls += block.id
            val blocks = document.blocks.toMutableList()
            blocks.add(targetIndex?.coerceIn(0, blocks.size) ?: blocks.size, block)
            save(document.copy(blocks = blocks))
            return true
        }

        override suspend fun deleteBlock(pageId: String, blockId: String): Boolean {
            deleteBlockCalls += blockId
            val blocks = document.blocks.filterNot { block -> block.id == blockId }
            save(document.copy(blocks = blocks))
            return true
        }

        override suspend fun moveBlock(pageId: String, blockId: String, targetIndex: Int): Boolean = false

        override suspend fun addProperty(pageId: String, property: PageProperty, targetIndex: Int?): Boolean = false

        override suspend fun deleteProperty(pageId: String, propertyId: String): Boolean = false

        override suspend fun moveProperty(pageId: String, propertyId: String, targetIndex: Int): Boolean = false

        override suspend fun addTableColumn(
            pageId: String,
            tableBlockId: String,
            column: PageTableColumn,
            cellValues: Map<String, String>,
            targetIndex: Int?,
        ): Boolean = false

        override suspend fun deleteTableColumn(pageId: String, tableBlockId: String, columnId: String): Boolean = false

        override suspend fun moveTableColumn(
            pageId: String,
            tableBlockId: String,
            columnId: String,
            targetIndex: Int,
        ): Boolean {
            moveTableColumnCalls += MoveCall(tableBlockId, columnId, targetIndex)
            updateTableBlock(tableBlockId) { table ->
                table.copy(columns = moveItemForTest(table.columns, columnId, targetIndex) { column -> column.id })
            }
            return true
        }

        override suspend fun addTableRow(
            pageId: String,
            tableBlockId: String,
            row: PageTableRow,
            targetIndex: Int?,
        ): Boolean = false

        override suspend fun updateTableRow(pageId: String, tableBlockId: String, row: PageTableRow): Boolean = false

        override suspend fun deleteTableRow(pageId: String, tableBlockId: String, rowId: String): Boolean = false

        override suspend fun moveTableRow(
            pageId: String,
            tableBlockId: String,
            rowId: String,
            targetIndex: Int,
        ): Boolean {
            moveTableRowCalls += MoveCall(tableBlockId, rowId, targetIndex)
            updateTableBlock(tableBlockId) { table ->
                table.copy(rows = moveItemForTest(table.rows, rowId, targetIndex) { row -> row.id })
            }
            return true
        }

        override suspend fun createPage(
            workspaceId: String,
            title: String,
            content: String,
            parentPageId: String?,
        ): Page = error("Not used in this test")

        override suspend fun deletePage(pageId: String) = Unit

        override suspend fun restorePage(pageId: String) = Unit

        override suspend fun deletePagePermanently(pageId: String) = Unit

        override suspend fun keepLocalPageConflict(pageId: String) = Unit

        override suspend fun useRemotePageConflict(pageId: String) = Unit

        private fun updateTableBlock(tableBlockId: String, update: (PageTable) -> PageTable) {
            save(
                document.copy(
                    blocks = document.blocks.map { block ->
                        if (block.id == tableBlockId) block.copy(table = update(block.table)) else block
                    },
                ),
            )
        }

        private fun save(nextDocument: PageBlockDocument) {
            document = nextDocument
            page = page.copy(content = PageBlockCodec.encodeDocument(document))
        }

        private inline fun <T> moveItemForTest(
            items: List<T>,
            itemId: String,
            targetIndex: Int,
            idSelector: (T) -> String,
        ): List<T> {
            val fromIndex = items.indexOfFirst { item -> idSelector(item) == itemId }
            if (fromIndex == -1) return items
            val item = items[fromIndex]
            val result = items.toMutableList().also { mutable -> mutable.removeAt(fromIndex) }
            result.add(targetIndex.coerceIn(0, result.size), item)
            return result
        }
    }

    private data class MoveCall(
        val tableBlockId: String,
        val itemId: String,
        val targetIndex: Int,
    )

    private class FakeTaskRepository : TaskRepository {
        override fun observeOpenTasks(): Flow<List<TaskItem>> = flowOf(emptyList())

        override fun observeOpenTasks(workspaceId: String): Flow<List<TaskItem>> = flowOf(emptyList())

        override fun observeTask(taskId: String): Flow<TaskItem?> = flowOf(null)

        override fun observeOpenTaskCount(): Flow<Int> = flowOf(0)

        override fun observeOpenTaskCount(workspaceId: String): Flow<Int> = flowOf(0)

        override suspend fun getTask(taskId: String): TaskItem? = null

        override suspend fun upsertTask(task: TaskItem) = Unit

        override suspend fun createTask(
            workspaceId: String,
            title: String,
            notes: String,
            dueAt: Long?,
            priority: Int,
            pageId: String?,
        ): TaskItem = error("Not used in this test")
    }

    private class FakeReminderRepository : ReminderRepository {
        override fun observePendingReminders(): Flow<List<Reminder>> = flowOf(emptyList())

        override fun observePendingReminders(workspaceId: String): Flow<List<Reminder>> = flowOf(emptyList())

        override fun observePendingReminderCount(): Flow<Int> = flowOf(0)

        override fun observePendingReminderCount(workspaceId: String): Flow<Int> = flowOf(0)

        override suspend fun getReminderForTask(taskId: String): Reminder? = null

        override suspend fun upsertReminder(reminder: Reminder) = Unit

        override suspend fun createReminder(
            workspaceId: String,
            title: String,
            remindAt: Long,
            pageId: String?,
            taskId: String?,
            id: String?,
        ): Reminder = error("Not used in this test")
    }

}
