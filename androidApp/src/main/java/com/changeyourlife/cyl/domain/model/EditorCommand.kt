package com.changeyourlife.cyl.domain.model

sealed interface EditorCommand {
    data class UpdateBlockText(
        val blockId: String,
        val text: String,
        val richTextSpans: List<PageTextSpan> = emptyList(),
    ) : EditorCommand

    data class ChangeBlockType(
        val blockId: String,
        val type: PageBlockType,
    ) : EditorCommand

    data class ToggleTodo(
        val blockId: String,
        val isChecked: Boolean? = null,
    ) : EditorCommand

    data class InsertBlock(
        val block: PageBlock,
        val parentBlockId: String? = null,
        val index: Int? = null,
    ) : EditorCommand

    data class DeleteBlock(
        val blockId: String,
    ) : EditorCommand

    data class MoveBlock(
        val blockId: String,
        val direction: Int,
    ) : EditorCommand

    data class MoveBlockToParent(
        val blockId: String,
        val parentBlockId: String? = null,
        val index: Int? = null,
    ) : EditorCommand

    data class ReplaceBlockMediaAttachments(
        val blockId: String,
        val mediaAttachments: List<PageMediaAttachment>,
    ) : EditorCommand

    data class ReplaceTable(
        val blockId: String,
        val table: PageTable,
    ) : EditorCommand

    data class InsertProperty(
        val property: PageProperty,
        val index: Int? = null,
    ) : EditorCommand

    data class ReplaceProperty(
        val property: PageProperty,
    ) : EditorCommand

    data class DeleteProperty(
        val propertyId: String,
    ) : EditorCommand
}

data class EditorCommandResult(
    val document: PageBlockDocument,
    val undoCommand: EditorCommand? = null,
    val changed: Boolean = false,
)

object EditorCommandExecutor {
    fun apply(
        document: PageBlockDocument,
        command: EditorCommand,
    ): EditorCommandResult {
        return when (command) {
            is EditorCommand.UpdateBlockText -> document.updateBlockText(command)
            is EditorCommand.ChangeBlockType -> document.changeBlockType(command)
            is EditorCommand.ToggleTodo -> document.toggleTodo(command)
            is EditorCommand.InsertBlock -> document.insertBlock(command)
            is EditorCommand.DeleteBlock -> document.deleteBlock(command)
            is EditorCommand.MoveBlock -> document.moveBlock(command)
            is EditorCommand.MoveBlockToParent -> document.moveBlockToParent(command)
            is EditorCommand.ReplaceBlockMediaAttachments -> document.replaceBlockMediaAttachments(command)
            is EditorCommand.ReplaceTable -> document.replaceTable(command)
            is EditorCommand.InsertProperty -> document.insertProperty(command)
            is EditorCommand.ReplaceProperty -> document.replaceProperty(command)
            is EditorCommand.DeleteProperty -> document.deleteProperty(command)
        }
    }

    private fun PageBlockDocument.updateBlockText(
        command: EditorCommand.UpdateBlockText,
    ): EditorCommandResult {
        val existing = blocks.findBlock(command.blockId)?.block
            ?: return unchanged()
        val nextSpans = RichTextSpanEngine.normalize(command.richTextSpans, command.text)
        if (existing.text == command.text && existing.richTextSpans == nextSpans) {
            return unchanged()
        }

        val updated = copy(
            blocks = blocks.updateBlock(command.blockId) {
                it.copy(text = command.text, richTextSpans = nextSpans)
            },
        )
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.UpdateBlockText(
                blockId = command.blockId,
                text = existing.text,
                richTextSpans = existing.richTextSpans,
            ),
            changed = true,
        )
    }

    private fun PageBlockDocument.changeBlockType(
        command: EditorCommand.ChangeBlockType,
    ): EditorCommandResult {
        val existing = blocks.findBlock(command.blockId)?.block
            ?: return unchanged()
        if (existing.type == command.type) return unchanged()

        val updated = copy(
            blocks = blocks.updateBlock(command.blockId) {
                it.copy(type = command.type)
            },
        )
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.ChangeBlockType(
                blockId = command.blockId,
                type = existing.type,
            ),
            changed = true,
        )
    }

    private fun PageBlockDocument.toggleTodo(
        command: EditorCommand.ToggleTodo,
    ): EditorCommandResult {
        val existing = blocks.findBlock(command.blockId)?.block
            ?: return unchanged()
        val nextChecked = command.isChecked ?: !existing.isChecked
        if (existing.isChecked == nextChecked) return unchanged()

        val updated = copy(
            blocks = blocks.updateBlock(command.blockId) {
                it.copy(isChecked = nextChecked)
            },
        )
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.ToggleTodo(
                blockId = command.blockId,
                isChecked = existing.isChecked,
            ),
            changed = true,
        )
    }

    private fun PageBlockDocument.insertBlock(
        command: EditorCommand.InsertBlock,
    ): EditorCommandResult {
        if (blocks.findBlock(command.block.id) != null) return unchanged()
        if (command.parentBlockId != null && blocks.findBlock(command.parentBlockId) == null) {
            return unchanged()
        }

        val updated = copy(
            blocks = blocks.insertBlock(
                parentBlockId = command.parentBlockId,
                index = command.index,
                block = command.block,
            ),
        )
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.DeleteBlock(command.block.id),
            changed = updated != this,
        )
    }

    private fun PageBlockDocument.deleteBlock(
        command: EditorCommand.DeleteBlock,
    ): EditorCommandResult {
        val existing = blocks.findBlock(command.blockId)
            ?: return unchanged()
        val updated = copy(blocks = blocks.deleteBlock(command.blockId))
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.InsertBlock(
                block = existing.block,
                parentBlockId = existing.parentBlockId,
                index = existing.index,
            ),
            changed = true,
        )
    }

    private fun PageBlockDocument.moveBlock(
        command: EditorCommand.MoveBlock,
    ): EditorCommandResult {
        if (command.direction == 0) return unchanged()
        val result = blocks.moveBlock(command.blockId, command.direction)
        if (!result.changed) return unchanged()
        return EditorCommandResult(
            document = copy(blocks = result.blocks),
            undoCommand = EditorCommand.MoveBlock(
                blockId = command.blockId,
                direction = -command.direction,
            ),
            changed = true,
        )
    }

    private fun PageBlockDocument.moveBlockToParent(
        command: EditorCommand.MoveBlockToParent,
    ): EditorCommandResult {
        val existing = blocks.findBlock(command.blockId)
            ?: return unchanged()
        val targetParentId = command.parentBlockId?.takeIf(String::isNotBlank)
        if (targetParentId == command.blockId) return unchanged()
        if (targetParentId != null && blocks.findBlock(targetParentId) == null) {
            return unchanged()
        }
        if (existing.block.containsDescendant(targetParentId)) {
            return unchanged()
        }

        val targetIndex = command.index
            ?.let { index ->
                if (existing.parentBlockId == targetParentId && index > existing.index) index - 1 else index
            }
        val movedBlocks = blocks
            .deleteBlock(command.blockId)
            .insertBlock(
                parentBlockId = targetParentId,
                index = targetIndex,
                block = existing.block,
            )
        if (movedBlocks == blocks) return unchanged()

        return EditorCommandResult(
            document = copy(blocks = movedBlocks),
            undoCommand = EditorCommand.MoveBlockToParent(
                blockId = command.blockId,
                parentBlockId = existing.parentBlockId,
                index = existing.index,
            ),
            changed = true,
        )
    }

    private fun PageBlockDocument.replaceBlockMediaAttachments(
        command: EditorCommand.ReplaceBlockMediaAttachments,
    ): EditorCommandResult {
        val existing = blocks.findBlock(command.blockId)?.block
            ?: return unchanged()
        if (existing.mediaAttachments == command.mediaAttachments) return unchanged()

        val updated = copy(
            blocks = blocks.updateBlock(command.blockId) {
                it.copy(mediaAttachments = command.mediaAttachments)
            },
        )
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.ReplaceBlockMediaAttachments(
                blockId = command.blockId,
                mediaAttachments = existing.mediaAttachments,
            ),
            changed = true,
        )
    }

    private fun PageBlockDocument.replaceTable(
        command: EditorCommand.ReplaceTable,
    ): EditorCommandResult {
        val existing = blocks.findBlock(command.blockId)?.block
            ?: return unchanged()
        if (existing.type != PageBlockType.DatabaseTable && existing.type != PageBlockType.Table) return unchanged()
        if (existing.table == command.table) return unchanged()

        val updated = copy(
            blocks = blocks.updateBlock(command.blockId) {
                it.copy(table = command.table)
            },
        )
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.ReplaceTable(
                blockId = command.blockId,
                table = existing.table,
            ),
            changed = true,
        )
    }

    private fun PageBlockDocument.insertProperty(
        command: EditorCommand.InsertProperty,
    ): EditorCommandResult {
        if (properties.any { property -> property.id == command.property.id }) {
            return unchanged()
        }
        val index = command.index?.coerceIn(0, properties.size) ?: properties.size
        val updated = copy(
            properties = properties.take(index) + command.property + properties.drop(index),
        )
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.DeleteProperty(command.property.id),
            changed = true,
        )
    }

    private fun PageBlockDocument.replaceProperty(
        command: EditorCommand.ReplaceProperty,
    ): EditorCommandResult {
        val existing = properties.firstOrNull { property -> property.id == command.property.id }
            ?: return unchanged()
        if (existing == command.property) return unchanged()

        val updated = copy(
            properties = properties.map { property ->
                if (property.id == command.property.id) command.property else property
            },
        )
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.ReplaceProperty(existing),
            changed = true,
        )
    }

    private fun PageBlockDocument.deleteProperty(
        command: EditorCommand.DeleteProperty,
    ): EditorCommandResult {
        val index = properties.indexOfFirst { property -> property.id == command.propertyId }
        if (index == -1) return unchanged()

        val existing = properties[index]
        val updated = copy(properties = properties.filterNot { property -> property.id == command.propertyId })
        return EditorCommandResult(
            document = updated,
            undoCommand = EditorCommand.InsertProperty(
                property = existing,
                index = index,
            ),
            changed = true,
        )
    }

    private fun PageBlockDocument.unchanged(): EditorCommandResult {
        return EditorCommandResult(document = this)
    }

    private fun List<PageBlock>.updateBlock(
        blockId: String,
        transform: (PageBlock) -> PageBlock,
    ): List<PageBlock> {
        return map { block ->
            when {
                block.id == blockId -> transform(block)
                block.children.isNotEmpty() -> block.copy(
                    children = block.children.updateBlock(blockId, transform),
                )
                else -> block
            }
        }
    }

    private fun List<PageBlock>.insertBlock(
        parentBlockId: String?,
        index: Int?,
        block: PageBlock,
    ): List<PageBlock> {
        if (parentBlockId == null) return insertAt(index, block)
        return map { existing ->
            if (existing.id == parentBlockId) {
                existing.copy(children = existing.children.insertAt(index, block))
            } else if (existing.children.isNotEmpty()) {
                existing.copy(
                    children = existing.children.insertBlock(parentBlockId, index, block),
                )
            } else {
                existing
            }
        }
    }

    private fun List<PageBlock>.insertAt(
        index: Int?,
        block: PageBlock,
    ): List<PageBlock> {
        val targetIndex = index?.coerceIn(0, size) ?: size
        return take(targetIndex) + block + drop(targetIndex)
    }

    private fun List<PageBlock>.deleteBlock(blockId: String): List<PageBlock> {
        return mapNotNull { block ->
            when {
                block.id == blockId -> null
                block.children.isNotEmpty() -> block.copy(
                    children = block.children.deleteBlock(blockId),
                )
                else -> block
            }
        }
    }

    private fun List<PageBlock>.moveBlock(
        blockId: String,
        direction: Int,
    ): MoveBlockResult {
        val currentIndex = indexOfFirst { block -> block.id == blockId }
        if (currentIndex >= 0) {
            val targetIndex = (currentIndex + direction).coerceIn(0, lastIndex)
            if (targetIndex == currentIndex) return MoveBlockResult(this)
            val moved = toMutableList()
            val block = moved.removeAt(currentIndex)
            moved.add(targetIndex, block)
            return MoveBlockResult(blocks = moved, changed = true)
        }

        var didMove = false
        val updated = map { block ->
            if (didMove || block.children.isEmpty()) {
                block
            } else {
                val childResult = block.children.moveBlock(blockId, direction)
                if (childResult.changed) {
                    didMove = true
                    block.copy(children = childResult.blocks)
                } else {
                    block
                }
            }
        }
        return MoveBlockResult(blocks = updated, changed = didMove)
    }

    private fun List<PageBlock>.findBlock(
        blockId: String,
        parentBlockId: String? = null,
    ): LocatedBlock? {
        forEachIndexed { index, block ->
            if (block.id == blockId) {
                return LocatedBlock(
                    block = block,
                    parentBlockId = parentBlockId,
                    index = index,
                )
            }
            val childResult = block.children.findBlock(
                blockId = blockId,
                parentBlockId = block.id,
            )
            if (childResult != null) return childResult
        }
        return null
    }

    private data class LocatedBlock(
        val block: PageBlock,
        val parentBlockId: String?,
        val index: Int,
    )

    private data class MoveBlockResult(
        val blocks: List<PageBlock>,
        val changed: Boolean = false,
    )

    private fun PageBlock.containsDescendant(blockId: String?): Boolean {
        if (blockId == null) return false
        return children.any { child ->
            child.id == blockId || child.containsDescendant(blockId)
        }
    }
}
