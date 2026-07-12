package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockType

internal fun PageBlock.editorLazyContentType(): String {
    return when (type) {
        PageBlockType.DatabaseTable -> "block:database"
        PageBlockType.Table -> "block:table"
        PageBlockType.MediaFile -> "block:media"
        PageBlockType.Divider -> "block:divider"
        PageBlockType.Todo -> "block:todo"
        PageBlockType.Bullet -> "block:bullet"
        PageBlockType.Numbered -> "block:numbered"
        PageBlockType.Toggle -> "block:toggle"
        PageBlockType.Quote -> "block:quote"
        PageBlockType.Callout -> "block:callout"
        PageBlockType.Heading -> "block:heading"
        PageBlockType.Code -> "block:code"
        PageBlockType.Text -> "block:text"
        PageBlockType.WebBookmark -> "block:webbookmark"
    }
}
