package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatTableColumn
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.usecase.ApplyEditorCommandUseCase
import com.changeyourlife.cyl.domain.usecase.PageMutationUseCase
import com.changeyourlife.cyl.domain.usecase.ScheduleTableDateReminderUseCase
import com.changeyourlife.cyl.domain.usecase.TableMutationUseCase
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AiPageActionExecutionRegressionTest(
    private val actionType: String,
) {
    @Test
    fun validSharedActionExecutesAgainstARealisticPageDocument() = runBlocking {
        val fixture = RegressionPageFixture.create(actionType)
        val executor = AiPageActionExecutor(
            pageRepository = RegressionPageRepository(
                page = fixture.page,
                supportingPages = fixture.supportingPages,
            ),
            pageMutationUseCase = PageMutationUseCase(ApplyEditorCommandUseCase()),
            tableMutationUseCase = TableMutationUseCase(ApplyEditorCommandUseCase()),
            scheduleTableDateReminderUseCase = ScheduleTableDateReminderUseCase(
                RegressionReminderRepository(),
            ),
        )

        val result = executor.executeOnPage(
            page = fixture.page,
            title = fixture.page.title,
            document = fixture.document,
            actions = listOf(RegressionActionFixtures.validAction(actionType)),
        )

        assertTrue(
            "$actionType produced validation/execution errors: ${result.validationIssues}",
            result.validationIssues.isEmpty(),
        )
        assertEquals(
            "$actionType was accepted but was not recorded as executed.",
            listOf(0),
            result.executedActionIndexes,
        )

        when (actionType) {
            "RENAME_CURRENT_PAGE", "RENAME_PAGE" -> assertNotNull(result.updatedTitle)
            "CREATE_PAGE", "CREATE_SUBPAGE", "DUPLICATE_PAGE" ->
                assertEquals(1, result.createdPages.size)
            "CREATE_REMINDER", "RESCHEDULE_REMINDER" -> {
                assertNotNull(result.updatedDocument)
                assertEquals(1, result.createdReminders.size)
            }
            "MOVE_PAGE", "TRASH_PAGE", "RESTORE_PAGE", "DELETE_PAGE_PERMANENTLY" -> Unit
            else -> assertNotNull(
                "$actionType reported execution without producing a page mutation.",
                result.updatedDocument,
            )
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun sharedActions(): List<Array<String>> =
            AiActionContractSchema.supportedTypes
                .sorted()
                .map { actionType -> arrayOf(actionType) }
    }
}

private object RegressionActionFixtures {
    fun validAction(type: String): ChatAction = when (type) {
        "RENAME_CURRENT_PAGE", "RENAME_PAGE" ->
            page(type).copy(title = "Renamed budget")

        "UPDATE_PAGE" ->
            page(type).copy(content = "Replaced page content")

        "CREATE_PAGE" ->
            page(type).copy(title = "Created page", content = "Created page content")

        "CREATE_SUBPAGE" ->
            page(type).copy(title = "Created subpage", content = "Created subpage content")
        "MOVE_PAGE" ->
            page(type).copy(parentPageTitle = "Archive")
        "DUPLICATE_PAGE" ->
            page(type).copy(title = "Budget copy")
        "TRASH_PAGE", "RESTORE_PAGE", "DELETE_PAGE_PERMANENTLY" ->
            page(type)

        "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" ->
            page(type).copy(content = "New note", blockType = "Text")

        "DELETE_ALL_BLOCKS" ->
            page(type)

        "DELETE_BLOCK" ->
            page(type).copy(blockId = "block-text")
        "MOVE_BLOCK" ->
            page(type).copy(blockId = "block-todo", moveDirection = "up")
        "INDENT_BLOCK" ->
            page(type).copy(blockId = "block-todo")
        "OUTDENT_BLOCK" ->
            page(type).copy(blockId = "block-todo")
        "DUPLICATE_BLOCK" ->
            page(type).copy(blockId = "block-text")

        "UPDATE_BLOCK", "EDIT_BLOCK" ->
            page(type).copy(blockId = "block-text", content = "Updated budget note")

        "UPDATE_TODO" ->
            page(type).copy(
                blockId = "block-todo",
                content = "Updated todo",
                isChecked = true,
            )

        "CHECK_BLOCK" ->
            page(type).copy(blockId = "block-todo", isChecked = true)

        "UNCHECK_BLOCK" ->
            page(type).copy(blockId = "block-todo", isChecked = false)

        "FORMAT_BLOCK_TEXT" ->
            page(type).copy(
                blockId = "block-text",
                textToFormat = "budget",
                format = "Bold",
            )

        "ADD_PROPERTY" ->
            page(type).copy(
                propertyName = "Owner",
                propertyType = "Text",
                value = "Kumar",
            )

        "UPDATE_PROPERTY" ->
            page(type).copy(
                propertyName = "Budget",
                propertyType = "Number",
                value = "200",
            )

        "DELETE_PROPERTY" ->
            page(type).copy(propertyName = "Budget")
        "RENAME_PROPERTY" ->
            page(type).copy(propertyName = "Budget", newPropertyName = "Monthly budget")
        "MOVE_PROPERTY" ->
            page(type).copy(propertyName = "Budget", targetIndex = 2)
        "DUPLICATE_PROPERTY" ->
            page(type).copy(propertyName = "Budget", newPropertyName = "Budget copy")

        "CREATE_DATABASE", "CREATE_TABLE" ->
            page(type).copy(
                tableTitle = "New transactions",
                tableColumns = listOf(
                    ChatTableColumn(name = "Name", type = "Text"),
                    ChatTableColumn(name = "Amount", type = "Number"),
                ),
            )

        "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" ->
            table(type).copy(title = "Renamed transactions")
        "DUPLICATE_DATABASE" ->
            table(type).copy(title = "Transactions copy")
        "ATTACH_TABLE_DATA_SOURCE" ->
            table(type).copy(sourcePageTitle = "Sales", sourceTableTitle = "Orders")
        "CLEAR_TABLE_DATA_SOURCE" ->
            table(type)

        "ADD_TABLE_COLUMN" ->
            table(type).copy(
                columnName = "Payment method",
                columnType = "Select",
                options = listOf("Cash", "Card"),
            )

        "DELETE_TABLE_COLUMN" ->
            table(type).copy(columnId = "column-category", columnName = "Category")

        "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN" ->
            table(type).copy(
                columnId = "column-category",
                columnName = "Category",
                newColumnName = "Expense category",
            )

        "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE" ->
            table(type).copy(
                columnId = "column-amount",
                columnName = "Amount",
                columnType = "Text",
            )

        "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG" ->
            table(type).copy(
                columnId = "column-category",
                columnName = "Category",
                columnType = "Select",
                options = listOf("Food", "Fuel", "Other"),
            )

        "UPDATE_FORMULA_COLUMN" ->
            table(type).copy(
                columnId = "column-balance",
                columnName = "Balance",
                formula = "{Amount} * 2",
            )

        "UPDATE_RELATION_COLUMN" ->
            table(type).copy(
                columnId = "column-relation",
                columnName = "Category relation",
                relationTargetTableId = "table-categories",
                relationTargetTableTitle = "Categories",
            )

        "UPDATE_ROLLUP_COLUMN" ->
            table(type).copy(
                columnId = "column-rollup",
                columnName = "Category total",
                rollupRelationColumnId = "column-relation",
                rollupRelationColumnName = "Category relation",
                rollupTargetColumnId = "category-column-amount",
                rollupTargetColumnName = "Amount",
                rollupAggregation = "Sum",
            )

        "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN" ->
            table(type).copy(
                columnId = "column-category",
                columnName = "Category",
                targetIndex = 2,
            )
        "DUPLICATE_TABLE_COLUMN" ->
            table(type).copy(
                columnId = "column-category",
                columnName = "Category",
                newColumnName = "Category copy",
            )

        "ADD_TABLE_ROW" ->
            table(type).copy(
                rowTitle = "Dinner",
                cellValues = mapOf(
                    "Name" to "Dinner",
                    "Amount" to "20",
                    "Month" to "2026-07",
                    "Category" to "Food",
                ),
            )

        "DELETE_TABLE_ROW" ->
            table(type).copy(rowId = "row-lunch", rowTitle = "Lunch")

        "UPDATE_TABLE_ROW" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                cellValues = mapOf("Amount" to "15"),
            )

        "RENAME_TABLE_ROW" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                newRowTitle = "Dinner",
            )

        "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                targetIndex = 2,
            )
        "DUPLICATE_TABLE_ROW" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                newRowTitle = "Lunch copy",
            )
        "DELETE_TABLE_ROWS" ->
            table(type).copy(
                columnId = "column-month",
                columnName = "Month",
                filterQuery = "2026-07",
            )
        "UPDATE_TABLE_ROWS" ->
            table(type).copy(
                rowIds = listOf("row-lunch", "row-fuel"),
                cellValues = mapOf("Category" to "Other"),
            )

        "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                content = "New receipt note",
                blockType = "Text",
            )

        "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                rowBlockId = "row-block",
                content = "Updated receipt note",
            )

        "CHECK_ROW_PAGE_BLOCK" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                rowBlockId = "row-todo",
                isChecked = true,
            )

        "UNCHECK_ROW_PAGE_BLOCK" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                rowBlockId = "row-todo",
                isChecked = false,
            )

        "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                rowBlockId = "row-block",
            )

        "UPDATE_TABLE_CELL" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                columnId = "column-amount",
                columnName = "Amount",
                value = "18",
            )

        "CLEAR_TABLE_CELL" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                columnId = "column-amount",
                columnName = "Amount",
            )

        "CLEAR_TABLE_CELLS" ->
            table(type).copy(
                columnId = "column-month",
                columnName = "Month",
                filterQuery = "2026-07",
            )

        "CHANGE_TABLE_VIEW", "SET_TABLE_VIEW" ->
            table(type).copy(tableView = "Calendar")

        "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG" ->
            table(type).copy(
                tableView = "Calendar",
                calendarDateColumnId = "column-date",
                calendarDateColumnName = "Date",
            )

        "SORT_TABLE", "SET_TABLE_SORT" ->
            table(type).copy(
                columnId = "column-amount",
                columnName = "Amount",
                sortDirection = "Descending",
            )

        "CLEAR_TABLE_SORT" ->
            table(type)

        "FILTER_TABLE", "SET_TABLE_FILTER" ->
            table(type).copy(
                columnId = "column-category",
                columnName = "Category",
                filterQuery = "Food",
            )

        "CLEAR_TABLE_FILTER" ->
            table(type)

        "GROUP_TABLE", "SET_TABLE_GROUP" ->
            table(type).copy(
                columnId = "column-category",
                columnName = "Category",
                groupByColumnId = "column-category",
                groupByColumnName = "Category",
            )

        "CLEAR_TABLE_GROUP" ->
            table(type)

        "CREATE_TASK" ->
            table(type).copy(
                title = "Submit report",
                delayMinutes = 120,
            )

        "CREATE_REMINDER" ->
            table(type).copy(
                title = "Pay electricity bill",
                delayMinutes = 120,
            )
        "CANCEL_REMINDER", "COMPLETE_REMINDER" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                columnId = "column-date",
                columnName = "Date",
            )
        "RESCHEDULE_REMINDER" ->
            table(type).copy(
                rowId = "row-lunch",
                rowTitle = "Lunch",
                columnId = "column-date",
                columnName = "Date",
                delayMinutes = 120,
            )

        else -> error("Missing Android execution fixture for shared action: $type")
    }

    private fun page(type: String): ChatAction =
        ChatAction(
            type = type,
            title = "",
            targetTitle = "Budget",
        )

    private fun table(type: String): ChatAction =
        page(type).copy(tableTitle = "Transactions")
}

private data class RegressionPageFixture(
    val page: Page,
    val document: PageBlockDocument,
    val supportingPages: List<Page>,
) {
    companion object {
        fun create(actionType: String): RegressionPageFixture {
            val todoChecked = actionType == "UNCHECK_BLOCK"
            val rowTodoChecked = actionType == "UNCHECK_ROW_PAGE_BLOCK"
            val categoryOptions = listOf(
                PageTableSelectOption(id = "food", name = "Food"),
                PageTableSelectOption(id = "fuel", name = "Fuel"),
            )
            val columns = listOf(
                PageTableColumn(id = "column-name", name = "Name"),
                PageTableColumn(
                    id = "column-date",
                    name = "Date",
                    type = PageTableColumnType.Date,
                ),
                PageTableColumn(
                    id = "column-month",
                    name = "Month",
                    type = PageTableColumnType.Select,
                    config = PageTableColumnConfig(
                        options = listOf(PageTableSelectOption(id = "2026-07", name = "2026-07")),
                    ),
                ),
                PageTableColumn(
                    id = "column-category",
                    name = "Category",
                    type = PageTableColumnType.Select,
                    config = PageTableColumnConfig(options = categoryOptions),
                ),
                PageTableColumn(
                    id = "column-amount",
                    name = "Amount",
                    type = PageTableColumnType.Number,
                ),
                PageTableColumn(
                    id = "column-balance",
                    name = "Balance",
                    type = PageTableColumnType.Formula,
                    formula = "{Amount}",
                ),
                PageTableColumn(
                    id = "column-relation",
                    name = "Category relation",
                    type = PageTableColumnType.Relation,
                ),
                PageTableColumn(
                    id = "column-rollup",
                    name = "Category total",
                    type = PageTableColumnType.Rollup,
                    relationTargetTableId = "table-categories",
                    rollupRelationColumnId = "column-relation",
                    rollupTargetColumnId = "category-column-amount",
                    rollupAggregation = PageTableRollupAggregation.Count,
                ),
            )
            val transactionTable = PageBlock(
                id = "table-transactions",
                type = PageBlockType.DatabaseTable,
                table = PageTable(
                    title = "Transactions",
                    viewConfig = if (actionType == "CLEAR_TABLE_DATA_SOURCE") {
                        PageTableViewConfig(
                            dataSourcePageId = "page-sales",
                            dataSourceTableBlockId = "table-orders",
                            dataSourceTitle = "Orders",
                        )
                    } else {
                        PageTableViewConfig()
                    },
                    columns = columns,
                    rows = listOf(
                        PageTableRow(
                            id = "row-lunch",
                            cells = mapOf(
                                "column-name" to "Lunch",
                                "column-date" to "2026-07-20",
                                "column-month" to "2026-07",
                                "column-category" to "Food",
                                "column-amount" to "12",
                            ),
                            blocks = listOf(
                                PageBlock(
                                    id = "row-block",
                                    type = PageBlockType.Text,
                                    text = "Receipt note",
                                ),
                                PageBlock(
                                    id = "row-todo",
                                    type = PageBlockType.Todo,
                                    text = "Check receipt",
                                    isChecked = rowTodoChecked,
                                ),
                            ),
                        ),
                        PageTableRow(
                            id = "row-fuel",
                            cells = mapOf(
                                "column-name" to "Fuel",
                                "column-date" to "2026-07-21",
                                "column-month" to "2026-07",
                                "column-category" to "Fuel",
                                "column-amount" to "5",
                            ),
                        ),
                    ),
                    sort = PageTableSort(
                        columnId = "column-amount",
                        direction = PageTableSortDirection.Ascending,
                    ),
                    filter = PageTableFilter(
                        columnId = "column-category",
                        query = "Fuel",
                    ),
                    groupByColumnId = "column-month",
                ),
            )
            val categoryTable = PageBlock(
                id = "table-categories",
                type = PageBlockType.DatabaseTable,
                table = PageTable(
                    title = "Categories",
                    columns = listOf(
                        PageTableColumn(id = "category-column-name", name = "Name"),
                        PageTableColumn(
                            id = "category-column-amount",
                            name = "Amount",
                            type = PageTableColumnType.Number,
                        ),
                    ),
                    rows = listOf(
                        PageTableRow(
                            id = "category-row-food",
                            cells = mapOf(
                                "category-column-name" to "Food",
                                "category-column-amount" to "12",
                            ),
                        ),
                    ),
                ),
            )
            val document = PageBlockDocument(
                properties = listOf(
                    PageProperty(
                        id = "property-budget",
                        name = "Budget",
                        type = PagePropertyType.Number,
                        value = "100",
                    ),
                    PageProperty(
                        id = "property-notes",
                        name = "Notes",
                        type = PagePropertyType.Text,
                        value = "",
                    ),
                ),
                blocks = buildList {
                    add(
                        PageBlock(
                        id = "block-text",
                        type = PageBlockType.Text,
                        text = "budget ayam",
                        children = if (actionType == "OUTDENT_BLOCK") {
                            listOf(
                                PageBlock(
                                    id = "block-todo",
                                    type = PageBlockType.Todo,
                                    text = "Review budget",
                                    isChecked = todoChecked,
                                ),
                            )
                        } else {
                            emptyList()
                        },
                    ),
                    )
                    if (actionType != "OUTDENT_BLOCK") {
                        add(
                            PageBlock(
                                id = "block-todo",
                                type = PageBlockType.Todo,
                                text = "Review budget",
                                isChecked = todoChecked,
                            ),
                        )
                    }
                    add(transactionTable)
                    add(categoryTable)
                },
            )
            val isDeletedPage = actionType in setOf("RESTORE_PAGE", "DELETE_PAGE_PERMANENTLY")
            val page = Page(
                id = "page-budget",
                workspaceId = "workspace-1",
                parentPageId = null,
                title = "Budget",
                content = PageBlockCodec.encodeDocument(document),
                sortOrder = 0,
                createdAt = 1_000,
                updatedAt = 1_000,
                deletedAt = if (isDeletedPage) 1_500 else null,
            )
            val archivePage = Page(
                id = "page-archive",
                workspaceId = "workspace-1",
                parentPageId = null,
                title = "Archive",
                content = PageBlockCodec.encodeDocument(PageBlockDocument()),
                sortOrder = 1,
                createdAt = 1_000,
                updatedAt = 1_000,
                deletedAt = null,
            )
            val ordersDocument = PageBlockDocument(
                blocks = listOf(
                    PageBlock(
                        id = "table-orders",
                        type = PageBlockType.DatabaseTable,
                        table = PageTable(
                            title = "Orders",
                            columns = listOf(
                                PageTableColumn(id = "orders-name", name = "Name"),
                                PageTableColumn(
                                    id = "orders-amount",
                                    name = "Amount",
                                    type = PageTableColumnType.Number,
                                ),
                            ),
                            rows = listOf(
                                PageTableRow(
                                    id = "orders-row-1",
                                    cells = mapOf(
                                        "orders-name" to "Order 1",
                                        "orders-amount" to "25",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            val salesPage = Page(
                id = "page-sales",
                workspaceId = "workspace-1",
                parentPageId = null,
                title = "Sales",
                content = PageBlockCodec.encodeDocument(ordersDocument),
                sortOrder = 2,
                createdAt = 1_000,
                updatedAt = 1_000,
                deletedAt = null,
            )
            return RegressionPageFixture(
                page = page,
                document = document,
                supportingPages = listOf(archivePage, salesPage),
            )
        }
    }
}

private class RegressionPageRepository(
    private var page: Page,
    private val supportingPages: List<Page>,
) : PageRepository {
    override fun observePages(workspaceId: String): Flow<List<Page>> =
        flowOf((listOf(page) + supportingPages).filter { candidate -> candidate.deletedAt == null })

    override fun observeChildPages(parentPageId: String): Flow<List<Page>> = flowOf(emptyList())

    override fun observeRecentPages(limit: Int): Flow<List<Page>> = flowOf(listOf(page))

    override fun observeRecentPages(workspaceId: String, limit: Int): Flow<List<Page>> =
        flowOf(listOf(page))

    override fun observeDeletedPages(workspaceId: String): Flow<List<Page>> =
        flowOf((listOf(page) + supportingPages).filter { candidate -> candidate.deletedAt != null })

    override fun observePage(pageId: String): Flow<Page?> = flowOf(page.takeIf { it.id == pageId })

    override fun observePageSyncState(pageId: String): Flow<PageSyncState> = flowOf(PageSyncState())

    override fun observePageCount(): Flow<Int> = flowOf(1)

    override fun observePageCount(workspaceId: String): Flow<Int> = flowOf(1)

    override suspend fun getPage(pageId: String): Page? = page.takeIf { it.id == pageId }

    override suspend fun upsertPage(page: Page) {
        this.page = page
    }

    override suspend fun updateBlockText(pageId: String, blockId: String, text: String): Boolean = true

    override suspend fun updateBlock(pageId: String, block: PageBlock): Boolean = true

    override suspend fun updatePropertyValue(
        pageId: String,
        propertyId: String,
        propertyName: String,
        value: String,
    ): Boolean = true

    override suspend fun updateTableCellValue(
        pageId: String,
        rowId: String,
        columnId: String,
        value: String,
    ): Boolean = true

    override suspend fun updateTable(pageId: String, tableBlockId: String, table: PageTable): Boolean = true

    override suspend fun updateTableColumn(
        pageId: String,
        tableBlockId: String,
        column: PageTableColumn,
    ): Boolean = true

    override suspend fun addBlock(
        pageId: String,
        block: PageBlock,
        parentBlockId: String,
        afterBlockId: String,
        targetIndex: Int?,
    ): Boolean = true

    override suspend fun deleteBlock(pageId: String, blockId: String): Boolean = true

    override suspend fun moveBlock(pageId: String, blockId: String, targetIndex: Int): Boolean = true

    override suspend fun addProperty(
        pageId: String,
        property: PageProperty,
        targetIndex: Int?,
    ): Boolean = true

    override suspend fun deleteProperty(pageId: String, propertyId: String): Boolean = true

    override suspend fun moveProperty(pageId: String, propertyId: String, targetIndex: Int): Boolean = true

    override suspend fun addTableColumn(
        pageId: String,
        tableBlockId: String,
        column: PageTableColumn,
        cellValues: Map<String, String>,
        targetIndex: Int?,
    ): Boolean = true

    override suspend fun deleteTableColumn(
        pageId: String,
        tableBlockId: String,
        columnId: String,
    ): Boolean = true

    override suspend fun moveTableColumn(
        pageId: String,
        tableBlockId: String,
        columnId: String,
        targetIndex: Int,
    ): Boolean = true

    override suspend fun addTableRow(
        pageId: String,
        tableBlockId: String,
        row: PageTableRow,
        targetIndex: Int?,
    ): Boolean = true

    override suspend fun updateTableRow(
        pageId: String,
        tableBlockId: String,
        row: PageTableRow,
    ): Boolean = true

    override suspend fun deleteTableRow(
        pageId: String,
        tableBlockId: String,
        rowId: String,
    ): Boolean = true

    override suspend fun moveTableRow(
        pageId: String,
        tableBlockId: String,
        rowId: String,
        targetIndex: Int,
    ): Boolean = true

    override suspend fun createPage(
        workspaceId: String,
        title: String,
        content: String,
        parentPageId: String?,
    ): Page = Page(
        id = "created-$title",
        workspaceId = workspaceId,
        parentPageId = parentPageId,
        title = title,
        content = content,
        sortOrder = 0,
        createdAt = 2_000,
        updatedAt = 2_000,
        deletedAt = null,
    )

    override suspend fun deletePage(pageId: String) = Unit

    override suspend fun restorePage(pageId: String) = Unit

    override suspend fun deletePagePermanently(pageId: String) = Unit

    override suspend fun keepLocalPageConflict(pageId: String) = Unit

    override suspend fun useRemotePageConflict(pageId: String) = Unit
}

private class RegressionReminderRepository : ReminderRepository {
    private val reminders = mutableListOf<Reminder>()

    override fun observePendingReminders(): Flow<List<Reminder>> = flowOf(reminders)

    override fun observePendingReminders(workspaceId: String): Flow<List<Reminder>> =
        flowOf(reminders.filter { reminder -> reminder.workspaceId == workspaceId })

    override fun observePendingReminderCount(): Flow<Int> = flowOf(reminders.size)

    override fun observePendingReminderCount(workspaceId: String): Flow<Int> =
        flowOf(reminders.count { reminder -> reminder.workspaceId == workspaceId })

    override suspend fun getReminderForTask(taskId: String): Reminder? =
        reminders.firstOrNull { reminder -> reminder.taskId == taskId }

    override suspend fun upsertReminder(reminder: Reminder) {
        reminders.removeAll { existing -> existing.id == reminder.id }
        reminders += reminder
    }

    override suspend fun reschedulePendingReminders() = Unit

    override suspend fun createReminder(
        workspaceId: String,
        title: String,
        remindAt: Long,
        pageId: String?,
        taskId: String?,
        id: String?,
    ): Reminder {
        val reminder = Reminder(
            id = id ?: "reminder-${reminders.size + 1}",
            workspaceId = workspaceId,
            pageId = pageId,
            taskId = taskId,
            title = title,
            remindAt = remindAt,
            isDone = false,
            createdAt = 2_000,
            updatedAt = 2_000,
            deletedAt = null,
        )
        upsertReminder(reminder)
        return reminder
    }
}
