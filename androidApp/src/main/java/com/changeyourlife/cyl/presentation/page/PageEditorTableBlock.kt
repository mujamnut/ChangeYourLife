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
internal fun DatabaseTableBlockEditor(
    tableBlockId: String,
    table: PageTable,
    tableReferences: List<PageTableReference>,
    onTitleChange: (String) -> Unit,
    onViewChange: (PageTableView) -> Unit,
    onViewConfigChange: (PageTableViewConfig) -> Unit,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (String, String) -> Unit,
    onGroupChange: (String) -> Unit,
    onColumnNameChange: (String, String) -> Unit,
    onColumnTypeChange: (String, PageTableColumnType) -> Unit,
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
    onAddColumn: (String, PageTableColumnType) -> Unit,
    onInsertColumn: (String, TableColumnInsertSide) -> Unit,
    onDuplicateColumn: (String) -> Unit,
    onDeleteColumn: (String) -> Unit,
    onAddRow: () -> Unit,
    onDeleteRow: (String) -> Unit,
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
    onIndentRowPageBlock: (String, String) -> Unit,
    onOutdentRowPageBlock: (String, String) -> Unit,
    mentionPages: List<Page> = emptyList(),
    searchTargetType: String = "",
    searchTargetId: String = "",
) {
    val horizontalScrollState = rememberScrollState()
    var openRowId by remember { mutableStateOf<String?>(null) }
    val openRow = table.rows.firstOrNull { row -> row.id == openRowId }
    val highlightedRowId = remember(table.rows, searchTargetType, searchTargetId) {
        table.highlightedRowId(searchTargetType, searchTargetId)
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
            table = table,
            row = openRow,
            tableReferences = tableReferences,
            searchTargetType = searchTargetType,
            searchTargetId = searchTargetId,
            onCellChange = onCellChange,
            onAddColumn = onAddColumn,
            onColumnDateSettingsChange = onColumnDateSettingsChange,
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
            onIndentBlock = { rowBlockId -> onIndentRowPageBlock(openRow.id, rowBlockId) },
            onOutdentBlock = { rowBlockId -> onOutdentRowPageBlock(openRow.id, rowBlockId) },
            mentionPages = mentionPages,
            onDismiss = { openRowId = null },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TableToolbar(
            table = table,
            onTitleChange = onTitleChange,
            onViewChange = onViewChange,
            onSortChange = onSortChange,
            onFilterChange = onFilterChange,
            onGroupChange = onGroupChange,
        )

        TableViewConfigControls(
            table = table,
            onViewConfigChange = onViewConfigChange,
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
                onColumnDateSettingsChange = onColumnDateSettingsChange,
                onColumnFormulaChange = onColumnFormulaChange,
                onColumnRelationTargetChange = onColumnRelationTargetChange,
                onColumnRollupChange = onColumnRollupChange,
                onCellChange = onCellChange,
                onDeleteColumn = onDeleteColumn,
                onAddColumn = onAddColumn,
                onInsertColumn = onInsertColumn,
                onDuplicateColumn = onDuplicateColumn,
                onDeleteRow = onDeleteRow,
                onAddRow = onAddRow,
                onOpenRow = { rowId -> openRowId = rowId },
                highlightedRowId = highlightedRowId,
            )
            PageTableView.List -> TableListView(table = table, tableReferences = tableReferences)
            PageTableView.Board -> TableBoardView(table = table, tableReferences = tableReferences)
            PageTableView.Calendar -> TableCalendarView(table = table, tableReferences = tableReferences)
            PageTableView.Gallery -> TableGalleryView(table = table, tableReferences = tableReferences)
            PageTableView.Timeline -> TableTimelineView(table = table, tableReferences = tableReferences)
            PageTableView.Dashboard -> TableDashboardView(table = table, tableReferences = tableReferences)
        }
    }
}

@Composable
internal fun TableToolbar(
    table: PageTable,
    onTitleChange: (String) -> Unit,
    onViewChange: (PageTableView) -> Unit,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (String, String) -> Unit,
    onGroupChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableViewSelector(
                tableTitle = table.title,
                selectedView = table.view,
                onTitleChange = onTitleChange,
                onViewChange = onViewChange,
            )
            Spacer(modifier = Modifier.weight(1f))
            TableControls(
                table = table,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onGroupChange = onGroupChange,
            )
        }
        TableActiveControlsRow(table = table)
    }
}

@Composable
internal fun TableViewSelector(
    tableTitle: String,
    selectedView: PageTableView,
    onTitleChange: (String) -> Unit,
    onViewChange: (PageTableView) -> Unit,
) {
    var isViewMenuOpen by remember { mutableStateOf(false) }
    val selectedOption = TableViewOption.entries.firstOrNull { option -> option.view == selectedView }
        ?: TableViewOption.entries.first()
    val displayTitle = tableTitle.ifBlank { "Untitled database" }

    Box {
        Row(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .clickable { isViewMenuOpen = true }
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                .padding(horizontal = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ViewColumn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = displayTitle,
                modifier = Modifier.widthIn(max = 178.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = selectedOption.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = isViewMenuOpen,
            onDismissRequest = { isViewMenuOpen = false },
        ) {
            OutlinedTextField(
                value = tableTitle,
                onValueChange = onTitleChange,
                modifier = Modifier
                    .width(300.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text(text = "Database name") },
                placeholder = { Text(text = "Untitled database") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                    )
                },
                colors = blockTextFieldColors(),
            )
            HorizontalDivider()
            TableViewOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(text = option.label)
                            if (option.view == selectedView) {
                                Text(
                                    text = "Current view",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        onViewChange(option.view)
                        isViewMenuOpen = false
                    },
                )
            }
        }
    }
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
@Composable
internal fun TableControls(
    table: PageTable,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (String, String) -> Unit,
    onGroupChange: (String) -> Unit,
) {
    if (table.columns.isEmpty()) return

    var activeControl by remember { mutableStateOf<TableControlType?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortColumn = table.columns.firstOrNull { column -> column.id == table.sort.columnId }
    val filterColumn = table.columns.firstOrNull { column -> column.id == table.filter.columnId }
    val groupColumn = table.columns.firstOrNull { column -> column.id == table.groupByColumnId }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableControlIconButton(
            icon = Icons.AutoMirrored.Rounded.Sort,
            selected = sortColumn != null,
            contentDescription = "Sort table",
            onClick = { activeControl = TableControlType.Sort },
        )
        TableControlIconButton(
            icon = Icons.Rounded.FilterList,
            selected = filterColumn != null && table.filter.query.isNotBlank(),
            contentDescription = "Filter table",
            onClick = { activeControl = TableControlType.Filter },
        )
        TableControlIconButton(
            icon = Icons.Rounded.ViewColumn,
            selected = groupColumn != null,
            contentDescription = "Group table",
            onClick = { activeControl = TableControlType.Group },
        )
    }

    activeControl?.let { control ->
        ModalBottomSheet(
            onDismissRequest = { activeControl = null },
            sheetState = sheetState,
        ) {
            TableControlSheet(
                control = control,
                table = table,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onGroupChange = onGroupChange,
                onDismiss = { activeControl = null },
            )
        }
    }
}

@Composable
internal fun TableControlIconButton(
    icon: ImageVector,
    selected: Boolean,
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
    }
}

@Composable
internal fun TableActiveControlsRow(table: PageTable) {
    val sortColumn = table.columns.firstOrNull { column -> column.id == table.sort.columnId }
    val filterColumn = table.columns.firstOrNull { column -> column.id == table.filter.columnId }
    val groupColumn = table.columns.firstOrNull { column -> column.id == table.groupByColumnId }
    val labels = buildList {
        if (sortColumn != null) {
            add("Sort ${sortColumn.name.ifBlank { "Untitled" }} ${table.sort.direction.arrowLabel}")
        }
        if (filterColumn != null && table.filter.query.isNotBlank()) {
            add("Filter ${filterColumn.name.ifBlank { "Untitled" }}: ${table.filter.query}")
        }
        if (groupColumn != null) {
            add("Group ${groupColumn.name.ifBlank { "Untitled" }}")
        }
    }
    if (labels.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(labels) { label ->
            Text(
                text = label.compactControlLabel(),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun TableControlSheet(
    control: TableControlType,
    table: PageTable,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (String, String) -> Unit,
    onGroupChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (control) {
            TableControlType.Sort -> TableSortSheet(
                table = table,
                onSortChange = onSortChange,
            )
            TableControlType.Filter -> TableFilterSheet(
                table = table,
                onFilterChange = onFilterChange,
                onDismiss = onDismiss,
            )
            TableControlType.Group -> TableGroupSheet(
                table = table,
                onGroupChange = onGroupChange,
                onDismiss = onDismiss,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
internal fun TableSortSheet(
    table: PageTable,
    onSortChange: (String, PageTableSortDirection) -> Unit,
) {
    val selectedColumnId = table.sort.columnId.ifBlank { table.columns.firstOrNull()?.id.orEmpty() }
    val selectedDirection = table.sort.direction

    TableControlSheetHeader(
        title = "Sort",
        canClear = table.sort.columnId.isNotBlank(),
        onClear = { onSortChange("", PageTableSortDirection.Ascending) },
    )
    Text(
        text = "Column",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TableColumnChoiceRow(
        columns = table.columns,
        selectedColumnId = selectedColumnId,
        onSelect = { column -> onSortChange(column.id, selectedDirection) },
    )
    Text(
        text = "Direction",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedDirection == PageTableSortDirection.Ascending,
            onClick = { onSortChange(selectedColumnId, PageTableSortDirection.Ascending) },
            label = { Text(text = "Ascending") },
        )
        FilterChip(
            selected = selectedDirection == PageTableSortDirection.Descending,
            onClick = { onSortChange(selectedColumnId, PageTableSortDirection.Descending) },
            label = { Text(text = "Descending") },
        )
    }
}

@Composable
internal fun TableFilterSheet(
    table: PageTable,
    onFilterChange: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstColumnId = table.columns.firstOrNull()?.id.orEmpty()
    var selectedColumnId by remember(table.filter.columnId, table.columns) {
        mutableStateOf(table.filter.columnId.ifBlank { firstColumnId })
    }
    var query by remember(table.filter.query) { mutableStateOf(table.filter.query) }

    TableControlSheetHeader(
        title = "Filter",
        canClear = table.filter.columnId.isNotBlank() || table.filter.query.isNotBlank(),
        onClear = {
            query = ""
            selectedColumnId = firstColumnId
            onFilterChange("", "")
        },
    )
    Text(
        text = "Column",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TableColumnChoiceRow(
        columns = table.columns,
        selectedColumnId = selectedColumnId,
        onSelect = { column -> selectedColumnId = column.id },
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(text = "Contains") },
        colors = blockTextFieldColors(),
    )
    Button(
        enabled = selectedColumnId.isNotBlank() && query.isNotBlank(),
        onClick = {
            onFilterChange(selectedColumnId, query)
            onDismiss()
        },
    ) {
        Text(text = "Apply")
    }
}

@Composable
internal fun TableGroupSheet(
    table: PageTable,
    onGroupChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    TableControlSheetHeader(
        title = "Group",
        canClear = table.groupByColumnId.isNotBlank(),
        onClear = { onGroupChange("") },
    )
    TableColumnChoiceRow(
        columns = table.columns,
        selectedColumnId = table.groupByColumnId,
        onSelect = { column ->
            onGroupChange(column.id)
            onDismiss()
        },
    )
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
                Text(text = "Clear")
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
    onFilterChange: (String, String) -> Unit,
    onGroupChange: (String) -> Unit,
    onColumnNameChange: (String, String) -> Unit,
    onColumnTypeChange: (String, PageTableColumnType) -> Unit,
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
    onDeleteColumn: (String) -> Unit,
    onAddColumn: (String, PageTableColumnType) -> Unit,
    onInsertColumn: (String, TableColumnInsertSide) -> Unit,
    onDuplicateColumn: (String) -> Unit,
    onDeleteRow: (String) -> Unit,
    onAddRow: () -> Unit,
    onOpenRow: (String) -> Unit,
    highlightedRowId: String? = null,
) {
    val visibleRows = table.visibleRows(tableReferences)
    val groupColumn = table.groupColumn()

    Column(
        modifier = Modifier.horizontalScroll(horizontalScrollState),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        TableHeaderRow(
            tableBlockId = tableBlockId,
            table = table,
            columns = table.columns,
            tableReferences = tableReferences,
            onSortChange = onSortChange,
            onFilterChange = onFilterChange,
            onGroupChange = onGroupChange,
            onColumnNameChange = onColumnNameChange,
            onColumnTypeChange = onColumnTypeChange,
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
        if (visibleRows.isEmpty()) {
            TableAddRowRow(
                columns = table.columns,
                onAddRow = onAddRow,
            )
        } else if (groupColumn != null) {
            visibleRows.groupBy { row -> table.groupLabel(row, groupColumn, tableReferences) }.forEach { (group, rows) ->
                TableGroupHeader(label = group, count = rows.size)
                rows.forEach { row ->
                    TableDataRow(
                        row = row,
                        table = table,
                        columns = table.columns,
                        tableReferences = tableReferences,
                        onColumnDateSettingsChange = onColumnDateSettingsChange,
                        onCellChange = onCellChange,
                        onDeleteRow = onDeleteRow,
                        onOpenRow = onOpenRow,
                        isHighlighted = row.id == highlightedRowId,
                    )
                }
            }
            TableAddRowRow(
                columns = table.columns,
                onAddRow = onAddRow,
            )
        } else {
            visibleRows.forEach { row ->
                TableDataRow(
                    row = row,
                    table = table,
                    columns = table.columns,
                    tableReferences = tableReferences,
                    onColumnDateSettingsChange = onColumnDateSettingsChange,
                    onCellChange = onCellChange,
                    onDeleteRow = onDeleteRow,
                    onOpenRow = onOpenRow,
                    isHighlighted = row.id == highlightedRowId,
                )
            }
            TableAddRowRow(
                columns = table.columns,
                onAddRow = onAddRow,
            )
        }
    }
}

@Composable
internal fun TableHeaderRow(
    tableBlockId: String,
    table: PageTable,
    columns: List<PageTableColumn>,
    tableReferences: List<PageTableReference>,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (String, String) -> Unit,
    onGroupChange: (String) -> Unit,
    onColumnNameChange: (String, String) -> Unit,
    onColumnTypeChange: (String, PageTableColumnType) -> Unit,
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
            onClick = { isColumnSheetOpen = true },
        )
        if (isColumnSheetOpen) {
            TableColumnEditSheet(
                currentTableBlockId = tableBlockId,
                table = table,
                column = column,
                tableReferences = tableReferences,
                onSort = {
                    onSortChange(
                        column.id,
                        if (table.sort.columnId == column.id &&
                            table.sort.direction == PageTableSortDirection.Ascending
                        ) {
                            PageTableSortDirection.Descending
                        } else {
                            PageTableSortDirection.Ascending
                        },
                    )
                },
                onFilter = { query -> onFilterChange(column.id, query) },
                onGroup = { onGroupChange(column.id) },
                onColumnNameChange = { name -> onColumnNameChange(column.id, name) },
                onColumnTypeChange = { type -> onColumnTypeChange(column.id, type) },
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
            TableOpenHeaderCell()
        }
        remainingColumns.forEach { column ->
            HeaderColumn(column)
        }
        TableAddColumnCell(onAddColumn = onAddColumn)
    }
}

@Composable
internal fun TableOpenHeaderCell() {
    Row(
        modifier = Modifier
            .width(TableOpenWidth)
            .height(TableHeaderHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Open",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TableHeaderCell(
    column: PageTableColumn,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(TableCellWidth)
            .height(TableHeaderHeight)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
        )
    }
}

@Composable
internal fun TableAddColumnCell(
    onAddColumn: (String, PageTableColumnType) -> Unit,
) {
    var isNewColumnSheetOpen by remember { mutableStateOf(false) }
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
    val canCreate = propertyName.trim().isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "New property",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
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
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = null,
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                PageTableColumnType.entries.forEachIndexed { index, type ->
                    ListItem(
                        headlineContent = { Text(text = type.label) },
                        leadingContent = {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable(enabled = canCreate) {
                            onCreateColumn(propertyName.trim(), type)
                        },
                    )
                    if (index < PageTableColumnType.entries.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }

            if (!canCreate) {
                Text(
                    text = "Enter a property name first.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableColumnEditSheet(
    currentTableBlockId: String,
    table: PageTable,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    onSort: () -> Unit,
    onFilter: (String) -> Unit,
    onGroup: () -> Unit,
    onColumnNameChange: (String) -> Unit,
    onColumnTypeChange: (PageTableColumnType) -> Unit,
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
    var filterQuery by remember(table.filter.query, table.filter.columnId, column.id) {
        mutableStateOf(if (table.filter.columnId == column.id) table.filter.query else "")
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
                    Text(
                        text = "Edit property",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    OutlinedTextField(
                        value = column.name,
                        onValueChange = onColumnNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(text = "Property name") },
                        leadingIcon = {
                            Icon(
                                imageVector = column.type.icon,
                                contentDescription = column.type.label,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = blockTextFieldColors(),
                    )

                    PropertyMenuGroup {
                        PropertyMenuRow(
                            icon = Icons.Rounded.Tune,
                            label = "Edit property",
                            value = if (column.type == PageTableColumnType.Date) {
                                column.dateFormat.label
                            } else {
                                column.configSummary(table, tableReferences)
                            },
                            onClick = {
                                detail = if (column.type == PageTableColumnType.Date) {
                                    PropertySheetDetail.DateSettings
                                } else {
                                    PropertySheetDetail.Calculate
                                }
                            },
                        )
                        PropertyMenuRow(
                            icon = Icons.Rounded.SwapVert,
                            label = "Change type",
                            value = column.type.label,
                            onClick = { detail = PropertySheetDetail.ChangeType },
                        )
                        PropertyMenuRow(
                            icon = Icons.Rounded.AutoAwesome,
                            label = "AI Autofill",
                            value = "Now with agents",
                            onClick = {},
                        )
                    }

                    PropertyMenuGroup {
                        PropertyMenuRow(
                            icon = Icons.Rounded.FilterList,
                            label = "Filter",
                            value = if (table.filter.columnId == column.id) table.filter.query else "",
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
                        PropertyMenuRow(
                            icon = Icons.Rounded.Functions,
                            label = "Calculate",
                            value = column.configSummary(table, tableReferences),
                            onClick = { detail = PropertySheetDetail.Calculate },
                        )
                        PropertyMenuRow(
                            icon = Icons.Rounded.VisibilityOff,
                            label = "Hide",
                            value = "",
                            onClick = onDismiss,
                        )
                        PropertyMenuRow(
                            icon = Icons.AutoMirrored.Rounded.WrapText,
                            label = "Unwrap content",
                            value = "",
                            onClick = onDismiss,
                        )
                    }

                    PropertyMenuGroup {
                        PropertyMenuRow(icon = Icons.AutoMirrored.Rounded.ArrowBack, label = "Insert left", onClick = onInsertLeft)
                        PropertyMenuRow(icon = Icons.AutoMirrored.Rounded.ArrowForward, label = "Insert right", onClick = onInsertRight)
                        PropertyMenuRow(icon = Icons.Rounded.ContentCopy, label = "Duplicate property", onClick = onDuplicate)
                        PropertyMenuRow(
                            icon = Icons.Rounded.Delete,
                            label = "Delete property",
                            color = MaterialTheme.colorScheme.error,
                            onClick = onDelete,
                        )
                    }
                }
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
                PropertySheetDetail.Filter -> ColumnFilterSheet(
                    column = column,
                    query = filterQuery,
                    onQueryChange = { filterQuery = it },
                    onApply = {
                        onFilter(filterQuery)
                        onDismiss()
                    },
                    onClear = {
                        filterQuery = ""
                        onFilter("")
                        onDismiss()
                    },
                    onBack = { detail = null },
                )
                PropertySheetDetail.Sort -> ColumnSortSheet(
                    column = column,
                    table = table,
                    onSort = {
                        onSort()
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
    ChangeType,
    DateSettings,
    Filter,
    Sort,
    Calculate,
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
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = color,
            )
        },
        supportingContent = if (value.isNotBlank()) {
            {
                Text(
                    text = value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.82f),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer(rotationZ = -90f),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
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
    onQueryChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    PropertyDetailHeader(title = "Filter", onBack = onBack)
    Text(
        text = column.name.ifBlank { "Untitled" },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(text = "Contains") },
        colors = blockTextFieldColors(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onClear) {
            Text(text = "Clear")
        }
        Button(
            enabled = query.isNotBlank(),
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
    onSort: () -> Unit,
    onBack: () -> Unit,
) {
    val current = if (table.sort.columnId == column.id) table.sort.direction.arrowLabel else "Off"
    PropertyDetailHeader(title = "Sort", onBack = onBack)
    Text(
        text = "${column.name.ifBlank { "Untitled" }}: $current",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSort,
    ) {
        Text(text = if (current == "Asc") "Sort descending" else "Sort ascending")
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

    OutlinedTextField(
        value = formula,
        onValueChange = { formula = it },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        placeholder = { Text(text = "{Price} * {Qty}") },
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

    if (targetTables.isEmpty()) {
        Text(
            text = "Create another table in this page first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                label = { Text(text = reference.title.ifBlank { "Untitled database" }.compactControlLabel()) },
            )
        }
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
    val selectedAggregation = column.rollupAggregation

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
            text = "Choose a target table for the relation column.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Spacer(modifier = Modifier.width(TableOpenWidth))
        Text(
            text = "$label ($count)",
            modifier = Modifier.width(TableGroupHeaderWidth),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun TableDataRow(
    row: PageTableRow,
    table: PageTable,
    columns: List<PageTableColumn>,
    tableReferences: List<PageTableReference>,
    onColumnDateSettingsChange: (
        String,
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onCellChange: (String, String, String) -> Unit,
    onDeleteRow: (String) -> Unit,
    onOpenRow: (String) -> Unit,
    isHighlighted: Boolean = false,
) {
    val primaryColumn = columns.firstOrNull()
    val remainingColumns = columns.drop(1)

    Row(
        modifier = Modifier.background(
            if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primaryColumn != null) {
            TableCellEditor(
                column = primaryColumn,
                row = row,
                table = table,
                tableReferences = tableReferences,
                value = row.cells[primaryColumn.id].orEmpty(),
                onValueChange = { value -> onCellChange(row.id, primaryColumn.id, value) },
                onDateSettingsChange = { dateFormat, timeFormat, reminder, timezoneLabel ->
                    onColumnDateSettingsChange(primaryColumn.id, dateFormat, timeFormat, reminder, timezoneLabel)
                },
                modifier = Modifier
                    .width(TableCellWidth)
                    .background(MaterialTheme.colorScheme.surface),
            )
            TableOpenRowCell(onClick = { onOpenRow(row.id) })
        } else {
            TableOpenRowCell(onClick = { onOpenRow(row.id) })
        }
        remainingColumns.forEach { column ->
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
                modifier = Modifier
                    .width(TableCellWidth)
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
internal fun TableOpenRowCell(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(TableOpenWidth)
            .height(TableRowHeight)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Open",
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TableAddRowRow(
    columns: List<PageTableColumn>,
    onAddRow: () -> Unit,
) {
    val hasPrimaryColumn = columns.isNotEmpty()
    val remainingColumnCount = (columns.size - 1).coerceAtLeast(0)

    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableAddRowCell(
            modifier = Modifier.width(TableCellWidth),
            onAddRow = onAddRow,
        )
        if (hasPrimaryColumn) {
            Box(
                modifier = Modifier
                    .width(TableOpenWidth)
                    .height(TableRowHeight)
                    .background(MaterialTheme.colorScheme.surface),
            )
            repeat(remainingColumnCount) {
                Box(
                    modifier = Modifier
                        .width(TableCellWidth)
                        .height(TableRowHeight)
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
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
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (displayText.isBlank()) column.name.ifBlank { "Date" } else displayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (displayText.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
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
    var visibleMonth by remember(value) { mutableStateOf(YearMonth.from(selectedDate)) }
    var includeEndDate by remember(value) { mutableStateOf(cellValue.includeEndDate) }
    var includeTime by remember(value, column.timeFormat) {
        mutableStateOf(cellValue.includeTime || column.timeFormat != PageTableTimeFormat.Hidden)
    }
    var selectedTime by remember(value) {
        mutableStateOf(cellValue.startTime.toLocalTimeOrNull() ?: LocalTime.of(10, 0))
    }
    var dateFormat by remember(column.dateFormat) { mutableStateOf(column.dateFormat) }
    var timeFormat by remember(column.timeFormat) { mutableStateOf(column.timeFormat) }
    var reminder by remember(column.dateReminder) { mutableStateOf(column.dateReminder) }
    var timezoneLabel by remember(column.timezoneLabel) { mutableStateOf(column.timezoneLabel) }

    fun saveCell(next: TableDateCellValue) {
        cellValue = next
        onValueChange(next.toTableDateCellStorageValue())
    }

    fun saveSettings(
        nextDateFormat: PageTableDateFormat = dateFormat,
        nextTimeFormat: PageTableTimeFormat = timeFormat,
        nextReminder: PageTableDateReminder = reminder,
        nextTimezoneLabel: String = timezoneLabel,
    ) {
        dateFormat = nextDateFormat
        timeFormat = nextTimeFormat
        reminder = nextReminder
        timezoneLabel = nextTimezoneLabel
        onDateSettingsChange(nextDateFormat, nextTimeFormat, nextReminder, nextTimezoneLabel)
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
                Text(
                    text = "?",
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Date",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DateEditorValueBox(
                    text = selectedDate.formatForColumn(dateFormat),
                    modifier = Modifier.weight(1f),
                    onClick = { visibleMonth = YearMonth.from(selectedDate) },
                )
                if (includeTime) {
                    DateTimeChoiceBox(
                        selectedTime = selectedTime,
                        timeFormat = timeFormat.visibleOrDefault(),
                        onSelect = { time ->
                            selectedTime = time
                            saveCell(
                                cellValue.copy(
                                    startDate = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                    startTime = time.format(DateTimeFormatter.ISO_LOCAL_TIME),
                                    includeTime = true,
                                    timezoneLabel = timezoneLabel,
                                ),
                            )
                        },
                    )
                }
            }

            TableDateCalendar(
                visibleMonth = visibleMonth,
                selectedDate = selectedDate,
                onPreviousMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) },
                onSelectDate = { date ->
                    selectedDate = date
                    visibleMonth = YearMonth.from(date)
                    saveCell(
                        cellValue.copy(
                            startDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            startTime = if (includeTime) {
                                selectedTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
                            } else {
                                ""
                            },
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
                        saveCell(
                            cellValue.copy(
                                includeEndDate = checked,
                                endDate = if (checked) {
                                    cellValue.endDate.ifBlank {
                                        selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    }
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
                    onSelect = { format -> saveSettings(nextDateFormat = format) },
                )
                DateToggleRow(
                    label = "Include time",
                    checked = includeTime,
                    onCheckedChange = { checked ->
                        includeTime = checked
                        val nextTimeFormat = if (checked) {
                            timeFormat.visibleOrDefault()
                        } else {
                            PageTableTimeFormat.Hidden
                        }
                        saveSettings(nextTimeFormat = nextTimeFormat)
                        saveCell(
                            cellValue.copy(
                                startTime = if (checked) {
                                    selectedTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
                                } else {
                                    ""
                                },
                                includeTime = checked,
                            ),
                        )
                    },
                )
                DateChoiceRow(
                    icon = Icons.Rounded.AccessTime,
                    label = "Time format",
                    selectedLabel = timeFormat.label,
                    items = PageTableTimeFormat.entries,
                    itemLabel = { format -> format.label },
                    onSelect = { format ->
                        includeTime = format != PageTableTimeFormat.Hidden
                        saveSettings(nextTimeFormat = format)
                        saveCell(
                            cellValue.copy(
                                startTime = if (format == PageTableTimeFormat.Hidden) {
                                    ""
                                } else {
                                    selectedTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
                                },
                                includeTime = format != PageTableTimeFormat.Hidden,
                            ),
                        )
                    },
                )
                DateChoiceRow(
                    icon = Icons.Rounded.Public,
                    label = "Timezone",
                    selectedLabel = timezoneLabel,
                    items = TableTimezoneOptions,
                    itemLabel = { timezone -> timezone },
                    onSelect = { timezone ->
                        saveSettings(nextTimezoneLabel = timezone)
                        saveCell(cellValue.copy(timezoneLabel = timezone))
                    },
                )
                DateChoiceRow(
                    icon = Icons.Rounded.Notifications,
                    label = "Remind",
                    selectedLabel = reminder.label,
                    items = PageTableDateReminder.entries,
                    itemLabel = { reminder -> reminder.label },
                    onSelect = { nextReminder -> saveSettings(nextReminder = nextReminder) },
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
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
        )
    }
}

@Composable
internal fun DateTimeChoiceBox(
    selectedTime: LocalTime,
    timeFormat: PageTableTimeFormat,
    onSelect: (LocalTime) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(150.dp)) {
        DateEditorValueBox(
            text = selectedTime.formatForColumn(timeFormat),
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TableTimeOptions.forEach { time ->
                DropdownMenuItem(
                    text = { Text(text = time.formatForColumn(timeFormat)) },
                    onClick = {
                        expanded = false
                        onSelect(time)
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
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val firstDay = visibleMonth.atDay(1)
    val firstGridDay = firstDay.minusDays((firstDay.dayOfWeek.value % 7).toLong())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
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
            supportingContent = {
                Text(
                    text = selectedLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
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
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer(rotationZ = -90f),
                )
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
                modifier = modifier,
            )
        }
        PageTableColumnType.Checkbox -> {
            Row(
                modifier = modifier
                    .height(TableRowHeight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = value == CheckboxValueChecked,
                    onCheckedChange = { isChecked ->
                        onValueChange(if (isChecked) CheckboxValueChecked else "")
                    },
                )
                Text(
                    text = if (value == CheckboxValueChecked) "Checked" else "Unchecked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        PageTableColumnType.Status -> {
            var isStatusMenuExpanded by remember { mutableStateOf(false) }
            Box(modifier = modifier) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TableRowHeight)
                        .clickable { isStatusMenuExpanded = true }
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value.ifBlank { "Select status" },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (value.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = isStatusMenuExpanded,
                    onDismissRequest = { isStatusMenuExpanded = false },
                ) {
                    TableStatusOptions.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(text = status) },
                            onClick = {
                                isStatusMenuExpanded = false
                                onValueChange(status)
                            },
                        )
                    }
                }
            }
        }
        PageTableColumnType.FilesMedia -> {
            TableMediaCellEditor(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
            )
        }
        PageTableColumnType.Number,
        PageTableColumnType.Text,
        -> {
            OutlinedTextField(
                value = value,
                onValueChange = { nextValue ->
                    onValueChange(nextValue.toSingleLineTableCellValue())
                },
                modifier = modifier.height(TableRowHeight),
                singleLine = true,
                placeholder = {
                    Text(
                        text = when (column.type) {
                            PageTableColumnType.Number -> "0"
                            PageTableColumnType.Text -> column.name
                            PageTableColumnType.Date,
                            PageTableColumnType.Formula,
                            PageTableColumnType.Relation,
                            PageTableColumnType.Rollup,
                            PageTableColumnType.Status,
                            PageTableColumnType.Checkbox,
                            PageTableColumnType.FilesMedia,
                            -> ""
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (column.type) {
                        PageTableColumnType.Number -> KeyboardType.Number
                        else -> KeyboardType.Text
                    },
                ),
                colors = plainBlockTextFieldColors(),
            )
        }
    }
}

internal fun String.toSingleLineTableCellValue(): String {
    return replace("\r\n", " ")
        .replace('\n', ' ')
        .replace('\r', ' ')
}

@Composable
internal fun ReadOnlyComputedCell(
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(TableRowHeight)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value.ifBlank { "Empty" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableMediaCellEditor(
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
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = attachments.toTableMediaSummary(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (attachments.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
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
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val targetTable = tableReferences.firstOrNull { reference -> reference.blockId == column.relationTargetTableId }
    val selectedRowId = value.split(",").firstOrNull()?.trim().orEmpty()
    val selectedRow = targetTable?.table?.rows?.firstOrNull { row -> row.id == selectedRowId }
    val displayText = when {
        targetTable == null -> "Set target"
        selectedRow != null -> targetTable.table.rowTitle(selectedRow)
        else -> "Select row"
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TableRowHeight)
                .clickable(enabled = targetTable != null) { isExpanded = true }
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayText,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (targetTable == null || selectedRow == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            targetTable?.table?.rows.orEmpty().forEach { targetRow ->
                DropdownMenuItem(
                    text = { Text(text = targetTable?.table?.rowTitle(targetRow).orEmpty().ifBlank { "Untitled" }) },
                    onClick = {
                        isExpanded = false
                        onValueChange(targetRow.id)
                    },
                )
            }
        }
    }
}
