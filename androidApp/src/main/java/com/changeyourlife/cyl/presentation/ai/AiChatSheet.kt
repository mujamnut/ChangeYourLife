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
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import com.changeyourlife.cyl.domain.model.Page

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AiChatSheet(
    messages: List<AiChatMessage>,
    mentionPages: List<Page>,
    isGenerating: Boolean,
    errorMessage: String?,
    aiMode: AiChatMode,
    availableModes: List<AiChatMode> = AiChatMode.entries,
    modelLabel: String = "AI model",
    onAiModeChange: (AiChatMode) -> Unit,
    onSendMessage: (String, List<String>, AiChatMode) -> Unit,
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
    val effectiveMode = aiMode.takeIf { mode -> mode in availableModes }
        ?: availableModes.firstOrNull()
        ?: AiChatMode.Planning
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val mentionQuery = inputText.activeMentionQuery()
    val mentionSuggestions = if (mentionQuery != null) {
        mentionPages
            .filter { page ->
                val title = page.title.ifBlank { "Untitled page" }
                mentionQuery.isBlank() || title.contains(mentionQuery, ignoreCase = true)
            }
            .take(6)
    } else {
        emptyList()
    }

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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "CYL AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        AiModeModelSelector(
                            mode = effectiveMode,
                            availableModes = availableModes,
                            modelLabel = modelLabel,
                            onModeChange = onAiModeChange,
                        )
                        if (!attachedPageTitle.isNullOrBlank()) {
                            Text(
                                text = "This page: ${attachedPageTitle.ifBlank { "Untitled page" }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCreateChatSession) {
                        Text(text = "New")
                    }
                    if (messages.isNotEmpty()) {
                        TextButton(onClick = onClearHistory) {
                            Text(text = "Clear")
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

                items(messages) { message ->
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
                                text = if (isUser) "You" else "CYL AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

            if (mentionSuggestions.isNotEmpty()) {
                AiMentionSuggestions(
                    pages = mentionSuggestions,
                    onSelectPage = { page ->
                        inputText = inputText.insertMention(page.title.ifBlank { "Untitled page" })
                        selectedMentionPageIds = (selectedMentionPageIds + page.id)
                            .filter { id -> id.isNotBlank() }
                            .distinct()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
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
                                    effectiveMode,
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
                                effectiveMode,
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
private fun AiModeModelSelector(
    mode: AiChatMode,
    availableModes: List<AiChatMode>,
    modelLabel: String,
    onModeChange: (AiChatMode) -> Unit,
) {
    var isMenuOpen by rememberSaveable { mutableStateOf(false) }
    val canSwitchMode = availableModes.size > 1
    val displayModel = modelLabel.compactAiModelLabel()
    val selectorModifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .let { base ->
            if (canSwitchMode) {
                base.clickable { isMenuOpen = true }
            } else {
                base
            }
        }
        .padding(start = 9.dp, end = if (canSwitchMode) 5.dp else 9.dp, top = 4.dp, bottom = 4.dp)

    Box {
        Row(
            modifier = selectorModifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${mode.label} - $displayModel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (canSwitchMode) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Change AI mode",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        DropdownMenu(
            expanded = isMenuOpen,
            onDismissRequest = { isMenuOpen = false },
        ) {
            availableModes.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (option == mode) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (option == mode) {
                                Text(
                                    text = displayModel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    onClick = {
                        isMenuOpen = false
                        onModeChange(option)
                    },
                )
            }
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
private fun AiMentionSuggestions(
    pages: List<Page>,
    onSelectPage: (Page) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column {
            pages.forEach { page ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = page.title.ifBlank { "Untitled page" },
                            maxLines = 1,
                        )
                    },
                    supportingContent = { Text(text = "Mention page") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Article,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.clickable { onSelectPage(page) },
                )
            }
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

private fun String.activeMentionQuery(): String? {
    val atIndex = lastIndexOf('@')
    if (atIndex < 0) return null
    val query = substring(atIndex + 1)
    if (query.contains('\n') || query.endsWith(" ")) return null
    return query.trim()
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
