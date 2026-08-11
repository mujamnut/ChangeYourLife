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

    @Test
    fun doesNotBlockWhenPageNotFoundInWorkspace() {
        // When a page reference doesn't match any workspace page (page == null),
        // the retrieval boundary should NOT block it. The boundary guards privacy
        // of existing Metadata-only pages, not page existence. A missing page will
        // either match a page created earlier in the plan or fail at execution with
        // a more appropriate target_not_found error.
        val result = AiRetrievalActionBoundary.enforce(
            result = AiService.AiActionResult(
                reply = "done",
                actions = listOf(
                    AiService.AiActionItem(
                        type = "ADD_TABLE_ROW",
                        targetTitle = "Nonexistent Page",
                        tableTitle = "Transactions",
                    ),
                ),
            ),
            pages = listOf(
                page(id = "current", title = "Current", access = "Target"),
            ),
        )

        assertTrue(
            result.validationIssues.isEmpty(),
            "Should not block with target_outside_retrieval_scope when page is not found",
        )
    }

    @Test
    fun createRootPageIgnoresIrrelevantMetadataTarget() {
        val result = AiRetrievalActionBoundary.enforce(
            result = AiService.AiActionResult(
                reply = "done",
                actions = listOf(
                    AiService.AiActionItem(
                        type = "CREATE_PAGE",
                        title = "Imported Note",
                        targetTitle = "Catalog only",
                        content = "Imported attachment content",
                    ),
                ),
            ),
            pages = listOf(
                page(id = "catalog", title = "Catalog only", access = "Metadata"),
            ),
        )

        assertTrue(
            result.validationIssues.isEmpty(),
            "CREATE_PAGE is home-scoped and must not read its unused targetTitle.",
        )
    }

    @Test
    fun moveToWorkspaceRootDoesNotTreatRootAsMetadataPage() {
        val result = AiRetrievalActionBoundary.enforce(
            result = AiService.AiActionResult(
                reply = "done",
                actions = listOf(
                    AiService.AiActionItem(
                        type = "MOVE_PAGE",
                        targetTitle = "Current",
                        parentPageTitle = "root",
                    ),
                ),
            ),
            pages = listOf(
                page(id = "current", title = "Current", access = "Target"),
                page(id = "root-page", title = "Root", access = "Metadata"),
            ),
        )

        assertTrue(
            result.validationIssues.isEmpty(),
            "The root sentinel means workspace root, not a page named Root.",
        )
    }

    @Test
    fun allowsCreatePagePlusCreateDatabaseWithMismatchedTitles() {
        // When the AI uses slightly different titles between CREATE_PAGE and
        // CREATE_DATABASE (e.g. "August Monthly Expenses" vs "August 2026 Monthly
        // Expenses"), the createdPageTitles exact match fails. Previously this
        // blocked the CREATE_DATABASE with a misleading "mention with @" error.
        // After the fix, page == null (no matching page in workspace) is not
        // treated as a retrieval scope violation.
        val result = AiRetrievalActionBoundary.enforce(
            result = AiService.AiActionResult(
                reply = "done",
                actions = listOf(
                    AiService.AiActionItem(
                        type = "CREATE_PAGE",
                        title = "August Monthly Expenses",
                    ),
                    AiService.AiActionItem(
                        type = "CREATE_DATABASE",
                        targetTitle = "August 2026 Monthly Expenses",
                        tableTitle = "Monthly Summary",
                    ),
                ),
            ),
            pages = emptyList(),
        )

        assertTrue(
            result.validationIssues.isEmpty(),
            "Mismatched titles between CREATE_PAGE and CREATE_DATABASE should not trigger retrieval scope block",
        )
    }

    @Test
    fun stillBlocksMetadataOnlyPagesEvenWhenOtherPagesNotFound() {
        // Ensure the fix doesn't regress: actions targeting an existing
        // Metadata-only page must still be blocked.
        val result = AiRetrievalActionBoundary.enforce(
            result = AiService.AiActionResult(
                reply = "done",
                actions = listOf(
                    AiService.AiActionItem(
                        type = "ADD_TABLE_ROW",
                        targetTitle = "Budget",
                        tableTitle = "Transactions",
                    ),
                ),
            ),
            pages = listOf(
                page(id = "budget", title = "Budget", access = "Metadata"),
            ),
        )

        assertEquals(1, result.validationIssues.size)
        assertEquals(
            AiRetrievalActionBoundary.TargetOutsideRetrievalScopeCode,
            result.validationIssues.single().code,
        )
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
