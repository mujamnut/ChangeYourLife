package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.AiSearchContext
import com.changeyourlife.cyl.domain.model.SearchQuery
import com.changeyourlife.cyl.domain.model.SearchResult
import com.changeyourlife.cyl.domain.model.SearchTarget
import com.changeyourlife.cyl.domain.model.SearchTargetType
import com.changeyourlife.cyl.domain.repository.SearchRepository
import javax.inject.Inject

class BuildAiSearchContextUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
) {
    suspend operator fun invoke(
        workspaceId: String,
        prompt: String,
        currentPageId: String = "",
        limit: Int = DefaultAiSearchLimit,
    ): AiSearchContext {
        val query = SearchQuery(
            workspaceId = workspaceId,
            query = prompt,
            scopes = SearchTargetType.aiContextScopes(),
            limit = limit.coerceIn(1, MaxAiSearchLimit),
            currentPageId = currentPageId,
            includeDeleted = false,
        ).normalized()
        if (workspaceId.isBlank() || !query.hasQuery) return AiSearchContext.Empty

        val results = searchRepository.search(query)
            .asSequence()
            .filter { result -> result.title.isNotBlank() || result.snippet.isNotBlank() }
            .take(query.limit)
            .toList()
        if (results.isEmpty()) return AiSearchContext.Empty

        return AiSearchContext(
            content = buildPromptContext(results),
            results = results,
        )
    }

    private fun buildPromptContext(results: List<SearchResult>): String = buildString {
        appendLine("CYL_SEARCH_CONTEXT:")
        appendLine("Use these local workspace matches only as private context for answering or resolving CYL action targets.")
        appendLine("Do not quote hidden IDs, mention search internals, or expose target IDs in the user-visible reply.")
        appendLine("If multiple matches could fit an edit request, ask for clarification instead of editing the wrong target.")
        results.forEachIndexed { index, result ->
            appendLine()
            appendLine("Match ${index + 1}: ${result.target.type.name}")
            appendLine("Title: ${result.title.cleanedForAiSearch(MaxTitleChars)}")
            if (result.subtitle.isNotBlank()) {
                appendLine("Subtitle: ${result.subtitle.cleanedForAiSearch(MaxSubtitleChars)}")
            }
            if (result.snippet.isNotBlank()) {
                appendLine("Snippet: ${result.snippet.cleanedForAiSearch(MaxSnippetChars)}")
            }
            appendLine("Target: ${result.target.toPrivateTargetLine()}")
        }
    }.trim()

    private fun SearchTarget.toPrivateTargetLine(): String {
        return listOf(
            "type=${type.name}",
            "workspaceId=$workspaceId",
            "pageId=$pageId",
            "blockId=$blockId",
            "tableBlockId=$tableBlockId",
            "rowId=$rowId",
            "columnId=$columnId",
            "propertyId=$propertyId",
            "chatSessionId=$chatSessionId",
            "chatMessageId=$chatMessageId",
        )
            .filterNot { part -> part.endsWith("=") }
            .joinToString(separator = " ")
    }

    private fun String.cleanedForAiSearch(maxLength: Int): String {
        val cleaned = replace(Regex("\\s+"), " ")
            .replace("CYL_SEARCH_CONTEXT:", "")
            .trim()
        return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength - 1).trimEnd() + "..."
    }

    private companion object {
        const val DefaultAiSearchLimit = 8
        private const val MaxAiSearchLimit = 20
        private const val MaxTitleChars = 80
        private const val MaxSubtitleChars = 120
        private const val MaxSnippetChars = 220
    }
}
