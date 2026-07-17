package com.changeyourlife.cyl.presentation.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.ChatSession
import com.changeyourlife.cyl.presentation.home.ChatSessionPreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AiHistoryRoute(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    previews: Map<String, ChatSessionPreview>,
    searchQuery: String,
    searchResults: List<ChatHistorySearchResult>,
    onBack: () -> Unit,
    onCreateChatSession: () -> Unit,
    onSelectChatSession: (String) -> Unit,
    onDeleteChatSession: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearchQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val isSearching = searchQuery.isNotBlank()
    val sortedSessions = remember(sessions) {
        sessions.sortedByDescending { session -> session.updatedAt }
    }

    LaunchedEffect(activeSessionId, sortedSessions, isSearching) {
        if (isSearching) return@LaunchedEffect
        val targetIndex = sortedSessions.indexOfFirst { session -> session.id == activeSessionId }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AiFullPageHeader(
                title = "Chat history",
                onBack = onBack,
                action = {
                    IconButton(
                        onClick = onCreateChatSession,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New chat",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (sessions.isEmpty()) {
            AiEmptyPageState(
                icon = Icons.Rounded.History,
                title = "No chat history yet",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AiHistorySearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClear = onClearSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )

            if (isSearching && searchResults.isEmpty()) {
                AiEmptyPageState(
                    icon = Icons.Rounded.Search,
                    title = "No chats found",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (isSearching) {
                        items(
                            items = searchResults,
                            key = { result -> result.session.id },
                        ) { result ->
                            AiHistoryRow(
                                session = result.session,
                                isActive = result.session.id == activeSessionId,
                                preview = previews[result.session.id],
                                supportTextOverride = result.snippet,
                                onClick = { onSelectChatSession(result.session.id) },
                                onDelete = { onDeleteChatSession(result.session.id) },
                            )
                        }
                    } else {
                        items(
                            items = sortedSessions,
                            key = { session -> session.id },
                        ) { session ->
                            AiHistoryRow(
                                session = session,
                                isActive = session.id == activeSessionId,
                                preview = previews[session.id],
                                onClick = { onSelectChatSession(session.id) },
                                onDelete = { onDeleteChatSession(session.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiProfileRoute(
    persona: AiPersonaUiState,
    onBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onAvatarColorIndexChange: (Int) -> Unit,
    onAvatarIconKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconOptions = remember { aiAvatarIconOptions() }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AiFullPageHeader(
                title = "CYL AI",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                AiAvatar(
                    spec = persona.toAvatarSpec(),
                    size = 84,
                    iconSize = 42,
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Name",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BasicTextField(
                        value = persona.displayName,
                        onValueChange = { value -> onDisplayNameChange(value.take(32)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(vertical = 10.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (persona.displayName.isBlank()) {
                                    Text(
                                        text = "CYL AI",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Color",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        aiAvatarColors.forEachIndexed { index, color ->
                            val selected = index == persona.avatarColorIndex
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(color)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                                        },
                                        shape = RoundedCornerShape(19.dp),
                                    )
                                    .clickable { onAvatarColorIndexChange(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Icon",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        iconOptions.forEach { option ->
                            val selected = option.key == persona.avatarIconKey
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .clickable { onAvatarIconKeyChange(option.key) },
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
                                    modifier = Modifier.size(23.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiFullPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        action()
    }
}

@Composable
private fun AiHistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank()) {
                        Text(
                            text = "Search chats",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotBlank()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear chat search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AiHistoryRow(
    session: ChatSession,
    isActive: Boolean,
    preview: ChatSessionPreview?,
    supportTextOverride: String? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val isMenuOpen = remember { mutableStateOf(false) }
    val supportText = supportTextOverride?.takeIf { it.isNotBlank() }
        ?: preview?.lastMessage?.takeIf { it.isNotBlank() }
        ?: session.updatedAt.toAiDisplayDateTime()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                ),
        )
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.44f)),
            contentAlignment = Alignment.Center,
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
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = session.title.ifBlank { "New chat" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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
                onClick = { isMenuOpen.value = true },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Chat actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = isMenuOpen.value,
                onDismissRequest = { isMenuOpen.value = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Delete chat") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        isMenuOpen.value = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun AiEmptyPageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
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
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Long.toAiDisplayDateTime(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
}
