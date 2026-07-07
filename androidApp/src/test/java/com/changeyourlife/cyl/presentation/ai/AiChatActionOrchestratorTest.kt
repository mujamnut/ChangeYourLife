package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatActionResult
import com.changeyourlife.cyl.domain.repository.ChatActionValidationIssue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatActionOrchestratorTest {
    @Test
    fun smartFlowExecutesBackendActionsAndKeepsMetadata() = runBlocking {
        var executorCalled = false

        val result = AiChatActionOrchestrator.orchestrate(
            workspaceId = "workspace-1",
            scopedTargetPage = page(),
            prompt = "padam block lama",
            backendResult = ChatActionResult(
                reply = "Siap.",
                actions = listOf(ChatAction(type = "DELETE_BLOCK", title = "lama", blockText = "lama")),
                schemaName = "CYL_ACTION_SCHEMA",
                schemaVersion = 1,
            ),
            auditId = "audit-planning",
            requestMessageId = "user-message-planning",
            executedAt = 2000L,
            provider = "openrouter",
            model = "openai/gpt-oss-20b:free",
        ) { _, _, actions ->
            executorCalled = true
            AiActionExecutionResult(
                messages = listOf("Done: Deleted block lama"),
                executedActionIndexes = actions.map { candidate -> candidate.originalIndex },
            )
        }

        assertTrue(executorCalled)
        assertEquals("Siap.\n\nDone: Deleted block lama", result.reply)
        assertEquals("audit-planning", result.actionMetadata.auditId)
        assertEquals("user-message-planning", result.actionMetadata.requestMessageId)
        assertEquals(2000L, result.actionMetadata.executedAt)
        assertEquals("openrouter", result.actionMetadata.provider)
        assertEquals("openai/gpt-oss-20b:free", result.actionMetadata.model)
        assertEquals("Smart", result.actionMetadata.mode)
        assertEquals(listOf("DELETE_BLOCK"), result.actionMetadata.proposedActions.map { it.type })
        assertEquals(listOf(0), result.actionMetadata.proposedActions.map { it.actionIndex })
        assertEquals(listOf("DELETE_BLOCK"), result.actionMetadata.executedActions.map { it.type })
        assertEquals(listOf("Done: Deleted block lama"), result.actionMetadata.executionMessages)
    }

    @Test
    fun executesBackendActionsAndKeepsMetadata() = runBlocking {
        var capturedWorkspaceId = ""
        var capturedTargetPage: Page? = null
        var capturedActions = emptyList<ChatAction>()

        val result = AiChatActionOrchestrator.orchestrate(
            workspaceId = "workspace-1",
            scopedTargetPage = page(),
            prompt = "tambah row makan 4 ringgit",
            backendResult = ChatActionResult(
                reply = "Siap.",
                actions = listOf(
                    ChatAction(
                        type = "ADD_TABLE_ROW",
                        title = "",
                        tableTitle = "Budget",
                        rowTitle = "Makan",
                        cellValues = mapOf("Item" to "Makan", "Amount" to "4"),
                    ),
                ),
                schemaName = "CYL_ACTION_SCHEMA",
                schemaVersion = 1,
            ),
            auditId = "audit-edit",
            requestMessageId = "user-message-edit",
            executedAt = 3000L,
            provider = "openrouter",
            model = "openai/gpt-oss-20b:free",
        ) { workspaceId, targetPage, actions ->
            capturedWorkspaceId = workspaceId
            capturedTargetPage = targetPage
            capturedActions = actions.map { candidate -> candidate.action }
            AiActionExecutionResult(
                messages = listOf("Done: Added row to Budget"),
                pageLinks = listOf(AiChatPageLink(pageId = "page-1", title = "Budget")),
                executedActionIndexes = actions.map { candidate -> candidate.originalIndex },
            )
        }

        assertEquals("workspace-1", capturedWorkspaceId)
        assertEquals("page-1", capturedTargetPage?.id)
        assertEquals(listOf("ADD_TABLE_ROW"), capturedActions.map { it.type })
        assertEquals("Siap.\n\nDone: Added row to Budget", result.reply)
        assertEquals("audit-edit", result.actionMetadata.auditId)
        assertEquals("user-message-edit", result.actionMetadata.requestMessageId)
        assertEquals(3000L, result.actionMetadata.executedAt)
        assertEquals("openrouter", result.actionMetadata.provider)
        assertEquals("openai/gpt-oss-20b:free", result.actionMetadata.model)
        assertEquals(listOf("ADD_TABLE_ROW"), result.actionMetadata.proposedActions.map { it.type })
        assertEquals(listOf("ADD_TABLE_ROW"), result.actionMetadata.executedActions.map { it.type })
        assertEquals(listOf(0), result.actionMetadata.proposedActions.map { it.actionIndex })
        assertEquals(listOf(0), result.actionMetadata.executedActions.map { it.actionIndex })
        assertEquals(listOf("Done: Added row to Budget"), result.actionMetadata.executionMessages)
        assertEquals(listOf("Budget"), result.pageLinks.map { it.title })
    }

    @Test
    fun recoversMarkdownTableAndExecutesRecoveredAction() = runBlocking {
        var capturedActions = emptyList<ChatAction>()
        val markdownReply = """
            ## Budget

            | Item | Amount |
            | --- | --- |
            | Makan | 4 |
        """.trimIndent()

        val result = AiChatActionOrchestrator.orchestrate(
            workspaceId = "workspace-1",
            scopedTargetPage = null,
            prompt = "buatkan jadual Budget",
            backendResult = ChatActionResult(
                reply = markdownReply,
                actions = emptyList(),
                schemaName = "CYL_ACTION_SCHEMA",
                schemaVersion = 1,
            ),
        ) { _, _, actions ->
            capturedActions = actions.map { candidate -> candidate.action }
            AiActionExecutionResult(
                messages = listOf("Done: Created page Budget"),
                pageLinks = listOf(AiChatPageLink(pageId = "created-1", title = "Budget")),
                executedActionIndexes = actions.map { candidate -> candidate.originalIndex },
            )
        }

        assertEquals(listOf("CREATE_PAGE"), capturedActions.map { it.type })
        assertEquals("Budget", capturedActions.single().tableTitle)
        assertEquals("Siap - saya tukar jadual itu kepada data CYL.\n\nDone: Created page Budget", result.reply)
        assertEquals(listOf("CREATE_PAGE"), result.actionMetadata.proposedActions.map { it.type })
        assertEquals(listOf("CREATE_PAGE"), result.actionMetadata.executedActions.map { it.type })
        assertEquals(listOf("Budget"), result.pageLinks.map { it.title })
    }

    @Test
    fun executedActionMetadataKeepsOriginalIndexesWhenEarlierActionIsRejected() = runBlocking {
        var capturedActions = emptyList<ChatAction>()

        val result = AiChatActionOrchestrator.orchestrate(
            workspaceId = "workspace-1",
            scopedTargetPage = page(),
            prompt = "ubah nama table jadi sesuai dan tambah row makan 4 ringgit",
            backendResult = ChatActionResult(
                reply = "Siap.",
                actions = listOf(
                    ChatAction(
                        type = "RENAME_TABLE",
                        title = "sesuai",
                        tableTitle = "Budget",
                    ),
                    ChatAction(
                        type = "ADD_TABLE_ROW",
                        title = "",
                        tableTitle = "Budget",
                        rowTitle = "Makan",
                        cellValues = mapOf("Item" to "Makan", "Amount" to "4"),
                    ),
                ),
                schemaName = "CYL_ACTION_SCHEMA",
                schemaVersion = 1,
            ),
            auditId = "audit-mixed",
            requestMessageId = "user-message-mixed",
            executedAt = 4000L,
        ) { _, _, actions ->
            capturedActions = actions.map { candidate -> candidate.action }
            AiActionExecutionResult(
                messages = listOf("Done: Added row to Budget"),
                executedActionIndexes = actions.map { candidate -> candidate.originalIndex },
            )
        }

        assertEquals(listOf("ADD_TABLE_ROW"), capturedActions.map { it.type })
        assertEquals(listOf("RENAME_TABLE", "ADD_TABLE_ROW"), result.actionMetadata.proposedActions.map { it.type })
        assertEquals(listOf(0, 1), result.actionMetadata.proposedActions.map { it.actionIndex })
        assertEquals(listOf("ADD_TABLE_ROW"), result.actionMetadata.executedActions.map { it.type })
        assertEquals(listOf(1), result.actionMetadata.executedActions.map { it.actionIndex })
        assertEquals(listOf(0), result.actionMetadata.validationIssues.map { it.actionIndex })
    }

    @Test
    fun executorRejectedActionsAreNotMarkedAsExecuted() = runBlocking {
        val result = AiChatActionOrchestrator.orchestrate(
            workspaceId = "workspace-1",
            scopedTargetPage = page(),
            prompt = "tambah row dan update block yang tak wujud",
            backendResult = ChatActionResult(
                reply = "Siap.",
                actions = listOf(
                    ChatAction(
                        type = "ADD_TABLE_ROW",
                        title = "",
                        tableTitle = "Budget",
                        rowTitle = "Makan",
                    ),
                    ChatAction(
                        type = "UPDATE_BLOCK",
                        title = "",
                        blockId = "missing",
                        content = "New",
                    ),
                ),
            ),
        ) { _, _, _ ->
            AiActionExecutionResult(
                messages = listOf("Done: Added row to Budget", "Failed UPDATE_BLOCK: Could not find block"),
                validationIssues = listOf(
                    ChatActionValidationMetadata(
                        actionIndex = 1,
                        field = "blockId",
                        code = "execution_failed",
                        message = "Could not find block",
                    ),
                ),
                executedActionIndexes = listOf(0),
            )
        }

        assertEquals(listOf("ADD_TABLE_ROW", "UPDATE_BLOCK"), result.actionMetadata.proposedActions.map { it.type })
        assertEquals(listOf("ADD_TABLE_ROW"), result.actionMetadata.executedActions.map { it.type })
        assertEquals(listOf(1), result.actionMetadata.validationIssues.map { it.actionIndex })
    }

    @Test
    fun rejectedPolicyActionsCannotBeMarkedAsExecutedByExecutor() = runBlocking {
        val result = AiChatActionOrchestrator.orchestrate(
            workspaceId = "workspace-1",
            scopedTargetPage = page(),
            prompt = "ubah nama table jadi sesuai dan tambah row makan",
            backendResult = ChatActionResult(
                reply = "Siap.",
                actions = listOf(
                    ChatAction(
                        type = "RENAME_TABLE",
                        title = "sesuai",
                        tableTitle = "Budget",
                    ),
                    ChatAction(
                        type = "ADD_TABLE_ROW",
                        title = "",
                        tableTitle = "Budget",
                        rowTitle = "Makan",
                    ),
                ),
            ),
        ) { _, _, _ ->
            AiActionExecutionResult(
                messages = listOf("Done: Added row to Budget"),
                executedActionIndexes = listOf(0, 1, 99),
            )
        }

        assertEquals(listOf("RENAME_TABLE", "ADD_TABLE_ROW"), result.actionMetadata.proposedActions.map { it.type })
        assertEquals(listOf("ADD_TABLE_ROW"), result.actionMetadata.executedActions.map { it.type })
        assertEquals(listOf(1), result.actionMetadata.executedActions.map { it.actionIndex })
        assertEquals(listOf(0), result.actionMetadata.validationIssues.map { it.actionIndex })
    }

    @Test
    fun backendValidationIssuesPreventExecutionForThatActionIndex() = runBlocking {
        var capturedIndexes = emptyList<Int>()

        val result = AiChatActionOrchestrator.orchestrate(
            workspaceId = "workspace-1",
            scopedTargetPage = page(),
            prompt = "tambah row dan field column bercampur",
            backendResult = ChatActionResult(
                reply = "Siap.",
                actions = listOf(
                    ChatAction(
                        type = "ADD_TABLE_ROW",
                        title = "",
                        tableTitle = "Budget",
                        rowTitle = "Makan",
                        columnName = "Category",
                        options = listOf("Food"),
                    ),
                    ChatAction(
                        type = "ADD_TABLE_ROW",
                        title = "",
                        tableTitle = "Budget",
                        rowTitle = "Fuel",
                    ),
                ),
                validationIssues = listOf(
                    ChatActionValidationIssue(
                        actionIndex = 0,
                        field = "columnName,options",
                        code = "UNEXPECTED_ACTION_FIELDS",
                        message = "Skipped invalid row action.",
                    ),
                ),
            ),
        ) { _, _, actions ->
            capturedIndexes = actions.map { candidate -> candidate.originalIndex }
            AiActionExecutionResult(
                messages = listOf("Done: Added row to Budget"),
                executedActionIndexes = actions.map { candidate -> candidate.originalIndex },
            )
        }

        assertEquals(listOf(1), capturedIndexes)
        assertEquals(listOf("ADD_TABLE_ROW", "ADD_TABLE_ROW"), result.actionMetadata.proposedActions.map { it.type })
        assertEquals(listOf("ADD_TABLE_ROW"), result.actionMetadata.executedActions.map { it.type })
        assertEquals(listOf(1), result.actionMetadata.executedActions.map { it.actionIndex })
        assertEquals(listOf(0), result.actionMetadata.validationIssues.map { it.actionIndex })
    }

    private fun page(): Page {
        return Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget",
            content = "",
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 1000,
            deletedAt = null,
        )
    }
}
