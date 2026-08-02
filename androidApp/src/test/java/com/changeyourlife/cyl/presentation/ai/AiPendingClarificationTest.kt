package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.ChatPendingActionMetadata
import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatActionResult
import com.changeyourlife.cyl.domain.repository.ChatActionValidationIssue
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPendingClarificationTest {
    @Test
    fun multiTurnClarificationPersistsRepairsAndExecutesTheSuspendedActionOnce() = runBlocking {
        val page = budgetPage()
        var firstTurnExecutions = 0
        val firstTurn = AiChatActionOrchestrator.orchestrate(
            workspaceId = "workspace-1",
            scopedTargetPage = page,
            prompt = "padam cell Month",
            backendResult = ChatActionResult(
                reply = "Siap.",
                actions = listOf(
                    ChatAction(
                        type = "CLEAR_TABLE_CELL",
                        title = "",
                        targetTitle = page.title,
                        blockId = "transactions",
                        tableTitle = "Transactions",
                        columnId = "month",
                        columnName = "Month",
                    ),
                ),
                validationIssues = listOf(
                    ChatActionValidationIssue(
                        actionIndex = 0,
                        field = "rowTitle",
                        code = "ambiguous_target",
                        message = "More than one row matched.",
                    ),
                ),
            ),
        ) { _, _, _ ->
            firstTurnExecutions++
            AiActionExecutionResult()
        }

        assertEquals(0, firstTurnExecutions)
        assertEquals(1, firstTurn.actionMetadata.pendingActions.size)
        val persistedMessages = listOf(
            ChatMessage(
                id = "assistant-1",
                sessionId = "session-1",
                role = "assistant",
                content = firstTurn.reply,
                pageLinks = emptyList(),
                actionMetadata = firstTurn.actionMetadata,
                createdAt = 1L,
            ),
        )
        val pendingActions = persistedMessages.latestPendingAiActions()
        val pendingContext = pendingActions.toPendingClarificationContext()
        assertTrue(pendingContext.contains("CYL_PENDING_CLARIFICATION"))
        assertTrue(pendingContext.contains("\"type\":\"CLEAR_TABLE_CELL\""))
        assertTrue(pendingContext.contains("issueFields=rowTitle"))

        val repairedResult = ChatActionResult(
            reply = "Siap, saya kosongkan cell itu.",
            actions = listOf(
                ChatAction(
                    type = "CLEAR_TABLE_CELL",
                    title = "",
                    rowId = "row-food",
                    rowTitle = "Makan",
                ),
            ),
        ).resolvePendingClarification(
            pendingActions = pendingActions,
            userPrompt = "row Makan",
            pages = listOf(page),
            scopedTargetPage = page,
        )
        val repairedAction = repairedResult.actions.single()
        assertEquals(page.title, repairedAction.targetTitle)
        assertEquals("transactions", repairedAction.blockId)
        assertEquals("month", repairedAction.columnId)
        assertEquals("row-food", repairedAction.rowId)

        var secondTurnExecutions = 0
        var executedAction: ChatAction? = null
        val secondTurn = AiChatActionOrchestrator.orchestrate(
            workspaceId = "workspace-1",
            scopedTargetPage = page,
            prompt = "row Makan",
            backendResult = repairedResult,
        ) { _, _, candidates ->
            secondTurnExecutions++
            executedAction = candidates.single().action
            AiActionExecutionResult(
                executedActionIndexes = candidates.map { candidate -> candidate.originalIndex },
            )
        }

        assertEquals(1, secondTurnExecutions)
        assertEquals("row-food", executedAction?.rowId)
        assertEquals("month", executedAction?.columnId)
        assertTrue(secondTurn.actionMetadata.pendingActions.isEmpty())
        assertEquals(
            listOf("CLEAR_TABLE_CELL"),
            secondTurn.actionMetadata.executedActions.map { action -> action.type },
        )
    }

    @Test
    fun destructiveConfirmationReplaysThePersistedPlanWithoutModelRegeneration() {
        val pending = listOf(
            ChatPendingActionMetadata(
                action = ChatAction(
                    type = "DELETE_TABLE_ROWS",
                    title = "",
                    targetTitle = "Budget",
                    tableTitle = "Transactions",
                    columnName = "Month",
                    filterQuery = "2026-04",
                ).toContractWire(),
                issueFields = listOf("confirmation"),
                issueCodes = listOf(DestructiveConfirmationRequiredCode),
            ),
        )

        assertEquals(
            AiPendingDestructiveDecision.None,
            pending.destructiveDecision("okay"),
        )
        assertEquals(
            AiPendingDestructiveDecision.Confirm,
            pending.destructiveDecision(ConfirmDestructiveActionsPrompt),
        )
        assertEquals(
            AiPendingDestructiveDecision.Cancel,
            pending.destructiveDecision(CancelDestructiveActionsPrompt),
        )

        val confirmed = pending.toConfirmedDestructiveActionResult()
        assertEquals(1, confirmed.actions.size)
        assertEquals("DELETE_TABLE_ROWS", confirmed.actions.single().type)
        assertEquals("2026-04", confirmed.actions.single().filterQuery)
    }

    @Test
    fun exactMalayRowReplyResolvesLocallyWhenProviderReturnsNoRepairedAction() {
        val page = budgetPage()
        val pendingAction = ChatAction(
            type = "CLEAR_TABLE_CELL",
            title = "",
            targetTitle = page.title,
            blockId = "transactions",
            tableTitle = "Transactions",
            columnId = "month",
            columnName = "Month",
        )

        val resolved = ChatActionResult(reply = "Row yang mana?")
            .resolvePendingClarification(
                pendingActions = listOf(
                    ChatPendingActionMetadata(
                        action = pendingAction.toContractWire(),
                        issueFields = listOf("rowTitle"),
                        issueCodes = listOf("ambiguous_target"),
                    ),
                ),
                userPrompt = "row Makan",
                pages = listOf(page),
                scopedTargetPage = page,
            )

        val action = resolved.actions.single()
        assertEquals("CLEAR_TABLE_CELL", action.type)
        assertEquals("row-food", action.rowId)
        assertEquals("Makan", action.rowTitle)
        assertEquals("month", action.columnId)
    }

    @Test
    fun allFollowUpConvertsAmbiguousSingleCellActionToBulkAction() {
        val page = budgetPage()
        val pendingAction = ChatAction(
            type = "CLEAR_TABLE_CELL",
            title = "",
            targetTitle = page.title,
            blockId = "transactions",
            tableTitle = "Transactions",
            columnId = "month",
            columnName = "Month",
            rowTitle = "bulan 4",
        )

        val resolved = ChatActionResult(reply = "Which row?")
            .resolvePendingClarification(
                pendingActions = listOf(
                    ChatPendingActionMetadata(
                        action = pendingAction.toContractWire(),
                        issueFields = listOf("rowTitle"),
                        issueCodes = listOf("ambiguous_target"),
                    ),
                ),
                userPrompt = "semua",
                pages = listOf(page),
                scopedTargetPage = page,
            )

        assertEquals(1, resolved.actions.size)
        assertEquals("CLEAR_TABLE_CELLS", resolved.actions.single().type)
        assertEquals("transactions", resolved.actions.single().blockId)
        assertEquals("month", resolved.actions.single().columnId)
        assertEquals("bulan 4", resolved.actions.single().filterQuery)
    }

    @Test
    fun repairedBackendActionPreservesHiddenPendingIdentity() {
        val pending = ChatAction(
            type = "UPDATE_TABLE_CELL",
            title = "",
            targetTitle = "Budget",
            blockId = "transactions",
            tableTitle = "Transactions",
            rowId = "row-food",
            rowTitle = "Makan",
            columnId = "amount",
            columnName = "Amount",
        )
        val backendAction = ChatAction(
            type = "UPDATE_TABLE_CELL",
            title = "",
            value = "29",
        )

        val resolved = ChatActionResult(
            reply = "Siap.",
            actions = listOf(backendAction),
        ).resolvePendingClarification(
            pendingActions = listOf(
                ChatPendingActionMetadata(
                    action = pending.toContractWire(),
                    issueFields = listOf("value"),
                    issueCodes = listOf("missing_required_field"),
                ),
            ),
            userPrompt = "29 ringgit",
            pages = listOf(budgetPage()),
            scopedTargetPage = budgetPage(),
        )

        val action = resolved.actions.single()
        assertEquals("row-food", action.rowId)
        assertEquals("amount", action.columnId)
        assertEquals("transactions", action.blockId)
        assertEquals("29", action.value)
    }

    @Test
    fun selectedMentionResolvesPendingRetrievalTargetWithoutFuzzyText() {
        val selectedPage = budgetPage()
        val pending = ChatAction(
            type = "RENAME_PAGE",
            title = "July Budget",
            targetTitle = "",
        )

        val resolved = ChatActionResult(reply = "Open or mention the page.")
            .resolvePendingClarification(
                pendingActions = listOf(
                    ChatPendingActionMetadata(
                        action = pending.toContractWire(),
                        issueFields = listOf("targetTitle"),
                        issueCodes = listOf("target_outside_retrieval_scope"),
                    ),
                ),
                userPrompt = "teruskan",
                pages = listOf(selectedPage),
                scopedTargetPage = selectedPage,
            )

        assertEquals(1, resolved.actions.size)
        assertEquals(selectedPage.title, resolved.actions.single().targetTitle)
    }

    @Test
    fun selectedMentionResolvesPendingSourcePageByIdAndTitle() {
        val targetPage = budgetPage()
        val sourcePage = budgetPage().copy(id = "source-page", title = "Source Data")
        val pending = ChatAction(
            type = "ATTACH_TABLE_DATA_SOURCE",
            title = "",
            targetTitle = targetPage.title,
            blockId = "transactions",
            tableTitle = "Transactions",
            sourceTableBlockId = "transactions",
            sourceTableTitle = "Transactions",
        )

        val resolved = ChatActionResult(reply = "Open or mention the source page.")
            .resolvePendingClarification(
                pendingActions = listOf(
                    ChatPendingActionMetadata(
                        action = pending.toContractWire(),
                        issueFields = listOf("sourcePageTitle"),
                        issueCodes = listOf("target_outside_retrieval_scope"),
                    ),
                ),
                userPrompt = "guna page yang saya mention",
                pages = listOf(targetPage, sourcePage),
                scopedTargetPage = sourcePage,
            )

        val action = resolved.actions.single()
        assertEquals(sourcePage.id, action.sourcePageId)
        assertEquals(sourcePage.title, action.sourcePageTitle)
    }

    @Test
    fun cancelFollowUpDoesNotResumePendingMutation() {
        val pending = ChatAction(
            type = "DELETE_TABLE_ROW",
            title = "",
            targetTitle = "Budget",
            tableTitle = "Transactions",
            rowTitle = "Makan",
        )

        val resolved = ChatActionResult(reply = "Okay.")
            .resolvePendingClarification(
                pendingActions = listOf(
                    ChatPendingActionMetadata(
                        action = pending.toContractWire(),
                        issueFields = listOf("rowId"),
                        issueCodes = listOf("ambiguous_target"),
                    ),
                ),
                userPrompt = "batalkan",
                pages = listOf(budgetPage()),
                scopedTargetPage = budgetPage(),
            )

        assertTrue(resolved.actions.isEmpty())
    }

    private fun budgetPage(): Page {
        val columns = listOf(
            PageTableColumn(id = "name", name = "Name"),
            PageTableColumn(id = "month", name = "Month", type = PageTableColumnType.Select),
            PageTableColumn(id = "amount", name = "Amount", type = PageTableColumnType.Number),
        )
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "transactions",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(
                        title = "Transactions",
                        columns = columns,
                        rows = listOf(
                            PageTableRow(
                                id = "row-food",
                                cells = mapOf(
                                    "name" to "Makan",
                                    "month" to "2026-04",
                                    "amount" to "4",
                                ),
                            ),
                            PageTableRow(
                                id = "row-fuel",
                                cells = mapOf(
                                    "name" to "Minyak",
                                    "month" to "2026-04",
                                    "amount" to "5",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        return Page(
            id = "page-budget",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget",
            content = PageBlockCodec.encodeDocument(document),
            sortOrder = 0,
            createdAt = 1,
            updatedAt = 1,
            deletedAt = null,
        )
    }
}
