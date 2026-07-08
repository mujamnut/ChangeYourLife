package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.Page

internal object AiPageTargetResolver {
    fun findMentionedPages(
        prompt: String,
        pages: List<Page>,
    ): List<Page> =
        pages
            .asSequence()
            .filter { page -> page.title.isNotBlank() }
            .sortedByDescending { page -> page.title.length }
            .filter { page -> prompt.hasExactPageMention(page.title) }
            .distinctBy { page -> page.id }
            .toList()

    fun resolveExactTarget(
        pages: List<Page>,
        rawTitle: String,
    ): TargetPageResolution {
        val title = rawTitle.trim().removePrefix("@").trim()
        if (title.isBlank()) return TargetPageResolution.Missing

        pages.firstOrNull { page -> page.id == title }?.let { page ->
            return TargetPageResolution.Found(page)
        }

        val normalizedTitle = title.normalizeForPageTarget()
        if (normalizedTitle.isBlank()) return TargetPageResolution.Missing
        val matches = pages.filter { page -> page.title.normalizeForPageTarget() == normalizedTitle }
        return when (matches.size) {
            0 -> TargetPageResolution.Missing
            1 -> TargetPageResolution.Found(matches.single())
            else -> TargetPageResolution.Ambiguous
        }
    }

    private fun String.hasExactPageMention(title: String): Boolean {
        if (title.isBlank()) return false
        return Regex("@${Regex.escape(title)}", RegexOption.IGNORE_CASE)
            .findAll(this)
            .any { match ->
                val beforeIndex = match.range.first - 1
                val beforeOk = beforeIndex < 0 || !this[beforeIndex].isLetterOrDigit()
                val tail = substring(match.range.last + 1)
                beforeOk && tail.isAcceptedMentionTail()
            }
    }

    private fun String.isAcceptedMentionTail(): Boolean {
        if (isEmpty()) return true
        val first = first()
        if (first in mentionPunctuationBoundaries) return true
        if (!first.isWhitespace()) return false
        val trimmed = trimStart()
        if (trimmed.isBlank()) return true
        val nextWord = trimmed
            .takeWhile { char -> char.isLetterOrDigit() }
            .lowercase()
        return nextWord in mentionCommandBoundaryWords
    }

    private fun String.normalizeForPageTarget(): String =
        lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val mentionPunctuationBoundaries = setOf(',', '.', ';', ':', '!', '?', ')', ']', '}', '\n', '\r')

    private val mentionCommandBoundaryWords = setOf(
        "add",
        "and",
        "as",
        "baris",
        "block",
        "blok",
        "buat",
        "buatkan",
        "buang",
        "catat",
        "change",
        "cipta",
        "create",
        "current",
        "dan",
        "database",
        "dekat",
        "delete",
        "di",
        "dalam",
        "edit",
        "ganti",
        "hapus",
        "ini",
        "insert",
        "into",
        "jadual",
        "ke",
        "kepada",
        "masukkan",
        "nama",
        "note",
        "nota",
        "padam",
        "page",
        "property",
        "record",
        "rekod",
        "remove",
        "rename",
        "row",
        "sebagai",
        "sini",
        "table",
        "tambah",
        "this",
        "to",
        "tukar",
        "ubah",
        "update",
        "with",
        "yang",
    )
}

internal sealed interface TargetPageResolution {
    data class Found(val page: Page) : TargetPageResolution
    data object Missing : TargetPageResolution
    data object Ambiguous : TargetPageResolution
}
