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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableFilter
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TableRowPageSheet(
    currentTableBlockId: String,
    currentPageId: String,
    table: PageTable,
    row: PageTableRow,
    syncState: PageSyncState,
    isSaving: Boolean,
    tableReferences: List<PageTableReference>,
    searchTargetType: String = "",
    searchTargetId: String = "",
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (PageTableFilter) -> Unit,
    onGroupChange: (String) -> Unit,
    onColumnNameChange: (String, String) -> Unit,
    onColumnTypeChange: (String, PageTableColumnType) -> Unit,
    onColumnConfigChange: (String, PageTableColumnConfig) -> Unit,
    onCellChange: (String, String, String) -> Unit,
    onRelationCellChange: (String, String, List<String>) -> Unit,
    onAddColumn: (String, PageTableColumnType) -> Unit,
    onInsertColumn: (String, TableColumnInsertSide) -> Unit,
    onDuplicateColumn: (String) -> Unit,
    onDeleteColumn: (String) -> Unit,
    onColumnDateSettingsChange: (
        String,
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onColumnFormulaChange: (String, String) -> Unit,
    onColumnRelationTargetChange: (String, String) -> Unit,
    onColumnRollupChange: (String, String, String, PageTableRollupAggregation) -> Unit,
    onAddRow: () -> Unit,
    onAddRelationTargetRow: (String) -> Unit,
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
    onMoveBlockUp: (String) -> Unit,
    onMoveBlockDown: (String) -> Unit,
    onIndentBlock: (String) -> Unit,
    onOutdentBlock: (String) -> Unit,
    mentionPages: List<Page> = emptyList(),
    onDismiss: () -> Unit,
) {
    val titleColumn = table.titleColumn()
    val title = row.cellText(titleColumn)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val focusManager = LocalFocusManager.current
    var isNewColumnSheetOpen by remember { mutableStateOf(false) }
    var editingPropertyColumnId by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
    var rowRichTextToolbarState by remember { mutableStateOf<RichTextToolbarUiState?>(null) }
    var activeRowBlockId by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
    var isRowBodyFocused by rememberSaveable(row.id) { mutableStateOf(false) }
    var isRowStarterPlaceholderDismissed by rememberSaveable(row.id) {
        mutableStateOf(row.blocks.isNotEmpty())
    }
    var pendingFocusBeforeAdd by remember(row.id) { mutableStateOf<Set<String>?>(null) }
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

    fun clearRowEditorFocus() {
        activeRowBlockId = null
        isRowBodyFocused = false
        rowRichTextToolbarState = null
        pendingFocusBeforeAdd = null
        focusManager.clearFocus()
    }

    fun addRowBlockAndFocus(type: PageBlockType) {
        isRowBodyFocused = true
        isRowStarterPlaceholderDismissed = true
        rowRichTextToolbarState = null
        pendingFocusBeforeAdd = row.blocks.rowBlockIds()
        onAddBlock(type)
    }

    fun focusOrCreateRowBodyTextBlock() {
        isRowBodyFocused = true
        isRowStarterPlaceholderDismissed = true
        rowRichTextToolbarState = null
        row.blocks.firstFocusableEditorBlockId()?.let { blockId ->
            activeRowBlockId = blockId
            requestRowEditorFocus(blockId)
        } ?: addRowBlockAndFocus(PageBlockType.Text)
    }

    fun updateRowToolbarState(
        blockId: String,
        toolbarState: RichTextToolbarUiState?,
    ) {
        rowRichTextToolbarState = toolbarState
        if (toolbarState == null && activeRowBlockId == blockId) {
            activeRowBlockId = null
            isRowBodyFocused = false
        }
    }

    fun deleteRowBlockAndFocusSibling(blockId: String) {
        val targetBlockId = row.blocks.editorFocusTargetAfterDeleting(blockId)
        activeRowBlockId = targetBlockId
        isRowBodyFocused = targetBlockId == null
        rowRichTextToolbarState = null
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

    LaunchedEffect(row.blocks, activeRowBlockId) {
        val currentActiveBlockId = activeRowBlockId
        if (currentActiveBlockId != null && !row.blocks.containsEditorBlock(currentActiveBlockId)) {
            activeRowBlockId = null
            rowRichTextToolbarState = null
        }
    }

    LaunchedEffect(row.blocks.isNotEmpty()) {
        if (row.blocks.isNotEmpty()) {
            isRowStarterPlaceholderDismissed = true
        }
    }

    LaunchedEffect(row.blocks, pendingFocusBeforeAdd) {
        val previousIds = pendingFocusBeforeAdd ?: return@LaunchedEffect
        val targetBlockId = row.blocks.firstNewFocusableRowBlockId(previousIds)
            ?: row.blocks.firstFocusableEditorBlockId()
        if (targetBlockId != null) {
            pendingFocusBeforeAdd = null
            activeRowBlockId = targetBlockId
            isRowBodyFocused = false
            requestRowEditorFocus(targetBlockId)
        } else if (row.blocks.rowBlockIds() != previousIds) {
            pendingFocusBeforeAdd = null
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
    val editingPropertyColumn = editingPropertyColumnId?.let { columnId ->
        table.columns.firstOrNull { column -> column.id == columnId }
    }
    if (editingPropertyColumn != null) {
        TableColumnEditSheet(
            currentTableBlockId = currentTableBlockId,
            table = table,
            column = editingPropertyColumn,
            tableReferences = tableReferences,
            onSort = { direction ->
                if (direction == null) {
                    onSortChange("", PageTableSortDirection.Ascending)
                } else {
                    onSortChange(editingPropertyColumn.id, direction)
                }
            },
            onFilter = { filter -> onFilterChange(filter.copy(columnId = editingPropertyColumn.id)) },
            onGroup = { onGroupChange(editingPropertyColumn.id) },
            onColumnNameChange = { name -> onColumnNameChange(editingPropertyColumn.id, name) },
            onColumnTypeChange = { type -> onColumnTypeChange(editingPropertyColumn.id, type) },
            onColumnConfigChange = { config -> onColumnConfigChange(editingPropertyColumn.id, config) },
            onDateSettingsChange = { dateFormat, timeFormat, reminder, timezoneLabel ->
                onColumnDateSettingsChange(editingPropertyColumn.id, dateFormat, timeFormat, reminder, timezoneLabel)
            },
            onFormulaChange = { formula -> onColumnFormulaChange(editingPropertyColumn.id, formula) },
            onRelationTargetChange = { targetTableId ->
                onColumnRelationTargetChange(editingPropertyColumn.id, targetTableId)
            },
            onRollupChange = { relationColumnId, targetColumnId, aggregation ->
                onColumnRollupChange(editingPropertyColumn.id, relationColumnId, targetColumnId, aggregation)
            },
            onDelete = {
                onDeleteColumn(editingPropertyColumn.id)
                editingPropertyColumnId = null
            },
            onInsertLeft = {
                onInsertColumn(editingPropertyColumn.id, TableColumnInsertSide.Left)
                editingPropertyColumnId = null
            },
            onInsertRight = {
                onInsertColumn(editingPropertyColumn.id, TableColumnInsertSide.Right)
                editingPropertyColumnId = null
            },
            onDuplicate = {
                onDuplicateColumn(editingPropertyColumn.id)
                editingPropertyColumnId = null
            },
            onDismiss = { editingPropertyColumnId = null },
        )
    } else if (editingPropertyColumnId != null) {
        editingPropertyColumnId = null
    }

    val showRowController = activeRowBlockId != null || isRowBodyFocused
    val rowContentBottomPadding = if (showRowController) 148.dp else 28.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = rowContentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowPageTitleEditor(
                    title = title,
                    enabled = titleColumn != null,
                    onFocusTitle = ::clearRowEditorFocus,
                    onTitleChange = { nextTitle ->
                        titleColumn?.let { column ->
                            onCellChange(row.id, column.id, nextTitle)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                DatabaseSyncStatusChip(
                    syncState = syncState,
                    isSaving = isSaving,
                    showDetailOnClick = false,
                )
                IconButton(
                    onClick = {
                        clearRowEditorFocus()
                        onDismiss()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close row",
                    )
                }
            }

            RowPagePropertyList(
                table = table,
                row = row,
                tableReferences = tableReferences,
                onAddProperty = {
                    clearRowEditorFocus()
                    isNewColumnSheetOpen = true
                },
                onEditProperty = { column ->
                    clearRowEditorFocus()
                    editingPropertyColumnId = column.id
                },
                onCellChange = { columnId, value -> onCellChange(row.id, columnId, value) },
                onRelationCellChange = { columnId, relationRowIds ->
                    onRelationCellChange(row.id, columnId, relationRowIds)
                },
                onColumnDateSettingsChange = onColumnDateSettingsChange,
                onFocusProperties = ::clearRowEditorFocus,
                currentPageId = currentPageId,
                onAddRelationTargetRow = onAddRelationTargetRow,
            )

            HorizontalDivider()

            if (row.blocks.isEmpty()) {
                RowContentBodyTapTarget(
                    height = 180.dp,
                    showPlaceholder = !isRowStarterPlaceholderDismissed,
                    onClick = ::focusOrCreateRowBodyTextBlock,
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSearchHighlighted) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)
                                } else {
                                    Color.Transparent
                                },
                            ),
                    ) {
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
                                onFocusBlock = {
                                    activeRowBlockId = block.id
                                    isRowBodyFocused = false
                                },
                                onRichTextToolbarChange = { toolbarState ->
                                    updateRowToolbarState(block.id, toolbarState)
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
                                    updateRowToolbarState(block.id, toolbarState)
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
                                onFocusBlock = {
                                    activeRowBlockId = block.id
                                    isRowBodyFocused = false
                                },
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
                                    updateRowToolbarState(block.id, toolbarState)
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
                                onFocusBlock = {
                                    activeRowBlockId = block.id
                                    isRowBodyFocused = false
                                },
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
                                    updateRowToolbarState(block.id, toolbarState)
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
                                onFocusBlock = {
                                    activeRowBlockId = block.id
                                    isRowBodyFocused = false
                                },
                                mentionPages = mentionPages,
                                isTableRowPage = true,
                            )
                            PageBlockType.Toggle -> LeadingTextBlockEditor(
                                blockId = block.id,
                                leadingText = ">",
                                block = block,
                                onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                onPasteBlocks = onBlockPasteBlocks,
                                onRichTextToolbarChange = { toolbarState ->
                                    updateRowToolbarState(block.id, toolbarState)
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
                                onFocusBlock = {
                                    activeRowBlockId = block.id
                                    isRowBodyFocused = false
                                },
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
                                    updateRowToolbarState(block.id, toolbarState)
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
                                onFocusBlock = {
                                    activeRowBlockId = block.id
                                    isRowBodyFocused = false
                                },
                                mentionPages = mentionPages,
                                isTableRowPage = true,
                            )
                            PageBlockType.Callout -> LeadingTextBlockEditor(
                                blockId = block.id,
                                leadingText = "!",
                                block = block,
                                onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                onPasteBlocks = onBlockPasteBlocks,
                                onRichTextToolbarChange = { toolbarState ->
                                    updateRowToolbarState(block.id, toolbarState)
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
                                onFocusBlock = {
                                    activeRowBlockId = block.id
                                    isRowBodyFocused = false
                                },
                                mentionPages = mentionPages,
                                isTableRowPage = true,
                            )
                            PageBlockType.WebBookmark -> LeadingTextBlockEditor(
                                blockId = block.id,
                                leadingText = "@",
                                block = block,
                                onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                onPasteBlocks = onBlockPasteBlocks,
                                onRichTextToolbarChange = { toolbarState ->
                                    updateRowToolbarState(block.id, toolbarState)
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
                                onFocusBlock = {
                                    activeRowBlockId = block.id
                                    isRowBodyFocused = false
                                },
                                mentionPages = mentionPages,
                                isTableRowPage = true,
                            )
                            PageBlockType.DatabaseTable,
                            PageBlockType.Table,
                            PageBlockType.Code,
                            PageBlockType.Heading,
                            PageBlockType.Text,
                            -> TextBlockEditor(
                                blockId = block.id,
                                block = block,
                                onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                onPasteBlocks = onBlockPasteBlocks,
                                onRichTextToolbarChange = { toolbarState ->
                                    updateRowToolbarState(block.id, toolbarState)
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
                                onFocusBlock = {
                                    activeRowBlockId = block.id
                                    isRowBodyFocused = false
                                },
                                mentionPages = mentionPages,
                                isTableRowPage = true,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

            if (showRowController) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .padding(bottom = if (isKeyboardVisible) 6.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PageKeyboardBlockToolbar(
                        activeBlockId = activeRowBlockId,
                        canUndoEditorChange = false,
                        richTextToolbarState = rowRichTextToolbarState,
                        allowPlainTable = false,
                        onAddBlock = ::addRowBlockAndFocus,
                        onChangeActiveBlockType = { type ->
                            activeRowBlockId?.let { blockId -> onBlockTypeChange(blockId, type) }
                                ?: addRowBlockAndFocus(type)
                        },
                        onAddChildToActiveBlock = { type ->
                            activeRowBlockId?.let { blockId ->
                                onInsertBlockNear(blockId, type, PageBlockInsertPosition.Below)
                            } ?: addRowBlockAndFocus(type)
                        },
                        onInsertTextAboveActiveBlock = {
                            activeRowBlockId?.let { blockId ->
                                onInsertBlockNear(blockId, PageBlockType.Text, PageBlockInsertPosition.Above)
                            }
                        },
                        onInsertTextBelowActiveBlock = {
                            activeRowBlockId?.let { blockId ->
                                onInsertBlockNear(blockId, PageBlockType.Text, PageBlockInsertPosition.Below)
                            }
                        },
                        onMoveActiveBlockUp = {
                            activeRowBlockId?.let(onMoveBlockUp)
                        },
                        onMoveActiveBlockDown = {
                            activeRowBlockId?.let(onMoveBlockDown)
                        },
                        onIndentActiveBlock = {
                            activeRowBlockId?.let(onIndentBlock)
                        },
                        onOutdentActiveBlock = {
                            activeRowBlockId?.let(onOutdentBlock)
                        },
                        onCreateLinkedPageFromActiveBlock = {
                            activeRowBlockId?.let(onCreateLinkedPage)
                        },
                        onDeleteActiveBlock = {
                            activeRowBlockId?.let(::deleteRowBlockAndFocusSibling)
                        },
                        onUndoEditorChange = {},
                        showActiveBlockActions = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowContentBodyTapTarget(
    height: androidx.compose.ui.unit.Dp,
    showPlaceholder: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        if (showPlaceholder) {
            Text(
                text = "Enter text",
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
            )
        }
    }
}

private fun List<PageBlock>.rowBlockIds(): Set<String> {
    return buildSet {
        fun walk(blocks: List<PageBlock>) {
            blocks.forEach { block ->
                add(block.id)
                walk(block.children)
            }
        }
        walk(this@rowBlockIds)
    }
}

private fun List<PageBlock>.firstNewFocusableRowBlockId(previousIds: Set<String>): String? {
    forEach { block ->
        if (block.id !in previousIds && block.type.isRowContentFocusable) {
            return block.id
        }
        block.children.firstNewFocusableRowBlockId(previousIds)?.let { childBlockId ->
            return childBlockId
        }
    }
    return null
}

private val PageBlockType.isRowContentFocusable: Boolean
    get() = when (this) {
        PageBlockType.Text,
        PageBlockType.Heading,
        PageBlockType.Todo,
        PageBlockType.Bullet,
        PageBlockType.Numbered,
        PageBlockType.Toggle,
        PageBlockType.Quote,
        PageBlockType.Callout,
        PageBlockType.Code,
        PageBlockType.WebBookmark,
        PageBlockType.MediaFile,
        -> true
        PageBlockType.Divider,
        PageBlockType.Table,
        PageBlockType.DatabaseTable,
        -> false
    }

