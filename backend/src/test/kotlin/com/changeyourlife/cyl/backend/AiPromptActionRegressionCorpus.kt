package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiTableCellContext
import com.changeyourlife.cyl.backend.model.ai.AiTableColumnContext
import com.changeyourlife.cyl.backend.model.ai.AiTableRowContext
import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import com.changeyourlife.cyl.backend.service.AiService
import kotlin.test.assertEquals
import kotlin.test.assertTrue

data class ExpectedPromptAction(
    val type: String,
    val title: String? = null,
    val targetTitle: String? = null,
    val tableTitle: String? = null,
    val blockId: String? = null,
    val rowId: String? = null,
    val rowTitle: String? = null,
    val columnId: String? = null,
    val columnName: String? = null,
    val columnType: String? = null,
    val value: String? = null,
    val content: String? = null,
    val filterQuery: String? = null,
    val delayMinutes: Long? = null,
    val requiredOptions: List<String> = emptyList(),
    val requiredCellValues: Map<String, String> = emptyMap(),
    val requiredColumns: List<ExpectedPromptColumn> = emptyList(),
    val requiredRows: List<Map<String, String>> = emptyList(),
)

data class ExpectedPromptColumn(
    val name: String,
    val type: String,
    val requiredOptions: List<String> = emptyList(),
)

data class AiPromptActionRegressionCase(
    val id: String,
    val messages: List<ChatMessage>,
    val pages: List<AiPageContext>,
    val providerReply: String,
    val expectedActions: List<ExpectedPromptAction>,
    val runWithLiveProvider: Boolean = false,
) {
    val latestUserPrompt: String
        get() = messages.last { message -> message.role.equals("user", ignoreCase = true) }.content

    fun assertResult(result: AiService.AiActionResult) {
        assertEquals(
            expected = expectedActions.map(ExpectedPromptAction::type),
            actual = result.actions.map(AiService.AiActionItem::type),
            message = "$id returned the wrong action sequence. Reply: ${result.reply}",
        )
        assertTrue(
            actual = result.validationIssues.isEmpty(),
            message = "$id returned contract issues: ${result.validationIssues}",
        )
        expectedActions.zip(result.actions).forEachIndexed { index, (expected, actual) ->
            expected.title?.let { value ->
                assertEquals(value, actual.title, "$id action $index has the wrong title.")
            }
            expected.targetTitle?.let { value ->
                assertEquals(value, actual.targetTitle, "$id action $index has the wrong target page.")
            }
            expected.tableTitle?.let { value ->
                assertEquals(value, actual.tableTitle, "$id action $index has the wrong table.")
            }
            expected.blockId?.let { value ->
                assertEquals(value, actual.blockId, "$id action $index lost its block id.")
            }
            expected.rowId?.let { value ->
                assertEquals(value, actual.rowId, "$id action $index lost its row id.")
            }
            expected.rowTitle?.let { value ->
                assertEquals(value, actual.rowTitle, "$id action $index has the wrong row.")
            }
            expected.columnId?.let { value ->
                assertEquals(value, actual.columnId, "$id action $index lost its column id.")
            }
            expected.columnName?.let { value ->
                assertEquals(value, actual.columnName, "$id action $index has the wrong column.")
            }
            expected.columnType?.let { value ->
                assertEquals(value, actual.columnType, "$id action $index has the wrong column type.")
            }
            expected.value?.let { value ->
                assertEquals(value, actual.value, "$id action $index has the wrong value.")
            }
            expected.content?.let { value ->
                assertEquals(value, actual.content, "$id action $index has the wrong content.")
            }
            expected.filterQuery?.let { value ->
                assertEquals(value, actual.filterQuery, "$id action $index has the wrong filter query.")
            }
            expected.delayMinutes?.let { value ->
                assertEquals(value, actual.delayMinutes, "$id action $index has the wrong reminder delay.")
            }
            assertTrue(
                actual = actual.options.containsAll(expected.requiredOptions),
                message = "$id action $index is missing required action options.",
            )
            expected.requiredCellValues.forEach { (column, value) ->
                assertEquals(
                    expected = value,
                    actual = actual.cellValues[column],
                    message = "$id action $index has the wrong value for $column.",
                )
            }
            expected.requiredColumns.forEach { expectedColumn ->
                val actualColumn = actual.tableColumns.firstOrNull { column ->
                    column.name.equals(expectedColumn.name, ignoreCase = true)
                }
                assertTrue(
                    actual = actualColumn != null,
                    message = "$id action $index is missing column ${expectedColumn.name}.",
                )
                assertEquals(
                    expected = expectedColumn.type,
                    actual = actualColumn.type,
                    message = "$id action $index has the wrong type for ${expectedColumn.name}.",
                )
                assertTrue(
                    actual = actualColumn.options.containsAll(expectedColumn.requiredOptions),
                    message = "$id action $index is missing options for ${expectedColumn.name}.",
                )
            }
            expected.requiredRows.forEach { expectedRow ->
                assertTrue(
                    actual = actual.tableRows.any { actualRow ->
                        expectedRow.all { (column, value) -> actualRow[column] == value }
                    },
                    message = "$id action $index is missing expected row $expectedRow.",
                )
            }
        }
    }

    override fun toString(): String = id
}

object AiPromptActionRegressionCorpus {
    val cases: List<AiPromptActionRegressionCase> = listOf(
        AiPromptActionRegressionCase(
            id = "malay-create-monthly-expenses",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "buatkan page baru untuk bulan 7 punya monthly expenses, dengan gaji 1488",
                ),
            ),
            pages = emptyList(),
            providerReply = """
                {
                  "reply": "Siap, saya buat halaman perbelanjaan bulan 7.",
                  "actions": [
                    {
                      "type": "CREATE_PAGE",
                      "title": "July Monthly Expenses",
                      "tableTitle": "Transactions",
                      "tableColumns": [
                        {"name": "Name", "type": "Text"},
                        {"name": "Date", "type": "Date"},
                        {"name": "Month", "type": "Select", "options": ["2026-07"]},
                        {"name": "Category", "type": "Select", "options": ["Salary", "Food", "Fuel", "Other"]},
                        {"name": "Type", "type": "Select", "options": ["Expense", "Income", "Debt"]},
                        {"name": "Amount", "type": "Number"},
                        {"name": "Status", "type": "Status", "options": ["Confirmed", "Incomplete", "Empty"]},
                        {"name": "Notes", "type": "Text"}
                      ],
                      "tableRows": [
                        {
                          "Name": "Salary",
                          "Month": "2026-07",
                          "Category": "Salary",
                          "Type": "Income",
                          "Amount": "1488",
                          "Status": "Confirmed"
                        }
                      ]
                    },
                    {
                      "type": "CREATE_DATABASE",
                      "targetTitle": "July Monthly Expenses",
                      "tableTitle": "Monthly Summary",
                      "tableColumns": [
                        {"name": "Month", "type": "Select", "options": ["2026-07"]},
                        {"name": "Status", "type": "Status", "options": ["Confirmed", "Incomplete", "Empty"]},
                        {"name": "Notes", "type": "Text"}
                      ],
                      "tableRows": [
                        {"Month": "2026-07", "Status": "Confirmed", "Notes": "Initial salary recorded"}
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            expectedActions = listOf(
                ExpectedPromptAction(
                    type = "CREATE_PAGE",
                    title = "July Monthly Expenses",
                    tableTitle = "Transactions",
                    requiredColumns = listOf(
                        ExpectedPromptColumn("Month", "Select", listOf("2026-07")),
                        ExpectedPromptColumn("Category", "Select", listOf("Salary", "Food", "Fuel")),
                        ExpectedPromptColumn("Amount", "Number"),
                        ExpectedPromptColumn("Status", "Status"),
                    ),
                    requiredRows = listOf(
                        mapOf(
                            "Name" to "Salary",
                            "Type" to "Income",
                            "Amount" to "1488",
                        ),
                    ),
                ),
                ExpectedPromptAction(
                    type = "CREATE_DATABASE",
                    targetTitle = "July Monthly Expenses",
                    tableTitle = "Monthly Summary",
                    requiredColumns = listOf(
                        ExpectedPromptColumn("Month", "Select", listOf("2026-07")),
                    ),
                ),
            ),
            runWithLiveProvider = true,
        ),
        AiPromptActionRegressionCase(
            id = "malay-add-expense-row",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "dalam Budget Tracker tambah belanja makeup 29 ringgit hari ini",
                ),
            ),
            pages = budgetPages(),
            providerReply = """
                {
                  "reply": "Siap, saya tambah perbelanjaan makeup.",
                  "actions": [
                    {
                      "type": "ADD_TABLE_ROW",
                      "targetTitle": "Budget Tracker",
                      "blockId": "table-transactions",
                      "tableTitle": "Transactions",
                      "rowTitle": "Makeup",
                      "cellValues": {
                        "Name": "Makeup",
                        "Date": "2026-07-28",
                        "Month": "2026-07",
                        "Category": "Makeup",
                        "Type": "Expense",
                        "Amount": "29",
                        "Status": "Confirmed"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            expectedActions = listOf(
                ExpectedPromptAction(
                    type = "ADD_TABLE_ROW",
                    targetTitle = "Budget Tracker",
                    tableTitle = "Transactions",
                    blockId = "table-transactions",
                    rowTitle = "Makeup",
                    requiredCellValues = mapOf(
                        "Category" to "Makeup",
                        "Amount" to "29",
                        "Type" to "Expense",
                    ),
                ),
            ),
            runWithLiveProvider = true,
        ),
        AiPromptActionRegressionCase(
            id = "malay-create-dropdown-options",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "tambah column Category dropdown Food, Fuel, Makeup dan Transport",
                ),
            ),
            pages = budgetPages(),
            providerReply = """
                {
                  "reply": "Siap, saya tambah dropdown Category.",
                  "actions": [
                    {
                      "type": "ADD_TABLE_COLUMN",
                      "targetTitle": "Budget Tracker",
                      "blockId": "table-transactions",
                      "tableTitle": "Transactions",
                      "columnName": "Category",
                      "columnType": "Select",
                      "options": ["Food", "Fuel", "Makeup", "Transport"]
                    }
                  ]
                }
            """.trimIndent(),
            expectedActions = listOf(
                ExpectedPromptAction(
                    type = "ADD_TABLE_COLUMN",
                    targetTitle = "Budget Tracker",
                    tableTitle = "Transactions",
                    blockId = "table-transactions",
                    columnName = "Category",
                    columnType = "Select",
                    requiredOptions = listOf("Food", "Fuel", "Makeup", "Transport"),
                ),
            ),
            runWithLiveProvider = true,
        ),
        AiPromptActionRegressionCase(
            id = "malay-bulk-clear-month",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "kosongkan semua cell Month yang bulan 4 dalam Transactions",
                ),
            ),
            pages = budgetPages(),
            providerReply = """
                {
                  "reply": "Siap, saya kosongkan semua cell Month yang sepadan.",
                  "actions": [
                    {
                      "type": "CLEAR_TABLE_CELLS",
                      "targetTitle": "Budget Tracker",
                      "blockId": "table-transactions",
                      "tableTitle": "Transactions",
                      "columnId": "column-month",
                      "columnName": "Month",
                      "filterQuery": "2026-04"
                    }
                  ]
                }
            """.trimIndent(),
            expectedActions = listOf(
                ExpectedPromptAction(
                    type = "CLEAR_TABLE_CELLS",
                    targetTitle = "Budget Tracker",
                    tableTitle = "Transactions",
                    blockId = "table-transactions",
                    columnId = "column-month",
                    columnName = "Month",
                    filterQuery = "2026-04",
                ),
            ),
            runWithLiveProvider = true,
        ),
        AiPromptActionRegressionCase(
            id = "malay-delete-database-block",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "padam database Transactions dalam Budget Tracker",
                ),
            ),
            pages = budgetPages(),
            providerReply = """
                {
                  "reply": "Siap, saya padam database Transactions.",
                  "actions": [
                    {
                      "type": "DELETE_BLOCK",
                      "targetTitle": "Budget Tracker",
                      "blockId": "table-transactions",
                      "blockType": "DatabaseTable",
                      "blockText": "Transactions"
                    }
                  ]
                }
            """.trimIndent(),
            expectedActions = listOf(
                ExpectedPromptAction(
                    type = "DELETE_BLOCK",
                    targetTitle = "Budget Tracker",
                    blockId = "table-transactions",
                ),
            ),
        ),
        AiPromptActionRegressionCase(
            id = "multi-page-explicit-targets",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "ubah Amount row Makan dalam Budget Tracker jadi 12, kemudian tambah nota Semak resit dalam Notes",
                ),
            ),
            pages = budgetAndNotesPages(),
            providerReply = """
                {
                  "reply": "Siap, saya kemas kini kedua-dua halaman.",
                  "actions": [
                    {
                      "type": "UPDATE_TABLE_CELL",
                      "targetTitle": "Budget Tracker",
                      "blockId": "table-transactions",
                      "tableTitle": "Transactions",
                      "rowId": "row-food",
                      "rowTitle": "Makan",
                      "columnId": "column-amount",
                      "columnName": "Amount",
                      "value": "12"
                    },
                    {
                      "type": "APPEND_BLOCK",
                      "targetTitle": "Notes",
                      "blockType": "Text",
                      "content": "Semak resit"
                    }
                  ]
                }
            """.trimIndent(),
            expectedActions = listOf(
                ExpectedPromptAction(
                    type = "UPDATE_TABLE_CELL",
                    targetTitle = "Budget Tracker",
                    blockId = "table-transactions",
                    rowId = "row-food",
                    columnId = "column-amount",
                    value = "12",
                ),
                ExpectedPromptAction(
                    type = "APPEND_BLOCK",
                    targetTitle = "Notes",
                    content = "Semak resit",
                ),
            ),
        ),
        AiPromptActionRegressionCase(
            id = "malay-create-reminder",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "ingatkan saya bayar elektrik dalam 30 minit",
                ),
            ),
            pages = budgetPages(),
            providerReply = """
                {
                  "reply": "Baik, saya tetapkan peringatan bayar elektrik.",
                  "actions": [
                    {
                      "type": "CREATE_REMINDER",
                      "targetTitle": "Budget Tracker",
                      "tableTitle": "Transactions",
                      "title": "Bayar elektrik",
                      "delayMinutes": 30
                    }
                  ]
                }
            """.trimIndent(),
            expectedActions = listOf(
                ExpectedPromptAction(
                    type = "CREATE_REMINDER",
                    title = "Bayar elektrik",
                    delayMinutes = 30,
                ),
            ),
        ),
        AiPromptActionRegressionCase(
            id = "conversation-does-not-mutate",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "apa cadangan terbaik untuk jimat belanja makan bulan ini?",
                ),
            ),
            pages = budgetPages(),
            providerReply = """
                {
                  "reply": "Kita boleh semak pola belanja makan dahulu dan tetapkan had mingguan.",
                  "actions": []
                }
            """.trimIndent(),
            expectedActions = emptyList(),
            runWithLiveProvider = true,
        ),
        AiPromptActionRegressionCase(
            id = "pending-clarification-resumes-exact-cell",
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = """
                        CYL_PENDING_CLARIFICATION:
                        The previous edit is suspended, not discarded.
                        issueFields=rowId,rowTitle
                        issueCodes=ambiguous_target
                        action={"type":"CLEAR_TABLE_CELL","targetTitle":"Budget Tracker","blockId":"table-transactions","tableTitle":"Transactions","columnId":"column-month","columnName":"Month"}
                    """.trimIndent(),
                ),
                ChatMessage(role = "user", content = "padam cell Month"),
                ChatMessage(role = "assistant", content = "Row yang mana?"),
                ChatMessage(role = "user", content = "Makan"),
            ),
            pages = budgetPages(),
            providerReply = """
                {
                  "reply": "Siap, saya kosongkan Month untuk row Makan.",
                  "actions": [
                    {
                      "type": "CLEAR_TABLE_CELL",
                      "targetTitle": "Budget Tracker",
                      "blockId": "table-transactions",
                      "tableTitle": "Transactions",
                      "rowId": "row-food",
                      "rowTitle": "Makan",
                      "columnId": "column-month",
                      "columnName": "Month"
                    }
                  ]
                }
            """.trimIndent(),
            expectedActions = listOf(
                ExpectedPromptAction(
                    type = "CLEAR_TABLE_CELL",
                    targetTitle = "Budget Tracker",
                    tableTitle = "Transactions",
                    blockId = "table-transactions",
                    rowId = "row-food",
                    rowTitle = "Makan",
                    columnId = "column-month",
                    columnName = "Month",
                ),
            ),
            runWithLiveProvider = true,
        ),
    )

    val liveCases: List<AiPromptActionRegressionCase> =
        cases.filter(AiPromptActionRegressionCase::runWithLiveProvider)

    private fun budgetAndNotesPages(): List<AiPageContext> =
        budgetPages() + AiPageContext(
            id = "page-notes",
            title = "Notes",
            blocks = listOf(
                AiBlockContext(
                    id = "block-note",
                    type = "Text",
                    text = "Resit lama",
                ),
            ),
            isFocused = false,
        )

    private fun budgetPages(): List<AiPageContext> = listOf(
        AiPageContext(
            id = "page-budget",
            title = "Budget Tracker",
            blocks = listOf(
                AiBlockContext(
                    id = "table-transactions",
                    type = "DatabaseTable",
                    text = "title=Transactions",
                    tableTitle = "Transactions",
                    tableBlockId = "table-transactions",
                    tableColumns = listOf(
                        AiTableColumnContext(id = "column-name", name = "Name", type = "Text"),
                        AiTableColumnContext(id = "column-date", name = "Date", type = "Date"),
                        AiTableColumnContext(id = "column-month", name = "Month", type = "Select"),
                        AiTableColumnContext(id = "column-category", name = "Category", type = "Select"),
                        AiTableColumnContext(id = "column-type", name = "Type", type = "Select"),
                        AiTableColumnContext(id = "column-amount", name = "Amount", type = "Number"),
                        AiTableColumnContext(id = "column-status", name = "Status", type = "Status"),
                        AiTableColumnContext(id = "column-notes", name = "Notes", type = "Text"),
                    ),
                    tableRows = listOf(
                        AiTableRowContext(
                            id = "row-food",
                            title = "Makan",
                            cells = listOf(
                                AiTableCellContext("column-name", "Name", "Makan"),
                                AiTableCellContext("column-month", "Month", "2026-04"),
                                AiTableCellContext("column-category", "Category", "Food"),
                                AiTableCellContext("column-amount", "Amount", "4"),
                            ),
                        ),
                        AiTableRowContext(
                            id = "row-fuel",
                            title = "Minyak",
                            cells = listOf(
                                AiTableCellContext("column-name", "Name", "Minyak"),
                                AiTableCellContext("column-month", "Month", "2026-04"),
                                AiTableCellContext("column-category", "Category", "Fuel"),
                                AiTableCellContext("column-amount", "Amount", "5"),
                            ),
                        ),
                    ),
                    totalRowCount = 2,
                    contextComplete = true,
                ),
            ),
            totalBlockCount = 1,
            isFocused = true,
            contextComplete = true,
        ),
    )
}
