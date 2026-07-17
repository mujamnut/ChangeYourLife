package com.changeyourlife.cyl.presentation.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import com.changeyourlife.cyl.domain.model.MentionCandidate
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteKind
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteItem
import com.changeyourlife.cyl.presentation.page.RichTextMentionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

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
    var isBodyDismissInProgress by remember { mutableStateOf(false) }
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
                onBodyDismiss = {
                    if (!isBodyDismissInProgress) {
                        isBodyDismissInProgress = true
                        composerScope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onDismiss()
                            } else {
                                isBodyDismissInProgress = false
                            }
                        }
                    }
                },
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
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    AnimatedVisibility(
        visible = requestedVisible && !isImeVisible,
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
    onBodyDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val dismissThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }
    val bodyScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (source == NestedScrollSource.UserInput) {
                Offset(x = 0f, y = available.y)
            } else {
                Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(x = 0f, y = available.y)
        }
    }
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
        modifier = modifier
            .nestedScroll(bodyScrollBoundary)
            .aiChatBodyDismissGesture(
                canStartDismiss = { !listState.canScrollBackward },
                dismissThresholdPx = dismissThresholdPx,
                onDismiss = onBodyDismiss,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        // The sheet already consumes unhandled vertical drag through nested scroll.
        // A second overscroll transform here makes pull-to-dismiss visibly fight the sheet.
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
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { context.copyAiChatMessage(messageText) },
                                    )
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { context.copyAiChatMessage(messageText) },
                                    )
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

private fun Modifier.aiChatBodyDismissGesture(
    canStartDismiss: () -> Boolean,
    dismissThresholdPx: Float,
    onDismiss: () -> Unit,
): Modifier = pointerInput(dismissThresholdPx, onDismiss) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        if (!canStartDismiss()) return@awaitEachGesture

        var distanceY = 0f
        var isDismissDrag = false
        var didDismiss = false

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { pointer -> pointer.id == down.id } ?: break
            if (!change.pressed) break

            val deltaY = change.positionChange().y
            distanceY += deltaY

            if (!isDismissDrag && abs(distanceY) >= viewConfiguration.touchSlop) {
                if (distanceY <= 0f) break
                isDismissDrag = true
            }

            if (isDismissDrag) {
                change.consume()
                if (!didDismiss && distanceY >= dismissThresholdPx) {
                    didDismiss = true
                    onDismiss()
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

private const val MaxAiChatImages = 4
private const val MaxAiChatImageBytes = 4L * 1024L * 1024L
private const val MaxAiChatTextFileBytes = 256L * 1024L
private const val MaxAiChatPreviewDimension = 384
private const val MaxAiChatPreviewBytes = 96 * 1024

private data class AiAttachmentReadResult(
    val attachment: AiImageAttachment? = null,
    val userMessage: String? = null,
)

private fun Context.readAiImageAttachment(
    uri: Uri,
    fallbackName: String,
): AiAttachmentReadResult {
    val rawMimeType = contentResolver.getType(uri).orEmpty()
    val name = queryOpenableName(uri).ifBlank { fallbackName }
    val bytes = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            input.readBytesWithLimit(maxOf(MaxAiChatImageBytes, MaxAiChatTextFileBytes) + 1)
        }
    }.getOrNull()

    if (bytes == null || bytes.isEmpty()) {
        return AiAttachmentReadResult(userMessage = "Could not read that file.")
    }
    val mimeType = inferAttachmentMimeType(
        rawMimeType = rawMimeType,
        name = name,
        bytes = bytes,
    )

    return when {
        mimeType.startsWith("image/", ignoreCase = true) -> {
            if (bytes.size.toLong() > MaxAiChatImageBytes) {
                AiAttachmentReadResult(userMessage = "Image is too large. Use an image under 4 MB.")
            } else {
                AiAttachmentReadResult(
                    attachment = AiImageAttachment(
                        dataUrl = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
                        previewDataUrl = bytes.toAiChatPreviewDataUrl(),
                        mimeType = mimeType,
                        name = name,
                        sizeBytes = bytes.size.toLong(),
                        kind = "image",
                    ),
                )
            }
        }
        isReadableTextAttachment(mimeType = mimeType, name = name) -> {
            if (bytes.size.toLong() > MaxAiChatTextFileBytes) {
                AiAttachmentReadResult(userMessage = "Text file is too large. Use a file under 256 KB.")
            } else {
                AiAttachmentReadResult(
                    attachment = AiImageAttachment(
                        textContent = bytes.toString(Charsets.UTF_8).cleanTextFileForAi(),
                        mimeType = mimeType,
                        name = name,
                        sizeBytes = bytes.size.toLong(),
                        kind = "text",
                    ),
                )
            }
        }
        else -> AiAttachmentReadResult(
            userMessage = "This file type is not readable yet. Use image, TXT, CSV, Markdown, or JSON.",
        )
    }
}

private fun Context.readPastedImageAttachment(
    uri: Uri,
    fallbackName: String,
): AiAttachmentReadResult {
    val result = readAiImageAttachment(uri = uri, fallbackName = fallbackName)
    val attachment = result.attachment ?: return result
    return if (attachment.kind == "image" || attachment.mimeType.startsWith("image/", ignoreCase = true)) {
        AiAttachmentReadResult(attachment = attachment.copy(name = attachment.name.ifBlank { fallbackName }))
    } else {
        AiAttachmentReadResult(userMessage = "Only images can be pasted into the chat composer.")
    }
}

private fun inferAttachmentMimeType(
    rawMimeType: String,
    name: String,
    bytes: ByteArray,
): String {
    val normalized = rawMimeType.lowercase().trim()
    if (normalized.startsWith("image/") || normalized.startsWith("text/")) return normalized
    if (normalized.isNotBlank() && normalized != "application/octet-stream") {
        return normalized
    }

    val lowerName = name.lowercase()
    return when {
        lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || bytes.isJpeg() -> "image/jpeg"
        lowerName.endsWith(".png") || bytes.isPng() -> "image/png"
        lowerName.endsWith(".webp") || bytes.isWebp() -> "image/webp"
        lowerName.endsWith(".gif") || bytes.isGif() -> "image/gif"
        isReadableTextAttachment(mimeType = normalized, name = name) -> "text/plain"
        else -> normalized.ifBlank { "application/octet-stream" }
    }
}

private fun ByteArray.isJpeg(): Boolean =
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

private fun ByteArray.isPng(): Boolean =
    size >= 8 &&
        this[0] == 0x89.toByte() &&
        this[1] == 0x50.toByte() &&
        this[2] == 0x4E.toByte() &&
        this[3] == 0x47.toByte()

private fun ByteArray.isGif(): Boolean =
    size >= 6 &&
        this[0] == 'G'.code.toByte() &&
        this[1] == 'I'.code.toByte() &&
        this[2] == 'F'.code.toByte()

private fun ByteArray.isWebp(): Boolean =
    size >= 12 &&
        this[0] == 'R'.code.toByte() &&
        this[1] == 'I'.code.toByte() &&
        this[2] == 'F'.code.toByte() &&
        this[3] == 'F'.code.toByte() &&
        this[8] == 'W'.code.toByte() &&
        this[9] == 'E'.code.toByte() &&
        this[10] == 'B'.code.toByte() &&
        this[11] == 'P'.code.toByte()

private fun Context.queryOpenableName(uri: Uri): String {
    return runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            } else {
                ""
            }
        }.orEmpty()
    }.getOrDefault("")
}

private fun Bitmap.toAiImageAttachmentResult(
    name: String,
): AiAttachmentReadResult {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 86, output)
    val bytes = output.toByteArray()
    if (bytes.size.toLong() > MaxAiChatImageBytes) {
        return AiAttachmentReadResult(userMessage = "Camera image is too large. Try a smaller image.")
    }
    return AiAttachmentReadResult(
        attachment = AiImageAttachment(
            dataUrl = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
            previewDataUrl = bytes.toAiChatPreviewDataUrl(),
            mimeType = "image/jpeg",
            name = "$name.jpg",
            sizeBytes = bytes.size.toLong(),
            kind = "image",
        ),
    )
}

private fun ByteArray.toAiChatPreviewDataUrl(): String {
    if (isEmpty()) return ""
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > MaxAiChatPreviewDimension * 2 ||
        bounds.outHeight / sampleSize > MaxAiChatPreviewDimension * 2
    ) {
        sampleSize *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
        this,
        0,
        size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return ""
    val scale = minOf(
        1f,
        MaxAiChatPreviewDimension.toFloat() / maxOf(decoded.width, decoded.height).toFloat(),
    )
    val targetWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
    val scaled = if (targetWidth == decoded.width && targetHeight == decoded.height) {
        decoded
    } else {
        Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
    }
    val flattened = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
        drawColor(Color.WHITE)
        drawBitmap(scaled, 0f, 0f, null)
    }

    var quality = 78
    var previewBytes: ByteArray
    do {
        val output = ByteArrayOutputStream()
        flattened.compress(Bitmap.CompressFormat.JPEG, quality, output)
        previewBytes = output.toByteArray()
        quality -= 10
    } while (previewBytes.size > MaxAiChatPreviewBytes && quality >= 38)

    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    flattened.recycle()
    if (previewBytes.size > MaxAiChatPreviewBytes) return ""
    return "data:image/jpeg;base64,${Base64.encodeToString(previewBytes, Base64.NO_WRAP)}"
}

private fun java.io.InputStream.readBytesWithLimit(limitBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        total += read
        if (total > limitBytes) {
            return output.toByteArray() + buffer.copyOf(read)
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun isReadableTextAttachment(
    mimeType: String,
    name: String,
): Boolean {
    val lowerMime = mimeType.lowercase()
    val lowerName = name.lowercase()
    return lowerMime.startsWith("text/") ||
        lowerMime in setOf(
            "application/json",
            "application/xml",
            "application/yaml",
            "application/x-yaml",
            "application/csv",
            "application/sql",
            "application/javascript",
        ) ||
        lowerName.endsWith(".txt") ||
        lowerName.endsWith(".md") ||
        lowerName.endsWith(".markdown") ||
        lowerName.endsWith(".csv") ||
        lowerName.endsWith(".tsv") ||
        lowerName.endsWith(".json") ||
        lowerName.endsWith(".xml") ||
        lowerName.endsWith(".yaml") ||
        lowerName.endsWith(".yml") ||
        lowerName.endsWith(".sql") ||
        lowerName.endsWith(".log")
}

private fun String.cleanTextFileForAi(): String {
    return replace("\u0000", "")
        .lines()
        .joinToString("\n") { line -> line.trimEnd() }
        .trim()
}

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
