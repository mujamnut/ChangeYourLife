package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.remote.ai.AiActionDto
import com.changeyourlife.cyl.data.remote.ai.AiTableColumnDto
import com.changeyourlife.cyl.data.remote.ai.ChatWithActionsResponseDto
import org.junit.Assert.assertEquals
import org.junit.Test

class AiActionContractMapperTest {
    @Test
    fun mapsValidCreateDatabaseWithSelectOptions() {
        val result = AiActionContractMapper.toDomain(
            ChatWithActionsResponseDto(
                reply = "Siap.",
                actions = listOf(
                    AiActionDto(
                        type = "CREATE_DATABASE",
                        title = "Expense Bulan 7",
                        tableTitle = "Expense",
                        tableColumns = listOf(
                            AiTableColumnDto(
                                name = "Category",
                                type = "Select",
                                options = listOf("Food", "Fuel", "Makeup", "Transport"),
                            ),
                            AiTableColumnDto(
                                name = "Status",
                                type = "Status",
                                options = listOf("Planned", "Paid"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(emptyList<String>(), result.validationIssues.map { issue -> issue.code })
        assertEquals("CREATE_DATABASE", result.actions.single().type)
        assertEquals(listOf("Food", "Fuel", "Makeup", "Transport"), result.actions.single().tableColumns.first().options)
    }

    @Test
    fun flagsMixedColumnFieldsInsideAddRowAction() {
        val result = AiActionContractMapper.toDomain(
            ChatWithActionsResponseDto(
                reply = "Siap.",
                actions = listOf(
                    AiActionDto(
                        type = "ADD_TABLE_ROW",
                        tableTitle = "Budget",
                        rowTitle = "Makan",
                        cellValues = mapOf("Amount" to "4"),
                        columnName = "Category",
                        options = listOf("Food", "Fuel"),
                    ),
                    AiActionDto(
                        type = "ADD_TABLE_ROW",
                        tableTitle = "Budget",
                        rowTitle = "Fuel",
                        cellValues = mapOf("Amount" to "5"),
                    ),
                ),
            ),
        )

        assertEquals(listOf("ADD_TABLE_ROW", "ADD_TABLE_ROW"), result.actions.map { action -> action.type })
        assertEquals(1, result.validationIssues.size)
        assertEquals(0, result.validationIssues.single().actionIndex)
        assertEquals("UNEXPECTED_ACTION_FIELDS", result.validationIssues.single().code)
        assertEquals("columnName,options", result.validationIssues.single().field)
    }

    @Test
    fun flagsRenameWithoutConcreteTitle() {
        val result = AiActionContractMapper.toDomain(
            ChatWithActionsResponseDto(
                reply = "Siap.",
                actions = listOf(
                    AiActionDto(
                        type = "RENAME_TABLE",
                        tableTitle = "Budget",
                    ),
                ),
            ),
        )

        assertEquals(1, result.validationIssues.size)
        assertEquals("MISSING_REQUIRED_ACTION_FIELDS", result.validationIssues.single().code)
        assertEquals("content|newColumnName|title|value", result.validationIssues.single().field)
    }
}
