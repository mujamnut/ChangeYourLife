package com.changeyourlife.cyl.presentation.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.RichTextFormat
import com.changeyourlife.cyl.domain.model.RichTextSpanEngine
import com.changeyourlife.cyl.domain.model.hasAnyStyle

data class RichTextToolbarUiState(
    val value: TextFieldValue,
    val spans: List<PageTextSpan>,
    val onToggle: (RichTextFormat) -> Unit,
    val onApplyLink: () -> Unit,
    val onApplyColor: (String) -> Unit,
    val onApplyHighlight: (String) -> Unit,
)

@Composable
fun RichTextToolbarHost(
    state: RichTextToolbarUiState,
    modifier: Modifier = Modifier,
) {
    RichTextToolbar(
        value = state.value,
        spans = state.spans,
        onToggle = state.onToggle,
        onApplyLink = state.onApplyLink,
        onApplyColor = state.onApplyColor,
        onApplyHighlight = state.onApplyHighlight,
        modifier = modifier,
    )
}

@Composable
fun CylRichTextBlockEditor(
    blockId: String,
    block: PageBlock,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onPasteBlocks: (String, List<RichTextPasteBlock>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onFocusBlock: () -> Unit = {},
    onBlockTypeCommand: (String, PageBlockType) -> Unit = { _, _ -> },
    onToolbarStateChange: (RichTextToolbarUiState?) -> Unit = {},
    showInlineToolbar: Boolean = true,
    enableMultiBlockPaste: Boolean = true,
    mentionPages: List<Page> = emptyList(),
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
    var currentSpans by remember(block.id) {
        mutableStateOf(RichTextSpanEngine.normalize(block.richTextSpans, block.text))
    }
    var isLinkEditorVisible by remember(block.id) {
        mutableStateOf(false)
    }
    var linkUrl by remember(block.id) {
        mutableStateOf("")
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
    val mentionQuery = remember(fieldValue.text, fieldValue.selection) {
        if (fieldValue.selection.start == fieldValue.selection.end) {
            RichTextMentionParser.activeQuery(
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
    val mentionSuggestions = remember(mentionPages, mentionQuery) {
        val query = mentionQuery?.query.orEmpty().trim()
        if (mentionQuery == null) {
            emptyList()
        } else {
            mentionPages
                .filter { page ->
                    val title = page.title.ifBlank { "Untitled page" }
                    query.isBlank() || title.contains(query, ignoreCase = true)
                }
                .take(8)
        }
    }

    fun commitSpans(nextText: String, nextSpans: List<PageTextSpan>, selection: TextRange = fieldValue.selection) {
        val normalized = RichTextSpanEngine.normalize(nextSpans, nextText)
        currentSpans = normalized
        fieldValue = TextFieldValue(
            annotatedString = buildRichTextAnnotatedString(nextText, normalized),
            selection = selection.coerceInText(nextText),
        )
        onRichTextChange(blockId, nextText, normalized)
    }

    fun toggleFormat(format: RichTextFormat) {
        val range = fieldValue.effectiveFormatRange()
        if (range.min == range.max) return
        val nextSpans = RichTextSpanEngine.toggleFormat(
            spans = currentSpans,
            format = format,
            start = range.min,
            end = range.max,
            textLength = fieldValue.text.length,
        )
        commitSpans(fieldValue.text, nextSpans)
    }

    fun showLinkEditor() {
        linkUrl = currentSpans
            .firstOrNull { span ->
                val range = fieldValue.effectiveFormatRange()
                span.start <= range.min && span.end >= range.max && span.linkUrl.isNotBlank()
            }
            ?.linkUrl
            .orEmpty()
        isLinkEditorVisible = true
    }

    fun applyColor(color: String) {
        val range = fieldValue.effectiveFormatRange()
        if (range.min == range.max) return
        commitSpans(
            fieldValue.text,
            RichTextSpanEngine.applyColor(
                spans = currentSpans,
                start = range.min,
                end = range.max,
                textLength = fieldValue.text.length,
                color = color,
            ),
        )
    }

    fun applyHighlight(highlight: String) {
        val range = fieldValue.effectiveFormatRange()
        if (range.min == range.max) return
        commitSpans(
            fieldValue.text,
            RichTextSpanEngine.applyHighlight(
                spans = currentSpans,
                start = range.min,
                end = range.max,
                textLength = fieldValue.text.length,
                highlight = highlight,
            ),
        )
    }

    LaunchedEffect(block.text, block.richTextSpans) {
        val normalized = RichTextSpanEngine.normalize(block.richTextSpans, block.text)
        if (fieldValue.text != block.text || currentSpans != normalized) {
            currentSpans = normalized
            fieldValue = TextFieldValue(
                annotatedString = buildRichTextAnnotatedString(block.text, normalized),
                selection = fieldValue.selection.coerceInText(block.text),
            )
        }
    }

    val toolbarState = RichTextToolbarUiState(
        value = fieldValue,
        spans = currentSpans,
        onToggle = ::toggleFormat,
        onApplyLink = ::showLinkEditor,
        onApplyColor = ::applyColor,
        onApplyHighlight = ::applyHighlight,
    )

    LaunchedEffect(isFocused, fieldValue, currentSpans) {
        onToolbarStateChange(if (isFocused) toolbarState else null)
    }

    DisposableEffect(block.id) {
        onDispose { onToolbarStateChange(null) }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { incoming ->
                val pasteBlocks = if (enableMultiBlockPaste) {
                    RichTextPasteParser.mergeTextChangeIntoBlocks(
                        currentType = block.type,
                        currentIsChecked = block.isChecked,
                        oldValue = fieldValue,
                        newValue = incoming,
                        oldSpans = currentSpans,
                    )
                } else {
                    emptyList()
                }
                if (pasteBlocks.size > 1) {
                    onPasteBlocks(blockId, pasteBlocks)
                    return@OutlinedTextField
                }
                val autoType = incoming.text.toAutoBlockTypeOrNull()
                if (fieldValue.text.isBlank() && autoType != null) {
                    commitSpans("", emptyList(), TextRange(0))
                    onBlockTypeCommand(blockId, autoType)
                    return@OutlinedTextField
                }
                val nextSpans = if (incoming.text == fieldValue.text) {
                    currentSpans
                } else {
                    RichTextSpanEngine.adjustForTextChange(
                        spans = currentSpans,
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
                currentSpans = nextSpans
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
            if (mentionQuery != null && mentionSuggestions.isNotEmpty()) {
                MentionSuggestionBar(
                    pages = mentionSuggestions,
                    onSelect = { page ->
                        val controller = RichTextController(
                            RichTextEditorState(
                                blockId = blockId,
                                value = fieldValue,
                                spans = currentSpans,
                                isFocused = true,
                            ),
                        )
                        val nextState = controller.replaceRangeWithMention(
                            range = TextRange(mentionQuery.start, mentionQuery.end),
                            pageId = page.id,
                            title = page.title,
                        )
                        commitSpans(
                            nextText = nextState.value.text,
                            nextSpans = nextState.spans,
                            selection = nextState.value.selection,
                        )
                    },
                )
            } else if (slashQuery != null && slashCommands.isNotEmpty()) {
                SlashCommandBar(
                    commands = slashCommands,
                    onSelect = { command ->
                        val oldText = fieldValue.text
                        val nextText = oldText
                            .removeRange(slashQuery.start, slashQuery.end)
                            .trimStart()
                        val nextSpans = RichTextSpanEngine.adjustForTextChange(
                            spans = currentSpans,
                            oldText = oldText,
                            newText = nextText,
                        )
                        commitSpans(nextText, nextSpans, TextRange(nextText.length))
                        onBlockTypeCommand(blockId, command.type)
                    },
                )
            }
            if (isLinkEditorVisible) {
                RichTextLinkEditor(
                    url = linkUrl,
                    onUrlChange = { linkUrl = it },
                    onApply = {
                        val range = fieldValue.effectiveFormatRange()
                        if (range.min != range.max) {
                            val nextSpans = RichTextSpanEngine.applyLink(
                                spans = currentSpans,
                                start = range.min,
                                end = range.max,
                                textLength = fieldValue.text.length,
                                url = linkUrl,
                            )
                            commitSpans(fieldValue.text, nextSpans)
                        }
                        isLinkEditorVisible = false
                    },
                    onDismiss = { isLinkEditorVisible = false },
                )
            }
            if (showInlineToolbar) {
                RichTextToolbarHost(state = toolbarState)
            }
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
private fun MentionSuggestionBar(
    pages: List<Page>,
    onSelect: (Page) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEach { page ->
            FilterChip(
                selected = false,
                onClick = { onSelect(page) },
                label = {
                    Text(
                        text = "@${page.title.ifBlank { "Untitled page" }}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun RichTextLinkEditor(
    url: String,
    onUrlChange: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = {
                Text("https://")
            },
            colors = plainRichTextFieldColors(),
        )
        Text(
            text = "Apply",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onApply)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Cancel",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RichTextToolbar(
    value: TextFieldValue,
    spans: List<PageTextSpan>,
    onToggle: (RichTextFormat) -> Unit,
    onApplyLink: () -> Unit,
    onApplyColor: (String) -> Unit,
    onApplyHighlight: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = value.effectiveFormatRange()
    val hasRange = range.min != range.max
    Row(
        modifier = modifier
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
        RichTextActionButton(
            label = "Link",
            enabled = hasRange,
            onClick = onApplyLink,
        )
        RichTextSwatchButton(
            color = Color(0xFF1565C0),
            selected = hasRange && spans.any { span ->
                span.start <= range.min && span.end >= range.max && span.color == "#1565C0"
            },
            enabled = hasRange,
            onClick = { onApplyColor("#1565C0") },
        )
        RichTextSwatchButton(
            color = Color(0xFFFFF59D),
            selected = hasRange && spans.any { span ->
                span.start <= range.min && span.end >= range.max && span.highlight == "#FFF59D"
            },
            enabled = hasRange,
            onClick = { onApplyHighlight("#FFF59D") },
        )
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

@Composable
private fun RichTextActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        maxLines = 1,
    )
}

@Composable
private fun RichTextSwatchButton(
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = if (enabled) 1f else 0.38f)),
        )
    }
}

private val RichTextFormat.label: String
    get() = when (this) {
        RichTextFormat.Bold -> "B"
        RichTextFormat.Italic -> "I"
        RichTextFormat.Underline -> "U"
        RichTextFormat.Strikethrough -> "S"
        RichTextFormat.Code -> "<>"
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
        fontFamily = if (code) FontFamily.Monospace else null,
        color = color.toComposeColorOrUnspecified().takeIf { it != Color.Unspecified }
            ?: if (linkUrl.isNotBlank() || mentionPageId.isNotBlank()) Color(0xFF1565C0) else Color.Unspecified,
        background = highlight.toComposeColorOrUnspecified(),
        textDecoration = if (textDecorations.isEmpty()) {
            null
        } else {
            TextDecoration.combine(textDecorations)
        },
    )
}

private fun String.toAutoBlockTypeOrNull(): PageBlockType? {
    return when (this) {
        "- " -> PageBlockType.Bullet
        "[] ", "[ ] " -> PageBlockType.Todo
        "# " -> PageBlockType.Heading
        "> " -> PageBlockType.Quote
        else -> null
    }
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
            code = style.fontFamily == FontFamily.Monospace,
        )
        if (span.hasAnyStyle()) span else null
    }.let { spans -> RichTextSpanEngine.normalize(spans, text) }
}

private fun String.toComposeColorOrUnspecified(): Color {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6) return Color.Unspecified
    val value = normalized.toLongOrNull(radix = 16) ?: return Color.Unspecified
    return Color(0xFF000000 or value)
}

@Composable
private fun plainRichTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor = MaterialTheme.colorScheme.surface,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
)
