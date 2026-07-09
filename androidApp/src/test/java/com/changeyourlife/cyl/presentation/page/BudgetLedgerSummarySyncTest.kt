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
        val transactionsBlock = synced.blocks.single { block -> block.id == "transactions" }
        val summaryBlock = synced.blocks.single { block -> block.id == "summary" }
        val transactions = transactionsBlock.table
        val summary = summaryBlock.table
        val references = synced.tableReferences()
        val monthRow = summary.rowWhere("Month", NoMonthLabelForTest)

        assertEquals(
            listOf("Name", "Date", "Month", "Category", "Type", "Amount", "Status", "Notes"),
            transactions.columns.map { column -> column.name },
        )
        assertEquals("8", summary.displayValue(monthRow, "Known Expenses", references))
        assertEquals("1488", summary.displayValue(monthRow, "Income", references))
        assertEquals("1480", summary.displayValue(monthRow, "Balance", references))
        assertTrue(transactions.columns.single { column -> column.name == "Category" }.config.options.any { option -> option.name == "Fuel" })
    }

    @Test
    fun migratesLegacyMonthlyBudgetTableToTransactionsAndSummary() {
        val category = PageTableColumn("legacy-category", "Category", PageTableColumnType.Text)
        val budget = PageTableColumn("legacy-budget", "Budget", PageTableColumnType.Number)
        val actual = PageTableColumn("legacy-actual", "Actual", PageTableColumnType.Number)
        val variance = PageTableColumn("legacy-variance", "Variance", PageTableColumnType.Number)
        val notes = PageTableColumn("legacy-notes", "Notes", PageTableColumnType.Text)
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "legacy-budget",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(
                        title = "July Monthly Expenses",
                        columns = listOf(category, budget, actual, variance, notes),
                        rows = listOf(
                            PageTableRow(
                                "salary",
                                mapOf(
                                    category.id to "Gaji",
                                    budget.id to "1488",
                                    actual.id to "1488",
                                    variance.id to "0",
                                ),
                            ),
                            PageTableRow(
                                "food",
                                mapOf(
                                    category.id to "Makan",
                                    budget.id to "300",
                                    actual.id to "15.90",
                                    variance.id to "-284.10",
                                    notes.id to "awal bulan",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val synced = document.withBudgetLedgerSummarySynced()

        assertEquals(listOf("Transactions", "Monthly Summary"), synced.blocks.map { block -> block.table.title })
        val transactions = synced.blocks.single { block -> block.table.title == "Transactions" }.table
        assertEquals(listOf("Name", "Date", "Month", "Category", "Type", "Amount", "Status", "Notes"), transactions.columns.map { column -> column.name })
        val salaryRow = transactions.rowWhere("Name", "Gaji")
        assertEquals("${java.time.LocalDate.now().year}-07", transactions.cell(salaryRow, "Month"))
        assertEquals("Income", transactions.cell(salaryRow, "Type"))
        assertEquals("1488", transactions.cell(salaryRow, "Amount"))
        val foodRow = transactions.rowWhere("Name", "Makan")
        assertEquals("Expense", transactions.cell(foodRow, "Type"))
        assertEquals("15.9", transactions.cell(foodRow, "Amount"))
        assertEquals("awal bulan; Variance: -284.10", transactions.cell(foodRow, "Notes"))

        val summary = synced.blocks.single { block -> block.table.title == "Monthly Summary" }.table
        val references = synced.tableReferences()
        val monthRow = summary.rowWhere("Month", "${java.time.LocalDate.now().year}-07")
        assertEquals("1488", summary.displayValue(monthRow, "Income", references))
        assertEquals("15.9", summary.displayValue(monthRow, "Known Expenses", references))
        assertEquals("1472.1", summary.displayValue(monthRow, "Balance", references))
    }

    @Test
    fun migratesLegacyBudgetTemplateRowsToTransactionRows() {
        val item = PageTableColumn("legacy-item", "Item", PageTableColumnType.Text)
        val category = PageTableColumn("legacy-category", "Category", PageTableColumnType.Text)
        val amount = PageTableColumn("legacy-amount", "Amount", PageTableColumnType.Number)
        val dueDate = PageTableColumn("legacy-due-date", "Due date", PageTableColumnType.Date)
        val paid = PageTableColumn("legacy-paid", "Paid", PageTableColumnType.Checkbox)
        val notes = PageTableColumn("legacy-notes", "Notes", PageTableColumnType.Text)
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "legacy-budget-template",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(
                        title = "Budget",
                        columns = listOf(item, category, amount, dueDate, paid, notes),
                        rows = listOf(
                            PageTableRow(
                                "fuel",
                                mapOf(
                                    item.id to "Minyak moto",
                                    category.id to "Fuel",
                                    amount.id to "RM5",
                                    dueDate.id to "2026-07-09",
                                    paid.id to "true",
                                    notes.id to "cash",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val synced = document.withBudgetLedgerSummarySynced()

        val transactions = synced.blocks.single { block -> block.table.title == "Transactions" }.table
        val fuelRow = transactions.rowWhere("Name", "Minyak Moto")
        assertEquals("2026-07-09", transactions.cell(fuelRow, "Date"))
        assertEquals("2026-07", transactions.cell(fuelRow, "Month"))
        assertEquals("Fuel", transactions.cell(fuelRow, "Category"))
        assertEquals("Expense", transactions.cell(fuelRow, "Type"))
        assertEquals("5", transactions.cell(fuelRow, "Amount"))
        assertEquals("Confirmed", transactions.cell(fuelRow, "Status"))
        assertEquals("cash", transactions.cell(fuelRow, "Notes"))

        val summary = synced.blocks.single { block -> block.table.title == "Monthly Summary" }.table
        val references = synced.tableReferences()
        val monthRow = summary.rowWhere("Month", "2026-07")
        assertEquals("5", summary.displayValue(monthRow, "Known Expenses", references))
    }

    private fun PageTable.rowWhere(columnName: String, value: String): PageTableRow {
        val columnId = columnId(columnName)
        return rows.single { row -> row.cells[columnId] == value }
    }

    private fun PageTable.cell(row: PageTableRow, columnName: String): String =
        row.cells[columnId(columnName)].orEmpty()

    private fun PageTable.displayValue(
        row: PageTableRow,
        columnName: String,
        references: List<PageTableReference>,
    ): String = displayCellText(row, columns.single { column -> column.name == columnName }, references)

    private fun PageTable.columnId(columnName: String): String =
        columns.single { column -> column.name == columnName }.id

    private fun PageBlockDocument.tableReferences(): List<PageTableReference> =
        blocks
            .filter { block -> block.type == PageBlockType.DatabaseTable }
            .map { block -> PageTableReference(blockId = block.id, title = block.table.title, table = block.table) }

    private companion object {
        const val NoMonthLabelForTest = "No month"
    }
}
