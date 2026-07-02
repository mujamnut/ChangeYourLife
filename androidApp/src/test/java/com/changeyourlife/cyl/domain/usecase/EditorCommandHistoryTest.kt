package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCommandHistoryTest {
    private val applyUseCase = ApplyEditorCommandUseCase()

    @Test
    fun recordsUndoCommandAndSupportsRedo() {
        val history = EditorCommandHistory(applyUseCase)
        val document = PageBlockDocument(
            blocks = listOf(PageBlock(id = "block-1", type = PageBlockType.Text, text = "Old")),
        )
        val applied = applyUseCase(
            document = document,
            command = EditorCommand.UpdateBlockText("block-1", "New"),
        )

        history.record(applied)

        assertTrue(history.canUndo)
        assertFalse(history.canRedo)

        val undo = history.undo(applied.document)
        assertEquals("Old", undo?.document?.blocks?.single()?.text)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)

        val redo = history.redo(requireNotNull(undo).document)
        assertEquals("New", redo?.document?.blocks?.single()?.text)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun ignoresUnchangedCommandsAndCapsHistory() {
        val history = EditorCommandHistory(applyUseCase, maxEntries = 1)
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(id = "a", type = PageBlockType.Text, text = "A"),
                PageBlock(id = "b", type = PageBlockType.Text, text = "B"),
            ),
        )

        history.record(
            applyUseCase(
                document = document,
                command = EditorCommand.UpdateBlockText("missing", "Nope"),
            ),
        )
        assertFalse(history.canUndo)

        val first = applyUseCase(document, EditorCommand.UpdateBlockText("a", "A1"))
        history.record(first)
        val second = applyUseCase(first.document, EditorCommand.UpdateBlockText("b", "B1"))
        history.record(second)

        val undo = history.undo(second.document)
        assertEquals("A1", undo?.document?.blocks?.first { block -> block.id == "a" }?.text)
        assertEquals("B", undo?.document?.blocks?.first { block -> block.id == "b" }?.text)
        assertNull(history.undo(requireNotNull(undo).document))
    }
}
