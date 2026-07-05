package com.changeyourlife.cyl.data.local.mapper

import com.changeyourlife.cyl.data.local.entity.PageBlockEntity
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.entity.PagePropertyEntity
import com.changeyourlife.cyl.data.local.entity.PageTableCellEntity
import com.changeyourlife.cyl.data.local.entity.PageTableColumnEntity
import com.changeyourlife.cyl.data.local.entity.PageTableEntity
import com.changeyourlife.cyl.data.local.entity.PageTableRowEntity
import com.changeyourlife.cyl.data.local.model.PageContentSnapshot
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableRowMetadata
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.displayValue
import com.changeyourlife.cyl.domain.model.normalizedForType
import com.changeyourlife.cyl.domain.model.toTypedCellValue
import com.changeyourlife.cyl.domain.model.withColumnType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PageContentProjection(
    val blocks: List<PageBlockEntity>,
    val properties: List<PagePropertyEntity>,
    val tables: List<PageTableEntity>,
    val columns: List<PageTableColumnEntity>,
    val rows: List<PageTableRowEntity>,
    val cells: List<PageTableCellEntity>,
)

private val pageContentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun PageEntity.toContentProjection(): PageContentProjection? {
    val document = decodeProjectionDocument(
        pageId = id,
        content = content,
    )
    val collector = PageContentProjectionCollector(
        pageId = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
    document.properties.forEachIndexed { index, property ->
        collector.properties += PagePropertyEntity(
            id = property.id,
            pageId = id,
            name = property.name,
            type = property.type.name,
            value = property.value,
            sortOrder = index,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )
    }
    document.blocks.forEachIndexed { index, block ->
        collector.collectBlock(
            block = block,
            parentBlockId = null,
            sortOrder = index,
        )
    }
    return collector.toProjection()
}

fun PageContentSnapshot.toDocument(): PageBlockDocument {
    val tablesByBlockId = tables.associateBy { table -> table.blockId }
    val columnsByTableId = columns.groupBy { column -> column.tableId }
    val rowsByTableId = rows.groupBy { row -> row.tableId }
    val cellsByRowId = cells.groupBy { cell -> cell.rowId }
    val childrenByParentId = blocks.groupBy { block -> block.parentBlockId }

    return PageBlockDocument(
        properties = properties
            .sortedBy { property -> property.sortOrder }
            .map { property -> property.toDomain() },
        blocks = childrenByParentId[null].orEmpty()
            .sortedBy { block -> block.sortOrder }
            .map { block ->
                block.toDomain(
                    childrenByParentId = childrenByParentId,
                    tablesByBlockId = tablesByBlockId,
                    columnsByTableId = columnsByTableId,
                    rowsByTableId = rowsByTableId,
                    cellsByRowId = cellsByRowId,
                )
            },
    )
}

private fun decodeProjectionDocument(
    pageId: String,
    content: String,
): PageBlockDocument {
    if (content.isBlank()) return PageBlockDocument()
    return runCatching {
        pageContentJson.decodeFromString<PageBlockDocument>(content)
    }.getOrElse { throwable ->
        if (throwable is SerializationException || throwable is IllegalArgumentException) {
            PageBlockDocument(blocks = content.toLegacyBlocks(pageId))
        } else {
            throw throwable
        }
    }
}

private fun String.toLegacyBlocks(pageId: String): List<PageBlock> {
    return lines()
        .ifEmpty { listOf(this) }
        .mapIndexed { index, line ->
            PageBlock(
                id = "$pageId:legacy-block:$index",
                type = PageBlockType.Text,
                text = line.trimEnd(),
            )
        }
        .ifEmpty {
            listOf(
                PageBlock(
                    id = "$pageId:legacy-block:0",
                    type = PageBlockType.Text,
                ),
            )
        }
}

private fun PagePropertyEntity.toDomain(): PageProperty {
    return PageProperty(
        id = id,
        name = name,
        type = type.enumValueOrDefault(PagePropertyType.Text),
        value = value,
    )
}

private fun PageBlockEntity.toDomain(
    childrenByParentId: Map<String?, List<PageBlockEntity>>,
    tablesByBlockId: Map<String, PageTableEntity>,
    columnsByTableId: Map<String, List<PageTableColumnEntity>>,
    rowsByTableId: Map<String, List<PageTableRowEntity>>,
    cellsByRowId: Map<String, List<PageTableCellEntity>>,
): PageBlock {
    val childBlocks = childrenByParentId[id].orEmpty()
        .sortedBy { block -> block.sortOrder }
        .map { block ->
            block.toDomain(
                childrenByParentId = childrenByParentId,
                tablesByBlockId = tablesByBlockId,
                columnsByTableId = columnsByTableId,
                rowsByTableId = rowsByTableId,
                cellsByRowId = cellsByRowId,
            )
        }

    return PageBlock(
        id = id,
        type = type.enumValueOrDefault(PageBlockType.Text),
        text = text,
        richTextSpans = richTextJson.decodeList<PageTextSpan>(),
        mediaAttachments = mediaJson.decodeList<PageMediaAttachment>(),
        isChecked = isChecked,
        table = tablesByBlockId[id]?.toDomain(
            columns = columnsByTableId[id].orEmpty(),
            rows = rowsByTableId[id].orEmpty(),
            cellsByRowId = cellsByRowId,
        ) ?: PageTable(),
        children = childBlocks,
    )
}

private fun PageTableEntity.toDomain(
    columns: List<PageTableColumnEntity>,
    rows: List<PageTableRowEntity>,
    cellsByRowId: Map<String, List<PageTableCellEntity>>,
): PageTable {
    val domainColumns = columns
        .sortedBy { column -> column.sortOrder }
        .map { column -> column.toDomain() }
    val columnsById = domainColumns.associateBy { column -> column.id }
    return PageTable(
        title = title,
        view = view.enumValueOrDefault(PageTableView.Table),
        viewConfig = viewConfigJson.decodeObject(PageTableViewConfig()),
        columns = domainColumns,
        rows = rows
            .sortedBy { row -> row.sortOrder }
            .map { row ->
                row.toDomain(
                    cells = cellsByRowId[row.id].orEmpty(),
                    columnsById = columnsById,
                )
            },
        sort = sortJson.decodeObject(PageTableSort()),
        filter = filterJson.decodeObject(PageTableFilter()),
        groupByColumnId = groupByColumnId,
    )
}

private fun PageTableColumnEntity.toDomain(): PageTableColumn {
    val decoded = configJson.decodeObject(
        PageTableColumn(
            id = id,
            name = name,
            type = type.enumValueOrDefault(PageTableColumnType.Text),
        ),
    )
    return decoded.copy(
        id = id,
        name = name,
        type = type.enumValueOrDefault(decoded.type),
        config = decoded.config.normalizedForType(type.enumValueOrDefault(decoded.type)),
    )
}

private fun PageTableRowEntity.toDomain(
    cells: List<PageTableCellEntity>,
    columnsById: Map<String, PageTableColumn>,
): PageTableRow {
    val cellTextValues = cells.associate { cell -> cell.columnId to cell.value }
    val typedValues = cells.associate { cell ->
        val type = columnsById[cell.columnId]?.type ?: cell.valueType.enumValueOrDefault(PageTableColumnType.Text)
        val fallback = cell.value.toTypedCellValue(type)
        val typedValue = cell.valueJson
            .decodeObject(fallback)
            .withColumnType(type, cell.value)
        cell.columnId to typedValue
    }
    val decodedMetadata = metadataJson.decodeObject(PageTableRowMetadata())
    return PageTableRow(
        id = id,
        cells = cellTextValues,
        cellValues = typedValues,
        metadata = decodedMetadata.copy(
            icon = icon.ifBlank { decodedMetadata.icon },
            isFavorite = isFavorite || decodedMetadata.isFavorite,
            createdAt = decodedMetadata.createdAt.takeIf { value -> value > 0 } ?: createdAt,
            lastEditedAt = decodedMetadata.lastEditedAt.takeIf { value -> value > 0 } ?: updatedAt,
        ),
        blocks = contentJson.decodeList<PageBlock>(),
    )
}

private inline fun <reified T : Enum<T>> String.enumValueOrDefault(defaultValue: T): T {
    return runCatching { enumValueOf<T>(this) }.getOrDefault(defaultValue)
}

private inline fun <reified T> String.decodeObject(defaultValue: T): T {
    return runCatching {
        pageContentJson.decodeFromString<T>(this)
    }.getOrDefault(defaultValue)
}

private inline fun <reified T> String.decodeList(): List<T> {
    return runCatching {
        pageContentJson.decodeFromString<List<T>>(this)
    }.getOrDefault(emptyList())
}

private class PageContentProjectionCollector(
    private val pageId: String,
    private val createdAt: Long,
    private val updatedAt: Long,
    private val deletedAt: Long?,
) {
    val blocks = mutableListOf<PageBlockEntity>()
    val properties = mutableListOf<PagePropertyEntity>()
    val tables = mutableListOf<PageTableEntity>()
    val columns = mutableListOf<PageTableColumnEntity>()
    val rows = mutableListOf<PageTableRowEntity>()
    val cells = mutableListOf<PageTableCellEntity>()

    fun collectBlock(
        block: PageBlock,
        parentBlockId: String?,
        sortOrder: Int,
    ) {
        blocks += PageBlockEntity(
            id = block.id,
            pageId = pageId,
            parentBlockId = parentBlockId,
            type = block.type.name,
            text = block.text,
            richTextJson = pageContentJson.encodeToString(block.richTextSpans),
            mediaJson = pageContentJson.encodeToString(block.mediaAttachments),
            isChecked = block.isChecked,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )

        if (block.type == PageBlockType.DatabaseTable || block.type == PageBlockType.Table) {
            collectTable(
                blockId = block.id,
                table = block.table,
            )
        }

        block.children.forEachIndexed { index, child ->
            collectBlock(
                block = child,
                parentBlockId = block.id,
                sortOrder = index,
            )
        }
    }

    private fun collectTable(
        blockId: String,
        table: PageTable,
    ) {
        val tableId = blockId
        tables += PageTableEntity(
            id = tableId,
            pageId = pageId,
            blockId = blockId,
            title = table.title,
            view = table.view.name,
            viewConfigJson = pageContentJson.encodeToString(table.viewConfig),
            sortJson = pageContentJson.encodeToString(table.sort),
            filterJson = pageContentJson.encodeToString(table.filter),
            groupByColumnId = table.groupByColumnId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )
        table.columns.forEachIndexed { index, column ->
            columns += column.toEntity(
                tableId = tableId,
                sortOrder = index,
            )
        }
        val columnsById = table.columns.associateBy { column -> column.id }
        table.rows.forEachIndexed { index, row ->
            val rowMetadata = row.metadata
            rows += PageTableRowEntity(
                id = row.id,
                tableId = tableId,
                sortOrder = index,
                contentJson = pageContentJson.encodeToString(row.blocks),
                icon = rowMetadata.icon,
                isFavorite = rowMetadata.isFavorite,
                metadataJson = pageContentJson.encodeToString(rowMetadata),
                createdAt = createdAt,
                updatedAt = updatedAt,
                deletedAt = deletedAt,
            )
            table.columns.forEach { column ->
                val columnId = column.id
                val value = row.cells[columnId].orEmpty()
                val typedValue = row.cellValues[columnId]
                    ?.withColumnType(column.type, value)
                    ?: column.toTypedCellValue(value)
                cells += PageTableCellEntity(
                    rowId = row.id,
                    columnId = columnId,
                    value = typedValue.displayValue(value),
                    valueType = columnsById[columnId]?.type?.name ?: typedValue.type.name,
                    valueJson = pageContentJson.encodeToString(typedValue),
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    deletedAt = deletedAt,
                )
            }
        }
    }

    private fun PageTableColumn.toEntity(
        tableId: String,
        sortOrder: Int,
    ): PageTableColumnEntity {
        return PageTableColumnEntity(
            id = id,
            tableId = tableId,
            name = name,
            type = type.name,
            sortOrder = sortOrder,
            configJson = pageContentJson.encodeToString(this),
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )
    }

    fun toProjection(): PageContentProjection {
        return PageContentProjection(
            blocks = blocks,
            properties = properties,
            tables = tables,
            columns = columns,
            rows = rows,
            cells = cells,
        )
    }
}
