package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType

data class RichTextSlashQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

data class RichTextSlashCommand(
    val label: String,
    val hint: String,
    val type: PageBlockType,
    val aliases: List<String>,
)

object RichTextSlashCommandParser {
    val commands: List<RichTextSlashCommand> = listOf(
        RichTextSlashCommand(
            label = "Text",
            hint = "Plain block",
            type = PageBlockType.Text,
            aliases = listOf("text", "plain", "paragraph", "p"),
        ),
        RichTextSlashCommand(
            label = "Heading",
            hint = "Large title",
            type = PageBlockType.Heading,
            aliases = listOf("heading", "head", "title", "h1"),
        ),
        RichTextSlashCommand(
            label = "Todo",
            hint = "Checkbox",
            type = PageBlockType.Todo,
            aliases = listOf("todo", "task", "checkbox", "check"),
        ),
        RichTextSlashCommand(
            label = "Bullet",
            hint = "List item",
            type = PageBlockType.Bullet,
            aliases = listOf("bullet", "list", "ul"),
        ),
        RichTextSlashCommand(
            label = "Quote",
            hint = "Callout text",
            type = PageBlockType.Quote,
            aliases = listOf("quote", "blockquote"),
        ),
        RichTextSlashCommand(
            label = "Divider",
            hint = "Separator",
            type = PageBlockType.Divider,
            aliases = listOf("divider", "line", "hr"),
        ),
        RichTextSlashCommand(
            label = "Media",
            hint = "File or image",
            type = PageBlockType.MediaFile,
            aliases = listOf("media", "file", "image", "photo"),
        ),
        RichTextSlashCommand(
            label = "Table",
            hint = "Database table",
            type = PageBlockType.DatabaseTable,
            aliases = listOf("table", "database", "db"),
        ),
    )

    fun activeQuery(
        text: String,
        cursor: Int,
    ): RichTextSlashQuery? {
        if (cursor !in 1..text.length) return null
        val slashIndex = text.lastIndexOf('/', startIndex = cursor - 1)
        if (slashIndex < 0) return null
        val prefix = text.substring(0, slashIndex)
        if (prefix.isNotEmpty() && !prefix.last().isWhitespace()) return null

        val query = text.substring(slashIndex + 1, cursor)
        if (query.any { it.isWhitespace() }) return null
        return RichTextSlashQuery(
            start = slashIndex,
            end = cursor,
            query = query,
        )
    }

    fun matchingCommands(query: String): List<RichTextSlashCommand> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return commands
        return commands.filter { command ->
            command.aliases.any { alias -> alias.startsWith(normalized) } ||
                command.label.lowercase().startsWith(normalized)
        }
    }
}
