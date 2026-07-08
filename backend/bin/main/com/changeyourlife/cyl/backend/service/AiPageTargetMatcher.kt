package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiPageContext

internal object AiPageTargetMatcher {
    fun findPageByAiTitle(
        pages: List<AiPageContext>,
        rawTitle: String,
    ): AiPageContext? {
        val title = rawTitle.cleanAiPageTitle()
        if (title.isBlank()) return null
        pages.firstOrNull { page -> page.id == title }?.let { return it }

        val normalizedTitle = title.normalizeForAiPageMatch()
        if (normalizedTitle.isBlank()) return null
        return pages
            .filter { page -> page.title.normalizeForAiPageMatch() == normalizedTitle }
            .singleOrNull()
    }

    fun findTargetPage(
        pages: List<AiPageContext>,
        prompt: String,
        allowSinglePageFallback: Boolean,
    ): AiPageContext? {
        val contextPages = prompt.extractMentionContextPageIds()
            .mapNotNull { pageId -> pages.firstOrNull { page -> page.id == pageId } }
            .distinctBy { page -> page.id }
        val visiblePrompt = prompt.withoutMentionContext()
        val explicitMentionPool = contextPages.ifEmpty { pages }
        val explicitlyMentionedPages = findExplicitMentionedPages(
            prompt = visiblePrompt,
            pages = explicitMentionPool,
        )

        return when {
            explicitlyMentionedPages.size == 1 -> explicitlyMentionedPages.single()
            explicitlyMentionedPages.size > 1 -> null
            contextPages.size == 1 -> contextPages.single()
            contextPages.size > 1 -> null
            visiblePrompt.contains("@") -> null
            allowSinglePageFallback -> pages.singleOrNull()
            else -> null
        }
    }

    private fun findExplicitMentionedPages(
        prompt: String,
        pages: List<AiPageContext>,
    ): List<AiPageContext> =
        pages
            .asSequence()
            .filter { page -> page.title.isNotBlank() }
            .sortedByDescending { page -> page.title.length }
            .filter { page -> prompt.hasExactPageMention(page.title) }
            .distinctBy { page -> page.id }
            .toList()

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

    private fun String.extractMentionContextPageIds(): List<String> {
        val context = substringAfter("CYL_MENTION_CONTEXT:", missingDelimiterValue = "")
        if (context.isBlank()) return emptyList()
        return Regex("\\bid=([^\\s]+)")
            .findAll(context)
            .map { match -> match.groupValues.getOrNull(1).orEmpty().trim() }
            .filter { pageId -> pageId.isNotBlank() }
            .distinct()
            .toList()
    }

    private fun String.withoutMentionContext(): String =
        substringBefore("CYL_MENTION_CONTEXT:").trim()

    private fun String.cleanAiPageTitle(): String =
        trim().removePrefix("@").trim()

    private fun String.normalizeForAiPageMatch(): String =
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
