package com.changeyourlife.cyl.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCommandExecutorTest {
    @Test
    fun updateBlockTextNormalizesSpansAndReturnsUndoCommand() {
        val document = PageBlockDocument(
            blocks = listOf(PageBlock(id = "block-1", type = PageBlockType.Text, text = "Old")),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.UpdateBlockText(
                blockId = "block-1",
                text = "New",
                richTextSpans = listOf(PageTextSpan(start = 0, end = 50, bold = true)),
            ),
        )

        assertTrue(result.changed)
        assertEquals("New", result.document.blocks.first().text)
        assertEquals(
            listOf(PageTextSpan(start = 0, end = 3, bold = true)),
            result.document.blocks.first().richTextSpans,
        )
        assertEquals(
            EditorCommand.UpdateBlockText(
                blockId = "block-1",
                text = "Old",
                richTextSpans = emptyList(),
            ),
            result.undoCommand,
        )
    }

    @Test
    fun changeBlockTypeCanBeUndone() {
        val document = PageBlockDocument(
            blocks = listOf(PageBlock(id = "block-1", type = PageBlockType.Text, text = "Task")),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.ChangeBlockType("block-1", PageBlockType.Todo),
        )
        val undoResult = EditorCommandExecutor.apply(result.document, result.undoCommand!!)

        assertEquals(PageBlockType.Todo, result.document.blocks.first().type)
        assertEquals(document, undoResult.document)
    }

    @Test
    fun insertBlockSupportsNestedParentAndDeleteUndo() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "parent",
                    type = PageBlockType.Text,
                    children = listOf(PageBlock(id = "child-1", type = PageBlockType.Text)),
                ),
            ),
        )
        val newBlock = PageBlock(id = "child-2", type = PageBlockType.Quote, text = "Nested")

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.InsertBlock(
                block = newBlock,
                parentBlockId = "parent",
                index = 1,
            ),
        )
        val undoResult = EditorCommandExecutor.apply(result.document, result.undoCommand!!)

        assertTrue(result.changed)
        assertEquals(
            listOf("child-1", "child-2"),
            result.document.blocks.first().children.map { it.id },
        )
        assertEquals(document, undoResult.document)
    }

    @Test
    fun deleteBlockReturnsUndoWithOriginalPosition() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(id = "first", type = PageBlockType.Text),
                PageBlock(id = "second", type = PageBlockType.Heading),
                PageBlock(id = "third", type = PageBlockType.Text),
            ),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.DeleteBlock("second"),
        )
        val undoResult = EditorCommandExecutor.apply(result.document, result.undoCommand!!)

        assertEquals(listOf("first", "third"), result.document.blocks.map { it.id })
        assertEquals(document, undoResult.document)
    }

    @Test
    fun moveBlockMovesWithinSameSiblingListAndCanBeUndone() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(id = "first", type = PageBlockType.Text),
                PageBlock(id = "second", type = PageBlockType.Heading),
                PageBlock(id = "third", type = PageBlockType.Text),
            ),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.MoveBlock(blockId = "second", direction = 1),
        )
        val undoResult = EditorCommandExecutor.apply(result.document, result.undoCommand!!)

        assertTrue(result.changed)
        assertEquals(listOf("first", "third", "second"), result.document.blocks.map { it.id })
        assertEquals(document, undoResult.document)
    }

    @Test
    fun moveBlockSupportsNestedSiblings() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "parent",
                    type = PageBlockType.Text,
                    children = listOf(
                        PageBlock(id = "first", type = PageBlockType.Text),
                        PageBlock(id = "second", type = PageBlockType.Text),
                    ),
                ),
            ),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.MoveBlock(blockId = "second", direction = -1),
        )

        assertEquals(
            listOf("second", "first"),
            result.document.blocks.first().children.map { it.id },
        )
    }

    @Test
    fun moveBlockAtBoundaryDoesNotChangeDocument() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(id = "first", type = PageBlockType.Text),
                PageBlock(id = "second", type = PageBlockType.Text),
            ),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.MoveBlock(blockId = "first", direction = -1),
        )

        assertFalse(result.changed)
        assertEquals(document, result.document)
        assertEquals(null, result.undoCommand)
    }

    @Test
    fun moveBlockToParentCanIndentAndUndo() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(id = "parent", type = PageBlockType.Text),
                PageBlock(id = "child", type = PageBlockType.Bullet, text = "Nested"),
                PageBlock(id = "after", type = PageBlockType.Text),
            ),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.MoveBlockToParent(
                blockId = "child",
                parentBlockId = "parent",
                index = 0,
            ),
        )
        val undoResult = EditorCommandExecutor.apply(result.document, result.undoCommand!!)

        assertTrue(result.changed)
        assertEquals(listOf("parent", "after"), result.document.blocks.map { it.id })
        assertEquals(listOf("child"), result.document.blocks.first().children.map { it.id })
        assertEquals(document, undoResult.document)
    }

    @Test
    fun moveBlockToParentCanOutdentAndUndo() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "parent",
                    type = PageBlockType.Text,
                    children = listOf(PageBlock(id = "child", type = PageBlockType.Bullet)),
                ),
                PageBlock(id = "after", type = PageBlockType.Text),
            ),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.MoveBlockToParent(
                blockId = "child",
                parentBlockId = null,
                index = 1,
            ),
        )
        val undoResult = EditorCommandExecutor.apply(result.document, result.undoCommand!!)

        assertTrue(result.changed)
        assertEquals(listOf("parent", "child", "after"), result.document.blocks.map { it.id })
        assertEquals(emptyList<PageBlock>(), result.document.blocks.first().children)
        assertEquals(document, undoResult.document)
    }

    @Test
    fun moveBlockToDescendantDoesNotChangeDocument() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "parent",
                    type = PageBlockType.Text,
                    children = listOf(PageBlock(id = "child", type = PageBlockType.Text)),
                ),
            ),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.MoveBlockToParent(
                blockId = "parent",
                parentBlockId = "child",
            ),
        )

        assertFalse(result.changed)
        assertEquals(document, result.document)
    }

    @Test
    fun replaceBlockMediaAttachmentsCanBeUndone() {
        val existingAttachment = PageMediaAttachment(
            id = "file-1",
            uri = "content://old",
            name = "old.png",
        )
        val nextAttachment = PageMediaAttachment(
            id = "file-2",
            uri = "content://new",
            name = "new.png",
        )
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "media",
                    type = PageBlockType.MediaFile,
                    mediaAttachments = listOf(existingAttachment),
                ),
            ),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.ReplaceBlockMediaAttachments(
                blockId = "media",
                mediaAttachments = listOf(existingAttachment, nextAttachment),
            ),
        )
        val undoResult = EditorCommandExecutor.apply(result.document, result.undoCommand!!)

        assertTrue(result.changed)
        assertEquals(
            listOf(existingAttachment, nextAttachment),
            result.document.blocks.first().mediaAttachments,
        )
        assertEquals(document, undoResult.document)
    }

    @Test
    fun replaceTableOnlyUpdatesDatabaseTableAndCanBeUndone() {
        val originalTable = PageTable(title = "Old table")
        val nextTable = originalTable.copy(title = "New table")
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "table",
                    type = PageBlockType.DatabaseTable,
                    table = originalTable,
                ),
            ),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.ReplaceTable(
                blockId = "table",
                table = nextTable,
            ),
        )
        val undoResult = EditorCommandExecutor.apply(result.document, result.undoCommand!!)

        assertTrue(result.changed)
        assertEquals("New table", result.document.blocks.first().table.title)
        assertEquals(document, undoResult.document)
    }

    @Test
    fun replaceTableIgnoresNonTableBlock() {
        val document = PageBlockDocument(
            blocks = listOf(PageBlock(id = "text", type = PageBlockType.Text)),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.ReplaceTable(
                blockId = "text",
                table = PageTable(title = "Should not apply"),
            ),
        )

        assertFalse(result.changed)
        assertEquals(document, result.document)
        assertEquals(null, result.undoCommand)
    }

    @Test
    fun propertyCommandsCanBeUndone() {
        val first = PageProperty(id = "property-1", name = "Status", type = PagePropertyType.Status)
        val inserted = PageProperty(id = "property-2", name = "Deadline", type = PagePropertyType.Date)
        val document = PageBlockDocument(properties = listOf(first))

        val insertResult = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.InsertProperty(
                property = inserted,
                index = 0,
            ),
        )
        val insertUndo = EditorCommandExecutor.apply(insertResult.document, insertResult.undoCommand!!)

        assertTrue(insertResult.changed)
        assertEquals(listOf("property-2", "property-1"), insertResult.document.properties.map { it.id })
        assertEquals(document, insertUndo.document)

        val renamed = inserted.copy(name = "Due date")
        val replaceResult = EditorCommandExecutor.apply(
            document = insertResult.document,
            command = EditorCommand.ReplaceProperty(renamed),
        )
        val replaceUndo = EditorCommandExecutor.apply(replaceResult.document, replaceResult.undoCommand!!)

        assertEquals("Due date", replaceResult.document.properties.first().name)
        assertEquals(insertResult.document, replaceUndo.document)

        val deleteResult = EditorCommandExecutor.apply(
            document = insertResult.document,
            command = EditorCommand.DeleteProperty("property-2"),
        )
        val deleteUndo = EditorCommandExecutor.apply(deleteResult.document, deleteResult.undoCommand!!)

        assertEquals(listOf("property-1"), deleteResult.document.properties.map { it.id })
        assertEquals(insertResult.document, deleteUndo.document)
    }

    @Test
    fun missingBlockDoesNotChangeDocument() {
        val document = PageBlockDocument(
            blocks = listOf(PageBlock(id = "block-1", type = PageBlockType.Text)),
        )

        val result = EditorCommandExecutor.apply(
            document = document,
            command = EditorCommand.DeleteBlock("missing"),
        )

        assertFalse(result.changed)
        assertEquals(document, result.document)
        assertEquals(null, result.undoCommand)
    }
}
