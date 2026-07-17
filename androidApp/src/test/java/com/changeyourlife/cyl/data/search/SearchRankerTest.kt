package com.changeyourlife.cyl.data.search

import com.changeyourlife.cyl.data.local.entity.SearchIndexEntity
import com.changeyourlife.cyl.domain.model.SearchTargetType
import com.changeyourlife.cyl.domain.model.normalizedSearchText
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerTest {
    private val ranker = SearchRanker()

    @Test
    fun exactTitleRanksAboveContainsAndBodyMatches() {
        val terms = ranker.tokenize("Budget Tracker")
        val exact = ranker.rank(
            entry = entry(
                id = "page-exact",
                targetType = SearchTargetType.Page,
                title = "Budget Tracker",
                snippet = "Monthly expense",
            ),
            terms = terms,
            currentPageId = "",
            nowMillis = FixedNow,
        )
        val contains = ranker.rank(
            entry = entry(
                id = "page-contains",
                targetType = SearchTargetType.Page,
                title = "July Budget Tracker",
                snippet = "Monthly expense",
            ),
            terms = terms,
            currentPageId = "",
            nowMillis = FixedNow,
        )
        val bodyOnly = ranker.rank(
            entry = entry(
                id = "block-body",
                targetType = SearchTargetType.Block,
                title = "Notes",
                snippet = "This note talks about budget tracker setup.",
            ),
            terms = terms,
            currentPageId = "",
            nowMillis = FixedNow,
        )

        assertTrue(exact.score > contains.score)
        assertTrue(contains.score > bodyOnly.score)
    }

    @Test
    fun currentPageBoostMakesNearbyResultWinWhenTextRelevanceMatches() {
        val terms = ranker.tokenize("fuel")
        val currentPage = ranker.rank(
            entry = entry(id = "row-current", pageId = "page-current", targetType = SearchTargetType.Row, title = "Fuel"),
            terms = terms,
            currentPageId = "page-current",
            nowMillis = FixedNow,
        )
        val otherPage = ranker.rank(
            entry = entry(id = "row-other", pageId = "page-other", targetType = SearchTargetType.Row, title = "Fuel"),
            terms = terms,
            currentPageId = "page-current",
            nowMillis = FixedNow,
        )

        assertTrue(currentPage.score > otherPage.score)
    }

    private fun entry(
        id: String,
        targetType: SearchTargetType,
        title: String,
        pageId: String = "page-budget",
        snippet: String = "",
        updatedAt: Long = FixedNow,
    ): SearchIndexEntity {
        val subtitle = "Budget"
        val normalizedText = listOf(title, subtitle, snippet)
            .joinToString(separator = " ")
            .normalizedSearchText()
        return SearchIndexEntity(
            id = id,
            workspaceId = "workspace-1",
            targetType = targetType.name,
            pageId = pageId,
            title = title,
            subtitle = subtitle,
            snippet = snippet,
            normalizedText = normalizedText,
            updatedAt = updatedAt,
            deletedAt = null,
        )
    }

    private companion object {
        private const val FixedNow = 100_000L
    }
}
