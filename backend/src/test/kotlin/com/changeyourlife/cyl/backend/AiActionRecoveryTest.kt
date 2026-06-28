package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.service.AiService
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AiActionRecoveryTest {
    private val service = AiService(openRouterApiKey = "test-key")

    @Test
    fun recoversMalayPropertyActionWhenModelJsonFails() {
        val result = service.recoverActionFromPrompt(
            prompt = "tambah property deadline date dekat @Tasks",
            pages = listOf(AiPageContext(id = "page-1", title = "Tasks")),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("ADD_PROPERTY", action.type)
        assertEquals("Tasks", action.targetTitle)
        assertEquals("deadline", action.propertyName)
        assertEquals("Date", action.propertyType)
    }

    @Test
    fun recoversMalayBlockUpdateWithExistingBlockId() {
        val result = service.recoverActionFromPrompt(
            prompt = "ubah block meeting lama kepada meeting baru dekat @Meeting",
            pages = listOf(
                AiPageContext(
                    id = "page-2",
                    title = "Meeting",
                    blocks = listOf(
                        AiBlockContext(
                            id = "block-1",
                            type = "Text",
                            text = "meeting lama",
                        ),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("UPDATE_BLOCK", action.type)
        assertEquals("Meeting", action.targetTitle)
        assertEquals("block-1", action.blockId)
        assertEquals("meeting lama", action.blockText)
        assertEquals("meeting baru", action.content)
    }

    @Test
    fun recoversMentionContextPageIdForTableCreation() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                buat jadual penjagaan ayam

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Reban Ayam id=page-ayam
            """.trimIndent(),
            pages = listOf(
                AiPageContext(id = "page-other", title = "Wrong Page"),
                AiPageContext(id = "page-ayam", title = "Reban Ayam"),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("CREATE_DATABASE", action.type)
        assertEquals("Reban Ayam", action.targetTitle)
        assertEquals("penjagaan ayam", action.tableTitle)
        assertEquals("Table", action.tableView)
        assertEquals(listOf("Name", "Status", "Notes"), action.tableColumns.map { it.name })
    }

    @Test
    fun recoversDeleteAllBlocksForMentionedPage() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                padam semua block dalam page tersebut

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Nota Ayam id=page-nota
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-nota",
                    title = "Nota Ayam",
                    blocks = listOf(
                        AiBlockContext(id = "block-1", type = "Text", text = "Makan pagi"),
                        AiBlockContext(id = "block-2", type = "Quote", text = "Jaga reban"),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("DELETE_ALL_BLOCKS", action.type)
        assertEquals("Nota Ayam", action.targetTitle)
    }

    @Test
    fun recoversAddRowInsteadOfCreatingDatabase() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                tambah satu row yang saya guna beli makan 4 ringgit

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Belanja Harian id=page-belanja
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-belanja",
                    title = "Belanja Harian",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-1",
                            type = "DatabaseTable",
                            text = "title=Expenses; columns=Name Text, Amount Number; rows=",
                            tableTitle = "Expenses",
                        ),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("ADD_TABLE_ROW", action.type)
        assertEquals("Belanja Harian", action.targetTitle)
        assertEquals("Expenses", action.tableTitle)
        assertEquals("beli makan", action.rowTitle)
        assertEquals("4", action.cellValues["Amount"])
        assertEquals("4", action.cellValues["Jumlah"])
        assertNull(action.cellValues["Task"])
    }

    @Test
    fun recoversLegacyModelJsonReplyIntoTableRowAction() {
        val result = service.recoverActionFromModelReply(
            reply = """{"page":"@Budget Tracker","action":"update","data":[{"id":"id1","category":"fuel","amount":"5"}]}""",
            prompt = "tambah fuel amount 5",
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget Tracker",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-1",
                            type = "DatabaseTable",
                            text = "title=Expenses; columns=Category Text, Amount Number; rows=",
                            tableTitle = "Expenses",
                        ),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("ADD_TABLE_ROW", action.type)
        assertEquals("Budget Tracker", action.targetTitle)
        assertEquals("Expenses", action.tableTitle)
        assertEquals("fuel", action.rowTitle)
        assertEquals("fuel", action.cellValues["Category"])
        assertEquals("5", action.cellValues["Amount"])
        assertNull(action.cellValues["Id"])
    }

    @Test
    fun recoversMultipleMalayCommandsWithoutPuttingPromptIntoRow() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                padam semua block, Dan untuk catat duit bulanan, Dan tu dah untuk makan harini, sekali tarikh

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Belanja Harian id=page-belanja
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-belanja",
                    title = "Belanja Harian",
                    blocks = listOf(
                        AiBlockContext(id = "block-1", type = "Text", text = "old note"),
                        AiBlockContext(
                            id = "table-1",
                            type = "DatabaseTable",
                            text = "title=Expenses; columns=Name Text, Amount Number, Date Date; rows=",
                            tableTitle = "Expenses",
                        ),
                    ),
                ),
            ),
        )

        val actions = assertNotNull(result).actions
        assertEquals("DELETE_ALL_BLOCKS", actions[0].type)
        val rowAction = actions[1]
        assertEquals("ADD_TABLE_ROW", rowAction.type)
        assertEquals("makan", rowAction.rowTitle)
        assertEquals(LocalDate.now().toString(), rowAction.cellValues["Date"])
        assertNull(rowAction.cellValues["Task"])
    }

    @Test
    fun recoversCreateTableThenAddRowAsSeparateActions() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                buat jadual belanja bulanan, dan tambah row beli makan 4 ringgit harini

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Belanja id=page-belanja
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-belanja",
                    title = "Belanja",
                ),
            ),
        )

        val actions = assertNotNull(result).actions
        assertEquals(2, actions.size)
        assertEquals("CREATE_DATABASE", actions[0].type)
        assertEquals("belanja bulanan", actions[0].tableTitle)

        val rowAction = actions[1]
        assertEquals("ADD_TABLE_ROW", rowAction.type)
        assertEquals("belanja bulanan", rowAction.tableTitle)
        assertEquals("beli makan", rowAction.rowTitle)
        assertEquals("4", rowAction.cellValues["Amount"])
        assertEquals(LocalDate.now().toString(), rowAction.cellValues["Date"])
        assertNull(rowAction.cellValues["Task"])
    }

    @Test
    fun recoversExpenseContextAsDatabaseWithoutExplicitTableWord() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                untuk catat duit bulanan

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Belanja id=page-belanja
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-belanja",
                    title = "Belanja",
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("CREATE_DATABASE", action.type)
        assertEquals("duit bulanan", action.tableTitle)
        assertEquals(listOf("Name", "Amount", "Date", "Notes"), action.tableColumns.map { it.name })
    }

    @Test
    fun recoversRowOnEmptyPageByCreatingTableFirst() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                tambah row beli makan 4 ringgit harini

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Belanja id=page-belanja
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-belanja",
                    title = "Belanja",
                ),
            ),
        )

        val actions = assertNotNull(result).actions
        assertEquals(2, actions.size)
        assertEquals("CREATE_DATABASE", actions[0].type)
        assertEquals("Belanja", actions[0].tableTitle)

        val rowAction = actions[1]
        assertEquals("ADD_TABLE_ROW", rowAction.type)
        assertEquals("Belanja", rowAction.tableTitle)
        assertEquals("beli makan", rowAction.rowTitle)
        assertEquals("4", rowAction.cellValues["Amount"])
        assertEquals(LocalDate.now().toString(), rowAction.cellValues["Date"])
        assertNull(rowAction.cellValues["Task"])
    }

    @Test
    fun recoversMalayNoteWriteForMentionedPage() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                buat nota ayam makan pagi

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Reban id=page-reban
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-reban",
                    title = "Reban",
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("APPEND_BLOCK", action.type)
        assertEquals("Reban", action.targetTitle)
        assertEquals("ayam makan pagi", action.content)
    }

    @Test
    fun recoversMalayTableRenameWhenExplicitTitleGiven() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                @Budget Tracker ubah nama table kepada Belanja

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Budget Tracker id=page-budget
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget Tracker",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-1",
                            type = "DatabaseTable",
                            text = "title=Budget; columns=Name Text, Amount Number; rows=",
                            tableTitle = "Budget",
                        ),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("RENAME_TABLE", action.type)
        assertEquals("Budget Tracker", action.targetTitle)
        assertEquals("Belanja", action.title)
    }

    @Test
    fun doesNotRecoverCreativeMalayTableRenameAsLiteralDescriptor() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                @Budget Tracker ubah nama table dengan yang sensual Dan pendek

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Budget Tracker id=page-budget
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget Tracker",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-1",
                            type = "DatabaseTable",
                            text = "title=Budget; columns=Name Text, Amount Number; rows=",
                            tableTitle = "Budget",
                        ),
                    ),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun doesNotRecoverPlanningChatWithMentionAsMutation() {
        val result = service.recoverActionFromPrompt(
            prompt = """
                @Budget Tracker boleh plan macam mana nak susun budget bulanan?

                CYL_MENTION_CONTEXT:
                The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                - @Budget Tracker id=page-budget
            """.trimIndent(),
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget Tracker",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-1",
                            type = "DatabaseTable",
                            text = "title=Budget; columns=Name Text, Amount Number; rows=",
                            tableTitle = "Budget",
                        ),
                    ),
                ),
            ),
        )

        assertNull(result)
    }
}
