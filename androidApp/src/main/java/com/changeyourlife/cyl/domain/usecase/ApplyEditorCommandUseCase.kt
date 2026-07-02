package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.EditorCommandExecutor
import com.changeyourlife.cyl.domain.model.EditorCommandResult
import com.changeyourlife.cyl.domain.model.PageBlockDocument

class ApplyEditorCommandUseCase {
    operator fun invoke(
        document: PageBlockDocument,
        command: EditorCommand,
    ): AppliedEditorCommand {
        return AppliedEditorCommand(
            command = command,
            previousDocument = document,
            result = EditorCommandExecutor.apply(document, command),
            target = command.target(),
        )
    }
}

data class AppliedEditorCommand(
    val command: EditorCommand,
    val previousDocument: PageBlockDocument,
    val result: EditorCommandResult,
    val target: EditorCommandTarget,
) {
    val document: PageBlockDocument
        get() = result.document

    val changed: Boolean
        get() = result.changed
}

sealed interface EditorCommandTarget {
    data object Document : EditorCommandTarget

    data class Block(
        val blockId: String,
    ) : EditorCommandTarget

    data class Table(
        val blockId: String,
    ) : EditorCommandTarget

    data class Property(
        val propertyId: String,
    ) : EditorCommandTarget
}

private fun EditorCommand.target(): EditorCommandTarget {
    return when (this) {
        is EditorCommand.UpdateBlockText -> EditorCommandTarget.Block(blockId)
        is EditorCommand.ChangeBlockType -> EditorCommandTarget.Block(blockId)
        is EditorCommand.ToggleTodo -> EditorCommandTarget.Block(blockId)
        is EditorCommand.ReplaceBlockMediaAttachments -> EditorCommandTarget.Block(blockId)
        is EditorCommand.ReplaceTable -> EditorCommandTarget.Table(blockId)
        is EditorCommand.InsertProperty -> EditorCommandTarget.Property(property.id)
        is EditorCommand.ReplaceProperty -> EditorCommandTarget.Property(property.id)
        is EditorCommand.DeleteProperty -> EditorCommandTarget.Property(propertyId)
        is EditorCommand.InsertBlock -> parentBlockId?.let(EditorCommandTarget::Block)
            ?: EditorCommandTarget.Document
        is EditorCommand.DeleteBlock -> EditorCommandTarget.Document
        is EditorCommand.MoveBlock -> EditorCommandTarget.Document
        is EditorCommand.MoveBlockToParent -> EditorCommandTarget.Document
    }
}
