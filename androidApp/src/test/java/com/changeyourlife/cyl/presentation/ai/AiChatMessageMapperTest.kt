package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.AiActionLog
import com.changeyourlife.cyl.domain.model.AiActionUndoState
import com.changeyourlife.cyl.domain.model.ChatActionMetadata
import com.changeyourlife.cyl.domain.model.ChatActionMetadataItem
import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatPageLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatMessageMapperTest {
    @Test
    fun mapsPersistedActionMetadataBackToUiMessage() {
        val message = ChatMessage(
            id = "message-1",
            sessionId = "session-1",
            role = "assistant",
            content = "Siap - saya tambah row itu.",
            pageLinks = listOf(
                ChatPageLink(
                    pageId = "page-1",
                    title = "Budget",
                    targetType = "row",
                    targetId = "row-1",
                ),
            ),
            actionMetadata = ChatActionMetadata(
                auditId = "audit-1",
                requestMessageId = "user-message-1",
                executedAt = 2000L,
                provider = "openrouter",
                model = "openai/gpt-oss-20b:free",
                mode = "Edit",
                schemaName = "CYL_ACTION_SCHEMA",
                schemaVersion = 2,
                proposedActions = listOf(ChatActionMetadataItem(type = "ADD_TABLE_ROW", target = "Budget / Makan", actionIndex = 0)),
                executedActions = listOf(ChatActionMetadataItem(type = "ADD_TABLE_ROW", target = "Budget / Makan", actionIndex = 0)),
                executionMessages = listOf("Done: Added row to Budget"),
                validationIssues = listOf(
                    ChatActionValidationMetadata(
                        actionIndex = 0,
                        field = "rowTitle",
                        code = "checked",
                        message = "validated",
                    ),
                ),
            ),
            createdAt = 1000L,
        )

        val mapped = AiChatMessageMapper.toAiChatMessages(
            messages = listOf(message),
            actionLogs = listOf(actionLog(undoState = AiActionUndoState.Available)),
        ).single()

        assertEquals("message-1", mapped.id)
        assertEquals("assistant", mapped.role)
        assertEquals("Siap - saya tambah row itu.", mapped.content)
        assertEquals(listOf(AiChatPageLink("page-1", "Budget", "row", "row-1")), mapped.pageLinks)
        assertEquals("audit-1", mapped.actionMetadata?.auditId)
        assertEquals("user-message-1", mapped.actionMetadata?.requestMessageId)
        assertEquals(AiActionUndoState.Available, mapped.actionMetadata?.undoState)
        assertEquals(2000L, mapped.actionMetadata?.executedAt)
        assertEquals("openrouter", mapped.actionMetadata?.provider)
        assertEquals("openai/gpt-oss-20b:free", mapped.actionMetadata?.model)
        assertEquals("Edit", mapped.actionMetadata?.mode)
        assertEquals("CYL_ACTION_SCHEMA", mapped.actionMetadata?.schemaName)
        assertEquals(2, mapped.actionMetadata?.schemaVersion)
        assertEquals(listOf(AiChatActionMetadataItem("ADD_TABLE_ROW", "Budget / Makan", 0)), mapped.actionMetadata?.proposedActions)
        assertEquals(listOf(AiChatActionMetadataItem("ADD_TABLE_ROW", "Budget / Makan", 0)), mapped.actionMetadata?.executedActions)
        assertEquals(listOf("Done: Added row to Budget"), mapped.actionMetadata?.executionMessages)
        assertEquals(
            listOf(AiChatActionValidationIssue(0, "rowTitle", "checked", "validated")),
            mapped.actionMetadata?.validationIssues,
        )
        assertTrue(mapped.actionMetadata?.hasDetails == true)
    }

    @Test
    fun mapsAppliedUndoStateFromActionLog() {
        val message = ChatMessage(
            id = "message-1",
            sessionId = "session-1",
            role = "assistant",
            content = "Undone.",
            pageLinks = emptyList(),
            actionMetadata = ChatActionMetadata(
                auditId = "audit-1",
                executedActions = listOf(ChatActionMetadataItem(type = "ADD_TABLE_ROW", target = "Budget")),
            ),
            createdAt = 1000L,
        )

        val mapped = AiChatMessageMapper.toAiChatMessages(
            messages = listOf(message),
            actionLogs = listOf(actionLog(undoState = AiActionUndoState.Applied)),
        ).single()

        assertEquals(AiActionUndoState.Applied, mapped.actionMetadata?.undoState)
    }

    @Test
    fun mapsPlainMessageWithoutActionMetadata() {
        val message = ChatMessage(
            id = "message-2",
            sessionId = "session-1",
            role = "user",
            content = "hello",
            pageLinks = emptyList(),
            actionMetadata = null,
            createdAt = 1000L,
        )

        val mapped = AiChatMessageMapper.toAiChatMessages(listOf(message)).single()

        assertEquals("message-2", mapped.id)
        assertEquals("user", mapped.role)
        assertEquals("hello", mapped.content)
        assertTrue(mapped.pageLinks.isEmpty())
        assertNull(mapped.actionMetadata)
    }

    private fun actionLog(undoState: String): AiActionLog {
        return AiActionLog(
            auditId = "audit-1",
            requestMessageId = "user-message-1",
            responseMessageId = "assistant-message-1",
            sessionId = "session-1",
            workspaceId = "workspace-1",
            mode = "Edit",
            provider = "openrouter",
            model = "openai/gpt-oss-20b:free",
            schemaName = "CYL_ACTION_SCHEMA",
            schemaVersion = 2,
            proposedActionsJson = "[]",
            executedActionsJson = "[]",
            validationIssuesJson = "[]",
            executionMessagesJson = "[]",
            undoCommandsJson = "[]",
            undoState = undoState,
            createdAt = 2000L,
        )
    }
}
