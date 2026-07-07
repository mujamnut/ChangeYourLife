package com.changeyourlife.cyl.presentation.ai

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val mentionSuggestionState = EditorSuggestionController.resolve(
        text = inputText,
        cursor = inputText.length,
        mentionPages = mentionPages,
        enabledKinds = setOf(RichTextCommandPaletteKind.Mention),
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AiModelChip(modelLabel = modelLabel)
                    if (!attachedPageTitle.isNullOrBlank()) {
                        Text(
                            text = attachedPageTitle.ifBlank { "Untitled page" },
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCreateChatSession) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New chat",
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Clear chat",
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                        )
                    }
                }
            }
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
                    .height(360.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
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

            if (mentionSuggestionState != null) {
                RichTextCommandPalette(
                    items = mentionSuggestionState.items,
                    onSelect = { item ->
                        val page = mentionPages.firstOrNull { candidate ->
                            candidate.paletteItemId() == item.id
                        }
                        if (page != null) {
                            inputText = inputText.insertMention(page.title.ifBlank { "Untitled page" })
                            selectedMentionPageIds = (selectedMentionPageIds + page.id)
                                .filter { id -> id.isNotBlank() }
                                .distinct()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    selectedItemId = mentionSuggestionState.selectedItem?.id,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message AI...") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
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
                            }
                        },
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
                IconButton(
                    onClick = {
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
                        }
                    },
                    enabled = inputText.isNotBlank() && !isGenerating,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank() && !isGenerating) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                }
            }
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
private fun AiModelChip(
    modelLabel: String,
) {
    val displayModel = modelLabel.compactAiModelLabel()
    val selectorModifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(horizontal = 9.dp, vertical = 4.dp)

    Row(
        modifier = selectorModifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = displayModel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

private fun String.insertMention(title: String): String {
    val atIndex = lastIndexOf('@')
    val mention = "@$title "
    if (atIndex < 0) return this + mention
    return substring(0, atIndex) + mention
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
