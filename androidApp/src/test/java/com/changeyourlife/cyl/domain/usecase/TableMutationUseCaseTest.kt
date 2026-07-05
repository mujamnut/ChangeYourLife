package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
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
    fun changingColumnTypeToStatusCreatesDefaultOptionsAndTypedCellValues() {
        val column = PageTableColumn(
            id = "state",
            name = "State",
            type = PageTableColumnType.Text,
        )
        val document = tableDocument(
            columns = listOf(column),
            rows = listOf(PageTableRow(id = "row-1", cells = mapOf("state" to "Done"))),
        )

        val result = useCase.updateColumnType(
            document = document,
            tableBlockId = "table-1",
            columnId = "state",
            type = PageTableColumnType.Status,
        )

        val updatedColumn = result.document.table.columns.single()
        val updatedRow = result.document.table.rows.single()
        assertTrue(result.changed)
        assertEquals(PageTableColumnType.Status, updatedColumn.type)
        assertEquals(listOf("Not started", "In progress", "Done", "Blocked"), updatedColumn.config.options.map { it.name })
        assertEquals(PageTableColumnType.Status, updatedRow.cellValues.getValue("state").type)
        assertEquals("Done", updatedRow.cellValues.getValue("state").text)
    }

    @Test
    fun updateColumnConfigNormalizesStatusOptions() {
        val statusColumn = PageTableColumn(
            id = "status",
            name = "Status",
            type = PageTableColumnType.Status,
        )
        val document = tableDocument(
            columns = listOf(statusColumn),
            rows = listOf(PageTableRow(id = "row-1", cells = mapOf("status" to ""))),
        )

        val result = useCase.updateColumnConfig(
            document = document,
            tableBlockId = "table-1",
            columnId = "status",
            config = PageTableColumnConfig(
                options = listOf(
                    PageTableSelectOption(id = "todo", name = " Todo "),
                    PageTableSelectOption(id = "todo-duplicate", name = "todo"),
                    PageTableSelectOption(id = "blank", name = " "),
                    PageTableSelectOption(id = "done", name = "Done"),
                ),
            ),
        )

        val options = result.document.table.columns.single().config.options
        assertTrue(result.changed)
        assertEquals(listOf("Todo", "Done"), options.map { it.name })
    }

    @Test
    fun updateColumnConfigKeepsSelectOptionsWithoutStatusDefaults() {
        val selectColumn = PageTableColumn(
            id = "category",
            name = "Category",
            type = PageTableColumnType.Select,
        )
        val document = tableDocument(
            columns = listOf(selectColumn),
            rows = listOf(PageTableRow(id = "row-1", cells = mapOf("category" to "Food"))),
        )

        val result = useCase.updateColumnConfig(
            document = document,
            tableBlockId = "table-1",
            columnId = "category",
            config = PageTableColumnConfig(
                options = listOf(
                    PageTableSelectOption(id = "food", name = "Food"),
                    PageTableSelectOption(id = "food-duplicate", name = " food "),
                    PageTableSelectOption(id = "fuel", name = "Fuel"),
                ),
            ),
        )

        val options = result.document.table.columns.single().config.options
        assertTrue(result.changed)
        assertEquals(listOf("Food", "Fuel"), options.map { it.name })
    }

    @Test
    fun addRowAppliesColumnDefaultValues() {
        val statusColumn = PageTableColumn(
            id = "status",
            name = "Status",
            type = PageTableColumnType.Status,
            config = PageTableColumnConfig(defaultValue = "Todo"),
        )
        val amountColumn = PageTableColumn(
            id = "amount",
            name = "Amount",
            type = PageTableColumnType.Number,
            config = PageTableColumnConfig(defaultValue = " 12.50 "),
        )
        val document = tableDocument(
            columns = listOf(statusColumn, amountColumn),
            rows = emptyList(),
        )

        val result = useCase.addRow(
            document = document,
            tableBlockId = "table-1",
            row = PageTableRow(id = "row-1"),
        )

        val row = result.document.table.rows.single()
        assertTrue(result.changed)
        assertEquals("Todo", row.cells.getValue("status"))
        assertEquals("12.50", row.cells.getValue("amount"))
        assertEquals(PageTableColumnType.Status, row.cellValues.getValue("status").type)
        assertEquals(PageTableColumnType.Number, row.cellValues.getValue("amount").type)
    }

    @Test
    fun changingRelationTargetClearsOldRowLinksAndDependentRollupTarget() {
        val relationColumn = PageTableColumn(
            id = "project",
            name = "Project",
            type = PageTableColumnType.Relation,
            relationTargetTableId = "old-table",
        )
        val rollupColumn = PageTableColumn(
            id = "total",
            name = "Total",
            type = PageTableColumnType.Rollup,
            rollupRelationColumnId = "project",
            rollupTargetColumnId = "amount",
            rollupAggregation = PageTableRollupAggregation.Sum,
        )
        val document = tableDocument(
            columns = listOf(relationColumn, rollupColumn),
            rows = listOf(PageTableRow(id = "row-1", cells = mapOf("project" to "old-row-1,old-row-2"))),
        )

        val result = useCase.updateColumnRelationTarget(
            document = document,
            tableBlockId = "table-1",
            columnId = "project",
            targetTableId = "new-table",
        )

        val table = result.document.table
        val row = table.rows.single()
        val updatedRollup = table.columns.single { column -> column.id == "total" }
        assertTrue(result.changed)
        assertEquals("new-table", table.columns.single { column -> column.id == "project" }.relationTargetTableId)
        assertEquals("", row.cells.getValue("project"))
        assertEquals(emptyList<String>(), row.cellValues.getValue("project").relationRowIds)
        assertEquals("project", updatedRollup.rollupRelationColumnId)
        assertEquals("", updatedRollup.rollupTargetColumnId)
    }

    @Test
    fun rollupConfigRejectsNonRelationDependency() {
        val nameColumn = PageTableColumn(id = "name", name = "Name")
        val rollupColumn = PageTableColumn(
            id = "total",
            name = "Total",
            type = PageTableColumnType.Rollup,
        )
        val document = tableDocument(
            columns = listOf(nameColumn, rollupColumn),
            rows = listOf(PageTableRow(id = "row-1", cells = mapOf("name" to "Food"))),
        )

        val result = useCase.updateColumnRollup(
            document = document,
            tableBlockId = "table-1",
            columnId = "total",
            relationColumnId = "name",
            targetColumnId = "amount",
            aggregation = PageTableRollupAggregation.Sum,
        )

        val updatedRollup = result.document.table.columns.single { column -> column.id == "total" }
        assertTrue(result.changed)
        assertEquals("", updatedRollup.rollupRelationColumnId)
        assertEquals("", updatedRollup.rollupTargetColumnId)
        assertEquals(PageTableRollupAggregation.Sum, updatedRollup.rollupAggregation)
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
    fun updateRelationCellStoresTypedRelationIdsAndRejectsNonRelationColumn() {
        val relationColumn = PageTableColumn(
            id = "project",
            name = "Project",
            type = PageTableColumnType.Relation,
            relationTargetTableId = "projects",
        )
        val textColumn = PageTableColumn(id = "note", name = "Note")
        val document = tableDocument(
            columns = listOf(relationColumn, textColumn),
            rows = listOf(PageTableRow(id = "row-1", cells = mapOf("project" to "", "note" to ""))),
        )

        val relationResult = useCase.updateRelationCell(
            document = document,
            tableBlockId = "table-1",
            rowId = "row-1",
            columnId = "project",
            relationRowIds = listOf(" target-1 ", "target-2", "target-1", ""),
        )

        val relationRow = relationResult.document.table.rows.single()
        assertTrue(relationResult.changed)
        assertEquals("target-1,target-2", relationResult.coercedValue)
        assertEquals("target-1,target-2", relationRow.cells.getValue("project"))
        assertEquals(listOf("target-1", "target-2"), relationRow.cellValues.getValue("project").relationRowIds)

        val textResult = useCase.updateRelationCell(
            document = relationResult.document,
            tableBlockId = "table-1",
            rowId = "row-1",
            columnId = "note",
            relationRowIds = listOf("target-3"),
        )

        assertFalse(textResult.changed)
        assertEquals(null, textResult.coercedValue)
        assertEquals("", textResult.document.table.rows.single().cells.getValue("note"))
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
    fun insertingFirstRowBlockDoesNotCreateFallbackDuplicate() {
        val document = tableDocument(
            columns = listOf(PageTableColumn(id = "name", name = "Name")),
            rows = listOf(
                PageTableRow(
                    id = "row-1",
                    cells = mapOf("name" to "Food"),
                    blocks = emptyList(),
                ),
            ),
        )

        val result = useCase.updateRowBlocks(
            document = document,
            tableBlockId = "table-1",
            rowId = "row-1",
        ) {
            EditorCommand.InsertBlock(
                block = PageBlock(
                    id = "first-note",
                    type = PageBlockType.Text,
                    text = "",
                ),
            )
        }

        assertTrue(result.changed)
        val rowBlocks = result.document.table.rows.single().blocks
        assertEquals(listOf("first-note"), rowBlocks.map { block -> block.id })
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
