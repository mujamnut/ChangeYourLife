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
        val nextSpans = if (
            newValue.text == state.value.text ||
            incomingSpans.isNotEmpty() ||
            state.spans.isEmpty()
        ) {
            incomingSpans
        } else {
            RichTextSpanEngine.adjustForTextChange(
                spans = state.spans,
                oldText = state.value.text,
                newText = newValue.text,
            )
        }
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
        if (range.min == range.max) return state
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
        if (range.min == range.max) return state
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
        if (range.min == range.max) return state
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
        val range = value.effectiveFormatRange()
        val active = RichTextFormat.entries.filterTo(mutableSetOf()) { format ->
            range.min != range.max &&
                RichTextSpanEngine.hasFormat(spans, format, range.min, range.max)
        }
        return copy(activeFormats = RichTextFormatSet(active))
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
    fun parse(rawText: String): List<RichTextPasteBlock> {
        return rawText
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
