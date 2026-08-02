package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.repository.ChatAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun requiresConfirmationBeforePermanentOrBulkDeletion() {
        val actions = listOf(
            ChatAction(type = "DELETE_PAGE_PERMANENTLY", title = "", targetTitle = "Budget"),
            ChatAction(
                type = "DELETE_TABLE_ROWS",
                title = "",
                targetTitle = "Budget",
                tableTitle = "Transactions",
                columnName = "Month",
                filterQuery = "2026-04",
            ),
        )

        val decision = AiActionExecutionPolicy.decide(actions)

        assertEquals(actions, decision.executableActions)
        assertEquals(2, decision.confirmationCandidates.size)
        assertTrue(decision.validationIssues.all { issue ->
            issue.code == DestructiveConfirmationRequiredCode
        })
    }

    @Test
    fun explicitConfirmationReleasesTheExactDestructivePlan() {
        val action = ChatAction(
            type = "DELETE_TABLE_COLUMN",
            title = "",
            targetTitle = "Budget",
            tableTitle = "Transactions",
            columnName = "Notes",
        )

        val decision = AiActionExecutionPolicy.decide(
            backendActions = listOf(action),
            destructiveActionsConfirmed = true,
        )

        assertEquals(listOf(action), decision.executableActions)
        assertTrue(decision.confirmationCandidates.isEmpty())
        assertTrue(decision.validationIssues.isEmpty())
    }

    @Test
    fun repeatedSmallDeletesAreTreatedAsBulkRemoval() {
        val actions = listOf("Food", "Fuel", "Rent").map { row ->
            ChatAction(
                type = "DELETE_TABLE_ROW",
                title = "",
                tableTitle = "Transactions",
                rowTitle = row,
            )
        }

        val decision = AiActionExecutionPolicy.decide(actions)

        assertEquals(3, decision.confirmationCandidates.size)
        assertFalse(decision.validationIssues.isEmpty())
    }

    @Test
    fun oneReversibleRowDeleteDoesNotAddConfirmationFriction() {
        val action = ChatAction(
            type = "DELETE_TABLE_ROW",
            title = "",
            tableTitle = "Transactions",
            rowTitle = "Food",
        )

        val decision = AiActionExecutionPolicy.decide(listOf(action))

        assertTrue(decision.confirmationCandidates.isEmpty())
        assertTrue(decision.validationIssues.isEmpty())
    }
}
