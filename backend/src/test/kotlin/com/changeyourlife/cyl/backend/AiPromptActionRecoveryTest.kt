package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.service.AiPromptActionRecovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    }
}
