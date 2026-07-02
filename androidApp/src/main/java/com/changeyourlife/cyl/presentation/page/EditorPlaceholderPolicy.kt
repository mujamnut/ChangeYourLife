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
            context.isTableRowPage && context.type == PageBlockType.Text -> {
                "Add row notes"
            }
            context.type == PageBlockType.Text -> {
                ""
            }
            context.type == PageBlockType.Heading -> ""
            context.type == PageBlockType.Todo -> ""
            context.type == PageBlockType.Bullet -> ""
            context.type == PageBlockType.Numbered -> ""
            context.type == PageBlockType.Quote -> ""
            context.type == PageBlockType.MediaFile -> "Caption"
            context.type == PageBlockType.Divider || context.type == PageBlockType.DatabaseTable -> ""
            else -> ""
        }
    }
}
