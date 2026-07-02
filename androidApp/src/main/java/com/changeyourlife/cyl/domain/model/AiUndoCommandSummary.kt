package com.changeyourlife.cyl.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AiUndoCommandSummary(
    val actionIndex: Int,
    val commandType: String,
    val targetType: String,
    val targetId: String,
    val blockId: String = "",
    val text: String = "",
    val richTextSpans: List<PageTextSpan> = emptyList(),
    val blockType: PageBlockType? = null,
    val isChecked: Boolean? = null,
    val block: PageBlock? = null,
    val parentBlockId: String? = null,
    val index: Int? = null,
    val direction: Int? = null,
    val mediaAttachments: List<PageMediaAttachment> = emptyList(),
    val table: PageTable? = null,
    val property: PageProperty? = null,
    val propertyId: String = "",
)

fun EditorCommand.toAiUndoCommandSummary(actionIndex: Int): AiUndoCommandSummary {
    return when (this) {
        is EditorCommand.UpdateBlockText -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "UpdateBlockText",
            targetType = "Block",
            targetId = blockId,
            blockId = blockId,
            text = text,
            richTextSpans = richTextSpans,
        )

        is EditorCommand.ChangeBlockType -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "ChangeBlockType",
            targetType = "Block",
            targetId = blockId,
            blockId = blockId,
            blockType = type,
        )

        is EditorCommand.ToggleTodo -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "ToggleTodo",
            targetType = "Block",
            targetId = blockId,
            blockId = blockId,
            isChecked = isChecked,
        )

        is EditorCommand.InsertBlock -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "InsertBlock",
            targetType = parentBlockId?.let { "Block" } ?: "Document",
            targetId = block.id,
            block = block,
            parentBlockId = parentBlockId,
            index = index,
        )

        is EditorCommand.DeleteBlock -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "DeleteBlock",
            targetType = "Block",
            targetId = blockId,
            blockId = blockId,
        )

        is EditorCommand.MoveBlock -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "MoveBlock",
            targetType = "Block",
            targetId = blockId,
            blockId = blockId,
            direction = direction,
        )

        is EditorCommand.MoveBlockToParent -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "MoveBlockToParent",
            targetType = parentBlockId?.let { "Block" } ?: "Document",
            targetId = blockId,
            blockId = blockId,
            parentBlockId = parentBlockId,
            index = index,
        )

        is EditorCommand.ReplaceBlockMediaAttachments -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "ReplaceBlockMediaAttachments",
            targetType = "Block",
            targetId = blockId,
            blockId = blockId,
            mediaAttachments = mediaAttachments,
        )

        is EditorCommand.ReplaceTable -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "ReplaceTable",
            targetType = "Table",
            targetId = blockId,
            blockId = blockId,
            table = table,
        )

        is EditorCommand.InsertProperty -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "InsertProperty",
            targetType = "Property",
            targetId = property.id,
            property = property,
            index = index,
        )

        is EditorCommand.ReplaceProperty -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "ReplaceProperty",
            targetType = "Property",
            targetId = property.id,
            property = property,
        )

        is EditorCommand.DeleteProperty -> AiUndoCommandSummary(
            actionIndex = actionIndex,
            commandType = "DeleteProperty",
            targetType = "Property",
            targetId = propertyId,
            propertyId = propertyId,
        )
    }
}

fun AiUndoCommandSummary.toEditorCommand(): EditorCommand? {
    return when (commandType) {
        "UpdateBlockText" -> blockId.takeIf(String::isNotBlank)?.let { id ->
            EditorCommand.UpdateBlockText(
                blockId = id,
                text = text,
                richTextSpans = richTextSpans,
            )
        }

        "ChangeBlockType" -> blockId.takeIf(String::isNotBlank)?.let { id ->
            blockType?.let { type -> EditorCommand.ChangeBlockType(blockId = id, type = type) }
        }

        "ToggleTodo" -> blockId.takeIf(String::isNotBlank)?.let { id ->
            EditorCommand.ToggleTodo(blockId = id, isChecked = isChecked)
        }

        "InsertBlock" -> block?.let { undoBlock ->
            EditorCommand.InsertBlock(
                block = undoBlock,
                parentBlockId = parentBlockId,
                index = index,
            )
        }

        "DeleteBlock" -> blockId.takeIf(String::isNotBlank)?.let(EditorCommand::DeleteBlock)

        "MoveBlock" -> blockId.takeIf(String::isNotBlank)?.let { id ->
            direction?.let { delta -> EditorCommand.MoveBlock(blockId = id, direction = delta) }
        }

        "MoveBlockToParent" -> blockId.takeIf(String::isNotBlank)?.let { id ->
            EditorCommand.MoveBlockToParent(
                blockId = id,
                parentBlockId = parentBlockId,
                index = index,
            )
        }

        "ReplaceBlockMediaAttachments" -> blockId.takeIf(String::isNotBlank)?.let { id ->
            EditorCommand.ReplaceBlockMediaAttachments(
                blockId = id,
                mediaAttachments = mediaAttachments,
            )
        }

        "ReplaceTable" -> blockId.takeIf(String::isNotBlank)?.let { id ->
            table?.let { undoTable -> EditorCommand.ReplaceTable(blockId = id, table = undoTable) }
        }

        "InsertProperty" -> property?.let { undoProperty ->
            EditorCommand.InsertProperty(property = undoProperty, index = index)
        }

        "ReplaceProperty" -> property?.let(EditorCommand::ReplaceProperty)

        "DeleteProperty" -> propertyId.takeIf(String::isNotBlank)?.let(EditorCommand::DeleteProperty)

        else -> null
    }
}
