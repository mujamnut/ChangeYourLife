package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.service.AiActionPlanner
import com.changeyourlife.cyl.backend.service.AiService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AiActionPlannerTest {
    private val planner = AiActionPlanner()

    @Test
    fun prefersPromptRowPlanWhenModelOnlyCreatesTable() {
        val modelResult = AiService.AiActionResult(
            reply = "Siap - saya buat table itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "CREATE_DATABASE",
                    targetTitle = "Budget Tracker",
                    tableTitle = "Budget Tracker",
                ),
            ),
        )
        val promptResult = AiService.AiActionResult(
            reply = "Siap - saya tambah row itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "ADD_TABLE_ROW",
                    targetTitle = "Budget Tracker",
                    tableTitle = "Budget Tracker",
                    cellValues = mapOf(
                        "Name" to "makeup",
                        "Amount" to "29",
                        "Date" to "today",
                    ),
                ),
            ),
        )

        val result = planner.selectActionResult(
            prompt = "saya guna 29 ringgit harini beli makeup",
            modelResult = modelResult,
            promptResult = promptResult,
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("ADD_TABLE_ROW", action.type)
        assertEquals("29", action.cellValues["Amount"])
    }

    @Test
    fun prefersPromptRowPlanWhenModelStoresInstructionAsTaskCell() {
        val modelResult = AiService.AiActionResult(
            reply = "Siap - saya tambah row itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "ADD_TABLE_ROW",
                    targetTitle = "Tasks",
                    tableTitle = "Tasks",
                    cellValues = mapOf(
                        "Task" to "delete semua block, Dan untuk catat duit bulanan, Dan tu dah untuk makan harini",
                    ),
                ),
            ),
        )
        val promptResult = AiService.AiActionResult(
            reply = "Siap - saya tambah row itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "ADD_TABLE_ROW",
                    targetTitle = "Budget Tracker",
                    tableTitle = "Budget Tracker",
                    cellValues = mapOf(
                        "Name" to "makan",
                        "Amount" to "4",
                        "Date" to "today",
                    ),
                ),
            ),
        )

        val result = planner.selectActionResult(
            prompt = "saya guna beli makan 4 ringgit harini",
            modelResult = modelResult,
            promptResult = promptResult,
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("Budget Tracker", action.targetTitle)
        assertEquals("makan", action.cellValues["Name"])
        assertEquals("4", action.cellValues["Amount"])
    }

    @Test
    fun keepsModelResultWhenPromptRecoveryHasNoAction() {
        val modelResult = AiService.AiActionResult(
            reply = "Saya boleh bantu rancang dahulu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "ADD_BLOCK",
                    targetTitle = "Plan",
                    content = "Draft outline",
                    blockType = "Text",
                ),
            ),
        )

        val result = planner.selectActionResult(
            prompt = "add outline to @Plan",
            modelResult = modelResult,
            promptResult = null,
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("ADD_BLOCK", action.type)
        assertEquals("Draft outline", action.content)
    }

    @Test
    fun doesNotUsePromptFallbackForCreativeTableCreationWhenModelHasNoAction() {
        val promptResult = AiService.AiActionResult(
            reply = "Siap - saya buat table itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "CREATE_PAGE",
                    title = "Penjagaan Ayam",
                    tableTitle = "Penjagaan Ayam",
                ),
            ),
        )

        val result = planner.selectActionResult(
            prompt = "buat jadual penjagaan ayam",
            modelResult = null,
            promptResult = promptResult,
        )

        assertNull(result)
    }

    @Test
    fun keepsModelEmptyActionReplyInsteadOfInventingCreativeTableFallback() {
        val modelResult = AiService.AiActionResult(
            reply = "Saya boleh buat jadual itu, tapi belum ada action JSON.",
            actions = emptyList(),
        )
        val promptResult = AiService.AiActionResult(
            reply = "Siap - saya buat table itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "CREATE_DATABASE",
                    targetTitle = "Ayam",
                    tableTitle = "Penjagaan Ayam",
                ),
            ),
        )

        val result = planner.selectActionResult(
            prompt = "buat jadual penjagaan ayam dalam @Ayam",
            modelResult = modelResult,
            promptResult = promptResult,
        )

        assertEquals(modelResult, result)
    }
}
