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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TableRowPageSheet(
    currentTableBlockId: String,
    table: PageTable,
    row: PageTableRow,
    tableReferences: List<PageTableReference>,
    searchTargetType: String = "",
    searchTargetId: String = "",
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (String, String) -> Unit,
    onGroupChange: (String) -> Unit,
    onColumnNameChange: (String, String) -> Unit,
    onColumnTypeChange: (String, PageTableColumnType) -> Unit,
    onCellChange: (String, String, String) -> Unit,
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
            onSort = {
                onSortChange(
                    editingPropertyColumn.id,
                    if (table.sort.columnId == editingPropertyColumn.id &&
                        table.sort.direction == PageTableSortDirection.Ascending
                    ) {
                        PageTableSortDirection.Descending
                    } else {
                        PageTableSortDirection.Ascending
                    },
                )
            },
            onFilter = { query -> onFilterChange(editingPropertyColumn.id, query) },
            onGroup = { onGroupChange(editingPropertyColumn.id) },
            onColumnNameChange = { name -> onColumnNameChange(editingPropertyColumn.id, name) },
            onColumnTypeChange = { type -> onColumnTypeChange(editingPropertyColumn.id, type) },
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
                onColumnDateSettingsChange = onColumnDateSettingsChange,
                onFocusProperties = ::clearRowEditorFocus,
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
                    rowRichTextToolbarState
                        ?.takeIf { state -> state.isValidForKeyboardToolbar() }
                        ?.let { toolbarState -> PageKeyboardRichTextToolbar(toolbarState) }

                    PageKeyboardBlockToolbar(
                        activeBlockId = activeRowBlockId,
                        canUndoEditorChange = false,
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
private fun RowPageTitleEditor(
    title: String,
    enabled: Boolean,
    onFocusTitle: () -> Unit,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = title,
        onValueChange = { nextTitle -> onTitleChange(nextTitle.toSingleLineTableCellValue()) },
        enabled = enabled,
        singleLine = true,
        modifier = modifier
            .heightIn(min = 50.dp)
            .padding(end = 10.dp)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusTitle()
            },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (title.isBlank()) {
                    Text(
                        text = "Untitled row",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                    )
                }
                innerTextField()
            }
        },
    )
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

@Composable
private fun RowPagePropertyList(
    table: PageTable,
    row: PageTableRow,
    tableReferences: List<PageTableReference>,
    onAddProperty: () -> Unit,
    onEditProperty: (PageTableColumn) -> Unit,
    onCellChange: (String, String) -> Unit,
    onColumnDateSettingsChange: (
        String,
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onFocusProperties: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFocusProperties,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Properties",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = onAddProperty,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add property",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (table.columns.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(onClick = onAddProperty),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Add property",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            table.columns.forEachIndexed { index, column ->
                RowPagePropertyItem(
                    table = table,
                    row = row,
                    column = column,
                    tableReferences = tableReferences,
                    onValueChange = { value -> onCellChange(column.id, value) },
                    onDateSettingsChange = { dateFormat, timeFormat, reminder, timezoneLabel ->
                        onColumnDateSettingsChange(column.id, dateFormat, timeFormat, reminder, timezoneLabel)
                    },
                    onEditProperty = { onEditProperty(column) },
                    onFocusProperties = onFocusProperties,
                )
                if (index < table.columns.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 32.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowPagePropertyItem(
    table: PageTable,
    row: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    onValueChange: (String) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onEditProperty: () -> Unit,
    onFocusProperties: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(143.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onEditProperty)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = column.type.icon,
                contentDescription = column.type.label,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            )
            Text(
                text = column.name.ifBlank { column.type.label },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowPagePropertyValueEditor(
            table = table,
            row = row,
            column = column,
            tableReferences = tableReferences,
            value = row.cells[column.id].orEmpty(),
            onValueChange = onValueChange,
            onDateSettingsChange = onDateSettingsChange,
            onFocusProperties = onFocusProperties,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowPagePropertyValueEditor(
    table: PageTable,
    row: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    value: String,
    onValueChange: (String) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onFocusProperties: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (column.type) {
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> RowPageReadOnlyPropertyValue(
            value = table.displayCellText(row, column, tableReferences),
            modifier = modifier,
        )
        PageTableColumnType.Relation -> RelationCellEditor(
            column = column,
            value = value,
            tableReferences = tableReferences,
            onValueChange = onValueChange,
            modifier = modifier,
        )
        PageTableColumnType.Checkbox -> RowPageCheckboxPropertyValue(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
        )
        PageTableColumnType.Date -> RowPageDatePropertyValue(
            column = column,
            value = value,
            onValueChange = onValueChange,
            onDateSettingsChange = onDateSettingsChange,
            onFocusProperties = onFocusProperties,
            modifier = modifier,
        )
        PageTableColumnType.Status -> RowPageStatusPropertyValue(
            value = value,
            onValueChange = onValueChange,
            onFocusProperties = onFocusProperties,
            modifier = modifier,
        )
        PageTableColumnType.FilesMedia -> TableMediaCellEditor(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
        )
        PageTableColumnType.Number,
        PageTableColumnType.Text,
        -> RowPagePlainPropertyValue(
            column = column,
            value = value,
            onValueChange = onValueChange,
            onFocusProperties = onFocusProperties,
            modifier = modifier,
        )
    }
}

@Composable
private fun RowPagePlainPropertyValue(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    onFocusProperties: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = { nextValue ->
            onFocusProperties()
            onValueChange(nextValue.toSingleLineTableCellValue())
        },
        modifier = modifier.height(52.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (column.type) {
                PageTableColumnType.Number -> KeyboardType.Number
                else -> KeyboardType.Text
            },
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isBlank()) {
                    Text(
                        text = when (column.type) {
                            PageTableColumnType.Number -> "0"
                            else -> "Empty"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun RowPageReadOnlyPropertyValue(
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(52.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value.ifBlank { "Empty" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun RowPageCheckboxPropertyValue(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clickable {
                onValueChange(if (value == CheckboxValueChecked) "" else CheckboxValueChecked)
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = value == CheckboxValueChecked,
            onCheckedChange = { checked -> onValueChange(if (checked) CheckboxValueChecked else "") },
        )
        Text(
            text = if (value == CheckboxValueChecked) "Checked" else "Empty",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == CheckboxValueChecked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowPageDatePropertyValue(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onFocusProperties: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSheetOpen by remember { mutableStateOf(false) }
    val displayText = column.displayDateCellValue(value)

    if (isSheetOpen) {
        TableDateEditorSheet(
            column = column,
            value = value,
            onValueChange = onValueChange,
            onDateSettingsChange = onDateSettingsChange,
            onDismiss = { isSheetOpen = false },
        )
    }

    RowPageClickablePropertyValue(
        text = displayText.ifBlank { "Empty" },
        isEmpty = displayText.isBlank(),
        modifier = modifier,
        onClick = {
            onFocusProperties()
            isSheetOpen = true
        },
    )
}

@Composable
private fun RowPageStatusPropertyValue(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusProperties: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        RowPageClickablePropertyValue(
            text = value.ifBlank { "Empty" },
            isEmpty = value.isBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onFocusProperties()
                isExpanded = true
            },
        )
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = "Clear") },
                onClick = {
                    isExpanded = false
                    onValueChange("")
                },
            )
            TableStatusOptions.forEach { status ->
                DropdownMenuItem(
                    text = { Text(text = status) },
                    onClick = {
                        isExpanded = false
                        onValueChange(status)
                    },
                )
            }
        }
    }
}

@Composable
private fun RowPageClickablePropertyValue(
    text: String,
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isEmpty) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
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
        PageBlockType.Quote,
        PageBlockType.MediaFile,
        -> true
        PageBlockType.Divider,
        PageBlockType.DatabaseTable,
        -> false
    }

