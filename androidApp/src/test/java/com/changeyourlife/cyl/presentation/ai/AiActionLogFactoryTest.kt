package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.AiActionUndoState
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.ChatActionMetadata
import com.changeyourlife.cyl.domain.model.ChatActionMetadataItem
import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.PageTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiActionLogFactoryTest {
    @Test
    fun createsActionLogFromPersistedMetadata() {
        val log = AiActionLogFactory.fromMetadata(
            sessionId = "session-1",
            workspaceId = "workspace-1",
            responseMessageId = "assistant-message-1",
            metadata = ChatActionMetadata(
                auditId = "audit-1",
                requestMessageId = "user-message-1",
                executedAt = 2000L,
                provider = "openrouter",
                model = "openai/gpt-oss-20b:free",
                mode = "Edit",
                schemaName = "CYL_ACTION_SCHEMA",
                schemaVersion = 2,
                proposedActions = listOf(
                    ChatActionMetadataItem("RENAME_TABLE", "Budget", 0),
                    ChatActionMetadataItem("ADD_TABLE_ROW", "Budget / Makan", 1),
                ),
                executedActions = listOf(
                    ChatActionMetadataItem("ADD_TABLE_ROW", "Budget / Makan", 1),
                ),
                validationIssues = listOf(
                    ChatActionValidationMetadata(
                        actionIndex = 0,
                        field = "title",
                        code = "UNSAFE_QUALITATIVE_RENAME",
                        message = "Skipped rename",
                    ),
                ),
                executionMessages = listOf("Done: Added row to Budget"),
            ),
        )

        requireNotNull(log)
        assertEquals("audit-1", log.auditId)
        assertEquals("user-message-1", log.requestMessageId)
        assertEquals("assistant-message-1", log.responseMessageId)
        assertEquals("session-1", log.sessionId)
        assertEquals("workspace-1", log.workspaceId)
        assertEquals("Edit", log.mode)
        assertEquals("openrouter", log.provider)
        assertEquals("openai/gpt-oss-20b:free", log.model)
        assertEquals("CYL_ACTION_SCHEMA", log.schemaName)
        assertEquals(2, log.schemaVersion)
        assertEquals(AiActionUndoState.PendingCommandLink, log.undoState)
        assertEquals(2000L, log.createdAt)
        assertTrue(log.proposedActionsJson.contains("\"actionIndex\":1"))
        assertTrue(log.executedActionsJson.contains("\"ADD_TABLE_ROW\""))
        assertTrue(log.validationIssuesJson.contains("UNSAFE_QUALITATIVE_RENAME"))
        assertTrue(log.executionMessagesJson.contains("Done: Added row to Budget"))
        assertEquals("[]", log.undoCommandsJson)
    }

    @Test
    fun marksActionLogUndoAvailableWhenUndoCommandsAreLinked() {
        val log = AiActionLogFactory.fromMetadata(
            sessionId = "session-1",
            workspaceId = "workspace-1",
            responseMessageId = "assistant-message-1",
            metadata = ChatActionMetadata(
                auditId = "audit-1",
                requestMessageId = "user-message-1",
                executedAt = 2000L,
                mode = "Edit",
                proposedActions = listOf(
                    ChatActionMetadataItem("ADD_TABLE_ROW", "Budget / Makan", 0),
                ),
                executedActions = listOf(
                    ChatActionMetadataItem("ADD_TABLE_ROW", "Budget / Makan", 0),
                ),
            ),
            undoCommands = listOf(
                AiUndoCommandSummary(
                    actionIndex = 0,
                    commandType = "ReplaceTable",
                    targetType = "Table",
                    targetId = "table-block-1",
                    table = PageTable(title = "Budget"),
                ),
            ),
        )

        requireNotNull(log)
        assertEquals(AiActionUndoState.Available, log.undoState)
        assertTrue(log.undoCommandsJson.contains("\"commandType\":\"ReplaceTable\""))
        assertTrue(log.undoCommandsJson.contains("\"targetId\":\"table-block-1\""))
        assertTrue(log.undoCommandsJson.contains("\"table\""))
        assertTrue(log.undoCommandsJson.contains("\"title\":\"Budget\""))
    }

    @Test
    fun returnsNullWhenMetadataHasNoLoggableDetails() {
        val log = AiActionLogFactory.fromMetadata(
            sessionId = "session-1",
            workspaceId = "workspace-1",
            responseMessageId = "assistant-message-1",
            metadata = ChatActionMetadata(
                auditId = "audit-1",
                requestMessageId = "user-message-1",
                executedAt = 2000L,
            ),
        )

        assertNull(log)
    }
}
