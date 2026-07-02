package com.changeyourlife.cyl.presentation.page

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.RichTextFormat
import com.changeyourlife.cyl.domain.model.RichTextSpanEngine

data class RichTextFormatSet(
    val formats: Set<RichTextFormat> = emptySet(),
) {
    operator fun contains(format: RichTextFormat): Boolean = format in formats
}

data class RichTextEditorState(
    val blockId: String,
    val value: TextFieldValue,
    val spans: List<PageTextSpan> = emptyList(),
    val activeFormats: RichTextFormatSet = RichTextFormatSet(),
    val typingFormats: RichTextFormatSet = RichTextFormatSet(),
    val typingLinkUrl: String = "",
    val typingColor: String = "",
    val typingHighlight: String = "",
    val isFocused: Boolean = false,
)

class RichTextController(
    initialState: RichTextEditorState,
) {
    var state: RichTextEditorState = initialState.normalized()
        private set

    fun updateFocus(isFocused: Boolean): RichTextEditorState {
        state = state.copy(isFocused = isFocused).withActiveFormats()
        return state
    }

    fun updateText(
        newValue: TextFieldValue,
        incomingSpans: List<PageTextSpan> = emptyList(),
    ): RichTextEditorState {
        val adjustedSpans = when {
            newValue.text == state.value.text -> state.spans
            incomingSpans.isNotEmpty() -> incomingSpans
            state.spans.isEmpty() -> emptyList()
            else -> RichTextSpanEngine.adjustForTextChange(
                spans = state.spans,
                oldText = state.value.text,
                newText = newValue.text,
            )
        }
        val nextSpans = adjustedSpans.withTypingFormats(
            oldText = state.value.text,
            newText = newValue.text,
            formats = state.typingFormats.formats,
            linkUrl = state.typingLinkUrl,
            color = state.typingColor,
            highlight = state.typingHighlight,
        )
        state = state.copy(
            value = newValue.copy(selection = newValue.selection.coerceInText(newValue.text)),
            spans = RichTextSpanEngine.normalize(nextSpans, newValue.text),
        ).withActiveFormats()
        return state
    }

    fun toggleBold(): RichTextEditorState = toggleFormat(RichTextFormat.Bold)

    fun toggleItalic(): RichTextEditorState = toggleFormat(RichTextFormat.Italic)

    fun toggleUnderline(): RichTextEditorState = toggleFormat(RichTextFormat.Underline)

    fun toggleStrike(): RichTextEditorState = toggleFormat(RichTextFormat.Strikethrough)

    fun toggleCode(): RichTextEditorState = toggleFormat(RichTextFormat.Code)

    fun toggleFormat(format: RichTextFormat): RichTextEditorState {
        if (state.value.selection.start == state.value.selection.end) {
            val nextTypingFormats = state.typingFormats.formats.toMutableSet().apply {
                if (format in this) remove(format) else add(format)
            }
            state = state.copy(typingFormats = RichTextFormatSet(nextTypingFormats)).withActiveFormats()
            return state
        }
        val range = state.value.effectiveFormatRange()
        if (range.min == range.max) return state
        state = state.copy(
            spans = RichTextSpanEngine.toggleFormat(
                spans = state.spans,
                format = format,
                start = range.min,
                end = range.max,
                textLength = state.value.text.length,
            ),
        ).withActiveFormats()
        return state
    }

    fun applyLink(url: String): RichTextEditorState {
        val range = state.value.effectiveFormatRange()
        if (range.min == range.max) {
            state = state.copy(typingLinkUrl = url.trim()).withActiveFormats()
            return state
        }
        state = state.copy(
            spans = RichTextSpanEngine.applyLink(
                spans = state.spans,
                start = range.min,
                end = range.max,
                textLength = state.value.text.length,
                url = url,
            ),
        ).withActiveFormats()
        return state
    }

    fun applyColor(color: String): RichTextEditorState {
        val range = state.value.effectiveFormatRange()
        if (range.min == range.max) {
            state = state.copy(typingColor = color).withActiveFormats()
            return state
        }
        state = state.copy(
            spans = RichTextSpanEngine.applyColor(
                spans = state.spans,
                start = range.min,
                end = range.max,
                textLength = state.value.text.length,
                color = color,
            ),
        ).withActiveFormats()
        return state
    }

    fun applyHighlight(highlight: String): RichTextEditorState {
        val range = state.value.effectiveFormatRange()
        if (range.min == range.max) {
            state = state.copy(typingHighlight = highlight).withActiveFormats()
            return state
        }
        state = state.copy(
            spans = RichTextSpanEngine.applyHighlight(
                spans = state.spans,
                start = range.min,
                end = range.max,
                textLength = state.value.text.length,
                highlight = highlight,
            ),
        ).withActiveFormats()
        return state
    }

    fun replaceRangeWithMention(
        range: TextRange,
        pageId: String,
        title: String,
    ): RichTextEditorState {
        val safeRange = range.coerceInText(state.value.text)
        val mentionLabel = "@${title.ifBlank { "Untitled page" }}"
        val replacement = "$mentionLabel "
        val oldText = state.value.text
        val nextText = oldText.replaceRange(safeRange.min, safeRange.max, replacement)
        val mentionStart = safeRange.min
        val mentionEnd = mentionStart + mentionLabel.length
        val adjustedSpans = RichTextSpanEngine.adjustForTextChange(state.spans, oldText, nextText)
        val nextSpans = RichTextSpanEngine.applyMention(
            spans = adjustedSpans,
            start = mentionStart,
            end = mentionEnd,
            textLength = nextText.length,
            pageId = pageId,
            label = mentionLabel,
        )
        state = state.copy(
            value = TextFieldValue(
                text = nextText,
                selection = TextRange(mentionStart + replacement.length),
            ),
            spans = nextSpans,
        ).withActiveFormats()
        return state
    }

    private fun RichTextEditorState.normalized(): RichTextEditorState {
        return copy(
            value = value.copy(selection = value.selection.coerceInText(value.text)),
            spans = RichTextSpanEngine.normalize(spans, value.text),
        ).withActiveFormats()
    }

    private fun RichTextEditorState.withActiveFormats(): RichTextEditorState {
        if (value.selection.start == value.selection.end && typingFormats.formats.isNotEmpty()) {
            return copy(activeFormats = typingFormats)
        }
        val range = value.effectiveFormatRange()
        val active = RichTextFormat.entries.filterTo(mutableSetOf()) { format ->
            range.min != range.max &&
                RichTextSpanEngine.hasFormat(spans, format, range.min, range.max)
        }
        return copy(activeFormats = RichTextFormatSet(active))
    }

    private fun List<PageTextSpan>.withTypingFormats(
        oldText: String,
        newText: String,
        formats: Set<RichTextFormat>,
        linkUrl: String,
        color: String,
        highlight: String,
    ): List<PageTextSpan> {
        if (
            formats.isEmpty() &&
            linkUrl.isBlank() &&
            color.isBlank() &&
            highlight.isBlank()
        ) {
            return this
        }
        if (oldText == newText) return this
        val insertedRange = insertedRangeOrNull(oldText, newText) ?: return this
        if (insertedRange.min == insertedRange.max) return this
        val typingSpan = PageTextSpan(
            start = insertedRange.min,
            end = insertedRange.max,
            bold = RichTextFormat.Bold in formats,
            italic = RichTextFormat.Italic in formats,
            underline = RichTextFormat.Underline in formats,
            strikethrough = RichTextFormat.Strikethrough in formats,
            code = RichTextFormat.Code in formats,
            linkUrl = linkUrl,
            color = color,
            highlight = highlight,
        )
        return RichTextSpanEngine.normalize(this + typingSpan, newText)
    }
}

data class RichTextMentionQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

object RichTextMentionParser {
    fun activeQuery(
        text: String,
        cursor: Int,
    ): RichTextMentionQuery? {
        if (cursor !in 1..text.length) return null
        val atIndex = text.lastIndexOf('@', startIndex = cursor - 1)
        if (atIndex < 0) return null
        val prefix = text.substring(0, atIndex)
        if (prefix.isNotEmpty() && !prefix.last().isWhitespace()) return null
        val query = text.substring(atIndex + 1, cursor)
        if (query.any { it == '\n' || it == '\t' }) return null
        return RichTextMentionQuery(
            start = atIndex,
            end = cursor,
            query = query,
        )
    }
}

data class RichTextPasteBlock(
    val type: PageBlockType,
    val text: String,
    val spans: List<PageTextSpan> = emptyList(),
    val isChecked: Boolean = false,
)

object RichTextPasteParser {
    fun mergeTextChangeIntoBlocks(
        currentType: PageBlockType,
        currentIsChecked: Boolean,
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
        oldSpans: List<PageTextSpan>,
    ): List<RichTextPasteBlock> {
        val inserted = insertedTextOrNull(oldValue.text, newValue.text) ?: return emptyList()
        if (!inserted.contains('\n')) return emptyList()
        val parsed = parse(inserted)
        if (parsed.size <= 1) return emptyList()

        val range = changedRange(oldValue.text, newValue.text).coerceInText(oldValue.text)
        val beforeText = oldValue.text.substring(0, range.min)
        val afterText = oldValue.text.substring(range.max)
        val beforeSpans = oldSpans.clipBefore(range.min)
        val afterSpans = oldSpans.clipAfter(
            rangeEnd = range.max,
            newOffset = parsed.last().text.length,
        )

        val first = parsed.first()
        val last = parsed.last()
        val firstText = beforeText + first.text
        val lastText = last.text + afterText
        val firstType = if (beforeText.isBlank()) first.type else currentType
        val lastType = if (afterText.isBlank()) last.type else currentType

        return buildList {
            add(
                RichTextPasteBlock(
                    type = firstType,
                    text = firstText,
                    spans = RichTextSpanEngine.normalize(
                        beforeSpans + first.spans.shiftBy(beforeText.length),
                        firstText,
                    ),
                    isChecked = if (firstType == currentType && beforeText.isNotBlank()) currentIsChecked else first.isChecked,
                ),
            )
            addAll(parsed.drop(1).dropLast(1))
            add(
                RichTextPasteBlock(
                    type = lastType,
                    text = lastText,
                    spans = RichTextSpanEngine.normalize(
                        last.spans + afterSpans,
                        lastText,
                    ),
                    isChecked = if (lastType == currentType && afterText.isNotBlank()) currentIsChecked else last.isChecked,
                ),
            )
        }
    }

    fun parse(rawText: String): List<RichTextPasteBlock> {
        return rawText
            .toLightMarkdownFromHtml()
            .replace("\r\n", "\n")
            .split('\n')
            .mapNotNull { line -> line.toPasteBlockOrNull() }
            .ifEmpty {
                val inline = parseInlineMarkdown(rawText)
                listOf(RichTextPasteBlock(PageBlockType.Text, inline.text, inline.spans))
            }
    }

    private fun String.toPasteBlockOrNull(): RichTextPasteBlock? {
        if (isBlank()) return null
        val trimmed = trimStart()
        val (type, content, isChecked) = when {
            trimmed.startsWith("# ") -> Triple(PageBlockType.Heading, trimmed.removePrefix("# "), false)
            trimmed.startsWith("- [x] ", ignoreCase = true) -> {
                Triple(PageBlockType.Todo, trimmed.substring(6), true)
            }
            trimmed.startsWith("- [ ] ") -> Triple(PageBlockType.Todo, trimmed.substring(6), false)
            trimmed.startsWith("[x] ", ignoreCase = true) -> Triple(PageBlockType.Todo, trimmed.substring(4), true)
            trimmed.startsWith("[ ] ") -> Triple(PageBlockType.Todo, trimmed.substring(4), false)
            trimmed.startsWith("[] ") -> Triple(PageBlockType.Todo, trimmed.substring(3), false)
            trimmed.isNumberedListLine() -> Triple(PageBlockType.Numbered, trimmed.substringAfterNumberedMarker(), false)
            trimmed.startsWith("- ") -> Triple(PageBlockType.Bullet, trimmed.removePrefix("- "), false)
            trimmed.startsWith("> ") -> Triple(PageBlockType.Quote, trimmed.removePrefix("> "), false)
            else -> Triple(PageBlockType.Text, this, false)
        }
        val inline = parseInlineMarkdown(content)
        return RichTextPasteBlock(type, inline.text, inline.spans, isChecked)
    }

    private data class InlineMarkdownResult(
        val text: String,
        val spans: List<PageTextSpan>,
    )

    private fun parseInlineMarkdown(input: String): InlineMarkdownResult {
        val output = StringBuilder()
        val spans = mutableListOf<PageTextSpan>()
        var index = 0
        while (index < input.length) {
            when {
                input.startsWith("**", index) -> {
                    val end = input.indexOf("**", startIndex = index + 2)
                    if (end > index + 2) {
                        val startOut = output.length
                        val text = input.substring(index + 2, end)
                        output.append(text)
                        spans += PageTextSpan(start = startOut, end = output.length, bold = true)
                        index = end + 2
                    } else {
                        output.append(input[index])
                        index++
                    }
                }
                input[index] == '[' -> {
                    val labelEnd = input.indexOf("](", startIndex = index + 1)
                    val urlEnd = if (labelEnd > index) input.indexOf(')', startIndex = labelEnd + 2) else -1
                    if (labelEnd > index && urlEnd > labelEnd + 2) {
                        val startOut = output.length
                        val label = input.substring(index + 1, labelEnd)
                        val url = input.substring(labelEnd + 2, urlEnd)
                        output.append(label)
                        spans += PageTextSpan(start = startOut, end = output.length, linkUrl = url)
                        index = urlEnd + 1
                    } else {
                        output.append(input[index])
                        index++
                    }
                }
                else -> {
                    output.append(input[index])
                    index++
                }
            }
        }
        val text = output.toString()
        return InlineMarkdownResult(
            text = text,
            spans = RichTextSpanEngine.normalize(spans, text),
            )
    }

    private fun insertedTextOrNull(
        oldText: String,
        newText: String,
    ): String? {
        if (oldText == newText) return null
        var prefixLength = 0
        val shortestLength = minOf(oldText.length, newText.length)
        while (
            prefixLength < shortestLength &&
            oldText[prefixLength] == newText[prefixLength]
        ) {
            prefixLength++
        }

        var suffixLength = 0
        while (
            suffixLength < oldText.length - prefixLength &&
            suffixLength < newText.length - prefixLength &&
            oldText[oldText.lastIndex - suffixLength] == newText[newText.lastIndex - suffixLength]
        ) {
            suffixLength++
        }
        return newText.substring(prefixLength, newText.length - suffixLength)
    }

    private fun changedRange(
        oldText: String,
        newText: String,
    ): TextRange {
        var prefixLength = 0
        val shortestLength = minOf(oldText.length, newText.length)
        while (
            prefixLength < shortestLength &&
            oldText[prefixLength] == newText[prefixLength]
        ) {
            prefixLength++
        }

        var suffixLength = 0
        while (
            suffixLength < oldText.length - prefixLength &&
            suffixLength < newText.length - prefixLength &&
            oldText[oldText.lastIndex - suffixLength] == newText[newText.lastIndex - suffixLength]
        ) {
            suffixLength++
        }
        return TextRange(prefixLength, oldText.length - suffixLength)
    }

    private fun List<PageTextSpan>.clipBefore(end: Int): List<PageTextSpan> {
        return mapNotNull { span ->
            val clippedEnd = minOf(span.end, end)
            if (span.start >= clippedEnd) {
                null
            } else {
                span.copy(end = clippedEnd)
            }
        }
    }

    private fun List<PageTextSpan>.clipAfter(
        rangeEnd: Int,
        newOffset: Int,
    ): List<PageTextSpan> {
        return mapNotNull { span ->
            val clippedStart = maxOf(span.start, rangeEnd)
            if (clippedStart >= span.end) {
                null
            } else {
                span.copy(
                    start = newOffset + (clippedStart - rangeEnd),
                    end = newOffset + (span.end - rangeEnd),
                )
            }
        }
    }

    private fun List<PageTextSpan>.shiftBy(offset: Int): List<PageTextSpan> {
        if (offset == 0) return this
        return map { span ->
            span.copy(
                start = span.start + offset,
                end = span.end + offset,
            )
        }
    }
}

object RichTextBlockInteractionParser {
    fun splitEnterChange(
        currentType: PageBlockType,
        currentIsChecked: Boolean,
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
        oldSpans: List<PageTextSpan>,
    ): List<RichTextPasteBlock> {
        val inserted = insertedTextOrNull(oldValue.text, newValue.text) ?: return emptyList()
        if (inserted != "\n") return emptyList()
        val range = changedRange(oldValue.text, newValue.text).coerceInText(oldValue.text)
        val beforeText = oldValue.text.substring(0, range.min)
        val afterText = oldValue.text.substring(range.max)
        val nextType = currentType.continuedBlockTypeAfterEnter()
        return listOf(
            RichTextPasteBlock(
                type = currentType,
                text = beforeText,
                spans = oldSpans.clipBefore(range.min),
                isChecked = currentIsChecked,
            ),
            RichTextPasteBlock(
                type = nextType,
                text = afterText,
                spans = oldSpans.clipAfter(rangeEnd = range.max, newOffset = 0),
                isChecked = false,
            ),
        )
    }
}

internal fun TextFieldValue.effectiveFormatRange(): TextRange {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    if (start != end) return TextRange(start, end)
    if (text.isBlank()) return TextRange(start)

    var wordStart = start
    while (wordStart > 0 && !text[wordStart - 1].isWhitespace()) {
        wordStart--
    }
    var wordEnd = start
    while (wordEnd < text.length && !text[wordEnd].isWhitespace()) {
        wordEnd++
    }
    return if (wordStart < wordEnd) TextRange(wordStart, wordEnd) else TextRange(start)
}

internal fun TextRange.coerceInText(text: String): TextRange =
    TextRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length))

private fun PageBlockType.continuedBlockTypeAfterEnter(): PageBlockType {
    return when (this) {
        PageBlockType.Bullet,
        PageBlockType.Numbered,
        PageBlockType.Todo,
        PageBlockType.Quote,
        -> this
        PageBlockType.Text,
        PageBlockType.Heading,
        PageBlockType.Divider,
        PageBlockType.MediaFile,
        PageBlockType.DatabaseTable,
        -> PageBlockType.Text
    }
}

private fun String.isNumberedListLine(): Boolean {
    val dotIndex = indexOf(". ")
    if (dotIndex <= 0) return false
    return substring(0, dotIndex).all { char -> char.isDigit() }
}

private fun String.substringAfterNumberedMarker(): String {
    return substringAfter(". ").trimStart()
}

private fun String.toLightMarkdownFromHtml(): String {
    if (!contains('<') || !contains('>')) return this
    return replace(Regex("(?is)<h[1-6][^>]*>"), "# ")
        .replace(Regex("(?is)</h[1-6]>"), "\n")
        .replace(Regex("(?is)<li[^>]*>"), "- ")
        .replace(Regex("(?is)</li>"), "\n")
        .replace(Regex("(?is)<br\\s*/?>"), "\n")
        .replace(Regex("(?is)</p>"), "\n")
        .replace(Regex("(?is)<p[^>]*>"), "")
        .replace(Regex("(?is)<strong[^>]*>|<b[^>]*>"), "**")
        .replace(Regex("(?is)</strong>|</b>"), "**")
        .replace(Regex("(?is)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .trim()
}

private fun insertedRangeOrNull(
    oldText: String,
    newText: String,
): TextRange? {
    val inserted = insertedTextOrNull(oldText, newText) ?: return null
    val range = changedRange(oldText, newText)
    return TextRange(range.min, range.min + inserted.length)
}

private fun insertedTextOrNull(
    oldText: String,
    newText: String,
): String? {
    if (oldText == newText) return null
    val range = changedRange(oldText, newText)
    val insertedEnd = newText.length - (oldText.length - range.max)
    if (insertedEnd < range.min) return null
    return newText.substring(range.min, insertedEnd)
}

private fun changedRange(
    oldText: String,
    newText: String,
): TextRange {
    var prefixLength = 0
    val shortestLength = minOf(oldText.length, newText.length)
    while (
        prefixLength < shortestLength &&
        oldText[prefixLength] == newText[prefixLength]
    ) {
        prefixLength++
    }

    var suffixLength = 0
    while (
        suffixLength < oldText.length - prefixLength &&
        suffixLength < newText.length - prefixLength &&
        oldText[oldText.lastIndex - suffixLength] == newText[newText.lastIndex - suffixLength]
    ) {
        suffixLength++
    }
    return TextRange(prefixLength, oldText.length - suffixLength)
}

private fun List<PageTextSpan>.clipBefore(end: Int): List<PageTextSpan> {
    return mapNotNull { span ->
        val clippedEnd = minOf(span.end, end)
        if (span.start >= clippedEnd) {
            null
        } else {
            span.copy(end = clippedEnd)
        }
    }
}

private fun List<PageTextSpan>.clipAfter(
    rangeEnd: Int,
    newOffset: Int,
): List<PageTextSpan> {
    return mapNotNull { span ->
        val clippedStart = maxOf(span.start, rangeEnd)
        if (clippedStart >= span.end) {
            null
        } else {
            span.copy(
                start = newOffset + (clippedStart - rangeEnd),
                end = newOffset + (span.end - rangeEnd),
            )
        }
    }
}
