package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.service.AiActionSchemaValidator
import com.changeyourlife.cyl.backend.service.AiService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiActionSchemaValidatorTest {
    private val validator = AiActionSchemaValidator()

    @Test
    fun normalizesSupportedActionType() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "add table row",
                    rowTitle = "Makeup",
                ),
            ),
        )

        assertEquals(emptyList(), result.issues)
        assertEquals("ADD_TABLE_ROW", result.actions.single().type)
    }

    @Test
    fun rejectsUnsupportedActionType() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "CREATE_DATABASE_WITH_MAGIC",
                    title = "Budget",
                ),
            ),
        )

        assertEquals(emptyList(), result.actions)
        val issue = result.issues.single()
        assertEquals("type", issue.field)
        assertEquals("unsupported_action_type", issue.code)
    }

    @Test
    fun rejectsMediaFileBlockWithoutUri() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "ADD_BLOCK",
                    blockType = "MediaFile",
                    title = "Receipt",
                ),
            ),
        )

        assertEquals(emptyList(), result.actions)
        val issue = result.issues.single()
        assertEquals("mediaUri", issue.field)
        assertEquals("missing_required_field", issue.code)
    }

    @Test
    fun rejectsFormulaColumnWithoutFormulaValueOrContent() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "UPDATE_FORMULA_COLUMN",
                    columnName = "Total",
                ),
            ),
        )

        assertEquals(emptyList(), result.actions)
        val issue = result.issues.single()
        assertEquals("formula", issue.field)
        assertEquals("missing_required_field", issue.code)
    }

    @Test
    fun acceptsCreateDatabaseWithSelectOptions() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "CREATE_DATABASE",
                    tableTitle = "Expense bulan 7",
                    tableColumns = listOf(
                        AiService.AiTableColumnItem(
                            name = "Category",
                            type = "Select",
                            options = listOf("Food", "Fuel", "Makeup", "Transport"),
                        ),
                        AiService.AiTableColumnItem(
                            name = "Status",
                            type = "Select",
                            options = listOf("Planned", "Paid"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(emptyList(), result.issues)
        assertEquals("CREATE_DATABASE", result.actions.single().type)
        assertEquals(listOf("Food", "Fuel", "Makeup", "Transport"), result.actions.single().tableColumns.first().options)
    }

    @Test
    fun acceptsAddTableColumnWithDropdownOptions() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "ADD_TABLE_COLUMN",
                    tableTitle = "Expense bulan 7",
                    columnName = "Category",
                    columnType = "Select",
                    options = listOf("Food", "Fuel", "Makeup", "Transport"),
                ),
            ),
        )

        assertEquals(emptyList(), result.issues)
        val action = result.actions.single()
        assertEquals("ADD_TABLE_COLUMN", action.type)
        assertEquals("Category", action.columnName)
        assertEquals(listOf("Food", "Fuel", "Makeup", "Transport"), action.options)
    }

    @Test
    fun rejectsTableRowActionWithColumnConfigFields() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "ADD_TABLE_ROW",
                    tableTitle = "Expense bulan 7",
                    rowTitle = "Makan",
                    cellValues = mapOf("Amount" to "4"),
                    columnName = "Category",
                    options = listOf("Food"),
                ),
            ),
        )

        assertEquals(emptyList(), result.actions)
        val issue = result.issues.single()
        assertEquals("columnName,options", issue.field)
        assertEquals("unexpected_action_fields", issue.code)
    }

    @Test
    fun invalidActionIssueCannotRejectFollowingValidActionAfterCompaction() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "ADD_TABLE_ROW",
                    tableTitle = "Budget",
                    columnName = "Category",
                ),
                AiService.AiActionItem(
                    type = "ADD_TABLE_ROW",
                    tableTitle = "Budget",
                    rowTitle = "Fuel",
                    cellValues = mapOf("Amount" to "5"),
                ),
            ),
        )

        assertEquals(listOf("Fuel"), result.actions.map { action -> action.rowTitle })
        assertNull(result.issues.single().actionIndex)
    }
}
