package com.changeyourlife.cyl.data.search

import com.changeyourlife.cyl.data.local.entity.SearchIndexEntity
import com.changeyourlife.cyl.domain.model.SearchTargetType
import com.changeyourlife.cyl.domain.model.normalizedSearchText
import javax.inject.Inject
import kotlin.math.max

data class RankedSearchEntry(
    val entry: SearchIndexEntity,
    val score: Int,
    val matchedTerms: List<String>,
    val snippet: String,
)

class SearchRanker @Inject constructor() {
    fun tokenize(query: String): List<String> =
        query.normalizedSearchText()
            .split(" ")
            .asSequence()
            .map(String::trim)
            .filter { term -> term.length >= MinTextTermLength || term.any(Char::isDigit) }
            .distinct()
            .take(MaxTerms)
            .toList()

    fun rank(
        entry: SearchIndexEntity,
        terms: List<String>,
        currentPageId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): RankedSearchEntry {
        if (terms.isEmpty()) {
            return RankedSearchEntry(
                entry = entry,
                score = entry.emptyQueryScore(currentPageId, nowMillis),
                matchedTerms = emptyList(),
                snippet = entry.bestSnippet(emptyList()),
            )
        }

        val titleText = entry.title.normalizedSearchText()
        val subtitleText = entry.subtitle.normalizedSearchText()
        val snippetText = entry.snippet.normalizedSearchText()
        val haystack = entry.normalizedText
        val phrase = terms.joinToString(" ")
        val matchedTerms = terms.filter { term ->
            titleText.hasTokenMatch(term) ||
                subtitleText.hasTokenMatch(term) ||
                snippetText.hasTokenMatch(term) ||
                haystack.hasTokenMatch(term)
        }
        if (matchedTerms.isEmpty()) {
            return RankedSearchEntry(
                entry = entry,
                score = 0,
                matchedTerms = emptyList(),
                snippet = entry.bestSnippet(terms),
            )
        }

        val allTermsMatched = matchedTerms.size == terms.size
        val score = buildList {
            add(entry.typeWeight())
            add(entry.currentPageBoost(currentPageId))
            add(entry.recencyBoost(nowMillis))
            add(if (titleText == phrase) 220 else 0)
            add(if (titleText.startsWith(phrase)) 150 else 0)
            add(if (titleText.contains(phrase)) 110 else 0)
            add(if (haystack.contains(phrase)) 70 else 0)
            add(if (allTermsMatched) 65 else 0)
            add(matchedTerms.sumOf { term -> entry.termScore(term, titleText, subtitleText, snippetText, haystack) })
        }.sum()

        return RankedSearchEntry(
            entry = entry,
            score = score,
            matchedTerms = matchedTerms,
            snippet = entry.bestSnippet(matchedTerms.ifEmpty { terms }),
        )
    }

    private fun SearchIndexEntity.termScore(
        term: String,
        titleText: String,
        subtitleText: String,
        snippetText: String,
        haystack: String,
    ): Int {
        val exactTitleToken = titleText.split(" ").any { token -> token == term }
        val prefixTitleToken = titleText.split(" ").any { token -> token.startsWith(term) }
        val exactHaystackToken = haystack.split(" ").any { token -> token == term }
        return listOf(
            if (exactTitleToken) 55 else 0,
            if (!exactTitleToken && prefixTitleToken) 38 else 0,
            if (titleText.contains(term)) 30 else 0,
            if (subtitleText.contains(term)) 18 else 0,
            if (snippetText.contains(term)) 16 else 0,
            if (exactHaystackToken) 14 else 0,
            if (haystack.contains(term)) 10 else 0,
        ).sum()
    }

    private fun SearchIndexEntity.typeWeight(): Int = when (targetType.toSearchTargetType()) {
        SearchTargetType.Page -> 36
        SearchTargetType.Table -> 30
        SearchTargetType.Row -> 24
        SearchTargetType.Cell -> 20
        SearchTargetType.Property,
        SearchTargetType.Column,
        -> 18
        SearchTargetType.Block -> 14
        SearchTargetType.Chat -> 8
    }

    private fun SearchIndexEntity.currentPageBoost(currentPageId: String): Int =
        if (currentPageId.isNotBlank() && pageId == currentPageId) 28 else 0

    private fun SearchIndexEntity.emptyQueryScore(currentPageId: String, nowMillis: Long): Int =
        typeWeight() + currentPageBoost(currentPageId) + recencyBoost(nowMillis)

    private fun SearchIndexEntity.recencyBoost(nowMillis: Long): Int {
        if (updatedAt <= 0) return 0
        val ageMillis = max(0, nowMillis - updatedAt)
        return when {
            ageMillis <= OneDayMillis -> 12
            ageMillis <= SevenDaysMillis -> 8
            ageMillis <= ThirtyDaysMillis -> 4
            else -> 0
        }
    }

    private fun SearchIndexEntity.bestSnippet(terms: List<String>): String {
        val source = listOf(snippet, subtitle, title)
            .firstOrNull { value -> value.isNotBlank() }
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
        if (source.length <= SnippetMaxChars) return source

        val lowerSource = source.lowercase()
        val matchIndex = terms
            .asSequence()
            .map { term -> lowerSource.indexOf(term.lowercase()) }
            .filter { index -> index >= 0 }
            .minOrNull()
            ?: 0
        val start = (matchIndex - SnippetContextChars).coerceAtLeast(0)
        val end = (matchIndex + SnippetContextChars).coerceAtMost(source.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < source.length) "..." else ""
        return prefix + source.substring(start, end).trim() + suffix
    }

    private fun String.hasTokenMatch(term: String): Boolean =
        contains(term)

    private fun String.toSearchTargetType(): SearchTargetType =
        SearchTargetType.entries.firstOrNull { type -> type.name == this } ?: SearchTargetType.Page

    private companion object {
        private const val MinTextTermLength = 2
        private const val MaxTerms = 8
        private const val SnippetMaxChars = 160
        private const val SnippetContextChars = 70
        private const val OneDayMillis = 24L * 60L * 60L * 1000L
        private const val SevenDaysMillis = 7L * OneDayMillis
        private const val ThirtyDaysMillis = 30L * OneDayMillis
    }
}
