package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetLedgerSummarySyncTest {
    @Test
    fun syncRebuildsSummaryFromTransactions() {
        val name = PageTableColumn("tx-name", "Name", PageTableColumnType.Text)
        val category = PageTableColumn("tx-category", "Category", PageTableColumnType.Select)
        val type = PageTableColumn("tx-type", "Type", PageTableColumnType.Select)
        val amount = PageTableColumn("tx-amount", "Amount", PageTableColumnType.Number)
        val status = PageTableColumn("tx-status", "Status", PageTableColumnType.Status)
        val summaryCategory = PageTableColumn("summary-category", "Category", PageTableColumnType.Select)
        val summaryType = PageTableColumn("summary-type", "Type", PageTableColumnType.Select)
        val summaryTotal = PageTableColumn("summary-total", "Total", PageTableColumnType.Number)
        val summaryStatus = PageTableColumn("summary-status", "Status", PageTableColumnType.Status)
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "transactions",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(
                        title = "Transactions",
                        columns = listOf(name, category, type, amount, status),
                        rows = listOf(
                            PageTableRow("salary", mapOf(name.id to "Salary", category.id to "Salary", type.id to "Income", amount.id to "1488", status.id to "Confirmed")),
                            PageTableRow("food", mapOf(name.id to "Food", category.id to "Food", type.id to "Expense", amount.id to "3", status.id to "Confirmed")),
                            PageTableRow("fuel", mapOf(name.id to "Fuel", category.id to "Fuel", type.id to "Expense", amount.id to "RM5", status.id to "Confirmed")),
                        ),
                    ),
                ),
                PageBlock(
                    id = "summary",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(
                        title = "Monthly Summary",
                        columns = listOf(summaryCategory, summaryType, summaryTotal, summaryStatus),
                    ),
                ),
            ),
        )

        val synced = document.withBudgetLedgerSummarySynced()
        val summary = synced.blocks.single { block -> block.id == "summary" }.table
        fun total(categoryName: String): String {
            val row = summary.rows.single { item -> item.cells[summaryCategory.id] == categoryName }
            return row.cells[summaryTotal.id].orEmpty()
        }

        assertEquals("8", total("Known Expenses"))
        assertEquals("1488", total("Income"))
        assertEquals("1480", total("Balance"))
        assertTrue(summary.columns.single { column -> column.id == summaryCategory.id }.config.options.any { option -> option.name == "Fuel" })
    }
}
