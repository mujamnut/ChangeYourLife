package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiRetrievalActionBoundaryTest {
    @Test
    fun rejectsMetadataTargetsButKeepsReadableTargets() {
        val result = AiRetrievalActionBoundary.enforce(
            result = AiService.AiActionResult(
                reply = "done",
                actions = listOf(
                    AiService.AiActionItem(
                        type = "UPDATE_BLOCK",
                        targetTitle = "Current",
                        blockId = "block-1",
                        content = "allowed",
                    ),
                    AiService.AiActionItem(
                        type = "DELETE_BLOCK",
                        targetTitle = "Catalog only",
                        blockId = "block-2",
                    ),
                ),
            ),
            pages = listOf(
                page(id = "current", title = "Current", access = "Target"),
                page(id = "catalog", title = "Catalog only", access = "Metadata"),
            ),
        )

        assertEquals(
            listOf("UPDATE_BLOCK", "DELETE_BLOCK"),
            result.actions.map { action -> action.type },
        )
        assertEquals(1, result.validationIssues.size)
        assertEquals(
            AiRetrievalActionBoundary.TargetOutsideRetrievalScopeCode,
            result.validationIssues.single().code,
        )
        assertEquals(1, result.validationIssues.single().actionIndex)
    }

    @Test
    fun rejectsMetadataDataSourceAndAllowsActionsForPageCreatedEarlierInPlan() {
        val result = AiRetrievalActionBoundary.enforce(
            result = AiService.AiActionResult(
                reply = "done",
                actions = listOf(
                    AiService.AiActionItem(
                        type = "ATTACH_TABLE_DATA_SOURCE",
                        targetTitle = "Current",
                        sourcePageTitle = "Catalog only",
                    ),
                    AiService.AiActionItem(
                        type = "CREATE_PAGE",
                        title = "Fresh page",
                    ),
                    AiService.AiActionItem(
                        type = "CREATE_DATABASE",
                        targetTitle = "Fresh page",
                        tableTitle = "Transactions",
                    ),
                ),
            ),
            pages = listOf(
                page(id = "current", title = "Current", access = "Target"),
                page(id = "catalog", title = "Catalog only", access = "Metadata"),
            ),
        )

        assertEquals(
            listOf("ATTACH_TABLE_DATA_SOURCE", "CREATE_PAGE", "CREATE_DATABASE"),
            result.actions.map { action -> action.type },
        )
        assertEquals("sourcePageTitle", result.validationIssues.single().field)
        assertTrue(result.validationIssues.single().message.contains("metadata-only"))
    }

    private fun page(
        id: String,
        title: String,
        access: String,
    ): AiPageContext = AiPageContext(
        id = id,
        title = title,
        workspaceId = "workspace-1",
        access = access,
    )
}
