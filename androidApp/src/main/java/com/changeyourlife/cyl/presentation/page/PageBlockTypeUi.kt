package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType

internal val PageBlockType.label: String
    get() = when (this) {
        PageBlockType.Text -> "Text"
        PageBlockType.Heading -> "Heading"
        PageBlockType.Todo -> "Todo"
        PageBlockType.Bullet -> "Bullet"
        PageBlockType.Numbered -> "Numbered"
        PageBlockType.Toggle -> "Toggle"
        PageBlockType.Quote -> "Quote"
        PageBlockType.Callout -> "Callout"
        PageBlockType.Code -> "Code"
        PageBlockType.Table -> "Table"
        PageBlockType.WebBookmark -> "Bookmark"
        PageBlockType.Divider -> "Divider"
        PageBlockType.MediaFile -> "Media/file"
        PageBlockType.DatabaseTable -> "Database"
    }

internal val PageBlockType.placeholder: String
    get() = when (this) {
        PageBlockType.Text -> "Write something"
        PageBlockType.Heading -> "Heading"
        PageBlockType.Todo -> "Todo item"
        PageBlockType.Bullet -> "Bullet item"
        PageBlockType.Numbered -> "Numbered item"
        PageBlockType.Toggle -> "Toggle item"
        PageBlockType.Quote -> "Quote"
        PageBlockType.Callout -> "Callout"
        PageBlockType.Code -> "Code"
        PageBlockType.Table -> ""
        PageBlockType.WebBookmark -> "Paste or write a link"
        PageBlockType.Divider -> ""
        PageBlockType.MediaFile -> "Caption"
        PageBlockType.DatabaseTable -> ""
    }

internal val PageBlockType.isTextLikeEditorBlock: Boolean
    get() = when (this) {
        PageBlockType.Text,
        PageBlockType.Heading,
        PageBlockType.Todo,
        PageBlockType.Bullet,
        PageBlockType.Numbered,
        PageBlockType.Toggle,
        PageBlockType.Quote,
        PageBlockType.Callout,
        PageBlockType.Code,
        PageBlockType.WebBookmark,
        -> true
        PageBlockType.Divider,
        PageBlockType.Table,
        PageBlockType.MediaFile,
        PageBlockType.DatabaseTable,
        -> false
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
            BlockTypeOption(PageBlockType.Toggle, "Toggle"),
            BlockTypeOption(PageBlockType.Quote, "Quote"),
            BlockTypeOption(PageBlockType.Callout, "Callout"),
            BlockTypeOption(PageBlockType.Code, "Code"),
            BlockTypeOption(PageBlockType.Table, "Table"),
            BlockTypeOption(PageBlockType.WebBookmark, "Bookmark"),
            BlockTypeOption(PageBlockType.Divider, "Divider"),
            BlockTypeOption(PageBlockType.MediaFile, "Media/file"),
            BlockTypeOption(PageBlockType.DatabaseTable, "Database"),
        )
    }
}
