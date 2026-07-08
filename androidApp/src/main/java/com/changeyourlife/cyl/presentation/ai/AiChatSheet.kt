package com.changeyourlife.cyl.presentation.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import com.changeyourlife.cyl.domain.model.AiActionUndoState
import com.changeyourlife.cyl.domain.model.ChatSession
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.presentation.page.EditorSuggestionController
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteItem
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteKind
import com.changeyourlife.cyl.presentation.page.paletteItemId
import com.changeyourlife.cyl.presentation.page.richTextMentionPaletteItems
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AiChatSheet(
    messages: List<AiChatMessage>,
    mentionPages: List<Page>,
    chatSessions: List<ChatSession> = emptyList(),
    activeChatSessionId: String? = null,
    isGenerating: Boolean,
    errorMessage: String?,
    modelLabel: String = "AI model",
    onSendMessage: (String, List<String>, List<AiImageAttachment>) -> Unit,
    onUndoAction: (String, String) -> Unit,
    onClearHistory: () -> Unit,
    onCreateChatSession: () -> Unit,
    onSelectChatSession: (String) -> Unit = {},
    onDeleteChatSession: (String) -> Unit = {},
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
    var stagedImageAttachments by remember {
        mutableStateOf(emptyList<AiImageAttachment>())
    }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            context.toAiImageAttachment(uri, fallbackName = "Photo")
                ?.let { attachment ->
                    stagedImageAttachments = (stagedImageAttachments + attachment).takeLast(MaxAiChatImages)
                }
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            context.toAiImageAttachment(uri, fallbackName = "Image file")
                ?.let { attachment ->
                    stagedImageAttachments = (stagedImageAttachments + attachment).takeLast(MaxAiChatImages)
                }
        }
    }
    val cameraPreview = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            bitmap.toAiImageAttachment(context, name = "Camera")
                ?.let { attachment ->
                    stagedImageAttachments = (stagedImageAttachments + attachment).takeLast(MaxAiChatImages)
                }
        }
    }
    val pastedImageReceiver = ReceiveContentListener { transferableContent ->
        if (!transferableContent.hasMediaType(MediaType.Image)) {
            return@ReceiveContentListener transferableContent
        }

        var attachedCount = 0
        var usedPlatformUri = false
        val platformUri = transferableContent.platformTransferableContent?.linkUri
        val remainingContent = transferableContent.consume { item ->
            val uri = item.uri ?: platformUri?.takeIf { !usedPlatformUri }
            val attachment = uri?.let { imageUri ->
                context.toPastedImageAttachment(imageUri, fallbackName = "Pasted image")
            }
            if (attachment != null) {
                if (item.uri == null) usedPlatformUri = true
                stagedImageAttachments = (stagedImageAttachments + attachment).takeLast(MaxAiChatImages)
                attachedCount += 1
                true
            } else {
                false
            }
        }
        if (attachedCount > 0) {
            Toast.makeText(
                context,
                if (attachedCount == 1) "Image pasted" else "$attachedCount images pasted",
                Toast.LENGTH_SHORT,
            ).show()
        }
        remainingContent
    }
    val mentionSuggestionState = EditorSuggestionController.resolve(
        text = inputText,
        cursor = inputText.length,
        mentionPages = mentionPages,
        enabledKinds = setOf(RichTextCommandPaletteKind.Mention),
    )
    var activePanel by rememberSaveable { mutableStateOf<AiComposerPanel?>(null) }
    var isMentionPickerOpen by rememberSaveable { mutableStateOf(false) }
    var aiDisplayName by rememberSaveable { mutableStateOf("CYL AI") }
    var avatarColorIndex by rememberSaveable { mutableStateOf(0) }
    var avatarIconKey by rememberSaveable { mutableStateOf(DefaultAiAvatarIconKey) }
    val avatarIconOptions = remember { aiAvatarIconOptions() }
    val avatarSpec = remember(avatarColorIndex, avatarIconKey, avatarIconOptions) {
        AiAvatarSpec(
            color = aiAvatarColors[avatarColorIndex.mod(aiAvatarColors.size)],
            icon = avatarIconOptions.firstOrNull { icon -> icon.key == avatarIconKey } ?: avatarIconOptions.first(),
        )
    }
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
        if ((inputText.isNotBlank() || stagedImageAttachments.isNotEmpty()) && !isGenerating) {
            val message = inputText.trim().ifBlank { "Read the attached file or image and help me use it in CYL." }
            onSendMessage(
                message,
                message.resolveMentionedPageIds(
                    pages = mentionPages,
                    knownPageIds = selectedMentionPageIds + attachedMentionPageIds,
                ),
                stagedImageAttachments,
            )
            inputState.setTextAndPlaceCursorAtEnd("")
            selectedMentionPageIds = emptyList()
            stagedImageAttachments = emptyList()
            activePanel = null
            isMentionPickerOpen = false
        }
    }
    val onSelectMentionPage: (Page) -> Unit = { page ->
        inputState.setTextAndPlaceCursorAtEnd(inputText.removeActiveMentionQuery())
        selectedMentionPageIds = (selectedMentionPageIds + page.id)
            .filter { id -> id.isNotBlank() }
            .distinct()
        isMentionPickerOpen = false
        activePanel = null
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(attachedPageId) {
        inputState.setTextAndPlaceCursorAtEnd("")
    }
    LaunchedEffect(activePanel, isMentionPickerOpen) {
        if (activePanel != null || isMentionPickerOpen) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            delay(80)
            keyboardController?.hide()
        }
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
                displayName = aiDisplayName,
                avatarSpec = avatarSpec,
                hasChatHistory = chatSessions.isNotEmpty(),
                onOpenHistory = {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    activePanel = AiComposerPanel.History
                    isMentionPickerOpen = false
                },
                onOpenProfile = {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    activePanel = AiComposerPanel.Profile
                    isMentionPickerOpen = false
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

            AiComposerCard(
                inputState = inputState,
                inputText = inputText,
                mentionChips = mentionChips,
                onRemoveMention = { pageId ->
                    selectedMentionPageIds = selectedMentionPageIds.filterNot { id -> id == pageId }
                },
                stagedAttachmentLabels = stagedImageAttachments.mapIndexed { index, attachment ->
                    attachment.name.ifBlank { "Image ${index + 1}" }
                },
                onRemoveAttachment = { index ->
                    stagedImageAttachments = stagedImageAttachments.filterIndexed { itemIndex, _ ->
                        itemIndex != index
                    }
                },
                isInputEnabled = activePanel == null && !isMentionPickerOpen,
                onInputFocus = {
                    activePanel = null
                    isMentionPickerOpen = false
                },
                onOpenAttachments = {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    activePanel = if (activePanel == AiComposerPanel.Attachments) {
                        null
                    } else {
                        AiComposerPanel.Attachments
                    }
                    isMentionPickerOpen = false
                },
                onOpenSettings = {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    activePanel = if (activePanel == AiComposerPanel.Settings) {
                        null
                    } else {
                        AiComposerPanel.Settings
                    }
                    isMentionPickerOpen = false
                },
                onSend = sendCurrentPrompt,
                isGenerating = isGenerating,
                pastedImageReceiver = pastedImageReceiver,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val manualMentionItems = if (isMentionPickerOpen) {
                richTextMentionPaletteItems(mentionPages)
            } else {
                emptyList()
            }
            val mentionItems = if (isMentionPickerOpen) {
                manualMentionItems
            } else {
                mentionSuggestionState?.items.orEmpty()
            }
            if (activePanel != null || isMentionPickerOpen || mentionSuggestionState != null) {
                AiKeyboardReplacementPanel(
                    title = when {
                        isMentionPickerOpen || mentionSuggestionState != null -> "Context"
                        activePanel == AiComposerPanel.History -> "Chat history"
                        activePanel == AiComposerPanel.Profile -> "Customize AI"
                        activePanel == AiComposerPanel.Settings -> "AI settings"
                        else -> "Add"
                    },
                    onClose = {
                        activePanel = null
                        isMentionPickerOpen = false
                    },
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    when {
                        isMentionPickerOpen || mentionSuggestionState != null -> {
                            AiMentionPickerPanel(
                                items = mentionItems,
                                onSelect = { item ->
                                    val page = mentionPages.firstOrNull { candidate ->
                                        candidate.paletteItemId() == item.id
                                    }
                                    if (page != null) {
                                        onSelectMentionPage(page)
                                    }
                                },
                                selectedItemId = mentionSuggestionState?.selectedItem?.id,
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
                                hasMessages = messages.isNotEmpty(),
                                onClearHistory = onClearHistory,
                            )
                        }
                        activePanel == AiComposerPanel.History -> {
                            AiHistoryPanel(
                                sessions = chatSessions,
                                activeSessionId = activeChatSessionId,
                                onSelect = { sessionId ->
                                    activePanel = null
                                    onSelectChatSession(sessionId)
                                },
                                onDelete = onDeleteChatSession,
                            )
                        }
                        activePanel == AiComposerPanel.Profile -> {
                            AiProfilePanel(
                                displayName = aiDisplayName,
                                onDisplayNameChange = { value ->
                                    aiDisplayName = value.take(32)
                                },
                                avatarColorIndex = avatarColorIndex,
                                onAvatarColorIndexChange = { index ->
                                    avatarColorIndex = index
                                },
                                avatarIconKey = avatarIconKey,
                                onAvatarIconKeyChange = { key ->
                                    avatarIconKey = key
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class AiComposerPanel {
    Attachments,
    Settings,
    History,
    Profile,
}

private const val MaxAiChatImages = 4
private const val MaxAiChatImageBytes = 4L * 1024L * 1024L
private const val MaxAiChatTextFileBytes = 256L * 1024L

private fun Context.toAiImageAttachment(
    uri: Uri,
    fallbackName: String,
): AiImageAttachment? {
    val rawMimeType = contentResolver.getType(uri).orEmpty()
    val name = queryOpenableName(uri).ifBlank { fallbackName }
    val bytes = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            input.readBytesWithLimit(maxOf(MaxAiChatImageBytes, MaxAiChatTextFileBytes) + 1)
        }
    }.getOrNull()

    if (bytes == null || bytes.isEmpty()) {
        Toast.makeText(this, "Could not read that file.", Toast.LENGTH_SHORT).show()
        return null
    }
    val mimeType = inferAttachmentMimeType(
        rawMimeType = rawMimeType,
        name = name,
        bytes = bytes,
    )

    return when {
        mimeType.startsWith("image/", ignoreCase = true) -> {
            if (bytes.size.toLong() > MaxAiChatImageBytes) {
                Toast.makeText(this, "Image is too large. Use an image under 4 MB.", Toast.LENGTH_SHORT).show()
                null
            } else {
                AiImageAttachment(
                    dataUrl = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
                    mimeType = mimeType,
                    name = name,
                    sizeBytes = bytes.size.toLong(),
                    kind = "image",
                )
            }
        }
        isReadableTextAttachment(mimeType = mimeType, name = name) -> {
            if (bytes.size.toLong() > MaxAiChatTextFileBytes) {
                Toast.makeText(this, "Text file is too large. Use a file under 256 KB.", Toast.LENGTH_SHORT).show()
                null
            } else {
                AiImageAttachment(
                    textContent = bytes.toString(Charsets.UTF_8).cleanTextFileForAi(),
                    mimeType = mimeType,
                    name = name,
                    sizeBytes = bytes.size.toLong(),
                    kind = "text",
                )
            }
        }
        else -> {
            Toast.makeText(
                this,
                "This file type is not readable yet. Use image, TXT, CSV, Markdown, or JSON.",
                Toast.LENGTH_LONG,
            ).show()
            null
        }
    }
}

private fun Context.toPastedImageAttachment(
    uri: Uri,
    fallbackName: String,
): AiImageAttachment? {
    val attachment = toAiImageAttachment(uri = uri, fallbackName = fallbackName) ?: return null
    return if (attachment.kind == "image" || attachment.mimeType.startsWith("image/", ignoreCase = true)) {
        attachment.copy(name = attachment.name.ifBlank { fallbackName })
    } else {
        null
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

private fun Bitmap.toAiImageAttachment(
    context: Context,
    name: String,
): AiImageAttachment? {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 86, output)
    val bytes = output.toByteArray()
    if (bytes.size.toLong() > MaxAiChatImageBytes) {
        Toast.makeText(context, "Camera image is too large. Try a smaller image.", Toast.LENGTH_SHORT).show()
        return null
    }
    return AiImageAttachment(
        dataUrl = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
        mimeType = "image/jpeg",
        name = "$name.jpg",
        sizeBytes = bytes.size.toLong(),
        kind = "image",
    )
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

private const val DefaultAiAvatarIconKey = "spark"

private data class AiAvatarSpec(
    val color: Color,
    val icon: AiAvatarIconOption,
)

private data class AiAvatarIconOption(
    val key: String,
    val icon: ImageVector,
    val label: String,
)

private val aiAvatarColors = listOf(
    Color(0xFFE74C3C),
    Color(0xFF2E7DFF),
    Color(0xFF18A058),
    Color(0xFFF59E0B),
    Color(0xFF8B5CF6),
    Color(0xFF0F766E),
)

private fun aiAvatarIconOptions(): List<AiAvatarIconOption> {
    return listOf(
        AiAvatarIconOption(DefaultAiAvatarIconKey, Icons.Rounded.AutoAwesome, "Spark"),
        AiAvatarIconOption("edit", Icons.Rounded.Edit, "Edit"),
        AiAvatarIconOption("web", Icons.Rounded.Public, "Web"),
        AiAvatarIconOption("person", Icons.Rounded.Person, "Guide"),
    )
}

@Composable
private fun AiAvatar(
    spec: AiAvatarSpec,
    size: Int,
    iconSize: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 2).dp))
            .background(spec.color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = spec.icon.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

@Composable
private fun AiKeyboardReplacementPanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 258.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close panel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun AiMentionPickerPanel(
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
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(item) }
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                    )
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.leading,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
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
private fun AiHistoryPanel(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        Text(
            text = "No chat history yet",
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 250.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = sessions.sortedByDescending { session -> session.updatedAt },
            key = { session -> session.id },
        ) { session ->
            val isActive = session.id == activeSessionId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(session.id) }
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                    )
                    .padding(start = 10.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = session.title.ifBlank { "New chat" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = session.updatedAt.toAiDisplayDateTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { onDelete(session.id) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiProfilePanel(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    avatarColorIndex: Int,
    onAvatarColorIndexChange: (Int) -> Unit,
    avatarIconKey: String,
    onAvatarIconKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconOptions = remember { aiAvatarIconOptions() }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selectedColor = aiAvatarColors[avatarColorIndex.mod(aiAvatarColors.size)]
            val selectedIcon = iconOptions.firstOrNull { icon -> icon.key == avatarIconKey } ?: iconOptions.first()
            AiAvatar(
                spec = AiAvatarSpec(selectedColor, selectedIcon),
                size = 54,
                iconSize = 28,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (displayName.isBlank()) {
                                Text(
                                    text = "CYL AI",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }

        Text(
            text = "Color",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            aiAvatarColors.forEachIndexed { index, color ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(color)
                        .border(
                            width = if (index == avatarColorIndex) 3.dp else 1.dp,
                            color = if (index == avatarColorIndex) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable { onAvatarColorIndexChange(index) },
                )
            }
        }

        Text(
            text = "Icon",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconOptions.forEach { option ->
                val selected = option.key == avatarIconKey
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onAvatarIconKeyChange(option.key) }
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.label,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
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
    displayName: String,
    avatarSpec: AiAvatarSpec,
    hasChatHistory: Boolean,
    onOpenHistory: () -> Unit,
    onOpenProfile: () -> Unit,
    onCreateChatSession: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AiRoundIconButton(
            icon = Icons.Rounded.AccessTime,
            contentDescription = "Chat history",
            enabled = true,
            onClick = onOpenHistory,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenProfile)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AiAvatar(
                spec = avatarSpec,
                size = 62,
                iconSize = 32,
            )
            Text(
                text = displayName.ifBlank { "CYL AI" },
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiComposerCard(
    inputState: TextFieldState,
    inputText: String,
    mentionChips: List<AiMentionChipUi>,
    onRemoveMention: (String) -> Unit,
    stagedAttachmentLabels: List<String>,
    onRemoveAttachment: (Int) -> Unit,
    isInputEnabled: Boolean,
    onInputFocus: () -> Unit,
    onOpenAttachments: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    pastedImageReceiver: ReceiveContentListener,
    modifier: Modifier = Modifier,
) {
    val canSend = (inputText.isNotBlank() || stagedAttachmentLabels.isNotEmpty()) && !isGenerating
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
                state = inputState,
                enabled = isInputEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp, max = 118.dp)
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

private fun Long.toAiDisplayDateTime(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
}

private fun String.prettyActionType(): String {
    return lowercase()
        .split('_')
        .filter { part -> part.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { char -> char.titlecase() } }
}
