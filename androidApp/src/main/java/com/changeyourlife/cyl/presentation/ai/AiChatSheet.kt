package com.changeyourlife.cyl.presentation.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import com.changeyourlife.cyl.domain.model.MentionCandidate
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteKind
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteItem
import com.changeyourlife.cyl.presentation.page.RichTextMentionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AiChatSheet(
    messages: List<AiChatMessage>,
    mentionCandidates: List<MentionCandidate>,
    persona: AiPersonaUiState,
    isGenerating: Boolean,
    errorMessage: String?,
    modelLabel: String = "AI model",
    visionStatusLabel: String = "",
    visionPipelineLabel: String = "",
    enabledSkillsCount: Int = 0,
    totalSkillsCount: Int = 0,
    onSendMessage: (String, List<String>, List<AiImageAttachment>, Boolean) -> Unit,
    onMentionQueryChange: (String) -> Unit = {},
    onUndoAction: (String, String) -> Unit,
    onClearHistory: () -> Unit,
    onCreateChatSession: () -> Unit,
    onOpenHistoryPage: () -> Unit,
    onOpenProfilePage: () -> Unit,
    onOpenSkillsPage: () -> Unit,
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
    val inputState = rememberTextFieldState()
    val inputText = inputState.text.toString()
    var selectedMentionPageIds by rememberSaveable(attachedPageId) {
        mutableStateOf(emptyList<String>())
    }
    var selectedMentionTitles by remember(attachedPageId) {
        mutableStateOf(emptyMap<String, String>())
    }
    var stagedImageAttachments by remember {
        mutableStateOf(emptyList<AiImageAttachment>())
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    val composerScope = rememberCoroutineScope()
    val stableSheetMaxHeight = remember(configuration.screenHeightDp) {
        configuration.screenHeightDp.dp * 0.90f
    }
    fun stageAttachmentResult(
        result: AiAttachmentReadResult,
        successMessage: String? = null,
    ) {
        result.attachment?.let { attachment ->
            if (stagedImageAttachments.size >= MaxAiChatImages) {
                Toast.makeText(
                    context,
                    "You can attach up to $MaxAiChatImages files.",
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            stagedImageAttachments = stagedImageAttachments + attachment
            if (!successMessage.isNullOrBlank()) {
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!result.userMessage.isNullOrBlank()) {
            Toast.makeText(context, result.userMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            composerScope.launch {
                val result = withContext(Dispatchers.IO) {
                    context.readAiImageAttachment(uri, fallbackName = "Photo")
                }
                stageAttachmentResult(result)
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            composerScope.launch {
                val result = withContext(Dispatchers.IO) {
                    context.readAiImageAttachment(uri, fallbackName = "Image file")
                }
                stageAttachmentResult(result)
            }
        }
    }
    val cameraPreview = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            composerScope.launch {
                val result = withContext(Dispatchers.Default) {
                    bitmap.toAiImageAttachmentResult(name = "Camera")
                }
                stageAttachmentResult(result)
            }
        }
    }
    val pastedImageReceiver = ReceiveContentListener { transferableContent ->
        if (!transferableContent.hasMediaType(MediaType.Image)) {
            return@ReceiveContentListener transferableContent
        }

        var usedPlatformUri = false
        val queuedUris = mutableListOf<Uri>()
        val platformUri = transferableContent.platformTransferableContent?.linkUri
        val remainingContent = transferableContent.consume { item ->
            val uri = item.uri ?: platformUri?.takeIf { !usedPlatformUri }
            if (uri != null) {
                if (item.uri == null) usedPlatformUri = true
                queuedUris += uri
                true
            } else {
                false
            }
        }
        if (queuedUris.isNotEmpty()) {
            composerScope.launch {
                val availableSlots = (MaxAiChatImages - stagedImageAttachments.size).coerceAtLeast(0)
                if (availableSlots == 0) {
                    Toast.makeText(
                        context,
                        "You can attach up to $MaxAiChatImages files.",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                val acceptedUris = queuedUris.take(availableSlots)
                val results = withContext(Dispatchers.IO) {
                    acceptedUris.map { imageUri ->
                        context.readPastedImageAttachment(imageUri, fallbackName = "Pasted image")
                    }
                }
                val acceptedAttachments = results
                    .mapNotNull(AiAttachmentReadResult::attachment)
                stagedImageAttachments = stagedImageAttachments + acceptedAttachments
                val attachedCount = acceptedAttachments.size
                if (attachedCount > 0) {
                    Toast.makeText(
                        context,
                        if (attachedCount == 1) "Image pasted" else "$attachedCount images pasted",
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (queuedUris.size > acceptedUris.size) {
                        Toast.makeText(
                            context,
                            "Only $MaxAiChatImages attachments can be added at once.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    val message = results.lastOrNull()?.userMessage?.takeIf { it.isNotBlank() }
                    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                }
            }
        }
        remainingContent
    }
    val activeMentionQuery = RichTextMentionParser.activeQuery(
        text = inputText,
        cursor = inputState.selection.end,
    )
    var activePanel by rememberSaveable { mutableStateOf<AiComposerPanel?>(null) }
    var isMentionPickerOpen by rememberSaveable { mutableStateOf(false) }
    var isWebSearchEnabled by rememberSaveable { mutableStateOf(false) }
    val shouldShowKeyboardReplacement = activePanel != null || isMentionPickerOpen
    val avatarSpec = remember(persona) { persona.toAvatarSpec() }
    val stagedAttachmentPreviews = remember(stagedImageAttachments) {
        stagedImageAttachments.mapIndexed { index, attachment ->
            AiAttachmentPreviewUi(
                label = attachment.name.ifBlank {
                    if (attachment.kind == "text") "File ${index + 1}" else "Image ${index + 1}"
                },
                mimeType = attachment.mimeType,
                dataUrl = attachment.previewDataUrl.ifBlank { attachment.dataUrl },
                kind = attachment.kind,
                statusLabel = when {
                    attachment.kind == "text" -> "Text ready"
                    attachment.mimeType.startsWith("image/", ignoreCase = true) -> "Vision ready"
                    else -> "Attached"
                },
            )
        }
    }
    val mentionChips = remember(
        attachedPageId,
        attachedPageTitle,
        selectedMentionPageIds,
        selectedMentionTitles,
        mentionCandidates,
    ) {
        val candidateById = mentionCandidates.associateBy { candidate -> candidate.pageId }
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
                .forEach { pageId ->
                    val candidate = candidateById[pageId]
                    val title = candidate?.title
                        ?: selectedMentionTitles[pageId]
                        ?: "Untitled page"
                    add(
                        AiMentionChipUi(
                            pageId = pageId,
                            title = title.ifBlank { "Untitled page" },
                            canRemove = true,
                        ),
                    )
                }
        }
    }
    val sendCurrentPrompt = {
        if ((inputText.isNotBlank() || stagedImageAttachments.isNotEmpty()) && !isGenerating) {
            val message = inputText.trim()
            onSendMessage(
                message,
                (selectedMentionPageIds + attachedMentionPageIds)
                    .filter { pageId -> pageId.isNotBlank() }
                    .distinct(),
                stagedImageAttachments,
                isWebSearchEnabled,
            )
            inputState.setTextAndPlaceCursorAtEnd("")
            selectedMentionPageIds = emptyList()
            selectedMentionTitles = emptyMap()
            stagedImageAttachments = emptyList()
            activePanel = null
            isMentionPickerOpen = false
        }
    }
    val onSelectMentionPage: (MentionCandidate) -> Unit = { page ->
        inputState.setTextAndPlaceCursorAtEnd(
            inputText.removeActiveMentionQuery(cursor = inputState.selection.end),
        )
        selectedMentionPageIds = (selectedMentionPageIds + page.pageId)
            .filter { id -> id.isNotBlank() }
            .distinct()
        selectedMentionTitles = selectedMentionTitles + (page.pageId to page.title.ifBlank { "Untitled page" })
        isMentionPickerOpen = false
        activePanel = null
        composerScope.launch {
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(attachedPageId) {
        inputState.setTextAndPlaceCursorAtEnd("")
    }
    LaunchedEffect(activeMentionQuery?.query, isMentionPickerOpen) {
        onMentionQueryChange(
            when {
                isMentionPickerOpen -> ""
                activeMentionQuery != null -> activeMentionQuery.query
                else -> ""
            },
        )
    }
    fun openComposerPanel(panel: AiComposerPanel) {
        val nextPanel = if (activePanel == panel) null else panel
        isMentionPickerOpen = false
        activePanel = nextPanel
        if (nextPanel != null) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        } else {
            composerScope.launch {
                inputFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = stableSheetMaxHeight)
                .padding(bottom = 12.dp),
        ) {
            AiSheetHeader(
                displayName = persona.displayName,
                avatarSpec = avatarSpec,
                isGenerating = isGenerating,
                onOpenHistory = {
                    onOpenHistoryPage()
                },
                onOpenProfile = {
                    onOpenProfilePage()
                },
                onCreateChatSession = {
                    activePanel = null
                    isMentionPickerOpen = false
                    onCreateChatSession()
                },
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

            AiChatMessageList(
                messages = messages,
                isGenerating = isGenerating,
                attachedPageTitle = attachedPageTitle,
                onOpenPage = onOpenPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            AiComposerCard(
                inputState = inputState,
                inputText = inputText,
                focusRequester = inputFocusRequester,
                mentionChips = mentionChips,
                onRemoveMention = { pageId ->
                    selectedMentionPageIds = selectedMentionPageIds.filterNot { id -> id == pageId }
                    selectedMentionTitles = selectedMentionTitles - pageId
                },
                stagedAttachments = stagedAttachmentPreviews,
                onRemoveAttachment = { index ->
                    stagedImageAttachments = stagedImageAttachments.filterIndexed { itemIndex, _ ->
                        itemIndex != index
                    }
                },
                isInputEnabled = true,
                isAttachmentsActive = activePanel == AiComposerPanel.Attachments,
                isSettingsActive = activePanel == AiComposerPanel.Settings,
                onInputFocus = {
                    activePanel = null
                    isMentionPickerOpen = false
                    keyboardController?.show()
                },
                onOpenAttachments = {
                    openComposerPanel(AiComposerPanel.Attachments)
                },
                onOpenSettings = {
                    openComposerPanel(AiComposerPanel.Settings)
                },
                onSend = sendCurrentPrompt,
                isGenerating = isGenerating,
                pastedImageReceiver = pastedImageReceiver,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val mentionItems = if (isMentionPickerOpen || activeMentionQuery != null) {
                mentionCandidates.toAiMentionPaletteItems()
            } else {
                emptyList()
            }
            AnimatedVisibility(
                visible = activePanel == null && !isMentionPickerOpen && activeMentionQuery != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                AiMentionPickerPanel(
                    items = mentionItems,
                    onSelect = { item ->
                        val page = mentionCandidates.firstOrNull { candidate ->
                            candidate.toPaletteItemId() == item.id
                        }
                        if (page != null) onSelectMentionPage(page)
                    },
                    selectedItemId = mentionItems.firstOrNull()?.id,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            AiKeyboardReplacementHost(
                requestedVisible = shouldShowKeyboardReplacement,
            ) {
                AiKeyboardReplacementPanel(
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    when {
                        isMentionPickerOpen || activeMentionQuery != null -> {
                            AiMentionPickerPanel(
                                items = mentionItems,
                                onSelect = { item ->
                                    val page = mentionCandidates.firstOrNull { candidate ->
                                        candidate.toPaletteItemId() == item.id
                                    }
                                    if (page != null) {
                                        onSelectMentionPage(page)
                                    }
                                },
                                selectedItemId = mentionItems.firstOrNull()?.id,
                            )
                        }
                        activePanel == AiComposerPanel.Attachments -> {
                            AiAttachmentPanel(
                                onPickPhoto = { photoPicker.launch("image/*") },
                                onPickFile = { filePicker.launch("*/*") },
                                onMentionContext = {
                                    activePanel = null
                                    isMentionPickerOpen = true
                                },
                                onOpenCamera = { cameraPreview.launch(null) },
                            )
                        }
                        activePanel == AiComposerPanel.Settings -> {
                            AiSettingsPanel(
                                modelLabel = modelLabel,
                                visionStatusLabel = visionStatusLabel,
                                visionPipelineLabel = visionPipelineLabel,
                                enabledSkillsCount = enabledSkillsCount,
                                totalSkillsCount = totalSkillsCount,
                                webSearchEnabled = isWebSearchEnabled,
                                hasMessages = messages.isNotEmpty(),
                                onToggleWebSearch = {
                                    isWebSearchEnabled = !isWebSearchEnabled
                                },
                                onOpenSkills = {
                                    activePanel = null
                                    onOpenSkillsPage()
                                },
                                onOpenPersonalize = {
                                    activePanel = null
                                    onOpenProfilePage()
                                },
                                onClearHistory = onClearHistory,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiKeyboardReplacementHost(
    requestedVisible: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    var hasWaitedForImeToHide by remember { mutableStateOf(false) }

    LaunchedEffect(requestedVisible) {
        hasWaitedForImeToHide = false
        if (requestedVisible) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            delay(AiKeyboardReplacementImeWaitMs)
            hasWaitedForImeToHide = true
            keyboardController?.hide()
        }
    }

    AnimatedVisibility(
        visible = requestedVisible && (!isImeVisible || hasWaitedForImeToHide),
        enter = slideInVertically(initialOffsetY = { height -> height }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { height -> height }) + fadeOut(),
        content = { content() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiChatMessageList(
    messages: List<AiChatMessage>,
    isGenerating: Boolean,
    attachedPageTitle: String?,
    onOpenPage: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var previousMessageCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(messages.size, isGenerating) {
        val layoutInfo = listState.layoutInfo
        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val wasNearBottom = lastVisibleIndex == null ||
            lastVisibleIndex >= (layoutInfo.totalItemsCount - 2).coerceAtLeast(0)
        val isInitialPosition = previousMessageCount < 0
        val latestMessageIsUser = messages.size > previousMessageCount &&
            messages.lastOrNull()?.role == "user"
        val targetIndex = when {
            isGenerating -> messages.size
            messages.isNotEmpty() -> messages.lastIndex
            else -> null
        }
        if (targetIndex != null && (isInitialPosition || wasNearBottom || latestMessageIsUser)) {
            if (isInitialPosition) {
                listState.scrollToItem(targetIndex)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
        previousMessageCount = messages.size
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        overscrollEffect = null,
    ) {
        if (messages.isEmpty() && !isGenerating) {
            item(key = "empty") {
                EmptyAiMessageHint(attachedPageTitle = attachedPageTitle)
            }
        }

        itemsIndexed(
            items = messages,
            key = { index, message ->
                message.id.ifBlank { "${message.role}:${message.content.hashCode()}:$index" }
            },
        ) { _, message ->
            val isUser = message.role == "user"
            val messageText = message.content.trim().ifBlank {
                if (message.attachments.isEmpty()) "No response content." else ""
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Column(
                    modifier = if (isUser) {
                        Modifier.widthIn(max = 300.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (message.attachments.isNotEmpty()) {
                        AiChatMessageAttachments(attachments = message.attachments)
                    }
                    if (messageText.isNotBlank()) {
                        Text(
                            text = messageText,
                            modifier = if (isUser) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 5.dp)
                            },
                            color = if (isUser) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (!isUser && message.pageLinks.isNotEmpty()) {
                        AiChatPageLinks(
                            links = message.pageLinks,
                            onOpenPage = onOpenPage,
                        )
                    }
                    if (!isUser && messageText.isNotBlank()) {
                        IconButton(
                            onClick = { context.copyAiChatMessage(messageText) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (isGenerating) {
            item(key = "generating") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
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
}

private fun Context.copyAiChatMessage(message: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("CYL AI message", message))
    Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
}

private enum class AiComposerPanel {
    Attachments,
    Settings,
}

private const val AiKeyboardReplacementImeWaitMs = 260L

private fun String.removeActiveMentionQuery(cursor: Int): String {
    val safeCursor = cursor.coerceIn(0, length)
    if (safeCursor == 0) return this
    val atIndex = lastIndexOf('@', startIndex = (safeCursor - 1).coerceAtLeast(0))
    if (atIndex < 0) return this
    val before = substring(0, atIndex).trimEnd()
    val after = substring(safeCursor).trimStart()
    return listOf(before, after)
        .filter { part -> part.isNotBlank() }
        .joinToString(" ")
}

private fun List<MentionCandidate>.toAiMentionPaletteItems(): List<RichTextCommandPaletteItem> =
    map { candidate ->
        RichTextCommandPaletteItem(
            id = candidate.toPaletteItemId(),
            title = candidate.label,
            subtitle = candidate.subtitle.ifBlank {
                if (candidate.score > 0) "Mention page" else "Recent page"
            },
            leading = "@",
            kind = RichTextCommandPaletteKind.Mention,
            groupLabel = "Page",
        )
    }

private fun MentionCandidate.toPaletteItemId(): String = "mention:$pageId"
