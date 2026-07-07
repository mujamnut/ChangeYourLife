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
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatTableColumn
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.domain.usecase.ApplyEditorCommandUseCase
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiActionExecutionUseCaseTest {
    @Test
    fun createsHomeScopedTablePage() = runBlocking {
        val pageRepository = FakePageRepository()
        val useCase = useCase(pageRepository)

        val result = useCase.execute(
            workspaceId = "workspace-1",
            scopedTargetPage = null,
            actions = listOf(
                ChatAction(
                    type = "CREATE_TABLE",
                    title = "",
                    tableTitle = "Budget",
                    tableColumns = listOf(
                        ChatTableColumn(name = "Item"),
                        ChatTableColumn(name = "Amount", type = "Number"),
                    ),
                    tableRows = listOf(mapOf("Item" to "Food", "Amount" to "4")),
                ),
            ),
        )

        assertEquals(listOf("Done: Created page Budget"), result.messages)
        assertEquals("Budget", pageRepository.createdPages.single().title)
        assertEquals("Budget", result.pageLinks.single().title)
        val document = PageBlockCodec.decodeDocument(pageRepository.createdPages.single().content)
        val table = document.blocks.single().table
        assertEquals("Budget", table.title)
        assertEquals(listOf("Item", "Amount"), table.columns.map { column -> column.name })
        assertEquals("4", table.rows.single().cells[table.columns[1].id])
    }

    @Test
    fun createsHomeScopedPageWithDatabaseDropdownOptions() = runBlocking {
        val pageRepository = FakePageRepository()
        val useCase = useCase(pageRepository)

        val result = useCase.execute(
            workspaceId = "workspace-1",
            scopedTargetPage = null,
            actions = listOf(
                ChatAction(
                    type = "CREATE_PAGE",
                    title = "July Monthly Expenses",
                    tableTitle = "Monthly Expenses",
                    tableColumns = listOf(
                        ChatTableColumn(
                            name = "Category",
                            type = "Select",
                            options = listOf("Food", "Fuel", "Makeup", "Transport"),
                        ),
                        ChatTableColumn(
                            name = "Status",
                            type = "Status",
                            options = listOf("Planned", "Paid"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(listOf("Done: Created page July Monthly Expenses"), result.messages)
        val document = PageBlockCodec.decodeDocument(pageRepository.createdPages.single().content)
        val table = document.blocks.single().table
        assertEquals("Monthly Expenses", table.title)
        assertEquals(listOf("Category", "Status"), table.columns.map { column -> column.name })
        assertEquals(PageTableColumnType.Select, table.columns[0].type)
        assertEquals(listOf("Food", "Fuel", "Makeup", "Transport"), table.columns[0].config.options.map { it.name })
        assertEquals(PageTableColumnType.Status, table.columns[1].type)
        assertEquals(listOf("Planned", "Paid"), table.columns[1].config.options.map { it.name })
    }

    @Test
    fun executesPageScopedActionAndPersistsUpdatedDocument() = runBlocking {
        val document = PageBlockDocument(blocks = listOf(PageBlock(id = "note", type = PageBlockType.Text, text = "Old")))
        val page = page(document)
        val pageRepository = FakePageRepository(page)
        val useCase = useCase(pageRepository)

        val result = useCase.execute(
            workspaceId = "workspace-1",
            scopedTargetPage = page,
            actions = listOf(
                ChatAction(
                    type = "UPDATE_BLOCK",
                    title = "Old",
                    blockId = "note",
                    content = "New",
                ),
            ),
        )

        assertEquals(listOf("Done: Updated block"), result.messages)
        val updated = PageBlockCodec.decodeDocument(requireNotNull(pageRepository.getPage("page-1")).content)
        assertEquals("New", updated.blocks.single().text)
    }

    @Test
    fun pageScopedActionWithoutTargetReturnsValidationIssue() = runBlocking {
        val useCase = useCase(FakePageRepository())

        val result = useCase.execute(
            workspaceId = "workspace-1",
            scopedTargetPage = null,
            actions = listOf(ChatAction(type = "ADD_BLOCK", title = "Note", content = "Note")),
        )

        assertTrue(result.messages.isEmpty())
        assertEquals(1, result.validationIssues.size)
        assertEquals("target_page_required", result.validationIssues.single().code)
    }

    private fun useCase(pageRepository: FakePageRepository): AiActionExecutionUseCase {
        return AiActionExecutionUseCase(
            pageRepository = pageRepository,
            aiPageActionExecutor = AiPageActionExecutor(
                pageRepository = pageRepository,
                taskRepository = FakeTaskRepository(),
                reminderRepository = FakeReminderRepository(),
                applyEditorCommandUseCase = ApplyEditorCommandUseCase(),
            ),
        )
    }

    private fun page(document: PageBlockDocument): Page {
        return Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Target",
            content = PageBlockCodec.encodeDocument(document),
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 1000,
            deletedAt = null,
        )
    }

    private class FakePageRepository(
        initialPage: Page? = null,
    ) : PageRepository {
        private var page: Page? = initialPage
        val createdPages = mutableListOf<Page>()

        override fun observePages(workspaceId: String): Flow<List<Page>> = flowOf(page?.let(::listOf).orEmpty())

        override fun observeChildPages(parentPageId: String): Flow<List<Page>> = flowOf(emptyList())

        override fun observeRecentPages(limit: Int): Flow<List<Page>> = flowOf(page?.let(::listOf).orEmpty())

        override fun observeRecentPages(workspaceId: String, limit: Int): Flow<List<Page>> = flowOf(page?.let(::listOf).orEmpty())

        override fun observeDeletedPages(workspaceId: String): Flow<List<Page>> = flowOf(emptyList())

        override fun observePage(pageId: String): Flow<Page?> = flowOf(page.takeIf { it?.id == pageId })

        override fun observePageSyncState(pageId: String): Flow<PageSyncState> = flowOf(PageSyncState())

        override fun observePageCount(): Flow<Int> = flowOf(if (page == null) 0 else 1)

        override fun observePageCount(workspaceId: String): Flow<Int> = observePageCount()

        override suspend fun getPage(pageId: String): Page? = page.takeIf { it?.id == pageId }

        override suspend fun upsertPage(page: Page) {
            this.page = page
        }

        override suspend fun updateBlockText(pageId: String, blockId: String, text: String): Boolean {
            val current = page ?: return false
            val document = PageBlockCodec.decodeDocument(current.content)
            val updated = document.copy(
                blocks = document.blocks.map { block ->
                    if (block.id == blockId) block.copy(text = text) else block
                },
            )
            page = current.copy(content = PageBlockCodec.encodeDocument(updated))
            return true
        }

        override suspend fun updateBlock(pageId: String, block: PageBlock): Boolean = false

        override suspend fun updatePropertyValue(
            pageId: String,
            propertyId: String,
            propertyName: String,
            value: String,
        ): Boolean = false

        override suspend fun updateTableCellValue(pageId: String, rowId: String, columnId: String, value: String): Boolean = false

        override suspend fun updateTable(pageId: String, tableBlockId: String, table: PageTable): Boolean = false

        override suspend fun updateTableColumn(pageId: String, tableBlockId: String, column: PageTableColumn): Boolean = false

        override suspend fun addBlock(
            pageId: String,
            block: PageBlock,
            parentBlockId: String,
            afterBlockId: String,
            targetIndex: Int?,
        ): Boolean = false

        override suspend fun deleteBlock(pageId: String, blockId: String): Boolean = false

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

        override suspend fun moveTableColumn(pageId: String, tableBlockId: String, columnId: String, targetIndex: Int): Boolean = false

        override suspend fun addTableRow(pageId: String, tableBlockId: String, row: PageTableRow, targetIndex: Int?): Boolean = false

        override suspend fun updateTableRow(pageId: String, tableBlockId: String, row: PageTableRow): Boolean = false

        override suspend fun deleteTableRow(pageId: String, tableBlockId: String, rowId: String): Boolean = false

        override suspend fun moveTableRow(pageId: String, tableBlockId: String, rowId: String, targetIndex: Int): Boolean = false

        override suspend fun createPage(
            workspaceId: String,
            title: String,
            content: String,
            parentPageId: String?,
        ): Page {
            val created = Page(
                id = "created-${createdPages.size + 1}",
                workspaceId = workspaceId,
                parentPageId = parentPageId,
                title = title,
                content = content,
                sortOrder = 0,
                createdAt = 1000,
                updatedAt = 1000,
                deletedAt = null,
            )
            createdPages += created
            page = created
            return created
        }

        override suspend fun deletePage(pageId: String) = Unit

        override suspend fun restorePage(pageId: String) = Unit

        override suspend fun deletePagePermanently(pageId: String) = Unit

        override suspend fun keepLocalPageConflict(pageId: String) = Unit

        override suspend fun useRemotePageConflict(pageId: String) = Unit
    }

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

        override suspend fun reschedulePendingReminders() = Unit

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
