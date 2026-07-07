package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.repository.ChatAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiActionExecutionPolicyTest {
    @Test
    fun doesNotInventActionsWhenBackendReturnsNone() {
        val decision = AiActionExecutionPolicy.decide(
            backendActions = emptyList(),
        )

        assertTrue(decision.executableActions.isEmpty())
        assertTrue(decision.validationIssues.isEmpty())
    }

    @Test
    fun executesBackendActionsByDefault() {
        val backendAction = ChatAction(type = "ADD_TABLE_ROW", title = "", rowTitle = "Makan")

        val decision = AiActionExecutionPolicy.decide(
            backendActions = listOf(backendAction),
        )

        assertEquals(listOf(backendAction), decision.executableActions)
        assertTrue(decision.validationIssues.isEmpty())
    }

    @Test
    fun skipsMissingActionType() {
        val validRow = ChatAction(type = "ADD_TABLE_ROW", title = "", rowTitle = "Fuel")

        val decision = AiActionExecutionPolicy.decide(
            backendActions = listOf(ChatAction(type = "", title = "Unknown"), validRow),
        )

        assertEquals(listOf(validRow), decision.executableActions)
        assertEquals(1, decision.validationIssues.size)
        assertEquals(0, decision.validationIssues.single().actionIndex)
        assertEquals("MISSING_ACTION_TYPE", decision.validationIssues.single().code)
    }

    @Test
    fun skipsUnsafeQualitativeRename() {
        val unsafeRename = ChatAction(type = "RENAME_TABLE", title = "sesuai dan pendek")
        val validRow = ChatAction(type = "ADD_TABLE_ROW", title = "", rowTitle = "Fuel")

        val decision = AiActionExecutionPolicy.decide(
            backendActions = listOf(unsafeRename, validRow),
        )

        assertEquals(listOf(validRow), decision.executableActions)
        assertEquals(1, decision.validationIssues.size)
        assertEquals(0, decision.validationIssues.single().actionIndex)
        assertEquals("UNSAFE_QUALITATIVE_RENAME", decision.validationIssues.single().code)
    }

    @Test
    fun allowsConcreteRename() {
        val rename = ChatAction(type = "RENAME_TABLE", title = "Budget")

        val decision = AiActionExecutionPolicy.decide(
            backendActions = listOf(rename),
        )

        assertEquals(listOf(rename), decision.executableActions)
        assertTrue(decision.validationIssues.isEmpty())
    }
}
