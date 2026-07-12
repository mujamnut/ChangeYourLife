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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.presentation.page.EditorSuggestionController
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteKind
import com.changeyourlife.cyl.presentation.page.paletteItemId
import com.changeyourlife.cyl.presentation.page.richTextMentionPaletteItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AiChatSheet(
    messages: List<AiChatMessage>,
    mentionPages: List<Page>,
    persona: AiPersonaUiState,
    isGenerating: Boolean,
    errorMessage: String?,
    modelLabel: String = "AI model",
    visionStatusLabel: String = "",
    visionPipelineLabel: String = "",
    onSendMessage: (String, List<String>, List<AiImageAttachment>) -> Unit,
    onUndoAction: (String, String) -> Unit,
    onClearHistory: () -> Unit,
    onCreateChatSession: () -> Unit,
    onOpenHistoryPage: () -> Unit,
    onOpenProfilePage: () -> Unit,
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
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val composerScope = rememberCoroutineScope()
    val stableSheetMaxHeight = remember(configuration.screenHeightDp) {
        configuration.screenHeightDp.dp * 0.90f
    }
    fun stageAttachmentResult(
        result: AiAttachmentReadResult,
        successMessage: String? = null,
    ) {
        result.attachment?.let { attachment ->
            stagedImageAttachments = (stagedImageAttachments + attachment).takeLast(MaxAiChatImages)
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
                val results = withContext(Dispatchers.IO) {
                    queuedUris.map { imageUri ->
                        context.readPastedImageAttachment(imageUri, fallbackName = "Pasted image")
                    }
                }
                var attachedCount = 0
                results.forEach { result ->
                    result.attachment?.let { attachment ->
                        stagedImageAttachments = (stagedImageAttachments + attachment).takeLast(MaxAiChatImages)
                        attachedCount += 1
                    }
                }
                if (attachedCount > 0) {
                    Toast.makeText(
                        context,
                        if (attachedCount == 1) "Image pasted" else "$attachedCount images pasted",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    results.lastOrNull()?.userMessage?.takeIf { it.isNotBlank() }?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
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
    var isKeyboardReplacementVisible by remember { mutableStateOf(false) }
    val shouldShowKeyboardReplacement = activePanel != null || isMentionPickerOpen || mentionSuggestionState != null
    val avatarSpec = remember(persona) { persona.toAvatarSpec() }
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
    LaunchedEffect(shouldShowKeyboardReplacement) {
        if (shouldShowKeyboardReplacement) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            delay(120)
            keyboardController?.hide()
            isKeyboardReplacementVisible = true
        } else {
            isKeyboardReplacementVisible = false
        }
    }
    fun openComposerPanel(panel: AiComposerPanel) {
        val nextPanel = if (activePanel == panel) null else panel
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        isMentionPickerOpen = false
        isKeyboardReplacementVisible = false
        activePanel = null
        if (nextPanel == null) return
        composerScope.launch {
            delay(80)
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            activePanel = nextPanel
            delay(120)
            focusManager.clearFocus(force = true)
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
                            modifier = if (isUser) {
                                Modifier.widthIn(max = 300.dp)
                            } else {
                                Modifier.fillMaxWidth()
                            },
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = messageText,
                                modifier = if (isUser) {
                                    Modifier
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
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
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
                                        .padding(horizontal = 2.dp, vertical = 5.dp)
                                },
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
                        }
                    }
                }

                if (isGenerating) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 2.dp, vertical = 10.dp),
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
                stagedAttachments = stagedImageAttachments.mapIndexed { index, attachment ->
                    AiAttachmentPreviewUi(
                        label = attachment.name.ifBlank {
                            if (attachment.kind == "text") "File ${index + 1}" else "Image ${index + 1}"
                        },
                        mimeType = attachment.mimeType,
                        dataUrl = attachment.dataUrl,
                        kind = attachment.kind,
                        statusLabel = when {
                            attachment.kind == "text" -> "Text ready"
                            attachment.mimeType.startsWith("image/", ignoreCase = true) -> "Vision ready"
                            else -> "Attached"
                        },
                    )
                },
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
            AnimatedVisibility(
                visible = shouldShowKeyboardReplacement && isKeyboardReplacementVisible,
                enter = slideInVertically(initialOffsetY = { height -> height }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { height -> height }) + fadeOut(),
            ) {
                AiKeyboardReplacementPanel(
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
                                visionStatusLabel = visionStatusLabel,
                                visionPipelineLabel = visionPipelineLabel,
                                hasMessages = messages.isNotEmpty(),
                                onClearHistory = onClearHistory,
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
}

private const val MaxAiChatImages = 4
private const val MaxAiChatImageBytes = 4L * 1024L * 1024L
private const val MaxAiChatTextFileBytes = 256L * 1024L

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
            mimeType = "image/jpeg",
            name = "$name.jpg",
            sizeBytes = bytes.size.toLong(),
            kind = "image",
        ),
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
