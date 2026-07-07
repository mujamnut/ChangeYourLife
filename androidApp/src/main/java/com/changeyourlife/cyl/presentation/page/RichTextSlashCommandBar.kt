package com.changeyourlife.cyl.presentation.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.Page

enum class RichTextCommandPaletteKind {
    Slash,
    Mention,
}

data class RichTextCommandPaletteItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val leading: String,
    val kind: RichTextCommandPaletteKind,
    val groupLabel: String = "",
)

fun richTextSlashPaletteItems(commands: List<RichTextSlashCommand>): List<RichTextCommandPaletteItem> {
    return commands.map { command ->
        RichTextCommandPaletteItem(
            id = command.paletteItemId(),
            title = command.label,
            subtitle = command.hint,
            leading = "/",
            kind = RichTextCommandPaletteKind.Slash,
            groupLabel = EditorCommandRegistry.groupFor(command).label,
        )
    }
}

fun editorSlashPaletteItems(entries: List<EditorCommandRegistryEntry>): List<RichTextCommandPaletteItem> {
    return entries.map { entry ->
        RichTextCommandPaletteItem(
            id = entry.command.paletteItemId(),
            title = entry.command.label,
            subtitle = entry.command.hint,
            leading = "/",
            kind = RichTextCommandPaletteKind.Slash,
            groupLabel = entry.group.label,
        )
    }
}

fun richTextMentionPaletteItems(pages: List<Page>): List<RichTextCommandPaletteItem> {
    return pages.map { page ->
        RichTextCommandPaletteItem(
            id = page.paletteItemId(),
            title = "@${page.title.ifBlank { "Untitled page" }}",
            subtitle = "Mention page",
            leading = "@",
            kind = RichTextCommandPaletteKind.Mention,
            groupLabel = EditorCommandGroup.Page.label,
        )
    }
}

fun RichTextSlashCommand.paletteItemId(): String {
    return when (val commandAction = action) {
        is RichTextSlashAction.ChangeType -> "slash:type:${commandAction.type.name.lowercase()}"
        is RichTextSlashAction.InsertBlock -> {
            "slash:insert:${commandAction.type.name.lowercase()}:${commandAction.position.name.lowercase()}"
        }
        RichTextSlashAction.CreateLinkedPage -> "slash:create_linked_page"
        RichTextSlashAction.OpenPropertySheet -> "slash:open_property_sheet"
        RichTextSlashAction.IndentBlock -> "slash:indent"
        RichTextSlashAction.OutdentBlock -> "slash:outdent"
    }
}

fun Page.paletteItemId(): String {
    return "mention:$id"
}

@Composable
fun RichTextCommandPalette(
    items: List<RichTextCommandPaletteItem>,
    onSelect: (RichTextCommandPaletteItem) -> Unit,
    modifier: Modifier = Modifier,
    selectedItemId: String? = null,
) {
    if (items.isEmpty()) return
    val groupedItems = items.groupBy { item -> item.groupLabel }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            groupedItems
                .forEach { (groupLabel, groupItems) ->
                    if (groupLabel.isNotBlank()) {
                        this.item(key = "group:$groupLabel") {
                            Text(
                                text = groupLabel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    this.items(
                        items = groupItems,
                        key = { item -> item.id },
                    ) { item ->
                        val isSelected = item.id == selectedItemId
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = item.title,
                                    maxLines = 1,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = item.subtitle,
                                    maxLines = 1,
                                )
                            },
                            leadingContent = {
                                Text(
                                    text = item.leading,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) },
                            trailingContent = null,
                            overlineContent = null,
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ),
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                        )
                    }
                }
        }
    }
}

@Composable
fun RichTextSlashCommandBar(
    commands: List<RichTextSlashCommand>,
    onSelect: (RichTextSlashCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    RichTextCommandPalette(
        items = richTextSlashPaletteItems(commands),
        onSelect = { item ->
            commands
                .firstOrNull { command -> command.paletteItemId() == item.id }
                ?.let(onSelect)
        },
        modifier = modifier,
    )
}
