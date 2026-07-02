package com.changeyourlife.cyl.domain.model

enum class RichTextFormat {
    Bold,
    Italic,
    Underline,
    Strikethrough,
}

object RichTextSpanEngine {
    fun normalize(
        spans: List<PageTextSpan>,
        text: String,
    ): List<PageTextSpan> {
        if (text.isEmpty()) return emptyList()
        return spans.mapNotNull { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(0, text.length)
            if (start >= end || !span.hasAnyStyle()) {
                null
            } else {
                span.copy(start = start, end = end)
            }
        }.mergeAdjacent()
    }

    fun adjustForTextChange(
        spans: List<PageTextSpan>,
        oldText: String,
        newText: String,
    ): List<PageTextSpan> {
        if (spans.isEmpty() || oldText == newText) return normalize(spans, newText)
        val prefixLength = oldText.commonPrefixWith(newText).length
        val suffixLength = oldText
            .drop(prefixLength)
            .commonSuffixWith(newText.drop(prefixLength))
            .length
        val oldChangeEnd = oldText.length - suffixLength
        val newChangeEnd = newText.length - suffixLength
        val delta = newChangeEnd - oldChangeEnd

        return spans.mapNotNull { span ->
            when {
                span.end <= prefixLength -> span
                span.start >= oldChangeEnd -> span.copy(
                    start = span.start + delta,
                    end = span.end + delta,
                )
                else -> span.copy(end = (span.end + delta).coerceAtLeast(prefixLength))
            }
        }.let { normalize(it, newText) }
    }

    fun toggleFormat(
        spans: List<PageTextSpan>,
        format: RichTextFormat,
        start: Int,
        end: Int,
        textLength: Int,
    ): List<PageTextSpan> {
        val safeStart = start.coerceIn(0, textLength)
        val safeEnd = end.coerceIn(0, textLength)
        if (safeStart >= safeEnd) return normalize(spans, " ".repeat(textLength))
        val flags = spans.toRichTextFlags(textLength)
        val shouldEnable = (safeStart until safeEnd).any { index -> !flags[index].has(format) }
        for (index in safeStart until safeEnd) {
            flags[index].set(format, shouldEnable)
        }
        return flags.toTextSpans()
    }

    fun hasFormat(
        spans: List<PageTextSpan>,
        format: RichTextFormat,
        start: Int,
        end: Int,
    ): Boolean {
        if (start >= end) return false
        val flags = spans.toRichTextFlags(end)
        return (start until end).all { index -> flags[index].has(format) }
    }
}

fun PageTextSpan.hasAnyStyle(): Boolean =
    bold || italic || underline || strikethrough

private data class RichTextFlags(
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var strikethrough: Boolean = false,
) {
    fun isEmpty(): Boolean = !bold && !italic && !underline && !strikethrough

    fun has(format: RichTextFormat): Boolean {
        return when (format) {
            RichTextFormat.Bold -> bold
            RichTextFormat.Italic -> italic
            RichTextFormat.Underline -> underline
            RichTextFormat.Strikethrough -> strikethrough
        }
    }

    fun set(format: RichTextFormat, value: Boolean) {
        when (format) {
            RichTextFormat.Bold -> bold = value
            RichTextFormat.Italic -> italic = value
            RichTextFormat.Underline -> underline = value
            RichTextFormat.Strikethrough -> strikethrough = value
        }
    }

    fun sameStyleAs(other: RichTextFlags): Boolean {
        return bold == other.bold &&
            italic == other.italic &&
            underline == other.underline &&
            strikethrough == other.strikethrough
    }

    fun toSpan(start: Int, end: Int): PageTextSpan {
        return PageTextSpan(
            start = start,
            end = end,
            bold = bold,
            italic = italic,
            underline = underline,
            strikethrough = strikethrough,
        )
    }
}

private fun List<PageTextSpan>.mergeAdjacent(): List<PageTextSpan> {
    if (isEmpty()) return emptyList()
    return sortedWith(compareBy<PageTextSpan> { it.start }.thenBy { it.end })
        .fold(mutableListOf()) { merged, span ->
            val last = merged.lastOrNull()
            if (last != null && last.end >= span.start && last.sameStyleAs(span)) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, span.end))
            } else {
                merged += span
            }
            merged
        }
}

private fun PageTextSpan.sameStyleAs(other: PageTextSpan): Boolean {
    return bold == other.bold &&
        italic == other.italic &&
        underline == other.underline &&
        strikethrough == other.strikethrough
}

private fun List<PageTextSpan>.toRichTextFlags(textLength: Int): MutableList<RichTextFlags> {
    val flags = MutableList(textLength) { RichTextFlags() }
    RichTextSpanEngine.normalize(this, " ".repeat(textLength)).forEach { span ->
        for (index in span.start until span.end) {
            flags[index].bold = flags[index].bold || span.bold
            flags[index].italic = flags[index].italic || span.italic
            flags[index].underline = flags[index].underline || span.underline
            flags[index].strikethrough = flags[index].strikethrough || span.strikethrough
        }
    }
    return flags
}

private fun List<RichTextFlags>.toTextSpans(): List<PageTextSpan> {
    val spans = mutableListOf<PageTextSpan>()
    var spanStart = -1
    var current = RichTextFlags()
    forEachIndexed { index, flags ->
        if (flags.isEmpty()) {
            if (spanStart != -1) {
                spans += current.toSpan(spanStart, index)
                spanStart = -1
                current = RichTextFlags()
            }
        } else if (spanStart == -1) {
            spanStart = index
            current = flags.copy()
        } else if (!current.sameStyleAs(flags)) {
            spans += current.toSpan(spanStart, index)
            spanStart = index
            current = flags.copy()
        }
    }
    if (spanStart != -1) {
        spans += current.toSpan(spanStart, size)
    }
    return spans
}
