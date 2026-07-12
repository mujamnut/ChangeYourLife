package com.changeyourlife.cyl.presentation.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageTable
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
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import com.changeyourlife.cyl.presentation.ai.AiChatSheet
import com.changeyourlife.cyl.presentation.ai.AiPersonaUiState
import com.changeyourlife.cyl.presentation.components.CylBottomCommandBar
import com.changeyourlife.cyl.presentation.components.CylChromeIconButton
import com.changeyourlife.cyl.presentation.home.HomeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageDatabaseSurface(
    databaseBlock: PageBlock,
    uiState: PageEditorUiState,
    homeAiState: HomeUiState,
    tableReferences: List<PageTableReference>,
    databaseRenderStates: Map<String, InlineDatabaseTableRenderState>,
    tableHorizontalScrollStates: androidx.compose.runtime.snapshots.SnapshotStateMap<String, ScrollState>,
    onSearchQueryChange: (String, String) -> Unit,
    tableOpenRowIds: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String?>,
    tableActiveEditingCellKeys: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String?>,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTableTitleChange: (String, String) -> Unit,
    onTableViewChange: (String, PageTableView) -> Unit,
    onTableViewConfigChange: (String, PageTableViewConfig) -> Unit,
    onTableDataSourceChange: (String, PageTableReference?) -> Unit,
    onTableSortChange: (String, String, PageTableSortDirection) -> Unit,
    onTableFilterChange: (String, PageTableFilter) -> Unit,
    onTableGroupChange: (String, String) -> Unit,
    onTableColumnNameChange: (String, String, String) -> Unit,
    onTableColumnTypeChange: (String, String, PageTableColumnType) -> Unit,
    onTableColumnConfigChange: (String, String, PageTableColumnConfig) -> Unit,
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
    onTableRelationCellChange: (String, String, String, List<String>) -> Unit,
    onAddTableColumn: (String, String, PageTableColumnType) -> Unit,
    onInsertTableColumn: (String, String, TableColumnInsertSide) -> Unit,
    onDuplicateTableColumn: (String, String) -> Unit,
    onDeleteTableColumn: (String, String) -> Unit,
    onAddTableRow: (String) -> Unit,
    onDeleteTableRow: (String, String) -> Unit,
    onDuplicateTableRow: (String, String) -> Unit,
    onMoveTableRow: (String, String, Int) -> Unit,
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
    onMoveTableRowPageBlockUp: (String, String, String) -> Unit,
    onMoveTableRowPageBlockDown: (String, String, String) -> Unit,
    onIndentTableRowPageBlock: (String, String, String) -> Unit,
    onOutdentTableRowPageBlock: (String, String, String) -> Unit,
    onKeepLocalConflict: () -> Unit,
    onUseRemoteConflict: () -> Unit,
    onSendAiMessage: (String, List<String>, String?, List<AiImageAttachment>) -> Unit,
    onUndoAiAction: (String, String) -> Unit,
    onClearHomeAiHistory: () -> Unit,
    onCreateHomeChatSession: () -> Unit,
    onSelectHomeChatSession: (String) -> Unit,
    onDeleteHomeChatSession: (String) -> Unit,
    onDismissHomeAiError: () -> Unit,
    onOpenAiHistory: () -> Unit,
    onOpenAiProfile: () -> Unit,
    onDismissHomeAiErrorClick: () -> Unit,
    initialSearchTargetType: String = "",
    initialSearchTargetId: String = "",
    aiPersona: AiPersonaUiState = AiPersonaUiState(),
    modifier: Modifier = Modifier,
) {
    var isAiChatSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isPageSearchSheetOpen by rememberSaveable { mutableStateOf(false) }
    val aiChatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val table = databaseBlock.table
    val tableBlockId = databaseBlock.id
    val scrollState = tableHorizontalScrollStates[tableBlockId] ?: ScrollState(0).also { tableHorizontalScrollStates[tableBlockId] = it }
    val openRowId = tableOpenRowIds[tableBlockId]
    val activeEditingCellKey = tableActiveEditingCellKeys[tableBlockId]

    val renderState = databaseRenderStates[tableBlockId] ?: table.buildInlineDatabaseTableRenderState(
        tableReferences = tableReferences,
        tableSearchInput = "",
        searchTargetType = initialSearchTargetType,
        searchTargetId = initialSearchTargetId,
    )
    val searchInput = renderState.tableSearchQuery

    val visibleColumns = renderState.visibleColumns
    val columnWidths = renderState.columnWidths

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            PageEditorTopBar(
                pageTitle = "",
                isSaving = uiState.isSaving,
                isAiGenerating = homeAiState.isAiGeneratingChat,
                syncState = uiState.syncState,
                onBack = onBack,
            )
        },
        bottomBar = {
            CylBottomCommandBar(
                modifier = Modifier,
                centerLabel = "Ask AI",
                centerIcon = Icons.Rounded.AutoAwesome,
                centerContentDescription = "Ask AI about this page",
                onCenterClick = { isAiChatSheetOpen = true },
                leadingActions = {
                    CylChromeIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search page",
                        onClick = { isPageSearchSheetOpen = true },
                    )
                },
                trailingActions = {}
            )
        }
    ) { innerPadding ->
        if (isAiChatSheetOpen) {
            val currentPageId = uiState.page?.id
            AiChatSheet(
                messages = homeAiState.chatMessages,
                mentionPages = homeAiState.allPages,
                persona = aiPersona,
                isGenerating = homeAiState.isAiGeneratingChat,
                errorMessage = homeAiState.aiChatError,
                modelLabel = homeAiState.aiModelLabel,
                visionStatusLabel = homeAiState.aiVisionStatusLabel,
                visionPipelineLabel = homeAiState.aiVisionPipelineLabel,
                onSendMessage = { message, mentionedPageIds, images ->
                    onSendAiMessage(message, mentionedPageIds, currentPageId, images)
                },
                onUndoAction = onUndoAiAction,
                onClearHistory = onClearHomeAiHistory,
                onCreateChatSession = onCreateHomeChatSession,
                onOpenHistoryPage = {
                    isAiChatSheetOpen = false
                    onOpenAiHistory()
                },
                onOpenProfilePage = {
                    isAiChatSheetOpen = false
                    onOpenAiProfile()
                },
                onDismissError = onDismissHomeAiError,
                onOpenPage = { pageId, _, _ ->
                    isAiChatSheetOpen = false
                    onBack()
                },
                onDismiss = { isAiChatSheetOpen = false },
                sheetState = aiChatSheetState,
                attachedPageId = currentPageId,
                attachedPageTitle = uiState.title.ifBlank { "Untitled page" },
            )
        }
        if (isPageSearchSheetOpen) {
            PageSearchSheet(
                pageTitle = uiState.title.ifBlank { "Untitled page" },
                properties = uiState.properties,
                blocks = uiState.blocks,
                onDismiss = { isPageSearchSheetOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(
                key = "database-page-title",
                contentType = "database-page-title",
            ) {
                PageTitleEditor(
                    title = uiState.title,
                    onTitleChange = onTitleChange,
                    onFocusChanged = {},
                )
            }

            if (uiState.syncState.hasConflict) {
                item(
                    key = "database-sync-conflict",
                    contentType = "database-sync-conflict",
                ) {
                    PageSyncConflictBanner(
                        onKeepLocal = onKeepLocalConflict,
                        onUseRemote = onUseRemoteConflict,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            item(
                key = "database-toolbar",
                contentType = "database-toolbar",
            ) {
                TableToolbar(
                    table = table,
                    tableBlockId = tableBlockId,
                    syncState = uiState.syncState,
                    isSaving = uiState.isSaving,
                    tableReferences = tableReferences,
                    onTitleChange = { title -> onTableTitleChange(tableBlockId, title) },
                    onViewChange = { view -> onTableViewChange(tableBlockId, view) },
                    onViewConfigChange = { config -> onTableViewConfigChange(tableBlockId, config) },
                    onDataSourceChange = { source -> onTableDataSourceChange(tableBlockId, source) },
                    onColumnConfigChange = { columnId, config -> onTableColumnConfigChange(tableBlockId, columnId, config) },
                    onSortChange = { columnId, direction -> onTableSortChange(tableBlockId, columnId, direction) },
                    onFilterChange = { filter -> onTableFilterChange(tableBlockId, filter) },
                    onGroupChange = { columnId -> onTableGroupChange(tableBlockId, columnId) },
                    searchQuery = searchInput,
                    onSearchQueryChange = { query -> onSearchQueryChange(tableBlockId, query) },
                )
            }

            item(
                key = "database-active-controls",
                contentType = "database-active-controls",
            ) {
                TableActiveControlsRow(
                    table = table,
                    searchQuery = searchInput,
                    onClearSort = { onTableSortChange(tableBlockId, "", PageTableSortDirection.Ascending) },
                    onClearFilter = { onTableFilterChange(tableBlockId, PageTableFilter()) },
                    onClearGroup = { onTableGroupChange(tableBlockId, "") },
                    onClearSearch = { onSearchQueryChange(tableBlockId, "") },
                    onClearAll = {
                        onSearchQueryChange(tableBlockId, "")
                        onTableSortChange(tableBlockId, "", PageTableSortDirection.Ascending)
                        onTableFilterChange(tableBlockId, PageTableFilter())
                        onTableGroupChange(tableBlockId, "")
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (table.view == PageTableView.Table) {
                item(
                    key = "database-table-header",
                    contentType = "database-table-header",
                ) {
                    Box(modifier = Modifier.horizontalScroll(scrollState)) {
                        TableHeaderRow(
                            tableBlockId = tableBlockId,
                            table = table,
                            columns = visibleColumns,
                            columnWidths = columnWidths,
                            tableReferences = tableReferences,
                            onSortChange = { columnId, direction -> onTableSortChange(tableBlockId, columnId, direction) },
                            onFilterChange = { filter -> onTableFilterChange(tableBlockId, filter) },
                            onGroupChange = { columnId -> onTableGroupChange(tableBlockId, columnId) },
                            onColumnNameChange = { columnId, name -> onTableColumnNameChange(tableBlockId, columnId, name) },
                            onColumnTypeChange = { columnId, type -> onTableColumnTypeChange(tableBlockId, columnId, type) },
                            onColumnConfigChange = { columnId, config -> onTableColumnConfigChange(tableBlockId, columnId, config) },
                            onColumnDateSettingsChange = { columnId, dateFormat, timeFormat, reminder, timezoneLabel ->
                                onTableColumnDateSettingsChange(tableBlockId, columnId, dateFormat, timeFormat, reminder, timezoneLabel)
                            },
                            onColumnFormulaChange = { columnId, formula -> onTableColumnFormulaChange(tableBlockId, columnId, formula) },
                            onColumnRelationTargetChange = { columnId, targetTableId -> onTableColumnRelationTargetChange(tableBlockId, columnId, targetTableId) },
                            onColumnRollupChange = { columnId, relationColumnId, targetColumnId, aggregation -> onTableColumnRollupChange(tableBlockId, columnId, relationColumnId, targetColumnId, aggregation) },
                            onDeleteColumn = { columnId -> onDeleteTableColumn(tableBlockId, columnId) },
                            onAddColumn = { name, type -> onAddTableColumn(tableBlockId, name, type) },
                            onInsertColumn = { columnId, side -> onInsertTableColumn(tableBlockId, columnId, side) },
                            onDuplicateColumn = { columnId -> onDuplicateTableColumn(tableBlockId, columnId) },
                        )
                    }
                }

                item(
                    key = "database-table-divider",
                    contentType = "database-table-divider",
                ) {
                    HorizontalDivider()
                }
            }

            inlineDatabaseTableBlockEditor(
                horizontalScrollState = scrollState,
                tableSearchInput = searchInput,
                onSearchQueryChange = { query -> onSearchQueryChange(tableBlockId, query) },
                openRowId = openRowId,
                onOpenRowIdChange = { rowId -> tableOpenRowIds[tableBlockId] = rowId },
                tableBlockId = tableBlockId,
                pageId = uiState.page!!.id,
                pageUpdatedAt = uiState.page.updatedAt,
                syncState = uiState.syncState,
                isSaving = uiState.isSaving,
                table = table,
                tableReferences = tableReferences,
                renderState = renderState,
                activeEditingCellKey = activeEditingCellKey,
                onActiveEditingCellKeyChange = { cellKey ->
                    tableActiveEditingCellKeys[tableBlockId] = cellKey
                },
                onTitleChange = { title -> onTableTitleChange(tableBlockId, title) },
                onViewChange = { view -> onTableViewChange(tableBlockId, view) },
                onViewConfigChange = { config -> onTableViewConfigChange(tableBlockId, config) },
                onDataSourceChange = { source -> onTableDataSourceChange(tableBlockId, source) },
                onSortChange = { columnId, direction -> onTableSortChange(tableBlockId, columnId, direction) },
                onFilterChange = { filter -> onTableFilterChange(tableBlockId, filter) },
                onGroupChange = { columnId -> onTableGroupChange(tableBlockId, columnId) },
                onColumnNameChange = { columnId, name -> onTableColumnNameChange(tableBlockId, columnId, name) },
                onColumnTypeChange = { columnId, type -> onTableColumnTypeChange(tableBlockId, columnId, type) },
                onColumnConfigChange = { columnId, config -> onTableColumnConfigChange(tableBlockId, columnId, config) },
                onColumnDateSettingsChange = { columnId, dateFormat, timeFormat, reminder, timezoneLabel ->
                    onTableColumnDateSettingsChange(tableBlockId, columnId, dateFormat, timeFormat, reminder, timezoneLabel)
                },
                onColumnFormulaChange = { columnId, formula -> onTableColumnFormulaChange(tableBlockId, columnId, formula) },
                onColumnRelationTargetChange = { columnId, targetTableId -> onTableColumnRelationTargetChange(tableBlockId, columnId, targetTableId) },
                onColumnRollupChange = { columnId, relationColumnId, targetColumnId, aggregation -> onTableColumnRollupChange(tableBlockId, columnId, relationColumnId, targetColumnId, aggregation) },
                onCellChange = { rowId, columnId, value -> onTableCellChange(tableBlockId, rowId, columnId, value) },
                onRelationCellChange = { rowId, columnId, relationRowIds -> onTableRelationCellChange(tableBlockId, rowId, columnId, relationRowIds) },
                onAddRelationTargetRow = { targetTableBlockId -> onAddTableRow(targetTableBlockId) },
                onAddColumn = { name, type -> onAddTableColumn(tableBlockId, name, type) },
                onInsertColumn = { columnId, side -> onInsertTableColumn(tableBlockId, columnId, side) },
                onDuplicateColumn = { columnId -> onDuplicateTableColumn(tableBlockId, columnId) },
                onDeleteColumn = { columnId -> onDeleteTableColumn(tableBlockId, columnId) },
                onAddRow = { onAddTableRow(tableBlockId) },
                onDeleteRow = { rowId -> onDeleteTableRow(tableBlockId, rowId) },
                onDuplicateRow = { rowId -> onDuplicateTableRow(tableBlockId, rowId) },
                onMoveRow = { rowId, targetIndex -> onMoveTableRow(tableBlockId, rowId, targetIndex) },
                onRowBlockTextChange = { rowId, rowBlockId, text -> onTableRowBlockTextChange(tableBlockId, rowId, rowBlockId, text) },
                onRowBlockRichTextChange = { rowId, rowBlockId, text, spans -> onTableRowBlockRichTextChange(tableBlockId, rowId, rowBlockId, text, spans) },
                onRowBlockPasteBlocks = { rowId, rowBlockId, pasteBlocks -> onTableRowBlockPasteBlocks(tableBlockId, rowId, rowBlockId, pasteBlocks) },
                onRowBlockTypeChange = { rowId, rowBlockId, type -> onTableRowBlockTypeChange(tableBlockId, rowId, rowBlockId, type) },
                onRowBlockMediaAdd = { rowId, rowBlockId, attachments -> onTableRowBlockMediaAdd(tableBlockId, rowId, rowBlockId, attachments) },
                onRowBlockMediaRemove = { rowId, rowBlockId, attachmentId ->
                    onTableRowBlockMediaRemove(tableBlockId, rowId, rowBlockId, attachmentId)
                },
                onToggleRowTodoBlock = { rowId, rowBlockId -> onToggleTableRowTodoBlock(tableBlockId, rowId, rowBlockId) },
                onAddRowPageBlock = { rowId, type -> onAddTableRowPageBlock(tableBlockId, rowId, type) },
                onInsertRowPageBlockNear = { rowId, rowBlockId, type, position -> onInsertTableRowPageBlockNear(tableBlockId, rowId, rowBlockId, type, position) },
                onCreateRowLinkedPage = { rowId, rowBlockId -> onCreateLinkedChildPageFromTableRowBlock(tableBlockId, rowId, rowBlockId) },
                onDeleteRowPageBlock = { rowId, rowBlockId -> onDeleteTableRowPageBlock(tableBlockId, rowId, rowBlockId) },
                onMoveRowPageBlockUp = { rowId, rowBlockId -> onMoveTableRowPageBlockUp(tableBlockId, rowId, rowBlockId) },
                onMoveRowPageBlockDown = { rowId, rowBlockId -> onMoveTableRowPageBlockDown(tableBlockId, rowId, rowBlockId) },
                onIndentRowPageBlock = { rowId, rowBlockId -> onIndentTableRowPageBlock(tableBlockId, rowId, rowBlockId) },
                onOutdentRowPageBlock = { rowId, rowBlockId -> onOutdentTableRowPageBlock(tableBlockId, rowId, rowBlockId) },
                mentionPages = homeAiState.allPages,
                searchTargetType = initialSearchTargetType,
                searchTargetId = initialSearchTargetId,
                isFullPage = true,
            )
        }
    }
}
