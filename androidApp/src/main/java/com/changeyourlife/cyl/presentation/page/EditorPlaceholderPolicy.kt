package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType

data class EditorPlaceholderContext(
    val type: PageBlockType,
    val isFirstBlock: Boolean = false,
    val isFocused: Boolean = false,
    val isTableRowPage: Boolean = false,
    val isMediaCaption: Boolean = false,
)

object EditorPlaceholderPolicy {
    fun placeholderFor(context: EditorPlaceholderContext): String {
        return when {
            context.isMediaCaption -> "Caption"
            context.isFirstBlock && context.type == PageBlockType.Text -> {
                "Write, type / for blocks, or ask AI"
            }
            context.isTableRowPage && context.type == PageBlockType.Text -> {
                "Add row notes"
            }
            context.type == PageBlockType.Text -> {
                if (context.isFocused) "Type / for blocks or @ to mention" else "Write something"
            }
            context.type == PageBlockType.Heading -> "Heading"
            context.type == PageBlockType.Todo -> "To-do"
            context.type == PageBlockType.Bullet -> "List item"
            context.type == PageBlockType.Numbered -> "Numbered item"
            context.type == PageBlockType.Quote -> "Quote"
            context.type == PageBlockType.MediaFile -> "Caption"
            context.type == PageBlockType.Divider || context.type == PageBlockType.DatabaseTable -> ""
            else -> ""
        }
    }
}
