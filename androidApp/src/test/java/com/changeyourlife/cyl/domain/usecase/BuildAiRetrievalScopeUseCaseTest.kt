package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.SearchResult
import com.changeyourlife.cyl.domain.model.SearchTarget
import com.changeyourlife.cyl.domain.model.SearchTargetType
import com.changeyourlife.cyl.domain.repository.AiPageContextAccess
import com.changeyourlife.cyl.domain.repository.AiRetrievalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildAiRetrievalScopeUseCaseTest {
    private val useCase = BuildAiRetrievalScopeUseCase()

    @Test
    fun currentAndMentionedPagesAreTheOnlyDetailedTargets() {
        val selection = useCase(
            workspaceId = WorkspaceId,
            pages = listOf(
                page(id = "current", workspaceId = WorkspaceId, updatedAt = 10L),
                page(id = "mentioned", workspaceId = WorkspaceId, updatedAt = 9L),
                page(id = "catalog", workspaceId = WorkspaceId, updatedAt = 8L),
                page(id = "foreign", workspaceId = "workspace-2", updatedAt = 7L),
                page(id = "deleted", workspaceId = WorkspaceId, updatedAt = 6L, deletedAt = 6L),
            ),
            prompt = "ingatkan saya ubah @Mentioned",
            attachedPageId = "current",
            explicitlyMentionedPageIds = listOf("mentioned", "foreign"),
            searchResults = listOf(searchResult(pageId = "catalog", workspaceId = WorkspaceId)),
        )

        assertEquals(AiRetrievalMode.Page, selection.scope.mode)
        assertEquals("current", selection.scope.currentPageId)
        assertEquals(listOf("mentioned"), selection.scope.explicitPageIds)
        assertTrue(selection.scope.retrievedPageIds.isEmpty())
        assertTrue(selection.scope.includeTasks)
        assertEquals(
            setOf("current", "mentioned"),
            selection.detailedPageIds,
        )
        assertEquals(AiPageContextAccess.Target, selection.accessFor("current"))
        assertEquals(AiPageContextAccess.Target, selection.accessFor("mentioned"))
        assertEquals(AiPageContextAccess.Metadata, selection.accessFor("catalog"))
        assertEquals(setOf("current", "mentioned", "catalog"), selection.pages.map(Page::id).toSet())
    }

    @Test
    fun homeSearchRetrievesOnlyWorkspaceMatchesAndKeepsOthersAsMetadata() {
        val selection = useCase(
            workspaceId = WorkspaceId,
            pages = listOf(
                page(id = "budget", workspaceId = WorkspaceId, updatedAt = 2L),
                page(id = "notes", workspaceId = WorkspaceId, updatedAt = 1L),
                page(id = "foreign", workspaceId = "workspace-2", updatedAt = 3L),
            ),
            prompt = "berapa budget makeup",
            attachedPageId = null,
            explicitlyMentionedPageIds = emptyList(),
            searchResults = listOf(
                searchResult(pageId = "budget", workspaceId = WorkspaceId),
                searchResult(pageId = "foreign", workspaceId = "workspace-2"),
            ),
        )

        assertEquals(AiRetrievalMode.Workspace, selection.scope.mode)
        assertEquals(listOf("budget"), selection.scope.retrievedPageIds)
        assertEquals(AiPageContextAccess.Retrieved, selection.accessFor("budget"))
        assertEquals(AiPageContextAccess.Metadata, selection.accessFor("notes"))
        assertFalse(selection.scope.includeTasks)
    }

    @Test
    fun catalogIsBoundedWithoutDroppingAnExplicitTarget() {
        val pages = (1..300).map { index ->
            page(
                id = "page-$index",
                workspaceId = WorkspaceId,
                updatedAt = index.toLong(),
            )
        }
        val selection = useCase(
            workspaceId = WorkspaceId,
            pages = pages,
            prompt = "edit current page",
            attachedPageId = "page-1",
            explicitlyMentionedPageIds = emptyList(),
            searchResults = emptyList(),
        )

        assertEquals(250, selection.pages.size)
        assertTrue(selection.pages.any { page -> page.id == "page-1" })
        assertEquals(AiPageContextAccess.Target, selection.accessFor("page-1"))
    }

    private fun page(
        id: String,
        workspaceId: String,
        updatedAt: Long,
        deletedAt: Long? = null,
    ): Page = Page(
        id = id,
        workspaceId = workspaceId,
        parentPageId = null,
        title = id,
        content = "private-$id",
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun searchResult(
        pageId: String,
        workspaceId: String,
    ): SearchResult = SearchResult(
        target = SearchTarget(
            type = SearchTargetType.Page,
            workspaceId = workspaceId,
            pageId = pageId,
        ),
        title = pageId,
    )

    private companion object {
        const val WorkspaceId = "workspace-1"
    }
}
