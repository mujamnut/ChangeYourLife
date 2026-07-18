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
import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
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
        assertEquals("Food", table.rows.single().cellValues[table.columns[0].id]?.text)
        assertEquals("4", table.rows.single().cellValues[table.columns[1].id]?.number)
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

    @Test
    fun executeCandidatesPreservesOriginalIndexForValidationIssue() = runBlocking {
        val useCase = useCase(FakePageRepository())

        val result = useCase.executeCandidates(
            workspaceId = "workspace-1",
            scopedTargetPage = null,
            actions = listOf(
                AiActionExecutionCandidate(
                    originalIndex = 4,
                    action = ChatAction(type = "ADD_BLOCK", title = "Note", content = "Note"),
                ),
            ),
        )

        assertEquals(1, result.validationIssues.size)
        assertEquals(4, result.validationIssues.single().actionIndex)
        assertEquals("target_page_required", result.validationIssues.single().code)
    }

    @Test
    fun executesPageActionByExactTargetTitleWhenNoScopedPage() = runBlocking {
        val budget = page(PageBlockDocument(), id = "page-budget", title = "Budget")
        val budgetTracker = page(PageBlockDocument(), id = "page-budget-tracker", title = "Budget Tracker")
        val pageRepository = FakePageRepository(budget, budgetTracker)
        val useCase = useCase(pageRepository)

        val result = useCase.execute(
            workspaceId = "workspace-1",
            scopedTargetPage = null,
            actions = listOf(
                ChatAction(
                    type = "ADD_BLOCK",
                    title = "",
                    targetTitle = "Budget Tracker",
                    content = "Only tracker",
                ),
            ),
        )

        assertEquals(emptyList<ChatActionValidationMetadata>(), result.validationIssues)
        val untouchedBudget = PageBlockCodec.decodeDocument(requireNotNull(pageRepository.getPage("page-budget")).content)
        val updatedTracker = PageBlockCodec.decodeDocument(requireNotNull(pageRepository.getPage("page-budget-tracker")).content)
        assertEquals(emptyList<PageBlock>(), untouchedBudget.meaningfulBlocks())
        assertEquals(listOf("Only tracker"), updatedTracker.meaningfulBlocks().map { block -> block.text })
    }

    @Test
    fun explicitTargetTitleOverridesCurrentlyOpenPage() = runBlocking {
        val currentPage = page(PageBlockDocument(), id = "page-current", title = "Current")
        val otherPage = page(PageBlockDocument(), id = "page-other", title = "Other")
        val pageRepository = FakePageRepository(currentPage, otherPage)
        val useCase = useCase(pageRepository)

        val result = useCase.execute(
            workspaceId = "workspace-1",
            scopedTargetPage = currentPage,
            actions = listOf(
                ChatAction(
                    type = "ADD_BLOCK",
                    title = "",
                    targetTitle = "Other",
                    content = "Edit the requested page",
                ),
            ),
        )

        assertTrue(result.validationIssues.isEmpty())
        val unchangedCurrent = PageBlockCodec.decodeDocument(
            requireNotNull(pageRepository.getPage("page-current")).content,
        )
        val updatedOther = PageBlockCodec.decodeDocument(
            requireNotNull(pageRepository.getPage("page-other")).content,
        )
        assertEquals(emptyList<PageBlock>(), unchangedCurrent.meaningfulBlocks())
        assertEquals(
            listOf("Edit the requested page"),
            updatedOther.meaningfulBlocks().map { block -> block.text },
        )
    }

    @Test
    fun routesActionsToDifferentPagesInOneRequest() = runBlocking {
        val firstPage = page(PageBlockDocument(), id = "page-first", title = "First")
        val secondPage = page(PageBlockDocument(), id = "page-second", title = "Second")
        val pageRepository = FakePageRepository(firstPage, secondPage)
        val useCase = useCase(pageRepository)

        val result = useCase.execute(
            workspaceId = "workspace-1",
            scopedTargetPage = firstPage,
            actions = listOf(
                ChatAction(
                    type = "ADD_BLOCK",
                    title = "",
                    targetTitle = "First",
                    content = "First change",
                ),
                ChatAction(
                    type = "ADD_BLOCK",
                    title = "",
                    targetTitle = "Second",
                    content = "Second change",
                ),
            ),
        )

        assertTrue(result.validationIssues.isEmpty())
        val updatedFirst = PageBlockCodec.decodeDocument(
            requireNotNull(pageRepository.getPage("page-first")).content,
        )
        val updatedSecond = PageBlockCodec.decodeDocument(
            requireNotNull(pageRepository.getPage("page-second")).content,
        )
        assertEquals(listOf("First change"), updatedFirst.meaningfulBlocks().map { block -> block.text })
        assertEquals(listOf("Second change"), updatedSecond.meaningfulBlocks().map { block -> block.text })
        assertEquals(
            setOf("page-first", "page-second"),
            result.pageLinks.map { link -> link.pageId }.toSet(),
        )
    }

    @Test
    fun doesNotFuzzyMatchShortTargetTitleToLongerPageTitle() = runBlocking {
        val budgetTracker = page(PageBlockDocument(), id = "page-budget-tracker", title = "Budget Tracker")
        val pageRepository = FakePageRepository(budgetTracker)
        val useCase = useCase(pageRepository)

        val result = useCase.execute(
            workspaceId = "workspace-1",
            scopedTargetPage = null,
            actions = listOf(
                ChatAction(
                    type = "ADD_BLOCK",
                    title = "",
                    targetTitle = "Budget",
                    content = "Wrong target",
                ),
            ),
        )

        assertTrue(result.messages.isEmpty())
        assertEquals("target_page_not_found", result.validationIssues.single().code)
        val document = PageBlockCodec.decodeDocument(requireNotNull(pageRepository.getPage("page-budget-tracker")).content)
        assertEquals(emptyList<PageBlock>(), document.meaningfulBlocks())
    }

    @Test
    fun rejectsAmbiguousExactTargetTitleWhenNoScopedPage() = runBlocking {
        val firstBudget = page(PageBlockDocument(), id = "page-budget-1", title = "Budget")
        val secondBudget = page(PageBlockDocument(), id = "page-budget-2", title = "Budget")
        val useCase = useCase(FakePageRepository(firstBudget, secondBudget))

        val result = useCase.execute(
            workspaceId = "workspace-1",
            scopedTargetPage = null,
            actions = listOf(
                ChatAction(
                    type = "ADD_BLOCK",
                    title = "",
                    targetTitle = "Budget",
                    content = "Ambiguous",
                ),
            ),
        )

        assertTrue(result.messages.isEmpty())
        assertEquals("target_page_ambiguous", result.validationIssues.single().code)
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

    private fun page(
        document: PageBlockDocument,
        id: String = "page-1",
        title: String = "Target",
    ): Page {
        return Page(
            id = id,
            workspaceId = "workspace-1",
            parentPageId = null,
            title = title,
            content = PageBlockCodec.encodeDocument(document),
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 1000,
            deletedAt = null,
        )
    }

    private fun PageBlockDocument.meaningfulBlocks(): List<PageBlock> {
        return blocks.filterNot { block ->
            block.type == PageBlockType.Text &&
                block.text.isBlank() &&
                block.richTextSpans.isEmpty() &&
                block.mediaAttachments.isEmpty() &&
                block.children.isEmpty()
        }
    }

    private class FakePageRepository(
        vararg initialPages: Page,
    ) : PageRepository {
        constructor(initialPage: Page?) : this(*listOfNotNull(initialPage).toTypedArray())

        private val pages = linkedMapOf<String, Page>().apply {
            initialPages.forEach { page -> put(page.id, page) }
        }
        val createdPages = mutableListOf<Page>()

        override fun observePages(workspaceId: String): Flow<List<Page>> = flowOf(pages.values.toList())

        override fun observeChildPages(parentPageId: String): Flow<List<Page>> = flowOf(emptyList())

        override fun observeRecentPages(limit: Int): Flow<List<Page>> = flowOf(pages.values.toList())

        override fun observeRecentPages(workspaceId: String, limit: Int): Flow<List<Page>> = flowOf(pages.values.toList())

        override fun observeDeletedPages(workspaceId: String): Flow<List<Page>> = flowOf(emptyList())

        override fun observePage(pageId: String): Flow<Page?> = flowOf(pages[pageId])

        override fun observePageSyncState(pageId: String): Flow<PageSyncState> = flowOf(PageSyncState())

        override fun observePageCount(): Flow<Int> = flowOf(pages.size)

        override fun observePageCount(workspaceId: String): Flow<Int> = observePageCount()

        override suspend fun getPage(pageId: String): Page? = pages[pageId]

        override suspend fun upsertPage(page: Page) {
            pages[page.id] = page
        }

        override suspend fun updateBlockText(pageId: String, blockId: String, text: String): Boolean {
            val current = pages[pageId] ?: return false
            val document = PageBlockCodec.decodeDocument(current.content)
            val updated = document.copy(
                blocks = document.blocks.map { block ->
                    if (block.id == blockId) block.copy(text = text) else block
                },
            )
            pages[pageId] = current.copy(content = PageBlockCodec.encodeDocument(updated))
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
            pages[created.id] = created
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
