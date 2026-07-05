package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableCellValue
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateCellValue
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTableSortGroupTest {
    @Test
    fun numberSortDescendingKeepsEmptyRowsLast() {
        val amount = PageTableColumn(
            id = "amount",
            name = "Amount",
            type = PageTableColumnType.Number,
        )
        val table = PageTable(
            columns = listOf(amount),
            rows = listOf(
                PageTableRow(id = "empty"),
                PageTableRow(id = "small", cells = mapOf("amount" to "4")),
                PageTableRow(id = "large", cells = mapOf("amount" to "29")),
            ),
            sort = PageTableSort(
                columnId = "amount",
                direction = PageTableSortDirection.Descending,
            ),
        )

        assertEquals(listOf("large", "small", "empty"), table.visibleRows().map { row -> row.id })
    }

    @Test
    fun dateSortUsesTypedDateValueInsteadOfFormattedText() {
        val deadline = PageTableColumn(
            id = "deadline",
            name = "Deadline",
            type = PageTableColumnType.Date,
        )
        val table = PageTable(
            columns = listOf(deadline),
            rows = listOf(
                dateRow(id = "january", columnId = "deadline", date = "2026-01-10"),
                dateRow(id = "december", columnId = "deadline", date = "2025-12-31"),
            ),
            sort = PageTableSort(columnId = "deadline"),
        )

        assertEquals(listOf("december", "january"), table.visibleRows().map { row -> row.id })
    }

    @Test
    fun formulaSortUsesNumericComputedValue() {
        val amount = PageTableColumn(
            id = "amount",
            name = "Amount",
            type = PageTableColumnType.Number,
        )
        val quantity = PageTableColumn(
            id = "quantity",
            name = "Quantity",
            type = PageTableColumnType.Number,
        )
        val total = PageTableColumn(
            id = "total",
            name = "Total",
            type = PageTableColumnType.Formula,
            formula = "{Amount} * {Quantity}",
        )
        val table = PageTable(
            columns = listOf(amount, quantity, total),
            rows = listOf(
                PageTableRow(id = "twenty", cells = mapOf("amount" to "10", "quantity" to "2")),
                PageTableRow(id = "hundred", cells = mapOf("amount" to "50", "quantity" to "2")),
            ),
            sort = PageTableSort(columnId = "total"),
        )

        assertEquals(listOf("twenty", "hundred"), table.visibleRows().map { row -> row.id })
    }

    @Test
    fun statusGroupUsesOptionOrderAndKeepsEmptyLast() {
        val status = PageTableColumn(
            id = "status",
            name = "Status",
            type = PageTableColumnType.Status,
            config = PageTableColumnConfig(
                options = listOf(
                    PageTableSelectOption(id = "todo", name = "Todo"),
                    PageTableSelectOption(id = "doing", name = "Doing"),
                    PageTableSelectOption(id = "done", name = "Done"),
                ),
            ),
        )
        val table = PageTable(
            columns = listOf(status),
            rows = listOf(
                PageTableRow(id = "done-row", cells = mapOf("status" to "Done")),
                PageTableRow(id = "empty-row"),
                PageTableRow(id = "todo-row", cells = mapOf("status" to "Todo")),
            ),
            groupByColumnId = "status",
        )

        assertEquals(
            listOf("Todo", "Done", "Empty"),
            table.groupedSummaries(tableReferences = emptyList()).map { group -> group.first },
        )
    }

    private fun dateRow(
        id: String,
        columnId: String,
        date: String,
    ): PageTableRow {
        return PageTableRow(
            id = id,
            cellValues = mapOf(
                columnId to PageTableCellValue(
                    type = PageTableColumnType.Date,
                    date = PageTableDateCellValue(startDate = date),
                ),
            ),
        )
    }
}
