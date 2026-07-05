package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTableSearchTest {
    @Test
    fun columnNameAloneDoesNotMatchEveryRow() {
        val table = PageTable(
            columns = listOf(
                PageTableColumn(id = "item", name = "Item"),
                PageTableColumn(id = "amount", name = "Amount", type = PageTableColumnType.Number),
            ),
            rows = listOf(
                PageTableRow(id = "makeup", cells = mapOf("item" to "Makeup", "amount" to "29")),
                PageTableRow(id = "food", cells = mapOf("item" to "Food", "amount" to "4")),
            ),
        )

        assertEquals(emptyList<String>(), table.visibleRows(searchQuery = "amount").map { row -> row.id })
    }

    @Test
    fun searchMatchesVisibleCellValuesAndRowNotes() {
        val table = PageTable(
            columns = listOf(PageTableColumn(id = "item", name = "Item")),
            rows = listOf(
                PageTableRow(id = "makeup", cells = mapOf("item" to "Makeup")),
                PageTableRow(
                    id = "note",
                    blocks = listOf(
                        PageBlock(
                            id = "note-block",
                            type = PageBlockType.Text,
                            text = "Paid at grocery store",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(listOf("makeup"), table.visibleRows(searchQuery = "makeup").map { row -> row.id })
        assertEquals(listOf("note"), table.visibleRows(searchQuery = "\"grocery store\"").map { row -> row.id })
    }

    @Test
    fun scopedSearchUsesColumnTypeSemantics() {
        val table = PageTable(
            columns = listOf(
                PageTableColumn(id = "status", name = "Status", type = PageTableColumnType.Status),
                PageTableColumn(id = "amount", name = "Amount", type = PageTableColumnType.Number),
            ),
            rows = listOf(
                PageTableRow(id = "paid", cells = mapOf("status" to "Paid", "amount" to "29")),
                PageTableRow(id = "unpaid", cells = mapOf("status" to "Unpaid", "amount" to "129")),
            ),
        )

        assertEquals(listOf("paid"), table.visibleRows(searchQuery = "status:paid").map { row -> row.id })
        assertEquals(listOf("paid"), table.visibleRows(searchQuery = "amount:29").map { row -> row.id })
    }

    @Test
    fun searchIgnoresHiddenColumns() {
        val table = PageTable(
            columns = listOf(
                PageTableColumn(id = "item", name = "Item"),
                PageTableColumn(
                    id = "secret",
                    name = "Secret",
                    config = PageTableColumnConfig(isHidden = true),
                ),
            ),
            rows = listOf(
                PageTableRow(id = "row-1", cells = mapOf("item" to "Visible", "secret" to "needle")),
            ),
        )

        assertEquals(emptyList<String>(), table.visibleRows(searchQuery = "needle").map { row -> row.id })
        assertEquals(emptyList<String>(), table.visibleRows(searchQuery = "secret:needle").map { row -> row.id })
    }
}
