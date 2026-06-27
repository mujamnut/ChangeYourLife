package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.service.AiService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    }
}
