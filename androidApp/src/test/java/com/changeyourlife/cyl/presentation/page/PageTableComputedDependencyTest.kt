package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTableComputedDependencyTest {
    @Test
    fun formulaCycleReturnsCircularDependency() {
        val first = PageTableColumn(
            id = "first",
            name = "First",
            type = PageTableColumnType.Formula,
            formula = "{Second} + 1",
        )
        val second = PageTableColumn(
            id = "second",
            name = "Second",
            type = PageTableColumnType.Formula,
            formula = "{First} + 1",
        )
        val row = PageTableRow(id = "row-1")
        val table = PageTable(
            title = "Computed",
            columns = listOf(first, second),
            rows = listOf(row),
        )
        val references = listOf(PageTableReference(blockId = "table-1", title = "Computed", table = table))

        assertEquals("Circular dependency", table.displayCellText(row, first, references))
        assertEquals("Circular dependency", table.displayCellText(row, second, references))
    }

    @Test
    fun rollupWithMissingRelationSourceShowsMissingSource() {
        val relation = PageTableColumn(
            id = "project",
            name = "Project",
            type = PageTableColumnType.Relation,
            relationTargetTableId = "deleted-table",
        )
        val rollup = PageTableColumn(
            id = "total",
            name = "Total",
            type = PageTableColumnType.Rollup,
            rollupRelationColumnId = "project",
            rollupTargetColumnId = "amount",
            rollupAggregation = PageTableRollupAggregation.Sum,
        )
        val row = PageTableRow(
            id = "row-1",
            cells = mapOf("project" to "target-row"),
        )
        val table = PageTable(
            title = "Budget",
            columns = listOf(relation, rollup),
            rows = listOf(row),
        )
        val references = listOf(PageTableReference(blockId = "table-1", title = "Budget", table = table))

        assertEquals("Missing source", table.displayCellText(row, relation, references))
        assertEquals("Missing source", table.displayCellText(row, rollup, references))
    }

    @Test
    fun crossTableRollupCycleReturnsCircularDependency() {
        val aRelation = PageTableColumn(
            id = "to-b",
            name = "To B",
            type = PageTableColumnType.Relation,
            relationTargetTableId = "table-b",
        )
        val aRollup = PageTableColumn(
            id = "from-b",
            name = "From B",
            type = PageTableColumnType.Rollup,
            rollupRelationColumnId = "to-b",
            rollupTargetColumnId = "from-a",
            rollupAggregation = PageTableRollupAggregation.Sum,
        )
        val bRelation = PageTableColumn(
            id = "to-a",
            name = "To A",
            type = PageTableColumnType.Relation,
            relationTargetTableId = "table-a",
        )
        val bRollup = PageTableColumn(
            id = "from-a",
            name = "From A",
            type = PageTableColumnType.Rollup,
            rollupRelationColumnId = "to-a",
            rollupTargetColumnId = "from-b",
            rollupAggregation = PageTableRollupAggregation.Sum,
        )
        val aRow = PageTableRow(id = "a-row", cells = mapOf("to-b" to "b-row"))
        val bRow = PageTableRow(id = "b-row", cells = mapOf("to-a" to "a-row"))
        val tableA = PageTable(
            title = "A",
            columns = listOf(aRelation, aRollup),
            rows = listOf(aRow),
        )
        val tableB = PageTable(
            title = "B",
            columns = listOf(bRelation, bRollup),
            rows = listOf(bRow),
        )
        val references = listOf(
            PageTableReference(blockId = "table-a", title = "A", table = tableA),
            PageTableReference(blockId = "table-b", title = "B", table = tableB),
        )

        assertEquals("Circular dependency", tableA.displayCellText(aRow, aRollup, references))
        assertEquals("Circular dependency", tableB.displayCellText(bRow, bRollup, references))
    }
}
