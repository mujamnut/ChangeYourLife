package com.changeyourlife.cyl.data.local.mapper

import com.changeyourlife.cyl.data.local.entity.PageBlockEntity
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.model.PageContentSnapshot
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PageContentProjectionMapperTest {
    @Test
    fun projectsJsonPageContentIntoGranularEntities() {
        val amountColumn = PageTableColumn(
            id = "column-amount",
            name = "Amount",
            type = PageTableColumnType.Number,
        )
        val tableBlock = PageBlock(
            id = "block-table",
            type = PageBlockType.DatabaseTable,
            table = PageTable(
                title = "Budget",
                columns = listOf(amountColumn),
                rows = listOf(
                    PageTableRow(
                        id = "row-1",
                        cells = mapOf("column-amount" to "4"),
                    ),
                ),
            ),
        )
        val document = PageBlockDocument(
            properties = listOf(
                PageProperty(
                    id = "property-date",
                    name = "Date",
                    type = PagePropertyType.Date,
                    value = "2026-06-29",
                ),
            ),
            blocks = listOf(
                PageBlock(
                    id = "block-text",
                    type = PageBlockType.Text,
                    text = "Monthly notes",
                ),
                tableBlock,
            ),
        )

        val projection = requireNotNull(pageEntity(
            content = json.encodeToString(document),
        ).toContentProjection())

        assertEquals(2, projection.blocks.size)
        assertEquals(1, projection.properties.size)
        assertEquals(1, projection.tables.size)
        assertEquals(1, projection.columns.size)
        assertEquals(1, projection.rows.size)
        assertEquals(1, projection.cells.size)
        assertEquals("Budget", projection.tables.single().title)
        assertEquals("4", projection.cells.single().value)

        val rebuiltDocument = PageContentSnapshot(
            blocks = projection.blocks,
            properties = projection.properties,
            tables = projection.tables,
            columns = projection.columns,
            rows = projection.rows,
            cells = projection.cells,
        ).toDocument()

        assertEquals("Date", rebuiltDocument.properties.single().name)
        assertEquals(PagePropertyType.Date, rebuiltDocument.properties.single().type)
        assertEquals("Monthly notes", rebuiltDocument.blocks.first().text)
        val rebuiltTable = rebuiltDocument.blocks[1].table
        assertEquals("Budget", rebuiltTable.title)
        assertEquals(PageTableColumnType.Number, rebuiltTable.columns.single().type)
        assertEquals("4", rebuiltTable.rows.single().cells["column-amount"])
    }

    @Test
    fun projectsLegacyPlainTextAsDeterministicTextBlocks() {
        val projection = requireNotNull(pageEntity(
            content = "line one\nline two",
        ).toContentProjection())

        assertEquals(2, projection.blocks.size)
        assertEquals("page-1:legacy-block:0", projection.blocks[0].id)
        assertEquals("line one", projection.blocks[0].text)
        assertEquals("page-1:legacy-block:1", projection.blocks[1].id)
        assertEquals("line two", projection.blocks[1].text)
    }

    @Test
    fun snapshotWithDatabaseBlockWithoutTableProjectionNormalizesToUsableDatabase() {
        val document = PageContentSnapshot(
            blocks = listOf(
                PageBlockEntity(
                    id = "block-db",
                    pageId = "page-1",
                    parentBlockId = null,
                    type = PageBlockType.DatabaseTable.name,
                    sortOrder = 0,
                    createdAt = 1000,
                    updatedAt = 2000,
                    deletedAt = null,
                ),
            ),
            properties = emptyList(),
            tables = emptyList(),
            columns = emptyList(),
            rows = emptyList(),
            cells = emptyList(),
        ).toDocument()

        val normalized = PageContentCodec.decodeDocument(PageContentCodec.encodeDocument(document))

        val block = normalized.blocks.single()
        assertEquals(PageBlockType.DatabaseTable, block.type)
        assertEquals(listOf("Name"), block.table.columns.map { column -> column.name })
    }

    private fun pageEntity(content: String): PageEntity {
        return PageEntity(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget Tracker",
            content = content,
            sortOrder = 0,
            createdAt = 1000,
            updatedAt = 2000,
            deletedAt = null,
        )
    }

    private companion object {
        val json = Json {
            encodeDefaults = true
        }
    }
}
