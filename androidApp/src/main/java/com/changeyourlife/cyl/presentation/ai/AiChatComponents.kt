package com.changeyourlife.cyl.presentation.ai
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteItem
import com.changeyourlife.cyl.presentation.utility.decodeBase64ImageDataUrlToImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val AiAttachmentThumbnailMaxDimensionPx = 192
private const val AiAttachmentPreviewMaxDimensionPx = 2048

@Immutable
internal data class AiMentionChipUi(
    val pageId: String,
    val title: String,
    val canRemove: Boolean,
)

@Immutable
internal data class AiAttachmentPreviewUi(
    val label: String,
    val mimeType: String,
    val dataUrl: String,
    val kind: String,
    val statusLabel: String,
)

@Composable
internal fun AiKeyboardReplacementPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(302.dp)
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
internal fun AiMentionPickerPanel(
    items: List<RichTextCommandPaletteItem>,
    onSelect: (RichTextCommandPaletteItem) -> Unit,
    selectedItemId: String? = null,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Text(
            text = "No pages found",
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 230.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            items = items,
            key = { item -> item.id },
        ) { item ->
            val selected = item.id == selectedItemId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(item) }
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.surfaceContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.leading,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(top = 3.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiSheetHeader(
    displayName: String,
    avatarSpec: AiAvatarSpec,
    isGenerating: Boolean,
    onOpenHistory: () -> Unit,
    onOpenProfile: () -> Unit,
    onCreateChatSession: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiRoundIconButton(
            icon = Icons.Rounded.AccessTime,
            contentDescription = "Chat history",
            onClick = onOpenHistory,
            enabled = !isGenerating,
        )
        Box(
            modifier = Modifier
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .widthIn(max = 220.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenProfile)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiAvatar(
                    spec = avatarSpec,
                    size = 34,
                    iconSize = 18,
                )
                Text(
                    text = displayName.ifBlank { "CYL AI" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AiRoundIconButton(
            icon = Icons.Rounded.Edit,
            contentDescription = "New chat",
            onClick = onCreateChatSession,
            enabled = !isGenerating,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AiComposerCard(
    inputState: TextFieldState,
    inputText: String,
    focusRequester: FocusRequester,
    mentionChips: List<AiMentionChipUi>,
    onRemoveMention: (String) -> Unit,
    stagedAttachments: List<AiAttachmentPreviewUi>,
    onRemoveAttachment: (Int) -> Unit,
    isInputEnabled: Boolean,
    isAttachmentsActive: Boolean,
    isSettingsActive: Boolean,
    onInputFocus: () -> Unit,
    onOpenAttachments: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    pastedImageReceiver: ReceiveContentListener,
    modifier: Modifier = Modifier,
) {
    val canSend = (inputText.isNotBlank() || stagedAttachments.isNotEmpty()) && !isGenerating
    val mentionRailScroll = rememberScrollState()
    val attachmentRailScroll = rememberScrollState()
    val composerShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(composerShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                shape = composerShape,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (mentionChips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(mentionRailScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                mentionChips.forEach { chip ->
                    AiMentionChip(
                        chip = chip,
                        onRemove = { onRemoveMention(chip.pageId) },
                    )
                }
            }
        }
        if (stagedAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(attachmentRailScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stagedAttachments.forEachIndexed { index, attachment ->
                    AiAttachmentChip(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(index) },
                    )
                }
            }
        }

        BasicTextField(
            state = inputState,
            enabled = isInputEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp, max = 112.dp)
                .focusRequester(focusRequester)
                .contentReceiver(pastedImageReceiver)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onInputFocus()
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            onKeyboardAction = { onSend() },
            decorator = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (inputText.isBlank()) {
                        Text(
                            text = "Ask, search, or make anything",
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiComposerIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Add attachment or context",
                active = isAttachmentsActive,
                onClick = onOpenAttachments,
            )
            AiComposerIconButton(
                icon = Icons.Rounded.Tune,
                contentDescription = "AI settings",
                active = isSettingsActive,
                onClick = onOpenSettings,
            )
            Spacer(modifier = Modifier.weight(1f))
            AiSendButton(
                enabled = canSend,
                isGenerating = isGenerating,
                onClick = onSend,
            )
        }
    }
}

@Composable
internal fun AiChatMessageAttachments(
    attachments: List<AiChatAttachment>,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val scrollState = rememberScrollState()
    var previewAttachment by remember { mutableStateOf<AiChatAttachment?>(null) }
    Row(
        modifier = modifier
            .widthIn(max = 300.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            key(attachment.id) {
                AiChatMessageAttachmentItem(
                    attachment = attachment,
                    onPreview = { previewAttachment = attachment },
                )
            }
        }
    }
    previewAttachment?.let { attachment ->
        AiImagePreviewDialog(
            attachment = attachment,
            onDismiss = { previewAttachment = null },
        )
    }
}

@Composable
private fun AiChatMessageAttachmentItem(
    attachment: AiChatAttachment,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isImage = attachment.kind == "image" ||
        attachment.mimeType.startsWith("image/", ignoreCase = true)
    val thumbnailDataUrl = attachment.previewDataUrl.ifBlank { attachment.dataUrl }
    if (isImage && thumbnailDataUrl.isNotBlank()) {
        var thumbnail by remember(thumbnailDataUrl) {
            mutableStateOf<ImageBitmap?>(null)
        }
        LaunchedEffect(thumbnailDataUrl) {
            thumbnail = withContext(Dispatchers.Default) {
                decodeBase64ImageDataUrlToImageBitmap(
                    dataUrl = thumbnailDataUrl,
                    maxDimensionPx = AiAttachmentThumbnailMaxDimensionPx * 2,
                )
            }
        }
        Column(
            modifier = modifier.width(140.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 140.dp, height = 104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onPreview),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = thumbnail
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = attachment.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = attachment.name.ifBlank { "Image" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Row(
            modifier = modifier
                .height(48.dp)
                .widthIn(max = 220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name.ifBlank { "Attached file" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = attachment.mimeType.ifBlank { attachment.kind },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AiImagePreviewDialog(
    attachment: AiChatAttachment,
    onDismiss: () -> Unit,
) {
    val previewDataUrl = attachment.dataUrl.ifBlank { attachment.previewDataUrl }
    var preview by remember(previewDataUrl) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var hasFinishedLoading by remember(previewDataUrl) {
        mutableStateOf(false)
    }
    LaunchedEffect(previewDataUrl) {
        hasFinishedLoading = false
        preview = withContext(Dispatchers.Default) {
            decodeBase64ImageDataUrlToImageBitmap(
                dataUrl = previewDataUrl,
                maxDimensionPx = AiAttachmentPreviewMaxDimensionPx,
            )
        }
        hasFinishedLoading = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20B0B0B))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            when {
                preview != null -> {
                    Image(
                        bitmap = preview!!,
                        contentDescription = attachment.name.ifBlank { "Image preview" },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 72.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                !hasFinishedLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
                else -> {
                    Text(
                        text = "Preview unavailable",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close image preview",
                    tint = Color.White,
                )
            }

            Text(
                text = attachment.name.ifBlank { "Image" },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AiMentionChip(
    chip: AiMentionChipUi,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(start = 9.dp, end = if (chip.canRemove) 0.dp else 9.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = chip.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (chip.canRemove) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove context",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun AiAttachmentChip(
    attachment: AiAttachmentPreviewUi,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var thumbnail by remember(attachment.dataUrl) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(attachment.dataUrl) {
        thumbnail = null
        thumbnail = withContext(Dispatchers.Default) {
            decodeBase64ImageDataUrlToImageBitmap(
                dataUrl = attachment.dataUrl,
                maxDimensionPx = AiAttachmentThumbnailMaxDimensionPx,
            )
        }
    }
    Row(
        modifier = modifier
            .height(44.dp)
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(start = 6.dp, end = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f),
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val thumbnailBitmap = thumbnail
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = if (attachment.kind == "text") Icons.Rounded.AttachFile else Icons.Rounded.Photo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Column(
            modifier = Modifier.widthIn(max = 120.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = attachment.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = attachment.statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove attachment",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
internal fun AiAttachmentPanel(
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onMentionContext: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AiPanelAction(
            icon = Icons.Rounded.Photo,
            label = "Photo",
            onClick = onPickPhoto,
            modifier = Modifier.fillMaxWidth(),
        )
        AiPanelAction(
            icon = Icons.Rounded.AttachFile,
            label = "File",
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth(),
        )
        AiPanelAction(
            icon = Icons.Rounded.AlternateEmail,
            label = "Context",
            onClick = onMentionContext,
            modifier = Modifier.fillMaxWidth(),
        )
        AiPanelAction(
            icon = Icons.Rounded.CameraAlt,
            label = "Camera",
            onClick = onOpenCamera,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun AiSettingsPanel(
    modelLabel: String,
    visionStatusLabel: String,
    visionPipelineLabel: String,
    enabledSkillsCount: Int,
    totalSkillsCount: Int,
    webSearchEnabled: Boolean,
    hasMessages: Boolean,
    onToggleWebSearch: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenPersonalize: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            AiSettingRow(
                icon = Icons.Rounded.AutoAwesome,
                label = "Model",
                value = modelLabel.compactAiModelLabel(),
            )
        }
        if (visionStatusLabel.isNotBlank()) {
            item {
                AiSettingRow(
                    icon = Icons.Rounded.Photo,
                    label = "Vision",
                    value = visionStatusLabel.compactAiModelLabel(),
                )
            }
        }
        if (visionPipelineLabel.isNotBlank()) {
            item {
                AiSettingRow(
                    icon = Icons.Rounded.Tune,
                    label = "Image pipeline",
                    value = visionPipelineLabel,
                )
            }
        }
        item {
            AiSettingRow(
                icon = Icons.Rounded.Public,
                label = "Source",
                value = if (webSearchEnabled) "App + web" else "App context",
                active = webSearchEnabled,
                onClick = onToggleWebSearch,
            )
        }
        item {
            AiSettingRow(
                icon = Icons.Rounded.Extension,
                label = "Skills",
                value = when {
                    totalSkillsCount == 0 -> "Set up"
                    enabledSkillsCount == totalSkillsCount -> "$enabledSkillsCount active"
                    else -> "$enabledSkillsCount/$totalSkillsCount active"
                },
                onClick = onOpenSkills,
            )
        }
        item {
            AiSettingRow(
                icon = Icons.Rounded.Person,
                label = "Personalize",
                value = "Profile page",
                onClick = onOpenPersonalize,
            )
        }
        if (hasMessages) {
            item {
                TextButton(
                    onClick = onClearHistory,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = "Clear chat")
                }
            }
        }
    }
}

@Composable
private fun AiPanelAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiSettingRow(
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                } else {
                    Color.Transparent
                },
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(22.dp),
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AiComposerIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    if (active) {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
                    } else {
                        Color.Transparent
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (active) {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(15.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun AiSendButton(
    enabled: Boolean,
    isGenerating: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isGenerating) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Send",
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun EmptyAiMessageHint(attachedPageTitle: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = if (attachedPageTitle == null) {
                    "Ask me anything about your goals,\ntasks, or productivity."
                } else {
                    "Ask about @${attachedPageTitle.ifBlank { "Untitled page" }}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun AiChatPageLinks(
    links: List<AiChatPageLink>,
    onOpenPage: (String, String, String) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        links.forEach { link ->
            TextButton(
                onClick = { onOpenPage(link.pageId, link.targetType, link.targetId) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(text = "Open ${link.title}")
            }
        }
    }
}

private fun String.compactAiModelLabel(): String {
    val clean = trim().ifBlank { return "AI model" }
    val model = clean.substringAfterLast('/')
        .replace(":free", " free", ignoreCase = true)
        .trim()
    return model.ifBlank { clean }
}
