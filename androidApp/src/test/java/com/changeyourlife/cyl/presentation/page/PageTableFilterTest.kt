package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableCellValue
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateCellValue
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableFilterOperator
import com.changeyourlife.cyl.domain.model.PageTableRow
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTableFilterTest {
    @Test
    fun numberGreaterThanFiltersByNumericValue() {
        val amount = PageTableColumn(
            id = "amount",
            name = "Amount",
            type = PageTableColumnType.Number,
        )
        val table = PageTable(
            columns = listOf(amount),
            rows = listOf(
                PageTableRow(id = "makeup", cells = mapOf("amount" to "29")),
                PageTableRow(id = "food", cells = mapOf("amount" to "4")),
            ),
            filter = PageTableFilter(
                columnId = "amount",
                query = "10",
                operator = PageTableFilterOperator.GreaterThan,
            ),
        )

        assertEquals(listOf("makeup"), table.visibleRows().map { row -> row.id })
    }

    @Test
    fun statusEqualsDoesNotMatchPartialText() {
        val status = PageTableColumn(
            id = "status",
            name = "Status",
            type = PageTableColumnType.Status,
        )
        val table = PageTable(
            columns = listOf(status),
            rows = listOf(
                PageTableRow(id = "paid", cells = mapOf("status" to "Paid")),
                PageTableRow(id = "unpaid", cells = mapOf("status" to "Unpaid")),
            ),
            filter = PageTableFilter(
                columnId = "status",
                query = "Paid",
                operator = PageTableFilterOperator.Equals,
            ),
        )

        assertEquals(listOf("paid"), table.visibleRows().map { row -> row.id })
    }

    @Test
    fun dateBeforeUsesTypedDateValue() {
        val deadline = PageTableColumn(
            id = "deadline",
            name = "Deadline",
            type = PageTableColumnType.Date,
        )
        val table = PageTable(
            columns = listOf(deadline),
            rows = listOf(
                PageTableRow(
                    id = "early",
                    cellValues = mapOf(
                        "deadline" to PageTableCellValue(
                            type = PageTableColumnType.Date,
                            date = PageTableDateCellValue(startDate = "2026-07-05"),
                        ),
                    ),
                ),
                PageTableRow(
                    id = "late",
                    cellValues = mapOf(
                        "deadline" to PageTableCellValue(
                            type = PageTableColumnType.Date,
                            date = PageTableDateCellValue(startDate = "2026-07-20"),
                        ),
                    ),
                ),
            ),
            filter = PageTableFilter(
                columnId = "deadline",
                query = "2026-07-10",
                operator = PageTableFilterOperator.Before,
            ),
        )

        assertEquals(listOf("early"), table.visibleRows().map { row -> row.id })
    }

    @Test
    fun isEmptyFilterDoesNotNeedQuery() {
        val notes = PageTableColumn(id = "notes", name = "Notes")
        val table = PageTable(
            columns = listOf(notes),
            rows = listOf(
                PageTableRow(id = "blank"),
                PageTableRow(id = "filled", cells = mapOf("notes" to "Paid today")),
            ),
            filter = PageTableFilter(
                columnId = "notes",
                operator = PageTableFilterOperator.IsEmpty,
            ),
        )

        assertEquals(listOf("blank"), table.visibleRows().map { row -> row.id })
    }
}
