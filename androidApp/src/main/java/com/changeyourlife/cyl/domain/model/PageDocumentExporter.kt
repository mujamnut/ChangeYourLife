package com.changeyourlife.cyl.domain.model

object PageDocumentExporter {
    fun toMarkdown(document: PageBlockDocument): String {
        return document.blocks
            .flatMapIndexed { index, block -> block.toMarkdownLines(depth = 0, siblingIndex = index) }
            .joinToString("\n")
            .trim()
    }

    fun toHtml(document: PageBlockDocument): String {
        return document.blocks
            .joinToString("\n") { block -> block.toHtml(depth = 0) }
            .trim()
    }
}

private fun PageBlock.toMarkdownLines(depth: Int, siblingIndex: Int): List<String> {
    val indent = "  ".repeat(depth)
    val content = text.toMarkdownInline(RichTextSpanEngine.normalize(richTextSpans, text))
    val ownLines = when (type) {
        PageBlockType.Text -> listOf("$indent$content")
        PageBlockType.Heading -> listOf("$indent# $content")
        PageBlockType.Todo -> listOf("$indent- [${if (isChecked) "x" else " "}] $content")
        PageBlockType.Bullet -> listOf("$indent- $content")
        PageBlockType.Numbered -> listOf("$indent${siblingIndex + 1}. $content")
        PageBlockType.Toggle -> listOf("$indent<details><summary>$content</summary></details>")
        PageBlockType.Quote -> listOf("$indent> $content")
        PageBlockType.Callout -> listOf("$indent> [!NOTE] $content")
        PageBlockType.Code -> listOf("$indent```", "$indent$content", "$indent```")
        PageBlockType.Table -> table.toMarkdownLines(indent, includeTitle = false)
        PageBlockType.WebBookmark -> listOf("$indent[$content]($content)")
        PageBlockType.Divider -> listOf("$indent---")
        PageBlockType.MediaFile -> mediaAttachments
            .map { attachment -> "$indent[${attachment.name.escapeMarkdown()}](${attachment.uri})" }
            .ifEmpty { listOf("$indent$content") }
        PageBlockType.DatabaseTable -> table.toMarkdownLines(indent, includeTitle = true)
    }.filter { line -> line.isNotBlank() }

    val childLines = children.flatMapIndexed { index, child ->
        child.toMarkdownLines(depth = depth + 1, siblingIndex = index)
    }
    return ownLines + childLines
}

private fun PageTable.toMarkdownLines(indent: String, includeTitle: Boolean): List<String> {
    if (columns.isEmpty()) {
        return if (includeTitle) listOf("$indent### ${title.escapeMarkdown()}") else emptyList()
    }
    val header = columns.joinToString(" | ") { column -> column.name.escapeMarkdown() }
    val separator = columns.joinToString(" | ") { "---" }
    val rowLines = rows.map { row ->
        columns.joinToString(" | ") { column ->
            row.cells[column.id].orEmpty().escapeMarkdownTableCell()
        }
    }
    val titleLines = if (includeTitle) listOf("$indent### ${title.escapeMarkdown()}") else emptyList()
    return titleLines + listOf(
        "$indent| $header |",
        "$indent| $separator |",
    ) + rowLines.map { line -> "$indent| $line |" }
}

private fun String.toMarkdownInline(spans: List<PageTextSpan>): String {
    if (isEmpty()) return ""
    return richSegments(spans).joinToString("") { segment ->
        var value = segment.text.escapeMarkdown()
        if (segment.style.code) value = "`${value.replace("`", "\\`")}`"
        if (segment.style.bold) value = "**$value**"
        if (segment.style.italic) value = "_${value}_"
        if (segment.style.underline) value = "<u>$value</u>"
        if (segment.style.strikethrough) value = "~~$value~~"
        if (segment.style.linkUrl.isNotBlank()) value = "[$value](${segment.style.linkUrl})"
        value
    }
}

private fun PageBlock.toHtml(depth: Int): String {
    val content = text.toHtmlInline(RichTextSpanEngine.normalize(richTextSpans, text))
    val own = when (type) {
        PageBlockType.Text -> "<p>$content</p>"
        PageBlockType.Heading -> "<h1>$content</h1>"
        PageBlockType.Todo -> "<label><input type=\"checkbox\"${if (isChecked) " checked" else ""}> $content</label>"
        PageBlockType.Bullet -> "<ul><li>$content</li></ul>"
        PageBlockType.Numbered -> "<ol><li>$content</li></ol>"
        PageBlockType.Toggle -> "<details><summary>$content</summary></details>"
        PageBlockType.Quote -> "<blockquote>$content</blockquote>"
        PageBlockType.Callout -> "<aside>$content</aside>"
        PageBlockType.Code -> "<pre><code>$content</code></pre>"
        PageBlockType.Table -> table.toHtml(includeTitle = false)
        PageBlockType.WebBookmark -> "<p><a href=\"${text.escapeHtmlAttribute()}\">$content</a></p>"
        PageBlockType.Divider -> "<hr>"
        PageBlockType.MediaFile -> mediaAttachments
            .joinToString("") { attachment ->
                "<p><a href=\"${attachment.uri.escapeHtmlAttribute()}\">${attachment.name.escapeHtml()}</a></p>"
            }
            .ifBlank { "<p>$content</p>" }
        PageBlockType.DatabaseTable -> table.toHtml(includeTitle = true)
    }
    if (children.isEmpty()) return own
    val nested = children.joinToString("\n") { child -> child.toHtml(depth = depth + 1) }
    return "$own\n<div class=\"cyl-children\" data-depth=\"$depth\">\n$nested\n</div>"
}

private fun PageTable.toHtml(includeTitle: Boolean): String {
    val header = columns.joinToString("") { column -> "<th>${column.name.escapeHtml()}</th>" }
    val body = rows.joinToString("") { row ->
        val cells = columns.joinToString("") { column ->
            "<td>${row.cells[column.id].orEmpty().escapeHtml()}</td>"
        }
        "<tr>$cells</tr>"
    }
    val caption = if (includeTitle) "<figcaption>${title.escapeHtml()}</figcaption>" else ""
    return "<figure>$caption<table><thead><tr>$header</tr></thead><tbody>$body</tbody></table></figure>"
}

private fun String.toHtmlInline(spans: List<PageTextSpan>): String {
    if (isEmpty()) return ""
    return richSegments(spans).joinToString("") { segment ->
        var value = segment.text.escapeHtml()
        if (segment.style.code) value = "<code>$value</code>"
        if (segment.style.bold) value = "<strong>$value</strong>"
        if (segment.style.italic) value = "<em>$value</em>"
        if (segment.style.underline) value = "<u>$value</u>"
        if (segment.style.strikethrough) value = "<s>$value</s>"
        val styles = buildList {
            if (segment.style.color.isNotBlank()) add("color:${segment.style.color}")
            if (segment.style.highlight.isNotBlank()) add("background-color:${segment.style.highlight}")
        }
        if (styles.isNotEmpty()) value = "<span style=\"${styles.joinToString(";")}\">$value</span>"
        if (segment.style.linkUrl.isNotBlank()) {
            value = "<a href=\"${segment.style.linkUrl.escapeHtmlAttribute()}\">$value</a>"
        }
        value
    }
}

private data class RichSegment(
    val text: String,
    val style: PageTextSpan,
)

private fun String.richSegments(spans: List<PageTextSpan>): List<RichSegment> {
    val points = (listOf(0, length) + spans.flatMap { span -> listOf(span.start, span.end) })
        .map { point -> point.coerceIn(0, length) }
        .distinct()
        .sorted()
    return points.zipWithNext().mapNotNull { (start, end) ->
        if (start == end) return@mapNotNull null
        val style = spans
            .filter { span -> span.start <= start && span.end >= end }
            .fold(PageTextSpan(start, end)) { acc, span ->
                acc.copy(
                    bold = acc.bold || span.bold,
                    italic = acc.italic || span.italic,
                    underline = acc.underline || span.underline,
                    strikethrough = acc.strikethrough || span.strikethrough,
                    code = acc.code || span.code,
                    linkUrl = acc.linkUrl.ifBlank { span.linkUrl },
                    color = acc.color.ifBlank { span.color },
                    highlight = acc.highlight.ifBlank { span.highlight },
                    mentionPageId = acc.mentionPageId.ifBlank { span.mentionPageId },
                    mentionLabel = acc.mentionLabel.ifBlank { span.mentionLabel },
                )
            }
        RichSegment(substring(start, end), style)
    }
}

private fun String.escapeMarkdown(): String =
    replace("\\", "\\\\")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("`", "\\`")

private fun String.escapeMarkdownTableCell(): String =
    escapeMarkdown().replace("|", "\\|").replace("\n", "<br>")

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun String.escapeHtmlAttribute(): String =
    escapeHtml().replace("\"", "&quot;")
