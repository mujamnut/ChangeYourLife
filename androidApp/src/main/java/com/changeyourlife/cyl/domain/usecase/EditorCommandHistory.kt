package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.PageBlockDocument

class EditorCommandHistory(
    private val applyEditorCommandUseCase: ApplyEditorCommandUseCase,
    private val maxEntries: Int = 40,
) {
    private val undoCommands = ArrayDeque<EditorCommand>()
    private val redoCommands = ArrayDeque<EditorCommand>()

    val canUndo: Boolean
        get() = undoCommands.isNotEmpty()

    val canRedo: Boolean
        get() = redoCommands.isNotEmpty()

    fun record(applied: AppliedEditorCommand) {
        if (!applied.changed) return
        recordUndoCommand(applied.result.undoCommand)
    }

    fun recordUndoCommand(command: EditorCommand?) {
        val undoCommand = command ?: return
        undoCommands.pushCapped(undoCommand)
        redoCommands.clear()
    }

    fun undo(document: PageBlockDocument): EditorCommandHistoryResult? {
        if (undoCommands.isEmpty()) return null
        val command = undoCommands.removeLast()
        val applied = applyEditorCommandUseCase(document, command)
        if (applied.changed) {
            applied.result.undoCommand?.let { redoCommand ->
                redoCommands.pushCapped(redoCommand)
            }
        }
        return EditorCommandHistoryResult(applied)
    }

    fun redo(document: PageBlockDocument): EditorCommandHistoryResult? {
        if (redoCommands.isEmpty()) return null
        val command = redoCommands.removeLast()
        val applied = applyEditorCommandUseCase(document, command)
        if (applied.changed) {
            applied.result.undoCommand?.let { undoCommand ->
                undoCommands.pushCapped(undoCommand)
            }
        }
        return EditorCommandHistoryResult(applied)
    }

    fun clear() {
        undoCommands.clear()
        redoCommands.clear()
    }

    private fun ArrayDeque<EditorCommand>.pushCapped(command: EditorCommand) {
        if (size >= maxEntries) removeFirst()
        addLast(command)
    }
}

data class EditorCommandHistoryResult(
    val applied: AppliedEditorCommand,
) {
    val document: PageBlockDocument
        get() = applied.document

    val changed: Boolean
        get() = applied.changed
}
