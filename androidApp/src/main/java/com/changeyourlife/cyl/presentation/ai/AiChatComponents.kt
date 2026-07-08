package com.changeyourlife.cyl.presentation.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.AiActionUndoState
import com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteItem

internal data class AiMentionChipUi(
    val pageId: String,
    val title: String,
    val canRemove: Boolean,
)

@Composable
internal fun AiKeyboardReplacementPanel(
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
internal fun AiSheetHeader(
    displayName: String,
    avatarSpec: AiAvatarSpec,
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
internal fun AiComposerCard(
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
internal fun AiAttachmentPanel(
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
internal fun AiSettingsPanel(
    modelLabel: String,
    visionStatusLabel: String,
    visionPipelineLabel: String,
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
        if (visionStatusLabel.isNotBlank()) {
            AiSettingRow(
                icon = Icons.Rounded.Photo,
                label = "Vision",
                value = visionStatusLabel.compactAiModelLabel(),
            )
        }
        if (visionPipelineLabel.isNotBlank()) {
            AiSettingRow(
                icon = Icons.Rounded.Tune,
                label = "Image pipeline",
                value = visionPipelineLabel,
            )
        }
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
            value = "Profile page",
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
internal fun AiChatActionDetails(
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

private fun String.prettyActionType(): String {
    return lowercase()
        .split('_')
        .filter { part -> part.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { char -> char.titlecase() } }
}
