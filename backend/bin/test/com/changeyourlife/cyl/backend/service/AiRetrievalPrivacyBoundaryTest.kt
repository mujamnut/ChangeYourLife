package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.data.InMemoryContentRepository
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiRetrievalScope
import com.changeyourlife.cyl.backend.model.ai.AiTaskContext
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AiRetrievalPrivacyBoundaryTest {
    @Test
    fun keepsTargetContentAndStripsMetadataAndUnrequestedTasks() = runBlocking {
        val repository = InMemoryContentRepository()
        repository.upsertWorkspace(workspace(WorkspaceId))
        repository.upsertPage(UserId, pageRecord(id = "target", workspaceId = WorkspaceId))
        repository.upsertPage(UserId, pageRecord(id = "catalog", workspaceId = WorkspaceId))
        val boundary = AiRetrievalPrivacyBoundary(repository)

        val result = boundary.enforce(
            userId = UserId,
            request = ChatWithActionsRequest(
                messages = emptyList(),
                retrievalScope = AiRetrievalScope(
                    workspaceId = WorkspaceId,
                    currentPageId = "target",
                    includeTasks = false,
                ),
                pages = listOf(
                    pageContext(id = "target", access = "Metadata", secret = "allowed-content"),
                    pageContext(id = "catalog", access = "Target", secret = "must-be-stripped"),
                ),
                tasks = listOf(
                    AiTaskContext(
                        id = "task-1",
                        title = "Private task",
                        workspaceId = WorkspaceId,
                    ),
                ),
            ),
        )

        val request = assertIs<AiRetrievalBoundaryResult.Allowed>(result).request
        val target = request.pages.single { page -> page.id == "target" }
        val catalog = request.pages.single { page -> page.id == "catalog" }
        assertEquals("Page", request.retrievalScope.mode)
        assertEquals("Target", target.access)
        assertEquals("allowed-content", target.blocks.single().text)
        assertEquals("Metadata", catalog.access)
        assertTrue(catalog.blocks.isEmpty())
        assertTrue(request.tasks.isEmpty())
    }

    @Test
    fun rejectsDetailedPageThatBelongsToAnotherWorkspace() = runBlocking {
        val repository = InMemoryContentRepository()
        repository.upsertWorkspace(workspace(WorkspaceId))
        repository.upsertWorkspace(workspace(OtherWorkspaceId))
        repository.upsertPage(
            UserId,
            pageRecord(id = "foreign-page", workspaceId = OtherWorkspaceId),
        )
        val boundary = AiRetrievalPrivacyBoundary(repository)

        val result = boundary.enforce(
            userId = UserId,
            request = ChatWithActionsRequest(
                messages = emptyList(),
                retrievalScope = AiRetrievalScope(
                    workspaceId = WorkspaceId,
                    currentPageId = "foreign-page",
                ),
                pages = listOf(
                    pageContext(
                        id = "foreign-page",
                        workspaceId = WorkspaceId,
                        access = "Target",
                        secret = "forged-content",
                    ),
                ),
            ),
        )

        val rejection = assertIs<AiRetrievalBoundaryResult.Rejected>(result)
        assertEquals("page_scope_forbidden", rejection.code)
        assertTrue(rejection.forbidden)
    }

    @Test
    fun includesOnlyTasksDeclaredForTheActiveWorkspace() = runBlocking {
        val repository = InMemoryContentRepository()
        repository.upsertWorkspace(workspace(WorkspaceId))
        val boundary = AiRetrievalPrivacyBoundary(repository)

        val result = boundary.enforce(
            userId = UserId,
            request = ChatWithActionsRequest(
                messages = emptyList(),
                retrievalScope = AiRetrievalScope(
                    workspaceId = WorkspaceId,
                    includeTasks = true,
                ),
                tasks = listOf(
                    AiTaskContext(id = "allowed", title = "Allowed", workspaceId = WorkspaceId),
                    AiTaskContext(id = "foreign", title = "Foreign", workspaceId = OtherWorkspaceId),
                    AiTaskContext(id = "unscoped", title = "Unscoped"),
                ),
            ),
        )

        val request = assertIs<AiRetrievalBoundaryResult.Allowed>(result).request
        assertEquals(listOf("allowed"), request.tasks.map { task -> task.id })
        assertTrue(request.tasks.all { task -> task.workspaceId == WorkspaceId })
    }

    private fun workspace(id: String): WorkspaceRecord = WorkspaceRecord(
        id = id,
        userId = UserId,
        name = id,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
    )

    private fun pageRecord(
        id: String,
        workspaceId: String,
    ): PageRecord = PageRecord(
        id = id,
        workspaceId = workspaceId,
        parentPageId = null,
        title = id,
        content = "",
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
    )

    private fun pageContext(
        id: String,
        access: String,
        secret: String,
        workspaceId: String = WorkspaceId,
    ): AiPageContext = AiPageContext(
        id = id,
        title = id,
        workspaceId = workspaceId,
        access = access,
        blocks = listOf(
            AiBlockContext(
                id = "$id-block",
                type = "Text",
                text = secret,
            ),
        ),
    )

    private companion object {
        const val UserId = "user-1"
        const val WorkspaceId = "workspace-1"
        const val OtherWorkspaceId = "workspace-2"
    }
}
