package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMutationUseCaseTest {
    private val applyUseCase = ApplyEditorCommandUseCase()
    private val useCase = PageMutationUseCase(applyUseCase)

    @Test
    fun updateBlockTextPreservesExistingRichTextSpans() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "block-1",
                    type = PageBlockType.Text,
                    text = "Old",
                    richTextSpans = listOf(PageTextSpan(start = 0, end = 3, bold = true)),
                ),
            ),
        )

        val result = useCase.updateBlockText(
            document = document,
            blockId = "block-1",
            text = "New",
        )

        assertTrue(result.changed)
        assertEquals("New", result.document.blocks.single().text)
        assertEquals(
            listOf(PageTextSpan(start = 0, end = 3, bold = true)),
            result.document.blocks.single().richTextSpans,
        )
    }

    @Test
    fun addBlockReturnsGeneratedBlockAndUndoCommand() {
        val document = PageBlockDocument(
            blocks = listOf(PageBlock(id = "block-1", type = PageBlockType.Text)),
        )

        val result = useCase.addBlock(document, PageBlockType.Todo)
        val undo = applyUseCase(result.document, requireNotNull(result.applied.result.undoCommand))

        assertTrue(result.changed)
        assertEquals(PageBlockType.Todo, result.block.type)
        assertEquals(listOf("block-1", result.block.id), result.document.blocks.map { it.id })
        assertEquals(document, undo.document)
    }

    @Test
    fun replaceBlockWithBlocksKeepsFirstBlockIdAndInsertsRemainingBlocks() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(id = "before", type = PageBlockType.Text, text = "Before"),
                PageBlock(id = "target", type = PageBlockType.Text, text = "Old"),
                PageBlock(id = "after", type = PageBlockType.Text, text = "After"),
            ),
        )

        val result = useCase.replaceBlockWithBlocks(
            document = document,
            blockId = "target",
            replacementBlocks = listOf(
                PageBlock(id = "new-1", type = PageBlockType.Heading, text = "Title"),
                PageBlock(id = "new-2", type = PageBlockType.Bullet, text = "Item"),
            ),
        )

        assertTrue(result.changed)
        assertEquals(
            listOf("before", "target", "new-2", "after"),
            result.document.blocks.map { block -> block.id },
        )
        assertEquals(PageBlockType.Heading, result.document.blocks[1].type)
        assertEquals("Title", result.document.blocks[1].text)
        assertEquals(PageBlockType.Bullet, result.document.blocks[2].type)
    }

    @Test
    fun insertBlockNearKeepsSiblingLevelAndUndoCommand() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "parent",
                    type = PageBlockType.Text,
                    children = listOf(
                        PageBlock(id = "first-child", type = PageBlockType.Text),
                        PageBlock(id = "target-child", type = PageBlockType.Text),
                    ),
                ),
                PageBlock(id = "after-parent", type = PageBlockType.Text),
            ),
        )

        val result = useCase.insertBlockNear(
            document = document,
            blockId = "target-child",
            type = PageBlockType.Todo,
            position = PageBlockInsertPosition.Below,
        )
        val undo = applyUseCase(result.document, requireNotNull(result.applied.result.undoCommand))

        assertTrue(result.changed)
        assertEquals(PageBlockType.Todo, result.block.type)
        assertEquals("parent", result.insertCommand?.parentBlockId)
        assertEquals(2, result.insertCommand?.index)
        assertEquals(
            listOf("first-child", "target-child", result.block.id),
            result.document.blocks.first().children.map { block -> block.id },
        )
        assertEquals(document, undo.document)
    }

    @Test
    fun moveBlockReturnsRepositoryTargetIndex() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(id = "first", type = PageBlockType.Text),
                PageBlock(id = "second", type = PageBlockType.Text),
                PageBlock(id = "third", type = PageBlockType.Text),
            ),
        )

        val result = useCase.moveBlock(document, blockId = "second", direction = 1)

        assertTrue(result.changed)
        assertEquals(2, result.targetIndex)
        assertEquals(listOf("first", "third", "second"), result.document.blocks.map { it.id })
    }

    @Test
    fun moveBlockAtBoundaryDoesNotChange() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(id = "first", type = PageBlockType.Text),
                PageBlock(id = "second", type = PageBlockType.Text),
            ),
        )

        val result = useCase.moveBlock(document, blockId = "first", direction = -1)

        assertFalse(result.changed)
        assertEquals(null, result.targetIndex)
        assertEquals(document, result.document)
    }

    @Test
    fun indentBlockMovesBlockInsidePreviousSibling() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(id = "parent", type = PageBlockType.Bullet),
                PageBlock(id = "child", type = PageBlockType.Bullet),
            ),
        )

        val result = useCase.indentBlock(document, blockId = "child")
        val undo = applyUseCase(result.document, requireNotNull(result.applied.result.undoCommand))

        assertTrue(result.changed)
        assertEquals(listOf("parent"), result.document.blocks.map { it.id })
        assertEquals(listOf("child"), result.document.blocks.single().children.map { it.id })
        assertEquals(document, undo.document)
    }

    @Test
    fun outdentBlockMovesChildAfterParent() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "parent",
                    type = PageBlockType.Bullet,
                    children = listOf(PageBlock(id = "child", type = PageBlockType.Bullet)),
                ),
                PageBlock(id = "after", type = PageBlockType.Text),
            ),
        )

        val result = useCase.outdentBlock(document, blockId = "child")
        val undo = applyUseCase(result.document, requireNotNull(result.applied.result.undoCommand))

        assertTrue(result.changed)
        assertEquals(listOf("parent", "child", "after"), result.document.blocks.map { it.id })
        assertEquals(document, undo.document)
    }

    @Test
    fun propertyMutationsReturnPropertyAndUndoCommand() {
        val document = PageBlockDocument(
            properties = listOf(PageProperty(id = "status", name = "Status", type = PagePropertyType.Status)),
        )

        val addResult = useCase.addProperty(document, type = PagePropertyType.Date, name = "Deadline")
        val addedProperty = requireNotNull(addResult.property)
        val renameResult = useCase.updatePropertyName(
            document = addResult.document,
            propertyId = addedProperty.id,
            name = "Due date",
        )
        val deleteResult = useCase.deleteProperty(
            document = renameResult.document,
            propertyId = addedProperty.id,
        )

        assertTrue(addResult.changed)
        assertEquals("Deadline", addedProperty.name)
        assertTrue(addResult.applied.result.undoCommand is EditorCommand.DeleteProperty)
        assertEquals("Due date", requireNotNull(renameResult.property).name)
        assertTrue(renameResult.applied.result.undoCommand is EditorCommand.ReplaceProperty)
        assertEquals(listOf("status"), deleteResult.document.properties.map { it.id })
        assertTrue(deleteResult.applied.result.undoCommand is EditorCommand.InsertProperty)
    }
}
