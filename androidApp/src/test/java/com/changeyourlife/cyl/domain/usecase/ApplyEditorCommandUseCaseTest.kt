package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyEditorCommandUseCaseTest {
    private val useCase = ApplyEditorCommandUseCase()

    @Test
    fun appliesCommandAndKeepsPreviousDocumentForUndoPipeline() {
        val document = PageBlockDocument(
            blocks = listOf(PageBlock(id = "block-1", type = PageBlockType.Text, text = "Old")),
        )

        val applied = useCase(
            document = document,
            command = EditorCommand.UpdateBlockText(
                blockId = "block-1",
                text = "New",
            ),
        )

        assertTrue(applied.changed)
        assertEquals(document, applied.previousDocument)
        assertEquals("New", applied.document.blocks.first().text)
        assertEquals(EditorCommandTarget.Block("block-1"), applied.target)
    }

    @Test
    fun classifiesTableCommandAsTableTarget() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "table-1",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(title = "Old"),
                ),
            ),
        )

        val applied = useCase(
            document = document,
            command = EditorCommand.ReplaceTable(
                blockId = "table-1",
                table = PageTable(title = "New"),
            ),
        )

        assertEquals(EditorCommandTarget.Table("table-1"), applied.target)
        assertEquals("New", applied.document.blocks.first().table.title)
    }

    @Test
    fun unchangedCommandStillReturnsTargetAndPreviousDocument() {
        val document = PageBlockDocument(
            blocks = listOf(PageBlock(id = "block-1", type = PageBlockType.Text, text = "Same")),
        )

        val applied = useCase(
            document = document,
            command = EditorCommand.UpdateBlockText(
                blockId = "block-1",
                text = "Same",
            ),
        )

        assertFalse(applied.changed)
        assertEquals(document, applied.previousDocument)
        assertEquals(EditorCommandTarget.Block("block-1"), applied.target)
    }
}
