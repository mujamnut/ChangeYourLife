package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.service.AiPromptActionRecovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiPromptActionRecoveryTest {
    private val recovery = AiPromptActionRecovery()

    @Test
    fun recoversDeleteAllBlocksFromMalayPromptWithMention() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "padam semua block dalam @Budget Tracker",
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget Tracker",
                    blocks = listOf(
                        AiBlockContext(id = "block-1", type = "Text", text = "old note"),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("DELETE_ALL_BLOCKS", action.type)
        assertEquals("Budget Tracker", action.targetTitle)
    }

    @Test
    fun recoversMalayExpenseAsTableRowWithoutInstructionText() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "saya guna 29 ringgit harini beli makeup",
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget Tracker",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-1",
                            type = "DatabaseTable",
                            text = "title=Budget Tracker",
                            tableTitle = "Budget Tracker",
                        ),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("ADD_TABLE_ROW", action.type)
        assertEquals("Budget Tracker", action.targetTitle)
        assertEquals("Budget Tracker", action.tableTitle)
        assertEquals("29", action.cellValues["Amount"])
        assertEquals("makeup", action.cellValues["Name"])
        assertEquals("Makeup", action.cellValues["Category"])
        assertEquals("Expense", action.cellValues["Type"])
        assertEquals("Confirmed", action.cellValues["Status"])
    }

    @Test
    fun recoversExpenseRowIntoTransactionsTableWhenBudgetPageHasSummaryToo() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "saya guna 5 ringgit harini beli minyak moto",
            pages = listOf(
                AiPageContext(
                    id = "page-july",
                    title = "July Monthly Expenses",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-transactions",
                            type = "DatabaseTable",
                            tableTitle = "Transactions",
                        ),
                        AiBlockContext(
                            id = "table-summary",
                            type = "DatabaseTable",
                            tableTitle = "Monthly Summary",
                        ),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("ADD_TABLE_ROW", action.type)
        assertEquals("July Monthly Expenses", action.targetTitle)
        assertEquals("Transactions", action.tableTitle)
        assertEquals("5", action.cellValues["Amount"])
        assertEquals("Minyak Moto", action.cellValues["Category"])
        assertEquals("Expense", action.cellValues["Type"])
        assertEquals("Confirmed", action.cellValues["Status"])
    }

    @Test
    fun recoversHomePromptForNewMonthlyExpensePageWithSalary() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "buatkan page baru untuk bulan 7 punya monthly expenses,dengan gaji 1488",
            pages = emptyList(),
        )

        val actions = assertNotNull(result).actions
        assertEquals(2, actions.size)
        val action = actions[0]
        val summary = actions[1]
        assertEquals("CREATE_PAGE", action.type)
        assertEquals("July Monthly Expenses", action.title)
        assertEquals("Transactions", action.tableTitle)
        assertEquals(
            listOf("Name", "Date", "Month", "Category", "Type", "Amount", "Status", "Notes"),
            action.tableColumns.map { column -> column.name },
        )
        assertEquals("Salary", action.tableRows.single()["Name"])
        assertEquals("${java.time.LocalDate.now().year}-07", action.tableRows.single()["Month"])
        assertEquals("Income", action.tableRows.single()["Type"])
        assertEquals("1488", action.tableRows.single()["Amount"])
        assertEquals("CREATE_DATABASE", summary.type)
        assertEquals("July Monthly Expenses", summary.targetTitle)
        assertEquals("Monthly Summary", summary.tableTitle)
        assertEquals(listOf("Month", "Status", "Notes"), summary.tableColumns.map { column -> column.name })
        assertTrue(summary.tableRows.any { row -> row["Month"] == "${java.time.LocalDate.now().year}-07" })
    }

    @Test
    fun recoversMalayIndonesianExpensePagePromptWithoutNewKeyword() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "buatkan halaman pengeluaran bulan 7 dengan gaji 1488",
            pages = emptyList(),
        )

        val action = assertNotNull(result).actions.first()
        assertEquals("CREATE_PAGE", action.type)
        assertEquals("July Monthly Expenses", action.title)
        assertEquals("Transactions", action.tableTitle)
        assertEquals("Salary", action.tableRows.single()["Name"])
        assertEquals("${java.time.LocalDate.now().year}-07", action.tableRows.single()["Month"])
        assertEquals("1488", action.tableRows.single()["Amount"])
    }

    @Test
    fun recoversHomeTableRequestAsPageWithTable() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "buat jadual penjagaan ayam",
            pages = listOf(AiPageContext(id = "page-existing", title = "Existing")),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("CREATE_PAGE", action.type)
        assertEquals("Penjagaan Ayam", action.title)
        assertEquals("Penjagaan Ayam", action.tableTitle)
        assertEquals(listOf("Name", "Status", "Notes"), action.tableColumns.map { column -> column.name })
    }

    @Test
    fun keepsMentionedTableRequestScopedToExistingPage() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "buat jadual penjagaan ayam dalam @Reban Ayam",
            pages = listOf(AiPageContext(id = "page-reban", title = "Reban Ayam")),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("CREATE_DATABASE", action.type)
        assertEquals("Reban Ayam", action.targetTitle)
        assertEquals("penjagaan ayam", action.tableTitle)
    }

    @Test
    fun doesNotFuzzyMatchShortMentionToLongerPageTitle() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "padam semua block dalam @Budget",
            pages = listOf(
                AiPageContext(
                    id = "page-budget-tracker",
                    title = "Budget Tracker",
                    blocks = listOf(AiBlockContext(id = "block-1", type = "Text", text = "old note")),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun doesNotPickFirstMentionWhenMultipleMentionContextHasNoVisibleTarget() {
        val result = recovery.recoverActionFromPrompt(
            prompt = """
                padam semua block dalam page tersebut

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Budget id=page-budget
                - @Notes id=page-notes
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget",
                    blocks = listOf(AiBlockContext(id = "budget-block", type = "Text", text = "budget")),
                ),
                AiPageContext(
                    id = "page-notes",
                    title = "Notes",
                    blocks = listOf(AiBlockContext(id = "notes-block", type = "Text", text = "notes")),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun resolvesExactVisibleTargetWhenMultipleMentionContextExists() {
        val result = recovery.recoverActionFromPrompt(
            prompt = """
                @Notes padam semua block

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Budget id=page-budget
                - @Notes id=page-notes
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget",
                    blocks = listOf(AiBlockContext(id = "budget-block", type = "Text", text = "budget")),
                ),
                AiPageContext(
                    id = "page-notes",
                    title = "Notes",
                    blocks = listOf(AiBlockContext(id = "notes-block", type = "Text", text = "notes")),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("DELETE_ALL_BLOCKS", action.type)
        assertEquals("Notes", action.targetTitle)
    }

    @Test
    fun recoversBudgetTrackerWithoutPageKeywordAsMonthlyExpensePage() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "buat budget tracker bulan 7 dengan gaji 1488",
            pages = emptyList(),
        )

        val action = assertNotNull(result).actions.first()
        assertEquals("CREATE_PAGE", action.type)
        assertEquals("July Monthly Expenses", action.title)
        assertEquals("Transactions", action.tableTitle)
        assertEquals("Salary", action.tableRows.single()["Name"])
        assertEquals("${java.time.LocalDate.now().year}-07", action.tableRows.single()["Month"])
        assertEquals("1488", action.tableRows.single()["Amount"])
    }

    @Test
    fun recoversHomeExpensePageWithExplicitDropdownOptions() {
        val result = recovery.recoverActionFromPrompt(
            prompt = "buat page expense bulan 7, ada database dengan Category dropdown Food, Fuel, Makeup, Transport dan Status dropdown Planned, Paid",
            pages = emptyList(),
        )

        val action = assertNotNull(result).actions.first()
        assertEquals("CREATE_PAGE", action.type)
        assertEquals("July Monthly Expenses", action.title)
        assertEquals("Transactions", action.tableTitle)
        val month = action.tableColumns.single { column -> column.name == "Month" }
        assertEquals("Select", month.type)
        assertEquals(listOf("${java.time.LocalDate.now().year}-07"), month.options)

        val category = action.tableColumns.single { column -> column.name == "Category" }
        assertEquals("Select", category.type)
        assertEquals(listOf("Food", "Fuel", "Makeup", "Transport"), category.options)

        val status = action.tableColumns.single { column -> column.name == "Status" }
        assertEquals("Status", status.type)
        assertEquals(listOf("Planned", "Paid"), status.options)
    }

    @Test
    fun recoversRawMonthlyExpenseLedgerAsTransactionsAndSummary() {
        val result = recovery.recoverActionFromPrompt(
            prompt = """
                masukkan ini dalam table,Bulan 7
                Makan : 3+8.9+4+5+
                Minyak moto : 5
                Internet :
                Letrik: 10+
                Barang lain :
                Spaylater : 65.54
                Ttshop : 93.68
                =159.22
                Hutang -
                kak amani : 400
                mak : 200
                Gaji : 1488
                -159.22
                -
            """.trimIndent(),
            pages = emptyList(),
        )

        val actions = assertNotNull(result).actions
        assertEquals(2, actions.size)
        val transactions = actions[0]
        val summary = actions[1]

        assertEquals("CREATE_PAGE", transactions.type)
        assertEquals("July Monthly Expenses", transactions.title)
        assertEquals("Transactions", transactions.tableTitle)
        assertEquals(listOf("Name", "Date", "Month", "Category", "Type", "Amount", "Status", "Notes"), transactions.tableColumns.map { it.name })

        val category = transactions.tableColumns.single { column -> column.name == "Category" }
        assertEquals("Select", category.type)
        assertTrue(category.options.containsAll(listOf("Makan", "Minyak Moto", "Internet", "Letrik", "Barang Lain", "Spaylater", "Ttshop", "Kak Amani", "Mak", "Gaji")))

        val rows = transactions.tableRows
        assertTrue(rows.all { row -> row["Month"] == "${java.time.LocalDate.now().year}-07" })
        assertTrue(rows.any { row -> row["Category"] == "Makan" && row["Amount"] == "8.9" && row["Status"] == "Incomplete" })
        assertTrue(rows.any { row -> row["Category"] == "Minyak Moto" && row["Amount"] == "5" && row["Type"] == "Expense" })
        assertTrue(rows.any { row -> row["Category"] == "Letrik" && row["Amount"] == "10" && row["Status"] == "Incomplete" })
        assertTrue(rows.any { row -> row["Category"] == "Kak Amani" && row["Amount"] == "400" && row["Type"] == "Debt" })
        assertTrue(rows.any { row -> row["Category"] == "Mak" && row["Amount"] == "200" && row["Type"] == "Debt" })
        assertTrue(rows.any { row -> row["Category"] == "Gaji" && row["Amount"] == "1488" && row["Type"] == "Income" })

        assertEquals("CREATE_DATABASE", summary.type)
        assertEquals("July Monthly Expenses", summary.targetTitle)
        assertEquals("Monthly Summary", summary.tableTitle)
        assertEquals(listOf("Month", "Status", "Notes"), summary.tableColumns.map { it.name })
        assertTrue(summary.tableRows.any { row -> row["Month"] == "${java.time.LocalDate.now().year}-07" && row["Status"] == "Incomplete" })
    }
}
