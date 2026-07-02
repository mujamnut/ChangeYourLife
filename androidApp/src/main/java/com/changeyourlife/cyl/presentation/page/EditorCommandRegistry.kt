package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.RichTextFormat

data class EditorCommandContext(
    val isCodeBlock: Boolean = false,
    val canEditTableProperties: Boolean = true,
)

enum class EditorCommandGroup(val label: String) {
    Basic("Basic"),
    Lists("Lists"),
    Insert("Insert"),
    Media("Media"),
    Database("Database"),
    Page("Page"),
}

data class EditorCommandRegistryEntry(
    val command: RichTextSlashCommand,
    val group: EditorCommandGroup,
    val isEnabled: (EditorCommandContext) -> Boolean = { true },
)

sealed interface RichTextToolbarRegistryAction {
    data class ToggleFormat(val format: RichTextFormat) : RichTextToolbarRegistryAction
    data object Link : RichTextToolbarRegistryAction
    data class ApplyColor(val colorHex: String) : RichTextToolbarRegistryAction
    data class ApplyHighlight(val colorHex: String) : RichTextToolbarRegistryAction
}

data class RichTextToolbarRegistryEntry(
    val id: String,
    val label: String,
    val action: RichTextToolbarRegistryAction,
)

object EditorCommandRegistry {
    private val slashEntries: List<EditorCommandRegistryEntry> = RichTextSlashCommandParser.commands.map { command ->
        EditorCommandRegistryEntry(
            command = command,
            group = command.defaultGroup(),
            isEnabled = command.defaultEnablement(),
        )
    }
    private val toolbarEntries: List<RichTextToolbarRegistryEntry> =
        RichTextFormat.entries.map { format ->
            RichTextToolbarRegistryEntry(
                id = "format:${format.name}",
                label = format.toolbarLabel,
                action = RichTextToolbarRegistryAction.ToggleFormat(format),
            )
        } + listOf(
            RichTextToolbarRegistryEntry(
                id = "link",
                label = "Link",
                action = RichTextToolbarRegistryAction.Link,
            ),
            RichTextToolbarRegistryEntry(
                id = "color:blue",
                label = "Blue",
                action = RichTextToolbarRegistryAction.ApplyColor("#1565C0"),
            ),
            RichTextToolbarRegistryEntry(
                id = "highlight:yellow",
                label = "Highlight",
                action = RichTextToolbarRegistryAction.ApplyHighlight("#FFF59D"),
            ),
        )

    fun matchingSlashCommands(
        query: String,
        context: EditorCommandContext = EditorCommandContext(),
    ): List<EditorCommandRegistryEntry> {
        val normalized = query.trim().lowercase()
        return slashEntries
            .filter { entry -> entry.isEnabled(context) }
            .filter { entry ->
                if (normalized.isBlank()) {
                    true
                } else {
                    entry.command.aliases.any { alias -> alias.startsWith(normalized) } ||
                        entry.command.label.lowercase().startsWith(normalized)
                }
            }
    }

    fun changeTypeEntries(
        context: EditorCommandContext = EditorCommandContext(),
    ): List<EditorCommandRegistryEntry> {
        return slashEntries
            .enabledIn(context)
            .filter { entry -> entry.command.action is RichTextSlashAction.ChangeType }
    }

    fun insertBlockEntries(
        position: PageBlockInsertPosition? = null,
        context: EditorCommandContext = EditorCommandContext(),
    ): List<EditorCommandRegistryEntry> {
        return slashEntries
            .enabledIn(context)
            .filter { entry ->
                val action = entry.command.action
                action is RichTextSlashAction.InsertBlock && (position == null || action.position == position)
            }
    }

    fun entryForPaletteItemId(id: String): EditorCommandRegistryEntry? {
        return slashEntries.firstOrNull { entry -> entry.command.paletteItemId() == id }
    }

    fun groupFor(command: RichTextSlashCommand): EditorCommandGroup {
        return slashEntries
            .firstOrNull { entry -> entry.command.paletteItemId() == command.paletteItemId() }
            ?.group
            ?: command.defaultGroup()
    }

    fun richTextToolbarEntries(): List<RichTextToolbarRegistryEntry> = toolbarEntries
}

fun EditorCommandRegistryEntry.changeTypeOrNull(): PageBlockType? {
    return (command.action as? RichTextSlashAction.ChangeType)?.type
}

fun EditorCommandRegistryEntry.insertBlockActionOrNull(): RichTextSlashAction.InsertBlock? {
    return command.action as? RichTextSlashAction.InsertBlock
}

private fun List<EditorCommandRegistryEntry>.enabledIn(
    context: EditorCommandContext,
): List<EditorCommandRegistryEntry> {
    return filter { entry -> entry.isEnabled(context) }
}

private fun RichTextSlashCommand.defaultGroup(): EditorCommandGroup {
    return when (action) {
        is RichTextSlashAction.ChangeType -> when (label) {
            "Bullet", "Numbered", "Todo" -> EditorCommandGroup.Lists
            "Media" -> EditorCommandGroup.Media
            "Database" -> EditorCommandGroup.Database
            else -> EditorCommandGroup.Basic
        }
        is RichTextSlashAction.InsertBlock -> if (label == "Database") {
            EditorCommandGroup.Database
        } else {
            EditorCommandGroup.Insert
        }
        RichTextSlashAction.CreateLinkedPage -> EditorCommandGroup.Page
        RichTextSlashAction.OpenPropertySheet -> EditorCommandGroup.Database
        RichTextSlashAction.IndentBlock,
        RichTextSlashAction.OutdentBlock,
        -> EditorCommandGroup.Lists
    }
}

private fun RichTextSlashCommand.defaultEnablement(): (EditorCommandContext) -> Boolean {
    return when (action) {
        RichTextSlashAction.OpenPropertySheet -> { context -> !context.isCodeBlock && context.canEditTableProperties }
        RichTextSlashAction.IndentBlock,
        RichTextSlashAction.OutdentBlock,
        -> { context -> !context.isCodeBlock }
        else -> { context -> !context.isCodeBlock }
    }
}

private val RichTextFormat.toolbarLabel: String
    get() = when (this) {
        RichTextFormat.Bold -> "B"
        RichTextFormat.Italic -> "I"
        RichTextFormat.Underline -> "U"
        RichTextFormat.Strikethrough -> "S"
        RichTextFormat.Code -> "<>"
    }
