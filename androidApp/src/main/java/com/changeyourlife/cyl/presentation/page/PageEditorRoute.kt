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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.presentation.ai.AiChatSheet
import com.changeyourlife.cyl.presentation.ai.AiChatMessage
import com.changeyourlife.cyl.presentation.ai.AiChatPageLink
import com.changeyourlife.cyl.presentation.components.CylBottomCommandBar
import com.changeyourlife.cyl.presentation.components.CylChromeIconButton
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
    onSendHomeAiMessage: (String) -> Unit,
    onClearHomeAiHistory: () -> Unit,
    onCreateHomeChatSession: () -> Unit,
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
        onBlockMediaAdd = viewModel::addBlockMediaAttachments,
        onBlockMediaRemove = viewModel::removeBlockMediaAttachment,
        onToggleTodo = viewModel::toggleTodoBlock,
        onAddBlock = viewModel::addBlock,
        onAddChildBlock = viewModel::addChildBlock,
        onDeleteBlock = viewModel::deleteBlock,
        onMoveBlockUp = viewModel::moveBlockUp,
        onMoveBlockDown = viewModel::moveBlockDown,
        onTableTitleChange = viewModel::updateTableTitle,
        onTableViewChange = viewModel::updateTableView,
        onTableViewConfigChange = viewModel::updateTableViewConfig,
        onTableSortChange = viewModel::updateTableSort,
        onTableFilterChange = viewModel::updateTableFilter,
        onTableGroupChange = viewModel::updateTableGroup,
        onTableColumnNameChange = viewModel::updateTableColumnName,
        onTableColumnTypeChange = viewModel::updateTableColumnType,
        onTableColumnDateSettingsChange = viewModel::updateTableColumnDateSettings,
        onTableColumnFormulaChange = viewModel::updateTableColumnFormula,
        onTableColumnRelationTargetChange = viewModel::updateTableColumnRelationTarget,
        onTableColumnRollupChange = viewModel::updateTableColumnRollup,
        onTableCellChange = viewModel::updateTableCell,
        onAddTableColumn = viewModel::addTableColumn,
        onInsertTableColumn = viewModel::insertTableColumn,
        onDuplicateTableColumn = viewModel::duplicateTableColumn,
        onDeleteTableColumn = viewModel::deleteTableColumn,
        onAddTableRow = viewModel::addTableRow,
        onDeleteTableRow = viewModel::deleteTableRow,
        onTableRowBlockTextChange = viewModel::updateTableRowBlockText,
        onTableRowBlockRichTextChange = viewModel::updateTableRowBlockRichText,
        onTableRowBlockMediaAdd = viewModel::addTableRowBlockMediaAttachments,
        onTableRowBlockMediaRemove = viewModel::removeTableRowBlockMediaAttachment,
        onToggleTableRowTodoBlock = viewModel::toggleTableRowTodoBlock,
        onAddTableRowPageBlock = viewModel::addTableRowPageBlock,
        onDeleteTableRowPageBlock = viewModel::deleteTableRowPageBlock,
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
        onSendHomeAiMessage = onSendHomeAiMessage,
        onClearHomeAiHistory = onClearHomeAiHistory,
        onCreateHomeChatSession = onCreateHomeChatSession,
        onDismissHomeAiError = onDismissHomeAiError,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageEditorScreen(
    uiState: PageEditorUiState,
    homeAiState: HomeUiState,
    onBack: () -> Unit,
    onOpenPage: (String, String, String) -> Unit,
    initialSearchTargetType: String = "",
    initialSearchTargetId: String = "",
    onTitleChange: (String) -> Unit,
    onBlockTextChange: (String, String) -> Unit,
    onBlockRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onBlockMediaAdd: (String, List<PageMediaAttachment>) -> Unit,
    onBlockMediaRemove: (String, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onAddBlock: (PageBlockType) -> Unit,
    onAddChildBlock: (String, PageBlockType) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onMoveBlockUp: (String) -> Unit,
    onMoveBlockDown: (String) -> Unit,
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
    onTableRowBlockMediaAdd: (String, String, String, List<PageMediaAttachment>) -> Unit,
    onTableRowBlockMediaRemove: (String, String, String, String) -> Unit,
    onToggleTableRowTodoBlock: (String, String, String) -> Unit,
    onAddTableRowPageBlock: (String, String, PageBlockType) -> Unit,
    onDeleteTableRowPageBlock: (String, String, String) -> Unit,
    onAddProperty: (PagePropertyType, String) -> Unit,
    onPropertyNameChange: (String, String) -> Unit,
    onPropertyValueChange: (String, String) -> Unit,
    onDeleteProperty: (String) -> Unit,
    onCreateChildPage: () -> Unit,
    onUndoEditorChange: () -> Unit,
    onSendHomeAiMessage: (String) -> Unit,
    onClearHomeAiHistory: () -> Unit,
    onCreateHomeChatSession: () -> Unit,
    onDismissHomeAiError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isAiChatSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isPageSearchSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isBlockPickerSheetOpen by rememberSaveable { mutableStateOf(false) }
    var activeBlockId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.blocks, activeBlockId) {
        val currentActiveBlockId = activeBlockId
        if (currentActiveBlockId != null && !uiState.blocks.containsBlockId(currentActiveBlockId)) {
            activeBlockId = null
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            PageEditorTopBar(
                pageTitle = uiState.title.ifBlank { "Untitled page" },
                blockCount = uiState.blocks.size,
                isAiGenerating = homeAiState.isAiGeneratingChat,
                canUndoEditorChange = uiState.canUndoEditorChange,
                onBack = onBack,
                onUndoEditorChange = onUndoEditorChange,
                onSearch = { isPageSearchSheetOpen = true },
                onOpenAi = { isAiChatSheetOpen = true },
                onCreateBlock = { isBlockPickerSheetOpen = true },
            )
        },
        bottomBar = {
            PageEditorBottomBar(
                activeBlockId = activeBlockId,
                canUndoEditorChange = uiState.canUndoEditorChange,
                onAddBlock = onAddBlock,
                onDeleteActiveBlock = {
                    activeBlockId?.let(onDeleteBlock)
                    activeBlockId = null
                },
                onUndoEditorChange = onUndoEditorChange,
                onSearch = { isPageSearchSheetOpen = true },
                onOpenAi = { isAiChatSheetOpen = true },
                onCreateBlock = { isBlockPickerSheetOpen = true },
            )
        },
    ) { innerPadding ->
        if (isAiChatSheetOpen) {
            AiChatSheet(
                messages = homeAiState.chatMessages,
                mentionPages = homeAiState.allPages,
                isGenerating = homeAiState.isAiGeneratingChat,
                errorMessage = homeAiState.aiChatError,
                onSendMessage = onSendHomeAiMessage,
                onClearHistory = onClearHomeAiHistory,
                onCreateChatSession = onCreateHomeChatSession,
                onDismissError = onDismissHomeAiError,
                onOpenPage = { pageId, targetType, targetId ->
                    isAiChatSheetOpen = false
                    onOpenPage(pageId, targetType, targetId)
                },
                onDismiss = { isAiChatSheetOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                val isSubpage = uiState.page.parentPageId != null
                val tableReferences = remember(uiState.blocks) {
                    uiState.blocks.tableReferences()
                }
                val listState = rememberLazyListState()
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
                        listState.animateScrollToItem(0)
                    } else if (!uiState.isLoading && searchTargetIndex >= 0) {
                        listState.animateScrollToItem(searchTargetIndex + 1)
                    }
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        BasicTextField(
                            value = uiState.title,
                            onValueChange = onTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                ) {
                                    if (uiState.title.isBlank()) {
                                        Text(
                                            text = "Untitled page",
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }

                    items(
                        items = uiState.blocks,
                        key = { block -> block.id },
                    ) { block ->
                        BlockEditorCard(
                            block = block,
                            indentLevel = 0,
                            onTextChange = onBlockTextChange,
                            onRichTextChange = onBlockRichTextChange,
                            onMediaAdd = onBlockMediaAdd,
                            onMediaRemove = onBlockMediaRemove,
                            onToggleTodo = onToggleTodo,
                            onDelete = onDeleteBlock,
                            onMoveUp = onMoveBlockUp,
                            onMoveDown = onMoveBlockDown,
                            onBlockFocused = { blockId -> activeBlockId = blockId },
                            onAddChildBlock = onAddChildBlock,
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
                            onTableRowBlockMediaAdd = onTableRowBlockMediaAdd,
                            onTableRowBlockMediaRemove = onTableRowBlockMediaRemove,
                            onToggleTableRowTodoBlock = onToggleTableRowTodoBlock,
                            onAddTableRowPageBlock = onAddTableRowPageBlock,
                            onDeleteTableRowPageBlock = onDeleteTableRowPageBlock,
                            tableReferences = tableReferences,
                            searchTargetType = initialSearchTargetType,
                            searchTargetId = initialSearchTargetId,
                        )
                    }

                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageEditorTopBar(
    pageTitle: String,
    blockCount: Int,
    isAiGenerating: Boolean,
    canUndoEditorChange: Boolean,
    onBack: () -> Unit,
    onUndoEditorChange: () -> Unit,
    onSearch: () -> Unit,
    onOpenAi: () -> Unit,
    onCreateBlock: () -> Unit,
) {
    var isMenuOpen by rememberSaveable { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isAiGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                Text(
                    text = "$blockCount blocks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            IconButton(
                onClick = onUndoEditorChange,
                enabled = canUndoEditorChange,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = "Undo last edit",
                )
            }
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search page",
                )
            }
            Box {
                IconButton(onClick = { isMenuOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Page actions",
                    )
                }
                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Ask AI") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onOpenAi()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Add block") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onCreateBlock()
                        },
                    )
                    if (canUndoEditorChange) {
                        DropdownMenuItem(
                            text = { Text(text = "Undo last edit") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                onUndoEditorChange()
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun PageEditorBottomBar(
    activeBlockId: String?,
    canUndoEditorChange: Boolean,
    onAddBlock: (PageBlockType) -> Unit,
    onDeleteActiveBlock: () -> Unit,
    onUndoEditorChange: () -> Unit,
    onSearch: () -> Unit,
    onOpenAi: () -> Unit,
    onCreateBlock: () -> Unit,
) {
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isKeyboardVisible) {
            PageKeyboardBlockToolbar(
                activeBlockId = activeBlockId,
                canUndoEditorChange = canUndoEditorChange,
                onAddBlock = onAddBlock,
                onDeleteActiveBlock = onDeleteActiveBlock,
                onUndoEditorChange = onUndoEditorChange,
            )
        } else {
            CylBottomCommandBar(
                centerLabel = "Ask AI",
                centerIcon = Icons.Rounded.AutoAwesome,
                centerContentDescription = "Ask AI about this page",
                onCenterClick = onOpenAi,
                leadingActions = {
                    if (canUndoEditorChange) {
                        CylChromeIconButton(
                            icon = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo last edit",
                            onClick = onUndoEditorChange,
                        )
                    }
                    CylChromeIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search page",
                        onClick = onSearch,
                    )
                },
                trailingActions = {
                    CylChromeIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Add block",
                        onClick = onCreateBlock,
                    )
                },
            )
        }
    }
}

@Composable
private fun PageKeyboardBlockToolbar(
    activeBlockId: String?,
    canUndoEditorChange: Boolean,
    onAddBlock: (PageBlockType) -> Unit,
    onDeleteActiveBlock: () -> Unit,
    onUndoEditorChange: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canUndoEditorChange) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onUndoEditorChange,
                        label = { Text(text = "Undo") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
            items(
                items = BlockTypeOption.entries,
                key = { option -> option.type.name },
            ) { option ->
                FilterChip(
                    selected = false,
                    onClick = { onAddBlock(option.type) },
                    label = { Text(text = option.label) },
                )
            }
            if (activeBlockId != null) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onDeleteActiveBlock,
                        label = { Text(text = "Delete") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageBlockPickerSheet(
    onAddBlock: (PageBlockType) -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "New block",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            BlockTypeOption.entries.forEach { option ->
                ListItem(
                    headlineContent = { Text(text = option.label) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onAddBlock(option.type) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSearchSheet(
    pageTitle: String,
    properties: List<PageProperty>,
    blocks: List<PageBlock>,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, properties, blocks) {
        pageLocalSearchResults(
            query = query,
            properties = properties,
            blocks = blocks,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Search $pageTitle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(text = "Find in this page") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(18.dp),
            )
            if (query.isBlank()) {
                Text(
                    text = "Search page title, properties, blocks, tables, and row content.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (results.isEmpty()) {
                Text(
                    text = "No matches found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = results,
                        key = { result -> result.key },
                    ) { result ->
                        ListItem(
                            headlineContent = { Text(text = result.title, maxLines = 1) },
                            supportingContent = { Text(text = result.snippet, maxLines = 2) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private data class PageLocalSearchResult(
    val key: String,
    val title: String,
    val snippet: String,
)

private fun pageLocalSearchResults(
    query: String,
    properties: List<PageProperty>,
    blocks: List<PageBlock>,
): List<PageLocalSearchResult> {
    val terms = query.trim()
        .split(Regex("\\s+"))
        .filter { term -> term.isNotBlank() }
    if (terms.isEmpty()) return emptyList()

    val propertyResults = properties.map { property ->
        PageLocalSearchResult(
            key = "property:${property.id}",
            title = property.name.ifBlank { property.type.label },
            snippet = "Property ${property.type.label}: ${property.value.ifBlank { "Empty" }}",
        )
    }
    return (propertyResults + blocks.flatMapIndexed { index, block ->
        block.localSearchResults(path = "Block ${index + 1}")
    })
        .filter { result ->
            val text = "${result.title}\n${result.snippet}"
            terms.all { term -> text.contains(term, ignoreCase = true) }
        }
        .take(30)
}

private fun PageBlock.localSearchResults(path: String): List<PageLocalSearchResult> {
    val self = when (type) {
        PageBlockType.DatabaseTable -> table.localSearchResults(blockId = id, path = path)
        PageBlockType.MediaFile -> listOf(
            PageLocalSearchResult(
                key = id,
                title = "$path · Media/file",
                snippet = mediaAttachments
                    .joinToString { attachment -> attachment.name }
                    .ifBlank { "Media/file block" },
            ),
        )
        PageBlockType.Divider -> listOf(
            PageLocalSearchResult(
                key = id,
                title = "$path · Divider",
                snippet = "Divider",
            ),
        )
        else -> listOf(
            PageLocalSearchResult(
                key = id,
                title = "$path · ${type.label}",
                snippet = text.ifBlank { type.label },
            ),
        )
    }
    val childResults = children.flatMapIndexed { index, child ->
        child.localSearchResults(path = "$path.${index + 1}")
    }
    return self + childResults
}

private fun PageTable.localSearchResults(
    blockId: String,
    path: String,
): List<PageLocalSearchResult> {
    val columnNames = columns.associate { column -> column.id to column.name }
    val tableResult = PageLocalSearchResult(
        key = blockId,
        title = "$path · ${title.ifBlank { "Table" }}",
        snippet = columns.joinToString { column -> "${column.name} (${column.type.name})" },
    )
    val rowResults = rows.flatMapIndexed { rowIndex, row ->
        val cells = row.cells.entries.joinToString(separator = "; ") { (columnId, value) ->
            "${columnNames[columnId].orEmpty().ifBlank { columnId }}: $value"
        }
        val rowResult = PageLocalSearchResult(
            key = "$blockId:${row.id}",
            title = "Row ${rowIndex + 1}",
            snippet = cells.ifBlank { "Empty row" },
        )
        listOf(rowResult) + row.blocks.flatMapIndexed { blockIndex, block ->
            block.localSearchResults(path = "Row ${rowIndex + 1}.${blockIndex + 1}")
        }
    }
    return listOf(tableResult) + rowResults
}

@Composable
private fun SubpagePropertiesSection(
    properties: List<PageProperty>,
    onAddProperty: (PagePropertyType, String) -> Unit,
    onPropertyNameChange: (String, String) -> Unit,
    onPropertyValueChange: (String, String) -> Unit,
    onDeleteProperty: (String) -> Unit,
) {
    var isPropertySheetOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Properties",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Button(onClick = { isPropertySheetOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                )
                Text(
                    text = "Property",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        if (properties.isEmpty()) {
            Text(
                text = "No properties yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            properties.forEach { property ->
                PagePropertyEditor(
                    property = property,
                    onNameChange = { name -> onPropertyNameChange(property.id, name) },
                    onValueChange = { value -> onPropertyValueChange(property.id, value) },
                    onDelete = { onDeleteProperty(property.id) },
                )
            }
        }
    }

    if (isPropertySheetOpen) {
        NewPropertySheet(
            onDismiss = { isPropertySheetOpen = false },
            onAddProperty = { type, name ->
                onAddProperty(type, name)
                isPropertySheetOpen = false
            },
        )
    }
}

@Composable
private fun PagePropertyEditor(
    property: PageProperty,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = property.type.symbol,
                    modifier = Modifier.width(PropertySymbolWidth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = property.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text(text = "Property name")
                    },
                    colors = blockTextFieldColors(),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete property",
                    )
                }
            }

            Text(
                text = property.type.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (property.type == PagePropertyType.Checkbox) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = property.value == CheckboxValueChecked,
                        onCheckedChange = { isChecked ->
                            onValueChange(if (isChecked) CheckboxValueChecked else "")
                        },
                    )
                    Text(
                        text = if (property.value == CheckboxValueChecked) "Checked" else "Unchecked",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                OutlinedTextField(
                    value = property.value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(text = property.type.valuePlaceholder)
                    },
                    colors = blockTextFieldColors(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewPropertySheet(
    onDismiss: () -> Unit,
    onAddProperty: (PagePropertyType, String) -> Unit,
) {
    var propertyName by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "New property",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = propertyName,
                onValueChange = { propertyName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(text = "Property name")
                },
                colors = blockTextFieldColors(),
            )

            PropertyTypeGroup(
                title = "AI Autofill",
                options = AiAutofillPropertyTypes,
                propertyName = propertyName,
                onSelect = onAddProperty,
            )
            PropertyTypeGroup(
                title = "Type",
                options = CorePropertyTypes,
                propertyName = propertyName,
                onSelect = onAddProperty,
            )
            PropertyTypeGroup(
                title = "Connections",
                options = ConnectionPropertyTypes,
                propertyName = propertyName,
                onSelect = onAddProperty,
            )
        }
    }
}

@Composable
private fun PropertyTypeGroup(
    title: String,
    options: List<PagePropertyType>,
    propertyName: String,
    onSelect: (PagePropertyType, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                options.forEachIndexed { index, type ->
                    PropertyTypeRow(
                        type = type,
                        propertyName = propertyName,
                        onSelect = onSelect,
                    )
                    if (index != options.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyTypeRow(
    type: PagePropertyType,
    propertyName: String,
    onSelect: (PagePropertyType, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onSelect(
                    type,
                    propertyName.ifBlank { type.label },
                )
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = type.symbol,
            modifier = Modifier.width(PropertySymbolWidth),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = type.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (type.isBasicAiType) {
            Text(
                text = "Basic",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddBlockToolbar(
    onAddBlock: (PageBlockType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Blocks",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = BlockTypeOption.entries,
                key = { option -> option.type.name },
            ) { option ->
                FilterChip(
                    selected = false,
                    onClick = { onAddBlock(option.type) },
                    label = { Text(text = option.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun BlockEditorCard(
    block: PageBlock,
    indentLevel: Int = 0,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onMediaAdd: (String, List<PageMediaAttachment>) -> Unit,
    onMediaRemove: (String, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onBlockFocused: (String) -> Unit,
    onAddChildBlock: (String, PageBlockType) -> Unit,
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
    onTableRowBlockMediaAdd: (String, String, String, List<PageMediaAttachment>) -> Unit,
    onTableRowBlockMediaRemove: (String, String, String, String) -> Unit,
    onToggleTableRowTodoBlock: (String, String, String) -> Unit,
    onAddTableRowPageBlock: (String, String, PageBlockType) -> Unit,
    onDeleteTableRowPageBlock: (String, String, String) -> Unit,
    tableReferences: List<PageTableReference>,
    searchTargetType: String = "",
    searchTargetId: String = "",
) {
    var isBlockMenuExpanded by remember { mutableStateOf(false) }
    val isPlainTextBlock = block.type.isPlainEditorBlock
    val isSearchHighlighted = block.isDirectSearchTarget(searchTargetType, searchTargetId)

    Card(
        modifier = Modifier
            .padding(start = (indentLevel * 16).dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSearchHighlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            } else if (isPlainTextBlock) {
                Color.Transparent
            } else if (block.type == PageBlockType.DatabaseTable) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (isPlainTextBlock || block.type == PageBlockType.DatabaseTable) 0.dp else 6.dp,
                vertical = if (isPlainTextBlock || block.type == PageBlockType.DatabaseTable) 0.dp else 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(
                if (isPlainTextBlock || block.type == PageBlockType.DatabaseTable) 0.dp else 6.dp,
            ),
        ) {
            if (!isPlainTextBlock && block.type != PageBlockType.DatabaseTable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = block.type.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { isBlockMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Block actions",
                        )
                    }
                }

                DropdownMenu(
                    expanded = isBlockMenuExpanded,
                    onDismissRequest = { isBlockMenuExpanded = false },
                ) {
                    BlockTypeOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = "Add ${option.label.lowercase()} inside") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                isBlockMenuExpanded = false
                                onAddChildBlock(block.id, option.type)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(text = "Delete") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            isBlockMenuExpanded = false
                            onDelete(block.id)
                        },
                    )
                }
            }

            when (block.type) {
                PageBlockType.Divider -> HorizontalDivider()
                PageBlockType.MediaFile -> MediaFileBlockEditor(
                    block = block,
                    onAddAttachments = { attachments -> onMediaAdd(block.id, attachments) },
                    onRemoveAttachment = { attachmentId -> onMediaRemove(block.id, attachmentId) },
                    onTextChange = { text -> onTextChange(block.id, text) },
                    onRichTextChange = { text, spans -> onRichTextChange(block.id, text, spans) },
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
                    onDeleteRowPageBlock = { rowId, rowBlockId ->
                        onDeleteTableRowPageBlock(block.id, rowId, rowBlockId)
                    },
                    searchTargetType = searchTargetType,
                    searchTargetId = searchTargetId,
                )
                PageBlockType.Todo -> TodoBlockEditor(
                    blockId = block.id,
                    block = block,
                    onTextChange = onTextChange,
                    onRichTextChange = onRichTextChange,
                    onToggleTodo = onToggleTodo,
                    onFocusBlock = { onBlockFocused(block.id) },
                )
                PageBlockType.Bullet -> LeadingTextBlockEditor(
                    blockId = block.id,
                    leadingText = "-",
                    block = block,
                    onTextChange = onTextChange,
                    onRichTextChange = onRichTextChange,
                    onFocusBlock = { onBlockFocused(block.id) },
                )
                PageBlockType.Quote -> LeadingTextBlockEditor(
                    blockId = block.id,
                    leadingText = "|",
                    block = block,
                    onTextChange = onTextChange,
                    onRichTextChange = onRichTextChange,
                    fontStyle = FontStyle.Italic,
                    onFocusBlock = { onBlockFocused(block.id) },
                )
                PageBlockType.Heading,
                PageBlockType.Text,
                -> TextBlockEditor(
                    blockId = block.id,
                    block = block,
                    onTextChange = onTextChange,
                    onRichTextChange = onRichTextChange,
                    onFocusBlock = { onBlockFocused(block.id) },
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
                                onMediaAdd = onMediaAdd,
                                onMediaRemove = onMediaRemove,
                                onToggleTodo = onToggleTodo,
                                onDelete = onDelete,
                                onMoveUp = onMoveUp,
                                onMoveDown = onMoveDown,
                                onBlockFocused = onBlockFocused,
                                onAddChildBlock = onAddChildBlock,
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
                                onTableRowBlockMediaAdd = onTableRowBlockMediaAdd,
                                onTableRowBlockMediaRemove = onTableRowBlockMediaRemove,
                                onToggleTableRowTodoBlock = onToggleTableRowTodoBlock,
                                onAddTableRowPageBlock = onAddTableRowPageBlock,
                                onDeleteTableRowPageBlock = onDeleteTableRowPageBlock,
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
}

@Composable
private fun TextBlockEditor(
    blockId: String,
    block: PageBlock,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onFocusBlock: () -> Unit = {},
) {
    RichTextBlockEditor(
        blockId = blockId,
        block = block,
        onTextChange = onTextChange,
        onRichTextChange = onRichTextChange,
        modifier = Modifier.fillMaxWidth(),
        onFocusBlock = onFocusBlock,
        minLines = if (block.type == PageBlockType.Heading) 1 else 2,
        textStyle = when (block.type) {
            PageBlockType.Heading -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            else -> MaterialTheme.typography.bodyLarge
        },
        placeholder = block.type.placeholder,
    )
}

@Composable
private fun TodoBlockEditor(
    blockId: String,
    block: PageBlock,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    onToggleTodo: (String) -> Unit,
    onFocusBlock: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = block.isChecked,
            onCheckedChange = { onToggleTodo(blockId) },
        )
        RichTextBlockEditor(
            blockId = blockId,
            block = block,
            onTextChange = onTextChange,
            onRichTextChange = onRichTextChange,
            modifier = Modifier.weight(1f),
            onFocusBlock = onFocusBlock,
            singleLine = true,
            placeholder = "Todo item",
        )
    }
}

@Composable
private fun LeadingTextBlockEditor(
    blockId: String,
    leadingText: String,
    block: PageBlock,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    fontStyle: FontStyle? = null,
    onFocusBlock: () -> Unit = {},
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
        RichTextBlockEditor(
            blockId = blockId,
            block = block,
            onTextChange = onTextChange,
            onRichTextChange = onRichTextChange,
            modifier = Modifier.weight(1f),
            onFocusBlock = onFocusBlock,
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontStyle = fontStyle,
            ),
            placeholder = block.type.placeholder,
        )
    }
}

@Composable
private fun RichTextBlockEditor(
    blockId: String,
    block: PageBlock,
    onTextChange: (String, String) -> Unit,
    onRichTextChange: (String, String, List<PageTextSpan>) -> Unit,
    modifier: Modifier = Modifier,
    onFocusBlock: () -> Unit = {},
    minLines: Int = 1,
    singleLine: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: String,
) {
    var fieldValue by remember(block.id) {
        mutableStateOf(block.toTextFieldValue())
    }

    LaunchedEffect(block.text, block.richTextSpans) {
        val normalized = block.richTextSpans.normalizedForText(block.text)
        if (fieldValue.text != block.text || fieldValue.toPageTextSpans() != normalized) {
            fieldValue = TextFieldValue(
                annotatedString = buildRichTextAnnotatedString(block.text, normalized),
                selection = fieldValue.selection.coerceInText(block.text),
            )
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { incoming ->
                val previousSpans = fieldValue.toPageTextSpans()
                val incomingSpans = incoming.toPageTextSpans()
                val nextSpans = if (
                    incoming.text == fieldValue.text ||
                    incomingSpans.isNotEmpty() ||
                    previousSpans.isEmpty()
                ) {
                    incomingSpans
                } else {
                    previousSpans.adjustForTextChange(
                        oldText = fieldValue.text,
                        newText = incoming.text,
                    )
                }.normalizedForText(incoming.text)
                val nextValue = TextFieldValue(
                    annotatedString = buildRichTextAnnotatedString(incoming.text, nextSpans),
                    selection = incoming.selection.coerceInText(incoming.text),
                    composition = incoming.composition,
                )
                fieldValue = nextValue
                onRichTextChange(blockId, nextValue.text, nextSpans)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus) onFocusBlock()
                },
            minLines = minLines,
            singleLine = singleLine,
            textStyle = textStyle,
            placeholder = {
                Text(text = placeholder)
            },
            colors = plainBlockTextFieldColors(),
        )
    }
}

@Composable
private fun RichTextToolbar(
    value: TextFieldValue,
    spans: List<PageTextSpan>,
    onToggle: (RichTextFormat) -> Unit,
) {
    val range = value.effectiveFormatRange()
    val hasRange = range.min != range.max
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RichTextFormat.entries.forEach { format ->
            RichTextFormatButton(
                label = format.label,
                selected = hasRange && spans.hasFormat(format, range),
                enabled = hasRange,
                onClick = { onToggle(format) },
            )
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
private fun MediaFileBlockEditor(
    block: PageBlock,
    onAddAttachments: (List<PageMediaAttachment>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onRichTextChange: (String, List<PageTextSpan>) -> Unit,
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Media & files",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${block.mediaAttachments.size} attached",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                Text(text = "Attach", modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (block.mediaAttachments.isEmpty()) {
            Text(
                text = "No files attached.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            block.mediaAttachments.forEach { attachment ->
                MediaAttachmentCard(
                    attachment = attachment,
                    onOpen = { context.openMediaAttachment(attachment) },
                    onRemove = { onRemoveAttachment(attachment.id) },
                )
            }
        }

        RichTextBlockEditor(
            blockId = block.id,
            block = block,
            onTextChange = { _, text -> onTextChange(text) },
            onRichTextChange = { _, text, spans -> onRichTextChange(text, spans) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            placeholder = "Caption",
        )
    }
}

@Composable
private fun MediaAttachmentCard(
    attachment: PageMediaAttachment,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val previewBitmap = rememberAttachmentBitmap(attachment)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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
                    .clip(RoundedCornerShape(8.dp)),
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
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOf(
                        attachment.mimeType.ifBlank { "unknown type" },
                        attachment.sizeBytes.formatFileSize(),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) {
                Text(text = "Open")
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
private fun rememberAttachmentBitmap(attachment: PageMediaAttachment): androidx.compose.ui.graphics.ImageBitmap? {
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

@Composable
private fun DatabaseTableBlockEditor(
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
    onRowBlockMediaAdd: (String, String, List<PageMediaAttachment>) -> Unit,
    onRowBlockMediaRemove: (String, String, String) -> Unit,
    onToggleRowTodoBlock: (String, String) -> Unit,
    onAddRowPageBlock: (String, PageBlockType) -> Unit,
    onDeleteRowPageBlock: (String, String) -> Unit,
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
            onBlockMediaAdd = { rowBlockId, attachments ->
                onRowBlockMediaAdd(openRow.id, rowBlockId, attachments)
            },
            onBlockMediaRemove = { rowBlockId, attachmentId ->
                onRowBlockMediaRemove(openRow.id, rowBlockId, attachmentId)
            },
            onToggleTodo = { rowBlockId -> onToggleRowTodoBlock(openRow.id, rowBlockId) },
            onAddBlock = { type -> onAddRowPageBlock(openRow.id, type) },
            onDeleteBlock = { rowBlockId -> onDeleteRowPageBlock(openRow.id, rowBlockId) },
            onDismiss = { openRowId = null },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicTextField(
            value = table.title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                ) {
                    if (table.title.isBlank()) {
                        Text(
                            text = "Untitled table",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        )
                    }
                    innerTextField()
                }
            },
        )

        TableToolbar(
            table = table,
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
private fun TableToolbar(
    table: PageTable,
    onViewChange: (PageTableView) -> Unit,
    onSortChange: (String, PageTableSortDirection) -> Unit,
    onFilterChange: (String, String) -> Unit,
    onGroupChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableViewSelector(
            selectedView = table.view,
            onViewChange = onViewChange,
        )
        Box(modifier = Modifier.weight(1f)) {
            TableControls(
                table = table,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onGroupChange = onGroupChange,
            )
        }
    }
}

@Composable
private fun TableViewSelector(
    selectedView: PageTableView,
    onViewChange: (PageTableView) -> Unit,
) {
    var isViewMenuOpen by remember { mutableStateOf(false) }
    val selectedOption = TableViewOption.entries.firstOrNull { option -> option.view == selectedView }
        ?: TableViewOption.entries.first()

    Box {
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable { isViewMenuOpen = true }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedOption.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = isViewMenuOpen,
            onDismissRequest = { isViewMenuOpen = false },
        ) {
            TableViewOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.label) },
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
private fun TableViewConfigControls(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableControls(
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

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            TableControlChip(
                label = sortColumn?.let { column ->
                    "Sort: ${column.name.ifBlank { "Untitled" }} ${table.sort.direction.arrowLabel}"
                } ?: "Sort",
                selected = sortColumn != null,
                onClick = { activeControl = TableControlType.Sort },
            )
        }
        item {
            TableControlChip(
                label = filterColumn?.let { column ->
                    "Filter: ${column.name.ifBlank { "Untitled" }}"
                } ?: "Filter",
                selected = filterColumn != null && table.filter.query.isNotBlank(),
                onClick = { activeControl = TableControlType.Filter },
            )
        }
        item {
            TableControlChip(
                label = groupColumn?.let { column ->
                    "Group: ${column.name.ifBlank { "Untitled" }}"
                } ?: "Group",
                selected = groupColumn != null,
                onClick = { activeControl = TableControlType.Group },
            )
        }
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
private fun TableControlChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label.compactControlLabel()) },
    )
}

@Composable
private fun TableControlSheet(
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
private fun TableSortSheet(
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
private fun TableFilterSheet(
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
private fun TableGroupSheet(
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
private fun TableControlSheetHeader(
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
private fun TableColumnChoiceRow(
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
private fun TableGridEditor(
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
private fun TableHeaderRow(
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
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        Box(modifier = Modifier.width(TableOpenWidth))
        columns.forEach { column ->
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
        TableAddColumnCell(onAddColumn = onAddColumn)
    }
}

@Composable
private fun TableHeaderCell(
    column: PageTableColumn,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(TableCellWidth)
            .height(TableHeaderHeight)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = column.type.shortLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = column.name.ifBlank { "Untitled" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TableAddColumnCell(
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
            .background(MaterialTheme.colorScheme.surface)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTableColumnSheet(
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
                            Text(
                                text = type.shortLabel,
                                modifier = Modifier.width(36.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
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
private fun TableColumnEditSheet(
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
                            Text(
                                text = column.type.shortLabel,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            Text(
                                text = "i",
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = blockTextFieldColors(),
                    )

                    PropertyMenuGroup {
                        PropertyMenuRow(
                            icon = "Sl",
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
                            icon = "Ty",
                            label = "Change type",
                            value = column.type.label,
                            onClick = { detail = PropertySheetDetail.ChangeType },
                        )
                        PropertyMenuRow(
                            icon = "AI",
                            label = "AI Autofill",
                            value = "Now with agents",
                            onClick = {},
                        )
                    }

                    PropertyMenuGroup {
                        PropertyMenuRow(
                            icon = "Fi",
                            label = "Filter",
                            value = if (table.filter.columnId == column.id) table.filter.query else "",
                            onClick = { detail = PropertySheetDetail.Filter },
                        )
                        PropertyMenuRow(
                            icon = "So",
                            label = "Sort",
                            value = if (table.sort.columnId == column.id) table.sort.direction.arrowLabel else "",
                            onClick = { detail = PropertySheetDetail.Sort },
                        )
                        PropertyMenuRow(
                            icon = "Gr",
                            label = "Group",
                            value = if (table.groupByColumnId == column.id) "Active" else "",
                            onClick = {
                                onGroup()
                                onDismiss()
                            },
                        )
                        PropertyMenuRow(
                            icon = "Fx",
                            label = "Calculate",
                            value = column.configSummary(table, tableReferences),
                            onClick = { detail = PropertySheetDetail.Calculate },
                        )
                        PropertyMenuRow(
                            icon = "Hi",
                            label = "Hide",
                            value = "",
                            onClick = onDismiss,
                        )
                        PropertyMenuRow(
                            icon = "Un",
                            label = "Unwrap content",
                            value = "",
                            onClick = onDismiss,
                        )
                    }

                    PropertyMenuGroup {
                        PropertyMenuRow(icon = "<", label = "Insert left", onClick = onInsertLeft)
                        PropertyMenuRow(icon = ">", label = "Insert right", onClick = onInsertRight)
                        PropertyMenuRow(icon = "Cp", label = "Duplicate property", onClick = onDuplicate)
                        PropertyMenuRow(
                            icon = "Del",
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

private enum class PropertySheetDetail {
    ChangeType,
    DateSettings,
    Filter,
    Sort,
    Calculate,
}

@Composable
private fun PropertyMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
private fun PropertyMenuRow(
    icon: String,
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
            Text(
                text = icon,
                modifier = Modifier.width(38.dp),
                textAlign = TextAlign.Center,
                color = color.copy(alpha = 0.82f),
                fontWeight = FontWeight.SemiBold,
            )
        },
        trailingContent = {
            Text(
                text = ">",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

@Composable
private fun PropertyDetailHeader(
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
private fun ChangePropertyTypeSheet(
    selectedType: PageTableColumnType,
    onTypeChange: (PageTableColumnType) -> Unit,
    onBack: () -> Unit,
) {
    PropertyDetailHeader(title = "Change type", onBack = onBack)
    PropertyMenuGroup {
        PageTableColumnType.entries.forEach { type ->
            PropertyMenuRow(
                icon = type.shortLabel,
                label = type.label,
                value = if (type == selectedType) "Selected" else "",
                onClick = { onTypeChange(type) },
            )
        }
    }
}

@Composable
private fun TableDatePropertySettingsSheet(
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
            icon = "Cal",
            label = "Date format",
            selectedLabel = dateFormat.label,
            items = PageTableDateFormat.entries,
            itemLabel = { format -> format.label },
            onSelect = { format -> save(nextDateFormat = format) },
        )
        DateSettingChoiceRow(
            icon = "Tm",
            label = "Time format",
            selectedLabel = timeFormat.label,
            items = PageTableTimeFormat.entries,
            itemLabel = { format -> format.label },
            onSelect = { format -> save(nextTimeFormat = format) },
        )
        DateSettingChoiceRow(
            icon = "Al",
            label = "Notifications",
            selectedLabel = reminder.label,
            items = PageTableDateReminder.entries,
            itemLabel = { value -> value.label },
            onSelect = { value -> save(nextReminder = value) },
        )
        DateSettingChoiceRow(
            icon = "TZ",
            label = "Timezone",
            selectedLabel = timezoneLabel,
            items = TableTimezoneOptions,
            itemLabel = { value -> value },
            onSelect = { value -> save(nextTimezone = value) },
        )
    }
}

@Composable
private fun <T> DateSettingChoiceRow(
    icon: String,
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
private fun ColumnFilterSheet(
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
private fun ColumnSortSheet(
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
private fun ColumnCalculateSheet(
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
private fun TableColumnConfigSheet(
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
private fun FormulaColumnConfig(
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
private fun RelationColumnConfig(
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
                label = { Text(text = reference.title.ifBlank { "Untitled table" }.compactControlLabel()) },
            )
        }
    }
}

@Composable
private fun RollupColumnConfig(
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
private fun TableGroupHeader(
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
private fun TableDataRow(
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
    Row(
        modifier = Modifier.background(
            if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                Color.Transparent
            },
        ),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            modifier = Modifier
                .width(TableOpenWidth)
                .height(TableRowHeight),
            onClick = { onOpenRow(row.id) },
        ) {
            Text(
                text = "OPEN",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        columns.forEach { column ->
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
                modifier = Modifier.width(TableCellWidth),
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun TableAddRowRow(
    columns: List<PageTableColumn>,
    onAddRow: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(TableOpenWidth)
                .height(TableRowHeight),
        )
        if (columns.isEmpty()) {
            TableAddRowCell(
                modifier = Modifier.width(TableCellWidth),
                onAddRow = onAddRow,
            )
        } else {
            TableAddRowCell(
                modifier = Modifier.width(TableCellWidth),
                onAddRow = onAddRow,
            )
            repeat((columns.size - 1).coerceAtLeast(0)) {
                Box(
                    modifier = Modifier
                        .width(TableCellWidth)
                        .height(TableRowHeight),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(TableAddColumnWidth)
                .height(TableRowHeight),
        )
    }
    HorizontalDivider()
}

@Composable
private fun TableAddRowCell(
    modifier: Modifier,
    onAddRow: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(TableRowHeight)
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
private fun TableDateCellEditor(
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
private fun TableDateEditorSheet(
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
                    icon = "Cal",
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
                    icon = "Tm",
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
                    icon = "TZ",
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
                    icon = "Al",
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
private fun DateEditorValueBox(
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
private fun DateTimeChoiceBox(
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
private fun TableDateCalendar(
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
private fun DateToggleRow(
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
private fun <T> DateChoiceRow(
    icon: String,
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
                Text(
                    text = icon,
                    modifier = Modifier.width(34.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Text(
                    text = ">",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun TableCellEditor(
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
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TableRowHeight),
                    readOnly = true,
                    singleLine = true,
                    placeholder = { Text(text = "Select status") },
                    trailingIcon = {
                        TextButton(onClick = { isStatusMenuExpanded = true }) {
                            Text(text = "Pick")
                        }
                    },
                    colors = plainBlockTextFieldColors(),
                )
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
                onValueChange = onValueChange,
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

@Composable
private fun ReadOnlyComputedCell(
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
private fun TableMediaCellEditor(
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
        TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
            Text(text = if (attachments.isEmpty()) "Attach" else "Add")
        }
    }
}

@Composable
private fun RelationCellEditor(
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
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(TableRowHeight),
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                TextButton(
                    enabled = targetTable != null,
                    onClick = { isExpanded = true },
                ) {
                    Text(text = "Pick")
                }
            },
            colors = plainBlockTextFieldColors(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableRowPageSheet(
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
    onBlockMediaAdd: (String, List<PageMediaAttachment>) -> Unit,
    onBlockMediaRemove: (String, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onAddBlock: (PageBlockType) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = row.cellText(table.titleColumn()).ifBlank { "Untitled row" }
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddRow) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                    Text(text = "Row", modifier = Modifier.padding(start = 8.dp))
                }
                Button(onClick = { isNewColumnSheetOpen = true }) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                    Text(text = "Property", modifier = Modifier.padding(start = 8.dp))
                }
            }

            Text(
                text = "Properties",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

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
                                )
                                PageBlockType.Todo -> TodoBlockEditor(
                                    blockId = block.id,
                                    block = block,
                                    onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                    onToggleTodo = { onToggleTodo(block.id) },
                                )
                                PageBlockType.Bullet -> LeadingTextBlockEditor(
                                    blockId = block.id,
                                    leadingText = "-",
                                    block = block,
                                    onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                )
                                PageBlockType.Quote -> LeadingTextBlockEditor(
                                    blockId = block.id,
                                    leadingText = "|",
                                    block = block,
                                    onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                    fontStyle = FontStyle.Italic,
                                )
                                PageBlockType.DatabaseTable,
                                PageBlockType.Heading,
                                PageBlockType.Text,
                                -> TextBlockEditor(
                                    blockId = block.id,
                                    block = block,
                                    onTextChange = { _, text -> onBlockTextChange(block.id, text) },
                                    onRichTextChange = { _, text, spans -> onBlockRichTextChange(block.id, text, spans) },
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteBlock(block.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete row block",
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TableListView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
) {
    val groupedSummaries = table.groupedSummaries(tableReferences = tableReferences)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (groupedSummaries.all { group -> group.second.isEmpty() }) {
            EmptyTableMessage()
        } else {
            groupedSummaries.forEach { (group, rows) ->
                if (group.isNotBlank()) {
                    Text(
                        text = "$group (${rows.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                rows.forEach { summary ->
                    ListItem(
                        headlineContent = { Text(text = summary.title) },
                        supportingContent = {
                            if (summary.details.isNotBlank()) {
                                Text(text = summary.details)
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TableBoardView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
) {
    val groupedRows = table.groupedSummaries(tableReferences = tableReferences, defaultToStatus = true)

    if (groupedRows.all { group -> group.second.isEmpty() }) {
        EmptyTableMessage()
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(
            items = groupedRows.toList(),
            key = { group -> group.first },
        ) { (status, rows) ->
            Column(
                modifier = Modifier.width(BoardColumnWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "$status (${rows.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                rows.forEach { summary ->
                    ListItem(
                        headlineContent = { Text(text = summary.title) },
                        supportingContent = {
                            if (summary.details.isNotBlank()) {
                                Text(text = summary.details)
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TableCalendarView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
) {
    val summaries = table.rowSummaries(
        tableReferences = tableReferences,
        dateColumnId = table.viewConfig.calendarDateColumnId,
    )
    val groupedRows = summaries
        .groupBy { summary -> summary.date }
        .toList()
        .sortedBy { group -> group.first.dateSortKey() }

    if (summaries.isEmpty()) {
        EmptyTableMessage()
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(
            items = groupedRows,
            key = { group -> group.first },
        ) { (date, rows) ->
            Column(
                modifier = Modifier.width(CalendarDayWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "$date (${rows.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                rows.forEach { summary ->
                    ListItem(
                        headlineContent = { Text(text = summary.title) },
                        supportingContent = {
                            if (summary.details.isNotBlank()) {
                                Text(text = summary.details)
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TableGalleryView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
) {
    val summaries = table.rowSummaries(tableReferences)

    if (summaries.isEmpty()) {
        EmptyTableMessage()
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(
            items = summaries,
            key = { summary -> summary.row.id },
        ) { summary ->
            Column(
                modifier = Modifier.width(GalleryItemWidth),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${summary.status} • ${summary.date}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.details.isNotBlank()) {
                    Text(
                        text = summary.details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TableTimelineView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
) {
    val summaries = if (table.sort.columnId.isBlank()) {
        table.rowSummaries(
            tableReferences = tableReferences,
            dateColumnId = table.viewConfig.timelineStartColumnId,
            endDateColumnId = table.viewConfig.timelineEndColumnId,
        ).sortedWith(
            compareBy<TableRowSummary> { summary -> summary.date.dateSortKey() }
                .thenBy { summary -> summary.title },
        )
    } else {
        table.rowSummaries(
            tableReferences = tableReferences,
            dateColumnId = table.viewConfig.timelineStartColumnId,
            endDateColumnId = table.viewConfig.timelineEndColumnId,
        )
    }

    if (summaries.isEmpty()) {
        EmptyTableMessage()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        summaries.forEach { summary ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val dateLabel = if (summary.endDate.isNotBlank() && summary.endDate != NoDateLabel) {
                    "${summary.date} - ${summary.endDate}"
                } else {
                    summary.date
                }
                Text(
                    text = dateLabel,
                    modifier = Modifier.width(TimelineDateWidth),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary.status,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.timelineDetails.isNotBlank()) {
                        Text(
                            text = summary.timelineDetails,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun TableDashboardView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
) {
    val summaries = table.rowSummaries(tableReferences)
    val rows = table.visibleRows(tableReferences)
    val groupColumn = table.columns.firstOrNull { column -> column.id == table.viewConfig.dashboardGroupColumnId }
        ?: table.statusColumn()
    val metricColumn = table.columns.firstOrNull { column -> column.id == table.viewConfig.dashboardMetricColumnId }
        ?: table.metricCandidateColumns().firstOrNull()
    val groupCounts = rows
        .groupingBy { row ->
            table.displayCellText(row, groupColumn, tableReferences).ifBlank { "Empty" }
        }
        .eachCount()
        .toList()
        .sortedByDescending { entry -> entry.second }
    val metricValues = rows.mapNotNull { row ->
        metricColumn?.let { column -> table.displayCellText(row, column, tableReferences).toDoubleOrNull() }
    }
    val dateCounts = summaries
        .filterNot { summary -> summary.date == NoDateLabel }
        .groupingBy { summary -> summary.date }
        .eachCount()
        .toList()
        .sortedBy { entry -> entry.first.dateSortKey() }

    if (summaries.isEmpty()) {
        EmptyTableMessage()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(
                items = buildList {
                    add(DashboardStat("Rows", summaries.size.toString()))
                    add(DashboardStat("Groups", groupCounts.size.toString()))
                    if (metricColumn != null && metricValues.isNotEmpty()) {
                        add(DashboardStat("${metricColumn.name} total", metricValues.sum().formatTableNumber()))
                        add(DashboardStat("${metricColumn.name} avg", (metricValues.sum() / metricValues.size).formatTableNumber()))
                    } else {
                        add(DashboardStat("Dated", dateCounts.sumOf { entry -> entry.second }.toString()))
                    }
                },
                key = { stat -> stat.label },
            ) { stat ->
                DashboardStatTile(stat = stat)
            }
        }

        Text(
            text = groupColumn?.name?.ifBlank { "Group" } ?: "Group",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        groupCounts.forEach { (status, count) ->
            DashboardBar(
                label = status,
                count = count,
                maxCount = groupCounts.maxOf { entry -> entry.second },
            )
        }

        Text(
            text = "Dates",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (dateCounts.isEmpty()) {
            Text(
                text = "No dated rows.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            dateCounts.forEach { (date, count) ->
                DashboardBar(
                    label = date,
                    count = count,
                    maxCount = dateCounts.maxOf { entry -> entry.second },
                )
            }
        }
    }
}

@Composable
private fun DashboardStatTile(stat: DashboardStat) {
    Column(
        modifier = Modifier.width(DashboardStatWidth),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stat.value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stat.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardBar(
    label: String,
    count: Int,
    maxCount: Int,
) {
    val progress = if (maxCount <= 0) {
        0f
    } else {
        count.toFloat() / maxCount.toFloat()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(DashboardLabelWidth),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            modifier = Modifier.width(DashboardCountWidth),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyTableMessage() {
    Text(
        text = "No rows yet.",
        modifier = Modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun blockTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)

@Composable
private fun plainBlockTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun SubpageSectionHeader(
    onCreateChildPage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Subpages",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Button(onClick = onCreateChildPage) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
            )
            Text(
                text = "New",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun CenterMessage(
    text: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val PageBlockType.label: String
    get() = when (this) {
        PageBlockType.Text -> "Text"
        PageBlockType.Heading -> "Heading"
        PageBlockType.Todo -> "Todo"
        PageBlockType.Bullet -> "Bullet"
        PageBlockType.Quote -> "Quote"
        PageBlockType.Divider -> "Divider"
        PageBlockType.MediaFile -> "Media/file"
        PageBlockType.DatabaseTable -> "Table"
    }

private val PageBlockType.placeholder: String
    get() = when (this) {
        PageBlockType.Text -> "Write something"
        PageBlockType.Heading -> "Heading"
        PageBlockType.Todo -> "Todo item"
        PageBlockType.Bullet -> "Bullet item"
        PageBlockType.Quote -> "Quote"
        PageBlockType.Divider -> ""
        PageBlockType.MediaFile -> "Caption"
        PageBlockType.DatabaseTable -> ""
    }

private fun List<PageBlock>.containsBlockId(blockId: String): Boolean {
    return any { block ->
        block.id == blockId || block.children.containsBlockId(blockId)
    }
}

private fun List<PageBlock>.indexOfSearchTarget(
    targetType: String,
    targetId: String,
): Int {
    if (targetType.isBlank() || targetType == SearchTargetPageTitle) return -1
    if (targetId.isBlank() && targetType != SearchTargetPageTitle) return -1
    return indexOfFirst { block -> block.containsSearchTarget(targetType, targetId) }
}

private fun PageBlock.containsSearchTarget(
    targetType: String,
    targetId: String,
): Boolean {
    return isDirectSearchTarget(targetType, targetId) ||
        (type == PageBlockType.DatabaseTable && table.highlightedRowId(targetType, targetId) != null) ||
        children.any { child -> child.containsSearchTarget(targetType, targetId) }
}

private fun PageBlock.isDirectSearchTarget(
    targetType: String,
    targetId: String,
): Boolean {
    return targetType == SearchTargetBlock && targetId == id
}

private fun PageTable.highlightedRowId(
    targetType: String,
    targetId: String,
): String? {
    return when (targetType) {
        SearchTargetRow -> rows.firstOrNull { row -> row.id == targetId }?.id
        SearchTargetRowBlock -> rows.firstOrNull { row -> row.blocks.containsBlockId(targetId) }?.id
        else -> null
    }
}

private const val SearchTargetPageTitle = "title"
private const val SearchTargetBlock = "block"
private const val SearchTargetRow = "row"
private const val SearchTargetRowBlock = "row_block"

private val PageBlockType.isPlainEditorBlock: Boolean
    get() = when (this) {
        PageBlockType.Text,
        PageBlockType.Heading,
        PageBlockType.Todo,
        PageBlockType.Bullet,
        PageBlockType.Quote,
        -> true
        PageBlockType.Divider,
        PageBlockType.MediaFile,
        PageBlockType.DatabaseTable,
        -> false
    }

private data class BlockTypeOption(
    val type: PageBlockType,
    val label: String,
) {
    companion object {
        val entries = listOf(
            BlockTypeOption(PageBlockType.Text, "Text"),
            BlockTypeOption(PageBlockType.Heading, "Heading"),
            BlockTypeOption(PageBlockType.Todo, "Todo"),
            BlockTypeOption(PageBlockType.Bullet, "Bullet"),
            BlockTypeOption(PageBlockType.Quote, "Quote"),
            BlockTypeOption(PageBlockType.Divider, "Divider"),
            BlockTypeOption(PageBlockType.MediaFile, "Media/file"),
            BlockTypeOption(PageBlockType.DatabaseTable, "Table"),
        )
    }
}

private enum class RichTextFormat(
    val label: String,
) {
    Bold("B"),
    Italic("I"),
    Underline("U"),
    Strikethrough("S"),
}

private data class RichTextFlags(
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var strikethrough: Boolean = false,
) {
    fun isEmpty(): Boolean = !bold && !italic && !underline && !strikethrough

    fun has(format: RichTextFormat): Boolean {
        return when (format) {
            RichTextFormat.Bold -> bold
            RichTextFormat.Italic -> italic
            RichTextFormat.Underline -> underline
            RichTextFormat.Strikethrough -> strikethrough
        }
    }

    fun set(format: RichTextFormat, value: Boolean) {
        when (format) {
            RichTextFormat.Bold -> bold = value
            RichTextFormat.Italic -> italic = value
            RichTextFormat.Underline -> underline = value
            RichTextFormat.Strikethrough -> strikethrough = value
        }
    }

    fun sameStyleAs(other: RichTextFlags): Boolean {
        return bold == other.bold &&
            italic == other.italic &&
            underline == other.underline &&
            strikethrough == other.strikethrough
    }

    fun toSpan(start: Int, end: Int): PageTextSpan {
        return PageTextSpan(
            start = start,
            end = end,
            bold = bold,
            italic = italic,
            underline = underline,
            strikethrough = strikethrough,
        )
    }
}

private fun PageBlock.toTextFieldValue(): TextFieldValue {
    val spans = richTextSpans.normalizedForText(text)
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
    spans.normalizedForText(text).forEach { span ->
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
        textDecoration = if (textDecorations.isEmpty()) {
            null
        } else {
            TextDecoration.combine(textDecorations)
        },
    )
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
        )
        if (span.hasAnyStyle()) span else null
    }.normalizedForText(text)
}

private fun List<PageTextSpan>.normalizedForText(text: String): List<PageTextSpan> {
    if (text.isEmpty()) return emptyList()
    return mapNotNull { span ->
        val start = span.start.coerceIn(0, text.length)
        val end = span.end.coerceIn(0, text.length)
        if (start >= end || !span.hasAnyStyle()) {
            null
        } else {
            span.copy(start = start, end = end)
        }
    }.mergeAdjacentTextSpans()
}

private fun List<PageTextSpan>.mergeAdjacentTextSpans(): List<PageTextSpan> {
    if (isEmpty()) return emptyList()
    return sortedWith(compareBy<PageTextSpan> { it.start }.thenBy { it.end })
        .fold(mutableListOf()) { merged, span ->
            val last = merged.lastOrNull()
            if (last != null && last.end >= span.start && last.sameStyleAs(span)) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, span.end))
            } else {
                merged += span
            }
            merged
        }
}

private fun List<PageTextSpan>.adjustForTextChange(
    oldText: String,
    newText: String,
): List<PageTextSpan> {
    if (isEmpty() || oldText == newText) return normalizedForText(newText)
    val prefixLength = oldText.commonPrefixWith(newText).length
    val suffixLength = oldText
        .drop(prefixLength)
        .commonSuffixWith(newText.drop(prefixLength))
        .length
    val oldChangeEnd = oldText.length - suffixLength
    val newChangeEnd = newText.length - suffixLength
    val delta = newChangeEnd - oldChangeEnd

    return mapNotNull { span ->
        when {
            span.end <= prefixLength -> span
            span.start >= oldChangeEnd -> span.copy(
                start = span.start + delta,
                end = span.end + delta,
            )
            else -> span.copy(end = (span.end + delta).coerceAtLeast(prefixLength))
        }
    }.normalizedForText(newText)
}

private fun List<PageTextSpan>.toggleFormat(
    format: RichTextFormat,
    range: TextRange,
    textLength: Int,
): List<PageTextSpan> {
    val start = range.min.coerceIn(0, textLength)
    val end = range.max.coerceIn(0, textLength)
    if (start >= end) return this
    val flags = toRichTextFlags(textLength)
    val shouldEnable = (start until end).any { index -> !flags[index].has(format) }
    for (index in start until end) {
        flags[index].set(format, shouldEnable)
    }
    return flags.toTextSpans()
}

private fun List<PageTextSpan>.hasFormat(
    format: RichTextFormat,
    range: TextRange,
): Boolean {
    val flags = toRichTextFlags(range.max)
    return (range.min until range.max).all { index -> flags[index].has(format) }
}

private fun List<PageTextSpan>.toRichTextFlags(textLength: Int): MutableList<RichTextFlags> {
    val flags = MutableList(textLength) { RichTextFlags() }
    normalizedForText(" ".repeat(textLength)).forEach { span ->
        for (index in span.start until span.end) {
            flags[index].bold = flags[index].bold || span.bold
            flags[index].italic = flags[index].italic || span.italic
            flags[index].underline = flags[index].underline || span.underline
            flags[index].strikethrough = flags[index].strikethrough || span.strikethrough
        }
    }
    return flags
}

private fun List<RichTextFlags>.toTextSpans(): List<PageTextSpan> {
    val spans = mutableListOf<PageTextSpan>()
    var spanStart = -1
    var current = RichTextFlags()
    forEachIndexed { index, flags ->
        if (flags.isEmpty()) {
            if (spanStart != -1) {
                spans += current.toSpan(spanStart, index)
                spanStart = -1
                current = RichTextFlags()
            }
        } else if (spanStart == -1) {
            spanStart = index
            current = flags.copy()
        } else if (!current.sameStyleAs(flags)) {
            spans += current.toSpan(spanStart, index)
            spanStart = index
            current = flags.copy()
        }
    }
    if (spanStart != -1) {
        spans += current.toSpan(spanStart, size)
    }
    return spans
}

private fun PageTextSpan.hasAnyStyle(): Boolean {
    return bold || italic || underline || strikethrough
}

private fun PageTextSpan.sameStyleAs(other: PageTextSpan): Boolean {
    return bold == other.bold &&
        italic == other.italic &&
        underline == other.underline &&
        strikethrough == other.strikethrough
}

private fun TextFieldValue.effectiveFormatRange(): TextRange {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    if (start != end) return TextRange(start, end)
    if (text.isBlank()) return TextRange(start)

    var wordStart = start
    while (wordStart > 0 && !text[wordStart - 1].isWhitespace()) {
        wordStart--
    }
    var wordEnd = start
    while (wordEnd < text.length && !text[wordEnd].isWhitespace()) {
        wordEnd++
    }
    return TextRange(wordStart, wordEnd)
}

private fun TextRange.coerceInText(text: String): TextRange {
    return TextRange(
        start.coerceIn(0, text.length),
        end.coerceIn(0, text.length),
    )
}

private fun Uri.toPageMediaAttachment(context: Context): PageMediaAttachment? {
    val resolver = context.contentResolver
    var displayName = lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { value -> value.isNotBlank() }
        ?: "Selected file"
    var sizeBytes = 0L

    runCatching {
        resolver.query(
            this,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex).orEmpty().ifBlank { displayName }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L)
                }
            }
        }
    }

    return PageMediaAttachment(
        id = UUID.randomUUID().toString(),
        uri = toString(),
        name = displayName,
        mimeType = resolver.getType(this).orEmpty(),
        sizeBytes = sizeBytes,
    )
}

private val TableMediaAttachmentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun String.toTableMediaAttachments(): List<PageMediaAttachment> {
    if (isBlank()) return emptyList()
    return runCatching {
        TableMediaAttachmentJson.decodeFromString<List<PageMediaAttachment>>(this)
    }.getOrDefault(emptyList())
}

private fun List<PageMediaAttachment>.toTableMediaCellValue(): String {
    return if (isEmpty()) {
        ""
    } else {
        TableMediaAttachmentJson.encodeToString(this)
    }
}

private fun List<PageMediaAttachment>.toTableMediaSummary(): String {
    return when (size) {
        0 -> "Attach file"
        1 -> first().name.ifBlank { "1 file" }
        else -> "$size files"
    }
}

private fun Context.persistMediaReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun Context.openMediaAttachment(attachment: PageMediaAttachment) {
    val uri = Uri.parse(attachment.uri)
    val mimeType = attachment.mimeType.ifBlank { "*/*" }
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(this, "Unable to open file", Toast.LENGTH_SHORT).show()
    }
}

private fun Long.formatFileSize(): String {
    if (this <= 0L) return "unknown size"
    val units = listOf("B", "KB", "MB", "GB")
    var size = toDouble()
    var unitIndex = 0
    while (size >= 1024.0 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${size.toLong()} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(Locale.US, size, units[unitIndex])
    }
}

private val AiAutofillPropertyTypes = listOf(
    PagePropertyType.Summarize,
    PagePropertyType.Translate,
)

private val CorePropertyTypes = listOf(
    PagePropertyType.Text,
    PagePropertyType.Number,
    PagePropertyType.Select,
    PagePropertyType.MultiSelect,
    PagePropertyType.Status,
    PagePropertyType.Date,
    PagePropertyType.Person,
    PagePropertyType.FilesMedia,
    PagePropertyType.Checkbox,
    PagePropertyType.Url,
    PagePropertyType.Email,
    PagePropertyType.Phone,
    PagePropertyType.Formula,
    PagePropertyType.Relation,
    PagePropertyType.Rollup,
    PagePropertyType.CreatedTime,
    PagePropertyType.CreatedBy,
    PagePropertyType.LastEditedTime,
    PagePropertyType.LastEditedBy,
    PagePropertyType.Button,
    PagePropertyType.Place,
    PagePropertyType.Id,
)

private val ConnectionPropertyTypes = listOf(
    PagePropertyType.GoogleDriveFile,
    PagePropertyType.FigmaFile,
    PagePropertyType.GitHubPullRequests,
    PagePropertyType.ZendeskTicket,
)

private val PagePropertyType.label: String
    get() = when (this) {
        PagePropertyType.Summarize -> "Summarize"
        PagePropertyType.Translate -> "Translate"
        PagePropertyType.Text -> "Text"
        PagePropertyType.Number -> "Number"
        PagePropertyType.Select -> "Select"
        PagePropertyType.MultiSelect -> "Multi-select"
        PagePropertyType.Status -> "Status"
        PagePropertyType.Date -> "Date"
        PagePropertyType.Person -> "Person"
        PagePropertyType.FilesMedia -> "Files & media"
        PagePropertyType.Checkbox -> "Checkbox"
        PagePropertyType.Url -> "URL"
        PagePropertyType.Email -> "Email"
        PagePropertyType.Phone -> "Phone"
        PagePropertyType.Formula -> "Formula"
        PagePropertyType.Relation -> "Relation"
        PagePropertyType.Rollup -> "Rollup"
        PagePropertyType.CreatedTime -> "Created time"
        PagePropertyType.CreatedBy -> "Created by"
        PagePropertyType.LastEditedTime -> "Last edited time"
        PagePropertyType.LastEditedBy -> "Last edited by"
        PagePropertyType.Button -> "Button"
        PagePropertyType.Place -> "Place"
        PagePropertyType.Id -> "ID"
        PagePropertyType.GoogleDriveFile -> "Google Drive File"
        PagePropertyType.FigmaFile -> "Figma File"
        PagePropertyType.GitHubPullRequests -> "GitHub Pull Requests"
        PagePropertyType.ZendeskTicket -> "Zendesk Ticket"
    }

private val PagePropertyType.symbol: String
    get() = when (this) {
        PagePropertyType.Summarize -> "AI"
        PagePropertyType.Translate -> "A"
        PagePropertyType.Text -> "T"
        PagePropertyType.Number -> "#"
        PagePropertyType.Select -> "v"
        PagePropertyType.MultiSelect -> "[]"
        PagePropertyType.Status -> "*"
        PagePropertyType.Date -> "Cal"
        PagePropertyType.Person -> "@"
        PagePropertyType.FilesMedia -> "F"
        PagePropertyType.Checkbox -> "OK"
        PagePropertyType.Url -> "URL"
        PagePropertyType.Email -> "Mail"
        PagePropertyType.Phone -> "Tel"
        PagePropertyType.Formula -> "Fx"
        PagePropertyType.Relation -> "Rel"
        PagePropertyType.Rollup -> "Roll"
        PagePropertyType.CreatedTime -> "CT"
        PagePropertyType.CreatedBy -> "CB"
        PagePropertyType.LastEditedTime -> "ET"
        PagePropertyType.LastEditedBy -> "EB"
        PagePropertyType.Button -> "Btn"
        PagePropertyType.Place -> "Pin"
        PagePropertyType.Id -> "ID"
        PagePropertyType.GoogleDriveFile -> "G"
        PagePropertyType.FigmaFile -> "Fig"
        PagePropertyType.GitHubPullRequests -> "GH"
        PagePropertyType.ZendeskTicket -> "Zen"
    }

private val PagePropertyType.valuePlaceholder: String
    get() = when (this) {
        PagePropertyType.Number -> "0"
        PagePropertyType.Select -> "Option"
        PagePropertyType.MultiSelect -> "Option 1, Option 2"
        PagePropertyType.Status -> "Not started"
        PagePropertyType.Date,
        PagePropertyType.CreatedTime,
        PagePropertyType.LastEditedTime,
        -> "YYYY-MM-DD"
        PagePropertyType.Email -> "name@example.com"
        PagePropertyType.Phone -> "+60"
        PagePropertyType.Url -> "https://"
        PagePropertyType.Place -> "Place"
        PagePropertyType.FilesMedia -> "File or media reference"
        PagePropertyType.Relation -> "Related page"
        PagePropertyType.Rollup -> "Rollup value"
        PagePropertyType.Formula -> "Formula result"
        PagePropertyType.Person,
        PagePropertyType.CreatedBy,
        PagePropertyType.LastEditedBy,
        -> "Person"
        PagePropertyType.Button -> "Button action"
        PagePropertyType.Id -> "ID"
        PagePropertyType.Summarize -> "Summary"
        PagePropertyType.Translate -> "Translation"
        PagePropertyType.GoogleDriveFile -> "Google Drive file"
        PagePropertyType.FigmaFile -> "Figma file"
        PagePropertyType.GitHubPullRequests -> "GitHub pull request"
        PagePropertyType.ZendeskTicket -> "Zendesk ticket"
        PagePropertyType.Text,
        PagePropertyType.Checkbox,
        -> "Value"
    }

private val PagePropertyType.isBasicAiType: Boolean
    get() = this == PagePropertyType.Summarize || this == PagePropertyType.Translate

private data class TableViewOption(
    val view: PageTableView,
    val label: String,
) {
    companion object {
        val entries = listOf(
            TableViewOption(PageTableView.Table, "Table"),
            TableViewOption(PageTableView.List, "List"),
            TableViewOption(PageTableView.Board, "Board"),
            TableViewOption(PageTableView.Calendar, "Calendar"),
            TableViewOption(PageTableView.Gallery, "Gallery"),
            TableViewOption(PageTableView.Timeline, "Timeline"),
            TableViewOption(PageTableView.Dashboard, "Dashboard"),
        )
    }
}

private data class PageTableReference(
    val blockId: String,
    val title: String,
    val table: PageTable,
)

private enum class TableControlType {
    Sort,
    Filter,
    Group,
}

private data class TableRowSummary(
    val row: PageTableRow,
    val title: String,
    val status: String,
    val date: String,
    val endDate: String,
    val details: String,
    val timelineDetails: String,
)

private data class DashboardStat(
    val label: String,
    val value: String,
)

private val TableCellWidth = 180.dp
private val TableOpenWidth = 88.dp
private val TableActionWidth = 48.dp
private val TableAddColumnWidth = 64.dp
private val TableHeaderHeight = 48.dp
private val TableRowHeight = 54.dp
private val TableGroupHeaderWidth = 280.dp
private val BoardColumnWidth = 220.dp
private val CalendarDayWidth = 240.dp
private val GalleryItemWidth = 220.dp
private val TimelineDateWidth = 92.dp
private val DashboardStatWidth = 120.dp
private val DashboardLabelWidth = 96.dp
private val DashboardCountWidth = 32.dp
private val PropertySymbolWidth = 44.dp
private val TableStatusOptions = listOf("Not started", "In progress", "Done", "Blocked")
private val TableWeekdayLabels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
private val TableTimeOptions = List(48) { index -> LocalTime.MIDNIGHT.plusMinutes(index * 30L) }
private val TableTimezoneOptions = listOf("Local", "GMT+0", "GMT+2", "GMT+8")
private const val NoStatusLabel = "No status"
private const val NoDateLabel = "No date"
private const val CheckboxValueChecked = "true"

@Serializable
private data class TableDateCellValue(
    val startDate: String = "",
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val includeEndDate: Boolean = false,
    val includeTime: Boolean = false,
    val timezoneLabel: String = "Local",
)

private val TableDateCellJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun String.toTableDateCellValue(): TableDateCellValue {
    val trimmed = trim()
    if (trimmed.isBlank()) return TableDateCellValue()
    if (trimmed.startsWith("{")) {
        return runCatching {
            TableDateCellJson.decodeFromString<TableDateCellValue>(trimmed)
        }.getOrDefault(TableDateCellValue())
    }
    val parsedDate = trimmed.toLocalDateOrNull()
    return TableDateCellValue(
        startDate = parsedDate
            ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ?: trimmed,
    )
}

private fun TableDateCellValue.toTableDateCellStorageValue(): String {
    if (startDate.isBlank()) return ""
    val isPlainDate = !includeTime &&
        !includeEndDate &&
        startTime.isBlank() &&
        endDate.isBlank() &&
        timezoneLabel == "Local"
    return if (isPlainDate) {
        startDate
    } else {
        TableDateCellJson.encodeToString(this)
    }
}

private fun PageTableColumn.displayDateCellValue(rawValue: String): String {
    val value = rawValue.toTableDateCellValue()
    val date = value.startDate.toLocalDateOrNull() ?: return value.startDate
    val parts = mutableListOf(date.formatForColumn(dateFormat))
    if (value.includeTime && timeFormat != PageTableTimeFormat.Hidden) {
        val time = value.startTime.toLocalTimeOrNull()
        if (time != null) {
            parts += time.formatForColumn(timeFormat.visibleOrDefault())
        }
    }
    return parts.joinToString(" ")
}

private fun LocalDate.formatForColumn(format: PageTableDateFormat): String {
    val pattern = when (format) {
        PageTableDateFormat.DayMonthYear -> "dd/MM/yyyy"
        PageTableDateFormat.MonthDayYear -> "MM/dd/yyyy"
        PageTableDateFormat.YearMonthDay -> "yyyy-MM-dd"
    }
    return format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

private fun LocalTime.formatForColumn(format: PageTableTimeFormat): String {
    val pattern = when (format.visibleOrDefault()) {
        PageTableTimeFormat.TwelveHour -> "h:mm a"
        PageTableTimeFormat.TwentyFourHour -> "HH:mm"
        PageTableTimeFormat.Hidden -> "h:mm a"
    }
    return format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

private fun PageTableTimeFormat.visibleOrDefault(): PageTableTimeFormat {
    return if (this == PageTableTimeFormat.Hidden) {
        PageTableTimeFormat.TwelveHour
    } else {
        this
    }
}

private val PageTableDateFormat.label: String
    get() = when (this) {
        PageTableDateFormat.DayMonthYear -> "Day/Month/Year"
        PageTableDateFormat.MonthDayYear -> "Month/Day/Year"
        PageTableDateFormat.YearMonthDay -> "Year/Month/Day"
    }

private val PageTableTimeFormat.label: String
    get() = when (this) {
        PageTableTimeFormat.Hidden -> "Hidden"
        PageTableTimeFormat.TwelveHour -> "12 hour"
        PageTableTimeFormat.TwentyFourHour -> "24 hour"
    }

private val PageTableDateReminder.label: String
    get() = when (this) {
        PageTableDateReminder.None -> "None"
        PageTableDateReminder.AtTimeOfEvent -> "At time of event"
        PageTableDateReminder.OnDayOfEvent -> "On day of event"
        PageTableDateReminder.OneDayBefore -> "1 day before"
    }

private fun String.toLocalDateOrNull(): LocalDate? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    val formatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US),
    )
    return formatters.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDate.parse(trimmed, formatter) }.getOrNull()
    }
}

private fun String.toLocalTimeOrNull(): LocalTime? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    val formatters = listOf(
        DateTimeFormatter.ISO_LOCAL_TIME,
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
        DateTimeFormatter.ofPattern("HH:mm", Locale.US),
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),
    )
    return formatters.firstNotNullOfOrNull { formatter ->
        runCatching { LocalTime.parse(trimmed.uppercase(Locale.US), formatter) }.getOrNull()
    }
}

private fun String.toDatePickerMillis(): Long? {
    val date = trim().toTableDateCellValue().startDate.toLocalDateOrNull() ?: return null
    return runCatching {
        date
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()
}

private fun Long.toIsoDateString(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}

private fun List<PageBlock>.tableReferences(): List<PageTableReference> {
    return flatMap { block ->
        val current = if (block.type == PageBlockType.DatabaseTable) {
            listOf(
                PageTableReference(
                    blockId = block.id,
                    title = block.table.title,
                    table = block.table,
                ),
            )
        } else {
            emptyList()
        }
        current + block.children.tableReferences()
    }
}

private fun PageTable.rowSummaries(
    tableReferences: List<PageTableReference>,
    dateColumnId: String = "",
    endDateColumnId: String = "",
    statusColumnId: String = "",
): List<TableRowSummary> {
    val titleColumn = titleColumn()
    val statusColumn = statusColumn(statusColumnId)
    val dateColumn = dateColumn(dateColumnId)
    val endDateColumn = columns.firstOrNull { column -> column.id == endDateColumnId }

    return visibleRows(tableReferences).map { row ->
        val excludedForDetails = setOfNotNull(titleColumn?.id)
        val excludedForTimeline = setOfNotNull(titleColumn?.id, statusColumn?.id, dateColumn?.id, endDateColumn?.id)
        TableRowSummary(
            row = row,
            title = displayCellText(row, titleColumn, tableReferences).ifBlank { "Untitled" },
            status = displayCellText(row, statusColumn, tableReferences).ifBlank { NoStatusLabel },
            date = displayCellText(row, dateColumn, tableReferences).ifBlank { NoDateLabel },
            endDate = displayCellText(row, endDateColumn, tableReferences),
            details = rowDetails(row, excludedForDetails, tableReferences),
            timelineDetails = rowDetails(row, excludedForTimeline, tableReferences),
        )
    }
}

private fun PageTable.visibleRows(tableReferences: List<PageTableReference> = emptyList()): List<PageTableRow> {
    val filterColumn = columns.firstOrNull { column -> column.id == filter.columnId }
    val filteredRows = if (filterColumn != null && filter.query.isNotBlank()) {
        rows.filter { row -> displayCellText(row, filterColumn, tableReferences).contains(filter.query, ignoreCase = true) }
    } else {
        rows
    }
    val sortColumn = columns.firstOrNull { column -> column.id == sort.columnId } ?: return filteredRows
    val sortedRows = filteredRows.sortedWith { left, right ->
        compareRowsByColumn(left, right, sortColumn, tableReferences)
    }
    return if (sort.direction == PageTableSortDirection.Descending) {
        sortedRows.asReversed()
    } else {
        sortedRows
    }
}

private fun PageTable.groupColumn(): PageTableColumn? {
    return columns.firstOrNull { column -> column.id == groupByColumnId }
}

private fun PageTable.groupedSummaries(
    tableReferences: List<PageTableReference>,
    defaultToStatus: Boolean = false,
): List<Pair<String, List<TableRowSummary>>> {
    val summaries = rowSummaries(tableReferences)
    val groupingColumn = groupColumn()
    if (groupingColumn != null) {
        return summaries.groupBy { summary -> groupLabel(summary.row, groupingColumn, tableReferences) }.toList()
    }
    if (defaultToStatus) {
        return summaries.groupBy { summary -> summary.status }.toList()
    }
    return listOf("" to summaries)
}

private fun PageTable.groupLabel(
    row: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
): String {
    return displayCellText(row, column, tableReferences).ifBlank { "Empty" }
}

private fun PageTable.compareRowsByColumn(
    leftRow: PageTableRow,
    rightRow: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
): Int {
    val left = displayCellText(leftRow, column, tableReferences)
    val right = displayCellText(rightRow, column, tableReferences)
    return when (column.type) {
        PageTableColumnType.Number -> compareValues(left.toDoubleOrNull(), right.toDoubleOrNull())
        PageTableColumnType.Checkbox -> compareValues(left == CheckboxValueChecked, right == CheckboxValueChecked)
        PageTableColumnType.Date,
        PageTableColumnType.Formula,
        PageTableColumnType.Relation,
        PageTableColumnType.Rollup,
        PageTableColumnType.Status,
        PageTableColumnType.Text,
        PageTableColumnType.FilesMedia,
        -> left.lowercase().compareTo(right.lowercase())
    }
}

private val PageTableSortDirection.arrowLabel: String
    get() = when (this) {
        PageTableSortDirection.Ascending -> "Asc"
        PageTableSortDirection.Descending -> "Desc"
    }

private fun PageTable.titleColumn(): PageTableColumn? {
    return columns.firstOrNull()
}

private fun PageTable.statusColumn(preferredColumnId: String = ""): PageTableColumn? {
    if (preferredColumnId.isNotBlank()) {
        columns.firstOrNull { column -> column.id == preferredColumnId }?.let { column -> return column }
    }
    return columns.firstOrNull { column ->
        column.type == PageTableColumnType.Status ||
            column.name.contains("status", ignoreCase = true) ||
            column.name.contains("stage", ignoreCase = true) ||
            column.name.contains("state", ignoreCase = true) ||
            column.name.contains("phase", ignoreCase = true)
    } ?: columns.getOrNull(1)
}

private fun PageTable.dateColumn(preferredColumnId: String = ""): PageTableColumn? {
    if (preferredColumnId.isNotBlank()) {
        columns.firstOrNull { column -> column.id == preferredColumnId }?.let { column -> return column }
    }
    return columns.firstOrNull { column ->
        column.type == PageTableColumnType.Date ||
            column.name.contains("date", ignoreCase = true) ||
            column.name.contains("due", ignoreCase = true) ||
            column.name.contains("deadline", ignoreCase = true) ||
            column.name.contains("day", ignoreCase = true)
    }
}

private fun PageTable.dateCandidateColumns(): List<PageTableColumn> {
    val candidates = columns.filter { column ->
        column.type == PageTableColumnType.Date ||
            column.name.contains("date", ignoreCase = true) ||
            column.name.contains("due", ignoreCase = true) ||
            column.name.contains("deadline", ignoreCase = true) ||
            column.name.contains("day", ignoreCase = true) ||
            column.name.contains("start", ignoreCase = true) ||
            column.name.contains("end", ignoreCase = true)
    }
    return candidates.ifEmpty { columns }
}

private fun PageTable.metricCandidateColumns(): List<PageTableColumn> {
    val candidates = columns.filter { column ->
        column.type == PageTableColumnType.Number ||
            column.type == PageTableColumnType.Formula ||
            column.type == PageTableColumnType.Rollup ||
            column.name.contains("amount", ignoreCase = true) ||
            column.name.contains("total", ignoreCase = true) ||
            column.name.contains("score", ignoreCase = true) ||
            column.name.contains("count", ignoreCase = true)
    }
    return candidates.ifEmpty { columns }
}

private fun PageTable.rowDetails(
    row: PageTableRow,
    excludedColumnIds: Set<String>,
    tableReferences: List<PageTableReference>,
): String {
    return columns
        .filterNot { column -> column.id in excludedColumnIds }
        .mapNotNull { column ->
            displayCellText(row, column, tableReferences)
                .takeIf { value -> value.isNotBlank() }
                ?.let { value -> "${column.name}: $value" }
        }
        .joinToString(" • ")
}

private fun PageTable.displayCellText(
    row: PageTableRow,
    column: PageTableColumn?,
    tableReferences: List<PageTableReference>,
    depth: Int = 0,
): String {
    if (column == null || depth > 4) return ""
    val rawValue = row.cellText(column)
    return when (column.type) {
        PageTableColumnType.Formula -> evaluateFormula(row, column.formula, tableReferences, depth + 1)
        PageTableColumnType.Relation -> relationDisplayText(rawValue, column, tableReferences)
        PageTableColumnType.Rollup -> rollupDisplayText(row, column, tableReferences, depth + 1)
        PageTableColumnType.Checkbox -> if (rawValue == CheckboxValueChecked) "Checked" else ""
        PageTableColumnType.FilesMedia -> rawValue.toTableMediaAttachments()
            .joinToString(separator = ", ") { attachment -> attachment.name }
        PageTableColumnType.Date -> column.displayDateCellValue(rawValue)
        PageTableColumnType.Text,
        PageTableColumnType.Number,
        PageTableColumnType.Status,
        -> rawValue
    }
}

private fun PageTable.evaluateFormula(
    row: PageTableRow,
    formula: String,
    tableReferences: List<PageTableReference>,
    depth: Int,
): String {
    if (formula.isBlank()) return ""
    var expression = formula
    columns
        .filterNot { column -> column.type == PageTableColumnType.Formula && column.formula == formula }
        .sortedByDescending { column -> column.name.length }
        .forEach { column ->
            val value = displayCellText(row, column, tableReferences, depth)
                .toDoubleOrNull()
                ?: 0.0
            expression = expression.replace("{${column.name}}", value.toString(), ignoreCase = true)
        }
    return expression.evaluateArithmeticExpression()
        ?.formatTableNumber()
        .orEmpty()
}

private fun PageTable.relationDisplayText(
    rawValue: String,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
): String {
    val targetTable = tableReferences.firstOrNull { reference -> reference.blockId == column.relationTargetTableId }?.table
        ?: return ""
    val relatedIds = rawValue.relatedRowIds()
    return targetTable.rows
        .filter { row -> row.id in relatedIds }
        .joinToString { row -> targetTable.rowTitle(row) }
}

private fun PageTable.rollupDisplayText(
    row: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    depth: Int,
): String {
    val relationColumn = columns.firstOrNull { candidate -> candidate.id == column.rollupRelationColumnId }
        ?: return ""
    val targetTable = tableReferences.firstOrNull { reference ->
        reference.blockId == relationColumn.relationTargetTableId
    }?.table ?: return ""
    val targetColumn = targetTable.columns.firstOrNull { target -> target.id == column.rollupTargetColumnId }
    val relatedIds = row.cellText(relationColumn).relatedRowIds()
    val values = targetTable.rows
        .filter { relatedRow -> relatedRow.id in relatedIds }
        .map { relatedRow -> targetTable.displayCellText(relatedRow, targetColumn, tableReferences, depth) }
        .filter { value -> value.isNotBlank() }

    return when (column.rollupAggregation) {
        PageTableRollupAggregation.Count -> values.size.toString()
        PageTableRollupAggregation.Sum -> values.sumOf { value -> value.toDoubleOrNull() ?: 0.0 }.formatTableNumber()
        PageTableRollupAggregation.Average -> {
            if (values.isEmpty()) {
                ""
            } else {
                (values.sumOf { value -> value.toDoubleOrNull() ?: 0.0 } / values.size.toDouble()).formatTableNumber()
            }
        }
        PageTableRollupAggregation.Min -> values.mapNotNull { value -> value.toDoubleOrNull() }.minOrNull()?.formatTableNumber().orEmpty()
        PageTableRollupAggregation.Max -> values.mapNotNull { value -> value.toDoubleOrNull() }.maxOrNull()?.formatTableNumber().orEmpty()
    }
}

private fun PageTableRow.cellText(column: PageTableColumn?): String {
    return column?.let { tableColumn -> cells[tableColumn.id] }
        .orEmpty()
        .trim()
}

private fun PageTable.rowTitle(row: PageTableRow): String {
    return row.cellText(titleColumn()).ifBlank { "Untitled" }
}

private val PageTableColumnType.needsColumnConfig: Boolean
    get() = this == PageTableColumnType.Formula ||
        this == PageTableColumnType.Relation ||
        this == PageTableColumnType.Rollup

private val PageTableColumnType.label: String
    get() = when (this) {
        PageTableColumnType.Text -> "Text"
        PageTableColumnType.Number -> "Number"
        PageTableColumnType.Status -> "Status"
        PageTableColumnType.Date -> "Date"
        PageTableColumnType.FilesMedia -> "Files & media"
        PageTableColumnType.Checkbox -> "Checkbox"
        PageTableColumnType.Formula -> "Formula"
        PageTableColumnType.Relation -> "Relation"
        PageTableColumnType.Rollup -> "Rollup"
    }

private val PageTableColumnType.shortLabel: String
    get() = when (this) {
        PageTableColumnType.Text -> "Aa"
        PageTableColumnType.Number -> "#"
        PageTableColumnType.Status -> "St"
        PageTableColumnType.Date -> "Cal"
        PageTableColumnType.FilesMedia -> "F"
        PageTableColumnType.Checkbox -> "OK"
        PageTableColumnType.Formula -> "Fx"
        PageTableColumnType.Relation -> "Rel"
        PageTableColumnType.Rollup -> "Roll"
    }

private fun PageTableColumn.configSummary(
    table: PageTable,
    tableReferences: List<PageTableReference>,
): String {
    return when (type) {
        PageTableColumnType.Formula -> formula.ifBlank { "Set formula" }
        PageTableColumnType.Relation -> {
            tableReferences.firstOrNull { reference -> reference.blockId == relationTargetTableId }
                ?.title
                ?.ifBlank { "Untitled table" }
                ?: "Set target"
        }
        PageTableColumnType.Rollup -> {
            val relationColumn = table.columns.firstOrNull { column -> column.id == rollupRelationColumnId }
            val targetTable = tableReferences.firstOrNull { reference ->
                reference.blockId == relationColumn?.relationTargetTableId
            }?.table
            val targetColumn = targetTable?.columns?.firstOrNull { column -> column.id == rollupTargetColumnId }
            listOfNotNull(
                relationColumn?.name?.ifBlank { "Relation" },
                targetColumn?.name?.ifBlank { "Property" },
                rollupAggregation.name,
            ).joinToString(" • ").ifBlank { "Set rollup" }
        }
        PageTableColumnType.Text,
        PageTableColumnType.Number,
        PageTableColumnType.Status,
        PageTableColumnType.Date,
        PageTableColumnType.Checkbox,
        PageTableColumnType.FilesMedia,
        -> ""
    }
}

private fun String.relatedRowIds(): Set<String> {
    return split(",")
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .toSet()
}

private fun String.evaluateArithmeticExpression(): Double? {
    class Parser(private val input: String) {
        private var index = 0

        fun parse(): Double? {
            val value = parseExpression() ?: return null
            skipSpaces()
            return if (index == input.length) value else null
        }

        private fun parseExpression(): Double? {
            var value = parseTerm() ?: return null
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '+' -> {
                        index++
                        value + (parseTerm() ?: return null)
                    }
                    '-' -> {
                        index++
                        value - (parseTerm() ?: return null)
                    }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double? {
            var value = parseFactor() ?: return null
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '*' -> {
                        index++
                        value * (parseFactor() ?: return null)
                    }
                    '/' -> {
                        index++
                        val divisor = parseFactor() ?: return null
                        if (divisor == 0.0) return null
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double? {
            skipSpaces()
            if (peek() == '-') {
                index++
                return -(parseFactor() ?: return null)
            }
            if (peek() == '(') {
                index++
                val value = parseExpression() ?: return null
                skipSpaces()
                if (peek() != ')') return null
                index++
                return value
            }
            val start = index
            while (peek()?.let { char -> char.isDigit() || char == '.' } == true) {
                index++
            }
            return input.substring(start, index).toDoubleOrNull()
        }

        private fun skipSpaces() {
            while (peek()?.isWhitespace() == true) {
                index++
            }
        }

        private fun peek(): Char? = input.getOrNull(index)
    }

    return Parser(this).parse()
}

private fun Double.formatTableNumber(): String {
    return if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        "%.2f".format(Locale.US, this).trimEnd('0').trimEnd('.')
    }
}

private fun String.compactControlLabel(maxLength: Int = 28): String {
    val clean = trim()
    return if (clean.length <= maxLength) {
        clean
    } else {
        clean.take(maxLength - 3).trimEnd() + "..."
    }
}

private fun String.dateSortKey(): String {
    if (this == NoDateLabel) return "9999-99-99"
    val clean = trim()
    val dateCandidate = clean.substringBefore(" ").ifBlank { clean }
    return dateCandidate.toLocalDateOrNull()
        ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ?: clean.lowercase()
}

@Preview(showBackground = true)
@Composable
private fun PageEditorScreenPreview() {
    ChangeYourLifeTheme {
        PageEditorScreen(
            uiState = PageEditorUiState(
                isLoading = false,
                page = Page(
                    id = "preview-page",
                    workspaceId = "preview-workspace",
                    parentPageId = null,
                    title = "Weekly reset",
                    content = "",
                    sortOrder = 0,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                ),
                title = "Weekly reset",
                blocks = listOf(
                    PageBlock(
                        id = "1",
                        type = PageBlockType.Heading,
                        text = "Weekly reset",
                    ),
                    PageBlock(
                        id = "2",
                        type = PageBlockType.Todo,
                        text = "Review goals",
                    ),
                    PageBlock(
                        id = "3",
                        type = PageBlockType.Bullet,
                        text = "Plan work blocks",
                    ),
                ),
            ),
            homeAiState = HomeUiState(),
            onBack = {},
            onOpenPage = { _, _, _ -> },
            onTitleChange = {},
            onBlockTextChange = { _, _ -> },
            onBlockRichTextChange = { _, _, _ -> },
            onBlockMediaAdd = { _, _ -> },
            onBlockMediaRemove = { _, _ -> },
            onToggleTodo = {},
            onAddBlock = {},
            onDeleteBlock = {},
            onMoveBlockUp = {},
            onMoveBlockDown = {},
            onTableTitleChange = { _, _ -> },
            onTableViewChange = { _, _ -> },
            onTableViewConfigChange = { _, _ -> },
            onTableSortChange = { _, _, _ -> },
            onTableFilterChange = { _, _, _ -> },
            onTableGroupChange = { _, _ -> },
            onTableColumnNameChange = { _, _, _ -> },
            onTableColumnTypeChange = { _, _, _ -> },
            onTableColumnDateSettingsChange = { _, _, _, _, _, _ -> },
            onTableColumnFormulaChange = { _, _, _ -> },
            onTableColumnRelationTargetChange = { _, _, _ -> },
            onTableColumnRollupChange = { _, _, _, _, _ -> },
            onTableCellChange = { _, _, _, _ -> },
            onAddTableColumn = { _, _, _ -> },
            onInsertTableColumn = { _, _, _ -> },
            onDuplicateTableColumn = { _, _ -> },
            onDeleteTableColumn = { _, _ -> },
            onAddTableRow = {},
            onDeleteTableRow = { _, _ -> },
            onTableRowBlockTextChange = { _, _, _, _ -> },
            onTableRowBlockRichTextChange = { _, _, _, _, _ -> },
            onTableRowBlockMediaAdd = { _, _, _, _ -> },
            onTableRowBlockMediaRemove = { _, _, _, _ -> },
            onToggleTableRowTodoBlock = { _, _, _ -> },
            onAddTableRowPageBlock = { _, _, _ -> },
            onDeleteTableRowPageBlock = { _, _, _ -> },
            onAddProperty = { _, _ -> },
            onPropertyNameChange = { _, _ -> },
            onPropertyValueChange = { _, _ -> },
            onDeleteProperty = {},
            onAddChildBlock = { _, _ -> },
            onCreateChildPage = {},
            onUndoEditorChange = {},
            onSendHomeAiMessage = {},
            onClearHomeAiHistory = {},
            onCreateHomeChatSession = {},
            onDismissHomeAiError = {},
        )
    }
}
