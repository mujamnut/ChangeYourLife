@file:OptIn(ExperimentalMaterial3Api::class)

package com.changeyourlife.cyl.presentation.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
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
import com.changeyourlife.cyl.domain.model.PageTableFilterOperator
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat
import com.changeyourlife.cyl.domain.model.PageTableOptionColor
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.isActive
import com.changeyourlife.cyl.presentation.ai.AiChatSheet
import com.changeyourlife.cyl.presentation.ai.AiChatMessage
import com.changeyourlife.cyl.presentation.ai.AiChatPageLink
import com.changeyourlife.cyl.presentation.components.CylBottomCommandBar
import com.changeyourlife.cyl.presentation.components.CylChromeIconButton
import com.changeyourlife.cyl.presentation.components.CylFloatingChromeSurface
import com.changeyourlife.cyl.presentation.home.HomeUiState
import com.changeyourlife.cyl.presentation.theme.ChangeYourLifeTheme
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
internal fun DatabaseTableBlockEditor(
    tableBlockId: String,
    pageId: String,
    pageUpdatedAt: Long,
    syncState: PageSyncState,
    isSaving: Boolean,
    table: PageTable,
    tableReferences: List<PageTableReference>,
    onTitleChange: (String) -> Unit,
    onViewChange: (PageTableView) -> Unit,
    onViewConfigChange: (PageTableViewConfig) -> Unit,
    onDataSourceChange: (PageTableReference?) -> Unit,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (PageTableFilter) -> Unit,
    onGroupChange: (String) -> Unit,
    onColumnNameChange: (String, String) -> Unit,
    onColumnTypeChange: (String, PageTableColumnType) -> Unit,
    onColumnConfigChange: (String, PageTableColumnConfig) -> Unit,
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
    onCellChange: (String, String, String) -> Unit,
    onRelationCellChange: (String, String, List<String>) -> Unit,
    onAddRelationTargetRow: (String) -> Unit,
    onAddColumn: (String, PageTableColumnType) -> Unit,
    onInsertColumn: (String, TableColumnInsertSide) -> Unit,
    onDuplicateColumn: (String) -> Unit,
    onDeleteColumn: (String) -> Unit,
    onAddRow: () -> Unit,
    onDeleteRow: (String) -> Unit,
    onDuplicateRow: (String) -> Unit,
    onMoveRow: (String, Int) -> Unit,
    onRowBlockTextChange: (String, String, String) -> Unit,
    onRowBlockRichTextChange: (String, String, String, List<PageTextSpan>) -> Unit,
    onRowBlockPasteBlocks: (String, String, List<RichTextPasteBlock>) -> Unit,
    onRowBlockTypeChange: (String, String, PageBlockType) -> Unit,
    onRowBlockMediaAdd: (String, String, List<PageMediaAttachment>) -> Unit,
    onRowBlockMediaRemove: (String, String, String) -> Unit,
    onToggleRowTodoBlock: (String, String) -> Unit,
    onAddRowPageBlock: (String, PageBlockType) -> Unit,
    onInsertRowPageBlockNear: (String, String, PageBlockType, PageBlockInsertPosition) -> Unit,
    onCreateRowLinkedPage: (String, String) -> Unit,
    onDeleteRowPageBlock: (String, String) -> Unit,
    onMoveRowPageBlockUp: (String, String) -> Unit,
    onMoveRowPageBlockDown: (String, String) -> Unit,
    onIndentRowPageBlock: (String, String) -> Unit,
    onOutdentRowPageBlock: (String, String) -> Unit,
    mentionPages: List<Page> = emptyList(),
    searchTargetType: String = "",
    searchTargetId: String = "",
) {
    val horizontalScrollState = rememberScrollState()
    var openRowId by remember { mutableStateOf<String?>(null) }
    var tableSearchInput by rememberSaveable(tableBlockId) { mutableStateOf("") }
    var tableSearchQuery by rememberSaveable(tableBlockId) { mutableStateOf("") }
    val openRow = table.rows.firstOrNull { row -> row.id == openRowId }
    val highlightedRowId = remember(table.rows, searchTargetType, searchTargetId) {
        table.highlightedRowId(searchTargetType, searchTargetId)
    }

    LaunchedEffect(tableSearchInput) {
        delay(120)
        tableSearchQuery = tableSearchInput.trim()
    }

    LaunchedEffect(table.rows, openRowId) {
        val currentOpenRowId = openRowId
        if (currentOpenRowId != null && table.rows.none { row -> row.id == currentOpenRowId }) {
            openRowId = null
        }
    }

    LaunchedEffect(searchTargetType, searchTargetId, highlightedRowId) {
        if (searchTargetType == SearchTargetRowBlock && highlightedRowId != null) {
            openRowId = highlightedRowId
        }
    }

    if (openRow != null) {
        TableRowPageSheet(
            currentTableBlockId = tableBlockId,
            currentPageId = pageId,
            table = table,
            row = openRow,
            syncState = syncState,
            isSaving = isSaving,
            tableReferences = tableReferences,
            searchTargetType = searchTargetType,
            searchTargetId = searchTargetId,
            onSortChange = onSortChange,
            onFilterChange = onFilterChange,
            onGroupChange = onGroupChange,
            onColumnNameChange = onColumnNameChange,
            onColumnTypeChange = onColumnTypeChange,
            onColumnConfigChange = onColumnConfigChange,
            onCellChange = onCellChange,
            onRelationCellChange = onRelationCellChange,
            onAddRelationTargetRow = onAddRelationTargetRow,
            onAddColumn = onAddColumn,
            onInsertColumn = onInsertColumn,
            onDuplicateColumn = onDuplicateColumn,
            onDeleteColumn = onDeleteColumn,
            onColumnDateSettingsChange = onColumnDateSettingsChange,
            onColumnFormulaChange = onColumnFormulaChange,
            onColumnRelationTargetChange = onColumnRelationTargetChange,
            onColumnRollupChange = onColumnRollupChange,
            onAddRow = onAddRow,
            onBlockTextChange = { rowBlockId, text -> onRowBlockTextChange(openRow.id, rowBlockId, text) },
            onBlockRichTextChange = { rowBlockId, text, spans ->
                onRowBlockRichTextChange(openRow.id, rowBlockId, text, spans)
            },
            onBlockPasteBlocks = { rowBlockId, pasteBlocks ->
                onRowBlockPasteBlocks(openRow.id, rowBlockId, pasteBlocks)
            },
            onBlockTypeChange = { rowBlockId, type ->
                onRowBlockTypeChange(openRow.id, rowBlockId, type)
            },
            onBlockMediaAdd = { rowBlockId, attachments ->
                onRowBlockMediaAdd(openRow.id, rowBlockId, attachments)
            },
            onBlockMediaRemove = { rowBlockId, attachmentId ->
                onRowBlockMediaRemove(openRow.id, rowBlockId, attachmentId)
            },
            onToggleTodo = { rowBlockId -> onToggleRowTodoBlock(openRow.id, rowBlockId) },
            onAddBlock = { type -> onAddRowPageBlock(openRow.id, type) },
            onInsertBlockNear = { rowBlockId, type, position ->
                onInsertRowPageBlockNear(openRow.id, rowBlockId, type, position)
            },
            onCreateLinkedPage = { rowBlockId -> onCreateRowLinkedPage(openRow.id, rowBlockId) },
            onDeleteBlock = { rowBlockId -> onDeleteRowPageBlock(openRow.id, rowBlockId) },
            onMoveBlockUp = { rowBlockId -> onMoveRowPageBlockUp(openRow.id, rowBlockId) },
            onMoveBlockDown = { rowBlockId -> onMoveRowPageBlockDown(openRow.id, rowBlockId) },
            onIndentBlock = { rowBlockId -> onIndentRowPageBlock(openRow.id, rowBlockId) },
            onOutdentBlock = { rowBlockId -> onOutdentRowPageBlock(openRow.id, rowBlockId) },
            mentionPages = mentionPages,
            onDismiss = { openRowId = null },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TableToolbar(
            table = table,
            tableBlockId = tableBlockId,
            syncState = syncState,
            isSaving = isSaving,
            tableReferences = tableReferences,
            onTitleChange = onTitleChange,
            onViewChange = onViewChange,
            onViewConfigChange = onViewConfigChange,
            onDataSourceChange = onDataSourceChange,
            onColumnConfigChange = onColumnConfigChange,
            onSortChange = onSortChange,
            onFilterChange = onFilterChange,
            onGroupChange = onGroupChange,
            searchQuery = tableSearchInput,
            onSearchQueryChange = { tableSearchInput = it },
        )
        TableActiveControlsRow(
            table = table,
            searchQuery = tableSearchInput,
            onClearSort = { onSortChange("", PageTableSortDirection.Ascending) },
            onClearFilter = { onFilterChange(PageTableFilter()) },
            onClearGroup = { onGroupChange("") },
            onClearSearch = {
                tableSearchInput = ""
                tableSearchQuery = ""
            },
            onClearAll = {
                tableSearchInput = ""
                tableSearchQuery = ""
                onSortChange("", PageTableSortDirection.Ascending)
                onFilterChange(PageTableFilter())
                onGroupChange("")
            },
        )

        when (table.view) {
            PageTableView.Table -> TableGridEditor(
                tableBlockId = tableBlockId,
                table = table,
                tableReferences = tableReferences,
                horizontalScrollState = horizontalScrollState,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onGroupChange = onGroupChange,
                onColumnNameChange = onColumnNameChange,
                onColumnTypeChange = onColumnTypeChange,
                onColumnConfigChange = onColumnConfigChange,
                onColumnDateSettingsChange = onColumnDateSettingsChange,
                onColumnFormulaChange = onColumnFormulaChange,
                onColumnRelationTargetChange = onColumnRelationTargetChange,
                onColumnRollupChange = onColumnRollupChange,
                onCellChange = onCellChange,
                onRelationCellChange = onRelationCellChange,
                onAddRelationTargetRow = onAddRelationTargetRow,
                onDeleteColumn = onDeleteColumn,
                onAddColumn = onAddColumn,
                onInsertColumn = onInsertColumn,
                onDuplicateColumn = onDuplicateColumn,
                onDeleteRow = onDeleteRow,
                onDuplicateRow = onDuplicateRow,
                onMoveRow = onMoveRow,
                onAddRow = onAddRow,
                onOpenRow = { rowId -> openRowId = rowId },
                highlightedRowId = highlightedRowId,
                searchQuery = tableSearchQuery,
                pageId = pageId,
                pageUpdatedAt = pageUpdatedAt,
            )
            PageTableView.List -> TableListView(
                table = table,
                tableReferences = tableReferences,
                searchQuery = tableSearchQuery,
            )
            PageTableView.Board -> TableBoardView(
                table = table,
                tableReferences = tableReferences,
                searchQuery = tableSearchQuery,
            )
            PageTableView.Calendar -> TableCalendarView(
                table = table,
                tableReferences = tableReferences,
                searchQuery = tableSearchQuery,
            )
            PageTableView.Gallery -> TableGalleryView(
                table = table,
                tableReferences = tableReferences,
                searchQuery = tableSearchQuery,
            )
            PageTableView.Timeline -> TableTimelineView(
                table = table,
                tableReferences = tableReferences,
                searchQuery = tableSearchQuery,
            )
            PageTableView.Dashboard -> TableDashboardView(
                table = table,
                tableReferences = tableReferences,
                searchQuery = tableSearchQuery,
            )
        }
    }
}

@Composable
internal fun TableToolbar(
    table: PageTable,
    tableBlockId: String,
    syncState: PageSyncState,
    isSaving: Boolean,
    tableReferences: List<PageTableReference>,
    onTitleChange: (String) -> Unit,
    onViewChange: (PageTableView) -> Unit,
    onViewConfigChange: (PageTableViewConfig) -> Unit,
    onDataSourceChange: (PageTableReference?) -> Unit,
    onColumnConfigChange: (String, PageTableColumnConfig) -> Unit,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (PageTableFilter) -> Unit,
    onGroupChange: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
) {
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    val showSearch = isSearchOpen || searchQuery.isNotBlank()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSearch) {
                TableSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = {
                        onSearchQueryChange("")
                        isSearchOpen = false
                    },
                    onEmptyBlur = { isSearchOpen = false },
                    modifier = Modifier.weight(1f),
                )
            } else {
                TableViewSelector(
                    table = table,
                    tableBlockId = tableBlockId,
                    tableReferences = tableReferences,
                    onTitleChange = onTitleChange,
                    onViewChange = onViewChange,
                    onViewConfigChange = onViewConfigChange,
                    onDataSourceChange = onDataSourceChange,
                )
                DatabaseSyncStatusChip(
                    syncState = syncState,
                    isSaving = isSaving,
                )
                Spacer(modifier = Modifier.weight(1f))
                TableControlIconButton(
                    icon = Icons.Rounded.Search,
                    selected = searchQuery.isNotBlank(),
                    contentDescription = "Search database",
                    onClick = { isSearchOpen = true },
                )
            }
            TableControls(
                table = table,
                onViewConfigChange = onViewConfigChange,
                onColumnConfigChange = onColumnConfigChange,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onGroupChange = onGroupChange,
            )
        }
    }
}

@Composable
internal fun DatabaseSyncStatusChip(
    syncState: PageSyncState,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
    showDetailOnClick: Boolean = true,
) {
    var isSheetOpen by remember { mutableStateOf(false) }
    val statusColor = syncState.databaseSyncColor(isSaving)
    val backgroundColor = if (syncState.hasConflict || isSaving || syncState.isPendingPush) {
        statusColor.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f)
    }
    val clickModifier = if (showDetailOnClick) {
        Modifier.clickable { isSheetOpen = true }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .height(32.dp)
            .widthIn(max = 108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .then(clickModifier)
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.8.dp,
                color = statusColor,
            )
        } else {
            Icon(
                imageVector = syncState.databaseSyncIcon(),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = statusColor,
            )
        }
        Text(
            text = syncState.databaseSyncLabel(isSaving),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (isSheetOpen) {
        ModalBottomSheet(onDismissRequest = { isSheetOpen = false }) {
            DatabaseSyncStatusSheet(
                syncState = syncState,
                isSaving = isSaving,
            )
        }
    }
}

@Composable
private fun DatabaseSyncStatusSheet(
    syncState: PageSyncState,
    isSaving: Boolean,
) {
    val statusColor = syncState.databaseSyncColor(isSaving)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Database sync",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        ListItem(
            headlineContent = { Text(text = syncState.databaseSyncTitle(isSaving)) },
            supportingContent = { Text(text = syncState.databaseSyncDetail(isSaving)) },
            leadingContent = {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = statusColor,
                    )
                } else {
                    Icon(
                        imageVector = syncState.databaseSyncIcon(),
                        contentDescription = null,
                        tint = statusColor,
                    )
                }
            },
        )
        Text(
            text = "Database rows and properties are saved locally first, then uploaded by CYL sync in the background.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PageSyncState.databaseSyncColor(isSaving: Boolean) = when {
    hasConflict -> MaterialTheme.colorScheme.error
    isSaving || isPendingPush || lastSyncedAt == 0L -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun PageSyncState.databaseSyncIcon(): ImageVector {
    return when {
        hasConflict -> Icons.Rounded.Info
        isPendingPush || lastSyncedAt == 0L -> Icons.Rounded.Notifications
        else -> Icons.Rounded.CheckCircle
    }
}

private fun PageSyncState.databaseSyncLabel(isSaving: Boolean): String {
    return when {
        hasConflict -> "Conflict"
        isSaving -> "Saving"
        isPendingPush -> "Queued"
        lastSyncedAt == 0L -> "Not synced"
        else -> "Saved"
    }
}

private fun PageSyncState.databaseSyncTitle(isSaving: Boolean): String {
    return when {
        hasConflict -> "Database has a sync conflict"
        isSaving -> "Saving database"
        isPendingPush -> "Waiting to upload"
        lastSyncedAt == 0L -> "Not synced yet"
        else -> "Database saved"
    }
}

private fun PageSyncState.databaseSyncDetail(isSaving: Boolean): String {
    return when {
        hasConflict -> "This page/database changed locally and remotely. Resolve the page sync conflict before continuing heavy edits."
        isSaving -> "CYL is writing your latest database change to local storage."
        isPendingPush -> "Your change is safe on this device and will upload when sync can reach the server."
        lastSyncedAt == 0L -> "This database has local data but has not completed its first server sync."
        else -> "All local database changes are saved and synced."
    }
}

@Composable
internal fun TableSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onEmptyBlur: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var hasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(42.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    hasFocused = true
                } else if (hasFocused && query.isBlank()) {
                    onEmptyBlur()
                }
            },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                if (query.isBlank()) {
                    onEmptyBlur()
                }
            },
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                    .padding(start = 13.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isBlank()) {
                        Text(
                            text = "Search database",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close search",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
internal fun TableViewSelector(
    table: PageTable,
    tableBlockId: String,
    tableReferences: List<PageTableReference>,
    onTitleChange: (String) -> Unit,
    onViewChange: (PageTableView) -> Unit,
    onViewConfigChange: (PageTableViewConfig) -> Unit,
    onDataSourceChange: (PageTableReference?) -> Unit,
) {
    var isSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedOption = TableViewOption.entries.firstOrNull { option -> option.view == table.view }
        ?: TableViewOption.entries.first()
    val displayTitle = table.title.databaseTitleOrPlaceholder()

    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .clickable { isSheetOpen = true }
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = selectedOption.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = displayTitle,
            modifier = Modifier.widthIn(max = 196.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }

    if (isSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isSheetOpen = false },
            sheetState = sheetState,
        ) {
            DatabaseViewSheet(
                table = table,
                tableBlockId = tableBlockId,
                tableReferences = tableReferences,
                onTitleChange = onTitleChange,
                onViewChange = onViewChange,
                onViewConfigChange = onViewConfigChange,
                onDataSourceChange = onDataSourceChange,
                onDismiss = { isSheetOpen = false },
            )
        }
    }
}

@Composable
internal fun DatabaseViewSheet(
    table: PageTable,
    tableBlockId: String,
    tableReferences: List<PageTableReference>,
    onTitleChange: (String) -> Unit,
    onViewChange: (PageTableView) -> Unit,
    onViewConfigChange: (PageTableViewConfig) -> Unit,
    onDataSourceChange: (PageTableReference?) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Database",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        DatabaseNameEditor(
            tableTitle = table.title,
            onTitleChange = onTitleChange,
        )
        DatabaseNewViewCard(
            selectedView = table.view,
            onViewChange = { view ->
                onViewChange(view)
            },
        )
        DatabaseViewSetupCard(
            table = table,
            onViewConfigChange = onViewConfigChange,
        )
        DatabaseDataSourceCard(
            tableBlockId = tableBlockId,
            viewConfig = table.viewConfig,
            tableReferences = tableReferences,
            onDataSourceChange = onDataSourceChange,
            onDismiss = onDismiss,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun DatabaseNameEditor(
    tableTitle: String,
    onTitleChange: (String) -> Unit,
) {
    val editableTitle = tableTitle.databaseEditableTitle()
    BasicTextField(
        value = editableTitle,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (editableTitle.isBlank()) {
                        Text(
                            text = DefaultDatabaseTitlePlaceholder,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

private const val DefaultDatabaseTitlePlaceholder = "Table"
private const val LegacyUntitledDatabaseTitle = "Untitled database"

private fun String.databaseTitleOrPlaceholder(): String {
    return databaseEditableTitle().ifBlank { DefaultDatabaseTitlePlaceholder }
}

private fun String.databaseEditableTitle(): String {
    return takeUnless { title -> title.isBlank() || title == LegacyUntitledDatabaseTitle }.orEmpty()
}

@Composable
internal fun DatabaseNewViewCard(
    selectedView: PageTableView,
    onViewChange: (PageTableView) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.68f))
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = "New view",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Choose how this database is displayed.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TableViewOption.entries) { option ->
                DatabaseViewChip(
                    option = option,
                    selected = option.view == selectedView,
                    onClick = { onViewChange(option.view) },
                )
            }
        }
    }
}

@Composable
internal fun DatabaseViewChip(
    option: TableViewOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                },
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
internal fun DatabaseViewSetupCard(
    table: PageTable,
    onViewConfigChange: (PageTableViewConfig) -> Unit,
) {
    val selectedOption = TableViewOption.entries.firstOrNull { option -> option.view == table.view }
        ?: TableViewOption.entries.first()
    val needsSetup = when (table.view) {
        PageTableView.Calendar,
        PageTableView.Timeline,
        PageTableView.Dashboard,
        -> true
        PageTableView.Table,
        PageTableView.List,
        PageTableView.Board,
        PageTableView.Gallery,
        -> false
    }
    if (!needsSetup) return

    val setupMessage = when {
        table.columns.isEmpty() -> "Add properties before configuring this view."
        (table.view == PageTableView.Calendar || table.view == PageTableView.Timeline) &&
            table.dateCandidateColumns().isEmpty() -> "Add a Date property to power this view."
        table.view == PageTableView.Dashboard &&
            table.metricCandidateColumns().isEmpty() -> "Add a Number property to use dashboard metrics."
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.68f))
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = selectedOption.icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${selectedOption.label} setup",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = table.viewSetupDescription(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        if (setupMessage.isNotBlank()) {
            DatabaseViewSetupHint(message = setupMessage)
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TableViewConfigControls(
                    table = table,
                    onViewConfigChange = onViewConfigChange,
                )
            }
        }
    }
}

@Composable
private fun DatabaseViewSetupHint(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun PageTable.viewSetupDescription(): String = when (view) {
    PageTableView.Calendar -> "Choose which Date property places rows on the calendar."
    PageTableView.Timeline -> "Choose start and optional end Date properties."
    PageTableView.Dashboard -> "Choose the metric and grouping used by widgets."
    PageTableView.Table,
    PageTableView.List,
    PageTableView.Board,
    PageTableView.Gallery,
    -> "This view does not need extra setup."
}

@Composable
internal fun DatabaseDataSourceCard(
    tableBlockId: String,
    viewConfig: PageTableViewConfig,
    tableReferences: List<PageTableReference>,
    onDataSourceChange: (PageTableReference?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sourceTables = remember(tableBlockId, tableReferences) {
        tableReferences
            .filter { reference -> reference.blockId != tableBlockId }
            .sortedWith(
                compareBy<PageTableReference> { reference -> reference.pageTitle.ifBlank { "Current page" } }
                    .thenBy { reference -> reference.title.databaseTitleOrPlaceholder() },
            )
    }
    val activeSource = sourceTables.firstOrNull { reference ->
        reference.blockId == viewConfig.dataSourceTableBlockId &&
            (viewConfig.dataSourcePageId.isBlank() || reference.pageId == viewConfig.dataSourcePageId)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.68f))
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New data source",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Use an existing database as this view's source.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (activeSource != null) {
                TextButton(
                    onClick = {
                        onDataSourceChange(null)
                        onDismiss()
                    },
                ) {
                    Text(text = "Clear")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        if (sourceTables.isEmpty()) {
            DatabaseDataSourceEmptyRow()
        } else {
            sourceTables.take(6).forEach { reference ->
                DatabaseDataSourceRow(
                    reference = reference,
                    selected = reference == activeSource,
                    onClick = {
                        onDataSourceChange(reference)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
internal fun DatabaseDataSourceEmptyRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No other database found yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DatabaseDataSourceRow(
    reference: PageTableReference,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                },
            )
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ViewColumn,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reference.title.databaseTitleOrPlaceholder(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reference.pageTitle.ifBlank { "Current page" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (selected) "Connected" else "${reference.table.columns.size} properties",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

internal val TableViewOption.icon: ImageVector
    get() = when (view) {
        PageTableView.Table -> Icons.Rounded.ViewColumn
        PageTableView.List -> Icons.AutoMirrored.Rounded.Article
        PageTableView.Board -> Icons.Rounded.TaskAlt
        PageTableView.Calendar -> Icons.Rounded.CalendarMonth
        PageTableView.Gallery -> Icons.Rounded.Public
        PageTableView.Timeline -> Icons.Rounded.AccessTime
        PageTableView.Dashboard -> Icons.Rounded.Calculate
    }

@Composable
internal fun TableViewConfigControls(
    table: PageTable,
    onViewConfigChange: (PageTableViewConfig) -> Unit,
) {
    if (table.columns.isEmpty()) return

    when (table.view) {
        PageTableView.Calendar -> {
            val dateColumns = table.dateCandidateColumns()
            if (dateColumns.isEmpty()) return
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Calendar date field",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TableColumnChoiceRow(
                    columns = dateColumns,
                    selectedColumnId = table.viewConfig.calendarDateColumnId.ifBlank {
                        table.dateColumn()?.id.orEmpty()
                    },
                    onSelect = { column ->
                        onViewConfigChange(table.viewConfig.copy(calendarDateColumnId = column.id))
                    },
                )
            }
        }
        PageTableView.Timeline -> {
            val dateColumns = table.dateCandidateColumns()
            if (dateColumns.isEmpty()) return
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Timeline start field",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TableColumnChoiceRow(
                    columns = dateColumns,
                    selectedColumnId = table.viewConfig.timelineStartColumnId.ifBlank {
                        table.dateColumn()?.id.orEmpty()
                    },
                    onSelect = { column ->
                        onViewConfigChange(table.viewConfig.copy(timelineStartColumnId = column.id))
                    },
                )

                Text(
                    text = "Timeline end field",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = table.viewConfig.timelineEndColumnId.isBlank(),
                            onClick = {
                                onViewConfigChange(table.viewConfig.copy(timelineEndColumnId = ""))
                            },
                            label = { Text(text = "No end") },
                        )
                    }
                    items(dateColumns, key = { column -> column.id }) { column ->
                        FilterChip(
                            selected = table.viewConfig.timelineEndColumnId == column.id,
                            onClick = {
                                onViewConfigChange(table.viewConfig.copy(timelineEndColumnId = column.id))
                            },
                            label = { Text(text = column.name.ifBlank { "Untitled" }.compactControlLabel()) },
                        )
                    }
                }
            }
        }
        PageTableView.Dashboard -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Dashboard metric",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TableColumnChoiceRow(
                    columns = table.metricCandidateColumns(),
                    selectedColumnId = table.viewConfig.dashboardMetricColumnId.ifBlank {
                        table.metricCandidateColumns().firstOrNull()?.id.orEmpty()
                    },
                    onSelect = { column ->
                        onViewConfigChange(table.viewConfig.copy(dashboardMetricColumnId = column.id))
                    },
                )

                Text(
                    text = "Dashboard group",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TableColumnChoiceRow(
                    columns = table.columns,
                    selectedColumnId = table.viewConfig.dashboardGroupColumnId.ifBlank {
                        table.statusColumn()?.id.orEmpty()
                    },
                    onSelect = { column ->
                        onViewConfigChange(table.viewConfig.copy(dashboardGroupColumnId = column.id))
                    },
                )
            }
        }
        PageTableView.Table,
        PageTableView.List,
        PageTableView.Board,
        PageTableView.Gallery,
        -> Unit
    }
}

private fun PageTable.hasViewSpecificConfig(): Boolean = when (view) {
    PageTableView.Calendar,
    PageTableView.Timeline,
    -> dateCandidateColumns().isNotEmpty()
    PageTableView.Dashboard -> columns.isNotEmpty()
    PageTableView.Table,
    PageTableView.List,
    PageTableView.Board,
    PageTableView.Gallery,
    -> false
}

@Composable
internal fun TableControls(
    table: PageTable,
    onViewConfigChange: (PageTableViewConfig) -> Unit,
    onColumnConfigChange: (String, PageTableColumnConfig) -> Unit,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (PageTableFilter) -> Unit,
    onGroupChange: (String) -> Unit,
) {
    if (table.columns.isEmpty()) return

    var isSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortColumn = table.columns.firstOrNull { column -> column.id == table.sort.columnId }
    val filterColumn = table.columns.firstOrNull { column -> column.id == table.filter.columnId }
    val groupColumn = table.columns.firstOrNull { column -> column.id == table.groupByColumnId }
    val hasActiveControls = sortColumn != null ||
        (filterColumn != null && table.filter.isActive()) ||
        groupColumn != null

    TableControlIconButton(
        icon = Icons.Rounded.Tune,
        selected = hasActiveControls,
        showBadge = hasActiveControls,
        contentDescription = "Database controls",
        onClick = { isSheetOpen = true },
    )

    if (isSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isSheetOpen = false },
            sheetState = sheetState,
        ) {
            TableControlsSheet(
                table = table,
                onViewConfigChange = onViewConfigChange,
                onColumnConfigChange = onColumnConfigChange,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onGroupChange = onGroupChange,
            )
        }
    }
}

@Composable
internal fun TableControlIconButton(
    icon: ImageVector,
    selected: Boolean,
    showBadge: Boolean = false,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
internal fun TableControlsSheet(
    table: PageTable,
    onViewConfigChange: (PageTableViewConfig) -> Unit,
    onColumnConfigChange: (String, PageTableColumnConfig) -> Unit,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (PageTableFilter) -> Unit,
    onGroupChange: (String) -> Unit,
) {
    val canClear = table.sort.columnId.isNotBlank() ||
        table.filter.isActive() ||
        table.groupByColumnId.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TableControlSheetHeader(
            title = "Database controls",
            canClear = canClear,
            onClear = {
                onSortChange("", PageTableSortDirection.Ascending)
                onFilterChange(PageTableFilter())
                onGroupChange("")
            },
        )

        if (table.hasViewSpecificConfig()) {
            TableControlSection(title = "View setup") {
                TableViewConfigControls(
                    table = table,
                    onViewConfigChange = onViewConfigChange,
                )
            }
        }

        val hiddenColumns = table.columns.filter { column -> column.config.isHidden }
        if (hiddenColumns.isNotEmpty()) {
            TableControlSection(title = "Hidden properties") {
                hiddenColumns.forEach { column ->
                    HiddenPropertyControlRow(
                        column = column,
                        onUnhide = {
                            onColumnConfigChange(
                                column.id,
                                column.config.copy(isHidden = false),
                            )
                        },
                    )
                }
            }
        }

        TableControlSection(title = "Sort") {
            CompactTableSortControls(
                table = table,
                onSortChange = onSortChange,
            )
        }

        TableControlSection(title = "Filter") {
            CompactTableFilterControls(
                table = table,
                onFilterChange = onFilterChange,
            )
        }

        TableControlSection(title = "Group") {
            CompactTableGroupControls(
                table = table,
                onGroupChange = onGroupChange,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TableControlSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun HiddenPropertyControlRow(
    column: PageTableColumn,
    onUnhide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(start = 10.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = column.type.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = column.name.ifBlank { column.type.label },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onUnhide) {
            Text(text = "Show")
        }
    }
}

@Composable
private fun CompactTableSortControls(
    table: PageTable,
    onSortChange: (String, PageTableSortDirection) -> Unit,
) {
    val selectedColumnId = table.sort.columnId.ifBlank { table.columns.firstOrNull()?.id.orEmpty() }
    val selectedDirection = table.sort.direction

    TableColumnChoiceRow(
        columns = table.columns,
        selectedColumnId = selectedColumnId,
        onSelect = { column -> onSortChange(column.id, selectedDirection) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = table.sort.columnId.isNotBlank() &&
                selectedDirection == PageTableSortDirection.Ascending,
            onClick = { onSortChange(selectedColumnId, PageTableSortDirection.Ascending) },
            label = { Text(text = "Asc") },
        )
        FilterChip(
            selected = table.sort.columnId.isNotBlank() &&
                selectedDirection == PageTableSortDirection.Descending,
            onClick = { onSortChange(selectedColumnId, PageTableSortDirection.Descending) },
            label = { Text(text = "Desc") },
        )
        if (table.sort.columnId.isNotBlank()) {
            FilterChip(
                selected = false,
                onClick = { onSortChange("", PageTableSortDirection.Ascending) },
                label = { Text(text = "Clear") },
            )
        }
    }
}

@Composable
private fun CompactTableFilterControls(
    table: PageTable,
    onFilterChange: (PageTableFilter) -> Unit,
) {
    val firstColumnId = table.columns.firstOrNull()?.id.orEmpty()
    var selectedColumnId by remember(table.filter.columnId, table.columns) {
        mutableStateOf(table.filter.columnId.ifBlank { firstColumnId })
    }
    var query by remember(table.filter.query) { mutableStateOf(table.filter.query) }
    val selectedColumn = table.columns.firstOrNull { column -> column.id == selectedColumnId }
    var operator by remember(table.filter.operator, table.filter.columnId, selectedColumnId) {
        mutableStateOf(
            if (table.filter.columnId == selectedColumnId) {
                table.filter.operator
            } else {
                selectedColumn?.defaultFilterOperator() ?: PageTableFilterOperator.Contains
            },
        )
    }
    val availableOperators = selectedColumn?.filterOperators().orEmpty()
    val requiresQuery = operator.requiresQuery()

    TableColumnChoiceRow(
        columns = table.columns,
        selectedColumnId = selectedColumnId,
        onSelect = { column ->
            selectedColumnId = column.id
            operator = column.defaultFilterOperator()
            query = ""
        },
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            items = availableOperators,
            key = { item -> item.name },
        ) { item ->
            FilterChip(
                selected = item == operator,
                onClick = { operator = item },
                label = { Text(text = item.label) },
            )
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { nextQuery ->
            query = nextQuery
            if (nextQuery.isBlank() && requiresQuery) {
                onFilterChange(PageTableFilter())
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = requiresQuery,
        singleLine = true,
        placeholder = { Text(text = operator.placeholderFor(selectedColumn)) },
        colors = blockTextFieldColors(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = table.filter.isActive(),
            enabled = selectedColumnId.isNotBlank() && (!requiresQuery || query.isNotBlank()),
            onClick = {
                onFilterChange(
                    PageTableFilter(
                        columnId = selectedColumnId,
                        query = query,
                        operator = operator,
                    ),
                )
            },
            label = { Text(text = "Apply") },
        )
        if (table.filter.isActive()) {
            FilterChip(
                selected = false,
                onClick = {
                    query = ""
                    selectedColumnId = firstColumnId
                    operator = table.columns.firstOrNull()?.defaultFilterOperator() ?: PageTableFilterOperator.Contains
                    onFilterChange(PageTableFilter())
                },
                label = { Text(text = "Clear") },
            )
        }
    }
}

@Composable
private fun CompactTableGroupControls(
    table: PageTable,
    onGroupChange: (String) -> Unit,
) {
    TableColumnChoiceRow(
        columns = table.columns,
        selectedColumnId = table.groupByColumnId,
        onSelect = { column -> onGroupChange(column.id) },
    )
    if (table.groupByColumnId.isNotBlank()) {
        FilterChip(
            selected = false,
            onClick = { onGroupChange("") },
            label = { Text(text = "Clear") },
        )
    }
}

private fun PageTableColumn.defaultFilterOperator(): PageTableFilterOperator = when (type) {
    PageTableColumnType.Number,
    PageTableColumnType.Formula,
    PageTableColumnType.Rollup,
    -> PageTableFilterOperator.Equals
    PageTableColumnType.Date -> PageTableFilterOperator.Equals
    PageTableColumnType.Checkbox,
    PageTableColumnType.Select,
    PageTableColumnType.MultiSelect,
    PageTableColumnType.Status,
    -> PageTableFilterOperator.Equals
    PageTableColumnType.Text,
    PageTableColumnType.FilesMedia,
    PageTableColumnType.Relation,
    -> PageTableFilterOperator.Contains
}

private fun PageTableColumn.filterOperators(): List<PageTableFilterOperator> = when (type) {
    PageTableColumnType.Number,
    PageTableColumnType.Formula,
    PageTableColumnType.Rollup,
    -> listOf(
        PageTableFilterOperator.Equals,
        PageTableFilterOperator.NotEquals,
        PageTableFilterOperator.GreaterThan,
        PageTableFilterOperator.LessThan,
        PageTableFilterOperator.IsEmpty,
        PageTableFilterOperator.IsNotEmpty,
    )
    PageTableColumnType.Date -> listOf(
        PageTableFilterOperator.Equals,
        PageTableFilterOperator.Before,
        PageTableFilterOperator.After,
        PageTableFilterOperator.OnOrBefore,
        PageTableFilterOperator.OnOrAfter,
        PageTableFilterOperator.IsEmpty,
        PageTableFilterOperator.IsNotEmpty,
    )
    PageTableColumnType.Checkbox -> listOf(
        PageTableFilterOperator.Equals,
        PageTableFilterOperator.NotEquals,
    )
    PageTableColumnType.Select,
    PageTableColumnType.MultiSelect,
    PageTableColumnType.Status,
    -> listOf(
        PageTableFilterOperator.Equals,
        PageTableFilterOperator.NotEquals,
        PageTableFilterOperator.Contains,
        PageTableFilterOperator.IsEmpty,
        PageTableFilterOperator.IsNotEmpty,
    )
    PageTableColumnType.Text,
    PageTableColumnType.FilesMedia,
    PageTableColumnType.Relation,
    -> listOf(
        PageTableFilterOperator.Contains,
        PageTableFilterOperator.Equals,
        PageTableFilterOperator.NotEquals,
        PageTableFilterOperator.IsEmpty,
        PageTableFilterOperator.IsNotEmpty,
    )
}

private fun PageTableFilterOperator.requiresQuery(): Boolean {
    return this != PageTableFilterOperator.IsEmpty &&
        this != PageTableFilterOperator.IsNotEmpty
}

private val PageTableFilterOperator.label: String
    get() = when (this) {
        PageTableFilterOperator.Contains -> "Contains"
        PageTableFilterOperator.Equals -> "Is"
        PageTableFilterOperator.NotEquals -> "Is not"
        PageTableFilterOperator.IsEmpty -> "Empty"
        PageTableFilterOperator.IsNotEmpty -> "Not empty"
        PageTableFilterOperator.GreaterThan -> ">"
        PageTableFilterOperator.LessThan -> "<"
        PageTableFilterOperator.Before -> "Before"
        PageTableFilterOperator.After -> "After"
        PageTableFilterOperator.OnOrBefore -> "On/before"
        PageTableFilterOperator.OnOrAfter -> "On/after"
    }

private fun PageTableFilterOperator.placeholderFor(column: PageTableColumn?): String = when {
    !requiresQuery() -> "No value needed"
    column?.type == PageTableColumnType.Number -> "Number"
    column?.type == PageTableColumnType.Date -> "Date"
    column?.type == PageTableColumnType.Checkbox -> "Done or empty"
    else -> "Value"
}

private fun PageTableFilter.labelForColumn(column: PageTableColumn): String {
    val name = column.name.ifBlank { "Untitled" }
    return if (operator.requiresQuery()) {
        "$name ${operator.label.lowercase(Locale.US)} $query"
    } else {
        "$name ${operator.label.lowercase(Locale.US)}"
    }
}

@Composable
internal fun TableActiveControlsRow(
    table: PageTable,
    searchQuery: String,
    onClearSort: () -> Unit,
    onClearFilter: () -> Unit,
    onClearGroup: () -> Unit,
    onClearSearch: () -> Unit,
    onClearAll: () -> Unit,
) {
    val sortColumn = table.columns.firstOrNull { column -> column.id == table.sort.columnId }
    val filterColumn = table.columns.firstOrNull { column -> column.id == table.filter.columnId }
    val groupColumn = table.columns.firstOrNull { column -> column.id == table.groupByColumnId }
    val activeControls = buildList {
        if (sortColumn != null) {
            add(
                TableActiveControlUi(
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    label = "${sortColumn.name.ifBlank { "Untitled" }} ${table.sort.direction.arrowLabel}",
                    onClear = onClearSort,
                ),
            )
        }
        if (filterColumn != null && table.filter.isActive()) {
            add(
                TableActiveControlUi(
                    icon = Icons.Rounded.FilterList,
                    label = table.filter.labelForColumn(filterColumn),
                    onClear = onClearFilter,
                ),
            )
        }
        if (groupColumn != null) {
            add(
                TableActiveControlUi(
                    icon = Icons.Rounded.ViewColumn,
                    label = groupColumn.name.ifBlank { "Untitled" },
                    onClear = onClearGroup,
                ),
            )
        }
        if (searchQuery.isNotBlank()) {
            add(
                TableActiveControlUi(
                    icon = Icons.Rounded.Search,
                    label = searchQuery,
                    onClear = onClearSearch,
                ),
            )
        }
    }
    if (activeControls.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(
            items = activeControls,
            key = { control -> control.label },
        ) { control ->
            TableActiveControlChip(control = control)
        }
        if (activeControls.size > 1) {
            item(key = "clear-all") {
                TableClearAllControlChip(onClick = onClearAll)
            }
        }
    }
}

private data class TableActiveControlUi(
    val icon: ImageVector,
    val label: String,
    val onClear: () -> Unit,
)

@Composable
private fun TableActiveControlChip(control: TableActiveControlUi) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f))
            .padding(start = 9.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = control.icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = control.label.compactControlLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = control.onClear,
            modifier = Modifier.size(26.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Clear ${control.label}",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
            )
        }
    }
}

@Composable
private fun TableClearAllControlChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Clear all",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
internal fun TableControlSheetHeader(
    title: String,
    canClear: Boolean,
    onClear: () -> Unit,
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
        if (canClear) {
            TextButton(onClick = onClear) {
                Text(text = "Clear all")
            }
        }
    }
}

@Composable
internal fun TableColumnChoiceRow(
    columns: List<PageTableColumn>,
    selectedColumnId: String,
    onSelect: (PageTableColumn) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            items = columns,
            key = { column -> column.id },
        ) { column ->
            FilterChip(
                selected = column.id == selectedColumnId,
                onClick = { onSelect(column) },
                label = { Text(text = column.name.ifBlank { "Untitled" }.compactControlLabel()) },
            )
        }
    }
}

@Composable
internal fun TableGridEditor(
    tableBlockId: String,
    table: PageTable,
    tableReferences: List<PageTableReference>,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (PageTableFilter) -> Unit,
    onGroupChange: (String) -> Unit,
    onColumnNameChange: (String, String) -> Unit,
    onColumnTypeChange: (String, PageTableColumnType) -> Unit,
    onColumnConfigChange: (String, PageTableColumnConfig) -> Unit,
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
    onCellChange: (String, String, String) -> Unit,
    onRelationCellChange: (String, String, List<String>) -> Unit,
    onAddRelationTargetRow: (String) -> Unit,
    onDeleteColumn: (String) -> Unit,
    onAddColumn: (String, PageTableColumnType) -> Unit,
    onInsertColumn: (String, TableColumnInsertSide) -> Unit,
    onDuplicateColumn: (String) -> Unit,
    onDeleteRow: (String) -> Unit,
    onDuplicateRow: (String) -> Unit,
    onMoveRow: (String, Int) -> Unit,
    onAddRow: () -> Unit,
    onOpenRow: (String) -> Unit,
    highlightedRowId: String? = null,
    searchQuery: String = "",
    pageId: String,
    pageUpdatedAt: Long,
) {
    val visibleColumns = remember(table.columns) {
        table.columns.filterNot { column -> column.config.isHidden }
    }
    val columnWidths = remember(table.columns, table.rows.size, tableReferences) {
        table.tableColumnWidths(
            tableReferences = tableReferences,
            rowSampleLimit = TableWidthMeasurementRowLimit,
        )
    }
    val visibleRows = remember(table.rows, table.columns, table.filter, table.sort, searchQuery, tableReferences) {
        table.visibleRows(
            tableReferences = tableReferences,
            searchQuery = searchQuery,
        )
    }
    val groupColumn = remember(table.columns, table.groupByColumnId) {
        table.groupColumn()
    }
    val rowIndexById = remember(table.rows) {
        table.rows.mapIndexed { index, row -> row.id to index }.toMap()
    }
    val isStarterEmptyDatabase = remember(table.columns, table.rows.size, table.sort, table.filter, table.groupByColumnId, searchQuery) {
        table.isStarterEmptyDatabase(searchQuery)
    }
    val groupedRows = remember(visibleRows, groupColumn, tableReferences) {
        groupColumn?.let { column ->
            visibleRows.groupBy { row -> table.groupLabel(row, column, tableReferences) }.toList()
        }.orEmpty()
    }
    val useLazyRows = visibleRows.size >= TableLargeDatasetRowThreshold

    Column(
        modifier = Modifier.horizontalScroll(horizontalScrollState),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        TableHeaderRow(
            tableBlockId = tableBlockId,
            table = table,
            columns = visibleColumns,
            columnWidths = columnWidths,
            tableReferences = tableReferences,
            onSortChange = onSortChange,
            onFilterChange = onFilterChange,
            onGroupChange = onGroupChange,
            onColumnNameChange = onColumnNameChange,
            onColumnTypeChange = onColumnTypeChange,
            onColumnConfigChange = onColumnConfigChange,
            onColumnDateSettingsChange = onColumnDateSettingsChange,
            onColumnFormulaChange = onColumnFormulaChange,
            onColumnRelationTargetChange = onColumnRelationTargetChange,
            onColumnRollupChange = onColumnRollupChange,
            onDeleteColumn = onDeleteColumn,
            onAddColumn = onAddColumn,
            onInsertColumn = onInsertColumn,
            onDuplicateColumn = onDuplicateColumn,
        )
        HorizontalDivider()
        if (isStarterEmptyDatabase) {
            TableStarterEmptyState(
                columns = visibleColumns,
                columnWidths = columnWidths,
                onAddRow = onAddRow,
            )
        } else if (visibleRows.isEmpty()) {
            TableAddRowRow(
                columns = visibleColumns,
                columnWidths = columnWidths,
                onAddRow = onAddRow,
            )
        } else if (groupColumn != null) {
            if (useLazyRows) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = TableLargeDatasetBodyMaxHeight),
                ) {
                    groupedRows.forEach { (group, rows) ->
                        item(
                            key = "group:$group",
                            contentType = "table-group-header",
                        ) {
                            TableGroupHeader(label = group, count = rows.size)
                        }
                        items(
                            items = rows,
                            key = { row -> row.id },
                            contentType = { "table-row" },
                        ) { row ->
                            TableDataRow(
                                row = row,
                                rowIndex = rowIndexById[row.id] ?: -1,
                                totalRows = table.rows.size,
                                pageId = pageId,
                                pageUpdatedAt = pageUpdatedAt,
                                table = table,
                                columns = visibleColumns,
                                columnWidths = columnWidths,
                                tableReferences = tableReferences,
                                onColumnDateSettingsChange = onColumnDateSettingsChange,
                                onCellChange = onCellChange,
                                onRelationCellChange = onRelationCellChange,
                                onAddRelationTargetRow = onAddRelationTargetRow,
                                onDeleteRow = onDeleteRow,
                                onDuplicateRow = onDuplicateRow,
                                onMoveRow = onMoveRow,
                                onOpenRow = onOpenRow,
                                isHighlighted = row.id == highlightedRowId,
                            )
                        }
                    }
                    item(
                        key = "add-row",
                        contentType = "table-add-row",
                    ) {
                        TableAddRowRow(
                            columns = visibleColumns,
                            columnWidths = columnWidths,
                            onAddRow = onAddRow,
                        )
                    }
                }
            } else {
                groupedRows.forEach { (group, rows) ->
                    TableGroupHeader(label = group, count = rows.size)
                    rows.forEach { row ->
                        key(row.id) {
                            TableDataRow(
                                row = row,
                                rowIndex = rowIndexById[row.id] ?: -1,
                                totalRows = table.rows.size,
                                pageId = pageId,
                                pageUpdatedAt = pageUpdatedAt,
                                table = table,
                                columns = visibleColumns,
                                columnWidths = columnWidths,
                                tableReferences = tableReferences,
                                onColumnDateSettingsChange = onColumnDateSettingsChange,
                                onCellChange = onCellChange,
                                onRelationCellChange = onRelationCellChange,
                                onAddRelationTargetRow = onAddRelationTargetRow,
                                onDeleteRow = onDeleteRow,
                                onDuplicateRow = onDuplicateRow,
                                onMoveRow = onMoveRow,
                                onOpenRow = onOpenRow,
                                isHighlighted = row.id == highlightedRowId,
                            )
                        }
                    }
                }
                TableAddRowRow(
                    columns = visibleColumns,
                    columnWidths = columnWidths,
                    onAddRow = onAddRow,
                )
            }
        } else {
            if (useLazyRows) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = TableLargeDatasetBodyMaxHeight),
                ) {
                    items(
                        items = visibleRows,
                        key = { row -> row.id },
                        contentType = { "table-row" },
                    ) { row ->
                        TableDataRow(
                            row = row,
                            rowIndex = rowIndexById[row.id] ?: -1,
                            totalRows = table.rows.size,
                            pageId = pageId,
                            pageUpdatedAt = pageUpdatedAt,
                            table = table,
                            columns = visibleColumns,
                            columnWidths = columnWidths,
                            tableReferences = tableReferences,
                            onColumnDateSettingsChange = onColumnDateSettingsChange,
                            onCellChange = onCellChange,
                            onRelationCellChange = onRelationCellChange,
                            onAddRelationTargetRow = onAddRelationTargetRow,
                            onDeleteRow = onDeleteRow,
                            onDuplicateRow = onDuplicateRow,
                            onMoveRow = onMoveRow,
                            onOpenRow = onOpenRow,
                            isHighlighted = row.id == highlightedRowId,
                        )
                    }
                    item(
                        key = "add-row",
                        contentType = "table-add-row",
                    ) {
                        TableAddRowRow(
                            columns = visibleColumns,
                            columnWidths = columnWidths,
                            onAddRow = onAddRow,
                        )
                    }
                }
            } else {
                visibleRows.forEach { row ->
                    key(row.id) {
                        TableDataRow(
                            row = row,
                            rowIndex = rowIndexById[row.id] ?: -1,
                            totalRows = table.rows.size,
                            pageId = pageId,
                            pageUpdatedAt = pageUpdatedAt,
                            table = table,
                            columns = visibleColumns,
                            columnWidths = columnWidths,
                            tableReferences = tableReferences,
                            onColumnDateSettingsChange = onColumnDateSettingsChange,
                            onCellChange = onCellChange,
                            onRelationCellChange = onRelationCellChange,
                            onAddRelationTargetRow = onAddRelationTargetRow,
                            onDeleteRow = onDeleteRow,
                            onDuplicateRow = onDuplicateRow,
                            onMoveRow = onMoveRow,
                            onOpenRow = onOpenRow,
                            isHighlighted = row.id == highlightedRowId,
                        )
                    }
                }
                TableAddRowRow(
                    columns = visibleColumns,
                    columnWidths = columnWidths,
                    onAddRow = onAddRow,
                )
            }
        }
    }
}

@Composable
internal fun TableHeaderRow(
    tableBlockId: String,
    table: PageTable,
    columns: List<PageTableColumn>,
    columnWidths: Map<String, Dp>,
    tableReferences: List<PageTableReference>,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (PageTableFilter) -> Unit,
    onGroupChange: (String) -> Unit,
    onColumnNameChange: (String, String) -> Unit,
    onColumnTypeChange: (String, PageTableColumnType) -> Unit,
    onColumnConfigChange: (String, PageTableColumnConfig) -> Unit,
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
    onDeleteColumn: (String) -> Unit,
    onAddColumn: (String, PageTableColumnType) -> Unit,
    onInsertColumn: (String, TableColumnInsertSide) -> Unit,
    onDuplicateColumn: (String) -> Unit,
) {
    @Composable
    fun HeaderColumn(column: PageTableColumn) {
        var isColumnSheetOpen by remember(column.id) { mutableStateOf(false) }
        TableHeaderCell(
            column = column,
            width = columnWidths[column.id] ?: TableCellWidth,
            isFiltered = table.filter.columnId == column.id && table.filter.isActive(),
            sortDirection = table.sort.direction.takeIf { table.sort.columnId == column.id },
            isGrouped = table.groupByColumnId == column.id,
            onClick = { isColumnSheetOpen = true },
        )
        if (isColumnSheetOpen) {
            TableColumnEditSheet(
                currentTableBlockId = tableBlockId,
                table = table,
                column = column,
                tableReferences = tableReferences,
                onSort = { direction ->
                    if (direction == null) {
                        onSortChange("", PageTableSortDirection.Ascending)
                    } else {
                        onSortChange(column.id, direction)
                    }
                },
                onFilter = { filter ->
                    onFilterChange(filter.copy(columnId = column.id))
                },
                onGroup = { onGroupChange(column.id) },
                onColumnNameChange = { name -> onColumnNameChange(column.id, name) },
                onColumnTypeChange = { type -> onColumnTypeChange(column.id, type) },
                onColumnConfigChange = { config -> onColumnConfigChange(column.id, config) },
                onDateSettingsChange = { dateFormat, timeFormat, reminder, timezoneLabel ->
                    onColumnDateSettingsChange(column.id, dateFormat, timeFormat, reminder, timezoneLabel)
                },
                onFormulaChange = { formula -> onColumnFormulaChange(column.id, formula) },
                onRelationTargetChange = { targetTableId -> onColumnRelationTargetChange(column.id, targetTableId) },
                onRollupChange = { relationColumnId, targetColumnId, aggregation ->
                    onColumnRollupChange(column.id, relationColumnId, targetColumnId, aggregation)
                },
                onDelete = {
                    onDeleteColumn(column.id)
                    isColumnSheetOpen = false
                },
                onInsertLeft = {
                    onInsertColumn(column.id, TableColumnInsertSide.Left)
                    isColumnSheetOpen = false
                },
                onInsertRight = {
                    onInsertColumn(column.id, TableColumnInsertSide.Right)
                    isColumnSheetOpen = false
                },
                onDuplicate = {
                    onDuplicateColumn(column.id)
                    isColumnSheetOpen = false
                },
                onDismiss = { isColumnSheetOpen = false },
            )
        }
    }

    val primaryColumn = columns.firstOrNull()
    val remainingColumns = columns.drop(1)

    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        if (primaryColumn != null) {
            HeaderColumn(primaryColumn)
        }
        remainingColumns.forEach { column ->
            HeaderColumn(column)
        }
        TableAddColumnCell(onAddColumn = onAddColumn)
    }
}

@Composable
internal fun TableStarterEmptyState(
    columns: List<PageTableColumn>,
    columnWidths: Map<String, Dp>,
    onAddRow: () -> Unit,
) {
    val firstColumnWidth = columns.firstOrNull()?.let { column -> columnWidths[column.id] } ?: TableCellWidth
    Row(
        modifier = Modifier
            .width(firstColumnWidth + TableAddColumnWidth)
            .height(96.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ViewColumn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                )
                Text(
                    text = "No items",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TableStarterAction(
                    icon = Icons.Rounded.Add,
                    label = "New item",
                    onClick = onAddRow,
                )
            }
        }
        Box(
            modifier = Modifier
                .width(TableAddColumnWidth)
                .height(96.dp)
                .background(MaterialTheme.colorScheme.surface),
        )
    }
    HorizontalDivider()
}

@Composable
private fun TableStarterAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun TableHeaderCell(
    column: PageTableColumn,
    width: Dp,
    isFiltered: Boolean,
    sortDirection: PageTableSortDirection?,
    isGrouped: Boolean,
    onClick: () -> Unit,
) {
    val tableColors = TableGridTokens.colors()
    val hasActiveViewRule = isFiltered || sortDirection != null || isGrouped
    Row(
        modifier = Modifier
            .width(width)
            .height(TableHeaderHeight)
            .clickable(onClick = onClick)
            .background(tableColors.headerBackground)
            .padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = column.type.icon,
            contentDescription = column.type.label,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
        )
        Text(
            text = column.name.ifBlank { "Untitled" },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (column.config.isRequired) {
            Text(
                text = "*",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
            )
        }
        if (hasActiveViewRule) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
internal fun TableAddColumnCell(
    onAddColumn: (String, PageTableColumnType) -> Unit,
) {
    var isNewColumnSheetOpen by remember { mutableStateOf(false) }
    val tableColors = TableGridTokens.colors()
    if (isNewColumnSheetOpen) {
        NewTableColumnSheet(
            onCreateColumn = { name, type ->
                onAddColumn(name, type)
                isNewColumnSheetOpen = false
            },
            onDismiss = { isNewColumnSheetOpen = false },
        )
    }
    Box(
        modifier = Modifier
            .width(TableAddColumnWidth)
            .height(TableHeaderHeight)
            .clickable { isNewColumnSheetOpen = true }
            .background(tableColors.headerBackground)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Add column",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
@Composable
internal fun NewTableColumnSheet(
    onCreateColumn: (String, PageTableColumnType) -> Unit,
    onDismiss: () -> Unit,
) {
    var propertyName by rememberSaveable { mutableStateOf("") }

    fun createColumn(type: PageTableColumnType) {
        val name = propertyName.trim().ifBlank { type.label }
        onCreateColumn(name, type)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "New property",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = propertyName,
                onValueChange = { propertyName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(text = "Property name") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = blockTextFieldColors(),
            )

            Text(
                text = "Type",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                PageTableColumnType.entries.forEachIndexed { index, type ->
                    TablePropertyTypeRow(
                        type = type,
                        onClick = { createColumn(type) },
                    )
                    if (index < PageTableColumnType.entries.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TablePropertyTypeRow(
    type: PageTableColumnType,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = type.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableColumnEditSheet(
    currentTableBlockId: String,
    table: PageTable,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    onSort: (PageTableSortDirection?) -> Unit,
    onFilter: (PageTableFilter) -> Unit,
    onGroup: () -> Unit,
    onColumnNameChange: (String) -> Unit,
    onColumnTypeChange: (PageTableColumnType) -> Unit,
    onColumnConfigChange: (PageTableColumnConfig) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onFormulaChange: (String) -> Unit,
    onRelationTargetChange: (String) -> Unit,
    onRollupChange: (String, String, PageTableRollupAggregation) -> Unit,
    onDelete: () -> Unit,
    onInsertLeft: () -> Unit,
    onInsertRight: () -> Unit,
    onDuplicate: () -> Unit,
    onDismiss: () -> Unit,
) {
    var detail by remember { mutableStateOf<PropertySheetDetail?>(null) }
    var isDeleteConfirmOpen by remember { mutableStateOf(false) }
    var filterQuery by remember(table.filter.query, table.filter.columnId, column.id) {
        mutableStateOf(if (table.filter.columnId == column.id) table.filter.query else "")
    }
    var filterOperator by remember(table.filter.operator, table.filter.columnId, column.id) {
        mutableStateOf(
            if (table.filter.columnId == column.id) {
                table.filter.operator
            } else {
                column.defaultFilterOperator()
            },
        )
    }
    val hasColumnValues = remember(table.rows, column.id) {
        table.rows.any { row -> row.cellText(column).isNotBlank() }
    }
    val isPrimaryColumn = remember(table.columns, column.id) {
        table.columns.firstOrNull()?.id == column.id
    }
    val canDeleteColumn = table.columns.size > 1 && !isPrimaryColumn

    if (isDeleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteConfirmOpen = false },
            title = { Text(text = "Delete property?") },
            text = {
                Text(
                    text = if (hasColumnValues) {
                        "This property has values. Deleting it will remove those values from every row."
                    } else {
                        "This property will be removed from the database."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteConfirmOpen = false
                        onDelete()
                    },
                ) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmOpen = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (detail) {
                null -> {
                    PropertySheetTitle(title = "Property")
                    PropertyNameEditor(
                        column = column,
                        onColumnNameChange = onColumnNameChange,
                    )
                    PropertyTypeSummary(column = column)

                    PropertyMenuGroup {
                        PropertyMenuRow(
                            icon = Icons.Rounded.SwapVert,
                            label = "Change type",
                            value = column.type.label,
                            onClick = { detail = PropertySheetDetail.ChangeType },
                        )
                        PropertyMenuRow(
                            icon = Icons.Rounded.Info,
                            label = "Details",
                            value = column.config.description.ifBlank {
                                column.config.databaseLayoutSummary().ifBlank {
                                    column.config.defaultValue.ifBlank { "" }
                                }
                            },
                            onClick = { detail = PropertySheetDetail.General },
                        )
                        when (column.type) {
                            PageTableColumnType.Date -> PropertyMenuRow(
                                icon = Icons.Rounded.CalendarMonth,
                                label = "Date settings",
                                value = column.dateFormat.label,
                                onClick = { detail = PropertySheetDetail.DateSettings },
                            )
                            PageTableColumnType.Select,
                            PageTableColumnType.MultiSelect,
                            PageTableColumnType.Status,
                            -> PropertyMenuRow(
                                icon = Icons.Rounded.TaskAlt,
                                label = "Options",
                                value = "${column.choiceOptions.size}",
                                onClick = { detail = PropertySheetDetail.StatusOptions },
                            )
                            PageTableColumnType.Formula,
                            PageTableColumnType.Relation,
                            PageTableColumnType.Rollup,
                            -> PropertyMenuRow(
                                icon = Icons.Rounded.Tune,
                                label = column.type.label,
                                value = column.configSummary(table, tableReferences),
                                onClick = { detail = PropertySheetDetail.Calculate },
                            )
                            PageTableColumnType.Text,
                            PageTableColumnType.Number,
                            PageTableColumnType.Checkbox,
                            PageTableColumnType.FilesMedia,
                            -> Unit
                        }
                    }

                    PropertyMenuGroup {
                        PropertyMenuRow(
                            icon = Icons.Rounded.FilterList,
                            label = "Filter",
                            value = if (table.filter.columnId == column.id) {
                                table.filter.labelForColumn(column)
                            } else {
                                ""
                            },
                            onClick = { detail = PropertySheetDetail.Filter },
                        )
                        PropertyMenuRow(
                            icon = Icons.AutoMirrored.Rounded.Sort,
                            label = "Sort",
                            value = if (table.sort.columnId == column.id) table.sort.direction.arrowLabel else "",
                            onClick = { detail = PropertySheetDetail.Sort },
                        )
                        PropertyMenuRow(
                            icon = Icons.Rounded.ViewColumn,
                            label = "Group",
                            value = if (table.groupByColumnId == column.id) "Active" else "",
                            onClick = {
                                onGroup()
                                onDismiss()
                            },
                        )
                    }

                    PropertyMenuGroup {
                        PropertyMenuRow(icon = Icons.AutoMirrored.Rounded.ArrowBack, label = "Insert left", onClick = onInsertLeft)
                        PropertyMenuRow(icon = Icons.AutoMirrored.Rounded.ArrowForward, label = "Insert right", onClick = onInsertRight)
                        PropertyMenuRow(icon = Icons.Rounded.ContentCopy, label = "Duplicate property", onClick = onDuplicate)
                    }

                    PropertyMenuGroup {
                        PropertyMenuRow(
                            icon = Icons.Rounded.Delete,
                            label = "Delete property",
                            value = if (isPrimaryColumn) "Primary" else "",
                            color = MaterialTheme.colorScheme.error,
                            enabled = canDeleteColumn,
                            onClick = {
                                if (hasColumnValues) {
                                    isDeleteConfirmOpen = true
                                } else {
                                    onDelete()
                                }
                            },
                        )
                    }
                }
                PropertySheetDetail.General -> PropertyGeneralSettingsSheet(
                    column = column,
                    onColumnConfigChange = onColumnConfigChange,
                    onBack = { detail = null },
                )
                PropertySheetDetail.ChangeType -> ChangePropertyTypeSheet(
                    selectedType = column.type,
                    onTypeChange = { type ->
                        onColumnTypeChange(type)
                        detail = null
                    },
                    onBack = { detail = null },
                )
                PropertySheetDetail.DateSettings -> TableDatePropertySettingsSheet(
                    column = column,
                    onDateSettingsChange = onDateSettingsChange,
                    onBack = { detail = null },
                )
                PropertySheetDetail.StatusOptions -> ChoicePropertyOptionsSheet(
                    column = column,
                    onColumnConfigChange = onColumnConfigChange,
                    onBack = { detail = null },
                )
                PropertySheetDetail.Filter -> ColumnFilterSheet(
                    column = column,
                    query = filterQuery,
                    operator = filterOperator,
                    onOperatorChange = { operator -> filterOperator = operator },
                    onQueryChange = { filterQuery = it },
                    onApply = {
                        onFilter(
                            PageTableFilter(
                                columnId = column.id,
                                query = filterQuery,
                                operator = filterOperator,
                            ),
                        )
                        onDismiss()
                    },
                    onClear = {
                        filterQuery = ""
                        onFilter(PageTableFilter())
                        onDismiss()
                    },
                    onBack = { detail = null },
                )
                PropertySheetDetail.Sort -> ColumnSortSheet(
                    column = column,
                    table = table,
                    onSortChange = { direction ->
                        onSort(direction)
                        onDismiss()
                    },
                    onBack = { detail = null },
                )
                PropertySheetDetail.Calculate -> ColumnCalculateSheet(
                    currentTableBlockId = currentTableBlockId,
                    table = table,
                    column = column,
                    tableReferences = tableReferences,
                    onFormulaChange = onFormulaChange,
                    onRelationTargetChange = onRelationTargetChange,
                    onRollupChange = onRollupChange,
                    onBack = { detail = null },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

internal enum class PropertySheetDetail {
    General,
    ChangeType,
    DateSettings,
    StatusOptions,
    Filter,
    Sort,
    Calculate,
}

@Composable
internal fun PropertySheetTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun PropertyNameEditor(
    column: PageTableColumn,
    onColumnNameChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 10.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = column.type.icon,
                contentDescription = column.type.label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        BasicTextField(
            value = column.name,
            onValueChange = onColumnNameChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (column.name.isBlank()) {
                        Text(
                            text = column.type.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Text(
            text = column.type.label,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        )
    }
}

@Composable
internal fun PropertyTypeSummary(column: PageTableColumn) {
    val summary = column.configSummaryForHeader()
    if (summary.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        )
        Text(
            text = summary,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun PageTableColumn.configSummaryForHeader(): String {
    val layoutSummary = config.databaseLayoutSummary()
    val typeSummary = when (type) {
        PageTableColumnType.Date -> "${dateFormat.label} · ${timeFormat.label} · ${dateReminder.label}"
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
        PageTableColumnType.Status,
        -> "${choiceOptions.size} options"
        PageTableColumnType.Formula -> formula.ifBlank { "No formula yet" }
        PageTableColumnType.Relation -> if (relationTargetTableId.isBlank()) "No target database" else "Linked database"
        PageTableColumnType.Rollup -> rollupAggregation.name
        PageTableColumnType.Text,
        PageTableColumnType.Number,
        PageTableColumnType.Checkbox,
        PageTableColumnType.FilesMedia,
        -> config.description
    }
    return listOf(typeSummary, layoutSummary)
        .filter { summary -> summary.isNotBlank() }
        .joinToString(" · ")
}

private fun PageTableColumnConfig.databaseLayoutSummary(): String {
    return buildList {
        if (isRequired) add("Required")
        if (isHidden) add("Hidden")
        if (wrapContent) add("Wrap")
        if (widthDp > 0) add("${widthDp}dp")
    }.joinToString(" · ")
}

@Composable
internal fun PropertyMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
internal fun PropertyMenuRow(
    icon: ImageVector,
    label: String,
    value: String = "",
    color: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val rowColor = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(start = 10.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = rowColor.copy(alpha = 0.82f),
                    modifier = Modifier.size(19.dp),
                )
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = rowColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    modifier = Modifier.widthIn(max = 136.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.78f else 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (enabled) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = -90f),
                )
            } else {
                Spacer(modifier = Modifier.width(18.dp))
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 54.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
        )
    }
}

@Composable
internal fun PropertyDetailHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
internal fun PropertyGeneralSettingsSheet(
    column: PageTableColumn,
    onColumnConfigChange: (PageTableColumnConfig) -> Unit,
    onBack: () -> Unit,
) {
    var description by remember(column.config.description) {
        mutableStateOf(column.config.description)
    }
    var defaultValue by remember(column.config.defaultValue) {
        mutableStateOf(column.config.defaultValue)
    }
    var isHidden by remember(column.config.isHidden) {
        mutableStateOf(column.config.isHidden)
    }
    var isRequired by remember(column.config.isRequired) {
        mutableStateOf(column.config.isRequired)
    }
    var wrapContent by remember(column.config.wrapContent) {
        mutableStateOf(column.config.wrapContent)
    }
    var widthText by remember(column.config.widthDp) {
        mutableStateOf(column.config.widthDp.takeIf { width -> width > 0 }?.toString().orEmpty())
    }
    val canEditDefault = column.type != PageTableColumnType.Formula &&
        column.type != PageTableColumnType.Rollup &&
        column.type != PageTableColumnType.Relation &&
        column.type != PageTableColumnType.FilesMedia

    fun save() {
        onColumnConfigChange(
            column.config.copy(
                description = description.trim(),
                isHidden = isHidden,
                isRequired = isRequired,
                wrapContent = wrapContent,
                widthDp = widthText.toIntOrNull()
                    ?.coerceIn(TableColumnMinWidthDp, TableColumnMaxWidthDp)
                    ?: 0,
                defaultValue = if (canEditDefault) {
                    defaultValue.toSingleLineTableCellValue().trim()
                } else {
                    ""
                },
            ),
        )
        onBack()
    }

    PropertyDetailHeader(title = "Details", onBack = onBack)
    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4,
        label = { Text(text = "Description") },
        placeholder = { Text(text = "What this property is for") },
        colors = blockTextFieldColors(),
    )
    OutlinedTextField(
        value = defaultValue,
        onValueChange = { defaultValue = it.toSingleLineTableCellValue() },
        modifier = Modifier.fillMaxWidth(),
        enabled = canEditDefault,
        singleLine = true,
        label = { Text(text = "Default value") },
        placeholder = {
            Text(
                text = when (column.type) {
                    PageTableColumnType.Number -> "0"
                    PageTableColumnType.Checkbox -> "true"
                    PageTableColumnType.Date -> "YYYY-MM-DD"
                    PageTableColumnType.Select,
                    PageTableColumnType.MultiSelect,
                    PageTableColumnType.Status,
                    -> column.choiceOptionNames.firstOrNull().orEmpty()
                    PageTableColumnType.Text -> "Empty"
                    PageTableColumnType.Formula,
                    PageTableColumnType.Relation,
                    PageTableColumnType.Rollup,
                    PageTableColumnType.FilesMedia,
                    -> "Not available"
                },
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = when (column.type) {
                PageTableColumnType.Number -> KeyboardType.Number
                else -> KeyboardType.Text
            },
            imeAction = ImeAction.Done,
        ),
        colors = blockTextFieldColors(),
        supportingText = if (canEditDefault) {
            {
                Text(text = "Used when new rows are created.")
            }
        } else {
            {
                Text(text = "This property type is computed or attachment-based.")
            }
        },
    )
    PropertyMenuGroup {
        PropertyToggleSettingRow(
            label = "Required",
            description = "Mark empty values clearly without blocking autosave.",
            checked = isRequired,
            onCheckedChange = { isRequired = it },
        )
        PropertyToggleSettingRow(
            label = "Hide in table",
            description = "Keep this property available in row pages, but hide it from the grid.",
            checked = isHidden,
            onCheckedChange = { isHidden = it },
        )
        PropertyToggleSettingRow(
            label = "Wrap content",
            description = "Allow long values to wrap instead of clipping in the grid.",
            checked = wrapContent,
            onCheckedChange = { wrapContent = it },
        )
    }
    OutlinedTextField(
        value = widthText,
        onValueChange = { text ->
            widthText = text.filter { char -> char.isDigit() }.take(3)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(text = "Column width") },
        placeholder = { Text(text = "Auto") },
        suffix = { Text(text = "dp") },
        supportingText = {
            Text(text = "Leave empty for auto width. Range: ${TableColumnMinWidthDp}-${TableColumnMaxWidthDp}dp.")
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        colors = blockTextFieldColors(),
    )
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = ::save,
    ) {
        Text(text = "Save details")
    }
}

@Composable
private fun PropertyToggleSettingRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
internal fun ChangePropertyTypeSheet(
    selectedType: PageTableColumnType,
    onTypeChange: (PageTableColumnType) -> Unit,
    onBack: () -> Unit,
) {
    PropertyDetailHeader(title = "Change type", onBack = onBack)
    PropertyMenuGroup {
        PageTableColumnType.entries.forEach { type ->
            PropertyMenuRow(
                icon = type.icon,
                label = type.label,
                value = if (type == selectedType) "Selected" else "",
                onClick = { onTypeChange(type) },
            )
        }
    }
}

@Composable
internal fun ChoicePropertyOptionsSheet(
    column: PageTableColumn,
    onColumnConfigChange: (PageTableColumnConfig) -> Unit,
    onBack: () -> Unit,
) {
    var options by remember(column.config.options) {
        mutableStateOf(column.choiceOptions)
    }

    fun updateOptionName(optionId: String, name: String) {
        options = options.map { option ->
            if (option.id == optionId) option.copy(name = name.toSingleLineTableCellValue()) else option
        }
    }

    fun cycleOptionColor(optionId: String) {
        options = options.map { option ->
            if (option.id != optionId) {
                option
            } else {
                val colors = PageTableOptionColor.entries
                val nextIndex = (colors.indexOf(option.color).coerceAtLeast(0) + 1) % colors.size
                option.copy(color = colors[nextIndex])
            }
        }
    }

    fun saveOptions() {
        onColumnConfigChange(
            column.config.copy(
                options = options
                    .map { option -> option.copy(name = option.name.trim()) }
                    .filter { option -> option.name.isNotBlank() },
            ),
        )
        onBack()
    }

    PropertyDetailHeader(title = "Options", onBack = onBack)
    PropertyMenuGroup {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 10.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(option.color.toChoiceColor())
                        .clickable { cycleOptionColor(option.id) },
                )
                BasicTextField(
                    value = option.name,
                    onValueChange = { name -> updateOptionName(option.id, name) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (option.name.isBlank()) {
                                Text(
                                    text = "Option",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                IconButton(
                    onClick = { options = options.filterNot { item -> item.id == option.id } },
                    enabled = options.size > 1,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete option",
                        modifier = Modifier.size(18.dp),
                        tint = if (options.size > 1) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 38.dp))
        }
        PropertyMenuRow(
            icon = Icons.Rounded.Add,
            label = "Add option",
            onClick = {
                options = options + PageTableSelectOption(
                    id = UUID.randomUUID().toString(),
                    name = "Option ${options.size + 1}",
                )
            },
        )
    }
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = options.any { option -> option.name.isNotBlank() },
        onClick = ::saveOptions,
    ) {
        Text(text = "Save options")
    }
}

@Composable
internal fun TableDatePropertySettingsSheet(
    column: PageTableColumn,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onBack: () -> Unit,
) {
    var dateFormat by remember(column.dateFormat) { mutableStateOf(column.dateFormat) }
    var timeFormat by remember(column.timeFormat) { mutableStateOf(column.timeFormat) }
    var reminder by remember(column.dateReminder) { mutableStateOf(column.dateReminder) }
    var timezoneLabel by remember(column.timezoneLabel) { mutableStateOf(column.timezoneLabel) }

    fun save(
        nextDateFormat: PageTableDateFormat = dateFormat,
        nextTimeFormat: PageTableTimeFormat = timeFormat,
        nextReminder: PageTableDateReminder = reminder,
        nextTimezone: String = timezoneLabel,
    ) {
        dateFormat = nextDateFormat
        timeFormat = nextTimeFormat
        reminder = nextReminder
        timezoneLabel = nextTimezone
        onDateSettingsChange(nextDateFormat, nextTimeFormat, nextReminder, nextTimezone)
    }

    PropertyDetailHeader(title = "Edit property", onBack = onBack)
    PropertyMenuGroup {
        DateSettingChoiceRow(
            icon = Icons.Rounded.CalendarMonth,
            label = "Date format",
            selectedLabel = dateFormat.label,
            items = PageTableDateFormat.entries,
            itemLabel = { format -> format.label },
            onSelect = { format -> save(nextDateFormat = format) },
        )
        DateSettingChoiceRow(
            icon = Icons.Rounded.AccessTime,
            label = "Time format",
            selectedLabel = timeFormat.label,
            items = PageTableTimeFormat.entries,
            itemLabel = { format -> format.label },
            onSelect = { format -> save(nextTimeFormat = format) },
        )
        DateSettingChoiceRow(
            icon = Icons.Rounded.Notifications,
            label = "Notifications",
            selectedLabel = reminder.label,
            items = PageTableDateReminder.entries,
            itemLabel = { value -> value.label },
            onSelect = { value -> save(nextReminder = value) },
        )
        DateSettingChoiceRow(
            icon = Icons.Rounded.Public,
            label = "Timezone",
            selectedLabel = timezoneLabel,
            items = TableTimezoneOptions,
            itemLabel = { value -> value },
            onSelect = { value -> save(nextTimezone = value) },
        )
    }
}

@Composable
internal fun <T> DateSettingChoiceRow(
    icon: ImageVector,
    label: String,
    selectedLabel: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PropertyMenuRow(
            icon = icon,
            label = label,
            value = selectedLabel,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = itemLabel(item)) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ColumnFilterSheet(
    column: PageTableColumn,
    query: String,
    operator: PageTableFilterOperator,
    onOperatorChange: (PageTableFilterOperator) -> Unit,
    onQueryChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val requiresQuery = operator.requiresQuery()
    PropertyDetailHeader(title = "Filter", onBack = onBack)
    Text(
        text = column.name.ifBlank { "Untitled" },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            items = column.filterOperators(),
            key = { item -> item.name },
        ) { item ->
            FilterChip(
                selected = item == operator,
                onClick = { onOperatorChange(item) },
                label = { Text(text = item.label) },
            )
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = requiresQuery,
        singleLine = true,
        placeholder = { Text(text = operator.placeholderFor(column)) },
        colors = blockTextFieldColors(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onClear) {
            Text(text = "Clear")
        }
        Button(
            enabled = !requiresQuery || query.isNotBlank(),
            onClick = onApply,
        ) {
            Text(text = "Apply")
        }
    }
}

@Composable
internal fun ColumnSortSheet(
    column: PageTableColumn,
    table: PageTable,
    onSortChange: (PageTableSortDirection?) -> Unit,
    onBack: () -> Unit,
) {
    val currentDirection = table.sort.direction.takeIf { table.sort.columnId == column.id }
    PropertyDetailHeader(title = "Sort", onBack = onBack)
    Text(
        text = column.name.ifBlank { "Untitled" },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PropertyMenuGroup {
        PropertyMenuRow(
            icon = Icons.Rounded.Close,
            label = "Off",
            value = if (currentDirection == null) "Selected" else "",
            onClick = { onSortChange(null) },
        )
        PropertyMenuRow(
            icon = Icons.AutoMirrored.Rounded.Sort,
            label = "Ascending",
            value = if (currentDirection == PageTableSortDirection.Ascending) "Selected" else "",
            onClick = { onSortChange(PageTableSortDirection.Ascending) },
        )
        PropertyMenuRow(
            icon = Icons.AutoMirrored.Rounded.Sort,
            label = "Descending",
            value = if (currentDirection == PageTableSortDirection.Descending) "Selected" else "",
            onClick = { onSortChange(PageTableSortDirection.Descending) },
        )
    }
}

@Composable
internal fun ColumnCalculateSheet(
    currentTableBlockId: String,
    table: PageTable,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    onFormulaChange: (String) -> Unit,
    onRelationTargetChange: (String) -> Unit,
    onRollupChange: (String, String, PageTableRollupAggregation) -> Unit,
    onBack: () -> Unit,
) {
    PropertyDetailHeader(title = "Calculate", onBack = onBack)
    when (column.type) {
        PageTableColumnType.Formula -> FormulaColumnConfig(
            column = column,
            sourceColumns = table.columns.filterNot { source -> source.id == column.id },
            onFormulaChange = onFormulaChange,
        )
        PageTableColumnType.Relation -> RelationColumnConfig(
            currentTableBlockId = currentTableBlockId,
            column = column,
            tableReferences = tableReferences,
            onRelationTargetChange = onRelationTargetChange,
        )
        PageTableColumnType.Rollup -> RollupColumnConfig(
            table = table,
            column = column,
            tableReferences = tableReferences,
            onRollupChange = onRollupChange,
        )
        PageTableColumnType.Number,
        PageTableColumnType.Checkbox,
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
        PageTableColumnType.Status,
        PageTableColumnType.Date,
        PageTableColumnType.Text,
        PageTableColumnType.FilesMedia,
        -> Text(
            text = "Change this property to Formula or Rollup to calculate values.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableColumnConfigSheet(
    currentTableBlockId: String,
    table: PageTable,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    onFormulaChange: (String) -> Unit,
    onRelationTargetChange: (String) -> Unit,
    onRollupChange: (String, String, PageTableRollupAggregation) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = column.type.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = column.name.ifBlank { "Untitled" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (column.type) {
                PageTableColumnType.Formula -> FormulaColumnConfig(
                    column = column,
                    sourceColumns = table.columns.filterNot { source -> source.id == column.id },
                    onFormulaChange = onFormulaChange,
                )
                PageTableColumnType.Relation -> RelationColumnConfig(
                    currentTableBlockId = currentTableBlockId,
                    column = column,
                    tableReferences = tableReferences,
                    onRelationTargetChange = onRelationTargetChange,
                )
                PageTableColumnType.Rollup -> RollupColumnConfig(
                    table = table,
                    column = column,
                    tableReferences = tableReferences,
                    onRollupChange = onRollupChange,
                )
                PageTableColumnType.Text,
                PageTableColumnType.Number,
                PageTableColumnType.Select,
                PageTableColumnType.MultiSelect,
                PageTableColumnType.Status,
                PageTableColumnType.Date,
                PageTableColumnType.Checkbox,
                PageTableColumnType.FilesMedia,
                -> Unit
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun FormulaColumnConfig(
    column: PageTableColumn,
    sourceColumns: List<PageTableColumn>,
    onFormulaChange: (String) -> Unit,
) {
    var formula by remember(column.formula) { mutableStateOf(column.formula) }
    val unknownReferences = remember(formula, sourceColumns) {
        val validNames = sourceColumns.map { source -> source.name.trim().lowercase() }.toSet()
        Regex("\\{([^}]+)}")
            .findAll(formula)
            .map { match -> match.groupValues.getOrNull(1).orEmpty().trim() }
            .filter { name -> name.isNotBlank() && name.lowercase() !in validNames }
            .distinct()
            .toList()
    }

    OutlinedTextField(
        value = formula,
        onValueChange = { formula = it },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        placeholder = { Text(text = "{Price} * {Qty}") },
        supportingText = {
            if (unknownReferences.isNotEmpty()) {
                Text(text = "Unknown property: ${unknownReferences.joinToString()}")
            } else {
                Text(text = "Use property names in braces, for example {Price} * {Qty}.")
            }
        },
        isError = unknownReferences.isNotEmpty(),
        colors = blockTextFieldColors(),
    )
    if (sourceColumns.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sourceColumns, key = { source -> source.id }) { source ->
                FilterChip(
                    selected = false,
                    onClick = { formula += "{${source.name}}" },
                    label = { Text(text = source.name.ifBlank { "Untitled" }.compactControlLabel()) },
                )
            }
        }
    }
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = unknownReferences.isEmpty(),
        onClick = { onFormulaChange(formula) },
    ) {
        Text(text = "Save formula")
    }
}

@Composable
internal fun RelationColumnConfig(
    currentTableBlockId: String,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    onRelationTargetChange: (String) -> Unit,
) {
    val targetTables = tableReferences.filterNot { reference -> reference.blockId == currentTableBlockId }
    val selectedTargetMissing = column.relationTargetTableId.isNotBlank() &&
        targetTables.none { reference -> reference.blockId == column.relationTargetTableId }

    if (targetTables.isEmpty()) {
        Text(
            text = if (selectedTargetMissing) {
                "The linked source database is missing."
            } else {
                "Create another table in this page first."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectedTargetMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Text(
        text = "Target table",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(targetTables, key = { reference -> reference.blockId }) { reference ->
            FilterChip(
                selected = reference.blockId == column.relationTargetTableId,
                onClick = { onRelationTargetChange(reference.blockId) },
                label = { Text(text = reference.title.databaseTitleOrPlaceholder().compactControlLabel()) },
            )
        }
    }
    if (selectedTargetMissing) {
        Text(
            text = "The linked source database is missing. Pick another target.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun RollupColumnConfig(
    table: PageTable,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    onRollupChange: (String, String, PageTableRollupAggregation) -> Unit,
) {
    val relationColumns = table.columns.filter { candidate -> candidate.type == PageTableColumnType.Relation }
    val selectedRelation = relationColumns.firstOrNull { relation -> relation.id == column.rollupRelationColumnId }
        ?: relationColumns.firstOrNull()
    val targetTable = tableReferences.firstOrNull { reference -> reference.blockId == selectedRelation?.relationTargetTableId }
    val selectedTargetColumnId = column.rollupTargetColumnId
        .ifBlank { targetTable?.table?.columns?.firstOrNull()?.id.orEmpty() }
    val selectedTargetColumn = targetTable?.table?.columns?.firstOrNull { target -> target.id == selectedTargetColumnId }
    val selectedAggregation = column.rollupAggregation
    val numericAggregationNeedsNumber = selectedAggregation.requiresNumericValues() &&
        selectedTargetColumn != null &&
        selectedTargetColumn.type !in setOf(
            PageTableColumnType.Number,
            PageTableColumnType.Formula,
            PageTableColumnType.Rollup,
        )
    val previewColumn = column.copy(
        rollupRelationColumnId = selectedRelation?.id.orEmpty(),
        rollupTargetColumnId = selectedTargetColumnId,
        rollupAggregation = selectedAggregation,
    )

    if (relationColumns.isEmpty()) {
        Text(
            text = "Add a Relation column first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Text(
        text = "Relation",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(relationColumns, key = { relation -> relation.id }) { relation ->
            FilterChip(
                selected = relation.id == selectedRelation?.id,
                onClick = {
                    val relatedTable = tableReferences.firstOrNull { reference ->
                        reference.blockId == relation.relationTargetTableId
                    }
                    onRollupChange(
                        relation.id,
                        relatedTable?.table?.columns?.firstOrNull()?.id.orEmpty(),
                        selectedAggregation,
                    )
                },
                label = { Text(text = relation.name.ifBlank { "Untitled" }.compactControlLabel()) },
            )
        }
    }

    if (targetTable == null) {
        Text(
            text = if (selectedRelation?.relationTargetTableId.isNullOrBlank()) {
                "Choose a target database for the relation property first."
            } else {
                "The linked source database is missing. Pick another relation target."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectedRelation?.relationTargetTableId.isNullOrBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        return
    }

    Text(
        text = "Target property",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TableColumnChoiceRow(
        columns = targetTable.table.columns,
        selectedColumnId = selectedTargetColumnId,
        onSelect = { targetColumn ->
            onRollupChange(selectedRelation?.id.orEmpty(), targetColumn.id, selectedAggregation)
        },
    )
    if (selectedTargetColumn == null) {
        Text(
            text = "Choose a target property.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Text(
        text = "Calculate",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PageTableRollupAggregation.entries, key = { aggregation -> aggregation.name }) { aggregation ->
            FilterChip(
                selected = aggregation == selectedAggregation,
                onClick = {
                    onRollupChange(selectedRelation?.id.orEmpty(), selectedTargetColumnId, aggregation)
                },
                label = { Text(text = aggregation.name) },
            )
        }
    }
    if (numericAggregationNeedsNumber) {
        Text(
            text = "${selectedAggregation.name} works best with Number, Formula, or Rollup properties. Non-number values will be ignored.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    table.rows.firstOrNull()?.let { firstRow ->
        val preview = table.rollupDisplayText(
            row = firstRow,
            column = previewColumn,
            tableReferences = tableReferences,
            depth = 1,
        )
        Text(
            text = "Preview: ${preview.ifBlank { "Empty" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun PageTableRollupAggregation.requiresNumericValues(): Boolean {
    return this == PageTableRollupAggregation.Sum ||
        this == PageTableRollupAggregation.Average ||
        this == PageTableRollupAggregation.Min ||
        this == PageTableRollupAggregation.Max
}

@Composable
internal fun TableGroupHeader(
    label: String,
    count: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label ($count)",
            modifier = Modifier.width(TableGroupHeaderWidth),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private val TableInlineOpenWidth = 40.dp
private val TableCellHorizontalPadding = 8.dp
private const val TableLargeDatasetRowThreshold = 40
private const val TableWidthMeasurementRowLimit = 80
private const val TableColumnMinWidthDp = 72
private const val TableColumnMaxWidthDp = 360
private val TableLargeDatasetBodyMaxHeight = 560.dp

private fun PageTable.tableColumnWidths(
    tableReferences: List<PageTableReference>,
    rowSampleLimit: Int,
): Map<String, Dp> {
    return columns.mapIndexed { index, column ->
        column.id to tableColumnWidth(
            column = column,
            tableReferences = tableReferences,
            includeInlineOpen = index == 0,
            rowSampleLimit = rowSampleLimit,
        )
    }.toMap()
}

private fun PageTable.tableColumnWidth(
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    includeInlineOpen: Boolean,
    rowSampleLimit: Int,
): Dp {
    if (column.config.widthDp > 0) {
        return column.config.widthDp
            .coerceIn(TableColumnMinWidthDp, TableColumnMaxWidthDp)
            .dp
    }
    val headerText = column.name.ifBlank { column.type.label }
    val longestCellText = rows.asSequence()
        .take(rowSampleLimit.coerceAtLeast(0))
        .map { row -> displayCellText(row, column, tableReferences) }
        .maxByOrNull { value -> value.length }
        .orEmpty()
    val textLength = maxOf(headerText.length, longestCellText.length)
    val textBasedWidth = 56 + (textLength.coerceAtMost(24) * 7)
    val baseMinWidth = when (column.type) {
        PageTableColumnType.Checkbox -> 92
        PageTableColumnType.Number -> 92
        PageTableColumnType.Date -> 116
        PageTableColumnType.Select,
        PageTableColumnType.Status,
        -> 112
        PageTableColumnType.MultiSelect -> 128
        PageTableColumnType.FilesMedia -> 112
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> 112
        PageTableColumnType.Relation -> 132
        PageTableColumnType.Text -> 132
    }
    val baseMaxWidth = when (column.type) {
        PageTableColumnType.Checkbox -> 112
        PageTableColumnType.Number -> 132
        PageTableColumnType.Date -> 172
        PageTableColumnType.Select,
        PageTableColumnType.Status,
        -> 176
        PageTableColumnType.MultiSelect -> 212
        PageTableColumnType.FilesMedia -> 176
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> 188
        PageTableColumnType.Relation -> 224
        PageTableColumnType.Text -> 260
    }
    val minWidth = if (includeInlineOpen) {
        (baseMinWidth + 8).coerceAtLeast(112)
    } else {
        baseMinWidth
    }
    val maxWidth = if (includeInlineOpen) {
        (baseMaxWidth - 8).coerceAtLeast(minWidth)
    } else {
        baseMaxWidth
    }
    return textBasedWidth.coerceIn(minWidth, maxWidth).dp
}

@Composable
internal fun TableDataRow(
    row: PageTableRow,
    rowIndex: Int,
    totalRows: Int,
    pageId: String,
    pageUpdatedAt: Long,
    table: PageTable,
    columns: List<PageTableColumn>,
    columnWidths: Map<String, Dp>,
    tableReferences: List<PageTableReference>,
    onColumnDateSettingsChange: (
        String,
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onCellChange: (String, String, String) -> Unit,
    onRelationCellChange: (String, String, List<String>) -> Unit,
    onAddRelationTargetRow: (String) -> Unit,
    onDeleteRow: (String) -> Unit,
    onDuplicateRow: (String) -> Unit,
    onMoveRow: (String, Int) -> Unit,
    onOpenRow: (String) -> Unit,
    isHighlighted: Boolean = false,
) {
    val primaryColumn = columns.firstOrNull()
    val remainingColumns = columns.drop(1)
    val primaryColumnWidth = primaryColumn?.let { column -> columnWidths[column.id] } ?: TableCellWidth
    var isActionSheetOpen by remember(row.id) { mutableStateOf(false) }
    var isDragging by remember(row.id) { mutableStateOf(false) }
    val tableColors = TableGridTokens.colors()
    val context = LocalContext.current
    val latestRowIndex by rememberUpdatedState(rowIndex)
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.01f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "table-row-drag-scale",
    )
    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.96f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "table-row-drag-alpha",
    )
    val dragShadow by animateFloatAsState(
        targetValue = if (isDragging) 10f else 0f,
        animationSpec = tween(durationMillis = 110),
        label = "table-row-drag-shadow",
    )
    val rowBackground = when {
        isDragging -> tableColors.draggedRowBackground
        isHighlighted -> tableColors.highlightedRowBackground
        else -> tableColors.cellBackground
    }

    if (isActionSheetOpen) {
        TableRowActionSheet(
            rowTitle = row.cellText(table.titleColumn()).ifBlank { "Untitled row" },
            lastEditedAt = pageUpdatedAt,
            onEditProperties = {
                isActionSheetOpen = false
                onOpenRow(row.id)
            },
            onCopyLink = {
                val rowLink = "cyl://page/$pageId?targetType=row&targetId=${row.id}"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("CYL row link", rowLink))
                Toast.makeText(context, "Row link copied", Toast.LENGTH_SHORT).show()
                isActionSheetOpen = false
            },
            onDuplicate = {
                onDuplicateRow(row.id)
                isActionSheetOpen = false
            },
            onMoveUp = {
                if (rowIndex > 0) onMoveRow(row.id, rowIndex - 1)
                isActionSheetOpen = false
            },
            onMoveDown = {
                if (rowIndex >= 0 && rowIndex < totalRows - 1) onMoveRow(row.id, rowIndex + 1)
                isActionSheetOpen = false
            },
            onMoveToTrash = {
                onDeleteRow(row.id)
                isActionSheetOpen = false
            },
            canMoveUp = rowIndex > 0,
            canMoveDown = rowIndex >= 0 && rowIndex < totalRows - 1,
            onDismiss = { isActionSheetOpen = false },
        )
    }

    Row(
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                alpha = dragAlpha
                shadowElevation = dragShadow
                shape = RoundedCornerShape(8.dp)
                clip = false
            }
            .background(rowBackground)
            .tableRowHoldGesture(
                rowId = row.id,
                totalRows = totalRows,
                rowIndexProvider = { latestRowIndex },
                onDragActiveChange = { isActive -> isDragging = isActive },
                onStationaryLongPress = { isActionSheetOpen = true },
                onMoveRow = onMoveRow,
            ),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primaryColumn != null) {
            PrimaryTableCellEditor(
                column = primaryColumn,
                row = row,
                table = table,
                tableReferences = tableReferences,
                value = row.cellText(primaryColumn),
                onValueChange = { value -> onCellChange(row.id, primaryColumn.id, value) },
                onRelationValueChange = { relationRowIds -> onRelationCellChange(row.id, primaryColumn.id, relationRowIds) },
                currentPageId = pageId,
                onCreateTargetRow = onAddRelationTargetRow,
                onDateSettingsChange = { dateFormat, timeFormat, reminder, timezoneLabel ->
                    onColumnDateSettingsChange(primaryColumn.id, dateFormat, timeFormat, reminder, timezoneLabel)
                },
                width = primaryColumnWidth,
                onOpenRow = { onOpenRow(row.id) },
            )
        } else {
            TableOpenRowCell(
                modifier = Modifier.width(TableInlineOpenWidth),
                onClick = { onOpenRow(row.id) },
            )
        }
        remainingColumns.forEach { column ->
            val columnWidth = columnWidths[column.id] ?: TableCellWidth
            TableCellEditor(
                column = column,
                row = row,
                table = table,
                tableReferences = tableReferences,
                value = row.cellText(column),
                onValueChange = { value -> onCellChange(row.id, column.id, value) },
                onRelationValueChange = { relationRowIds -> onRelationCellChange(row.id, column.id, relationRowIds) },
                currentPageId = pageId,
                onCreateTargetRow = onAddRelationTargetRow,
                onDateSettingsChange = { dateFormat, timeFormat, reminder, timezoneLabel ->
                    onColumnDateSettingsChange(column.id, dateFormat, timeFormat, reminder, timezoneLabel)
                },
                modifier = Modifier
                    .width(columnWidth)
                    .background(tableColors.cellBackground),
            )
        }
        Box(
            modifier = Modifier
                .width(TableAddColumnWidth)
                .height(TableRowHeight)
                .background(tableColors.cellBackground),
        )
    }
    HorizontalDivider(color = tableColors.divider)
}

private fun Modifier.tableRowHoldGesture(
    rowId: String,
    totalRows: Int,
    rowIndexProvider: () -> Int,
    onDragActiveChange: (Boolean) -> Unit,
    onStationaryLongPress: () -> Unit,
    onMoveRow: (String, Int) -> Unit,
): Modifier {
    if (totalRows <= 0) return this
    return pointerInput(rowId, totalRows) {
        val rowStepThreshold = TableRowHeight.toPx() * 0.42f

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            var currentIndex = rowIndexProvider().coerceIn(0, totalRows - 1)
            var dragOffset = 0f
            var moved = false
            var dragged = false

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { pointerChange -> pointerChange.id == longPress.id }
                        ?: break
                    if (!change.pressed) break

                    val deltaY = change.positionChange().y
                    if (deltaY == 0f) continue
                    if (!dragged) {
                        onDragActiveChange(true)
                    }
                    dragged = true
                    if (totalRows <= 1) {
                        change.consume()
                        continue
                    }
                    dragOffset += deltaY
                    while (abs(dragOffset) >= rowStepThreshold) {
                        val direction = if (dragOffset > 0f) 1 else -1
                        val targetIndex = (currentIndex + direction).coerceIn(0, totalRows - 1)
                        if (targetIndex == currentIndex) {
                            dragOffset = 0f
                            break
                        }
                        onMoveRow(rowId, targetIndex)
                        currentIndex = targetIndex
                        moved = true
                        dragOffset -= direction * rowStepThreshold
                    }
                    change.consume()
                }
            } finally {
                onDragActiveChange(false)
            }

            if (!moved && !dragged) {
                onStationaryLongPress()
            }
        }
    }
}

private fun PageTable.isStarterEmptyDatabase(searchQuery: String): Boolean {
    return columns.size == 1 &&
        columns.firstOrNull()?.name.equals("Name", ignoreCase = true) &&
        rows.isEmpty() &&
        sort.columnId.isBlank() &&
        !filter.isActive() &&
        groupByColumnId.isBlank() &&
        searchQuery.isBlank()
}

@Composable
internal fun TableRowActionSheet(
    rowTitle: String,
    lastEditedAt: Long,
    onEditProperties: () -> Unit,
    onCopyLink: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTrash: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Last edited ${lastEditedAt.toTableRowActionDateTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.52f)),
            ) {
                TableRowActionItem(
                    icon = Icons.Rounded.CheckCircle,
                    label = "Add to favourite",
                    enabled = false,
                    onClick = {},
                )
                TableRowActionItem(
                    icon = Icons.Rounded.Edit,
                    label = "Edit icon",
                    enabled = false,
                    onClick = {},
                )
                TableRowActionItem(
                    icon = Icons.Rounded.Tune,
                    label = "Edit property",
                    onClick = onEditProperties,
                )
                TableRowActionItem(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy link",
                    onClick = onCopyLink,
                )
                TableRowActionItem(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Duplicate",
                    onClick = onDuplicate,
                )
                TableRowActionItem(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    label = "Move up",
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                )
                TableRowActionItem(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    label = "Move down",
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                )
                TableRowActionItem(
                    icon = Icons.Rounded.SwapVert,
                    label = "Move to",
                    enabled = false,
                    onClick = {},
                )
                TableRowActionItem(
                    icon = Icons.Rounded.Delete,
                    label = "Move to trash",
                    destructive = true,
                    onClick = onMoveToTrash,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun TableRowActionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

private fun Long.toTableRowActionDateTime(): String {
    if (this <= 0L) return "unknown"
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US))
}

@Composable
internal fun PrimaryTableCellEditor(
    column: PageTableColumn,
    row: PageTableRow,
    table: PageTable,
    tableReferences: List<PageTableReference>,
    value: String,
    onValueChange: (String) -> Unit,
    onRelationValueChange: (List<String>) -> Unit,
    currentPageId: String,
    onCreateTargetRow: (String) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    width: Dp,
    onOpenRow: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(TableRowHeight)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TableCellEditor(
            column = column,
            row = row,
            table = table,
            tableReferences = tableReferences,
            value = value,
            onValueChange = onValueChange,
            onRelationValueChange = onRelationValueChange,
            currentPageId = currentPageId,
            onCreateTargetRow = onCreateTargetRow,
            onDateSettingsChange = onDateSettingsChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = TableInlineOpenWidth - 4.dp),
        )
        TableOpenRowCell(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(TableInlineOpenWidth),
            onClick = onOpenRow,
        )
    }
}

@Composable
internal fun TableOpenRowCell(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(TableRowHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "OPEN",
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f))
                .padding(horizontal = 5.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
                letterSpacing = 0.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TableAddRowRow(
    columns: List<PageTableColumn>,
    columnWidths: Map<String, Dp>,
    onAddRow: () -> Unit,
) {
    val firstColumnWidth = columns.firstOrNull()?.let { column -> columnWidths[column.id] } ?: TableCellWidth
    val remainingColumns = columns.drop(1)

    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableAddRowCell(
            modifier = Modifier.width(firstColumnWidth),
            onAddRow = onAddRow,
        )
        remainingColumns.forEach { column ->
            Box(
                modifier = Modifier
                    .width(columnWidths[column.id] ?: TableCellWidth)
                    .height(TableRowHeight)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
        Box(
            modifier = Modifier
                .width(TableAddColumnWidth)
                .height(TableRowHeight)
                .background(MaterialTheme.colorScheme.surface),
        )
    }
    HorizontalDivider()
}

@Composable
internal fun TableAddRowCell(
    modifier: Modifier,
    onAddRow: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(TableRowHeight)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onAddRow)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "New row",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableDateCellEditor(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
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

    Row(
        modifier = modifier
            .height(TableRowHeight)
            .clickable { isSheetOpen = true }
            .padding(horizontal = TableCellHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (displayText.isNotBlank()) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
        Text(
            text = displayText,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private enum class DateEditorTarget {
    Start,
    End,
}

private enum class TimeEditorTarget {
    Start,
    End,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableDateEditorSheet(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var cellValue by remember(value) { mutableStateOf(value.toTableDateCellValue()) }
    var selectedDate by remember(value) {
        mutableStateOf(cellValue.startDate.toLocalDateOrNull() ?: LocalDate.now())
    }
    var endDate by remember(value) {
        mutableStateOf(cellValue.endDate.toLocalDateOrNull() ?: selectedDate)
    }
    var activeTarget by remember(value) { mutableStateOf(DateEditorTarget.Start) }
    var activeTimeTarget by remember { mutableStateOf<TimeEditorTarget?>(null) }
    var visibleMonth by remember(value) { mutableStateOf(YearMonth.from(selectedDate)) }
    var includeEndDate by remember(value) { mutableStateOf(cellValue.includeEndDate) }
    var includeTime by remember(value, column.timeFormat) {
        mutableStateOf(cellValue.includeTime || column.timeFormat != PageTableTimeFormat.Hidden)
    }
    var selectedTime by remember(value) {
        mutableStateOf(cellValue.startTime.toLocalTimeOrNull() ?: LocalTime.of(10, 0))
    }
    var selectedEndTime by remember(value) {
        mutableStateOf(cellValue.endTime.toLocalTimeOrNull() ?: selectedTime)
    }
    var dateFormat by remember(column.dateFormat) { mutableStateOf(column.dateFormat) }
    var timeFormat by remember(column.timeFormat) { mutableStateOf(column.timeFormat) }
    val hasCellDateMetadata = remember(value) { value.trim().startsWith("{") }
    var reminder by remember(value, column.dateReminder) {
        mutableStateOf(if (hasCellDateMetadata) cellValue.reminder else column.dateReminder)
    }
    var timezoneLabel by remember(value, column.timezoneLabel) {
        mutableStateOf(if (hasCellDateMetadata) cellValue.timezoneLabel else column.timezoneLabel)
    }

    fun saveCell(next: TableDateCellValue) {
        val normalized = next.copy(
            timezoneLabel = timezoneLabel,
            reminder = reminder,
        )
        cellValue = normalized
        onValueChange(normalized.toTableDateCellStorageValue())
    }

    fun normalizedEndDate(start: LocalDate, end: LocalDate): LocalDate {
        return if (end.isBefore(start)) start else end
    }

    fun saveStartTime(time: LocalTime) {
        selectedTime = time
        saveCell(
            cellValue.copy(
                startDate = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                startTime = time.format(DateTimeFormatter.ISO_LOCAL_TIME),
                includeTime = true,
                timezoneLabel = timezoneLabel,
            ),
        )
    }

    fun saveEndTime(time: LocalTime) {
        selectedEndTime = time
        saveCell(
            cellValue.copy(
                endDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endTime = time.format(DateTimeFormatter.ISO_LOCAL_TIME),
                includeEndDate = true,
                includeTime = true,
                timezoneLabel = timezoneLabel,
            ),
        )
    }

    fun saveColumnSettings(
        nextDateFormat: PageTableDateFormat = dateFormat,
        nextTimeFormat: PageTableTimeFormat = timeFormat,
    ) {
        dateFormat = nextDateFormat
        timeFormat = nextTimeFormat
        onDateSettingsChange(nextDateFormat, nextTimeFormat, column.dateReminder, column.timezoneLabel)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.width(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close date editor",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = column.name.ifBlank { "Date" },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DateEditorValueBox(
                        label = "Start",
                        text = selectedDate.formatForColumn(dateFormat),
                        isSelected = activeTarget == DateEditorTarget.Start,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTarget = DateEditorTarget.Start
                            visibleMonth = YearMonth.from(selectedDate)
                        },
                    )
                    if (includeTime) {
                        DateTimeChoiceBox(
                            selectedTime = selectedTime,
                            timeFormat = timeFormat.visibleOrDefault(),
                            isSelected = activeTimeTarget == TimeEditorTarget.Start,
                            onClick = {
                                activeTarget = DateEditorTarget.Start
                                activeTimeTarget = if (activeTimeTarget == TimeEditorTarget.Start) {
                                    null
                                } else {
                                    TimeEditorTarget.Start
                                }
                            },
                        )
                    }
                }
                if (includeEndDate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DateEditorValueBox(
                            label = "End",
                            text = endDate.formatForColumn(dateFormat),
                            isSelected = activeTarget == DateEditorTarget.End,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                activeTarget = DateEditorTarget.End
                                visibleMonth = YearMonth.from(endDate)
                            },
                        )
                        if (includeTime) {
                            DateTimeChoiceBox(
                                selectedTime = selectedEndTime,
                                timeFormat = timeFormat.visibleOrDefault(),
                                isSelected = activeTimeTarget == TimeEditorTarget.End,
                                onClick = {
                                    activeTarget = DateEditorTarget.End
                                    activeTimeTarget = if (activeTimeTarget == TimeEditorTarget.End) {
                                        null
                                    } else {
                                        TimeEditorTarget.End
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (includeTime && activeTimeTarget != null) {
                TimeEditorPanel(
                    title = if (activeTimeTarget == TimeEditorTarget.End) "End time" else "Start time",
                    selectedTime = if (activeTimeTarget == TimeEditorTarget.End) selectedEndTime else selectedTime,
                    timeFormat = timeFormat.visibleOrDefault(),
                    onTimeChange = { time ->
                        if (activeTimeTarget == TimeEditorTarget.End) {
                            saveEndTime(time)
                        } else {
                            saveStartTime(time)
                        }
                    },
                    onDone = { activeTimeTarget = null },
                )
            }

            TableDateCalendar(
                visibleMonth = visibleMonth,
                selectedDate = if (activeTarget == DateEditorTarget.Start) selectedDate else endDate,
                rangeStartDate = selectedDate.takeIf { includeEndDate },
                rangeEndDate = endDate.takeIf { includeEndDate },
                onPreviousMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) },
                onSelectDate = { date ->
                    val nextStart = if (activeTarget == DateEditorTarget.Start) date else selectedDate
                    val nextEnd = if (activeTarget == DateEditorTarget.End) {
                        normalizedEndDate(selectedDate, date)
                    } else {
                        normalizedEndDate(date, endDate)
                    }
                    selectedDate = nextStart
                    endDate = nextEnd
                    visibleMonth = YearMonth.from(date)
                    saveCell(
                        cellValue.copy(
                            startDate = nextStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            startTime = if (includeTime) {
                                selectedTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
                            } else {
                                ""
                            },
                            endDate = if (includeEndDate) {
                                nextEnd.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            } else {
                                ""
                            },
                            endTime = if (includeEndDate && includeTime) {
                                selectedEndTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
                            } else {
                                ""
                            },
                            includeEndDate = includeEndDate,
                            includeTime = includeTime,
                            timezoneLabel = timezoneLabel,
                        ),
                    )
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                DateToggleRow(
                    label = "End date",
                    checked = includeEndDate,
                    onCheckedChange = { checked ->
                        includeEndDate = checked
                        activeTarget = if (checked) DateEditorTarget.End else DateEditorTarget.Start
                        endDate = normalizedEndDate(selectedDate, endDate)
                        saveCell(
                            cellValue.copy(
                                includeEndDate = checked,
                                endDate = if (checked) {
                                    endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                } else {
                                    ""
                                },
                                endTime = if (checked && includeTime) {
                                    selectedEndTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
                                } else {
                                    ""
                                },
                            ),
                        )
                    },
                )
                DateChoiceRow(
                    icon = Icons.Rounded.CalendarMonth,
                    label = "Date format",
                    selectedLabel = dateFormat.label,
                    items = PageTableDateFormat.entries,
                    itemLabel = { format -> format.label },
                    onSelect = { format -> saveColumnSettings(nextDateFormat = format) },
                )
                DateToggleRow(
                    label = "Include time",
                    checked = includeTime,
                    onCheckedChange = { checked ->
                        includeTime = checked
                        activeTimeTarget = if (checked) TimeEditorTarget.Start else null
                        val nextTimeFormat = if (checked) {
                            timeFormat.visibleOrDefault()
                        } else {
                            PageTableTimeFormat.Hidden
                        }
                        saveColumnSettings(nextTimeFormat = nextTimeFormat)
                        saveCell(
                            cellValue.copy(
                                startTime = if (checked) {
                                    selectedTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
                                } else {
                                    ""
                                },
                                endTime = if (checked && includeEndDate) {
                                    selectedEndTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
                                } else {
                                    ""
                                },
                                includeTime = checked,
                            ),
                        )
                    },
                )
                if (includeTime) {
                    DateChoiceRow(
                        icon = Icons.Rounded.AccessTime,
                        label = "Time format",
                        selectedLabel = timeFormat.visibleOrDefault().label,
                        items = listOf(PageTableTimeFormat.TwelveHour, PageTableTimeFormat.TwentyFourHour),
                        itemLabel = { format -> format.label },
                        onSelect = { format ->
                            includeTime = true
                            saveColumnSettings(nextTimeFormat = format)
                            saveCell(
                                cellValue.copy(
                                    startTime = selectedTime.format(DateTimeFormatter.ISO_LOCAL_TIME),
                                    endTime = if (includeEndDate) {
                                        selectedEndTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
                                    } else {
                                        ""
                                    },
                                    includeTime = true,
                                ),
                            )
                        },
                    )
                }
                DateChoiceRow(
                    icon = Icons.Rounded.Public,
                    label = "Timezone",
                    selectedLabel = timezoneLabel,
                    items = TableTimezoneOptions,
                    itemLabel = { timezone -> timezone },
                    onSelect = { timezone ->
                        timezoneLabel = timezone
                        saveCell(cellValue.copy(timezoneLabel = timezone))
                    },
                )
                DateChoiceRow(
                    icon = Icons.Rounded.Notifications,
                    label = "Remind",
                    selectedLabel = reminder.label,
                    items = PageTableDateReminder.entries,
                    itemLabel = { reminder -> reminder.label },
                    onSelect = { nextReminder ->
                        reminder = nextReminder
                        saveCell(cellValue.copy(reminder = nextReminder))
                    },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                ListItem(
                    headlineContent = { Text(text = "Clear") },
                    modifier = Modifier.clickable {
                        cellValue = TableDateCellValue()
                        includeEndDate = false
                        includeTime = timeFormat != PageTableTimeFormat.Hidden
                        activeTarget = DateEditorTarget.Start
                        activeTimeTarget = null
                        onValueChange("")
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun DateEditorValueBox(
    label: String = "",
    text: String,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DateTimeChoiceBox(
    selectedTime: LocalTime,
    timeFormat: PageTableTimeFormat,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.width(132.dp)) {
        DateEditorValueBox(
            label = "Time",
            text = selectedTime.formatForColumn(timeFormat),
            isSelected = isSelected,
            onClick = onClick,
        )
    }
}

@Composable
internal fun TimeEditorPanel(
    title: String,
    selectedTime: LocalTime,
    timeFormat: PageTableTimeFormat,
    onTimeChange: (LocalTime) -> Unit,
    onDone: () -> Unit,
) {
    val visibleFormat = timeFormat.visibleOrDefault()
    val use24Hour = visibleFormat == PageTableTimeFormat.TwentyFourHour
    val pickerState = rememberTimePickerState(
        initialHour = selectedTime.hour,
        initialMinute = selectedTime.minute,
        is24Hour = use24Hour,
    )
    LaunchedEffect(selectedTime.hour, selectedTime.minute) {
        if (pickerState.hour != selectedTime.hour) {
            pickerState.hour = selectedTime.hour
        }
        if (pickerState.minute != selectedTime.minute) {
            pickerState.minute = selectedTime.minute
        }
    }
    LaunchedEffect(pickerState.hour, pickerState.minute) {
        val updatedTime = selectedTime
            .withHour(pickerState.hour)
            .withMinute(pickerState.minute)
        if (updatedTime != selectedTime) {
            onTimeChange(updatedTime)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onDone) {
                Text(text = "Done")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f))
                .padding(vertical = 14.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = selectedTime.formatForColumn(visibleFormat),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            TimePicker(state = pickerState)
        }

        Text(
            text = "Quick time",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(TableQuickTimeOptions) { time ->
                FilterChip(
                    selected = selectedTime.hour == time.hour && selectedTime.minute == time.minute,
                    onClick = { onTimeChange(time) },
                    label = {
                        Text(
                            text = time.formatForColumn(visibleFormat),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun TableDateCalendar(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    rangeStartDate: LocalDate? = null,
    rangeEndDate: LocalDate? = null,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val firstDay = visibleMonth.atDay(1)
    val firstGridDay = firstDay.minusDays((firstDay.dayOfWeek.value % 7).toLong())
    val normalizedRangeStart = rangeStartDate?.let { start ->
        rangeEndDate?.let { end -> if (start.isAfter(end)) end else start }
    }
    val normalizedRangeEnd = rangeEndDate?.let { end ->
        rangeStartDate?.let { start -> if (end.isBefore(start)) start else end }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Previous month",
                    modifier = Modifier.graphicsLayer(rotationZ = -90f),
                )
            }
            Text(
                text = visibleMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Next month",
                    modifier = Modifier.graphicsLayer(rotationZ = 90f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            TableWeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                )
            }
        }
        repeat(6) { rowIndex ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { columnIndex ->
                    val date = firstGridDay.plusDays((rowIndex * 7 + columnIndex).toLong())
                    val isSelected = date == selectedDate
                    val isCurrentMonth = date.month == visibleMonth.month
                    val isInRange = normalizedRangeStart != null &&
                        normalizedRangeEnd != null &&
                        !date.isBefore(normalizedRangeStart) &&
                        !date.isAfter(normalizedRangeEnd)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isInRange -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                                        else -> Color.Transparent
                                    },
                                )
                                .clickable { onSelectDate(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DateToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = label) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
    HorizontalDivider()
}

@Composable
internal fun <T> DateChoiceRow(
    icon: ImageVector,
    label: String,
    selectedLabel: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(text = label) },
            leadingContent = {
                Box(
                    modifier = Modifier.width(34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                }
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(rotationZ = -90f),
                    )
                }
            },
            modifier = Modifier.clickable { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = itemLabel(item)) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                )
            }
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableCellEditor(
    column: PageTableColumn,
    row: PageTableRow,
    table: PageTable,
    tableReferences: List<PageTableReference>,
    value: String,
    onValueChange: (String) -> Unit,
    onRelationValueChange: (List<String>) -> Unit,
    currentPageId: String,
    onCreateTargetRow: ((String) -> Unit)?,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (column.type) {
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> {
            ReadOnlyComputedCell(
                value = table.displayCellText(row, column, tableReferences),
                modifier = modifier,
            )
        }
        PageTableColumnType.Relation -> {
            RelationCellEditor(
                column = column,
                value = value,
                tableReferences = tableReferences,
                onValueChange = onValueChange,
                onRelationValueChange = onRelationValueChange,
                currentPageId = currentPageId,
                onCreateTargetRow = onCreateTargetRow,
                modifier = modifier,
            )
        }
        PageTableColumnType.Checkbox -> {
            val isChecked = value == CheckboxValueChecked
            Row(
                modifier = modifier
                    .height(TableRowHeight)
                    .clickable {
                        onValueChange(if (isChecked) "" else CheckboxValueChecked)
                    }
                    .padding(horizontal = TableCellHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { isChecked ->
                        onValueChange(if (isChecked) CheckboxValueChecked else "")
                    },
                )
                if (isChecked) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        PageTableColumnType.Date -> {
            TableDateCellEditor(
                column = column,
                value = value,
                onValueChange = onValueChange,
                onDateSettingsChange = onDateSettingsChange,
                modifier = modifier,
            )
        }
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
        PageTableColumnType.Status,
        -> TableChoiceCellEditor(
            column = column,
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
        )
        PageTableColumnType.FilesMedia -> {
            TableMediaCellEditor(
                column = column,
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
            )
        }
        PageTableColumnType.Number,
        PageTableColumnType.Text,
        -> {
            TablePlainTextCellEditor(
                column = column,
                value = value,
                onValueChange = { nextValue ->
                    onValueChange(nextValue.toSingleLineTableCellValue())
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TablePlainTextCellEditor(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 22.sp,
        fontFamily = if (column.type == PageTableColumnType.Number) FontFamily.Monospace else FontFamily.Default,
        textAlign = if (column.type == PageTableColumnType.Number) TextAlign.End else TextAlign.Start,
    )
    val cellHeightModifier = if (column.config.wrapContent) {
        Modifier.heightIn(min = TableRowHeight, max = 112.dp)
    } else {
        Modifier.height(TableRowHeight)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(cellHeightModifier),
        singleLine = !column.config.wrapContent,
        maxLines = if (column.config.wrapContent) 4 else 1,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = when (column.type) {
                PageTableColumnType.Number -> KeyboardType.Number
                else -> KeyboardType.Text
            },
            imeAction = ImeAction.Done,
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(cellHeightModifier)
                    .padding(horizontal = TableCellHorizontalPadding, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                innerTextField()
            }
        },
    )
}

internal fun String.toSingleLineTableCellValue(): String {
    return replace("\r\n", " ")
        .replace('\n', ' ')
        .replace('\r', ' ')
}

@Composable
internal fun TableChoiceCellEditor(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedValues = remember(value) { value.selectedChoiceValues() }
    val options = column.choiceOptions

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TableRowHeight)
                .clickable { isExpanded = true }
                .padding(horizontal = TableCellHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedValues.isEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    selectedValues.take(2).forEach { selectedName ->
                        val option = options.firstOrNull { option -> option.name == selectedName }
                        TableChoiceValuePill(
                            name = selectedName,
                            color = option?.color?.toChoiceColor()
                                ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        )
                    }
                    val overflowCount = selectedValues.size - 2
                    if (overflowCount > 0) {
                        Text(
                            text = "+$overflowCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
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
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "No options yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
            }
            options.forEach { option ->
                val selected = option.name in selectedValues
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(option.color.toChoiceColor()),
                            )
                            Text(text = option.name)
                        }
                    },
                    trailingIcon = if (column.type == PageTableColumnType.MultiSelect && selected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        if (column.type == PageTableColumnType.MultiSelect) {
                            val nextValues = if (selected) {
                                selectedValues.filterNot { selectedValue -> selectedValue == option.name }
                            } else {
                                selectedValues + option.name
                            }
                            onValueChange(nextValues.toChoiceCellValue())
                        } else {
                            isExpanded = false
                            onValueChange(option.name)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TableChoiceValuePill(
    name: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .widthIn(max = 96.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun PageTableOptionColor.toChoiceColor(): Color {
    return when (this) {
        PageTableOptionColor.Gray -> Color(0xFF8A8A84)
        PageTableOptionColor.Red -> Color(0xFF5E5E59)
        PageTableOptionColor.Orange -> Color(0xFF707069)
        PageTableOptionColor.Yellow -> Color(0xFF9A9A92)
        PageTableOptionColor.Green -> Color(0xFF4F4F4A)
        PageTableOptionColor.Blue -> Color(0xFF3A3A37)
        PageTableOptionColor.Purple -> Color(0xFF6F6F68)
        PageTableOptionColor.Pink -> Color(0xFFA8A8A0)
    }
}

@Composable
internal fun ReadOnlyComputedCell(
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(TableRowHeight)
            .padding(horizontal = TableCellHorizontalPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableMediaCellEditor(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isSheetOpen by remember { mutableStateOf(false) }
    val attachments = remember(value) { value.toTableMediaAttachments() }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val selectedAttachments = uris.mapNotNull { uri ->
            context.persistMediaReadPermission(uri)
            uri.toPageMediaAttachment(context)
        }
        if (selectedAttachments.isNotEmpty()) {
            onValueChange((attachments + selectedAttachments).toTableMediaCellValue())
            isSheetOpen = true
        }
    }

    if (isSheetOpen) {
        ModalBottomSheet(onDismissRequest = { isSheetOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Files & media",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                        Text(text = "Attach", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (attachments.isEmpty()) {
                    Text(
                        text = "No files attached.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    attachments.forEach { attachment ->
                        MediaAttachmentCard(
                            attachment = attachment,
                            onOpen = { context.openMediaAttachment(attachment) },
                            onRemove = {
                                onValueChange(
                                    attachments
                                        .filterNot { candidate -> candidate.id == attachment.id }
                                        .toTableMediaCellValue(),
                                )
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    Row(
        modifier = modifier
            .height(TableRowHeight)
            .clickable { isSheetOpen = true }
            .padding(horizontal = TableCellHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (attachments.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
        Text(
            text = attachments.toTableMediaSummary(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun RelationCellEditor(
    column: PageTableColumn,
    value: String,
    tableReferences: List<PageTableReference>,
    onValueChange: (String) -> Unit,
    onRelationValueChange: (List<String>) -> Unit = { ids -> onValueChange(ids.toRelationCellValue()) },
    currentPageId: String = "",
    onCreateTargetRow: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isPickerOpen by remember { mutableStateOf(false) }
    val targetTable = tableReferences.firstOrNull { reference -> reference.blockId == column.relationTargetTableId }
    val selectedIds = remember(value) { value.relatedRowIdList() }
    val rowsById = remember(targetTable?.table?.rows) {
        targetTable?.table?.rows.orEmpty().associateBy { row -> row.id }
    }
    val selectedTitles = selectedIds
        .mapNotNull { rowId -> rowsById[rowId] }
        .map { row -> targetTable?.table?.rowTitle(row).orEmpty().ifBlank { "Untitled" } }
    val displayText = when {
        targetTable == null && column.relationTargetTableId.isBlank() -> "Set target"
        targetTable == null -> "Missing source"
        selectedTitles.isNotEmpty() -> selectedTitles.joinToString()
        else -> ""
    }

    if (isPickerOpen && targetTable != null) {
        RelationRowPickerSheet(
            targetTable = targetTable,
            selectedIds = selectedIds,
            tableReferences = tableReferences,
            currentPageId = currentPageId,
            onCreateTargetRow = onCreateTargetRow,
            onSave = { nextIds ->
                isPickerOpen = false
                onRelationValueChange(nextIds)
            },
            onDismiss = { isPickerOpen = false },
        )
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TableRowHeight)
                .clickable(enabled = targetTable != null) { isPickerOpen = true }
                .padding(horizontal = TableCellHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedTitles.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(
                text = displayText,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    targetTable == null || selectedTitles.isEmpty() ->
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationRowPickerSheet(
    targetTable: PageTableReference,
    selectedIds: List<String>,
    tableReferences: List<PageTableReference>,
    currentPageId: String,
    onCreateTargetRow: ((String) -> Unit)?,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showSelectedOnly by remember { mutableStateOf(false) }
    var selectedRowIds by remember(targetTable.blockId, selectedIds) {
        mutableStateOf(selectedIds)
    }
    val canCreateTargetRow = onCreateTargetRow != null &&
        currentPageId.isNotBlank() &&
        targetTable.pageId == currentPageId
    val rows = remember(targetTable.table.rows, query, showSelectedOnly, selectedRowIds, tableReferences) {
        val normalizedQuery = query.trim()
        val baseRows = if (showSelectedOnly) {
            targetTable.table.rows.filter { row -> row.id in selectedRowIds }
        } else {
            targetTable.table.rows
        }
        if (normalizedQuery.isBlank()) {
            baseRows
        } else {
            baseRows.filter { row ->
                targetTable.table.columns.any { column ->
                    targetTable.table.displayCellText(row, column, tableReferences)
                        .contains(normalizedQuery, ignoreCase = true)
                }
            }
        }
    }

    fun toggleRow(rowId: String) {
        selectedRowIds = if (rowId in selectedRowIds) {
            selectedRowIds.filterNot { selectedId -> selectedId == rowId }
        } else {
            selectedRowIds + rowId
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PropertySheetTitle(title = targetTable.title.ifBlank { "Select rows" })
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = showSelectedOnly,
                    onClick = { showSelectedOnly = !showSelectedOnly },
                    label = { Text(text = "Selected ${selectedRowIds.size}") },
                    leadingIcon = if (showSelectedOnly) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    enabled = canCreateTargetRow,
                    onClick = { onCreateTargetRow?.invoke(targetTable.blockId) },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "New row")
                }
            }
            if (!canCreateTargetRow && onCreateTargetRow != null && targetTable.pageId != currentPageId) {
                Text(
                    text = "Open the source database to add rows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
                placeholder = { Text(text = "Search rows") },
                colors = blockTextFieldColors(),
            )
            if (targetTable.table.rows.isEmpty()) {
                Text(
                    text = "No rows in this database.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (rows.isEmpty()) {
                Text(
                    text = "No matching rows.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        count = rows.size,
                        key = { index -> "${rows[index].id}-$index" },
                    ) { index ->
                        val row = rows[index]
                        val isSelected = row.id in selectedRowIds
                        val title = targetTable.table.rowTitle(row).ifBlank { "Untitled" }
                        val details = targetTable.table.columns
                            .drop(1)
                            .map { column -> targetTable.table.displayCellText(row, column, tableReferences) }
                            .filter { value -> value.isNotBlank() }
                            .take(2)
                            .joinToString(" • ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { toggleRow(row.id) }
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { toggleRow(row.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (details.isNotBlank()) {
                                    Text(
                                        text = details,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { selectedRowIds = emptyList() },
                ) {
                    Text(text = "Clear")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onSave(selectedRowIds) },
                ) {
                    Text(text = "Save")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun List<String>.toRelationCellValue(): String {
    return filter { value -> value.isNotBlank() }
        .distinct()
        .joinToString(",")
}
