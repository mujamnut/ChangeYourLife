package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiTableCellContext
import com.changeyourlife.cyl.backend.model.ai.AiTableColumnContext
import com.changeyourlife.cyl.backend.model.ai.AiTableRowContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiActionContextBuilderTest {
    @Test
    fun includesRowsBeyondLegacyTwelveAndTwentyRowLimits() {
        val rows = (1..40).map { index ->
            row(index = index, value = "Expense $index")
        }
        val result = AiActionContextBuilder().build(
            pages = listOf(page(title = "Monthly Expenses", rows = rows, focused = true)),
            tasks = emptyList(),
            latestUserPrompt = "analisis semua transaksi Monthly Expenses",
            clientDate = "2026-07-28",
            clientTimezone = "Asia/Kuala_Lumpur",
        )

        assertTrue(result.text.contains("row-40"))
        assertTrue(result.text.contains("Expense 40"))
        assertEquals(40, result.includedRowCount)
        assertEquals(40, result.totalRowCount)
        assertEquals("FULL", result.coverage)
    }

    @Test
    fun manifestListsPagesBeyondLegacyTwentyFivePageLimit() {
        val pages = (1..35).map { index ->
            AiPageContext(
                id = "page-$index",
                title = "Page $index",
                totalBlockCount = 0,
            )
        }
        val result = AiActionContextBuilder().build(
            pages = pages,
            tasks = emptyList(),
            latestUserPrompt = "senaraikan workspace",
            clientDate = "",
            clientTimezone = "",
        )

        assertTrue(result.text.contains("id=\"page-35\" title=\"Page 35\""))
        assertEquals(35, result.totalPageCount)
    }

    @Test
    fun focusedPageWinsBudgetAndPartialCoverageIsExplicit() {
        val oversizedRows = (1..30).map { index ->
            row(index = index, value = "x".repeat(3_000))
        }
        val focusedRows = listOf(row(index = 999, value = "needle-value"))
        val result = AiActionContextBuilder(maxDetailChars = 8_000).build(
            pages = listOf(
                page(title = "Archive", rows = oversizedRows),
                page(title = "Current Budget", rows = focusedRows, focused = true),
            ),
            tasks = emptyList(),
            latestUserPrompt = "kira Current Budget",
            clientDate = "",
            clientTimezone = "",
        )

        assertTrue(result.text.contains("needle-value"))
        assertEquals("PARTIAL", result.coverage)
        assertTrue(result.text.contains("CYL_CONTEXT_COVERAGE: status=PARTIAL"))
        assertTrue(result.text.contains("omitted data must not be treated as empty"))
        assertFalse(result.includedRowCount == result.totalRowCount)
    }

    @Test
    fun metadataPageAppearsInManifestWithoutExposingItsBlocks() {
        val result = AiActionContextBuilder().build(
            pages = listOf(
                AiPageContext(
                    id = "target",
                    title = "Target",
                    access = "Target",
                    blocks = listOf(
                        AiBlockContext(id = "visible", type = "Text", text = "visible-content"),
                    ),
                ),
                AiPageContext(
                    id = "metadata",
                    title = "Catalog only",
                    access = "Metadata",
                    blocks = listOf(
                        AiBlockContext(
                            id = "hidden",
                            type = "DatabaseTable",
                            text = "must-not-leak",
                            tableTitle = "Secret table title",
                            tableRows = listOf(row(index = 999, value = "must-not-leak")),
                        ),
                    ),
                ),
            ),
            tasks = emptyList(),
            latestUserPrompt = "summarize target",
            clientDate = "",
            clientTimezone = "",
        )

        assertTrue(result.text.contains("id=\"metadata\" title=\"Catalog only\" access=Metadata"))
        assertTrue(result.text.contains("visible-content"))
        assertFalse(result.text.contains("must-not-leak"))
        assertFalse(result.text.contains("Secret table title"))
        assertFalse(
            result.text.lineSequence()
                .first { line -> line.contains("id=\"metadata\"") }
                .contains("blocks="),
        )
        assertEquals(1, result.detailedPageCount)
        assertEquals(1, result.totalPageCount)
    }

    private fun page(
        title: String,
        rows: List<AiTableRowContext>,
        focused: Boolean = false,
    ): AiPageContext {
        val table = AiBlockContext(
            id = "table-${title.lowercase().replace(' ', '-')}",
            type = "DatabaseTable",
            text = "sort=none; filter=none; groupBy=none",
            tableTitle = "Transactions",
            tableBlockId = "table-${title.lowercase().replace(' ', '-')}",
            tableColumns = listOf(
                AiTableColumnContext(id = "name", name = "Name", type = "Text"),
            ),
            tableRows = rows,
            totalRowCount = rows.size,
        )
        return AiPageContext(
            id = "page-${title.lowercase().replace(' ', '-')}",
            title = title,
            blocks = listOf(table),
            totalBlockCount = 1,
            isFocused = focused,
        )
    }

    private fun row(index: Int, value: String): AiTableRowContext =
        AiTableRowContext(
            id = "row-$index",
            title = value,
            cells = listOf(
                AiTableCellContext(
                    columnId = "name",
                    columnName = "Name",
                    value = value,
                ),
            ),
        )
}
