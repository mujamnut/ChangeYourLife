package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType

internal val PageBlockType.label: String
    get() = when (this) {
        PageBlockType.Text -> "Text"
        PageBlockType.Heading -> "Heading"
        PageBlockType.Todo -> "Todo"
        PageBlockType.Bullet -> "Bullet"
        PageBlockType.Numbered -> "Numbered"
        PageBlockType.Quote -> "Quote"
        PageBlockType.Divider -> "Divider"
        PageBlockType.MediaFile -> "Media/file"
        PageBlockType.DatabaseTable -> "Table"
    }

internal val PageBlockType.placeholder: String
    get() = when (this) {
        PageBlockType.Text -> "Write something"
        PageBlockType.Heading -> "Heading"
        PageBlockType.Todo -> "Todo item"
        PageBlockType.Bullet -> "Bullet item"
        PageBlockType.Numbered -> "Numbered item"
        PageBlockType.Quote -> "Quote"
        PageBlockType.Divider -> ""
        PageBlockType.MediaFile -> "Caption"
        PageBlockType.DatabaseTable -> ""
    }

internal val PageBlockType.isPlainEditorBlock: Boolean
    get() = when (this) {
        PageBlockType.Text,
        PageBlockType.Heading,
        PageBlockType.Todo,
        PageBlockType.Bullet,
        PageBlockType.Numbered,
        PageBlockType.Quote,
        PageBlockType.Divider,
        PageBlockType.MediaFile,
        -> true
        PageBlockType.DatabaseTable -> false
    }

internal data class BlockTypeOption(
    val type: PageBlockType,
    val label: String,
) {
    companion object {
        val entries = listOf(
            BlockTypeOption(PageBlockType.Text, "Text"),
            BlockTypeOption(PageBlockType.Heading, "Heading"),
            BlockTypeOption(PageBlockType.Todo, "Todo"),
            BlockTypeOption(PageBlockType.Bullet, "Bullet"),
            BlockTypeOption(PageBlockType.Numbered, "Numbered"),
            BlockTypeOption(PageBlockType.Quote, "Quote"),
            BlockTypeOption(PageBlockType.Divider, "Divider"),
            BlockTypeOption(PageBlockType.MediaFile, "Media/file"),
            BlockTypeOption(PageBlockType.DatabaseTable, "Table"),
        )
    }
}
