package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableMutationUseCaseTest {
    private val useCase = TableMutationUseCase(ApplyEditorCommandUseCase())

    @Test
    fun changingColumnTypeCoercesExistingCells() {
        val amountColumn = PageTableColumn(
            id = "amount",
            name = "Amount",
            type = PageTableColumnType.Text,
        )
        val document = tableDocument(
            columns = listOf(amountColumn),
            rows = listOf(
                PageTableRow(id = "row-1", cells = mapOf("amount" to "1,488")),
                PageTableRow(id = "row-2", cells = mapOf("amount" to "abc")),
            ),
        )

        val result = useCase.updateColumnType(
            document = document,
            tableBlockId = "table-1",
            columnId = "amount",
            type = PageTableColumnType.Number,
        )

        assertTrue(result.changed)
        val rows = result.document.table.rows
        assertEquals("1488", rows[0].cells["amount"])
        assertEquals("", rows[1].cells["amount"])
        assertEquals(PageTableColumnType.Number, result.document.table.columns.single().type)
    }

    @Test
    fun deletingColumnCleansDependentTableState() {
        val nameColumn = PageTableColumn(id = "name", name = "Name")
        val statusColumn = PageTableColumn(
            id = "status",
            name = "Status",
            rollupRelationColumnId = "amount",
            rollupTargetColumnId = "amount",
        )
        val amountColumn = PageTableColumn(
            id = "amount",
            name = "Amount",
            type = PageTableColumnType.Number,
        )
        val document = tableDocument(
            table = PageTable(
                title = "Budget",
                columns = listOf(nameColumn, statusColumn, amountColumn),
                rows = listOf(
                    PageTableRow(
                        id = "row-1",
                        cells = mapOf("name" to "Food", "status" to "Need", "amount" to "4"),
                    ),
                ),
                sort = PageTableSort(columnId = "amount", direction = PageTableSortDirection.Descending),
                filter = PageTableFilter(columnId = "amount", query = "4"),
                groupByColumnId = "amount",
            ),
        )

        val result = useCase.deleteColumn(
            document = document,
            tableBlockId = "table-1",
            columnId = "amount",
        )

        val table = result.document.table
        assertTrue(result.changed)
        assertEquals(listOf("name", "status"), table.columns.map { column -> column.id })
        assertFalse(table.rows.single().cells.containsKey("amount"))
        assertEquals(PageTableSort(), table.sort)
        assertEquals(PageTableFilter(), table.filter)
        assertEquals("", table.groupByColumnId)
        assertEquals("", table.columns.single { column -> column.id == "status" }.rollupRelationColumnId)
        assertEquals("", table.columns.single { column -> column.id == "status" }.rollupTargetColumnId)
    }

    @Test
    fun updateCellCoercesEditableTypesAndSkipsFormulaColumns() {
        val checkboxColumn = PageTableColumn(
            id = "done",
            name = "Done",
            type = PageTableColumnType.Checkbox,
        )
        val formulaColumn = PageTableColumn(
            id = "total",
            name = "Total",
            type = PageTableColumnType.Formula,
            formula = "{Amount} * 2",
        )
        val document = tableDocument(
            columns = listOf(checkboxColumn, formulaColumn),
            rows = listOf(PageTableRow(id = "row-1", cells = mapOf("done" to "", "total" to ""))),
        )

        val checkboxResult = useCase.updateCell(
            document = document,
            tableBlockId = "table-1",
            rowId = "row-1",
            columnId = "done",
            value = "yes",
        )

        assertTrue(checkboxResult.changed)
        assertEquals("true", checkboxResult.coercedValue)
        assertEquals("true", checkboxResult.document.table.rows.single().cells["done"])

        val formulaResult = useCase.updateCell(
            document = checkboxResult.document,
            tableBlockId = "table-1",
            rowId = "row-1",
            columnId = "total",
            value = "999",
        )

        assertFalse(formulaResult.changed)
        assertEquals(null, formulaResult.coercedValue)
        assertEquals("", formulaResult.document.table.rows.single().cells["total"])
    }

    @Test
    fun updatesRowBlocksThroughEditorCommandPipeline() {
        val rowBlock = PageBlock(
            id = "row-block-1",
            type = PageBlockType.Text,
            text = "Old note",
        )
        val document = tableDocument(
            columns = listOf(PageTableColumn(id = "name", name = "Name")),
            rows = listOf(
                PageTableRow(
                    id = "row-1",
                    cells = mapOf("name" to "Food"),
                    blocks = listOf(rowBlock),
                ),
            ),
        )

        val updateResult = useCase.updateRowBlocks(
            document = document,
            tableBlockId = "table-1",
            rowId = "row-1",
        ) {
            EditorCommand.UpdateBlockText(
                blockId = "row-block-1",
                text = "New note",
            )
        }

        assertTrue(updateResult.changed)
        assertEquals("New note", updateResult.document.table.rows.single().blocks.single().text)

        val deleteResult = useCase.updateRowBlocks(
            document = updateResult.document,
            tableBlockId = "table-1",
            rowId = "row-1",
        ) {
            EditorCommand.DeleteBlock("row-block-1")
        }

        assertTrue(deleteResult.changed)
        val fallbackBlock = deleteResult.document.table.rows.single().blocks.single()
        assertEquals(PageBlockType.Text, fallbackBlock.type)
        assertEquals("", fallbackBlock.text)
    }

    @Test
    fun rowBlockNestingUsesEditorCommandPipelineAndSupportsUndo() {
        val document = tableDocument(
            columns = listOf(PageTableColumn(id = "name", name = "Name")),
            rows = listOf(
                PageTableRow(
                    id = "row-1",
                    cells = mapOf("name" to "Food"),
                    blocks = listOf(
                        PageBlock(id = "parent", type = PageBlockType.Bullet, text = "Parent"),
                        PageBlock(id = "child", type = PageBlockType.Text, text = "Child"),
                    ),
                ),
            ),
        )

        val result = useCase.updateRowBlocks(
            document = document,
            tableBlockId = "table-1",
            rowId = "row-1",
        ) {
            EditorCommand.MoveBlockToParent(
                blockId = "child",
                parentBlockId = "parent",
                index = 0,
            )
        }
        val undoResult = ApplyEditorCommandUseCase()(result.document, requireNotNull(result.commandResult.undoCommand))

        assertTrue(result.changed)
        val rowBlocks = result.document.table.rows.single().blocks
        assertEquals(listOf("parent"), rowBlocks.map { block -> block.id })
        assertEquals(listOf("child"), rowBlocks.single().children.map { block -> block.id })
        assertTrue(result.commandResult.undoCommand is EditorCommand.ReplaceTable)
        assertEquals(document, undoResult.document)
    }

    @Test
    fun replaceRowBlockWithBlocksKeepsFirstIdAndReturnsTableUndo() {
        val document = tableDocument(
            columns = listOf(PageTableColumn(id = "name", name = "Name")),
            rows = listOf(
                PageTableRow(
                    id = "row-1",
                    cells = mapOf("name" to "Food"),
                    blocks = listOf(
                        PageBlock(id = "before", type = PageBlockType.Text, text = "Before"),
                        PageBlock(id = "target", type = PageBlockType.Text, text = "Old"),
                        PageBlock(id = "after", type = PageBlockType.Text, text = "After"),
                    ),
                ),
            ),
        )

        val result = useCase.replaceRowBlockWithBlocks(
            document = document,
            tableBlockId = "table-1",
            rowId = "row-1",
            rowBlockId = "target",
            replacementBlocks = listOf(
                PageBlock(id = "new-heading", type = PageBlockType.Heading, text = "Plan"),
                PageBlock(id = "new-bullet", type = PageBlockType.Bullet, text = "Buy rice"),
            ),
        )
        val undoResult = ApplyEditorCommandUseCase()(result.document, requireNotNull(result.commandResult.undoCommand))

        assertTrue(result.changed)
        val rowBlocks = result.document.table.rows.single().blocks
        assertEquals(listOf("before", "target", "new-bullet", "after"), rowBlocks.map { block -> block.id })
        assertEquals(PageBlockType.Heading, rowBlocks[1].type)
        assertEquals("Plan", rowBlocks[1].text)
        assertEquals(PageBlockType.Bullet, rowBlocks[2].type)
        assertTrue(result.commandResult.undoCommand is EditorCommand.ReplaceTable)
        assertEquals(document, undoResult.document)
    }

    @Test
    fun tableMutationsExposeUndoCommand() {
        val document = tableDocument(
            columns = listOf(PageTableColumn(id = "name", name = "Name")),
            rows = listOf(PageTableRow(id = "row-1", cells = mapOf("name" to "Food"))),
        )

        val result = useCase.updateColumnName(
            document = document,
            tableBlockId = "table-1",
            columnId = "name",
            name = "Item",
        )
        val undoCommand = result.commandResult.undoCommand
        val undoResult = ApplyEditorCommandUseCase()(result.document, requireNotNull(undoCommand))

        assertTrue(result.changed)
        assertTrue(undoCommand is EditorCommand.ReplaceTable)
        assertEquals(document, undoResult.document)
    }

    private fun tableDocument(
        table: PageTable,
    ): PageBlockDocument {
        return PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "table-1",
                    type = PageBlockType.DatabaseTable,
                    table = table,
                ),
            ),
        )
    }

    private fun tableDocument(
        columns: List<PageTableColumn>,
        rows: List<PageTableRow>,
    ): PageBlockDocument {
        return tableDocument(
            table = PageTable(
                title = "Budget",
                columns = columns,
                rows = rows,
            ),
        )
    }

    private val PageBlockDocument.table: PageTable
        get() = blocks.single().table
}
