package com.changeyourlife.cyl.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageContentCodecTest {
    @Test
    fun encodeDocumentNormalizesDuplicateRowIdsAcrossWholeDocument() {
        val firstTable = PageContentCodec.newBlock(PageBlockType.DatabaseTable).copy(
            id = "table-1",
            table = PageTable(
                columns = listOf(PageTableColumn(id = "name-1", name = "Name")),
                rows = listOf(
                    PageTableRow(id = "row-duplicate", cells = mapOf("name-1" to "First")),
                    PageTableRow(id = "row-duplicate", cells = mapOf("name-1" to "Second")),
                ),
            ),
        )
        val secondTable = PageContentCodec.newBlock(PageBlockType.DatabaseTable).copy(
            id = "table-2",
            table = PageTable(
                columns = listOf(PageTableColumn(id = "name-2", name = "Name")),
                rows = listOf(
                    PageTableRow(id = "row-duplicate", cells = mapOf("name-2" to "Third")),
                    PageTableRow(id = "", cells = mapOf("name-2" to "Fourth")),
                ),
            ),
        )

        val encoded = PageContentCodec.encodeDocument(
            PageBlockDocument(blocks = listOf(firstTable, secondTable)),
        )
        val decoded = PageContentCodec.decodeDocument(encoded)
        val rowIds = decoded.blocks.flatMap { block -> block.table.rows.map { row -> row.id } }

        assertEquals(4, rowIds.size)
        assertEquals("row-duplicate", rowIds.first())
        assertEquals(rowIds.size, rowIds.toSet().size)
        assertTrue(rowIds.all { rowId -> rowId.isNotBlank() })
    }
}
