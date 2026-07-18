package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import com.changeyourlife.cyl.backend.service.AiService
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun rejectsModelMediaBlockActionWithoutUri() {
        val result = service.recoverActionFromModelReply(
            reply = """
                {
                  "reply": "Done",
                  "actions": [
                    {
                      "type": "ADD_BLOCK",
                      "targetTitle": "Receipts",
                      "title": "Receipt",
                      "blockType": "MediaFile"
                    }
                  ]
                }
            """.trimIndent(),
            prompt = "add receipt file to @Receipts",
            pages = listOf(AiPageContext(id = "page-1", title = "Receipts")),
        )

        val issue = assertNotNull(result).validationIssues.single()
        assertEquals(emptyList(), result.actions)
        assertEquals("mediaUri", issue.field)
        assertEquals("missing_required_field", issue.code)
    }

    @Test
    fun normalizesEmptyCellUpdateIntoExplicitClearCellAction() {
        val result = service.recoverActionFromModelReply(
            reply = """
                {
                  "reply": "Siap",
                  "actions": [
                    {
                      "type": "UPDATE_TABLE_CELL",
                      "targetTitle": "Monthly Expenses",
                      "rowTitle": "bulan 4",
                      "columnName": "Month"
                    }
                  ]
                }
            """.trimIndent(),
            prompt = "padam cell bulan 4",
            pages = listOf(
                AiPageContext(
                    id = "page-monthly",
                    title = "Monthly Expenses",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-primary",
                            type = "DatabaseTable",
                            tableTitle = "First table",
                        ),
                        AiBlockContext(
                            id = "table-secondary",
                            type = "DatabaseTable",
                            tableTitle = "Second table",
                        ),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals(emptyList(), result.validationIssues)
        assertEquals("CLEAR_TABLE_CELL", action.type)
        assertEquals("Monthly Expenses", action.targetTitle)
        assertEquals("", action.tableTitle)
        assertEquals("bulan 4", action.rowTitle)
        assertEquals("Month", action.columnName)
    }

    @Test
    fun recoversMalayExpenseRowWithTodayDate() {
        val result = service.recoverActionFromPrompt(
            prompt = "saya guna 29 ringgit harini beli makeup",
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget Tracker",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-budget",
                            type = "DatabaseTable",
                            text = "title=Budget; columns=Name Text, Amount Number, Date Date; rows=",
                            tableTitle = "Budget",
                        ),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("ADD_TABLE_ROW", action.type)
        assertEquals("Budget Tracker", action.targetTitle)
        assertEquals("Budget", action.tableTitle)
        assertEquals("makeup", action.rowTitle)
        assertEquals("makeup", action.cellValues["Name"])
        assertEquals("29", action.cellValues["Amount"])
        assertEquals(LocalDate.now().toString(), action.cellValues["Date"])
    }

    @Test
    fun preservesModelMediaBlockPayload() {
        val result = service.recoverActionFromModelReply(
            reply = """
                {
                  "reply": "Done",
                  "actions": [
                    {
                      "type": "ADD_BLOCK",
                      "targetTitle": "Receipts",
                      "title": "Receipt",
                      "blockType": "MediaFile",
                      "mediaUri": "content://receipts/receipt.png",
                      "mediaName": "receipt.png",
                      "mediaMimeType": "image/png",
                      "mediaSizeBytes": 1234
                    }
                  ]
                }
            """.trimIndent(),
            prompt = "add receipt file to @Receipts",
            pages = listOf(AiPageContext(id = "page-1", title = "Receipts")),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals(emptyList(), result.validationIssues)
        assertEquals("ADD_BLOCK", action.type)
        assertEquals("Receipts", action.targetTitle)
        assertEquals("content://receipts/receipt.png", action.mediaUri)
        assertEquals("receipt.png", action.mediaName)
        assertEquals("image/png", action.mediaMimeType)
        assertEquals(1234L, action.mediaSizeBytes)
    }

    @Test
    fun rejectsModelFormulaColumnActionWithoutFormula() {
        val result = service.recoverActionFromModelReply(
            reply = """
                {
                  "reply": "Done",
                  "actions": [
                    {
                      "type": "UPDATE_FORMULA_COLUMN",
                      "targetTitle": "Budget",
                      "tableTitle": "Budget",
                      "columnName": "Total"
                    }
                  ]
                }
            """.trimIndent(),
            prompt = "update Total formula in @Budget",
            pages = listOf(AiPageContext(id = "page-1", title = "Budget")),
        )

        val issue = assertNotNull(result).validationIssues.single()
        assertEquals(emptyList(), result.actions)
        assertEquals("formula", issue.field)
        assertEquals("missing_required_field", issue.code)
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
        assertEquals("makan", action.rowTitle)
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
    fun modelJsonUnsupportedActionIsReportedButNotExecutable() {
        val result = service.recoverActionFromModelReply(
            reply = """{"reply":"Saya akan cuba.","actions":[{"type":"MAKE_MAGIC","title":"Budget"}]}""",
            prompt = "@Budget buat magic",
            pages = listOf(AiPageContext(id = "page-budget", title = "Budget")),
        )

        val recovered = assertNotNull(result)
        assertEquals(emptyList(), recovered.actions)
        val issue = recovered.validationIssues.single()
        assertEquals("type", issue.field)
        assertEquals("unsupported_action_type", issue.code)
    }

    @Test
    fun chatWithActionsDoesNotRecoverActionWhenSandboxReplyHasNoStructuredActions() = runBlocking {
        val sandboxService = AiService()
        val result = sandboxService.chatWithActions(
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = """
                        tambah fuel amount 5

                        CYL_MENTION_CONTEXT:
                        The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions:
                        - @Budget Tracker id=page-budget
                    """.trimIndent(),
                ),
            ),
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

        assertEquals(emptyList(), result.actions)
    }

    @Test
    fun chatWithActionsDoesNotInventHomeExpensePageWhenModelHasNoAction() = runBlocking {
        val sandboxService = AiService()
        val result = sandboxService.chatWithActions(
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "buatkan halaman pengeluaran bulan 7 dengan gaji 1488",
                ),
            ),
            pages = emptyList(),
        )

        assertEquals(emptyList(), result.actions)
    }

    @Test
    fun chatWithActionsDoesNotInventHomeTableWhenModelHasNoAction() = runBlocking {
        val sandboxService = AiService()
        val result = sandboxService.chatWithActions(
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "buat jadual penjagaan ayam",
                ),
            ),
            pages = emptyList(),
        )

        assertEquals(emptyList(), result.actions)
    }

    @Test
    fun chatWithActionsUsesModelActionBeforePromptFallback() = runBlocking {
        val aiFirstService = AiService(
            openRouterApiKey = "test-key",
            completionProvider = { messages, jsonMode, _ ->
                assertTrue(jsonMode)
                assertTrue(messages.first().content.contains("Return ONLY one valid JSON object"))
                """
                    {
                      "reply": "Siap - saya buat jadual ikut arahan AI.",
                      "actions": [
                        {
                          "type": "CREATE_PAGE",
                          "title": "AI Planned Chicken Care",
                          "tableTitle": "Care Schedule",
                          "tableColumns": [
                            { "name": "When", "type": "Text" },
                            { "name": "Task", "type": "Text" }
                          ],
                          "tableRows": [
                            { "When": "Morning", "Task": "Feed chickens" }
                          ]
                        }
                      ]
                    }
                """.trimIndent()
            },
        )

        val result = aiFirstService.chatWithActions(
            messages = listOf(ChatMessage(role = "user", content = "buat jadual penjagaan ayam")),
            pages = emptyList(),
        )

        val action = result.actions.single()
        assertEquals("CREATE_PAGE", action.type)
        assertEquals("AI Planned Chicken Care", action.title)
        assertEquals("Care Schedule", action.tableTitle)
        assertEquals("Morning", action.tableRows.single()["When"])
    }

    @Test
    fun chatWithActionsDoesNotLetPromptRecoveryTakeOverPlainModelReply() = runBlocking {
        val conversationalService = AiService(
            openRouterApiKey = "test-key",
            completionProvider = { _, jsonMode, _ ->
                assertTrue(jsonMode)
                "Saya boleh bantu tambah row itu, tetapi saya belum menghantar action JSON."
            },
        )

        val result = conversationalService.chatWithActions(
            messages = listOf(ChatMessage(role = "user", content = "tambah row fuel dalam @Budget Tracker")),
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

        assertEquals(emptyList(), result.actions)
        assertTrue(result.reply.contains("belum menghantar action JSON"))
    }

    @Test
    fun modelJsonWithEmptyActionsKeepsConversationalReply() {
        val result = service.recoverActionFromModelReply(
            reply = """{"reply":"Boleh, kita bincang dulu tanpa ubah app.","actions":[]}""",
            prompt = "kita planning dulu",
            pages = emptyList(),
        )

        val recovered = assertNotNull(result)
        assertEquals("Boleh, kita bincang dulu tanpa ubah app.", recovered.reply)
        assertEquals(emptyList(), recovered.actions)
        assertEquals(emptyList(), recovered.validationIssues)
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
        assertEquals("makan", rowAction.rowTitle)
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
        assertEquals("makan", rowAction.rowTitle)
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

    @Test
    fun recoversLegacyModelJsonRowWithoutLeakingNullOrIdFields() {
        val result = service.recoverActionFromModelReply(
            reply = """
                {"page":"@Budget Tracker","action":"update","data":[{"id":"id1","category":"fuel","amount":"5","note":null}]}
            """.trimIndent(),
            prompt = "tambah fuel 5 ringgit dekat @Budget Tracker",
            pages = listOf(
                AiPageContext(
                    id = "page-budget",
                    title = "Budget Tracker",
                    blocks = listOf(
                        AiBlockContext(
                            id = "table-1",
                            type = "DatabaseTable",
                            text = "title=Budget; columns=Name Text, Amount Number, Notes Text; rows=",
                            tableTitle = "Budget",
                        ),
                    ),
                ),
            ),
        )

        val action = assertNotNull(result).actions.single()
        assertEquals("ADD_TABLE_ROW", action.type)
        assertEquals("Budget Tracker", action.targetTitle)
        assertEquals("Budget", action.tableTitle)
        assertEquals("fuel", action.rowTitle)
        assertEquals("5", action.cellValues["Amount"])
        assertNull(action.cellValues["Id"])
        assertNull(action.cellValues["Note"])
    }

    @Test
    fun selectorRepairsModelWhenItStoresRowPromptAsTableName() {
        val modelResult = AiService.AiActionResult(
            reply = "Siap - saya buat table itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "CREATE_DATABASE",
                    targetTitle = "Budget Tracker",
                    tableTitle = "saya guna 29 ringgit harini beli makeup",
                ),
            ),
        )
        val promptResult = AiService.AiActionResult(
            reply = "Siap - saya tambah row itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "ADD_TABLE_ROW",
                    targetTitle = "Budget Tracker",
                    tableTitle = "Budget",
                    rowTitle = "makeup",
                    cellValues = mapOf(
                        "Name" to "makeup",
                        "Amount" to "29",
                        "Date" to LocalDate.now().toString(),
                    ),
                ),
            ),
        )

        val selected = service.selectActionResultForPrompt(
            prompt = "saya guna 29 ringgit harini beli makeup",
            modelResult = modelResult,
            promptResult = promptResult,
        )

        val action = assertNotNull(selected).actions.single()
        assertEquals("ADD_TABLE_ROW", action.type)
        assertEquals("makeup", action.rowTitle)
        assertEquals("29", action.cellValues["Amount"])
    }

    @Test
    fun selectorKeepsValidModelResultWhenPromptRecoveryHasExtraSegments() {
        val modelResult = AiService.AiActionResult(
            reply = "Siap - saya tambah row itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "ADD_TABLE_ROW",
                    targetTitle = "Belanja",
                    tableTitle = "Expenses",
                    rowTitle = "makan",
                ),
            ),
        )
        val promptResult = AiService.AiActionResult(
            reply = "Siap - saya buat perubahan itu.",
            actions = listOf(
                AiService.AiActionItem(
                    type = "DELETE_ALL_BLOCKS",
                    targetTitle = "Belanja",
                ),
                AiService.AiActionItem(
                    type = "ADD_TABLE_ROW",
                    targetTitle = "Belanja",
                    tableTitle = "Expenses",
                    rowTitle = "makan",
                    cellValues = mapOf("Date" to LocalDate.now().toString()),
                ),
            ),
        )

        val selected = service.selectActionResultForPrompt(
            prompt = "padam semua block, dan catat makan harini",
            modelResult = modelResult,
            promptResult = promptResult,
        )

        val actions = assertNotNull(selected).actions
        assertEquals(listOf("ADD_TABLE_ROW"), actions.map { it.type })
        assertEquals("makan", actions.single().rowTitle)
    }
}
