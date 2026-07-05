package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockType

internal data class EditorBlockFocusRequest(
    val blockId: String,
    val token: Long,
)

internal fun List<PageBlock>.editorFocusTargetAfterDeleting(deletedBlockId: String): String? {
    val entries = toEditorFocusEntries()
    val deletedIndex = entries.indexOfFirst { entry -> entry.blockId == deletedBlockId }
    if (deletedIndex < 0) return null

    return entries
        .take(deletedIndex)
        .lastOrNull { entry -> entry.isFocusable }
        ?.blockId
        ?: entries
            .drop(deletedIndex + 1)
            .firstOrNull { entry -> entry.isFocusable }
            ?.blockId
}

internal fun List<PageBlock>.containsFocusableEditorBlock(blockId: String): Boolean {
    return toEditorFocusEntries().any { entry ->
        entry.blockId == blockId && entry.isFocusable
    }
}

internal fun List<PageBlock>.containsEditorBlock(blockId: String): Boolean {
    return any { block ->
        block.id == blockId || block.children.containsEditorBlock(blockId)
    }
}

internal fun List<PageBlock>.firstFocusableEditorBlockId(): String? {
    return toEditorFocusEntries()
        .firstOrNull { entry -> entry.isFocusable }
        ?.blockId
}

internal fun List<PageBlock>.containsDatabaseTableBlock(): Boolean {
    return any { block ->
        block.type == PageBlockType.DatabaseTable || block.children.containsDatabaseTableBlock()
    }
}

private data class EditorFocusEntry(
    val blockId: String,
    val isFocusable: Boolean,
)

private fun List<PageBlock>.toEditorFocusEntries(): List<EditorFocusEntry> {
    return flatMap { block ->
        buildList {
            add(EditorFocusEntry(block.id, block.type.isTextEditorFocusable))
            addAll(block.children.toEditorFocusEntries())
        }
    }
}

private val PageBlockType.isTextEditorFocusable: Boolean
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
        PageBlockType.MediaFile,
        -> true
        PageBlockType.Divider,
        PageBlockType.Table,
        PageBlockType.DatabaseTable,
        -> false
    }
