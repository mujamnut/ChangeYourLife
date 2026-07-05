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
import androidx.compose.ui.focus.onFocusChanged
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

internal enum class PageEditorFocusScope {
    None,
    Header,
    Body,
    Block,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageEditorTopBar(
    pageTitle: String,
    isSaving: Boolean,
    isAiGenerating: Boolean,
    syncState: PageSyncState,
    onBack: () -> Unit,
) {
    var isSyncSheetOpen by rememberSaveable { mutableStateOf(false) }

    if (isSyncSheetOpen) {
        PageSyncStatusSheet(
            syncState = syncState,
            isSaving = isSaving,
            onDismiss = { isSyncSheetOpen = false },
        )
    }

    TopAppBar(
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            PageSyncStatusButton(
                syncState = syncState,
                isSaving = isSaving,
                onClick = { isSyncSheetOpen = true },
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
internal fun PageSyncStatusButton(
    syncState: PageSyncState,
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = syncState.pageSyncColor(isSaving = true),
            )
        } else {
            Icon(
                imageVector = syncState.pageSyncIcon(),
                contentDescription = "Page sync status",
                tint = syncState.pageSyncColor(isSaving = false),
            )
        }
    }
}
@Composable
internal fun PageSyncStatusSheet(
    syncState: PageSyncState,
    isSaving: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Page sync",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            ListItem(
                headlineContent = { Text(text = syncState.pageSyncTitle(isSaving)) },
                supportingContent = { Text(text = syncState.pageSyncDetail(isSaving)) },
                leadingContent = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = syncState.pageSyncColor(isSaving = true),
                        )
                    } else {
                        Icon(
                            imageVector = syncState.pageSyncIcon(),
                            contentDescription = null,
                            tint = syncState.pageSyncColor(isSaving = false),
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

internal fun PageSyncState.pageSyncTitle(isSaving: Boolean): String {
    return when {
        hasConflict -> "Sync conflict"
        isSaving -> "Saving"
        isPendingPush -> "Waiting to sync"
        lastSyncedAt == 0L -> "Not synced yet"
        else -> "Saved"
    }
}

internal fun PageSyncState.pageSyncDetail(isSaving: Boolean): String {
    return when {
        hasConflict -> "This page changed locally and remotely. Resolve the conflict in the page."
        isSaving -> "Saving local changes."
        isPendingPush -> "Changes will upload when connection is available."
        lastSyncedAt == 0L -> "This page has not completed a sync yet."
        else -> "All local page changes are saved."
    }
}

@Composable
internal fun PageSyncState.pageSyncColor(isSaving: Boolean) = when {
    hasConflict -> MaterialTheme.colorScheme.error
    isSaving || isPendingPush -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun PageSyncState.pageSyncIcon(): ImageVector {
    return when {
        hasConflict -> Icons.Rounded.Notifications
        isPendingPush || lastSyncedAt == 0L -> Icons.Rounded.Notifications
        else -> Icons.Rounded.CheckCircle
    }
}

@Composable
internal fun PageTitleEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    BasicTextField(
        value = title,
        onValueChange = onTitleChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 86.dp)
            .onFocusChanged { focusState -> onFocusChanged(focusState.isFocused) },
        singleLine = false,
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 16.dp),
            ) {
                innerTextField()
            }
        },
    )
}

@Composable
internal fun PageSyncConflictBanner(
    onKeepLocal: () -> Unit,
    onUseRemote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Sync conflict",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "This page changed locally and on the server. Choose which version should win.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onUseRemote) {
                    Text("Use server")
                }
                TextButton(onClick = onKeepLocal) {
                    Text("Keep mine")
                }
            }
        }
    }
}

@Composable
internal fun PageEditorBottomBar(
    activeBlockId: String?,
    focusScope: PageEditorFocusScope,
    canUndoEditorChange: Boolean,
    richTextToolbarState: RichTextToolbarUiState?,
    onAddBlock: (PageBlockType) -> Unit,
    onAddDatabaseFromHeader: () -> Unit,
    onChangeActiveBlockType: (PageBlockType) -> Unit,
    onAddChildToActiveBlock: (PageBlockType) -> Unit,
    onInsertTextAboveActiveBlock: () -> Unit,
    onInsertTextBelowActiveBlock: () -> Unit,
    onMoveActiveBlockUp: () -> Unit,
    onMoveActiveBlockDown: () -> Unit,
    onIndentActiveBlock: () -> Unit,
    onOutdentActiveBlock: () -> Unit,
    onCreateLinkedPageFromActiveBlock: () -> Unit,
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
        if (isKeyboardVisible || focusScope != PageEditorFocusScope.None) {
            when (focusScope) {
                PageEditorFocusScope.Header -> {
                    PageKeyboardHeaderToolbar(
                        onAddDatabase = onAddDatabaseFromHeader,
                    )
                }
                PageEditorFocusScope.Body,
                PageEditorFocusScope.Block,
                PageEditorFocusScope.None,
                -> {
                    if (isKeyboardVisible) {
                        richTextToolbarState
                            ?.takeIf { state -> state.isValidForKeyboardToolbar() }
                            ?.let { toolbarState -> PageKeyboardRichTextToolbar(toolbarState) }
                    }
                    PageKeyboardBlockToolbar(
                        activeBlockId = activeBlockId,
                        canUndoEditorChange = canUndoEditorChange,
                        onAddBlock = onAddBlock,
                        onChangeActiveBlockType = onChangeActiveBlockType,
                        onAddChildToActiveBlock = onAddChildToActiveBlock,
                        onInsertTextAboveActiveBlock = onInsertTextAboveActiveBlock,
                        onInsertTextBelowActiveBlock = onInsertTextBelowActiveBlock,
                        onMoveActiveBlockUp = onMoveActiveBlockUp,
                        onMoveActiveBlockDown = onMoveActiveBlockDown,
                        onIndentActiveBlock = onIndentActiveBlock,
                        onOutdentActiveBlock = onOutdentActiveBlock,
                        onCreateLinkedPageFromActiveBlock = onCreateLinkedPageFromActiveBlock,
                        onDeleteActiveBlock = onDeleteActiveBlock,
                        onUndoEditorChange = onUndoEditorChange,
                        showActiveBlockActions = focusScope == PageEditorFocusScope.Body ||
                            focusScope == PageEditorFocusScope.Block,
                    )
                }
            }
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
internal fun PageKeyboardHeaderToolbar(
    onAddDatabase: () -> Unit,
) {
    CylFloatingChromeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageKeyboardIconButton(
                icon = Icons.Rounded.ViewColumn,
                contentDescription = "Create database",
                onClick = onAddDatabase,
            )
        }
    }
}

internal fun RichTextToolbarUiState.isValidForKeyboardToolbar(): Boolean {
    val textLength = value.text.length
    val selection = value.selection
    if (selection.start !in 0..textLength || selection.end !in 0..textLength) return false
    return spans.all { span ->
        span.start in 0..textLength &&
            span.end in 0..textLength &&
            span.start <= span.end
    }
}

@Composable
internal fun PageKeyboardRichTextToolbar(
    state: RichTextToolbarUiState,
) {
    CylFloatingChromeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        RichTextToolbarHost(
            state = state,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
internal fun PageKeyboardBlockToolbar(
    activeBlockId: String?,
    canUndoEditorChange: Boolean,
    onAddBlock: (PageBlockType) -> Unit,
    onChangeActiveBlockType: (PageBlockType) -> Unit,
    onAddChildToActiveBlock: (PageBlockType) -> Unit,
    onInsertTextAboveActiveBlock: () -> Unit,
    onInsertTextBelowActiveBlock: () -> Unit,
    onMoveActiveBlockUp: () -> Unit,
    onMoveActiveBlockDown: () -> Unit,
    onIndentActiveBlock: () -> Unit,
    onOutdentActiveBlock: () -> Unit,
    onCreateLinkedPageFromActiveBlock: () -> Unit,
    onDeleteActiveBlock: () -> Unit,
    onUndoEditorChange: () -> Unit,
    showActiveBlockActions: Boolean,
) {
    val blockTypeEntries = remember {
        EditorCommandRegistry.changeTypeEntries()
            .filter { entry -> entry.changeTypeOrNull() != PageBlockType.DatabaseTable }
    }
    val hasActiveBlock = activeBlockId != null
    CylFloatingChromeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canUndoEditorChange) {
                item {
                    PageKeyboardIconButton(
                        icon = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo",
                        onClick = onUndoEditorChange,
                    )
                }
            }
            items(
                items = blockTypeEntries,
                key = { entry -> entry.command.paletteItemId() },
            ) { entry ->
                val type = entry.changeTypeOrNull() ?: return@items
                PageKeyboardIconButton(
                    icon = type.pageKeyboardIcon(),
                    contentDescription = entry.command.label,
                    onClick = {
                        if (hasActiveBlock) {
                            onChangeActiveBlockType(type)
                        } else {
                            onAddBlock(type)
                        }
                    },
                )
            }
            if (showActiveBlockActions && hasActiveBlock) {
                item {
                    PageKeyboardIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Insert text above",
                        onClick = onInsertTextAboveActiveBlock,
                    )
                }
                item {
                    PageKeyboardIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Insert text below",
                        onClick = onInsertTextBelowActiveBlock,
                    )
                }
                item {
                    PageKeyboardIconButton(
                        icon = Icons.AutoMirrored.Rounded.Article,
                        contentDescription = "Add child text block",
                        onClick = { onAddChildToActiveBlock(PageBlockType.Text) },
                    )
                }
                item {
                    PageKeyboardIconButton(
                        icon = Icons.AutoMirrored.Rounded.Article,
                        contentDescription = "Create linked page",
                        onClick = onCreateLinkedPageFromActiveBlock,
                    )
                }
                item {
                    PageKeyboardIconButton(
                        icon = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Move block up",
                        onClick = onMoveActiveBlockUp,
                    )
                }
                item {
                    PageKeyboardIconButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Move block down",
                        onClick = onMoveActiveBlockDown,
                    )
                }
                item {
                    PageKeyboardIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Indent block",
                        onClick = onIndentActiveBlock,
                    )
                }
                item {
                    PageKeyboardIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Outdent block",
                        onClick = onOutdentActiveBlock,
                    )
                }
                item {
                    PageKeyboardIconButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = "Delete block",
                        isDestructive = true,
                        onClick = onDeleteActiveBlock,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageKeyboardIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
                isDestructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun PageBlockType.pageKeyboardIcon(): ImageVector {
    return when (this) {
        PageBlockType.Text -> Icons.AutoMirrored.Rounded.Article
        PageBlockType.Heading -> Icons.AutoMirrored.Rounded.Article
        PageBlockType.Todo -> Icons.Rounded.TaskAlt
        PageBlockType.Bullet -> Icons.AutoMirrored.Rounded.Sort
        PageBlockType.Numbered -> Icons.AutoMirrored.Rounded.Sort
        PageBlockType.Quote -> Icons.AutoMirrored.Rounded.WrapText
        PageBlockType.Divider -> Icons.Rounded.MoreVert
        PageBlockType.MediaFile -> Icons.Rounded.ContentCopy
        PageBlockType.DatabaseTable -> Icons.Rounded.ViewColumn
    }
}
@Composable
internal fun PageBlockPickerSheet(
    onAddBlock: (PageBlockType) -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    val blockTypeEntries = remember {
        EditorCommandRegistry.changeTypeEntries()
            .filter { entry -> entry.changeTypeOrNull() != PageBlockType.DatabaseTable }
    }
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
            blockTypeEntries.forEach { entry ->
                val type = entry.changeTypeOrNull() ?: return@forEach
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onAddBlock(type) }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = entry.command.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = entry.command.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
