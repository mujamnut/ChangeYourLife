package com.changeyourlife.cyl.domain.model

import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PageContentCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decode(content: String): List<PageBlock> {
        return decodeDocument(content).blocks
    }

    fun decodeDocument(content: String): PageBlockDocument {
        if (content.isBlank()) {
            return PageBlockDocument(blocks = listOf(newBlock(PageBlockType.Text)))
        }

        return runCatching {
            json.decodeFromString<PageBlockDocument>(content).withNormalizedBlocks()
        }.getOrElse { throwable ->
            if (throwable is SerializationException || throwable is IllegalArgumentException) {
                PageBlockDocument(blocks = content.toLegacyBlocks())
            } else {
                throw throwable
            }
        }
    }

    fun encode(blocks: List<PageBlock>): String {
        return encodeDocument(PageBlockDocument(blocks = blocks))
    }

    fun encodeDocument(document: PageBlockDocument): String {
        val normalizedBlocks = document.blocks.normalizedTopLevelBlocks()
        return json.encodeToString(
            document.copy(
                blocks = normalizedBlocks,
            ),
        )
    }

    fun newBlock(type: PageBlockType): PageBlock {
        val block = PageBlock(
            id = UUID.randomUUID().toString(),
            type = type,
        )
        return if (type == PageBlockType.DatabaseTable) {
            block.copy(table = newTable())
        } else {
            block
        }
    }

    fun newProperty(
        type: PagePropertyType,
        name: String,
    ): PageProperty {
        return PageProperty(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type,
        )
    }

    fun newTableColumn(
        name: String,
        type: PageTableColumnType = PageTableColumnType.Text,
    ): PageTableColumn {
        return PageTableColumn(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type,
        )
    }

    fun newTableRow(columns: List<PageTableColumn>): PageTableRow {
        return PageTableRow(
            id = UUID.randomUUID().toString(),
            cells = columns.associate { column -> column.id to "" },
            cellValues = columns.associate { column -> column.id to column.toTypedCellValue("") },
        )
    }

    private fun newTable(): PageTable {
        val columns = listOf(
            newTableColumn("Name"),
        )
        return PageTable(
            title = "Untitled database",
            columns = columns,
            rows = emptyList(),
        )
    }

    private fun PageBlockDocument.withNormalizedBlocks(): PageBlockDocument {
        return copy(
            blocks = blocks.normalizedTopLevelBlocks(),
        )
    }

    private fun List<PageBlock>.normalizedTopLevelBlocks(): List<PageBlock> {
        return ifEmpty { listOf(newBlock(PageBlockType.Text)) }
            .map { block -> block.normalizedBlock() }
    }

    private fun List<PageBlock>.normalizedChildBlocks(): List<PageBlock> {
        return map { block -> block.normalizedBlock() }
    }

    private fun PageBlock.normalizedBlock(): PageBlock {
        val normalizedChildren = children.normalizedChildBlocks()
        return if (type == PageBlockType.DatabaseTable) {
            copy(
                table = table.normalizedTable(),
                children = normalizedChildren,
            )
        } else {
            copy(children = normalizedChildren)
        }
    }

    private fun PageTable.normalizedTable(): PageTable {
        val normalizedColumns = columns.ifEmpty {
            listOf(newTableColumn("Name"))
        }
        val validColumnIds = normalizedColumns.map { column -> column.id }.toSet()
        val normalizedRows = rows.map { row ->
            row.copy(
                cells = normalizedColumns.associate { column ->
                    column.id to row.cells[column.id].orEmpty()
                },
                cellValues = normalizedColumns.associate { column ->
                    val displayValue = row.cells[column.id].orEmpty()
                    val typedValue = row.cellValues[column.id]
                        ?.withColumnType(column.type, displayValue)
                        ?: column.toTypedCellValue(displayValue)
                    column.id to typedValue
                },
                blocks = row.blocks.normalizedChildBlocks(),
            )
        }
        return copy(
            columns = normalizedColumns,
            rows = normalizedRows,
            sort = if (sort.columnId in validColumnIds) sort else PageTableSort(),
            filter = if (filter.columnId in validColumnIds && filter.query.isNotBlank()) {
                filter
            } else {
                PageTableFilter()
            },
            groupByColumnId = groupByColumnId.takeIf { columnId -> columnId in validColumnIds }.orEmpty(),
            viewConfig = viewConfig.copy(
                calendarDateColumnId = viewConfig.calendarDateColumnId.takeIf { columnId -> columnId in validColumnIds }.orEmpty(),
                timelineStartColumnId = viewConfig.timelineStartColumnId.takeIf { columnId -> columnId in validColumnIds }.orEmpty(),
                timelineEndColumnId = viewConfig.timelineEndColumnId.takeIf { columnId -> columnId in validColumnIds }.orEmpty(),
                dashboardMetricColumnId = viewConfig.dashboardMetricColumnId.takeIf { columnId -> columnId in validColumnIds }.orEmpty(),
                dashboardGroupColumnId = viewConfig.dashboardGroupColumnId.takeIf { columnId -> columnId in validColumnIds }.orEmpty(),
            ),
        )
    }

    private fun String.toLegacyBlocks(): List<PageBlock> {
        val lines = lines()
            .ifEmpty { listOf(this) }
            .map { line -> line.trimEnd() }

        return lines.map { line ->
            newBlock(PageBlockType.Text).copy(text = line)
        }.ifEmpty {
            listOf(newBlock(PageBlockType.Text))
        }
    }
}
