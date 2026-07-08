package com.changeyourlife.cyl.presentation.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.AiActionUndoState
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.presentation.page.EditorSuggestionController
import com.changeyourlife.cyl.presentation.page.RichTextCommandPalette
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteKind
import com.changeyourlife.cyl.presentation.page.paletteItemId
import com.changeyourlife.cyl.presentation.page.richTextMentionPaletteItems

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AiChatSheet(
    messages: List<AiChatMessage>,
    mentionPages: List<Page>,
    isGenerating: Boolean,
    errorMessage: String?,
    modelLabel: String = "AI model",
    onSendMessage: (String, List<String>) -> Unit,
    onUndoAction: (String, String) -> Unit,
    onClearHistory: () -> Unit,
    onCreateChatSession: () -> Unit,
    onDismissError: () -> Unit,
    onOpenPage: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    attachedPageId: String? = null,
    attachedPageTitle: String? = null,
) {
    val attachedMentionPageIds = rememberSaveable(attachedPageId) {
        attachedPageId
            ?.takeIf { it.isNotBlank() }
            ?.let(::listOf)
            .orEmpty()
    }
    var inputText by rememberSaveable(attachedPageId) {
        mutableStateOf("")
    }
    var selectedMentionPageIds by rememberSaveable(attachedPageId) {
        mutableStateOf(emptyList<String>())
    }
    var stagedAttachmentLabels by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            stagedAttachmentLabels = (stagedAttachmentLabels + "Photo").takeLast(4)
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            stagedAttachmentLabels = (stagedAttachmentLabels + "File").takeLast(4)
        }
    }
    val cameraPreview = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            stagedAttachmentLabels = (stagedAttachmentLabels + "Camera").takeLast(4)
        }
    }
    val mentionSuggestionState = EditorSuggestionController.resolve(
        text = inputText,
        cursor = inputText.length,
        mentionPages = mentionPages,
        enabledKinds = setOf(RichTextCommandPaletteKind.Mention),
    )
    var isAttachmentPanelOpen by rememberSaveable { mutableStateOf(false) }
    var isSettingsPanelOpen by rememberSaveable { mutableStateOf(false) }
    var isMentionPickerOpen by rememberSaveable { mutableStateOf(false) }
    val mentionChips = remember(
        attachedPageId,
        attachedPageTitle,
        selectedMentionPageIds,
        mentionPages,
    ) {
        buildList {
            if (!attachedPageId.isNullOrBlank()) {
                add(
                    AiMentionChipUi(
                        pageId = attachedPageId,
                        title = attachedPageTitle?.ifBlank { "Untitled page" } ?: "Current page",
                        canRemove = false,
                    ),
                )
            }
            selectedMentionPageIds
                .filterNot { id -> id == attachedPageId }
                .distinct()
                .mapNotNull { id -> mentionPages.firstOrNull { page -> page.id == id } }
                .forEach { page ->
                    add(
                        AiMentionChipUi(
                            pageId = page.id,
                            title = page.title.ifBlank { "Untitled page" },
                            canRemove = true,
                        ),
                    )
                }
        }
    }
    val sendCurrentPrompt = {
        if (inputText.isNotBlank() && !isGenerating) {
            val message = inputText.trim()
            onSendMessage(
                message,
                message.resolveMentionedPageIds(
                    pages = mentionPages,
                    knownPageIds = selectedMentionPageIds + attachedMentionPageIds,
                ),
            )
            inputText = ""
            selectedMentionPageIds = emptyList()
            stagedAttachmentLabels = emptyList()
            isAttachmentPanelOpen = false
            isSettingsPanelOpen = false
            isMentionPickerOpen = false
        }
    }
    val onSelectMentionPage: (Page) -> Unit = { page ->
        inputText = inputText.removeActiveMentionQuery()
        selectedMentionPageIds = (selectedMentionPageIds + page.id)
            .filter { id -> id.isNotBlank() }
            .distinct()
        isMentionPickerOpen = false
        isAttachmentPanelOpen = false
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(bottom = 12.dp),
        ) {
            AiSheetHeader(
                hasMessages = messages.isNotEmpty(),
                onOpenHistory = {
                    isSettingsPanelOpen = true
                    isAttachmentPanelOpen = false
                    isMentionPickerOpen = false
                },
                onCreateChatSession = onCreateChatSession,
            )

            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable(onClick = onDismissError),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    item {
                        EmptyAiMessageHint(attachedPageTitle = attachedPageTitle)
                    }
                }

                items(
                    items = messages,
                    key = { message -> message.id.ifBlank { "${message.role}:${message.content.hashCode()}" } },
                ) { message ->
                    val isUser = message.role == "user"
                    val messageText = message.content.ifBlank { "No response content." }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 300.dp),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = messageText,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText("CYL AI message", messageText),
                                            )
                                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                    .background(
                                        if (isUser) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (!isUser && message.pageLinks.isNotEmpty()) {
                                AiChatPageLinks(
                                    links = message.pageLinks,
                                    onOpenPage = onOpenPage,
                                )
                            }
                            if (!isUser && message.actionMetadata?.hasDetails == true) {
                                AiChatActionDetails(
                                    metadata = message.actionMetadata,
                                    pageId = message.pageLinks.firstOrNull()?.pageId.orEmpty(),
                                    onUndoAction = onUndoAction,
                                )
                            }
                        }
                    }
                }

                if (isGenerating) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            val manualMentionItems = if (isMentionPickerOpen) {
                richTextMentionPaletteItems(mentionPages)
            } else {
                emptyList()
            }
            if (isMentionPickerOpen || mentionSuggestionState != null) {
                RichTextCommandPalette(
                    items = if (isMentionPickerOpen) manualMentionItems else mentionSuggestionState?.items.orEmpty(),
                    onSelect = { item ->
                        val page = mentionPages.firstOrNull { candidate ->
                            candidate.paletteItemId() == item.id
                        }
                        if (page != null) {
                            onSelectMentionPage(page)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    selectedItemId = mentionSuggestionState?.selectedItem?.id,
                )
            }

            if (isAttachmentPanelOpen) {
                AiAttachmentPanel(
                    onPickPhoto = { photoPicker.launch("image/*") },
                    onPickFile = { filePicker.launch("*/*") },
                    onMentionContext = {
                        isMentionPickerOpen = true
                        isAttachmentPanelOpen = false
                        isSettingsPanelOpen = false
                    },
                    onOpenCamera = { cameraPreview.launch(null) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (isSettingsPanelOpen) {
                AiSettingsPanel(
                    modelLabel = modelLabel,
                    hasMessages = messages.isNotEmpty(),
                    onClearHistory = onClearHistory,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            AiComposerCard(
                inputText = inputText,
                onInputTextChange = { inputText = it },
                mentionChips = mentionChips,
                onRemoveMention = { pageId ->
                    selectedMentionPageIds = selectedMentionPageIds.filterNot { id -> id == pageId }
                },
                stagedAttachmentLabels = stagedAttachmentLabels,
                onRemoveAttachment = { index ->
                    stagedAttachmentLabels = stagedAttachmentLabels.filterIndexed { itemIndex, _ ->
                        itemIndex != index
                    }
                },
                onOpenAttachments = {
                    isAttachmentPanelOpen = !isAttachmentPanelOpen
                    isSettingsPanelOpen = false
                    isMentionPickerOpen = false
                },
                onOpenSettings = {
                    isSettingsPanelOpen = !isSettingsPanelOpen
                    isAttachmentPanelOpen = false
                    isMentionPickerOpen = false
                },
                onSend = sendCurrentPrompt,
                isGenerating = isGenerating,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private data class AiMentionChipUi(
    val pageId: String,
    val title: String,
    val canRemove: Boolean,
)

@Composable
private fun AiSheetHeader(
    hasMessages: Boolean,
    onOpenHistory: () -> Unit,
    onCreateChatSession: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AiRoundIconButton(
            icon = Icons.Rounded.AccessTime,
            contentDescription = "Chat options",
            enabled = hasMessages,
            onClick = onOpenHistory,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(31.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = "CYL AI",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        AiRoundIconButton(
            icon = Icons.Rounded.Edit,
            contentDescription = "New chat",
            onClick = onCreateChatSession,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun AiComposerCard(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    mentionChips: List<AiMentionChipUi>,
    onRemoveMention: (String) -> Unit,
    stagedAttachmentLabels: List<String>,
    onRemoveAttachment: (Int) -> Unit,
    onOpenAttachments: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
) {
    val canSend = inputText.isNotBlank() && !isGenerating
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (mentionChips.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    mentionChips.take(2).forEach { chip ->
                        AiMentionChip(
                            chip = chip,
                            onRemove = { onRemoveMention(chip.pageId) },
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (mentionChips.size > 2) {
                        Text(
                            text = "+${mentionChips.size - 2}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (stagedAttachmentLabels.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    stagedAttachmentLabels.take(3).forEachIndexed { index, label ->
                        AiAttachmentChip(
                            label = label,
                            onRemove = { onRemoveAttachment(index) },
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (stagedAttachmentLabels.size > 3) {
                        Text(
                            text = "+${stagedAttachmentLabels.size - 3}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            BasicTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp, max = 118.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (inputText.isBlank()) {
                            Text(
                                text = "Ask, search, or make anything",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiComposerIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add attachment or context",
                    onClick = onOpenAttachments,
                )
                AiComposerIconButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = "AI settings",
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
}

@Composable
private fun AiMentionChip(
    chip: AiMentionChipUi,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(17.dp),
            )
            .padding(start = 9.dp, end = if (chip.canRemove) 2.dp else 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
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
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AiAttachmentChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                shape = RoundedCornerShape(17.dp),
            )
            .padding(start = 9.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (label == "File") Icons.Rounded.AttachFile else Icons.Rounded.Photo,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove attachment",
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AiAttachmentPanel(
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onMentionContext: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AiPanelSurface(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiPanelAction(
                icon = Icons.Rounded.Photo,
                label = "Photo",
                onClick = onPickPhoto,
                modifier = Modifier.weight(1f),
            )
            AiPanelAction(
                icon = Icons.Rounded.AttachFile,
                label = "File",
                onClick = onPickFile,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiPanelAction(
                icon = Icons.Rounded.AlternateEmail,
                label = "Context",
                onClick = onMentionContext,
                modifier = Modifier.weight(1f),
            )
            AiPanelAction(
                icon = Icons.Rounded.CameraAlt,
                label = "Camera",
                onClick = onOpenCamera,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AiSettingsPanel(
    modelLabel: String,
    hasMessages: Boolean,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AiPanelSurface(modifier = modifier) {
        AiSettingRow(
            icon = Icons.Rounded.AutoAwesome,
            label = "Model",
            value = modelLabel.compactAiModelLabel(),
        )
        AiSettingRow(
            icon = Icons.Rounded.Public,
            label = "Source",
            value = "App context",
        )
        AiSettingRow(
            icon = Icons.Rounded.Public,
            label = "Web search",
            value = "Off",
        )
        AiSettingRow(
            icon = Icons.Rounded.Extension,
            label = "Skills",
            value = "Default",
        )
        AiSettingRow(
            icon = Icons.Rounded.Person,
            label = "Personalize",
            value = "Default",
        )
        if (hasMessages) {
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

@Composable
private fun AiPanelSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
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
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
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
            .size(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp),
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
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AiComposerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
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
private fun AiChatActionDetails(
    metadata: AiChatActionMetadata,
    pageId: String,
    onUndoAction: (String, String) -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val validationCount = metadata.validationIssues.size
    val executedCount = metadata.executedActions.size
    val proposedCount = metadata.proposedActions.size
    val summary = when {
        validationCount > 0 -> "$validationCount issue${if (validationCount == 1) "" else "s"}"
        executedCount > 0 -> "$executedCount action${if (executedCount == 1) "" else "s"} applied"
        proposedCount > 0 -> "$proposedCount action${if (proposedCount == 1) "" else "s"} proposed"
        else -> "Action details"
    }
    val summaryColor = if (validationCount > 0) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val canUndo = metadata.auditId.isNotBlank() &&
        pageId.isNotBlank() &&
        metadata.executedActions.isNotEmpty() &&
        metadata.undoState == AiActionUndoState.Available

    Column(
        modifier = Modifier.widthIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(
            onClick = { isExpanded = !isExpanded },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = summaryColor,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Hide action details" else "Show action details",
                tint = summaryColor,
                modifier = Modifier.size(16.dp),
            )
        }
        if (isExpanded) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (metadata.schemaName.isNotBlank()) {
                        Text(
                            text = "${metadata.schemaName} v${metadata.schemaVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AiActionMetadataSection(
                        title = "Proposed",
                        items = metadata.proposedActions,
                    )
                    AiActionMetadataSection(
                        title = "Applied",
                        items = metadata.executedActions,
                    )
                    AiValidationIssueSection(issues = metadata.validationIssues)
                    if (metadata.executionMessages.isNotEmpty()) {
                        AiActionTextSection(
                            title = "Result",
                            lines = metadata.executionMessages,
                        )
                    }
                    if (canUndo) {
                        TextButton(
                            onClick = { onUndoAction(metadata.auditId, pageId) },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(text = "Undo AI action")
                        }
                    } else if (metadata.undoState == AiActionUndoState.Applied) {
                        Text(
                            text = "Undone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiActionMetadataSection(
    title: String,
    items: List<AiChatActionMetadataItem>,
) {
    if (items.isEmpty()) return
    AiActionTextSection(
        title = title,
        lines = items.map { item ->
            listOf(item.type.prettyActionType(), item.target)
                .filter { value -> value.isNotBlank() }
                .joinToString(": ")
        },
    )
}

@Composable
private fun AiValidationIssueSection(issues: List<AiChatActionValidationIssue>) {
    if (issues.isEmpty()) return
    AiActionTextSection(
        title = "Rejected",
        lines = issues.map { issue ->
            listOf(issue.field, issue.message.ifBlank { issue.code })
                .filter { value -> value.isNotBlank() }
                .joinToString(": ")
        },
        isWarning = true,
    )
}

@Composable
private fun AiActionTextSection(
    title: String,
    lines: List<String>,
    isWarning: Boolean = false,
) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        lines
            .filter { line -> line.isNotBlank() }
            .take(4)
            .forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isWarning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}

@Composable
private fun EmptyAiMessageHint(attachedPageTitle: String?) {
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
private fun AiChatPageLinks(
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

private fun String.removeActiveMentionQuery(): String {
    val atIndex = lastIndexOf('@')
    if (atIndex < 0) return this
    val before = substring(0, atIndex).trimEnd()
    return if (before.isBlank()) "" else "$before "
}

private fun String.resolveMentionedPageIds(
    pages: List<Page>,
    knownPageIds: List<String>,
): List<String> {
    val ids = LinkedHashSet<String>()
    knownPageIds
        .filter { id -> id.isNotBlank() }
        .forEach(ids::add)

    val normalizedMessage = lowercase()
    pages
        .filter { page -> page.id.isNotBlank() && page.title.isNotBlank() }
        .sortedByDescending { page -> page.title.length }
        .forEach { page ->
            val title = page.title.lowercase()
            if (normalizedMessage.contains("@$title")) {
                ids += page.id
            }
        }

    Regex("@([^\\n,.;:]+)")
        .findAll(this)
        .map { match -> match.groupValues.getOrNull(1).orEmpty().trim().lowercase() }
        .filter { mention -> mention.isNotBlank() }
        .forEach { mention ->
            pages
                .filter { page -> page.id.isNotBlank() && page.title.isNotBlank() }
                .sortedByDescending { page -> page.title.length }
                .firstOrNull { page ->
                    val title = page.title.lowercase()
                    title == mention ||
                        title.startsWith(mention) ||
                        mention.startsWith(title)
                }
                ?.let { page -> ids += page.id }
        }

    return ids.toList()
}

private fun String.compactAiModelLabel(): String {
    val clean = trim().ifBlank { return "AI model" }
    val model = clean.substringAfterLast('/')
        .replace(":free", " free", ignoreCase = true)
        .trim()
    return model.ifBlank { clean }
}

private fun String.prettyActionType(): String {
    return lowercase()
        .split('_')
        .filter { part -> part.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { char -> char.titlecase() } }
}
