package com.changeyourlife.cyl.presentation.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.presentation.ai.AiChatMode
import com.changeyourlife.cyl.presentation.ai.AiChatSheet
import com.changeyourlife.cyl.presentation.ai.AiChatMessage
import com.changeyourlife.cyl.presentation.ai.AiChatPageLink
import com.changeyourlife.cyl.presentation.components.CylBottomCommandBar
import com.changeyourlife.cyl.presentation.components.CylChromeIconButton
import com.changeyourlife.cyl.presentation.components.CylFloatingChromeSurface
import com.changeyourlife.cyl.presentation.home.HomeUiState
import com.changeyourlife.cyl.presentation.theme.ChangeYourLifeTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableRowPageSheet(
    table: PageTable,
    row: PageTableRow,
    tableReferences: List<PageTableReference>,
    searchTargetType: String = "",
    searchTargetId: String = "",
    onCellChange: (String, String, String) -> Unit,
    onAddColumn: (String, PageTableColumnType) -> Unit,
    onColumnDateSettingsChange: (
        String,
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onAddRow: () -> Unit,
    onBlockTextChange: (String, String) -> Unit,
    onBlockRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onBlockPasteBlocks: (String, List<RichTextPasteBlock>) -> Unit,
    onBlockTypeChange: (String, PageBlockType) -> Unit,
    onBlockMediaAdd: (String, List<PageMediaAttachment>) -> Unit,
    onBlockMediaRemove: (String, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onAddBlock: (PageBlockType) -> Unit,
    onInsertBlockNear: (String, PageBlockType, PageBlockInsertPosition) -> Unit,
    onCreateLinkedPage: (String) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onIndentBlock: (String) -> Unit,
    onOutdentBlock: (String) -> Unit,
    mentionPages: List<Page> = emptyList(),
    onDismiss: () -> Unit,
) {
    val title = row.cellText(table.titleColumn()).ifBlank { "Untitled row" }
    var isNewColumnSheetOpen by remember { mutableStateOf(false) }
    var rowRichTextToolbarState by remember { mutableStateOf<RichTextToolbarUiState?>(null) }
    var rowFocusRequestSequence by remember(row.id) { mutableStateOf(0L) }
    var rowEditorFocusRequest by remember(row.id) { mutableStateOf<EditorBlockFocusRequest?>(null) }

    fun requestRowEditorFocus(blockId: String?) {
        if (blockId.isNullOrBlank()) return
        rowFocusRequestSequence += 1
        rowEditorFocusRequest = EditorBlockFocusRequest(
            blockId = blockId,
            token = rowFocusRequestSequence,
        )
    }

    fun deleteRowBlockAndFocusSibling(blockId: String) {
        val targetBlockId = row.blocks.editorFocusTargetAfterDeleting(blockId)
        onDeleteBlock(blockId)
        requestRowEditorFocus(targetBlockId)
    }

    LaunchedEffect(row.blocks, rowEditorFocusRequest) {
        val currentFocusRequest = rowEditorFocusRequest
        if (
            currentFocusRequest != null &&
            !row.blocks.containsFocusableEditorBlock(currentFocusRequest.blockId)
        ) {
            rowEditorFocusRequest = null
        }
    }

    if (isNewColumnSheetOpen) {
        NewTableColumnSheet(
            onCreateColumn = { name, type ->
                onAddColumn(name, type)
                isNewColumnSheetOpen = false
            },
            onDismiss = { isNewColumnSheetOpen = false },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close row",
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Properties",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = { isNewColumnSheetOpen = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add property",
                    )
                }
            }

            table.columns.forEach { column ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.widthIn(min = 96.dp).weight(0.42f)) {
                        Text(
                            text = column.name.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = column.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TableCellEditor(
                        column = column,
                        row = row,
                        table = table,
                        tableReferences = tableReferences,
                        value = row.cells[column.id].orEmpty(),
                        onValueChange = { value -> onCellChange(row.id, column.id, value) },
                        onDateSettingsChange = { dateFormat, timeFormat, reminder, timezoneLabel ->
                            onColumnDateSettingsChange(column.id, dateFormat, timeFormat, reminder, timezoneLabel)
                        },
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }

            HorizontalDivider()

            AddBlockToolbar(onAddBlock = onAddBlock)

            if (row.blocks.isEmpty()) {
                Text(
                    text = "No row content yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                row.blocks.forEach { block ->
                    val isSearchHighlighted = block.isDirectSearchTarget(searchTargetType, searchTargetId) ||
                        (searchTargetType == SearchTargetRowBlock && searchTargetId == block.id)
                    val focusRequestToken = if (rowEditorFocusRequest?.blockId == block.id) {
                        rowEditorFocusRequest?.token ?: 0L
                    } else {
                        0L
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSearchHighlighted) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                                } else {
                                    Color.Transparent
                                },
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            when (block.type) {
                                PageBlockType.Divider -> HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))
                                PageBlockType.MediaFile -> MediaFileBlockEditor(
                                    block = block,
                                    onAddAttachments = { attachments -> onBlockMediaAdd(block.id, attachments) },
                                    onRemoveAttachment = { attachmentId -> onBlockMediaRemove(block.id, attachmentId) },
                                    onTextChange = { text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                    onBlockTypeCommand = { type -> onBlockTypeChange(block.id, type) },
                                    onInsertBlockCommand = { type, position ->
                                        onInsertBlockNear(block.id, type, position)
                                    },
                                    onDeleteEmptyBlockCommand = { deleteRowBlockAndFocusSibling(block.id) },
                                    onIndentBlockCommand = { onIndentBlock(block.id) },
                                    onOutdentBlockCommand = { onOutdentBlock(block.id) },
                                    onCreateLinkedPageCommand = { onCreateLinkedPage(block.id) },
                                    onOpenPropertySheetCommand = { isNewColumnSheetOpen = true },
                                    focusRequestToken = focusRequestToken,
                                    onRichTextToolbarChange = { toolbarState ->
                                        rowRichTextToolbarState = toolbarState
                                    },
                                    showInlineRichTextToolbar = false,
                                    mentionPages = mentionPages,
                                    isTableRowPage = true,
                                )
                                PageBlockType.Todo -> TodoBlockEditor(
                                    blockId = block.id,
                                    block = block,
                                    onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                    onPasteBlocks = onBlockPasteBlocks,
                                    onRichTextToolbarChange = { toolbarState ->
                                        rowRichTextToolbarState = toolbarState
                                    },
                                    showInlineRichTextToolbar = false,
                                    onBlockTypeCommand = { _, type -> onBlockTypeChange(block.id, type) },
                                    onInsertBlockCommand = { _, type, position ->
                                        onInsertBlockNear(block.id, type, position)
                                    },
                                    onDeleteEmptyBlockCommand = { deleteRowBlockAndFocusSibling(block.id) },
                                    onIndentBlockCommand = { onIndentBlock(block.id) },
                                    onOutdentBlockCommand = { onOutdentBlock(block.id) },
                                    onCreateLinkedPageCommand = { onCreateLinkedPage(block.id) },
                                    onOpenPropertySheetCommand = { isNewColumnSheetOpen = true },
                                    focusRequestToken = focusRequestToken,
                                    onToggleTodo = { onToggleTodo(block.id) },
                                    mentionPages = mentionPages,
                                    isTableRowPage = true,
                                )
                                PageBlockType.Bullet -> LeadingTextBlockEditor(
                                    blockId = block.id,
                                    leadingText = "-",
                                    block = block,
                                    onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                    onPasteBlocks = onBlockPasteBlocks,
                                    onRichTextToolbarChange = { toolbarState ->
                                        rowRichTextToolbarState = toolbarState
                                    },
                                    showInlineRichTextToolbar = false,
                                    onBlockTypeCommand = { _, type -> onBlockTypeChange(block.id, type) },
                                    onInsertBlockCommand = { _, type, position ->
                                        onInsertBlockNear(block.id, type, position)
                                    },
                                    onDeleteEmptyBlockCommand = { deleteRowBlockAndFocusSibling(block.id) },
                                    onIndentBlockCommand = { onIndentBlock(block.id) },
                                    onOutdentBlockCommand = { onOutdentBlock(block.id) },
                                    onCreateLinkedPageCommand = { onCreateLinkedPage(block.id) },
                                    onOpenPropertySheetCommand = { isNewColumnSheetOpen = true },
                                    focusRequestToken = focusRequestToken,
                                    mentionPages = mentionPages,
                                    isTableRowPage = true,
                                )
                                PageBlockType.Numbered -> LeadingTextBlockEditor(
                                    blockId = block.id,
                                    leadingText = "1.",
                                    block = block,
                                    onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                    onPasteBlocks = onBlockPasteBlocks,
                                    onRichTextToolbarChange = { toolbarState ->
                                        rowRichTextToolbarState = toolbarState
                                    },
                                    showInlineRichTextToolbar = false,
                                    onBlockTypeCommand = { _, type -> onBlockTypeChange(block.id, type) },
                                    onInsertBlockCommand = { _, type, position ->
                                        onInsertBlockNear(block.id, type, position)
                                    },
                                    onDeleteEmptyBlockCommand = { deleteRowBlockAndFocusSibling(block.id) },
                                    onIndentBlockCommand = { onIndentBlock(block.id) },
                                    onOutdentBlockCommand = { onOutdentBlock(block.id) },
                                    onCreateLinkedPageCommand = { onCreateLinkedPage(block.id) },
                                    onOpenPropertySheetCommand = { isNewColumnSheetOpen = true },
                                    focusRequestToken = focusRequestToken,
                                    mentionPages = mentionPages,
                                    isTableRowPage = true,
                                )
                                PageBlockType.Quote -> LeadingTextBlockEditor(
                                    blockId = block.id,
                                    leadingText = "|",
                                    block = block,
                                    onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                    onPasteBlocks = onBlockPasteBlocks,
                                    onRichTextToolbarChange = { toolbarState ->
                                        rowRichTextToolbarState = toolbarState
                                    },
                                    showInlineRichTextToolbar = false,
                                    onBlockTypeCommand = { _, type -> onBlockTypeChange(block.id, type) },
                                    onInsertBlockCommand = { _, type, position ->
                                        onInsertBlockNear(block.id, type, position)
                                    },
                                    onDeleteEmptyBlockCommand = { deleteRowBlockAndFocusSibling(block.id) },
                                    onIndentBlockCommand = { onIndentBlock(block.id) },
                                    onOutdentBlockCommand = { onOutdentBlock(block.id) },
                                    onCreateLinkedPageCommand = { onCreateLinkedPage(block.id) },
                                    onOpenPropertySheetCommand = { isNewColumnSheetOpen = true },
                                    focusRequestToken = focusRequestToken,
                                    fontStyle = FontStyle.Italic,
                                    mentionPages = mentionPages,
                                    isTableRowPage = true,
                                )
                                PageBlockType.DatabaseTable,
                                PageBlockType.Heading,
                                PageBlockType.Text,
                                -> TextBlockEditor(
                                    blockId = block.id,
                                    block = block,
                                    onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                    onPasteBlocks = onBlockPasteBlocks,
                                    onRichTextToolbarChange = { toolbarState ->
                                        rowRichTextToolbarState = toolbarState
                                    },
                                    showInlineRichTextToolbar = false,
                                    onBlockTypeCommand = { _, type -> onBlockTypeChange(block.id, type) },
                                    onInsertBlockCommand = { _, type, position ->
                                        onInsertBlockNear(block.id, type, position)
                                    },
                                    onDeleteEmptyBlockCommand = { deleteRowBlockAndFocusSibling(block.id) },
                                    onIndentBlockCommand = { onIndentBlock(block.id) },
                                    onOutdentBlockCommand = { onOutdentBlock(block.id) },
                                    onCreateLinkedPageCommand = { onCreateLinkedPage(block.id) },
                                    onOpenPropertySheetCommand = { isNewColumnSheetOpen = true },
                                    focusRequestToken = focusRequestToken,
                                    mentionPages = mentionPages,
                                    isTableRowPage = true,
                                )
                            }
                        }
                        IconButton(
                            onClick = { deleteRowBlockAndFocusSibling(block.id) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete row block",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            )
                        }
                    }
                }
            }

            rowRichTextToolbarState?.takeIf { state -> state.isValidForKeyboardToolbar() }?.let { toolbarState ->
                PageKeyboardRichTextToolbar(toolbarState)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

