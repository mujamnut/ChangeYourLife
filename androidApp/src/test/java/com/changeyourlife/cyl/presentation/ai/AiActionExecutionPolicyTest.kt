package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.repository.ChatAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiActionExecutionPolicyTest {
    @Test
    fun planningModeNeverExecutesBackendActions() {
        val decision = AiActionExecutionPolicy.decide(
            mode = AiChatMode.Planning,
            backendActions = listOf(ChatAction(type = "APPEND_BLOCK", title = "Note")),
        )

        assertTrue(decision.executableActions.isEmpty())
        assertTrue(decision.validationIssues.isEmpty())
    }

    @Test
    fun editModeDoesNotInventActionsWhenBackendReturnsNone() {
        val decision = AiActionExecutionPolicy.decide(
            mode = AiChatMode.Edit,
            backendActions = emptyList(),
        )

        assertTrue(decision.executableActions.isEmpty())
        assertTrue(decision.validationIssues.isEmpty())
    }

    @Test
    fun editModeExecutesBackendActionsOnly() {
        val backendAction = ChatAction(type = "ADD_TABLE_ROW", title = "", rowTitle = "Makan")

        val decision = AiActionExecutionPolicy.decide(
            mode = AiChatMode.Edit,
            backendActions = listOf(backendAction),
        )

        assertEquals(listOf(backendAction), decision.executableActions)
        assertTrue(decision.validationIssues.isEmpty())
    }

    @Test
    fun autoModeSkipsUnsafeQualitativeRename() {
        val unsafeRename = ChatAction(type = "RENAME_TABLE", title = "sesuai dan pendek")
        val validRow = ChatAction(type = "ADD_TABLE_ROW", title = "", rowTitle = "Fuel")

        val decision = AiActionExecutionPolicy.decide(
            mode = AiChatMode.Auto,
            backendActions = listOf(unsafeRename, validRow),
        )

        assertEquals(listOf(validRow), decision.executableActions)
        assertEquals(1, decision.validationIssues.size)
        assertEquals(0, decision.validationIssues.single().actionIndex)
        assertEquals("UNSAFE_QUALITATIVE_RENAME", decision.validationIssues.single().code)
    }

    @Test
    fun autoModeAllowsConcreteRename() {
        val rename = ChatAction(type = "RENAME_TABLE", title = "Budget")

        val decision = AiActionExecutionPolicy.decide(
            mode = AiChatMode.Auto,
            backendActions = listOf(rename),
        )

        assertEquals(listOf(rename), decision.executableActions)
        assertTrue(decision.validationIssues.isEmpty())
    }
}
