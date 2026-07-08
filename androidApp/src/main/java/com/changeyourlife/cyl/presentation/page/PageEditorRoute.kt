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
import androidx.compose.runtime.derivedStateOf
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
import com.changeyourlife.cyl.domain.model.PageContentCodec
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
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
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
fun PageEditorRoute(
    onBack: () -> Unit,
    homeAiState: HomeUiState,
    initialSearchTargetType: String = "",
    initialSearchTargetId: String = "",
    onOpenPage: (String, String, String) -> Unit,
    onSendAiMessage: (String, List<String>, String?, List<AiImageAttachment>) -> Unit,
    onUndoAiAction: (String, String) -> Unit,
    onClearHomeAiHistory: () -> Unit,
    onCreateHomeChatSession: () -> Unit,
    onSelectHomeChatSession: (String) -> Unit,
    onDeleteHomeChatSession: (String) -> Unit,
    onDismissHomeAiError: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PageEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PageEditorScreen(
        uiState = uiState,
        homeAiState = homeAiState,
        onBack = onBack,
        onOpenPage = onOpenPage,
        initialSearchTargetType = initialSearchTargetType,
        initialSearchTargetId = initialSearchTargetId,
        onTitleChange = viewModel::updateTitle,
        onBlockTextChange = viewModel::updateBlockText,
        onBlockRichTextChange = viewModel::updateBlockRichText,
        onPasteBlocks = viewModel::pasteBlocks,
        onBlockTypeChange = viewModel::changeBlockType,
        onBlockMediaAdd = viewModel::addBlockMediaAttachments,
        onBlockMediaRemove = viewModel::removeBlockMediaAttachment,
        onToggleTodo = viewModel::toggleTodoBlock,
        onAddBlock = viewModel::addBlock,
        onAddChildBlock = viewModel::addChildBlock,
        onInsertBlockNear = viewModel::insertBlockNear,
        onCreateLinkedChildPageFromBlock = viewModel::createLinkedChildPageFromBlock,
        onDeleteBlock = viewModel::deleteBlock,
        onMoveBlockUp = viewModel::moveBlockUp,
        onMoveBlockDown = viewModel::moveBlockDown,
        onIndentBlock = viewModel::indentBlock,
        onOutdentBlock = viewModel::outdentBlock,
        onTableTitleChange = viewModel::updateTableTitle,
        onTableViewChange = viewModel::updateTableView,
        onTableViewConfigChange = viewModel::updateTableViewConfig,
        onTableDataSourceChange = viewModel::updateTableDataSource,
        onTableSortChange = viewModel::updateTableSort,
        onTableFilterChange = viewModel::updateTableFilter,
        onTableGroupChange = viewModel::updateTableGroup,
        onTableColumnNameChange = viewModel::updateTableColumnName,
        onTableColumnTypeChange = viewModel::updateTableColumnType,
        onTableColumnConfigChange = viewModel::updateTableColumnConfig,
        onTableColumnDateSettingsChange = viewModel::updateTableColumnDateSettings,
        onTableColumnFormulaChange = viewModel::updateTableColumnFormula,
        onTableColumnRelationTargetChange = viewModel::updateTableColumnRelationTarget,
        onTableColumnRollupChange = viewModel::updateTableColumnRollup,
        onTableCellChange = viewModel::updateTableCell,
        onTableRelationCellChange = viewModel::updateTableRelationCell,
        onAddTableColumn = viewModel::addTableColumn,
        onInsertTableColumn = viewModel::insertTableColumn,
        onDuplicateTableColumn = viewModel::duplicateTableColumn,
        onDeleteTableColumn = viewModel::deleteTableColumn,
        onAddTableRow = viewModel::addTableRow,
        onDeleteTableRow = viewModel::deleteTableRow,
        onDuplicateTableRow = viewModel::duplicateTableRow,
        onMoveTableRow = viewModel::moveTableRow,
        onTableRowBlockTextChange = viewModel::updateTableRowBlockText,
        onTableRowBlockRichTextChange = viewModel::updateTableRowBlockRichText,
        onTableRowBlockPasteBlocks = viewModel::pasteTableRowBlocks,
        onTableRowBlockTypeChange = viewModel::changeTableRowBlockType,
        onTableRowBlockMediaAdd = viewModel::addTableRowBlockMediaAttachments,
        onTableRowBlockMediaRemove = viewModel::removeTableRowBlockMediaAttachment,
        onToggleTableRowTodoBlock = viewModel::toggleTableRowTodoBlock,
        onAddTableRowPageBlock = viewModel::addTableRowPageBlock,
        onInsertTableRowPageBlockNear = viewModel::insertTableRowPageBlockNear,
        onCreateLinkedChildPageFromTableRowBlock = viewModel::createLinkedChildPageFromTableRowBlock,
        onDeleteTableRowPageBlock = viewModel::deleteTableRowPageBlock,
        onMoveTableRowPageBlockUp = viewModel::moveTableRowPageBlockUp,
        onMoveTableRowPageBlockDown = viewModel::moveTableRowPageBlockDown,
        onIndentTableRowPageBlock = viewModel::indentTableRowPageBlock,
        onOutdentTableRowPageBlock = viewModel::outdentTableRowPageBlock,
        onAddProperty = viewModel::addProperty,
        onPropertyNameChange = viewModel::updatePropertyName,
        onPropertyValueChange = viewModel::updatePropertyValue,
        onDeleteProperty = viewModel::deleteProperty,
        onCreateChildPage = {
            viewModel.createChildPage { page ->
                onOpenPage(page.id, "", "")
            }
        },
        onUndoEditorChange = viewModel::undoLastEditorChange,
        onKeepLocalConflict = viewModel::keepLocalConflict,
        onUseRemoteConflict = viewModel::useRemoteConflict,
        onSendAiMessage = onSendAiMessage,
        onUndoAiAction = onUndoAiAction,
        onClearHomeAiHistory = onClearHomeAiHistory,
        onCreateHomeChatSession = onCreateHomeChatSession,
        onSelectHomeChatSession = onSelectHomeChatSession,
        onDeleteHomeChatSession = onDeleteHomeChatSession,
        onDismissHomeAiError = onDismissHomeAiError,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageEditorScreen(
    uiState: PageEditorUiState,
    homeAiState: HomeUiState,
    onBack: () -> Unit,
    onOpenPage: (String, String, String) -> Unit,
    initialSearchTargetType: String = "",
    initialSearchTargetId: String = "",
    onTitleChange: (String) -> Unit,
    onBlockTextChange: (String, String) -> Unit,
    onBlockRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onPasteBlocks: (String, List<RichTextPasteBlock>) -> Unit,
    onBlockTypeChange: (String, PageBlockType) -> Unit,
    onBlockMediaAdd: (String, List<PageMediaAttachment>) -> Unit,
    onBlockMediaRemove: (String, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onAddBlock: (PageBlockType) -> Unit,
    onAddChildBlock: (String, PageBlockType) -> Unit,
    onInsertBlockNear: (String, PageBlockType, PageBlockInsertPosition) -> Unit,
    onCreateLinkedChildPageFromBlock: (String) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onMoveBlockUp: (String) -> Unit,
    onMoveBlockDown: (String) -> Unit,
    onIndentBlock: (String) -> Unit,
    onOutdentBlock: (String) -> Unit,
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
    onAddProperty: (PagePropertyType, String) -> Unit,
    onPropertyNameChange: (String, String) -> Unit,
    onPropertyValueChange: (String, String) -> Unit,
    onDeleteProperty: (String) -> Unit,
    onCreateChildPage: () -> Unit,
    onUndoEditorChange: () -> Unit,
    onKeepLocalConflict: () -> Unit,
    onUseRemoteConflict: () -> Unit,
    onSendAiMessage: (String, List<String>, String?, List<AiImageAttachment>) -> Unit,
    onUndoAiAction: (String, String) -> Unit,
    onClearHomeAiHistory: () -> Unit,
    onCreateHomeChatSession: () -> Unit,
    onSelectHomeChatSession: (String) -> Unit,
    onDeleteHomeChatSession: (String) -> Unit,
    onDismissHomeAiError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isAiChatSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isPageSearchSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isBlockPickerSheetOpen by rememberSaveable { mutableStateOf(false) }
    var activeBlockId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorFocusScope by rememberSaveable { mutableStateOf(PageEditorFocusScope.None) }
    var richTextToolbarState by remember { mutableStateOf<RichTextToolbarUiState?>(null) }
    var focusRequestSequence by rememberSaveable { mutableStateOf(0L) }
    var editorFocusRequest by remember { mutableStateOf<EditorBlockFocusRequest?>(null) }
    val pageListState = rememberLazyListState()
    val density = LocalDensity.current
    val collapsedTitleThresholdPx = with(density) { 56.dp.toPx().toInt() }
    val showTopBarTitle by remember(pageListState, collapsedTitleThresholdPx) {
        derivedStateOf {
            pageListState.firstVisibleItemIndex > 0 ||
                pageListState.firstVisibleItemScrollOffset > collapsedTitleThresholdPx
        }
    }
    val hasDatabaseBlock = remember(uiState.blocks) {
        uiState.blocks.containsDatabaseTableBlock()
    }

    fun requestEditorFocus(
        blockId: String?,
        focusScope: PageEditorFocusScope = PageEditorFocusScope.Block,
    ) {
        if (blockId.isNullOrBlank()) return
        focusRequestSequence += 1
        editorFocusRequest = EditorBlockFocusRequest(
            blockId = blockId,
            token = focusRequestSequence,
        )
        activeBlockId = blockId
        editorFocusScope = focusScope
    }

    fun deleteBlockAndFocusSibling(blockId: String) {
        val targetBlockId = uiState.blocks.editorFocusTargetAfterDeleting(blockId)
        onDeleteBlock(blockId)
        requestEditorFocus(targetBlockId)
        if (targetBlockId == null) {
            activeBlockId = null
            richTextToolbarState = null
            editorFocusScope = PageEditorFocusScope.Body
        }
    }

    LaunchedEffect(uiState.blocks, activeBlockId) {
        val currentActiveBlockId = activeBlockId
        if (currentActiveBlockId != null && !uiState.blocks.containsBlockId(currentActiveBlockId)) {
            activeBlockId = null
            richTextToolbarState = null
            if (editorFocusScope == PageEditorFocusScope.Block) {
                editorFocusScope = PageEditorFocusScope.Body
            }
        }
    }

    LaunchedEffect(uiState.blocks, editorFocusRequest) {
        val currentFocusRequest = editorFocusRequest
        if (
            currentFocusRequest != null &&
            !uiState.blocks.containsFocusableEditorBlock(currentFocusRequest.blockId)
        ) {
            editorFocusRequest = null
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            PageEditorTopBar(
                pageTitle = if (showTopBarTitle) uiState.title else "",
                isSaving = uiState.isSaving,
                isAiGenerating = homeAiState.isAiGeneratingChat,
                syncState = uiState.syncState,
                onBack = onBack,
            )
        },
        bottomBar = {
            PageEditorBottomBar(
                activeBlockId = activeBlockId,
                focusScope = editorFocusScope,
                canAddDatabaseFromHeader = !hasDatabaseBlock,
                canUndoEditorChange = uiState.canUndoEditorChange,
                richTextToolbarState = richTextToolbarState,
                onAddBlock = onAddBlock,
                onAddDatabaseFromHeader = {
                    if (!hasDatabaseBlock) {
                        val emptyBodyBlockId = uiState.blocks.firstOrNull { block ->
                            block.type == PageBlockType.Text &&
                                block.text.isBlank() &&
                                block.richTextSpans.isEmpty() &&
                                block.mediaAttachments.isEmpty() &&
                                block.children.isEmpty()
                        }?.id
                        emptyBodyBlockId?.let { blockId ->
                            onBlockTypeChange(blockId, PageBlockType.DatabaseTable)
                        } ?: onAddBlock(PageBlockType.DatabaseTable)
                    }
                },
                onChangeActiveBlockType = { type ->
                    activeBlockId?.let { blockId -> onBlockTypeChange(blockId, type) } ?: onAddBlock(type)
                },
                onAddChildToActiveBlock = { type ->
                    activeBlockId?.let { blockId -> onAddChildBlock(blockId, type) }
                },
                onInsertTextAboveActiveBlock = {
                    activeBlockId?.let { blockId -> onInsertBlockNear(blockId, PageBlockType.Text, PageBlockInsertPosition.Above) }
                },
                onInsertTextBelowActiveBlock = {
                    activeBlockId?.let { blockId -> onInsertBlockNear(blockId, PageBlockType.Text, PageBlockInsertPosition.Below) }
                },
                onMoveActiveBlockUp = {
                    activeBlockId?.let(onMoveBlockUp)
                },
                onMoveActiveBlockDown = {
                    activeBlockId?.let(onMoveBlockDown)
                },
                onIndentActiveBlock = {
                    activeBlockId?.let(onIndentBlock)
                },
                onOutdentActiveBlock = {
                    activeBlockId?.let(onOutdentBlock)
                },
                onCreateLinkedPageFromActiveBlock = {
                    activeBlockId?.let(onCreateLinkedChildPageFromBlock)
                },
                onDeleteActiveBlock = {
                    activeBlockId?.let(::deleteBlockAndFocusSibling)
                },
                onUndoEditorChange = onUndoEditorChange,
                onSearch = { isPageSearchSheetOpen = true },
                onOpenAi = {
                    isAiChatSheetOpen = true
                },
                onCreateBlock = { isBlockPickerSheetOpen = true },
                onClearEditorFocus = {
                    activeBlockId = null
                    richTextToolbarState = null
                    editorFocusScope = PageEditorFocusScope.None
                },
            )
        },
    ) { innerPadding ->
        if (isAiChatSheetOpen) {
            AiChatSheet(
                messages = homeAiState.chatMessages,
                mentionPages = homeAiState.allPages,
                chatSessions = homeAiState.chatSessions,
                activeChatSessionId = homeAiState.activeChatSessionId,
                isGenerating = homeAiState.isAiGeneratingChat,
                errorMessage = homeAiState.aiChatError,
                modelLabel = homeAiState.aiModelLabel,
                onSendMessage = { message, mentionedPageIds, images ->
                    onSendAiMessage(message, mentionedPageIds, uiState.page?.id, images)
                },
                onUndoAction = onUndoAiAction,
                onClearHistory = onClearHomeAiHistory,
                onCreateChatSession = onCreateHomeChatSession,
                onSelectChatSession = onSelectHomeChatSession,
                onDeleteChatSession = onDeleteHomeChatSession,
                onDismissError = onDismissHomeAiError,
                onOpenPage = { pageId, targetType, targetId ->
                    isAiChatSheetOpen = false
                    onOpenPage(pageId, targetType, targetId)
                },
                onDismiss = { isAiChatSheetOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                attachedPageId = uiState.page?.id,
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
        if (isBlockPickerSheetOpen) {
            PageBlockPickerSheet(
                onAddBlock = { type ->
                    onAddBlock(type)
                    isBlockPickerSheetOpen = false
                },
                onDismiss = { isBlockPickerSheetOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            )
        }
        when {
            uiState.isLoading -> {
                CenterMessage(
                    text = "Loading page",
                    contentPadding = innerPadding,
                )
            }

            uiState.page == null -> {
                CenterMessage(
                    text = "Page not found",
                    contentPadding = innerPadding,
                )
            }

            else -> {
                val currentPage = uiState.page
                val isSubpage = currentPage.parentPageId != null
                val tableReferences = remember(currentPage.id, uiState.title, uiState.blocks, homeAiState.allPages) {
                    val currentPageId = currentPage.id
                    val currentPageTitle = uiState.title.ifBlank { "Untitled page" }
                    val currentPageTables = uiState.blocks.tableReferences().map { reference ->
                        reference.copy(
                            pageId = currentPageId,
                            pageTitle = currentPageTitle,
                        )
                    }
                    val otherPageTables = homeAiState.allPages
                        .filter { page -> page.id != currentPageId && page.deletedAt == null }
                        .flatMap { page ->
                            PageContentCodec.decode(page.content).tableReferences().map { reference ->
                                reference.copy(
                                    pageId = page.id,
                                    pageTitle = page.title.ifBlank { "Untitled page" },
                                )
                            }
                        }
                    (currentPageTables + otherPageTables).distinctBy { reference -> reference.blockId }
                }
                val searchTargetIndex = remember(
                    uiState.blocks,
                    initialSearchTargetType,
                    initialSearchTargetId,
                ) {
                    uiState.blocks.indexOfSearchTarget(
                        targetType = initialSearchTargetType,
                        targetId = initialSearchTargetId,
                    )
                }

                LaunchedEffect(
                    uiState.isLoading,
                    searchTargetIndex,
                    initialSearchTargetType,
                    initialSearchTargetId,
                ) {
                    if (!uiState.isLoading && initialSearchTargetType == SearchTargetPageTitle) {
                        pageListState.animateScrollToItem(0)
                    } else if (!uiState.isLoading && searchTargetIndex >= 0) {
                        pageListState.animateScrollToItem(searchTargetIndex + 1)
                    }
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    state = pageListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 22.dp,
                        top = 8.dp,
                        end = 22.dp,
                        bottom = 18.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item {
                        PageTitleEditor(
                            title = uiState.title,
                            onTitleChange = onTitleChange,
                            onFocusChanged = { isFocused ->
                                if (isFocused) {
                                    activeBlockId = null
                                    richTextToolbarState = null
                                    editorFocusScope = PageEditorFocusScope.Header
                                }
                            },
                        )
                    }

                    if (uiState.syncState.hasConflict) {
                        item(key = "page-sync-conflict") {
                            PageSyncConflictBanner(
                                onKeepLocal = onKeepLocalConflict,
                                onUseRemote = onUseRemoteConflict,
                            )
                        }
                    }

                    items(
                        items = uiState.blocks,
                        key = { block -> block.id },
                    ) { block ->
                        PageEditorBlock(
                            block = block,
                            pageId = uiState.page.id,
                            pageUpdatedAt = uiState.page.updatedAt,
                            syncState = uiState.syncState,
                            isSaving = uiState.isSaving,
                            isFirstBlock = uiState.blocks.firstOrNull()?.id == block.id,
                            indentLevel = 0,
                            onTextChange = onBlockTextChange,
                            onRichTextChange = onBlockRichTextChange,
                            onPasteBlocks = onPasteBlocks,
                            onRichTextToolbarChange = { toolbarState ->
                                richTextToolbarState = toolbarState
                            },
                            onBlockTypeChange = onBlockTypeChange,
                            onMediaAdd = onBlockMediaAdd,
                            onMediaRemove = onBlockMediaRemove,
                            onToggleTodo = onToggleTodo,
                            onDelete = ::deleteBlockAndFocusSibling,
                            onMoveUp = onMoveBlockUp,
                            onMoveDown = onMoveBlockDown,
                            onIndentBlock = onIndentBlock,
                            onOutdentBlock = onOutdentBlock,
                            onBlockFocused = { blockId ->
                                activeBlockId = blockId
                                editorFocusScope = PageEditorFocusScope.Body
                            },
                            focusRequest = editorFocusRequest,
                            activeBlockId = activeBlockId,
                            onAddChildBlock = onAddChildBlock,
                            onInsertBlockNear = onInsertBlockNear,
                            onCreateLinkedChildPageFromBlock = onCreateLinkedChildPageFromBlock,
                            mentionPages = homeAiState.allPages,
                            onTableTitleChange = onTableTitleChange,
                            onTableViewChange = onTableViewChange,
                            onTableViewConfigChange = onTableViewConfigChange,
                            onTableDataSourceChange = onTableDataSourceChange,
                            onTableSortChange = onTableSortChange,
                            onTableFilterChange = onTableFilterChange,
                            onTableGroupChange = onTableGroupChange,
                            onTableColumnNameChange = onTableColumnNameChange,
                            onTableColumnTypeChange = onTableColumnTypeChange,
                            onTableColumnConfigChange = onTableColumnConfigChange,
                            onTableColumnDateSettingsChange = onTableColumnDateSettingsChange,
                            onTableColumnFormulaChange = onTableColumnFormulaChange,
                            onTableColumnRelationTargetChange = onTableColumnRelationTargetChange,
                            onTableColumnRollupChange = onTableColumnRollupChange,
                            onTableCellChange = onTableCellChange,
                            onTableRelationCellChange = onTableRelationCellChange,
                            onAddTableColumn = onAddTableColumn,
                            onInsertTableColumn = onInsertTableColumn,
                            onDuplicateTableColumn = onDuplicateTableColumn,
                            onDeleteTableColumn = onDeleteTableColumn,
                            onAddTableRow = onAddTableRow,
                            onDeleteTableRow = onDeleteTableRow,
                            onDuplicateTableRow = onDuplicateTableRow,
                            onMoveTableRow = onMoveTableRow,
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
                            onMoveTableRowPageBlockUp = onMoveTableRowPageBlockUp,
                            onMoveTableRowPageBlockDown = onMoveTableRowPageBlockDown,
                            onIndentTableRowPageBlock = onIndentTableRowPageBlock,
                            onOutdentTableRowPageBlock = onOutdentTableRowPageBlock,
                            tableReferences = tableReferences,
                            searchTargetType = initialSearchTargetType,
                            searchTargetId = initialSearchTargetId,
                        )
                    }

                    if (!hasDatabaseBlock) {
                        item(key = "page-body-tap-target") {
                            val bodyTapInteractionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clickable(
                                        interactionSource = bodyTapInteractionSource,
                                        indication = null,
                                    ) {
                                        richTextToolbarState = null
                                        uiState.blocks.firstFocusableEditorBlockId()?.let { blockId ->
                                            requestEditorFocus(blockId, PageEditorFocusScope.Body)
                                        }
                                            ?: run {
                                                activeBlockId = null
                                                editorFocusScope = PageEditorFocusScope.Body
                                                onAddBlock(PageBlockType.Text)
                                            }
                                    },
                            )
                        }
                    }

                }
            }
        }
    }
}
