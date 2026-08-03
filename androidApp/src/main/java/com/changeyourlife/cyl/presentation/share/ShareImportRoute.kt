package com.changeyourlife.cyl.presentation.share

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.model.IncomingShareItem
import com.changeyourlife.cyl.domain.model.IncomingShareItemKind
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ShareImportRoute(
    onBack: () -> Unit,
    onImported: (String) -> Unit,
    onAskAi: (String) -> Unit,
    viewModel: ShareImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestExit = {
        val draftStatus = state.draft?.status
        if (draftStatus == null || draftStatus in FinishedShareStatuses) {
            onBack()
        } else {
            viewModel.cancel()
        }
    }
    BackHandler(enabled = !state.isBusy, onBack = requestExit)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ShareImportEvent.Cancelled -> onBack()
                is ShareImportEvent.Imported -> onImported(event.pageId)
                is ShareImportEvent.AskAi -> onAskAi(event.draftId)
            }
        }
    }
    ShareImportScreen(
        state = state,
        onBack = requestExit,
        onDestinationModeChange = viewModel::setDestinationMode,
        onTitleChange = viewModel::setTitle,
        onSelectPage = viewModel::selectPage,
        onRemoveItem = viewModel::remove,
        onRetryItem = viewModel::retry,
        onConfirm = viewModel::confirm,
        onAskAi = viewModel::askAi,
        onCancel = viewModel::cancel,
        onDismissError = viewModel::dismissError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareImportScreen(
    state: ShareImportUiState,
    onBack: () -> Unit,
    onDestinationModeChange: (ShareImportDestinationMode) -> Unit,
    onTitleChange: (String) -> Unit,
    onSelectPage: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onRetryItem: (String) -> Unit,
    onConfirm: () -> Unit,
    onAskAi: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import to CYL", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            ShareImportActions(
                state = state,
                onCancel = onCancel,
                onConfirm = onConfirm,
                onAskAi = onAskAi,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.errorMessage?.let { message ->
                item(key = "error") {
                    InlineImportError(message = message, onDismiss = onDismissError)
                }
            }
            item(key = "destination-label") {
                Text(
                    text = "Destination",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "destination-controls") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.destinationMode == ShareImportDestinationMode.NewPage,
                        onClick = { onDestinationModeChange(ShareImportDestinationMode.NewPage) },
                        label = { Text("New page") },
                    )
                    FilterChip(
                        selected = state.destinationMode == ShareImportDestinationMode.ExistingPage,
                        onClick = { onDestinationModeChange(ShareImportDestinationMode.ExistingPage) },
                        label = { Text("Existing page") },
                    )
                }
            }
            if (state.destinationMode == ShareImportDestinationMode.NewPage) {
                item(key = "title") {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Page title") },
                        singleLine = true,
                    )
                }
            } else {
                if (state.pages.isEmpty()) {
                    item(key = "no-pages") {
                        Text(
                            text = "No existing pages are available in this workspace.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.pages, key = { page -> "destination-${page.id}" }) { page ->
                        DestinationPageRow(
                            title = page.title.ifBlank { "Untitled page" },
                            selected = state.selectedPageId == page.id,
                            onClick = { onSelectPage(page.id) },
                        )
                    }
                }
            }
            item(key = "content-header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Shared content",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${state.stagedItemCount} ready of ${state.visibleItems.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.visibleItems, key = IncomingShareItem::id) { item ->
                SharedItemRow(
                    item = item,
                    isBusy = item.id in state.busyItemIds,
                    onRemove = { onRemoveItem(item.id) },
                    onRetry = { onRetryItem(item.id) },
                )
            }
            item(key = "privacy") {
                Text(
                    text = "Page import stays in your workspace. Ask AI sends only items you explicitly approve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "bottom-space") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ShareImportActions(
    state: ShareImportUiState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onAskAi: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !state.isBusy,
            ) {
                Text("Cancel")
            }
            OutlinedButton(
                onClick = onAskAi,
                enabled = state.canAskAi && !state.isBusy,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Ask AI")
            }
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm && !state.isBusy,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        if (state.destinationMode == ShareImportDestinationMode.NewPage) {
                            "Create page"
                        } else {
                            "Add to page"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationPageRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SharedItemRow(
    item: IncomingShareItem,
    isBusy: Boolean,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SharedItemPreview(item)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = item.primaryLabel(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.statusLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == IncomingShareItemStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else if (item.status == IncomingShareItemStatus.FAILED) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Retry")
            }
        }
        IconButton(onClick = onRemove, enabled = !isBusy) {
            Icon(Icons.Outlined.Close, contentDescription = "Remove")
        }
    }
}

@Composable
private fun SharedItemPreview(item: IncomingShareItem) {
    val isImage = item.assetKind == ContentAssetKind.IMAGE && !item.stagedPath.isNullOrBlank()
    if (isImage) {
        val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.stagedPath) {
            value = decodePreview(item.stagedPath.orEmpty())
        }
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Crop,
            )
            return
        }
    }
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when {
                item.status == IncomingShareItemStatus.FAILED -> Icons.Outlined.BrokenImage
                item.kind == IncomingShareItemKind.URL -> Icons.Outlined.Link
                item.assetKind == ContentAssetKind.IMAGE -> Icons.Outlined.Image
                item.assetKind == ContentAssetKind.PDF -> Icons.Outlined.Description
                item.kind == IncomingShareItemKind.TEXT || item.kind == IncomingShareItemKind.HTML ->
                    Icons.AutoMirrored.Outlined.Article
                else -> Icons.AutoMirrored.Outlined.InsertDriveFile
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineImportError(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
        }
    }
}

private suspend fun decodePreview(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / sample > PreviewDecodeSize || bounds.outHeight / sample > PreviewDecodeSize) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) },
        )?.asImageBitmap()
    }.getOrNull()
}

private fun IncomingShareItem.primaryLabel(): String = when (kind) {
    IncomingShareItemKind.TEXT,
    IncomingShareItemKind.HTML -> text.orEmpty().lineSequence().firstOrNull(String::isNotBlank)?.trim()
        ?: "Shared text"
    IncomingShareItemKind.URL -> text.orEmpty().ifBlank { "Shared link" }
    IncomingShareItemKind.STREAM -> displayName.ifBlank { "Shared file" }
}

private fun IncomingShareItem.statusLabel(): String = when (status) {
    IncomingShareItemStatus.RECEIVED -> "Waiting to prepare"
    IncomingShareItemStatus.STAGING -> "Preparing"
    IncomingShareItemStatus.STAGED -> when {
        sizeBytes > 0L && kind == IncomingShareItemKind.STREAM -> "Ready - ${sizeBytes.toReadableBytes()}"
        else -> "Ready"
    }
    IncomingShareItemStatus.FAILED -> errorCode?.replace('_', ' ')?.replaceFirstChar(Char::uppercase)
        ?: "Could not prepare this item"
    IncomingShareItemStatus.REMOVED -> "Removed"
}

private fun Long.toReadableBytes(): String = when {
    this >= 1024L * 1024L -> "%.1f MB".format(this / (1024f * 1024f))
    this >= 1024L -> "%.1f KB".format(this / 1024f)
    else -> "$this B"
}

private const val PreviewDecodeSize = 256

private val FinishedShareStatuses = setOf(
    com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus.CANCELLED,
    com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus.COMPLETED,
    com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus.UPLOAD_QUEUED,
)
