package com.changeyourlife.cyl.backend.data

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import javax.sql.DataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal data class PageProjectionSource(
    val pageId: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

internal data class PageContentProjection(
    val source: PageProjectionSource,
    val blocks: List<ProjectedPageBlock>,
    val properties: List<ProjectedPageProperty>,
    val tables: List<ProjectedPageTable>,
    val columns: List<ProjectedTableColumn>,
    val rows: List<ProjectedTableRow>,
    val cells: List<ProjectedTableCell>,
)

internal data class ProjectedPageBlock(
    val id: String,
    val parentBlockId: String?,
    val type: String,
    val text: String,
    val richTextJson: String,
    val mediaJson: String,
    val isChecked: Boolean,
    val sortOrder: Int,
)

internal data class ProjectedPageProperty(
    val id: String,
    val name: String,
    val type: String,
    val value: String,
    val sortOrder: Int,
)

internal data class ProjectedPageTable(
    val id: String,
    val blockId: String,
    val title: String,
    val view: String,
    val viewConfigJson: String,
    val sortJson: String,
    val filterJson: String,
    val groupByColumnId: String,
)

internal data class ProjectedTableColumn(
    val id: String,
    val tableId: String,
    val name: String,
    val type: String,
    val sortOrder: Int,
    val configJson: String,
)

internal data class ProjectedTableRow(
    val id: String,
    val tableId: String,
    val sortOrder: Int,
    val contentJson: String,
    val icon: String,
    val isFavorite: Boolean,
    val metadataJson: String,
)

internal data class ProjectedTableCell(
    val rowId: String,
    val columnId: String,
    val value: String,
    val valueType: String,
    val valueJson: String,
)

internal object PageContentProjectionParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(source: PageProjectionSource): PageContentProjection {
        val collector = ProjectionCollector(source)
        val document = source.content.toDocumentOrNull()
        if (document == null) {
            collector.collectLegacyContent()
            return collector.build()
        }

        document.array("properties").forEachIndexed { index, element ->
            (element as? JsonObject)?.let { property ->
                collector.collectProperty(property, index)
            }
        }
        document.array("blocks").forEachIndexed { index, element ->
            (element as? JsonObject)?.let { block ->
                collector.collectBlock(
                    block = block,
                    parentBlockId = null,
                    sortOrder = index,
                )
            }
        }
        return collector.build()
    }

    private fun String.toDocumentOrNull(): JsonObject? {
        if (isBlank()) return JsonObject(emptyMap())
        return runCatching {
            json.parseToJsonElement(this) as? JsonObject
        }.getOrNull()
    }
}

private class ProjectionCollector(
    private val source: PageProjectionSource,
) {
    private val seenBlockIds = mutableSetOf<String>()
    private val seenPropertyIds = mutableSetOf<String>()
    private val seenColumnIds = mutableSetOf<String>()
    private val seenRowIds = mutableSetOf<String>()
    private val seenCellIds = mutableSetOf<Pair<String, String>>()

    private val blocks = mutableListOf<ProjectedPageBlock>()
    private val properties = mutableListOf<ProjectedPageProperty>()
    private val tables = mutableListOf<ProjectedPageTable>()
    private val columns = mutableListOf<ProjectedTableColumn>()
    private val rows = mutableListOf<ProjectedTableRow>()
    private val cells = mutableListOf<ProjectedTableCell>()

    fun collectLegacyContent() {
        if (source.content.isBlank()) return
        source.content.lines().forEachIndexed { index, line ->
            val blockId = "${source.pageId}:legacy-block:$index"
            blocks += ProjectedPageBlock(
                id = blockId,
                parentBlockId = null,
                type = "Text",
                text = line.trimEnd(),
                richTextJson = "[]",
                mediaJson = "[]",
                isChecked = false,
                sortOrder = index,
            )
        }
    }

    fun collectProperty(property: JsonObject, sortOrder: Int) {
        val id = property.string("id")
            .ifBlank { "${source.pageId}:projection-property:$sortOrder" }
        if (!seenPropertyIds.add(id)) return
        properties += ProjectedPageProperty(
            id = id,
            name = property.string("name"),
            type = property.string("type").ifBlank { "Text" },
            value = property.string("value"),
            sortOrder = sortOrder,
        )
    }

    fun collectBlock(
        block: JsonObject,
        parentBlockId: String?,
        sortOrder: Int,
    ) {
        val id = block.string("id")
            .ifBlank { "${source.pageId}:projection-block:${blocks.size}" }
        if (!seenBlockIds.add(id)) return

        val type = block.string("type").ifBlank { "Text" }
        blocks += ProjectedPageBlock(
            id = id,
            parentBlockId = parentBlockId,
            type = type,
            text = block.string("text"),
            richTextJson = block.elementJson("richTextSpans", "[]"),
            mediaJson = block.elementJson("mediaAttachments", "[]"),
            isChecked = block.boolean("isChecked"),
            sortOrder = sortOrder,
        )

        if (type.equals("DatabaseTable", ignoreCase = true) ||
            type.equals("Table", ignoreCase = true)
        ) {
            collectTable(
                blockId = id,
                table = block.objectOrEmpty("table"),
            )
        }

        block.array("children").forEachIndexed { index, element ->
            (element as? JsonObject)?.let { child ->
                collectBlock(
                    block = child,
                    parentBlockId = id,
                    sortOrder = index,
                )
            }
        }
    }

    private fun collectTable(
        blockId: String,
        table: JsonObject,
    ) {
        tables += ProjectedPageTable(
            id = blockId,
            blockId = blockId,
            title = table.string("title"),
            view = table.string("view").ifBlank { "Table" },
            viewConfigJson = table.elementJson("viewConfig", "{}"),
            sortJson = table.elementJson("sort", "{}"),
            filterJson = table.elementJson("filter", "{}"),
            groupByColumnId = table.string("groupByColumnId"),
        )

        val tableColumns = table.array("columns").mapIndexedNotNull { index, element ->
            val column = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = column.string("id")
                .ifBlank { "$blockId:projection-column:$index" }
            if (!seenColumnIds.add(id)) return@mapIndexedNotNull null
            ProjectedTableColumn(
                id = id,
                tableId = blockId,
                name = column.string("name"),
                type = column.string("type").ifBlank { "Text" },
                sortOrder = index,
                configJson = column.toString(),
            ).also(columns::add)
        }

        table.array("rows").forEachIndexed { index, element ->
            val row = element as? JsonObject ?: return@forEachIndexed
            val rowId = row.string("id")
                .ifBlank { "$blockId:projection-row:$index" }
            if (!seenRowIds.add(rowId)) return@forEachIndexed

            val metadata = row.objectOrEmpty("metadata")
            rows += ProjectedTableRow(
                id = rowId,
                tableId = blockId,
                sortOrder = index,
                contentJson = row.elementJson("blocks", "[]"),
                icon = metadata.string("icon"),
                isFavorite = metadata.boolean("isFavorite"),
                metadataJson = metadata.toString(),
            )

            val legacyCells = row.objectOrEmpty("cells")
            val typedCells = row.objectOrEmpty("cellValues")
            tableColumns.forEach { column ->
                if (!seenCellIds.add(rowId to column.id)) return@forEach
                val fallback = legacyCells.string(column.id)
                val typedValue = typedCells[column.id] as? JsonObject
                val valueJson = typedValue ?: fallbackCellValue(
                    type = column.type,
                    fallback = fallback,
                )
                cells += ProjectedTableCell(
                    rowId = rowId,
                    columnId = column.id,
                    value = displayCellValue(
                        type = column.type,
                        value = typedValue,
                        fallback = fallback,
                    ),
                    valueType = column.type,
                    valueJson = valueJson.toString(),
                )
            }
        }
    }

    fun build(): PageContentProjection {
        return PageContentProjection(
            source = source,
            blocks = blocks,
            properties = properties,
            tables = tables,
            columns = columns,
            rows = rows,
            cells = cells,
        )
    }
}

private fun fallbackCellValue(type: String, fallback: String): JsonObject = buildJsonObject {
    put("type", type)
    when (type) {
        "Number" -> put("number", fallback.trim())
        "Checkbox" -> put("checked", fallback.equals("true", ignoreCase = true))
        "Relation" -> put(
            "relationRowIds",
            JsonArray(
                fallback.split(",")
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .map(::JsonPrimitive),
            ),
        )
        else -> put("text", fallback)
    }
}

private fun displayCellValue(
    type: String,
    value: JsonObject?,
    fallback: String,
): String {
    if (value == null) return fallback
    return when (type) {
        "Number" -> value.string("number").ifBlank { fallback }
        "Checkbox" -> if (value.boolean("checked")) "true" else ""
        "Date" -> value.objectOrEmpty("date").toDateStorageValue().ifBlank { fallback }
        "FilesMedia" -> value.array("files")
            .takeIf(JsonArray::isNotEmpty)
            ?.toString()
            ?: value.string("text").ifBlank { fallback }
        "Relation" -> value.array("relationRowIds")
            .mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
            .filter(String::isNotBlank)
            .joinToString(",")
            .ifBlank { fallback }
        else -> value.string("text").ifBlank { fallback }
    }
}

private fun JsonObject.toDateStorageValue(): String {
    val startDate = string("startDate")
    if (startDate.isBlank()) return ""
    val includeTime = boolean("includeTime")
    val startTime = string("startTime")
    val includeEndDate = boolean("includeEndDate")
    val endDate = string("endDate")
    val endTime = string("endTime")
    val timezoneLabel = string("timezoneLabel").ifBlank { "Local" }
    val isPlainDate = !includeTime &&
        !includeEndDate &&
        startTime.isBlank() &&
        endDate.isBlank() &&
        timezoneLabel == "Local"
    if (isPlainDate) return startDate
    return buildJsonObject {
        put("startDate", startDate)
        put("includeTime", includeTime)
        put("startTime", startTime)
        put("includeEndDate", includeEndDate)
        put("endDate", endDate)
        put("endTime", endTime)
        put("timezoneLabel", timezoneLabel)
    }.toString()
}

private fun JsonObject.array(name: String): JsonArray =
    this[name] as? JsonArray ?: JsonArray(emptyList())

private fun JsonObject.objectOrEmpty(name: String): JsonObject =
    this[name] as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.string(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.boolean(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.booleanOrNull ?: false

private fun JsonObject.elementJson(name: String, fallback: String): String =
    this[name]?.toString() ?: fallback

internal class PostgresPageContentProjectionWriter {
    fun replace(
        connection: Connection,
        source: PageProjectionSource,
    ) {
        val projection = PageContentProjectionParser.parse(source)
        connection.deleteProjection(source.pageId)
        connection.insertBlocks(projection)
        connection.insertProperties(projection)
        connection.insertTables(projection)
        connection.insertColumns(projection)
        connection.insertRows(projection)
        connection.insertCells(projection)
        connection.markProjectionCurrent(source)
    }
}

internal class PostgresPageContentProjectionBackfill(
    private val dataSource: DataSource,
    private val writer: PostgresPageContentProjectionWriter = PostgresPageContentProjectionWriter(),
    private val batchSize: Int = DefaultBatchSize,
) {
    fun run(): Int {
        require(batchSize > 0) { "Projection backfill batch size must be positive." }
        var total = 0
        while (true) {
            val rebuilt = rebuildBatch()
            total += rebuilt
            if (rebuilt < batchSize) return total
        }
    }

    private fun rebuildBatch(): Int {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val sources = connection.selectPendingProjectionSources(batchSize)
                sources.forEach { source -> writer.replace(connection, source) }
                connection.commit()
                return sources.size
            } catch (error: Throwable) {
                runCatching { connection.rollback() }
                throw error
            }
        }
    }

    private companion object {
        const val DefaultBatchSize = 100
    }
}

private fun Connection.selectPendingProjectionSources(limit: Int): List<PageProjectionSource> {
    prepareStatement(
        """
        SELECT id, content, created_at, updated_at, deleted_at
        FROM pages
        WHERE content_projection_updated_at IS DISTINCT FROM updated_at
        ORDER BY updated_at ASC, id ASC
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """.trimIndent(),
    ).use { statement ->
        statement.setInt(1, limit)
        statement.executeQuery().use { resultSet ->
            return buildList {
                while (resultSet.next()) {
                    add(
                        PageProjectionSource(
                            pageId = resultSet.getString("id"),
                            content = resultSet.getString("content"),
                            createdAt = resultSet.getLong("created_at"),
                            updatedAt = resultSet.getLong("updated_at"),
                            deletedAt = resultSet.getLong("deleted_at").let { value ->
                                if (resultSet.wasNull()) null else value
                            },
                        ),
                    )
                }
            }
        }
    }
}

private fun Connection.deleteProjection(pageId: String) {
    prepareStatement("DELETE FROM page_properties WHERE page_id = ?").use { statement ->
        statement.setString(1, pageId)
        statement.executeUpdate()
    }
    prepareStatement("DELETE FROM page_blocks WHERE page_id = ?").use { statement ->
        statement.setString(1, pageId)
        statement.executeUpdate()
    }
}

private fun Connection.insertBlocks(projection: PageContentProjection) {
    executeProjectionBatch(
        """
        INSERT INTO page_blocks (
            id, page_id, parent_block_id, type, text, rich_text_json, media_json,
            is_checked, sort_order, metadata_json, created_at, updated_at, deleted_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '{}', ?, ?, ?)
        """.trimIndent(),
        projection.blocks,
    ) { statement, block ->
        statement.setString(1, block.id)
        statement.setString(2, projection.source.pageId)
        statement.setString(3, block.parentBlockId)
        statement.setString(4, block.type)
        statement.setString(5, block.text)
        statement.setString(6, block.richTextJson)
        statement.setString(7, block.mediaJson)
        statement.setBoolean(8, block.isChecked)
        statement.setInt(9, block.sortOrder)
        statement.setLong(10, projection.source.createdAt)
        statement.setLong(11, projection.source.updatedAt)
        statement.setNullableLong(12, projection.source.deletedAt)
    }
}

private fun Connection.insertProperties(projection: PageContentProjection) {
    executeProjectionBatch(
        """
        INSERT INTO page_properties (
            id, page_id, name, type, value, sort_order, metadata_json,
            created_at, updated_at, deleted_at
        )
        VALUES (?, ?, ?, ?, ?, ?, '{}', ?, ?, ?)
        """.trimIndent(),
        projection.properties,
    ) { statement, property ->
        statement.setString(1, property.id)
        statement.setString(2, projection.source.pageId)
        statement.setString(3, property.name)
        statement.setString(4, property.type)
        statement.setString(5, property.value)
        statement.setInt(6, property.sortOrder)
        statement.setLong(7, projection.source.createdAt)
        statement.setLong(8, projection.source.updatedAt)
        statement.setNullableLong(9, projection.source.deletedAt)
    }
}

private fun Connection.insertTables(projection: PageContentProjection) {
    executeProjectionBatch(
        """
        INSERT INTO page_tables (
            id, page_id, block_id, title, view, view_config_json, sort_json,
            filter_json, group_by_column_id, created_at, updated_at, deleted_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        projection.tables,
    ) { statement, table ->
        statement.setString(1, table.id)
        statement.setString(2, projection.source.pageId)
        statement.setString(3, table.blockId)
        statement.setString(4, table.title)
        statement.setString(5, table.view)
        statement.setString(6, table.viewConfigJson)
        statement.setString(7, table.sortJson)
        statement.setString(8, table.filterJson)
        statement.setString(9, table.groupByColumnId)
        statement.setLong(10, projection.source.createdAt)
        statement.setLong(11, projection.source.updatedAt)
        statement.setNullableLong(12, projection.source.deletedAt)
    }
}

private fun Connection.insertColumns(projection: PageContentProjection) {
    executeProjectionBatch(
        """
        INSERT INTO page_table_columns (
            id, table_id, name, type, sort_order, config_json,
            created_at, updated_at, deleted_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        projection.columns,
    ) { statement, column ->
        statement.setString(1, column.id)
        statement.setString(2, column.tableId)
        statement.setString(3, column.name)
        statement.setString(4, column.type)
        statement.setInt(5, column.sortOrder)
        statement.setString(6, column.configJson)
        statement.setLong(7, projection.source.createdAt)
        statement.setLong(8, projection.source.updatedAt)
        statement.setNullableLong(9, projection.source.deletedAt)
    }
}

private fun Connection.insertRows(projection: PageContentProjection) {
    executeProjectionBatch(
        """
        INSERT INTO page_table_rows (
            id, table_id, sort_order, content_json, icon, is_favorite,
            metadata_json, created_at, updated_at, deleted_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        projection.rows,
    ) { statement, row ->
        statement.setString(1, row.id)
        statement.setString(2, row.tableId)
        statement.setInt(3, row.sortOrder)
        statement.setString(4, row.contentJson)
        statement.setString(5, row.icon)
        statement.setBoolean(6, row.isFavorite)
        statement.setString(7, row.metadataJson)
        statement.setLong(8, projection.source.createdAt)
        statement.setLong(9, projection.source.updatedAt)
        statement.setNullableLong(10, projection.source.deletedAt)
    }
}

private fun Connection.insertCells(projection: PageContentProjection) {
    executeProjectionBatch(
        """
        INSERT INTO page_table_cells (
            row_id, column_id, value, value_type, value_json,
            created_at, updated_at, deleted_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        projection.cells,
    ) { statement, cell ->
        statement.setString(1, cell.rowId)
        statement.setString(2, cell.columnId)
        statement.setString(3, cell.value)
        statement.setString(4, cell.valueType)
        statement.setString(5, cell.valueJson)
        statement.setLong(6, projection.source.createdAt)
        statement.setLong(7, projection.source.updatedAt)
        statement.setNullableLong(8, projection.source.deletedAt)
    }
}

private fun Connection.markProjectionCurrent(source: PageProjectionSource) {
    prepareStatement(
        """
        UPDATE pages
        SET content_projection_updated_at = ?
        WHERE id = ?
          AND updated_at = ?
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, source.updatedAt)
        statement.setString(2, source.pageId)
        statement.setLong(3, source.updatedAt)
        check(statement.executeUpdate() == 1) {
            "Page changed while rebuilding content projection: ${source.pageId}"
        }
    }
}

private fun <T> Connection.executeProjectionBatch(
    sql: String,
    values: List<T>,
    bind: (PreparedStatement, T) -> Unit,
) {
    if (values.isEmpty()) return
    prepareStatement(sql).use { statement ->
        values.forEach { value ->
            bind(statement, value)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
    if (value == null) {
        setNull(index, Types.BIGINT)
    } else {
        setLong(index, value)
    }
}
