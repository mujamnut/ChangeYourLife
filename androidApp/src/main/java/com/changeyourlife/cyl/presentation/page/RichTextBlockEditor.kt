package com.changeyourlife.cyl.presentation.page

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.RichTextFormat
import com.changeyourlife.cyl.domain.model.RichTextSpanEngine
import com.changeyourlife.cyl.domain.model.hasAnyStyle

@Composable
fun CylRichTextBlockEditor(
    blockId: String,
    block: PageBlock,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    modifier: Modifier = Modifier,
    onFocusBlock: () -> Unit = {},
    onBlockTypeCommand: (String, PageBlockType) -> Unit = { _, _ -> },
    minLines: Int = 1,
    singleLine: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: String,
) {
    var fieldValue by remember(block.id) {
        mutableStateOf(block.toTextFieldValue())
    }
    var isFocused by remember(block.id) {
        mutableStateOf(false)
    }
    val slashQuery = remember(fieldValue.text, fieldValue.selection) {
        if (fieldValue.selection.start == fieldValue.selection.end) {
            RichTextSlashCommandParser.activeQuery(
                text = fieldValue.text,
                cursor = fieldValue.selection.end,
            )
        } else {
            null
        }
    }
    val slashCommands = remember(slashQuery) {
        slashQuery?.let { query ->
            RichTextSlashCommandParser.matchingCommands(query.query)
        }.orEmpty()
    }

    LaunchedEffect(block.text, block.richTextSpans) {
        val normalized = RichTextSpanEngine.normalize(block.richTextSpans, block.text)
        if (fieldValue.text != block.text || fieldValue.toPageTextSpans() != normalized) {
            fieldValue = TextFieldValue(
                annotatedString = buildRichTextAnnotatedString(block.text, normalized),
                selection = fieldValue.selection.coerceInText(block.text),
            )
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { incoming ->
                val previousSpans = fieldValue.toPageTextSpans()
                val incomingSpans = incoming.toPageTextSpans()
                val nextSpans = if (
                    incoming.text == fieldValue.text ||
                    incomingSpans.isNotEmpty() ||
                    previousSpans.isEmpty()
                ) {
                    incomingSpans
                } else {
                    RichTextSpanEngine.adjustForTextChange(
                        spans = previousSpans,
                        oldText = fieldValue.text,
                        newText = incoming.text,
                    )
                }.let { spans -> RichTextSpanEngine.normalize(spans, incoming.text) }
                val nextValue = TextFieldValue(
                    annotatedString = buildRichTextAnnotatedString(incoming.text, nextSpans),
                    selection = incoming.selection.coerceInText(incoming.text),
                    composition = incoming.composition,
                )
                fieldValue = nextValue
                onRichTextChange(blockId, nextValue.text, nextSpans)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    isFocused = focusState.hasFocus
                    if (focusState.hasFocus) onFocusBlock()
                },
            minLines = minLines,
            singleLine = singleLine,
            textStyle = textStyle,
            placeholder = {
                Text(text = placeholder)
            },
            colors = plainRichTextFieldColors(),
        )

        if (isFocused) {
            if (slashQuery != null && slashCommands.isNotEmpty()) {
                SlashCommandBar(
                    commands = slashCommands,
                    onSelect = { command ->
                        val oldText = fieldValue.text
                        val nextText = oldText
                            .removeRange(slashQuery.start, slashQuery.end)
                            .trimStart()
                        val nextSpans = RichTextSpanEngine.adjustForTextChange(
                            spans = fieldValue.toPageTextSpans(),
                            oldText = oldText,
                            newText = nextText,
                        )
                        val nextValue = TextFieldValue(
                            annotatedString = buildRichTextAnnotatedString(nextText, nextSpans),
                            selection = TextRange(nextText.length),
                        )
                        fieldValue = nextValue
                        onRichTextChange(blockId, nextText, nextSpans)
                        onBlockTypeCommand(blockId, command.type)
                    },
                )
            }
            RichTextToolbar(
                value = fieldValue,
                spans = fieldValue.toPageTextSpans(),
                onToggle = { format ->
                    val range = fieldValue.effectiveFormatRange()
                    if (range.min == range.max) return@RichTextToolbar
                    val nextSpans = RichTextSpanEngine.toggleFormat(
                        spans = fieldValue.toPageTextSpans(),
                        format = format,
                        start = range.min,
                        end = range.max,
                        textLength = fieldValue.text.length,
                    )
                    val nextValue = TextFieldValue(
                        annotatedString = buildRichTextAnnotatedString(fieldValue.text, nextSpans),
                        selection = fieldValue.selection.coerceInText(fieldValue.text),
                        composition = fieldValue.composition,
                    )
                    fieldValue = nextValue
                    onRichTextChange(blockId, nextValue.text, nextSpans)
                },
            )
        }
    }
}

@Composable
private fun SlashCommandBar(
    commands: List<RichTextSlashCommand>,
    onSelect: (RichTextSlashCommand) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        commands.forEach { command ->
            FilterChip(
                selected = false,
                onClick = { onSelect(command) },
                label = {
                    Text(
                        text = "${command.label} · ${command.hint}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun RichTextToolbar(
    value: TextFieldValue,
    spans: List<PageTextSpan>,
    onToggle: (RichTextFormat) -> Unit,
) {
    val range = value.effectiveFormatRange()
    val hasRange = range.min != range.max
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RichTextFormat.entries.forEach { format ->
            RichTextFormatButton(
                label = format.label,
                selected = hasRange &&
                    RichTextSpanEngine.hasFormat(spans, format, range.min, range.max),
                enabled = hasRange,
                onClick = { onToggle(format) },
            )
        }
    }
}

@Composable
private fun RichTextFormatButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (label == "B") FontWeight.Bold else FontWeight.SemiBold,
                fontStyle = if (label == "I") FontStyle.Italic else null,
                textDecoration = when (label) {
                    "U" -> TextDecoration.Underline
                    "S" -> TextDecoration.LineThrough
                    else -> null
                },
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}

private val RichTextFormat.label: String
    get() = when (this) {
        RichTextFormat.Bold -> "B"
        RichTextFormat.Italic -> "I"
        RichTextFormat.Underline -> "U"
        RichTextFormat.Strikethrough -> "S"
    }

private fun PageBlock.toTextFieldValue(): TextFieldValue {
    val spans = RichTextSpanEngine.normalize(richTextSpans, text)
    return TextFieldValue(
        annotatedString = buildRichTextAnnotatedString(text, spans),
        selection = TextRange(text.length),
    )
}

private fun buildRichTextAnnotatedString(
    text: String,
    spans: List<PageTextSpan>,
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    RichTextSpanEngine.normalize(spans, text).forEach { span ->
        builder.addStyle(span.toSpanStyle(), span.start, span.end)
    }
    return builder.toAnnotatedString()
}

private fun PageTextSpan.toSpanStyle(): SpanStyle {
    val textDecorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (textDecorations.isEmpty()) {
            null
        } else {
            TextDecoration.combine(textDecorations)
        },
    )
}

private fun TextFieldValue.toPageTextSpans(): List<PageTextSpan> {
    return annotatedString.spanStyles.mapNotNull { range ->
        val style = range.item
        val span = PageTextSpan(
            start = range.start,
            end = range.end,
            bold = style.fontWeight?.let { weight -> weight.weight >= FontWeight.SemiBold.weight } == true,
            italic = style.fontStyle == FontStyle.Italic,
            underline = style.textDecoration?.contains(TextDecoration.Underline) == true,
            strikethrough = style.textDecoration?.contains(TextDecoration.LineThrough) == true,
        )
        if (span.hasAnyStyle()) span else null
    }.let { spans -> RichTextSpanEngine.normalize(spans, text) }
}

private fun TextFieldValue.effectiveFormatRange(): TextRange {
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

private fun TextRange.coerceInText(text: String): TextRange =
    TextRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length))

@Composable
private fun plainRichTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor = MaterialTheme.colorScheme.surface,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
)
