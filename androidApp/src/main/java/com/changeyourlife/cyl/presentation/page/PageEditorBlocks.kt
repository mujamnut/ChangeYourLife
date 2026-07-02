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

@Composable
internal fun BlockEditorCard(
    block: PageBlock,
    isFirstBlock: Boolean = false,
    indentLevel: Int = 0,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onPasteBlocks: (String, List<RichTextPasteBlock>) -> Unit,
    onRichTextToolbarChange: (RichTextToolbarUiState?) -> Unit,
    onBlockTypeChange: (String, PageBlockType) -> Unit,
    onMediaAdd: (String, List<PageMediaAttachment>) -> Unit,
    onMediaRemove: (String, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onIndentBlock: (String) -> Unit,
    onOutdentBlock: (String) -> Unit,
    onBlockFocused: (String) -> Unit,
    focusRequest: EditorBlockFocusRequest?,
    activeBlockId: String? = null,
    onAddChildBlock: (String, PageBlockType) -> Unit,
    onInsertBlockNear: (String, PageBlockType, PageBlockInsertPosition) -> Unit,
    onCreateLinkedChildPageFromBlock: (String) -> Unit,
    mentionPages: List<Page> = emptyList(),
    onTableTitleChange: (String, String) -> Unit,
    onTableViewChange: (String, PageTableView) -> Unit,
    onTableViewConfigChange: (String, PageTableViewConfig) -> Unit,
    onTableSortChange: (String, String, PageTableSortDirection) -> Unit,
    onTableFilterChange: (String, String, String) -> Unit,
    onTableGroupChange: (String, String) -> Unit,
    onTableColumnNameChange: (String, String, String) -> Unit,
    onTableColumnTypeChange: (String, String, PageTableColumnType) -> Unit,
    onTableColumnDateSettingsChange: (
        String,
        String,
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onTableColumnFormulaChange: (String, String, String) -> Unit,
    onTableColumnRelationTargetChange: (String, String, String) -> Unit,
    onTableColumnRollupChange: (String, String, String, String, PageTableRollupAggregation) -> Unit,
    onTableCellChange: (String, String, String, String) -> Unit,
    onAddTableColumn: (String, String, PageTableColumnType) -> Unit,
    onInsertTableColumn: (String, String, TableColumnInsertSide) -> Unit,
    onDuplicateTableColumn: (String, String) -> Unit,
    onDeleteTableColumn: (String, String) -> Unit,
    onAddTableRow: (String) -> Unit,
    onDeleteTableRow: (String, String) -> Unit,
    onTableRowBlockTextChange: (String, String, String, String) -> Unit,
    onTableRowBlockRichTextChange: (String, String, String, String, List<PageTextSpan>) -> Unit,
    onTableRowBlockPasteBlocks: (String, String, String, List<RichTextPasteBlock>) -> Unit,
    onTableRowBlockTypeChange: (String, String, String, PageBlockType) -> Unit,
    onTableRowBlockMediaAdd: (String, String, String, List<PageMediaAttachment>) -> Unit,
    onTableRowBlockMediaRemove: (String, String, String, String) -> Unit,
    onToggleTableRowTodoBlock: (String, String, String) -> Unit,
    onAddTableRowPageBlock: (String, String, PageBlockType) -> Unit,
    onInsertTableRowPageBlockNear: (String, String, String, PageBlockType, PageBlockInsertPosition) -> Unit,
    onCreateLinkedChildPageFromTableRowBlock: (String, String, String) -> Unit,
    onDeleteTableRowPageBlock: (String, String, String) -> Unit,
    onIndentTableRowPageBlock: (String, String, String) -> Unit,
    onOutdentTableRowPageBlock: (String, String, String) -> Unit,
    tableReferences: List<PageTableReference>,
    searchTargetType: String = "",
    searchTargetId: String = "",
) {
    val isTextLikeBlock = block.type.isTextLikeEditorBlock
    val isFlushBlock = isTextLikeBlock ||
        block.type == PageBlockType.DatabaseTable ||
        block.type == PageBlockType.Divider
    val isSearchHighlighted = block.isDirectSearchTarget(searchTargetType, searchTargetId)
    val focusRequestToken = if (focusRequest?.blockId == block.id) {
        focusRequest.token
    } else {
        0L
    }

    val blockModifier = Modifier
        .fillMaxWidth()
        .padding(start = (indentLevel * 16).dp)

    val blockContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isFlushBlock) {
                        0.dp
                    } else {
                        6.dp
                    },
                    vertical = if (isFlushBlock) {
                        0.dp
                    } else {
                        8.dp
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(
                if (isFlushBlock) 0.dp else 6.dp,
            ),
        ) {
            when (block.type) {
                PageBlockType.Divider -> HorizontalDivider()
                PageBlockType.MediaFile -> MediaFileBlockEditor(
                    block = block,
                    onAddAttachments = { attachments -> onMediaAdd(block.id, attachments) },
                    onRemoveAttachment = { attachmentId -> onMediaRemove(block.id, attachmentId) },
                    onTextChange = { text -> onTextChange(block.id, text) },
                    onRichTextChange = { text, spans -> onRichTextChange(block.id, text, spans) },
                    onBlockTypeCommand = { type -> onBlockTypeChange(block.id, type) },
                    onInsertBlockCommand = { type, position -> onInsertBlockNear(block.id, type, position) },
                    onDeleteEmptyBlockCommand = { onDelete(block.id) },
                    onIndentBlockCommand = { onIndentBlock(block.id) },
                    onOutdentBlockCommand = { onOutdentBlock(block.id) },
                    onCreateLinkedPageCommand = { onCreateLinkedChildPageFromBlock(block.id) },
                    focusRequestToken = focusRequestToken,
                    onRichTextToolbarChange = onRichTextToolbarChange,
                    showInlineRichTextToolbar = false,
                    mentionPages = mentionPages,
                    isFirstBlock = isFirstBlock,
                )
                PageBlockType.DatabaseTable -> DatabaseTableBlockEditor(
                    tableBlockId = block.id,
                    table = block.table,
                    tableReferences = tableReferences,
                    onTitleChange = { title -> onTableTitleChange(block.id, title) },
                    onViewChange = { view -> onTableViewChange(block.id, view) },
                    onViewConfigChange = { config -> onTableViewConfigChange(block.id, config) },
                    onSortChange = { columnId, direction -> onTableSortChange(block.id, columnId, direction) },
                    onFilterChange = { columnId, query -> onTableFilterChange(block.id, columnId, query) },
                    onGroupChange = { columnId -> onTableGroupChange(block.id, columnId) },
                    onColumnNameChange = { columnId, name -> onTableColumnNameChange(block.id, columnId, name) },
                    onColumnTypeChange = { columnId, type -> onTableColumnTypeChange(block.id, columnId, type) },
                    onColumnDateSettingsChange = { columnId, dateFormat, timeFormat, reminder, timezoneLabel ->
                        onTableColumnDateSettingsChange(
                            block.id,
                            columnId,
                            dateFormat,
                            timeFormat,
                            reminder,
                            timezoneLabel,
                        )
                    },
                    onColumnFormulaChange = { columnId, formula ->
                        onTableColumnFormulaChange(block.id, columnId, formula)
                    },
                    onColumnRelationTargetChange = { columnId, targetTableId ->
                        onTableColumnRelationTargetChange(block.id, columnId, targetTableId)
                    },
                    onColumnRollupChange = { columnId, relationColumnId, targetColumnId, aggregation ->
                        onTableColumnRollupChange(block.id, columnId, relationColumnId, targetColumnId, aggregation)
                    },
                    onCellChange = { rowId, columnId, value -> onTableCellChange(block.id, rowId, columnId, value) },
                    onAddColumn = { name, type -> onAddTableColumn(block.id, name, type) },
                    onInsertColumn = { columnId, side -> onInsertTableColumn(block.id, columnId, side) },
                    onDuplicateColumn = { columnId -> onDuplicateTableColumn(block.id, columnId) },
                    onDeleteColumn = { columnId -> onDeleteTableColumn(block.id, columnId) },
                    onAddRow = { onAddTableRow(block.id) },
                    onDeleteRow = { rowId -> onDeleteTableRow(block.id, rowId) },
                    onRowBlockTextChange = { rowId, rowBlockId, text ->
                        onTableRowBlockTextChange(block.id, rowId, rowBlockId, text)
                    },
                    onRowBlockRichTextChange = { rowId, rowBlockId, text, spans ->
                        onTableRowBlockRichTextChange(block.id, rowId, rowBlockId, text, spans)
                    },
                    onRowBlockPasteBlocks = { rowId, rowBlockId, pasteBlocks ->
                        onTableRowBlockPasteBlocks(block.id, rowId, rowBlockId, pasteBlocks)
                    },
                    onRowBlockTypeChange = { rowId, rowBlockId, type ->
                        onTableRowBlockTypeChange(block.id, rowId, rowBlockId, type)
                    },
                    onRowBlockMediaAdd = { rowId, rowBlockId, attachments ->
                        onTableRowBlockMediaAdd(block.id, rowId, rowBlockId, attachments)
                    },
                    onRowBlockMediaRemove = { rowId, rowBlockId, attachmentId ->
                        onTableRowBlockMediaRemove(block.id, rowId, rowBlockId, attachmentId)
                    },
                    onToggleRowTodoBlock = { rowId, rowBlockId ->
                        onToggleTableRowTodoBlock(block.id, rowId, rowBlockId)
                    },
                    onAddRowPageBlock = { rowId, type ->
                        onAddTableRowPageBlock(block.id, rowId, type)
                    },
                    onInsertRowPageBlockNear = { rowId, rowBlockId, type, position ->
                        onInsertTableRowPageBlockNear(block.id, rowId, rowBlockId, type, position)
                    },
                    onCreateRowLinkedPage = { rowId, rowBlockId ->
                        onCreateLinkedChildPageFromTableRowBlock(block.id, rowId, rowBlockId)
                    },
                    onDeleteRowPageBlock = { rowId, rowBlockId ->
                        onDeleteTableRowPageBlock(block.id, rowId, rowBlockId)
                    },
                    onIndentRowPageBlock = { rowId, rowBlockId ->
                        onIndentTableRowPageBlock(block.id, rowId, rowBlockId)
                    },
                    onOutdentRowPageBlock = { rowId, rowBlockId ->
                        onOutdentTableRowPageBlock(block.id, rowId, rowBlockId)
                    },
                    mentionPages = mentionPages,
                    searchTargetType = searchTargetType,
                    searchTargetId = searchTargetId,
                )
                PageBlockType.Todo -> TodoBlockEditor(
                    blockId = block.id,
                    block = block,
                    onTextChange = onTextChange,
                    onRichTextChange = onRichTextChange,
                    onPasteBlocks = onPasteBlocks,
                    onRichTextToolbarChange = onRichTextToolbarChange,
                    showInlineRichTextToolbar = false,
                    onBlockTypeCommand = onBlockTypeChange,
                    onInsertBlockCommand = onInsertBlockNear,
                    onDeleteEmptyBlockCommand = onDelete,
                    onIndentBlockCommand = onIndentBlock,
                    onOutdentBlockCommand = onOutdentBlock,
                    onCreateLinkedPageCommand = onCreateLinkedChildPageFromBlock,
                    focusRequestToken = focusRequestToken,
                    onToggleTodo = onToggleTodo,
                    onFocusBlock = { onBlockFocused(block.id) },
                    mentionPages = mentionPages,
                    isFirstBlock = isFirstBlock,
                )
                PageBlockType.Bullet -> LeadingTextBlockEditor(
                    blockId = block.id,
                    leadingText = "-",
                    block = block,
                    onTextChange = onTextChange,
                    onRichTextChange = onRichTextChange,
                    onPasteBlocks = onPasteBlocks,
                    onRichTextToolbarChange = onRichTextToolbarChange,
                    showInlineRichTextToolbar = false,
                    onBlockTypeCommand = onBlockTypeChange,
                    onInsertBlockCommand = onInsertBlockNear,
                    onDeleteEmptyBlockCommand = onDelete,
                    onIndentBlockCommand = onIndentBlock,
                    onOutdentBlockCommand = onOutdentBlock,
                    onCreateLinkedPageCommand = onCreateLinkedChildPageFromBlock,
                    focusRequestToken = focusRequestToken,
                    onFocusBlock = { onBlockFocused(block.id) },
                    mentionPages = mentionPages,
                    isFirstBlock = isFirstBlock,
                )
                PageBlockType.Numbered -> LeadingTextBlockEditor(
                    blockId = block.id,
                    leadingText = "1.",
                    block = block,
                    onTextChange = onTextChange,
                    onRichTextChange = onRichTextChange,
                    onPasteBlocks = onPasteBlocks,
                    onRichTextToolbarChange = onRichTextToolbarChange,
                    showInlineRichTextToolbar = false,
                    onBlockTypeCommand = onBlockTypeChange,
                    onInsertBlockCommand = onInsertBlockNear,
                    onDeleteEmptyBlockCommand = onDelete,
                    onIndentBlockCommand = onIndentBlock,
                    onOutdentBlockCommand = onOutdentBlock,
                    onCreateLinkedPageCommand = onCreateLinkedChildPageFromBlock,
                    focusRequestToken = focusRequestToken,
                    onFocusBlock = { onBlockFocused(block.id) },
                    mentionPages = mentionPages,
                    isFirstBlock = isFirstBlock,
                )
                PageBlockType.Quote -> LeadingTextBlockEditor(
                    blockId = block.id,
                    leadingText = "|",
                    block = block,
                    onTextChange = onTextChange,
                    onRichTextChange = onRichTextChange,
                    onPasteBlocks = onPasteBlocks,
                    onRichTextToolbarChange = onRichTextToolbarChange,
                    showInlineRichTextToolbar = false,
                    onBlockTypeCommand = onBlockTypeChange,
                    onInsertBlockCommand = onInsertBlockNear,
                    onDeleteEmptyBlockCommand = onDelete,
                    onIndentBlockCommand = onIndentBlock,
                    onOutdentBlockCommand = onOutdentBlock,
                    onCreateLinkedPageCommand = onCreateLinkedChildPageFromBlock,
                    focusRequestToken = focusRequestToken,
                    fontStyle = FontStyle.Italic,
                    onFocusBlock = { onBlockFocused(block.id) },
                    mentionPages = mentionPages,
                    isFirstBlock = isFirstBlock,
                )
                PageBlockType.Heading,
                PageBlockType.Text,
                -> TextBlockEditor(
                    blockId = block.id,
                    block = block,
                    onTextChange = onTextChange,
                    onRichTextChange = onRichTextChange,
                    onPasteBlocks = onPasteBlocks,
                    onRichTextToolbarChange = onRichTextToolbarChange,
                    showInlineRichTextToolbar = false,
                    onBlockTypeCommand = onBlockTypeChange,
                    onInsertBlockCommand = onInsertBlockNear,
                    onDeleteEmptyBlockCommand = onDelete,
                    onIndentBlockCommand = onIndentBlock,
                    onOutdentBlockCommand = onOutdentBlock,
                    onCreateLinkedPageCommand = onCreateLinkedChildPageFromBlock,
                    focusRequestToken = focusRequestToken,
                    onFocusBlock = { onBlockFocused(block.id) },
                    mentionPages = mentionPages,
                    isFirstBlock = isFirstBlock,
                )
            }

            if (block.children.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    block.children.forEach { child ->
                        key(child.id) {
                            BlockEditorCard(
                                block = child,
                                indentLevel = indentLevel + 1,
                                onTextChange = onTextChange,
                                onRichTextChange = onRichTextChange,
                                onPasteBlocks = onPasteBlocks,
                                onRichTextToolbarChange = onRichTextToolbarChange,
                                onBlockTypeChange = onBlockTypeChange,
                                onMediaAdd = onMediaAdd,
                                onMediaRemove = onMediaRemove,
                                onToggleTodo = onToggleTodo,
                                onDelete = onDelete,
                                onMoveUp = onMoveUp,
                                onMoveDown = onMoveDown,
                                onIndentBlock = onIndentBlock,
                                onOutdentBlock = onOutdentBlock,
                                onBlockFocused = onBlockFocused,
                                focusRequest = focusRequest,
                                activeBlockId = activeBlockId,
                                isFirstBlock = false,
                                onAddChildBlock = onAddChildBlock,
                                onInsertBlockNear = onInsertBlockNear,
                                onCreateLinkedChildPageFromBlock = onCreateLinkedChildPageFromBlock,
                                mentionPages = mentionPages,
                                onTableTitleChange = onTableTitleChange,
                                onTableViewChange = onTableViewChange,
                                onTableViewConfigChange = onTableViewConfigChange,
                                onTableSortChange = onTableSortChange,
                                onTableFilterChange = onTableFilterChange,
                                onTableGroupChange = onTableGroupChange,
                                onTableColumnNameChange = onTableColumnNameChange,
                                onTableColumnTypeChange = onTableColumnTypeChange,
                                onTableColumnDateSettingsChange = onTableColumnDateSettingsChange,
                                onTableColumnFormulaChange = onTableColumnFormulaChange,
                                onTableColumnRelationTargetChange = onTableColumnRelationTargetChange,
                                onTableColumnRollupChange = onTableColumnRollupChange,
                                onTableCellChange = onTableCellChange,
                                onAddTableColumn = onAddTableColumn,
                                onInsertTableColumn = onInsertTableColumn,
                                onDuplicateTableColumn = onDuplicateTableColumn,
                                onDeleteTableColumn = onDeleteTableColumn,
                                onAddTableRow = onAddTableRow,
                                onDeleteTableRow = onDeleteTableRow,
                                onTableRowBlockTextChange = onTableRowBlockTextChange,
                                onTableRowBlockRichTextChange = onTableRowBlockRichTextChange,
                                onTableRowBlockPasteBlocks = onTableRowBlockPasteBlocks,
                                onTableRowBlockTypeChange = onTableRowBlockTypeChange,
                                onTableRowBlockMediaAdd = onTableRowBlockMediaAdd,
                                onTableRowBlockMediaRemove = onTableRowBlockMediaRemove,
                                onToggleTableRowTodoBlock = onToggleTableRowTodoBlock,
                                onAddTableRowPageBlock = onAddTableRowPageBlock,
                                onInsertTableRowPageBlockNear = onInsertTableRowPageBlockNear,
                                onCreateLinkedChildPageFromTableRowBlock = onCreateLinkedChildPageFromTableRowBlock,
                                onDeleteTableRowPageBlock = onDeleteTableRowPageBlock,
                                onIndentTableRowPageBlock = onIndentTableRowPageBlock,
                                onOutdentTableRowPageBlock = onOutdentTableRowPageBlock,
                                tableReferences = tableReferences,
                                searchTargetType = searchTargetType,
                                searchTargetId = searchTargetId,
                            )
                        }
                    }
                }
            }
        }
    }

    if (isTextLikeBlock) {
        val textBlockModifier = if (isSearchHighlighted) {
            blockModifier.background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                shape = RoundedCornerShape(8.dp),
            )
        } else {
            blockModifier
        }
        Box(modifier = textBlockModifier) {
            blockContent()
        }
    } else {
        val structuredBlockModifier = if (isSearchHighlighted) {
            blockModifier.background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                shape = RoundedCornerShape(8.dp),
            )
        } else {
            blockModifier
        }
        Box(modifier = structuredBlockModifier) {
            blockContent()
        }
    }
}

@Composable
internal fun TextBlockEditor(
    blockId: String,
    block: PageBlock,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onPasteBlocks: (String, List<RichTextPasteBlock>) -> Unit = { _, _ -> },
    onRichTextToolbarChange: (RichTextToolbarUiState?) -> Unit = {},
    showInlineRichTextToolbar: Boolean = true,
    onBlockTypeCommand: (String, PageBlockType) -> Unit,
    onInsertBlockCommand: (String, PageBlockType, PageBlockInsertPosition) -> Unit,
    onDeleteEmptyBlockCommand: (String) -> Unit,
    onIndentBlockCommand: (String) -> Unit = {},
    onOutdentBlockCommand: (String) -> Unit = {},
    onCreateLinkedPageCommand: (String) -> Unit,
    onOpenPropertySheetCommand: (String) -> Unit = {},
    focusRequestToken: Long = 0L,
    onFocusBlock: () -> Unit = {},
    mentionPages: List<Page> = emptyList(),
    isFirstBlock: Boolean = false,
    isTableRowPage: Boolean = false,
) {
    CylRichTextBlockEditor(
        blockId = blockId,
        block = block,
        onTextChange = onTextChange,
        onRichTextChange = onRichTextChange,
        onPasteBlocks = onPasteBlocks,
        onToolbarStateChange = onRichTextToolbarChange,
        showInlineToolbar = showInlineRichTextToolbar,
        modifier = Modifier.fillMaxWidth(),
        onFocusBlock = onFocusBlock,
        onBlockTypeCommand = onBlockTypeCommand,
        onInsertBlockCommand = onInsertBlockCommand,
        onDeleteEmptyBlockCommand = onDeleteEmptyBlockCommand,
        onIndentBlockCommand = onIndentBlockCommand,
        onOutdentBlockCommand = onOutdentBlockCommand,
        onCreateLinkedPageCommand = onCreateLinkedPageCommand,
        onOpenPropertySheetCommand = onOpenPropertySheetCommand,
        focusRequestToken = focusRequestToken,
        mentionPages = mentionPages,
        minLines = if (block.type == PageBlockType.Heading) 1 else 2,
        textStyle = when (block.type) {
            PageBlockType.Heading -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            else -> MaterialTheme.typography.bodyLarge
        },
        placeholder = block.type.placeholder,
        placeholderContext = EditorPlaceholderContext(
            type = block.type,
            isFirstBlock = isFirstBlock,
            isTableRowPage = isTableRowPage,
        ),
    )
}

@Composable
internal fun TodoBlockEditor(
    blockId: String,
    block: PageBlock,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onPasteBlocks: (String, List<RichTextPasteBlock>) -> Unit = { _, _ -> },
    onRichTextToolbarChange: (RichTextToolbarUiState?) -> Unit = {},
    showInlineRichTextToolbar: Boolean = true,
    onBlockTypeCommand: (String, PageBlockType) -> Unit,
    onInsertBlockCommand: (String, PageBlockType, PageBlockInsertPosition) -> Unit,
    onDeleteEmptyBlockCommand: (String) -> Unit,
    onIndentBlockCommand: (String) -> Unit = {},
    onOutdentBlockCommand: (String) -> Unit = {},
    onCreateLinkedPageCommand: (String) -> Unit,
    onOpenPropertySheetCommand: (String) -> Unit = {},
    focusRequestToken: Long = 0L,
    onToggleTodo: (String) -> Unit,
    onFocusBlock: () -> Unit = {},
    mentionPages: List<Page> = emptyList(),
    isFirstBlock: Boolean = false,
    isTableRowPage: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = block.isChecked,
            onCheckedChange = { onToggleTodo(blockId) },
        )
        CylRichTextBlockEditor(
            blockId = blockId,
            block = block,
            onTextChange = onTextChange,
            onRichTextChange = onRichTextChange,
            onPasteBlocks = onPasteBlocks,
            onToolbarStateChange = onRichTextToolbarChange,
            showInlineToolbar = showInlineRichTextToolbar,
            modifier = Modifier.weight(1f),
            onFocusBlock = onFocusBlock,
            onBlockTypeCommand = onBlockTypeCommand,
            onInsertBlockCommand = onInsertBlockCommand,
            onDeleteEmptyBlockCommand = onDeleteEmptyBlockCommand,
            onIndentBlockCommand = onIndentBlockCommand,
            onOutdentBlockCommand = onOutdentBlockCommand,
            onCreateLinkedPageCommand = onCreateLinkedPageCommand,
            onOpenPropertySheetCommand = onOpenPropertySheetCommand,
            focusRequestToken = focusRequestToken,
            mentionPages = mentionPages,
            singleLine = true,
            placeholder = "Todo item",
            placeholderContext = EditorPlaceholderContext(
                type = block.type,
                isFirstBlock = isFirstBlock,
                isTableRowPage = isTableRowPage,
            ),
        )
    }
}

@Composable
internal fun LeadingTextBlockEditor(
    blockId: String,
    leadingText: String,
    block: PageBlock,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onPasteBlocks: (String, List<RichTextPasteBlock>) -> Unit = { _, _ -> },
    onRichTextToolbarChange: (RichTextToolbarUiState?) -> Unit = {},
    showInlineRichTextToolbar: Boolean = true,
    onBlockTypeCommand: (String, PageBlockType) -> Unit,
    onInsertBlockCommand: (String, PageBlockType, PageBlockInsertPosition) -> Unit,
    onDeleteEmptyBlockCommand: (String) -> Unit,
    onIndentBlockCommand: (String) -> Unit = {},
    onOutdentBlockCommand: (String) -> Unit = {},
    onCreateLinkedPageCommand: (String) -> Unit,
    onOpenPropertySheetCommand: (String) -> Unit = {},
    focusRequestToken: Long = 0L,
    fontStyle: FontStyle? = null,
    onFocusBlock: () -> Unit = {},
    mentionPages: List<Page> = emptyList(),
    isFirstBlock: Boolean = false,
    isTableRowPage: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = leadingText,
            modifier = Modifier
                .width(28.dp)
                .padding(top = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CylRichTextBlockEditor(
            blockId = blockId,
            block = block,
            onTextChange = onTextChange,
            onRichTextChange = onRichTextChange,
            onPasteBlocks = onPasteBlocks,
            onToolbarStateChange = onRichTextToolbarChange,
            showInlineToolbar = showInlineRichTextToolbar,
            modifier = Modifier.weight(1f),
            onFocusBlock = onFocusBlock,
            onBlockTypeCommand = onBlockTypeCommand,
            onInsertBlockCommand = onInsertBlockCommand,
            onDeleteEmptyBlockCommand = onDeleteEmptyBlockCommand,
            onIndentBlockCommand = onIndentBlockCommand,
            onOutdentBlockCommand = onOutdentBlockCommand,
            onCreateLinkedPageCommand = onCreateLinkedPageCommand,
            onOpenPropertySheetCommand = onOpenPropertySheetCommand,
            focusRequestToken = focusRequestToken,
            mentionPages = mentionPages,
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontStyle = fontStyle,
            ),
            placeholder = block.type.placeholder,
            placeholderContext = EditorPlaceholderContext(
                type = block.type,
                isFirstBlock = isFirstBlock,
                isTableRowPage = isTableRowPage,
            ),
        )
    }
}

@Composable
internal fun MediaFileBlockEditor(
    block: PageBlock,
    onAddAttachments: (List<PageMediaAttachment>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onRichTextChange: (String, List<PageTextSpan>) -> Unit,
    onBlockTypeCommand: (PageBlockType) -> Unit,
    onInsertBlockCommand: (PageBlockType, PageBlockInsertPosition) -> Unit,
    onDeleteEmptyBlockCommand: () -> Unit,
    onIndentBlockCommand: () -> Unit = {},
    onOutdentBlockCommand: () -> Unit = {},
    onCreateLinkedPageCommand: () -> Unit,
    onOpenPropertySheetCommand: () -> Unit = {},
    focusRequestToken: Long = 0L,
    onRichTextToolbarChange: (RichTextToolbarUiState?) -> Unit = {},
    showInlineRichTextToolbar: Boolean = true,
    mentionPages: List<Page> = emptyList(),
    isFirstBlock: Boolean = false,
    isTableRowPage: Boolean = false,
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val attachments = uris.mapNotNull { uri ->
            context.persistMediaReadPermission(uri)
            uri.toPageMediaAttachment(context)
        }
        onAddAttachments(attachments)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { filePicker.launch(arrayOf("*/*")) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Article,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                )
                Text(
                    text = if (block.mediaAttachments.isEmpty()) {
                        "Add file or media"
                    } else {
                        "${block.mediaAttachments.size} attached"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Attach file",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }

        if (block.mediaAttachments.isNotEmpty()) {
            block.mediaAttachments.forEach { attachment ->
                MediaAttachmentCard(
                    attachment = attachment,
                    onOpen = { context.openMediaAttachment(attachment) },
                    onRemove = { onRemoveAttachment(attachment.id) },
                )
            }
        }

        CylRichTextBlockEditor(
            blockId = block.id,
            block = block,
            onTextChange = { _, text -> onTextChange(text) },
            onRichTextChange = { _, text, spans -> onRichTextChange(text, spans) },
            modifier = Modifier.fillMaxWidth(),
            onBlockTypeCommand = { _, type -> onBlockTypeCommand(type) },
            onInsertBlockCommand = { _, type, position -> onInsertBlockCommand(type, position) },
            onDeleteEmptyBlockCommand = { onDeleteEmptyBlockCommand() },
            onIndentBlockCommand = { onIndentBlockCommand() },
            onOutdentBlockCommand = { onOutdentBlockCommand() },
            onCreateLinkedPageCommand = { onCreateLinkedPageCommand() },
            onOpenPropertySheetCommand = { onOpenPropertySheetCommand() },
            focusRequestToken = focusRequestToken,
            onToolbarStateChange = onRichTextToolbarChange,
            showInlineToolbar = showInlineRichTextToolbar,
            enableMultiBlockPaste = false,
            mentionPages = mentionPages,
            minLines = 1,
            placeholder = "Caption",
            placeholderContext = EditorPlaceholderContext(
                type = PageBlockType.MediaFile,
                isFirstBlock = isFirstBlock,
                isTableRowPage = isTableRowPage,
                isMediaCaption = true,
            ),
        )
    }
}

@Composable
internal fun MediaAttachmentCard(
    attachment: PageMediaAttachment,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val previewBitmap = rememberAttachmentBitmap(attachment)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = attachment.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name.ifBlank { "Untitled file" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(
                        attachment.mimeType.ifBlank { "unknown type" },
                        attachment.sizeBytes.formatFileSize(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Remove file",
                )
            }
        }
    }
}

@Composable
internal fun rememberAttachmentBitmap(attachment: PageMediaAttachment): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    return remember(attachment.uri, attachment.mimeType) {
        if (!attachment.mimeType.startsWith("image/")) {
            null
        } else {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
}

