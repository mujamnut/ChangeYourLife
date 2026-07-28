package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiTableRowContext
import com.changeyourlife.cyl.backend.model.ai.AiTaskContext

internal data class AiActionContextResult(
    val text: String,
    val coverage: String,
    val detailedPageCount: Int,
    val totalPageCount: Int,
    val includedRowCount: Int,
    val totalRowCount: Int,
)

internal class AiActionContextBuilder(
    private val maxDetailChars: Int = DefaultMaxDetailChars,
    private val maxSingleValueChars: Int = DefaultMaxSingleValueChars,
) {
    fun build(
        pages: List<AiPageContext>,
        tasks: List<AiTaskContext>,
        latestUserPrompt: String,
        clientDate: String,
        clientTimezone: String,
    ): AiActionContextResult {
        val rankedPages = pages.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<AiPageContext>> { indexed ->
                    indexed.value.relevanceScore(latestUserPrompt)
                }.thenBy { indexed -> indexed.index },
            )
            .map(IndexedValue<AiPageContext>::value)
        val detail = StringBuilder()
        var remainingChars = maxDetailChars.coerceAtLeast(MinimumDetailChars)
        var detailedPages = 0
        var includedRows = 0
        var contextComplete = true

        rankedPages.forEach { page ->
            val rendered = renderPage(page, remainingChars)
            if (rendered.text.isNotBlank()) {
                detail.append(rendered.text)
                remainingChars -= rendered.text.length
                detailedPages += 1
                includedRows += rendered.includedRows
            }
            if (!rendered.complete) contextComplete = false
        }

        val totalRows = pages.sumOf { page ->
            page.blocks.sumOf { block -> block.totalRowCount.coerceAtLeast(block.tableRows.size) }
        }
        if (includedRows < totalRows || detailedPages < pages.size || pages.any { !it.contextComplete }) {
            contextComplete = false
        }
        val coverage = if (contextComplete) CoverageFull else CoveragePartial
        val text = buildString {
            appendLine("Client date: ${clientDate.ifBlank { "unknown" }}")
            appendLine("Client timezone: ${clientTimezone.ifBlank { "unknown" }}")
            appendLine()
            appendLine("CYL_WORKSPACE_MANIFEST:")
            if (pages.isEmpty()) {
                appendLine("- none")
            } else {
                pages.forEach { page ->
                    appendLine(page.manifestLine())
                    page.blocks
                        .filter { block -> block.isTableContext() }
                        .forEach { table ->
                            appendLine(table.manifestLine())
                        }
                }
            }
            appendLine()
            appendLine("CYL_CONTEXT_DETAILS:")
            if (detail.isEmpty()) {
                appendLine("- none")
            } else {
                append(detail)
            }
            appendLine()
            appendLine("Open tasks:")
            if (tasks.isEmpty()) {
                appendLine("- none")
            } else {
                tasks.forEach { task ->
                    appendLine("- id=${task.id.quoted()} title=${task.title.compactForContext().quoted()}")
                }
            }
            appendLine()
            appendLine(
                "CYL_CONTEXT_COVERAGE: status=$coverage " +
                    "detailedPages=$detailedPages/${pages.size} rows=$includedRows/$totalRows",
            )
            if (!contextComplete) {
                appendLine(
                    "Some detail exceeded the model context budget or arrived incomplete. " +
                        "The manifest counts remain authoritative; omitted data must not be treated as empty.",
                )
            }
        }
        return AiActionContextResult(
            text = text,
            coverage = coverage,
            detailedPageCount = detailedPages,
            totalPageCount = pages.size,
            includedRowCount = includedRows,
            totalRowCount = totalRows,
        )
    }

    private fun renderPage(
        page: AiPageContext,
        availableChars: Int,
    ): RenderedPage {
        if (availableChars < MinimumPageDetailChars) {
            return RenderedPage(complete = page.blocks.isEmpty() && page.contextComplete)
        }
        val builder = StringBuilder()
        builder.appendLine(
            "page id=${page.id.quoted()} title=${page.title.compactForContext().quoted()} " +
                "focused=${page.isFocused} blocks=${page.blocks.size}/${page.totalBlockCount}",
        )
        val pageCoverageReserve = 140
        var remaining = availableChars - builder.length - pageCoverageReserve
        var includedRows = 0
        var emittedBlocks = 0
        var complete = page.contextComplete
        val orderedBlocks = page.blocks.sortedByDescending { block -> block.isTableContext() }

        orderedBlocks.forEach { block ->
            val rendered = if (block.isTableContext()) {
                renderTable(block, remaining)
            } else {
                renderBlock(block, remaining)
            }
            if (rendered.text.isNotBlank()) {
                builder.append(rendered.text)
                remaining -= rendered.text.length
                emittedBlocks += 1
                includedRows += rendered.includedRows
            }
            if (!rendered.complete) complete = false
        }
        if (emittedBlocks < page.blocks.size || page.blocks.size < page.totalBlockCount) {
            complete = false
        }
        builder.appendLine(
            "pageCoverage id=${page.id.quoted()} status=${if (complete) CoverageFull else CoveragePartial} " +
                "blocks=$emittedBlocks/${page.totalBlockCount}",
        )
        if (builder.length > availableChars) {
            return RenderedPage(complete = false)
        }
        return RenderedPage(
            text = builder.toString(),
            includedRows = includedRows,
            complete = complete,
        )
    }

    private fun renderTable(
        block: AiBlockContext,
        availableChars: Int,
    ): RenderedBlock {
        if (availableChars < MinimumBlockDetailChars) {
            return RenderedBlock(complete = false)
        }
        val title = block.tableTitle.ifBlank { "Table" }.compactForContext()
        val rowRenderings = block.tableRows.map(::renderRow)
        val columnsText = block.tableColumns.joinToString(separator = ", ") { column ->
            buildString {
                append(
                    "id=${column.id.quoted()} name=${column.name.compactForContext().quoted()} " +
                        "type=${column.type.quoted()}",
                )
                if (column.config.isNotBlank()) {
                    append(" config=${column.config.compactForContext().quoted()}")
                }
            }
        }
        val prefix = buildString {
            appendLine(
                "  table id=${block.tableBlockId.ifBlank { block.id }.quoted()} " +
                    "title=${title.quoted()} rows=${block.tableRows.size}/${block.totalRowCount} " +
                    "sourceComplete=${block.contextComplete}",
            )
            appendLine("    state=${block.text.compactForContext().quoted()}")
            appendLine("    columns=$columnsText")
        }
        val suffixReserve = 160
        var used = prefix.length + suffixReserve
        val included = mutableListOf<RenderedRow>()
        rowRenderings.forEach { row ->
            if (used + row.text.length <= availableChars) {
                included += row
                used += row.text.length
            }
        }
        val totalRows = block.totalRowCount.coerceAtLeast(block.tableRows.size)
        val complete = block.contextComplete &&
            included.size == totalRows &&
            included.all(RenderedRow::complete)
        val text = buildString {
            append(prefix)
            included.forEach { row -> append(row.text) }
            appendLine(
                "    tableCoverage status=${if (complete) CoverageFull else CoveragePartial} " +
                    "rows=${included.size}/$totalRows",
            )
        }
        if (text.length > availableChars) {
            return RenderedBlock(complete = false)
        }
        return RenderedBlock(
            text = text,
            includedRows = included.size,
            complete = complete,
        )
    }

    private fun renderRow(row: AiTableRowContext): RenderedRow {
        var complete = true
        val cells = row.cells.joinToString(separator = ", ") { cell ->
            val renderedValue = cell.value.renderValue()
            if (!renderedValue.complete) complete = false
            "columnId=${cell.columnId.quoted()} " +
                "column=${cell.columnName.compactForContext().quoted()} value=${renderedValue.text}"
        }
        return RenderedRow(
            text = "    row id=${row.id.quoted()} title=${row.title.compactForContext().quoted()} " +
                "cells=[$cells] rowBlocks=${row.totalBlockCount}\n",
            complete = complete,
        )
    }

    private fun renderBlock(
        block: AiBlockContext,
        availableChars: Int,
    ): RenderedBlock {
        if (availableChars < MinimumBlockDetailChars) {
            return RenderedBlock(complete = false)
        }
        val renderedText = block.text.renderValue()
        val line = buildString {
            append("  block id=${block.id.quoted()} type=${block.type} path=${block.path.quoted()}")
            if (block.rowId.isNotBlank()) {
                append(" rowId=${block.rowId.quoted()} rowTitle=${block.rowTitle.compactForContext().quoted()}")
            }
            block.isChecked?.let { checked -> append(" checked=$checked") }
            append(" text=${renderedText.text}\n")
        }
        return if (line.length <= availableChars) {
            RenderedBlock(
                text = line,
                complete = block.contextComplete && renderedText.complete,
            )
        } else {
            RenderedBlock(complete = false)
        }
    }

    private fun String.renderValue(): RenderedValue {
        val compact = compactForContext()
        if (compact.length <= maxSingleValueChars) {
            return RenderedValue(text = compact.quoted(), complete = true)
        }
        val kept = compact.take(maxSingleValueChars)
        return RenderedValue(
            text = "$kept [omittedChars=${compact.length - kept.length}]".quoted(),
            complete = false,
        )
    }

    private fun AiPageContext.manifestLine(): String {
        val tableCount = blocks.count { block -> block.isTableContext() }
        return "- page id=${id.quoted()} title=${title.compactForContext().quoted()} " +
            "focused=$isFocused blocks=${blocks.size}/$totalBlockCount tables=$tableCount " +
            "sourceComplete=$contextComplete"
    }

    private fun AiBlockContext.manifestLine(): String =
        "  - table id=${tableBlockId.ifBlank { id }.quoted()} " +
            "title=${tableTitle.ifBlank { "Table" }.compactForContext().quoted()} " +
            "columns=${tableColumns.size} rows=${tableRows.size}/$totalRowCount " +
            "sourceComplete=$contextComplete"

    private fun AiBlockContext.isTableContext(): Boolean =
        type.equals("DatabaseTable", ignoreCase = true) ||
            tableTitle.isNotBlank() ||
            tableColumns.isNotEmpty() ||
            tableRows.isNotEmpty() ||
            totalRowCount > 0

    private fun AiPageContext.relevanceScore(prompt: String): Int {
        var score = if (isFocused) FocusedPageScore else 0
        val normalizedPrompt = prompt.lowercase()
        if (id.isNotBlank() && normalizedPrompt.contains(id.lowercase())) {
            score += MentionedPageScore
        }
        val normalizedTitle = title.trim().lowercase()
        if (normalizedTitle.isNotBlank() && normalizedPrompt.contains(normalizedTitle)) {
            score += ExactTitleScore
        }
        val terms = prompt.contextTerms()
        score += terms.count { term -> normalizedTitle.contains(term) } * TitleTermScore
        blocks.forEach { block ->
            val metadata = "${block.tableTitle} ${block.rowTitle}".lowercase()
            score += terms.count { term -> metadata.contains(term) } * MetadataTermScore
            if (score < FocusedPageScore && terms.isNotEmpty()) {
                val hasMatchingRow = block.tableRows.any { row ->
                    row.title.lowercase().containsAny(terms) ||
                        row.cells.any { cell -> cell.value.lowercase().containsAny(terms) }
                }
                if (hasMatchingRow) score += RowMatchScore
            }
        }
        return score
    }

    private fun String.contextTerms(): List<String> =
        lowercase()
            .substringBefore("cyl_mention_context:")
            .split(NonWordPattern)
            .filter { token -> token.length >= 2 && token !in ContextStopWords }
            .distinct()

    private fun String.containsAny(terms: List<String>): Boolean =
        terms.any { term -> contains(term) }

    private fun String.compactForContext(): String =
        trim()
            .replace("\r", "")
            .replace("\n", "\\n")

    private fun String.quoted(): String =
        "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private data class RenderedPage(
        val text: String = "",
        val includedRows: Int = 0,
        val complete: Boolean,
    )

    private data class RenderedBlock(
        val text: String = "",
        val includedRows: Int = 0,
        val complete: Boolean,
    )

    private data class RenderedRow(
        val text: String,
        val complete: Boolean,
    )

    private data class RenderedValue(
        val text: String,
        val complete: Boolean,
    )

    private companion object {
        const val DefaultMaxDetailChars = 128_000
        const val DefaultMaxSingleValueChars = 12_000
        const val MinimumDetailChars = 8_000
        const val MinimumPageDetailChars = 240
        const val MinimumBlockDetailChars = 160
        const val FocusedPageScore = 1_000_000
        const val MentionedPageScore = 500_000
        const val ExactTitleScore = 100_000
        const val TitleTermScore = 5_000
        const val MetadataTermScore = 500
        const val RowMatchScore = 1_000
        const val CoverageFull = "FULL"
        const val CoveragePartial = "PARTIAL"

        val NonWordPattern = Regex("[^a-z0-9@_-]+")
        val ContextStopWords = setOf(
            "ai",
            "aku",
            "awak",
            "boleh",
            "buat",
            "cari",
            "data",
            "dalam",
            "dan",
            "di",
            "for",
            "in",
            "ini",
            "itu",
            "page",
            "row",
            "table",
            "the",
            "to",
            "tolong",
            "yang",
        )
    }
}
