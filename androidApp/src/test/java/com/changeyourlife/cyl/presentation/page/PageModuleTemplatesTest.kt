package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageModuleTemplatesTest {
    @Test
    fun budgetTemplateUsesTransactionLedgerAndSummary() {
        val document = PageBlockCodec.decodeDocument(PageModuleTemplates.contentFor(PageModuleType.Budget))
        val tables = document.blocks.filter { block -> block.type == PageBlockType.DatabaseTable }

        assertEquals(2, tables.size)

        val transactions = tables[0].table
        assertEquals("Transactions", transactions.title)
        assertEquals(
            listOf("Name", "Date", "Month", "Category", "Type", "Amount", "Status", "Notes"),
            transactions.columns.map { column -> column.name },
        )
        assertEquals(PageTableColumnType.Select, transactions.columns.single { column -> column.name == "Month" }.type)
        assertEquals(PageTableColumnType.Select, transactions.columns.single { column -> column.name == "Category" }.type)
        assertTrue(
            transactions.columns.single { column -> column.name == "Category" }
                .config.options.map { option -> option.name }
                .containsAll(listOf("Salary", "Food", "Fuel", "Makeup", "Transport", "Other")),
        )
        assertTrue(
            transactions.columns.single { column -> column.name == "Type" }
                .config.options.map { option -> option.name }
                .containsAll(listOf("Expense", "Income", "Debt")),
        )

        val summary = tables[1].table
        assertEquals("Monthly Summary", summary.title)
        assertEquals(
            listOf(
                "Month",
                "Income Transactions",
                "Expense Transactions",
                "Debt Transactions",
                "Income",
                "Known Expenses",
                "Debt",
                "Balance",
                "Status",
                "Notes",
            ),
            summary.columns.map { column -> column.name },
        )
        assertTrue(summary.columns.single { column -> column.name == "Income Transactions" }.config.isHidden)
        assertEquals(PageTableColumnType.Rollup, summary.columns.single { column -> column.name == "Income" }.type)
        assertEquals(PageTableColumnType.Rollup, summary.columns.single { column -> column.name == "Known Expenses" }.type)
        assertEquals(PageTableColumnType.Formula, summary.columns.single { column -> column.name == "Balance" }.type)
        assertEquals(
            "{Income} - {Known Expenses} - {Debt}",
            summary.columns.single { column -> column.name == "Balance" }.formula,
        )
    }
}
