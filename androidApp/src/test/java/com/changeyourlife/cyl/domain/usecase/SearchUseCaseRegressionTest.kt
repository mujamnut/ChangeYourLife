package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.MentionQuery
import com.changeyourlife.cyl.domain.model.SearchQuery
import com.changeyourlife.cyl.domain.model.SearchResult
import com.changeyourlife.cyl.domain.model.SearchTarget
import com.changeyourlife.cyl.domain.model.SearchTargetType
import com.changeyourlife.cyl.domain.repository.SearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUseCaseRegressionTest {
    @Test
    fun searchWorkspaceUseCaseNeverRequestsDeletedItemsByDefault() = runBlocking {
        val repository = CapturingSearchRepository(
            results = listOf(pageResult(pageId = "page-budget", title = "Budget Tracker")),
        )
        val useCase = SearchWorkspaceUseCase(repository)

        val results = useCase(
            SearchQuery(
                workspaceId = WorkspaceId,
                query = "budget",
            ),
        )

        assertEquals(listOf("page-budget"), results.map { result -> result.target.pageId })
        assertFalse(requireNotNull(repository.lastQuery).includeDeleted)
    }

    @Test
    fun mentionResolverKeepsDuplicateTitlesSeparatedByHiddenPageId() = runBlocking {
        val repository = CapturingSearchRepository(
            results = listOf(
                pageResult(pageId = "page-budget-exact", title = "Budget", score = 900, updatedAt = 1_000L),
                pageResult(pageId = "page-budget-old", title = "Budget", score = 700, updatedAt = 2_000L),
            ),
        )
        val useCase = ResolveMentionUseCase(repository)

        val candidates = useCase(
            MentionQuery(
                workspaceId = WorkspaceId,
                query = "@Budget",
            ),
        )

        assertEquals(listOf("page-budget-exact", "page-budget-old"), candidates.map { candidate -> candidate.pageId })
        assertEquals(setOf(SearchTargetType.Page), requireNotNull(repository.lastQuery).scopes)
    }

    @Test
    fun aiSearchContextUsesNonChatScopesAndKeepsIdsOnlyInPrivateTargetLine() = runBlocking {
        val repository = CapturingSearchRepository(
            results = listOf(
                SearchResult(
                    target = SearchTarget(
                        type = SearchTargetType.Block,
                        workspaceId = WorkspaceId,
                        pageId = "page-secret-123",
                        blockId = "block-secret-456",
                    ),
                    title = "Fuel expense",
                    subtitle = "Budget Tracker",
                    snippet = "Fuel 5 ringgit",
                    score = 800,
                    updatedAt = 2_000L,
                ),
            ),
        )
        val useCase = BuildAiSearchContextUseCase(repository)

        val context = useCase(
            workspaceId = WorkspaceId,
            prompt = "berapa fuel",
            currentPageId = "page-budget",
        )

        assertTrue(context.isNotBlank)
        val query = requireNotNull(repository.lastQuery)
        assertFalse(SearchTargetType.Chat in query.scopes)
        assertFalse(query.includeDeleted)
        assertTrue(context.content.contains("Do not quote hidden IDs"))
        assertTrue(context.content.contains("If multiple matches could fit an edit request"))

        val targetLine = context.content
            .lines()
            .singleOrNull { line -> line.startsWith("Target:") }
        assertNotNull(targetLine)
        assertTrue(requireNotNull(targetLine).contains("pageId=page-secret-123"))
        assertTrue(targetLine.contains("blockId=block-secret-456"))

        val nonTargetContent = context.content
            .lines()
            .filterNot { line -> line.startsWith("Target:") }
            .joinToString(separator = "\n")
        assertFalse(nonTargetContent.contains("page-secret-123"))
        assertFalse(nonTargetContent.contains("block-secret-456"))
    }

    private fun pageResult(
        pageId: String,
        title: String,
        score: Int = 500,
        updatedAt: Long = 1_000L,
    ): SearchResult {
        return SearchResult(
            target = SearchTarget(
                type = SearchTargetType.Page,
                workspaceId = WorkspaceId,
                pageId = pageId,
            ),
            title = title,
            subtitle = "Page",
            snippet = title,
            score = score,
            updatedAt = updatedAt,
        )
    }

    private class CapturingSearchRepository(
        private val results: List<SearchResult>,
    ) : SearchRepository {
        var lastQuery: SearchQuery? = null
            private set

        override suspend fun search(query: SearchQuery): List<SearchResult> {
            lastQuery = query
            return results
        }
    }

    private companion object {
        private const val WorkspaceId = "workspace-1"
    }
}
