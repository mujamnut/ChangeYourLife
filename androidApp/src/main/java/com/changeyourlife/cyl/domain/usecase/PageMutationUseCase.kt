package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTextSpan

class PageMutationUseCase(
    private val applyEditorCommandUseCase: ApplyEditorCommandUseCase,
) {
    fun updateBlockText(
        document: PageBlockDocument,
        blockId: String,
        text: String,
    ): PageMutationResult {
        val richTextSpans = document.findBlock(blockId)?.richTextSpans.orEmpty()
        return apply(
            document = document,
            command = EditorCommand.UpdateBlockText(
                blockId = blockId,
                text = text,
                richTextSpans = richTextSpans,
            ),
        )
    }

    fun updateBlockRichText(
        document: PageBlockDocument,
        blockId: String,
        text: String,
        spans: List<PageTextSpan>,
    ): PageMutationResult {
        return apply(
            document = document,
            command = EditorCommand.UpdateBlockText(
                blockId = blockId,
                text = text,
                richTextSpans = spans,
            ),
        )
    }

    fun changeBlockType(
        document: PageBlockDocument,
        blockId: String,
        type: PageBlockType,
    ): PageMutationResult {
        return apply(
            document = document,
            command = EditorCommand.ChangeBlockType(blockId, type),
        )
    }

    fun addBlockMediaAttachments(
        document: PageBlockDocument,
        blockId: String,
        attachments: List<PageMediaAttachment>,
    ): PageMutationResult {
        if (attachments.isEmpty()) return document.unchangedResult()
        val block = document.findBlock(blockId) ?: return document.unchangedResult()
        return replaceBlockMediaAttachments(
            document = document,
            blockId = blockId,
            attachments = block.mediaAttachments + attachments,
        )
    }

    fun removeBlockMediaAttachment(
        document: PageBlockDocument,
        blockId: String,
        attachmentId: String,
    ): PageMutationResult {
        val block = document.findBlock(blockId) ?: return document.unchangedResult()
        return replaceBlockMediaAttachments(
            document = document,
            blockId = blockId,
            attachments = block.mediaAttachments.filterNot { attachment -> attachment.id == attachmentId },
        )
    }

    fun toggleTodoBlock(
        document: PageBlockDocument,
        blockId: String,
    ): PageMutationResult {
        return apply(
            document = document,
            command = EditorCommand.ToggleTodo(blockId),
        )
    }

    fun addBlock(
        document: PageBlockDocument,
        type: PageBlockType,
        parentBlockId: String? = null,
    ): BlockMutationResult {
        val block = PageContentCodec.newBlock(type)
        val command = EditorCommand.InsertBlock(
            block = block,
            parentBlockId = parentBlockId,
        )
        val applied = applyEditorCommandUseCase(
            document = document,
            command = command,
        )
        return BlockMutationResult(
            applied = applied,
            block = block,
            insertCommand = command,
        )
    }

    fun insertBlockNear(
        document: PageBlockDocument,
        blockId: String,
        type: PageBlockType,
        position: PageBlockInsertPosition,
    ): BlockMutationResult {
        val block = PageContentCodec.newBlock(type)
        val command = insertBlockNearCommand(
            document = document,
            blockId = blockId,
            block = block,
            position = position,
        ) ?: EditorCommand.InsertBlock(block = block)
        val applied = applyEditorCommandUseCase(
            document = document,
            command = command,
        )
        return BlockMutationResult(
            applied = applied,
            block = block,
            insertCommand = command,
        )
    }

    fun insertBlockNearCommand(
        document: PageBlockDocument,
        blockId: String,
        block: PageBlock,
        position: PageBlockInsertPosition,
    ): EditorCommand.InsertBlock? {
        val location = document.blocks.findBlockLocation(blockId) ?: return null
        val targetIndex = when (position) {
            PageBlockInsertPosition.Above -> location.index
            PageBlockInsertPosition.Below -> location.index + 1
        }
        return EditorCommand.InsertBlock(
            block = block,
            parentBlockId = location.parentBlockId,
            index = targetIndex,
        )
    }

    fun replaceBlockWithBlocks(
        document: PageBlockDocument,
        blockId: String,
        replacementBlocks: List<PageBlock>,
    ): PasteBlocksMutationResult {
        if (replacementBlocks.isEmpty()) {
            return PasteBlocksMutationResult(document = document, changed = false)
        }
        val result = document.blocks.replaceBlockWithBlocks(blockId, replacementBlocks)
        return PasteBlocksMutationResult(
            document = if (result.changed) document.copy(blocks = result.blocks) else document,
            changed = result.changed,
        )
    }

    fun deleteBlock(
        document: PageBlockDocument,
        blockId: String,
    ): PageMutationResult {
        return apply(
            document = document,
            command = EditorCommand.DeleteBlock(blockId),
        )
    }

    fun moveBlock(
        document: PageBlockDocument,
        blockId: String,
        direction: Int,
    ): MoveBlockMutationResult {
        val targetIndex = document.blocks.findBlockMoveTargetIndex(blockId, direction)
        val result = if (targetIndex == null) {
            document.unchangedResult()
        } else {
            apply(
                document = document,
                command = EditorCommand.MoveBlock(blockId = blockId, direction = direction),
            )
        }
        return MoveBlockMutationResult(
            applied = result.applied,
            targetIndex = targetIndex,
        )
    }

    fun indentBlock(
        document: PageBlockDocument,
        blockId: String,
    ): PageMutationResult {
        val command = indentBlockCommand(document, blockId)
            ?: return document.unchangedResult()
        return apply(
            document = document,
            command = command,
        )
    }

    fun indentBlockCommand(
        document: PageBlockDocument,
        blockId: String,
    ): EditorCommand.MoveBlockToParent? {
        val location = document.blocks.findBlockLocation(blockId)
            ?: return null
        if (location.index <= 0) return null
        val siblings = document.siblingBlocks(location.parentBlockId)
            ?: return null
        val targetParent = siblings.getOrNull(location.index - 1)
            ?: return null
        return EditorCommand.MoveBlockToParent(
            blockId = blockId,
            parentBlockId = targetParent.id,
            index = targetParent.children.size,
        )
    }

    fun outdentBlock(
        document: PageBlockDocument,
        blockId: String,
    ): PageMutationResult {
        val command = outdentBlockCommand(document, blockId)
            ?: return document.unchangedResult()
        return apply(
            document = document,
            command = command,
        )
    }

    fun outdentBlockCommand(
        document: PageBlockDocument,
        blockId: String,
    ): EditorCommand.MoveBlockToParent? {
        val location = document.blocks.findBlockLocation(blockId)
            ?: return null
        val parentBlockId = location.parentBlockId
            ?: return null
        val parentLocation = document.blocks.findBlockLocation(parentBlockId)
            ?: return null
        return EditorCommand.MoveBlockToParent(
            blockId = blockId,
            parentBlockId = parentLocation.parentBlockId,
            index = parentLocation.index + 1,
        )
    }

    fun addProperty(
        document: PageBlockDocument,
        type: PagePropertyType,
        name: String,
    ): PropertyMutationResult {
        val property = PageContentCodec.newProperty(
            type = type,
            name = name.ifBlank { "Untitled property" },
        )
        val applied = applyEditorCommandUseCase(
            document = document,
            command = EditorCommand.InsertProperty(property),
        )
        return PropertyMutationResult(
            applied = applied,
            property = property,
        )
    }

    fun updatePropertyName(
        document: PageBlockDocument,
        propertyId: String,
        name: String,
    ): PropertyMutationResult {
        val property = document.properties.firstOrNull { it.id == propertyId }
            ?: return PropertyMutationResult(document.unchangedApplied(), property = null)
        return replaceProperty(document, property.copy(name = name))
    }

    fun updatePropertyValue(
        document: PageBlockDocument,
        propertyId: String,
        value: String,
    ): PropertyMutationResult {
        val property = document.properties.firstOrNull { it.id == propertyId }
            ?: return PropertyMutationResult(document.unchangedApplied(), property = null)
        return replaceProperty(document, property.copy(value = value))
    }

    fun deleteProperty(
        document: PageBlockDocument,
        propertyId: String,
    ): PageMutationResult {
        return apply(
            document = document,
            command = EditorCommand.DeleteProperty(propertyId),
        )
    }

    private fun replaceBlockMediaAttachments(
        document: PageBlockDocument,
        blockId: String,
        attachments: List<PageMediaAttachment>,
    ): PageMutationResult {
        return apply(
            document = document,
            command = EditorCommand.ReplaceBlockMediaAttachments(
                blockId = blockId,
                mediaAttachments = attachments,
            ),
        )
    }

    private fun replaceProperty(
        document: PageBlockDocument,
        property: PageProperty,
    ): PropertyMutationResult {
        val applied = applyEditorCommandUseCase(
            document = document,
            command = EditorCommand.ReplaceProperty(property),
        )
        return PropertyMutationResult(
            applied = applied,
            property = property,
        )
    }

    private fun apply(
        document: PageBlockDocument,
        command: EditorCommand,
    ): PageMutationResult {
        return PageMutationResult(
            applied = applyEditorCommandUseCase(document, command),
        )
    }

    private fun PageBlockDocument.unchangedResult(): PageMutationResult {
        return PageMutationResult(unchangedApplied())
    }

    private fun PageBlockDocument.unchangedApplied(): AppliedEditorCommand {
        return applyEditorCommandUseCase(
            document = this,
            command = EditorCommand.DeleteBlock(MissingCommandTargetId),
        )
    }
}

data class PageMutationResult(
    val applied: AppliedEditorCommand,
) {
    val document: PageBlockDocument
        get() = applied.document

    val changed: Boolean
        get() = applied.changed
}

data class BlockMutationResult(
    val applied: AppliedEditorCommand,
    val block: PageBlock,
    val insertCommand: EditorCommand.InsertBlock? = null,
) {
    val document: PageBlockDocument
        get() = applied.document

    val changed: Boolean
        get() = applied.changed
}

data class PropertyMutationResult(
    val applied: AppliedEditorCommand,
    val property: PageProperty?,
) {
    val document: PageBlockDocument
        get() = applied.document

    val changed: Boolean
        get() = applied.changed
}

data class MoveBlockMutationResult(
    val applied: AppliedEditorCommand,
    val targetIndex: Int?,
) {
    val document: PageBlockDocument
        get() = applied.document

    val changed: Boolean
        get() = applied.changed
}

data class PasteBlocksMutationResult(
    val document: PageBlockDocument,
    val changed: Boolean,
)

private fun PageBlockDocument.findBlock(blockId: String): PageBlock? {
    fun walk(blocks: List<PageBlock>): PageBlock? {
        blocks.forEach { block ->
            if (block.id == blockId) return block
            walk(block.children)?.let { return it }
        }
        return null
    }
    return walk(blocks)
}

private fun PageBlockDocument.siblingBlocks(parentBlockId: String?): List<PageBlock>? {
    return parentBlockId
        ?.let { id -> findBlock(id)?.children }
        ?: blocks
}

private data class ReplaceBlocksResult(
    val blocks: List<PageBlock>,
    val changed: Boolean,
)

private data class BlockLocation(
    val parentBlockId: String?,
    val index: Int,
)

private fun List<PageBlock>.findBlockLocation(
    blockId: String,
    parentBlockId: String? = null,
): BlockLocation? {
    forEachIndexed { index, block ->
        if (block.id == blockId) {
            return BlockLocation(parentBlockId = parentBlockId, index = index)
        }
        block.children.findBlockLocation(blockId, parentBlockId = block.id)?.let { return it }
    }
    return null
}

private fun List<PageBlock>.replaceBlockWithBlocks(
    blockId: String,
    replacementBlocks: List<PageBlock>,
): ReplaceBlocksResult {
    val directIndex = indexOfFirst { block -> block.id == blockId }
    if (directIndex >= 0) {
        val currentBlock = this[directIndex]
        val replacements = replacementBlocks.mapIndexed { index, block ->
            if (index == 0) block.copy(id = currentBlock.id) else block
        }
        return ReplaceBlocksResult(
            blocks = take(directIndex) + replacements + drop(directIndex + 1),
            changed = true,
        )
    }

    forEachIndexed { index, block ->
        val childResult = block.children.replaceBlockWithBlocks(blockId, replacementBlocks)
        if (childResult.changed) {
            return ReplaceBlocksResult(
                blocks = toMutableList().apply {
                    set(index, block.copy(children = childResult.blocks))
                },
                changed = true,
            )
        }
    }

    return ReplaceBlocksResult(blocks = this, changed = false)
}

private fun List<PageBlock>.findBlockMoveTargetIndex(
    blockId: String,
    direction: Int,
): Int? {
    val index = indexOfFirst { block -> block.id == blockId }
    if (index == -1) return null
    val targetIndex = (index + direction).coerceIn(0, lastIndex)
    return targetIndex.takeIf { it != index }
}

private const val MissingCommandTargetId = "__missing__"
