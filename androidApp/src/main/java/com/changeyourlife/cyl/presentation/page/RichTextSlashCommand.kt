package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition

data class RichTextSlashQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

data class RichTextSlashCommand(
    val label: String,
    val hint: String,
    val action: RichTextSlashAction,
    val aliases: List<String>,
)

sealed interface RichTextSlashAction {
    data class ChangeType(val type: PageBlockType) : RichTextSlashAction
    data class InsertBlock(
        val type: PageBlockType,
        val position: PageBlockInsertPosition,
    ) : RichTextSlashAction

    data object CreateLinkedPage : RichTextSlashAction
    data object OpenPropertySheet : RichTextSlashAction
    data object IndentBlock : RichTextSlashAction
    data object OutdentBlock : RichTextSlashAction
}

object RichTextSlashCommandParser {
    val commands: List<RichTextSlashCommand> = listOf(
        RichTextSlashCommand(
            label = "Text",
            hint = "Plain block",
            action = RichTextSlashAction.ChangeType(PageBlockType.Text),
            aliases = listOf("text", "plain", "paragraph", "p"),
        ),
        RichTextSlashCommand(
            label = "Heading",
            hint = "Large title",
            action = RichTextSlashAction.ChangeType(PageBlockType.Heading),
            aliases = listOf("heading", "head", "title", "h1"),
        ),
        RichTextSlashCommand(
            label = "Todo",
            hint = "Checkbox",
            action = RichTextSlashAction.ChangeType(PageBlockType.Todo),
            aliases = listOf("todo", "task", "checkbox", "check"),
        ),
        RichTextSlashCommand(
            label = "Bullet",
            hint = "List item",
            action = RichTextSlashAction.ChangeType(PageBlockType.Bullet),
            aliases = listOf("bullet", "list", "ul"),
        ),
        RichTextSlashCommand(
            label = "Numbered",
            hint = "Ordered list",
            action = RichTextSlashAction.ChangeType(PageBlockType.Numbered),
            aliases = listOf("numbered", "number", "ordered", "ol", "numberedlist"),
        ),
        RichTextSlashCommand(
            label = "Toggle",
            hint = "Collapsible list item",
            action = RichTextSlashAction.ChangeType(PageBlockType.Toggle),
            aliases = listOf("toggle", "togglelist", "collapse"),
        ),
        RichTextSlashCommand(
            label = "Quote",
            hint = "Callout text",
            action = RichTextSlashAction.ChangeType(PageBlockType.Quote),
            aliases = listOf("quote", "blockquote"),
        ),
        RichTextSlashCommand(
            label = "Callout",
            hint = "Highlighted note",
            action = RichTextSlashAction.ChangeType(PageBlockType.Callout),
            aliases = listOf("callout", "notice", "info"),
        ),
        RichTextSlashCommand(
            label = "Code",
            hint = "Code block",
            action = RichTextSlashAction.ChangeType(PageBlockType.Code),
            aliases = listOf("code", "pre", "snippet"),
        ),
        RichTextSlashCommand(
            label = "Table",
            hint = "Simple table",
            action = RichTextSlashAction.ChangeType(PageBlockType.Table),
            aliases = listOf("table", "grid", "plain-table"),
        ),
        RichTextSlashCommand(
            label = "Bookmark",
            hint = "Web link preview",
            action = RichTextSlashAction.ChangeType(PageBlockType.WebBookmark),
            aliases = listOf("bookmark", "web", "url", "linkpreview"),
        ),
        RichTextSlashCommand(
            label = "Divider",
            hint = "Separator",
            action = RichTextSlashAction.ChangeType(PageBlockType.Divider),
            aliases = listOf("divider", "line", "hr"),
        ),
        RichTextSlashCommand(
            label = "Media",
            hint = "File or image",
            action = RichTextSlashAction.ChangeType(PageBlockType.MediaFile),
            aliases = listOf("media", "file", "image", "photo"),
        ),
        RichTextSlashCommand(
            label = "Table",
            hint = "Database table",
            action = RichTextSlashAction.ChangeType(PageBlockType.DatabaseTable),
            aliases = listOf("database", "db", "data", "datasource"),
        ),
        RichTextSlashCommand(
            label = "Page",
            hint = "Linked page",
            action = RichTextSlashAction.CreateLinkedPage,
            aliases = listOf("page", "subpage", "linkedpage"),
        ),
        RichTextSlashCommand(
            label = "Property",
            hint = "Add table property",
            action = RichTextSlashAction.OpenPropertySheet,
            aliases = listOf("property", "prop", "column", "field"),
        ),
        RichTextSlashCommand(
            label = "Indent",
            hint = "Nest under previous block",
            action = RichTextSlashAction.IndentBlock,
            aliases = listOf("indent", "nest", "tab"),
        ),
        RichTextSlashCommand(
            label = "Outdent",
            hint = "Move out one level",
            action = RichTextSlashAction.OutdentBlock,
            aliases = listOf("outdent", "unnest", "shift-tab"),
        ),
        RichTextSlashCommand(
            label = "Text above",
            hint = "Insert before",
            action = RichTextSlashAction.InsertBlock(
                type = PageBlockType.Text,
                position = PageBlockInsertPosition.Above,
            ),
            aliases = listOf("above", "insertabove", "textabove", "before"),
        ),
        RichTextSlashCommand(
            label = "Text below",
            hint = "Insert after",
            action = RichTextSlashAction.InsertBlock(
                type = PageBlockType.Text,
                position = PageBlockInsertPosition.Below,
            ),
            aliases = listOf("below", "insertbelow", "textbelow", "after", "insert"),
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
