package com.changeyourlife.cyl.presentation.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.changeyourlife.cyl.domain.model.ChatSession
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.SyncOverview
import com.changeyourlife.cyl.presentation.ai.AiChatMode
import com.changeyourlife.cyl.presentation.ai.AiChatSheet
import com.changeyourlife.cyl.presentation.ai.AiChatMessage
import com.changeyourlife.cyl.presentation.ai.AiChatPageLink
import com.changeyourlife.cyl.presentation.components.CylBottomCommandBar
import com.changeyourlife.cyl.presentation.components.CylChromePill
import com.changeyourlife.cyl.presentation.components.CylChromeIconButton
import com.changeyourlife.cyl.presentation.components.CylFloatingChromeSurface
import com.changeyourlife.cyl.presentation.page.PageModuleType
import com.changeyourlife.cyl.presentation.theme.ChangeYourLifeTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    onCreatePage: () -> Unit,
    onOpenPage: (String, String, String) -> Unit,
    onSearch: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onCreatePage = {
            viewModel.createQuickPage { page ->
                onOpenPage(page.id, "", "")
            }
            onCreatePage()
        },
        onCreateModule = { type ->
            viewModel.createModulePage(type) { page ->
                onOpenPage(page.id, "", "")
            }
        },
        onDeletePage = viewModel::deletePage,
        onRestorePage = viewModel::restorePage,
        onDeletePagePermanently = viewModel::deletePagePermanently,
        onOpenPage = { pageId -> onOpenPage(pageId, "", "") },
        onOpenPageTarget = onOpenPage,
        onSearch = onSearch,
        onDismissCreateWorkspace = viewModel::hideCreateWorkspace,
        onNewWorkspaceNameChange = viewModel::updateNewWorkspaceName,
        onCreateWorkspace = viewModel::createWorkspace,
        onSendChatMessage = viewModel::sendChatMessage,
        onUndoAiAction = viewModel::undoAiAction,
        onAiModeChange = viewModel::updateAiChatMode,
        onClearChatHistory = viewModel::clearChatHistory,
        onCreateChatSession = viewModel::createNewChatSession,
        onSelectChatSession = viewModel::selectChatSession,
        onDeleteChatSession = viewModel::deleteChatSession,
        onChatHistorySearchQueryChange = viewModel::updateChatHistorySearchQuery,
        onClearChatHistorySearchQuery = viewModel::clearChatHistorySearchQuery,
        onRetrySync = viewModel::retrySyncNow,
        onDismissChatError = viewModel::clearAiChatError,
        onLogout = {
            viewModel.logout(onLoggedOut)
        },
        modifier = modifier,
    )
}

@Composable
fun HomeSearchRoute(
    onBack: () -> Unit,
    onOpenPage: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeSearchScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenPage = onOpenPage,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onClearSearchQuery = viewModel::clearSearchQuery,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onCreatePage: () -> Unit,
    onCreateModule: (PageModuleType) -> Unit,
    onDeletePage: (String) -> Unit,
    onRestorePage: (String) -> Unit,
    onDeletePagePermanently: (String) -> Unit,
    onOpenPage: (String) -> Unit,
    onOpenPageTarget: (String, String, String) -> Unit,
    onSearch: () -> Unit,
    onDismissCreateWorkspace: () -> Unit,
    onNewWorkspaceNameChange: (String) -> Unit,
    onCreateWorkspace: () -> Unit,
    onSendChatMessage: (String, List<String>, AiChatMode) -> Unit,
    onUndoAiAction: (String, String) -> Unit,
    onAiModeChange: (AiChatMode) -> Unit,
    onClearChatHistory: () -> Unit,
    onCreateChatSession: () -> Unit,
    onSelectChatSession: (String) -> Unit,
    onDeleteChatSession: (String) -> Unit,
    onChatHistorySearchQueryChange: (String) -> Unit,
    onClearChatHistorySearchQuery: () -> Unit,
    onRetrySync: () -> Unit,
    onDismissChatError: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isChatSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isCreateSheetOpen by rememberSaveable { mutableStateOf(false) }
    var selectedHomeTab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    var selectedPageActionId by rememberSaveable { mutableStateOf<String?>(null) }
    var permanentDeletePage by rememberSaveable { mutableStateOf<Page?>(null) }
    val chatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val createSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageActionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val selectedPageActionPage = remember(
        selectedPageActionId,
        uiState.allPages,
        uiState.recentPages,
    ) {
        (uiState.allPages + uiState.recentPages)
            .distinctBy { page -> page.id }
            .firstOrNull { page -> page.id == selectedPageActionId }
    }

    if (isChatSheetOpen) {
        AiChatSheet(
            messages = uiState.chatMessages,
            mentionPages = uiState.allPages,
            isGenerating = uiState.isAiGeneratingChat,
            errorMessage = uiState.aiChatError,
            aiMode = uiState.aiChatMode,
            modelLabel = uiState.aiModelLabel,
            onAiModeChange = onAiModeChange,
            onSendMessage = onSendChatMessage,
            onUndoAction = onUndoAiAction,
            onClearHistory = onClearChatHistory,
            onCreateChatSession = onCreateChatSession,
            onDismissError = onDismissChatError,
            onOpenPage = { pageId, targetType, targetId ->
                scope.launch { chatSheetState.hide() }.invokeOnCompletion {
                    isChatSheetOpen = false
                    onOpenPageTarget(pageId, targetType, targetId)
                }
            },
            onDismiss = {
                scope.launch { chatSheetState.hide() }.invokeOnCompletion {
                    isChatSheetOpen = false
                }
            },
            sheetState = chatSheetState,
        )
    }
    if (isCreateSheetOpen) {
        HomeCreateSheet(
            onCreatePage = {
                scope.launch { createSheetState.hide() }.invokeOnCompletion {
                    isCreateSheetOpen = false
                    onCreatePage()
                }
            },
            onCreateModule = { type ->
                scope.launch { createSheetState.hide() }.invokeOnCompletion {
                    isCreateSheetOpen = false
                    onCreateModule(type)
                }
            },
            onDismiss = {
                scope.launch { createSheetState.hide() }.invokeOnCompletion {
                    isCreateSheetOpen = false
                }
            },
            sheetState = createSheetState,
        )
    }
    selectedPageActionPage?.let { page ->
        HomePageActionSheet(
            page = page,
            onDeletePage = {
                selectedPageActionId = null
                onDeletePage(page.id)
            },
            onDismiss = { selectedPageActionId = null },
            sheetState = pageActionSheetState,
        )
    }
    permanentDeletePage?.let { page ->
        AlertDialog(
            onDismissRequest = { permanentDeletePage = null },
            title = { Text(text = "Delete permanently?") },
            text = {
                Text(
                    text = "This will permanently delete ${page.title.ifBlank { "Untitled page" }}. This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permanentDeletePage = null
                        onDeletePagePermanently(page.id)
                    },
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeletePage = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
    if (uiState.isCreateWorkspaceDialogVisible) {
        CreateWorkspaceDialog(
            name = uiState.newWorkspaceName,
            canCreate = uiState.canCreateWorkspace,
            onNameChange = onNewWorkspaceNameChange,
            onCreate = onCreateWorkspace,
            onDismiss = onDismissCreateWorkspace,
        )
    }

    if (selectedHomeTab == HomeTab.Trash) {
        TrashScreen(
            pages = uiState.deletedPages,
            onBack = { selectedHomeTab = HomeTab.Home },
            onRestorePage = onRestorePage,
            onDeletePermanently = { page -> permanentDeletePage = page },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            HomeHeader(
                workspaceName = uiState.workspaceName,
                syncOverview = uiState.syncOverview,
                selectedTab = selectedHomeTab,
                onSelectTab = { tab -> selectedHomeTab = tab },
                onRetrySync = onRetrySync,
                onOpenTrash = { selectedHomeTab = HomeTab.Trash },
                onLogout = onLogout,
            )
        },
        bottomBar = {
            HomeBottomBar(
                onSearch = onSearch,
                onOpenAi = { isChatSheetOpen = true },
                onCreatePage = { isCreateSheetOpen = true },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                top = innerPadding.calculateTopPadding() + 20.dp,
                end = 20.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (selectedHomeTab) {
                HomeTab.Home -> {
                    if (uiState.recentPages.isNotEmpty()) {
                        item {
                            HomeSectionHeader(text = "Recents")
                            Spacer(modifier = Modifier.height(10.dp))
                            RecentPagesRail(
                                pages = uiState.recentPages,
                                onOpenPage = onOpenPage,
                            )
                        }
                    }

                    item {
                        HomeSectionHeader(
                            text = "Private",
                            actionIcon = Icons.Rounded.Add,
                            actionContentDescription = "Create private page",
                            onAction = { isCreateSheetOpen = true },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (uiState.allPages.isEmpty()) {
                            EmptyStateCard()
                        }
                    }

                    items(
                        items = uiState.allPages,
                        key = { page -> page.id },
                    ) { page ->
                        HomePageRow(
                            page = page,
                            onOpenPage = onOpenPage,
                            onOpenActions = { selectedPageActionId = page.id },
                        )
                    }
                }

                HomeTab.Chat -> {
                    item {
                        HomeSectionHeader(
                            text = "Chat History",
                            actionIcon = Icons.Rounded.Add,
                            actionContentDescription = "New chat",
                            onAction = {
                                onCreateChatSession()
                                isChatSheetOpen = true
                            },
                        )
                        if (uiState.chatSessions.isEmpty()) {
                            CenteredHomeEmptyLabel(
                                title = "No chat history yet",
                                icon = Icons.Rounded.AutoAwesome,
                            )
                        }
                    }

                    items(
                        items = uiState.chatSessions,
                        key = { session -> session.id },
                    ) { session ->
                        HomeChatSessionRow(
                            session = session,
                            isActive = session.id == uiState.activeChatSessionId,
                            preview = uiState.chatSessionPreviews[session.id],
                            onDelete = { onDeleteChatSession(session.id) },
                            onClick = {
                                onSelectChatSession(session.id)
                                isChatSheetOpen = true
                            },
                        )
                    }
                }

                HomeTab.Activity -> {
                    item {
                        HomeSectionHeader(text = "Mentions & Activity")
                        Spacer(modifier = Modifier.height(8.dp))
                        EmptyHomeTabCard(
                            title = "No mentions or activity yet",
                            icon = Icons.Rounded.Notifications,
                        )
                    }
                }

                HomeTab.Trash -> Unit
            }

        }
    }
}

@Composable
private fun TrashScreen(
    pages: List<Page>,
    onBack: () -> Unit,
    onRestorePage: (String) -> Unit,
    onDeletePermanently: (Page) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = innerPadding.calculateTopPadding() + 14.dp,
                end = 20.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                    Text(
                        text = "Trash",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (pages.isEmpty()) {
                item {
                    EmptyHomeTabCard(
                        title = "Trash is empty",
                        icon = Icons.Rounded.Delete,
                    )
                }
            } else {
                items(
                    items = pages,
                    key = { page -> page.id },
                ) { page ->
                    TrashPageRow(
                        page = page,
                        onRestore = { onRestorePage(page.id) },
                        onDeletePermanently = { onDeletePermanently(page) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSearchScreen(
    uiState: HomeUiState,
    onBack: () -> Unit,
    onOpenPage: (String, String, String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearchQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = innerPadding.calculateTopPadding() + 18.dp,
                end = 20.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                    Column {
                        Text(
                            text = "Search",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Pages, tables, rows, and properties",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                GlobalPageSearch(
                    query = uiState.searchQuery,
                    resultCount = uiState.searchResults.size,
                    onQueryChange = onSearchQueryChange,
                    onClear = onClearSearchQuery,
                )
            }

            if (uiState.searchQuery.isBlank()) {
                item {
                    SearchPromptState()
                }
            } else {
                item {
                    HomeSectionHeader(text = "Results")
                    if (uiState.searchResults.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        EmptySearchCard()
                    }
                }

                items(
                    items = uiState.searchResults,
                    key = { result -> result.page.id },
                ) { result ->
                    SearchResultCard(
                        result = result,
                        onOpenPage = onOpenPage,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeHeader(
    workspaceName: String,
    syncOverview: SyncOverview,
    selectedTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    onRetrySync: () -> Unit,
    onOpenTrash: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isProfileSheetOpen by rememberSaveable { mutableStateOf(false) }

    if (isProfileSheetOpen) {
        HomeProfileSheet(
            workspaceName = workspaceName,
            syncOverview = syncOverview,
            onRetrySync = onRetrySync,
            onOpenTrash = {
                isProfileSheetOpen = false
                onOpenTrash()
            },
            onLogout = {
                isProfileSheetOpen = false
                onLogout()
            },
            onDismiss = { isProfileSheetOpen = false },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CylFloatingChromeSurface(
            modifier = Modifier
                .size(44.dp)
                .clickable { isProfileSheetOpen = true },
            shape = RoundedCornerShape(14.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = workspaceName.firstOrNull()?.uppercaseChar()?.toString() ?: "C",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        CylFloatingChromeSurface(
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeTopTabButton(
                    tab = HomeTab.Home,
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    modifier = Modifier.weight(1f),
                )
                HomeTopTabButton(
                    tab = HomeTab.Chat,
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    modifier = Modifier.weight(1f),
                )
                HomeTopTabButton(
                    tab = HomeTab.Activity,
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeProfileSheet(
    workspaceName: String,
    syncOverview: SyncOverview,
    onRetrySync: () -> Unit,
    onOpenTrash: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val statusColor = syncOverview.syncStatusColor()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = workspaceName.firstOrNull()?.uppercaseChar()?.toString() ?: "C",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workspaceName.ifBlank { "CYL Workspace" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Profile and workspace",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(text = syncOverview.statusTitle()) },
                supportingContent = {
                    Text(
                        text = syncOverview.statusDetail(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    if (syncOverview.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = statusColor,
                        )
                    } else {
                        Icon(
                            imageVector = syncOverview.syncStatusIcon(),
                            contentDescription = null,
                            tint = statusColor,
                        )
                    }
                },
                trailingContent = {
                    TextButton(onClick = onRetrySync) {
                        Text(text = "Retry")
                    }
                },
            )

            ListItem(
                headlineContent = { Text(text = "Trash") },
                supportingContent = { Text(text = "Restore or permanently delete pages") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onOpenTrash),
            )
            ListItem(
                headlineContent = { Text(text = "Logout") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onLogout),
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SyncStatusButton(
    syncOverview: SyncOverview,
    onRetrySync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMenuOpen by rememberSaveable { mutableStateOf(false) }
    val statusColor = when {
        syncOverview.hasConflict || syncOverview.hasError -> MaterialTheme.colorScheme.error
        syncOverview.hasPending || syncOverview.isSyncing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { isMenuOpen = true }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (syncOverview.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = statusColor,
                )
            } else {
                Icon(
                    imageVector = if (syncOverview.isClean) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications,
                    contentDescription = "Sync status",
                    tint = statusColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        DropdownMenu(
            expanded = isMenuOpen,
            onDismissRequest = { isMenuOpen = false },
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = syncOverview.statusTitle(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = syncOverview.statusDetail(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = {
                    if (syncOverview.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (syncOverview.isClean) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = statusColor,
                        )
                    }
                },
                onClick = {},
            )
            DropdownMenuItem(
                text = { Text(text = "Retry sync") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                    )
                },
                onClick = {
                    isMenuOpen = false
                    onRetrySync()
                },
            )
        }
    }
}

private fun SyncOverview.statusTitle(): String {
    return when {
        hasConflict -> "$conflictCount sync conflict"
        isSyncing -> "Syncing"
        hasPending -> "$pendingCount waiting to sync"
        hasError -> "Sync needs attention"
        else -> "Synced"
    }
}

private fun SyncOverview.statusDetail(): String {
    return when {
        hasConflict -> "Open the affected page to resolve conflict."
        hasError -> lastErrorMessage.orEmpty()
        hasPending -> "Changes will upload when connection is available."
        isSyncing -> "Updating your workspace."
        lastCompletedAt > 0L -> "All local changes are uploaded."
        else -> "Ready."
    }
}

@Composable
private fun SyncOverview.syncStatusColor() = when {
    hasConflict || hasError -> MaterialTheme.colorScheme.error
    hasPending || isSyncing -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun SyncOverview.syncStatusIcon(): ImageVector {
    return if (isClean) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications
}

private enum class HomeTab(
    val icon: ImageVector,
    val contentDescription: String,
) {
    Home(
        icon = Icons.Rounded.Home,
        contentDescription = "Home",
    ),
    Chat(
        icon = Icons.Rounded.AutoAwesome,
        contentDescription = "Chat history",
    ),
    Activity(
        icon = Icons.Rounded.Notifications,
        contentDescription = "Mentions and activity",
    ),
    Trash(
        icon = Icons.Rounded.Delete,
        contentDescription = "Trash",
    ),
}

@Composable
private fun HomeTopTabButton(
    tab: HomeTab,
    selectedTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = tab == selectedTab
    CylChromePill(
        selected = isSelected,
        onClick = { onSelectTab(tab) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.contentDescription,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun HomeBottomBar(
    onSearch: () -> Unit,
    onOpenAi: () -> Unit,
    onCreatePage: () -> Unit,
) {
    CylBottomCommandBar(
        centerLabel = "Ask AI",
        centerIcon = Icons.Rounded.AutoAwesome,
        centerContentDescription = "Ask AI",
        onCenterClick = onOpenAi,
        leadingActions = {
            CylChromeIconButton(
                icon = Icons.Rounded.Search,
                contentDescription = "Search",
                onClick = onSearch,
            )
        },
        trailingActions = {
            CylChromeIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Create page",
                onClick = onCreatePage,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCreateSheet(
    onCreatePage: () -> Unit,
    onCreateModule: (PageModuleType) -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Create",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HomePageActionRow(
                icon = Icons.AutoMirrored.Rounded.Article,
                text = "Blank page",
                onClick = onCreatePage,
            )
            Text(
                text = "Modules",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            PageModuleType.entries.forEach { type ->
                HomePageActionRow(
                    icon = Icons.Rounded.AutoAwesome,
                    text = type.label,
                    onClick = { onCreateModule(type) },
                )
            }
        }
    }
}

@Composable
private fun EmptyHomeTabCard(
    title: String,
    icon: ImageVector,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredHomeEmptyLabel(
    title: String,
    icon: ImageVector,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeRowIconFrame(
    imageVector: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HomeChatSessionRow(
    session: ChatSession,
    isActive: Boolean,
    preview: ChatSessionPreview?,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    var isMenuOpen by rememberSaveable { mutableStateOf(false) }
    val supportText = preview?.lastMessage?.takeIf { it.isNotBlank() }
        ?: session.updatedAt.toDisplayDateTime()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.background.copy(alpha = 0f)
                },
            )
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeRowIconFrame(
            imageVector = Icons.Rounded.AutoAwesome,
            tint = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = session.title.ifBlank { "New chat" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(
                onClick = { isMenuOpen = true },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Chat session actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = "Delete chat") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        isMenuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    text: String,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    actionContentDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        } else if (actionIcon != null && onAction != null) {
            IconButton(
                onClick = onAction,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentPagesRail(
    pages: List<Page>,
    onOpenPage: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = pages,
            key = { page -> page.id },
        ) { page ->
            RecentPageCard(
                page = page,
                onOpenPage = onOpenPage,
            )
        }
    }
}

@Composable
private fun RecentPageCard(
    page: Page,
    onOpenPage: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .widthIn(min = 150.dp, max = 190.dp)
            .height(72.dp)
            .clickable { onOpenPage(page.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Article,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = page.title.ifBlank { "Untitled page" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomePageRow(
    page: Page,
    onOpenPage: (String) -> Unit,
    onOpenActions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpenPage(page.id) }
            .padding(start = 8.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeRowIconFrame(
            imageVector = Icons.AutoMirrored.Rounded.Article,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = page.title.ifBlank { "Untitled page" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = page.updatedAt.toDisplayDateTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onOpenActions,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Page actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePageActionSheet(
    page: Page,
    onDeletePage: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeRowIconFrame(
                    imageVector = Icons.AutoMirrored.Rounded.Article,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = page.title.ifBlank { "Untitled page" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Page",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            HomePageActionRow(
                icon = Icons.Rounded.Delete,
                text = "Delete page",
                isDestructive = true,
                onClick = onDeletePage,
            )
        }
    }
}

@Composable
private fun HomePageActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
    }
}

@Composable
private fun TrashPageRow(
    page: Page,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f))
            .padding(start = 8.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeRowIconFrame(
            imageVector = Icons.AutoMirrored.Rounded.Article,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = page.title.ifBlank { "Untitled page" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Deleted ${page.deletedAt?.toDisplayDateTime().orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRestore,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Restore",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(
            onClick = onDeletePermanently,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete permanently",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ModuleQuickCreate(
    onCreateModule: (PageModuleType) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        LazyRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = PageModuleType.entries,
                key = { type -> type.name },
            ) { type ->
                FilterChip(
                    selected = false,
                    onClick = { onCreateModule(type) },
                    label = { Text(text = type.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun GlobalPageSearch(
    query: String,
    resultCount: Int,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(text = "Search pages, tables, rows") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
        if (query.isNotBlank()) {
            Text(
                text = "$resultCount result${if (resultCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    result: PageSearchResult,
    onOpenPage: (String, String, String) -> Unit,
) {
    Column {
        ListItem(
            headlineContent = {
                Text(text = result.page.title.ifBlank { "Untitled page" })
            },
            supportingContent = {
                Text(text = result.snippet)
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Article,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            modifier = Modifier.clickable {
                onOpenPage(result.page.id, result.targetType, result.targetId)
            },
        )
        HorizontalDivider()
    }
}

@Composable
private fun SearchPromptState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Type to search your workspace",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptySearchCard() {
    ListItem(
        headlineContent = { Text(text = "No matching pages") },
        supportingContent = {
            Text(text = "Try another word from a page, table, row, or property.")
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
    )
}

@Composable
private fun CreateWorkspaceDialog(
    name: String,
    canCreate: Boolean,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "New Workspace") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                label = { Text(text = "Workspace name") },
                placeholder = { Text(text = "Personal goals") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = canCreate,
            ) {
                Text(text = "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun EmptyStateCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "No pages yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Tap + to create a blank page or start from a module.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Long.toDisplayDateTime(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
}

@Preview(showBackground = true)
@Composable
private fun HomeRoutePreview() {
    ChangeYourLifeTheme {
        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                pageCount = 2,
            ),
            onCreatePage = {},
            onCreateModule = {},
            onDeletePage = {},
            onRestorePage = {},
            onDeletePagePermanently = {},
            onOpenPage = {},
            onOpenPageTarget = { _, _, _ -> },
            onSearch = {},
            onDismissCreateWorkspace = {},
            onNewWorkspaceNameChange = {},
            onCreateWorkspace = {},
            onSendChatMessage = { _, _, _ -> },
            onUndoAiAction = { _, _ -> },
            onAiModeChange = {},
            onClearChatHistory = {},
            onCreateChatSession = {},
            onSelectChatSession = {},
            onDeleteChatSession = {},
            onChatHistorySearchQueryChange = {},
            onClearChatHistorySearchQuery = {},
            onRetrySync = {},
            onDismissChatError = {},
            onLogout = {},
        )
    }
}
