package com.changeyourlife.cyl.presentation.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.RichTextFormat
import com.changeyourlife.cyl.domain.model.RichTextSpanEngine
import com.changeyourlife.cyl.domain.model.hasAnyStyle

data class RichTextToolbarUiState(
    val value: TextFieldValue,
    val spans: List<PageTextSpan>,
    val activeFormats: RichTextFormatSet,
    val typingLinkUrl: String = "",
    val typingColor: String = "",
    val typingHighlight: String = "",
    val onToggle: (RichTextFormat) -> Unit,
    val onApplyLink: () -> Unit,
    val onApplyColor: (String) -> Unit,
    val onApplyHighlight: (String) -> Unit,
    val onInsertMentionTrigger: () -> Unit = {},
)

private data class PendingRichTextDraft(
    val text: String,
    val spans: List<PageTextSpan>,
)

@Composable
fun RichTextToolbarHost(
    state: RichTextToolbarUiState,
    modifier: Modifier = Modifier,
) {
    RichTextToolbar(
        value = state.value,
        spans = state.spans,
        activeFormats = state.activeFormats,
        typingLinkUrl = state.typingLinkUrl,
        typingColor = state.typingColor,
        typingHighlight = state.typingHighlight,
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
    onInsertBlockCommand: (String, PageBlockType, PageBlockInsertPosition) -> Unit = { _, _, _ -> },
    onDeleteEmptyBlockCommand: (String) -> Unit = {},
    onIndentBlockCommand: (String) -> Unit = {},
    onOutdentBlockCommand: (String) -> Unit = {},
    onCreateLinkedPageCommand: (String) -> Unit = {},
    onOpenPropertySheetCommand: (String) -> Unit = {},
    commandContext: EditorCommandContext = EditorCommandContext(),
    focusRequestToken: Long = 0L,
    onToolbarStateChange: (RichTextToolbarUiState?) -> Unit = {},
    showInlineToolbar: Boolean = true,
    enableMultiBlockPaste: Boolean = true,
    mentionPages: List<Page> = emptyList(),
    minLines: Int = 1,
    singleLine: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: String,
    placeholderContext: EditorPlaceholderContext? = null,
) {
    var fieldValue by remember(block.id) {
        mutableStateOf(block.toTextFieldValue())
    }
    var isFocused by remember(block.id) {
        mutableStateOf(false)
    }
    val focusRequester = remember(block.id) {
        FocusRequester()
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    var currentSpans by remember(block.id) {
        mutableStateOf(RichTextSpanEngine.normalize(block.richTextSpans, block.text))
    }
    var pendingDraft by remember(block.id) {
        mutableStateOf<PendingRichTextDraft?>(null)
    }
    var typingFormats by remember(block.id) {
        mutableStateOf(RichTextFormatSet())
    }
    var typingLinkUrl by remember(block.id) {
        mutableStateOf("")
    }
    var typingColor by remember(block.id) {
        mutableStateOf("")
    }
    var typingHighlight by remember(block.id) {
        mutableStateOf("")
    }
    var isLinkEditorVisible by remember(block.id) {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    var linkUrl by remember(block.id) {
        mutableStateOf("")
    }
    val effectivePlaceholder = placeholderContext
        ?.copy(isFocused = isFocused)
        ?.let(EditorPlaceholderPolicy::placeholderFor)
        ?: placeholder
    var suggestionSelectionIndex by remember(block.id) {
        mutableStateOf(0)
    }
    var dismissedSuggestionQuery by remember(block.id) {
        mutableStateOf<EditorSuggestionQuery?>(null)
    }
    val suggestionState = remember(
        fieldValue.text,
        fieldValue.selection,
        mentionPages,
        commandContext,
        suggestionSelectionIndex,
        dismissedSuggestionQuery,
    ) {
        if (fieldValue.selection.start == fieldValue.selection.end) {
            EditorSuggestionController.resolve(
                text = fieldValue.text,
                cursor = fieldValue.selection.end,
                mentionPages = mentionPages,
                context = commandContext,
                selectedIndex = suggestionSelectionIndex,
            )?.takeUnless { state -> state.query == dismissedSuggestionQuery }
        } else {
            null
        }
    }

    fun commitState(nextState: RichTextEditorState, notifyChange: Boolean = true) {
        val normalized = RichTextSpanEngine.normalize(nextState.spans, nextState.value.text)
        currentSpans = normalized
        typingFormats = nextState.typingFormats
        typingLinkUrl = nextState.typingLinkUrl
        typingColor = nextState.typingColor
        typingHighlight = nextState.typingHighlight
        fieldValue = TextFieldValue(
            annotatedString = buildRichTextAnnotatedString(nextState.value.text, normalized),
            selection = nextState.value.selection.coerceInText(nextState.value.text),
            composition = nextState.value.composition,
        )
        if (notifyChange) {
            pendingDraft = PendingRichTextDraft(
                text = nextState.value.text,
                spans = normalized,
            )
            onRichTextChange(blockId, nextState.value.text, normalized)
        }
    }

    fun commitSpans(nextText: String, nextSpans: List<PageTextSpan>, selection: TextRange = fieldValue.selection) {
        val normalized = RichTextSpanEngine.normalize(nextSpans, nextText)
        currentSpans = normalized
        typingFormats = RichTextFormatSet()
        typingLinkUrl = ""
        typingColor = ""
        typingHighlight = ""
        fieldValue = TextFieldValue(
            annotatedString = buildRichTextAnnotatedString(nextText, normalized),
            selection = selection.coerceInText(nextText),
        )
        pendingDraft = PendingRichTextDraft(
            text = nextText,
            spans = normalized,
        )
        onRichTextChange(blockId, nextText, normalized)
    }

    fun selectSuggestion(
        state: EditorSuggestionState,
        item: RichTextCommandPaletteItem,
    ) {
        when (item.kind) {
            RichTextCommandPaletteKind.Mention -> {
                val page = mentionPages.firstOrNull { candidate ->
                    candidate.paletteItemId() == item.id
                } ?: return
                val controller = RichTextController(
                    RichTextEditorState(
                        blockId = blockId,
                        value = fieldValue,
                        spans = currentSpans,
                        isFocused = true,
                    ),
                )
                val nextState = controller.replaceRangeWithMention(
                    range = TextRange(state.query.start, state.query.end),
                    pageId = page.id,
                    title = page.title,
                )
                commitSpans(
                    nextText = nextState.value.text,
                    nextSpans = nextState.spans,
                    selection = nextState.value.selection,
                )
            }
            RichTextCommandPaletteKind.Slash -> {
                val command = EditorCommandRegistry
                    .entryForPaletteItemId(item.id)
                    ?.command
                    ?: return
                val oldText = fieldValue.text
                val nextText = oldText
                    .removeRange(state.query.start, state.query.end)
                    .trimStart()
                val nextSpans = RichTextSpanEngine.adjustForTextChange(
                    spans = currentSpans,
                    oldText = oldText,
                    newText = nextText,
                )
                commitSpans(nextText, nextSpans, TextRange(nextText.length))
                when (val action = command.action) {
                    is RichTextSlashAction.ChangeType -> {
                        onBlockTypeCommand(blockId, action.type)
                    }
                    is RichTextSlashAction.InsertBlock -> {
                        onInsertBlockCommand(blockId, action.type, action.position)
                    }
                    RichTextSlashAction.CreateLinkedPage -> {
                        onCreateLinkedPageCommand(blockId)
                    }
                    RichTextSlashAction.OpenPropertySheet -> {
                        onOpenPropertySheetCommand(blockId)
                    }
                    RichTextSlashAction.IndentBlock -> {
                        onIndentBlockCommand(blockId)
                    }
                    RichTextSlashAction.OutdentBlock -> {
                        onOutdentBlockCommand(blockId)
                    }
                }
            }
        }
        suggestionSelectionIndex = 0
        dismissedSuggestionQuery = null
    }

    fun toggleFormat(format: RichTextFormat) {
        val nextState = RichTextController(
            RichTextEditorState(
                blockId = blockId,
                value = fieldValue,
                spans = currentSpans,
                typingFormats = typingFormats,
                typingLinkUrl = typingLinkUrl,
                typingColor = typingColor,
                typingHighlight = typingHighlight,
                isFocused = true,
            ),
        ).toggleFormat(format)
        val changedSpans = nextState.spans != currentSpans || nextState.value.text != fieldValue.text
        commitState(
            nextState = nextState,
            notifyChange = changedSpans,
        )
    }

    fun showLinkEditor() {
        linkUrl = RichTextLinkPolicy.selectedLinkUrl(
            spans = currentSpans,
            range = fieldValue.effectiveFormatRange(),
        )
        isLinkEditorVisible = true
    }

    fun insertMentionTrigger() {
        val selection = fieldValue.selection
        val oldText = fieldValue.text
        val nextText = oldText.replaceRange(selection.min, selection.max, "@")
        val nextSpans = RichTextSpanEngine.adjustForTextChange(
            spans = currentSpans,
            oldText = oldText,
            newText = nextText,
        )
        dismissedSuggestionQuery = null
        commitSpans(nextText, nextSpans, TextRange(selection.min + 1))
        focusRequester.requestFocus()
        keyboardController?.show()
        onFocusBlock()
    }

    fun applyLinkValue(url: String) {
        val nextState = RichTextController(
            RichTextEditorState(
                blockId = blockId,
                value = fieldValue,
                spans = currentSpans,
                typingFormats = typingFormats,
                typingLinkUrl = typingLinkUrl,
                typingColor = typingColor,
                typingHighlight = typingHighlight,
                isFocused = true,
            ),
        ).applyLink(url)
        val changedSpans = nextState.spans != currentSpans ||
            nextState.value.text != fieldValue.text
        commitState(nextState, notifyChange = changedSpans)
    }

    fun copyCurrentLink() {
        val trimmed = linkUrl.trim()
        if (trimmed.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Link", trimmed))
        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
    }

    fun openCurrentLink() {
        val target = RichTextLinkPolicy.normalizedOpenUrl(linkUrl)
        if (target.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        }.onFailure {
            Toast.makeText(context, "Can't open link", Toast.LENGTH_SHORT).show()
        }
    }

    fun applyColor(color: String) {
        val nextState = RichTextController(
            RichTextEditorState(
                blockId = blockId,
                value = fieldValue,
                spans = currentSpans,
                typingFormats = typingFormats,
                typingLinkUrl = typingLinkUrl,
                typingColor = typingColor,
                typingHighlight = typingHighlight,
                isFocused = true,
            ),
        ).applyColor(color)
        val changedSpans = nextState.spans != currentSpans || nextState.value.text != fieldValue.text
        commitState(nextState, notifyChange = changedSpans)
    }

    fun applyHighlight(highlight: String) {
        val nextState = RichTextController(
            RichTextEditorState(
                blockId = blockId,
                value = fieldValue,
                spans = currentSpans,
                typingFormats = typingFormats,
                typingLinkUrl = typingLinkUrl,
                typingColor = typingColor,
                typingHighlight = typingHighlight,
                isFocused = true,
            ),
        ).applyHighlight(highlight)
        val changedSpans = nextState.spans != currentSpans || nextState.value.text != fieldValue.text
        commitState(nextState, notifyChange = changedSpans)
    }

    LaunchedEffect(block.text, block.richTextSpans, isFocused) {
        val normalized = RichTextSpanEngine.normalize(block.richTextSpans, block.text)
        val incomingDraft = PendingRichTextDraft(
            text = block.text,
            spans = normalized,
        )
        val localPendingDraft = pendingDraft
        when {
            localPendingDraft == incomingDraft -> {
                if (!isFocused) pendingDraft = null
            }
            localPendingDraft != null -> Unit
            fieldValue.text != block.text || currentSpans != normalized -> {
                currentSpans = normalized
                fieldValue = TextFieldValue(
                    annotatedString = buildRichTextAnnotatedString(block.text, normalized),
                    selection = fieldValue.selection.coerceInText(block.text),
                )
            }
        }
    }

    LaunchedEffect(focusRequestToken) {
        if (focusRequestToken <= 0L) return@LaunchedEffect
        fieldValue = fieldValue.copy(selection = TextRange(fieldValue.text.length))
        focusRequester.requestFocus()
        keyboardController?.show()
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
        onFocusBlock()
    }

    val toolbarState = RichTextToolbarUiState(
        value = fieldValue,
        spans = currentSpans,
        activeFormats = RichTextController(
            RichTextEditorState(
                blockId = blockId,
                value = fieldValue,
                spans = currentSpans,
                typingFormats = typingFormats,
                typingLinkUrl = typingLinkUrl,
                typingColor = typingColor,
                typingHighlight = typingHighlight,
                isFocused = isFocused,
            ),
        ).state.activeFormats,
        typingLinkUrl = typingLinkUrl,
        typingColor = typingColor,
        typingHighlight = typingHighlight,
        onToggle = ::toggleFormat,
        onApplyLink = ::showLinkEditor,
        onApplyColor = ::applyColor,
        onApplyHighlight = ::applyHighlight,
        onInsertMentionTrigger = ::insertMentionTrigger,
    )

    LaunchedEffect(isFocused, fieldValue, currentSpans, typingFormats, typingLinkUrl, typingColor, typingHighlight) {
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
                if (incoming.text != fieldValue.text || incoming.selection != fieldValue.selection) {
                    suggestionSelectionIndex = 0
                    dismissedSuggestionQuery = null
                }
                val richClipboardHtml = if (enableMultiBlockPaste) {
                    context.richClipboardHtmlForPaste(
                        oldText = fieldValue.text,
                        newText = incoming.text,
                    )
                } else {
                    null
                }
                if (richClipboardHtml != null) {
                    val clipboardBlocks = RichTextPasteParser.mergeRichClipboardTextChangeIntoBlocks(
                        currentType = block.type,
                        currentIsChecked = block.isChecked,
                        oldValue = fieldValue,
                        newValue = incoming,
                        oldSpans = currentSpans,
                        clipboardHtmlText = richClipboardHtml,
                    )
                    if (clipboardBlocks.isNotEmpty()) {
                        onPasteBlocks(blockId, clipboardBlocks)
                        return@OutlinedTextField
                    }
                }
                val enterBlocks = if (enableMultiBlockPaste) {
                    RichTextBlockInteractionParser.splitEnterChange(
                        currentType = block.type,
                        currentIsChecked = block.isChecked,
                        oldValue = fieldValue,
                        newValue = incoming,
                        oldSpans = currentSpans,
                    )
                } else {
                    emptyList()
                }
                if (enterBlocks.size > 1) {
                    onPasteBlocks(blockId, enterBlocks)
                    return@OutlinedTextField
                }
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
                val nextValue = TextFieldValue(
                    text = incoming.text,
                    selection = incoming.selection.coerceInText(incoming.text),
                    composition = incoming.composition,
                )
                val nextState = RichTextController(
                    RichTextEditorState(
                        blockId = blockId,
                        value = fieldValue,
                        spans = currentSpans,
                        typingFormats = typingFormats,
                        typingLinkUrl = typingLinkUrl,
                        typingColor = typingColor,
                        typingHighlight = typingHighlight,
                        isFocused = isFocused,
                    ),
                ).updateText(nextValue)
                commitState(nextState)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    val activeSuggestionState = suggestionState
                    if (
                        event.type == KeyEventType.KeyDown &&
                        activeSuggestionState != null
                    ) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                suggestionSelectionIndex = EditorSuggestionController
                                    .moveSelection(activeSuggestionState, delta = 1)
                                    .selectedIndex
                                true
                            }
                            Key.DirectionUp -> {
                                suggestionSelectionIndex = EditorSuggestionController
                                    .moveSelection(activeSuggestionState, delta = -1)
                                    .selectedIndex
                                true
                            }
                            Key.Enter -> {
                                activeSuggestionState.selectedItem?.let { item ->
                                    selectSuggestion(activeSuggestionState, item)
                                }
                                true
                            }
                            Key.Escape -> {
                                dismissedSuggestionQuery = activeSuggestionState.query
                                true
                            }
                            else -> false
                        }
                    } else if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.Backspace &&
                        fieldValue.text.isEmpty()
                    ) {
                        onDeleteEmptyBlockCommand(blockId)
                        true
                    } else {
                        false
                    }
                }
                .onFocusChanged { focusState ->
                    isFocused = focusState.hasFocus
                    if (focusState.hasFocus) onFocusBlock()
                },
            minLines = minLines,
            singleLine = singleLine,
            textStyle = textStyle,
            placeholder = {
                Text(text = effectivePlaceholder)
            },
            colors = plainRichTextFieldColors(),
        )

        if (isFocused) {
            if (suggestionState != null) {
                RichTextCommandPalette(
                    items = suggestionState.items,
                    onSelect = { item -> selectSuggestion(suggestionState, item) },
                    selectedItemId = suggestionState.selectedItem?.id,
                )
            }
            if (isLinkEditorVisible) {
                RichTextLinkEditor(
                    url = linkUrl,
                    onUrlChange = { linkUrl = it },
                    onApply = {
                        applyLinkValue(linkUrl)
                        isLinkEditorVisible = false
                    },
                    onRemove = {
                        applyLinkValue("")
                        linkUrl = ""
                        isLinkEditorVisible = false
                    },
                    onCopy = ::copyCurrentLink,
                    onOpen = ::openCurrentLink,
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
private fun RichTextLinkEditor(
    url: String,
    onUrlChange: (String) -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasLink = url.trim().isNotBlank()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("https://")
            },
            colors = plainRichTextFieldColors(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onApply) {
                Text("Apply")
            }
            TextButton(onClick = onOpen, enabled = hasLink) {
                Text("Open")
            }
            TextButton(onClick = onCopy, enabled = hasLink) {
                Text("Copy")
            }
            TextButton(onClick = onRemove, enabled = hasLink) {
                Text("Remove")
            }
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun RichTextToolbar(
    value: TextFieldValue,
    spans: List<PageTextSpan>,
    activeFormats: RichTextFormatSet,
    typingLinkUrl: String,
    typingColor: String,
    typingHighlight: String,
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
        EditorCommandRegistry.richTextToolbarEntries().forEach { entry ->
            when (val action = entry.action) {
                is RichTextToolbarRegistryAction.ToggleFormat -> {
                    RichTextFormatButton(
                        label = entry.label,
                        selected = action.format in activeFormats,
                        enabled = true,
                        onClick = { onToggle(action.format) },
                    )
                }
                RichTextToolbarRegistryAction.Link -> {
                    RichTextActionButton(
                        label = entry.label,
                        enabled = true,
                        onClick = onApplyLink,
                    )
                }
                is RichTextToolbarRegistryAction.ApplyColor -> {
                    RichTextSwatchButton(
                        color = action.colorHex.toToolbarSwatchColor(),
                        selected = if (hasRange) {
                            spans.any { span ->
                                span.start <= range.min && span.end >= range.max && span.color == action.colorHex
                            }
                        } else {
                            typingColor == action.colorHex
                        },
                        enabled = true,
                        onClick = { onApplyColor(action.colorHex) },
                    )
                }
                is RichTextToolbarRegistryAction.ApplyHighlight -> {
                    RichTextSwatchButton(
                        color = action.colorHex.toToolbarSwatchColor(),
                        selected = if (hasRange) {
                            spans.any { span ->
                                span.start <= range.min && span.end >= range.max &&
                                    span.highlight == action.colorHex
                            }
                        } else {
                            typingHighlight == action.colorHex
                        },
                        enabled = true,
                        onClick = { onApplyHighlight(action.colorHex) },
                    )
                }
            }
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

private fun PageBlock.toTextFieldValue(): TextFieldValue {
    val spans = RichTextSpanEngine.normalize(richTextSpans, text)
    return TextFieldValue(
        annotatedString = buildRichTextAnnotatedString(text, spans),
        selection = TextRange(text.length),
    )
}

private fun String.toToolbarSwatchColor(): Color {
    return toOpaqueRgbColorOrNull() ?: Color.Black
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
            ?: if (linkUrl.isNotBlank() || mentionPageId.isNotBlank()) Color(0xFF2F2F2C) else Color.Unspecified,
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
        "1. " -> PageBlockType.Numbered
        "[] ", "[ ] " -> PageBlockType.Todo
        "# " -> PageBlockType.Heading
        ">> " -> PageBlockType.Toggle
        "> " -> PageBlockType.Quote
        "``` " -> PageBlockType.Code
        else -> null
    }
}

private fun Context.richClipboardHtmlForPaste(
    oldText: String,
    newText: String,
): String? {
    val inserted = RichTextPasteParser.insertedTextForChange(oldText, newText)
        ?.takeIf { text -> text.length > 1 }
        ?: return null
    val clipboardPayload = runCatching {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val item = clipboard.primaryClip
            ?.takeIf { clip -> clip.itemCount > 0 }
            ?.getItemAt(0)
            ?: return null
        val html = item.htmlText
            ?.takeIf { value -> value.isNotBlank() }
            ?: return null
        val plainText = item.coerceToText(this)?.toString().orEmpty()
        html to plainText
    }.getOrNull() ?: return null
    val (html, plainText) = clipboardPayload
    val parsedText = runCatching {
        RichTextPasteParser
            .parseClipboard(rawText = plainText, htmlText = html)
            .joinToString("\n") { block -> block.text }
    }.getOrDefault("")

    return if (
        inserted.matchesClipboardPasteText(plainText) ||
        inserted.matchesClipboardPasteText(parsedText)
    ) {
        html
    } else {
        null
    }
}

private fun String.matchesClipboardPasteText(other: String): Boolean {
    val self = normalizeClipboardPasteText()
    val candidate = other.normalizeClipboardPasteText()
    if (self.isBlank() || candidate.isBlank()) return false
    return self == candidate
}

private fun String.normalizeClipboardPasteText(): String {
    return replace("\r\n", "\n")
        .replace('\u00A0', ' ')
        .lines()
        .joinToString(" ") { line -> line.trim() }
        .replace(Regex("\\s+"), " ")
        .trim()
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
    return toOpaqueRgbColorOrNull() ?: Color.Unspecified
}

private fun String.toOpaqueRgbColorOrNull(): Color? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6) return null
    val value = normalized.toIntOrNull(radix = 16) ?: return null
    return Color(
        red = ((value shr 16) and 0xFF) / 255f,
        green = ((value shr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f,
        alpha = 1f,
    )
}

@Composable
private fun plainRichTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
