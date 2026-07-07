package com.changeyourlife.cyl.backend.data

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object PageContentJsonMutator {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun updateBlockText(
        content: String,
        blockId: String,
        text: String,
    ): String? {
        if (blockId.isBlank()) return null
        val root = content.toJsonObjectOrNull() ?: return null
        val result = updateBlocks(root["blocks"]) { block ->
            if (block.stringValue("id") == blockId) {
                Mutation(block.withString("text", text), changed = true)
            } else {
                Mutation(block)
            }
        }
        if (!result.changed) return null
        return root.withElement("blocks", result.value).toString()
    }

    fun updateBlock(
        content: String,
        blockId: String,
        text: String?,
        richTextSpans: JsonArray?,
        mediaAttachments: JsonArray?,
        isChecked: Boolean?,
    ): String? {
        if (blockId.isBlank()) return null
        val root = content.toJsonObjectOrNull() ?: return null
        val result = updateBlocks(root["blocks"]) { block ->
            if (block.stringValue("id") != blockId) return@updateBlocks Mutation(block)
            var updatedBlock = block
            text?.let { updatedBlock = updatedBlock.withString("text", it) }
            richTextSpans?.let { updatedBlock = updatedBlock.withElement("richTextSpans", it) }
            mediaAttachments?.let { updatedBlock = updatedBlock.withElement("mediaAttachments", it) }
            isChecked?.let { updatedBlock = updatedBlock.withElement("isChecked", JsonPrimitive(it)) }
            Mutation(updatedBlock, changed = updatedBlock != block)
        }
        if (!result.changed) return null
        return root.withElement("blocks", result.value).toString()
    }

    fun updatePropertyValue(
        content: String,
        propertyId: String,
        propertyName: String,
        value: String,
    ): String? {
        if (propertyId.isBlank() && propertyName.isBlank()) return null
        val root = content.toJsonObjectOrNull() ?: return null
        val properties = root["properties"] as? JsonArray ?: JsonArray(emptyList())
        var changed = false
        val normalizedPropertyName = propertyName.trim()
        val updatedProperties = properties.map { element ->
            val property = element as? JsonObject ?: return@map element
            val matchesId = propertyId.isNotBlank() && property.stringValue("id") == propertyId
            val matchesName = normalizedPropertyName.isNotBlank() &&
                property.stringValue("name").equals(normalizedPropertyName, ignoreCase = true)
            if (matchesId || matchesName) {
                changed = true
                property.withString("value", value)
            } else {
                property
            }
        }
        if (!changed) return null
        return root.withElement("properties", JsonArray(updatedProperties)).toString()
    }

    fun updateTableCellValue(
        content: String,
        rowId: String,
        columnId: String,
        value: String,
        valueJson: JsonObject?,
    ): String? {
        if (rowId.isBlank() || columnId.isBlank()) return null
        val root = content.toJsonObjectOrNull() ?: return null
        val result = updateBlocks(root["blocks"]) { block ->
            val table = block["table"] as? JsonObject ?: return@updateBlocks Mutation(block)
            val columns = table["columns"] as? JsonArray ?: JsonArray(emptyList())
            val hasColumn = columns.any { column ->
                (column as? JsonObject)?.stringValue("id") == columnId
            }
            if (!hasColumn) return@updateBlocks Mutation(block)

            val rows = table["rows"] as? JsonArray ?: JsonArray(emptyList())
            var changed = false
            val updatedRows = rows.map { element ->
                val row = element as? JsonObject ?: return@map element
                if (row.stringValue("id") != rowId) return@map row
                val cells = row["cells"] as? JsonObject ?: JsonObject(emptyMap())
                val cellValues = row["cellValues"] as? JsonObject ?: JsonObject(emptyMap())
                changed = true
                val rowWithCell = row.withElement("cells", cells.withString(columnId, value))
                if (valueJson == null) {
                    rowWithCell
                } else {
                    rowWithCell.withElement("cellValues", cellValues.withElement(columnId, valueJson))
                }
            }
            if (!changed) return@updateBlocks Mutation(block)

            Mutation(
                block.withElement("table", table.withElement("rows", JsonArray(updatedRows))),
                changed = true,
            )
        }
        if (!result.changed) return null
        return root.withElement("blocks", result.value).toString()
    }

    fun updateTable(
        content: String,
        tableBlockId: String,
        title: String?,
        view: String?,
        calendarDateColumnId: String?,
        timelineStartColumnId: String?,
        timelineEndColumnId: String?,
        dashboardMetricColumnId: String?,
        dashboardGroupColumnId: String?,
        sortColumnId: String?,
        sortDirection: String?,
        filterColumnId: String?,
        filterQuery: String?,
        filterOperator: String?,
        groupByColumnId: String?,
    ): String? {
        if (tableBlockId.isBlank()) return null
        return mutateTable(content, tableBlockId) { table ->
            var updatedTable = table
            title?.let { updatedTable = updatedTable.withString("title", it) }
            view?.let { updatedTable = updatedTable.withString("view", it) }
            groupByColumnId?.let { updatedTable = updatedTable.withString("groupByColumnId", it) }

            if (
                calendarDateColumnId != null ||
                timelineStartColumnId != null ||
                timelineEndColumnId != null ||
                dashboardMetricColumnId != null ||
                dashboardGroupColumnId != null
            ) {
                var viewConfig = updatedTable["viewConfig"] as? JsonObject ?: JsonObject(emptyMap())
                calendarDateColumnId?.let { viewConfig = viewConfig.withString("calendarDateColumnId", it) }
                timelineStartColumnId?.let { viewConfig = viewConfig.withString("timelineStartColumnId", it) }
                timelineEndColumnId?.let { viewConfig = viewConfig.withString("timelineEndColumnId", it) }
                dashboardMetricColumnId?.let { viewConfig = viewConfig.withString("dashboardMetricColumnId", it) }
                dashboardGroupColumnId?.let { viewConfig = viewConfig.withString("dashboardGroupColumnId", it) }
                updatedTable = updatedTable.withElement("viewConfig", viewConfig)
            }

            if (sortColumnId != null || sortDirection != null) {
                var sort = updatedTable["sort"] as? JsonObject ?: JsonObject(emptyMap())
                sortColumnId?.let { sort = sort.withString("columnId", it) }
                sortDirection?.let { sort = sort.withString("direction", it) }
                updatedTable = updatedTable.withElement("sort", sort)
            }

            if (filterColumnId != null || filterQuery != null || filterOperator != null) {
                var filter = updatedTable["filter"] as? JsonObject ?: JsonObject(emptyMap())
                filterColumnId?.let { filter = filter.withString("columnId", it) }
                filterQuery?.let { filter = filter.withString("query", it) }
                filterOperator
                    ?.takeIf { operator -> operator in ValidTableFilterOperators }
                    ?.let { filter = filter.withString("operator", it) }
                updatedTable = updatedTable.withElement("filter", filter)
            }

            updatedTable.takeUnless { it == table }
        }
    }

    fun updateTableColumn(
        content: String,
        tableBlockId: String,
        columnId: String,
        name: String?,
        type: String?,
        config: JsonObject?,
        dateFormat: String?,
        timeFormat: String?,
        dateReminder: String?,
        timezoneLabel: String?,
        formula: String?,
        relationTargetTableId: String?,
        rollupRelationColumnId: String?,
        rollupTargetColumnId: String?,
        rollupAggregation: String?,
    ): String? {
        if (tableBlockId.isBlank() || columnId.isBlank()) return null
        return mutateTable(content, tableBlockId) { table ->
            val columns = table["columns"] as? JsonArray ?: JsonArray(emptyList())
            val originalColumn = columns.firstNotNullOfOrNull { element ->
                (element as? JsonObject)?.takeIf { column -> column.stringValue("id") == columnId }
            } ?: return@mutateTable null
            var changed = false
            val updatedColumns = columns.map { element ->
                val column = element as? JsonObject ?: return@map element
                if (column.stringValue("id") != columnId) return@map column
                var updatedColumn = column
                name?.let { updatedColumn = updatedColumn.withString("name", it) }
                type?.let { updatedColumn = updatedColumn.withString("type", it) }
                config?.let { updatedColumn = updatedColumn.withElement("config", it) }
                dateFormat?.let { updatedColumn = updatedColumn.withString("dateFormat", it) }
                timeFormat?.let { updatedColumn = updatedColumn.withString("timeFormat", it) }
                dateReminder?.let { updatedColumn = updatedColumn.withString("dateReminder", it) }
                timezoneLabel?.let { updatedColumn = updatedColumn.withString("timezoneLabel", it) }
                formula?.let { updatedColumn = updatedColumn.withString("formula", it) }
                relationTargetTableId?.let { updatedColumn = updatedColumn.withString("relationTargetTableId", it) }
                rollupRelationColumnId?.let { updatedColumn = updatedColumn.withString("rollupRelationColumnId", it) }
                rollupTargetColumnId?.let { updatedColumn = updatedColumn.withString("rollupTargetColumnId", it) }
                rollupAggregation?.let { updatedColumn = updatedColumn.withString("rollupAggregation", it) }
                if (updatedColumn != column) changed = true
                updatedColumn
            }
            val editedColumn = updatedColumns
                .filterIsInstance<JsonObject>()
                .firstOrNull { column -> column.stringValue("id") == columnId }
                ?: return@mutateTable null
            val effectiveType = editedColumn.stringValue("type").ifBlank { "Text" }
            validateTableColumnPatch(
                columns = updatedColumns.filterIsInstance<JsonObject>(),
                effectiveType = effectiveType,
                formula = formula,
                relationTargetTableId = relationTargetTableId,
                rollupRelationColumnId = rollupRelationColumnId,
                rollupTargetColumnId = rollupTargetColumnId,
            )
            val relationTargetChanged = relationTargetTableId != null &&
                originalColumn.stringValue("relationTargetTableId") != relationTargetTableId
            val sanitizedColumns = updatedColumns.map { element ->
                val column = element as? JsonObject ?: return@map element
                var sanitized = column
                if (column.stringValue("id") == columnId) {
                    if (effectiveType != "Relation") {
                        sanitized = sanitized.withString("relationTargetTableId", "")
                    }
                    if (effectiveType != "Rollup") {
                        sanitized = sanitized
                            .withString("rollupRelationColumnId", "")
                            .withString("rollupTargetColumnId", "")
                    } else {
                        val relationColumnId = sanitized.stringValue("rollupRelationColumnId")
                        val validRelationColumn = updatedColumns.any { candidate ->
                            (candidate as? JsonObject)?.let { candidateColumn ->
                                candidateColumn.stringValue("id") == relationColumnId &&
                                    candidateColumn.stringValue("type") == "Relation"
                            } ?: false
                        }
                        if (!validRelationColumn) {
                            sanitized = sanitized
                                .withString("rollupRelationColumnId", "")
                                .withString("rollupTargetColumnId", "")
                        }
                    }
                    if (effectiveType != "Formula") {
                        sanitized = sanitized.withString("formula", "")
                    }
                } else if (relationTargetChanged && column.stringValue("rollupRelationColumnId") == columnId) {
                    sanitized = sanitized.withString("rollupTargetColumnId", "")
                }
                if (sanitized != column) changed = true
                sanitized
            }
            var updatedTable = table.withElement("columns", JsonArray(sanitizedColumns))
            if (relationTargetChanged) {
                val rows = table["rows"] as? JsonArray ?: JsonArray(emptyList())
                val clearedRows = rows.map { element ->
                    val row = element as? JsonObject ?: return@map element
                    val cells = row["cells"] as? JsonObject ?: JsonObject(emptyMap())
                    val cellValues = row["cellValues"] as? JsonObject ?: JsonObject(emptyMap())
                    row
                        .withElement("cells", cells.withString(columnId, ""))
                        .withElement("cellValues", cellValues.withElement(columnId, emptyRelationCellValue()))
                }
                updatedTable = updatedTable.withElement("rows", JsonArray(clearedRows))
                changed = true
            }
            if (!changed) return@mutateTable null
            updatedTable
        }
    }

    fun addBlock(
        content: String,
        blockId: String,
        type: String,
        text: String,
        parentBlockId: String,
        afterBlockId: String,
        targetIndex: Int?,
    ): String? {
        val root = content.toJsonObjectOrNull() ?: return null
        val block = JsonObject(
            mapOf(
                "id" to JsonPrimitive(blockId.ifBlank { UUID.randomUUID().toString() }),
                "type" to JsonPrimitive(type.ifBlank { "Text" }),
                "text" to JsonPrimitive(text),
                "children" to JsonArray(emptyList()),
            ),
        )
        if (parentBlockId.isBlank()) {
            val blocks = root["blocks"] as? JsonArray ?: JsonArray(emptyList())
            return root.withElement(
                "blocks",
                blocks.insertElement(block, afterBlockId = afterBlockId, targetIndex = targetIndex),
            ).toString()
        }

        val result = updateBlocks(root["blocks"]) { existingBlock ->
            if (existingBlock.stringValue("id") != parentBlockId) return@updateBlocks Mutation(existingBlock)
            val children = existingBlock["children"] as? JsonArray ?: JsonArray(emptyList())
            Mutation(
                existingBlock.withElement(
                    "children",
                    children.insertElement(block, afterBlockId = afterBlockId, targetIndex = targetIndex),
                ),
                changed = true,
            )
        }
        if (!result.changed) return null
        return root.withElement("blocks", result.value).toString()
    }

    fun deleteBlock(content: String, blockId: String): String? {
        if (blockId.isBlank()) return null
        val root = content.toJsonObjectOrNull() ?: return null
        val result = deleteBlockFromList(root["blocks"], blockId)
        if (!result.changed) return null
        return root.withElement("blocks", result.value).toString()
    }

    fun moveBlock(content: String, blockId: String, targetIndex: Int): String? {
        if (blockId.isBlank()) return null
        val root = content.toJsonObjectOrNull() ?: return null
        val result = moveBlockInList(root["blocks"], blockId, targetIndex)
        if (!result.changed) return null
        return root.withElement("blocks", result.value).toString()
    }

    fun addProperty(
        content: String,
        propertyId: String,
        name: String,
        type: String,
        value: String,
        targetIndex: Int?,
    ): String? {
        if (name.isBlank()) return null
        val root = content.toJsonObjectOrNull() ?: return null
        val property = JsonObject(
            mapOf(
                "id" to JsonPrimitive(propertyId.ifBlank { UUID.randomUUID().toString() }),
                "name" to JsonPrimitive(name),
                "type" to JsonPrimitive(type.ifBlank { "Text" }),
                "value" to JsonPrimitive(value),
            ),
        )
        val properties = root["properties"] as? JsonArray ?: JsonArray(emptyList())
        return root.withElement(
            "properties",
            properties.insertElement(property, afterBlockId = "", targetIndex = targetIndex),
        ).toString()
    }

    fun deleteProperty(content: String, propertyId: String, propertyName: String): String? {
        if (propertyId.isBlank() && propertyName.isBlank()) return null
        val root = content.toJsonObjectOrNull() ?: return null
        val properties = root["properties"] as? JsonArray ?: JsonArray(emptyList())
        val updated = properties.filterNot { element ->
            val property = element as? JsonObject ?: return@filterNot false
            val matchesId = propertyId.isNotBlank() && property.stringValue("id") == propertyId
            val matchesName = propertyName.isNotBlank() && property.stringValue("name").equals(propertyName, ignoreCase = true)
            matchesId || matchesName
        }
        if (updated.size == properties.size) return null
        return root.withElement("properties", JsonArray(updated)).toString()
    }

    fun moveProperty(content: String, propertyId: String, propertyName: String, targetIndex: Int): String? {
        if (propertyId.isBlank() && propertyName.isBlank()) return null
        val root = content.toJsonObjectOrNull() ?: return null
        val properties = root["properties"] as? JsonArray ?: JsonArray(emptyList())
        val index = properties.indexOfFirst { element ->
            val property = element as? JsonObject ?: return@indexOfFirst false
            val matchesId = propertyId.isNotBlank() && property.stringValue("id") == propertyId
            val matchesName = propertyName.isNotBlank() && property.stringValue("name").equals(propertyName, ignoreCase = true)
            matchesId || matchesName
        }
        if (index < 0) return null
        return root.withElement("properties", properties.moveElement(index, targetIndex)).toString()
    }

    fun addTableColumn(
        content: String,
        tableBlockId: String,
        columnId: String,
        name: String,
        type: String,
        config: JsonObject?,
        cellValues: Map<String, String>,
        targetIndex: Int?,
    ): String? {
        if (tableBlockId.isBlank() || name.isBlank()) return null
        return mutateTable(content, tableBlockId) { table ->
            val newColumnId = columnId.ifBlank { UUID.randomUUID().toString() }
            val column = JsonObject(
                buildMap {
                    put("id", JsonPrimitive(newColumnId))
                    put("name", JsonPrimitive(name))
                    put("type", JsonPrimitive(type.ifBlank { "Text" }))
                    config?.let { put("config", it) }
                },
            )
            val columns = table["columns"] as? JsonArray ?: JsonArray(emptyList())
            val rows = table["rows"] as? JsonArray ?: JsonArray(emptyList())
            val updatedRows = rows.map { element ->
                val row = element as? JsonObject ?: return@map element
                val rowId = row.stringValue("id")
                val cells = row["cells"] as? JsonObject ?: JsonObject(emptyMap())
                val typedValues = row["cellValues"] as? JsonObject ?: JsonObject(emptyMap())
                row
                    .withElement("cells", cells.withString(newColumnId, cellValues[rowId].orEmpty()))
                    .withElement("cellValues", typedValues.withElement(newColumnId, JsonObject(emptyMap())))
            }
            table
                .withElement("columns", columns.insertElement(column, afterBlockId = "", targetIndex = targetIndex))
                .withElement("rows", JsonArray(updatedRows))
        }
    }

    fun deleteTableColumn(content: String, tableBlockId: String, columnId: String): String? {
        if (tableBlockId.isBlank() || columnId.isBlank()) return null
        return mutateTable(content, tableBlockId) { table ->
            val columns = table["columns"] as? JsonArray ?: JsonArray(emptyList())
            if (columns.size <= 1) return@mutateTable null
            val primaryColumnId = (columns.firstOrNull() as? JsonObject)?.stringValue("id")
            if (primaryColumnId == columnId) return@mutateTable null
            val updatedColumns = columns.filterNot { element ->
                (element as? JsonObject)?.stringValue("id") == columnId
            }
            if (updatedColumns.size == columns.size) return@mutateTable null
            val rows = table["rows"] as? JsonArray ?: JsonArray(emptyList())
            val updatedRows = rows.map { element ->
                val row = element as? JsonObject ?: return@map element
                val cells = row["cells"] as? JsonObject ?: JsonObject(emptyMap())
                val typedValues = row["cellValues"] as? JsonObject ?: JsonObject(emptyMap())
                row
                    .withElement("cells", cells.withoutKey(columnId))
                    .withElement("cellValues", typedValues.withoutKey(columnId))
            }
            table
                .withElement("columns", JsonArray(updatedColumns))
                .withElement("rows", JsonArray(updatedRows))
        }
    }

    fun moveTableColumn(content: String, tableBlockId: String, columnId: String, targetIndex: Int): String? {
        if (tableBlockId.isBlank() || columnId.isBlank()) return null
        return mutateTable(content, tableBlockId) { table ->
            val columns = table["columns"] as? JsonArray ?: JsonArray(emptyList())
            val index = columns.indexOfFirst { element ->
                (element as? JsonObject)?.stringValue("id") == columnId
            }
            if (index <= 0) return@mutateTable null
            val updatedColumns = columns.moveElement(index, targetIndex.coerceAtLeast(1))
            if (updatedColumns == columns) return@mutateTable null
            table.withElement("columns", updatedColumns)
        }
    }

    fun addTableRow(
        content: String,
        tableBlockId: String,
        rowId: String,
        cells: Map<String, String>,
        cellValues: Map<String, JsonObject>,
        metadata: JsonObject,
        targetIndex: Int?,
    ): String? {
        if (tableBlockId.isBlank()) return null
        return mutateTable(content, tableBlockId) { table ->
            val columns = table["columns"] as? JsonArray ?: JsonArray(emptyList())
            val rows = table["rows"] as? JsonArray ?: JsonArray(emptyList())
            val newRowId = uniqueRowId(rowId, rows)
            val normalizedCells = columns.associate { element ->
                val column = element as? JsonObject
                val id = column?.stringValue("id").orEmpty()
                id to JsonPrimitive(cells[id].orEmpty())
            }.filterKeys { id -> id.isNotBlank() }
            val normalizedCellValues = columns.associate { element ->
                val column = element as? JsonObject
                val id = column?.stringValue("id").orEmpty()
                id to (cellValues[id] ?: JsonObject(emptyMap()))
            }.filterKeys { id -> id.isNotBlank() }
            val row = JsonObject(
                mapOf(
                    "id" to JsonPrimitive(newRowId),
                    "cells" to JsonObject(normalizedCells),
                    "cellValues" to JsonObject(normalizedCellValues),
                    "metadata" to metadata,
                    "blocks" to JsonArray(emptyList()),
                ),
            )
            table.withElement("rows", rows.insertElement(row, afterBlockId = "", targetIndex = targetIndex))
        }
    }

    fun updateTableRow(
        content: String,
        tableBlockId: String,
        rowId: String,
        blocks: JsonArray?,
        metadata: JsonObject?,
    ): String? {
        if (tableBlockId.isBlank() || rowId.isBlank() || (blocks == null && metadata == null)) return null
        return mutateTable(content, tableBlockId) { table ->
            val rows = table["rows"] as? JsonArray ?: JsonArray(emptyList())
            var changed = false
            val updatedRows = rows.map { element ->
                val row = element as? JsonObject ?: return@map element
                if (row.stringValue("id") == rowId) {
                    var updatedRow = row
                    blocks?.let { updatedRow = updatedRow.withElement("blocks", it) }
                    metadata?.let { updatedRow = updatedRow.withElement("metadata", it) }
                    changed = true
                    updatedRow
                } else {
                    row
                }
            }
            if (!changed) return@mutateTable null
            table.withElement("rows", JsonArray(updatedRows))
        }
    }

    fun deleteTableRow(content: String, tableBlockId: String, rowId: String): String? {
        if (tableBlockId.isBlank() || rowId.isBlank()) return null
        return mutateTable(content, tableBlockId) { table ->
            val rows = table["rows"] as? JsonArray ?: JsonArray(emptyList())
            val updatedRows = rows.filterNot { element ->
                (element as? JsonObject)?.stringValue("id") == rowId
            }
            if (updatedRows.size == rows.size) return@mutateTable null
            table.withElement("rows", JsonArray(updatedRows))
        }
    }

    fun moveTableRow(content: String, tableBlockId: String, rowId: String, targetIndex: Int): String? {
        if (tableBlockId.isBlank() || rowId.isBlank()) return null
        return mutateTable(content, tableBlockId) { table ->
            val rows = table["rows"] as? JsonArray ?: JsonArray(emptyList())
            val index = rows.indexOfFirst { element ->
                (element as? JsonObject)?.stringValue("id") == rowId
            }
            if (index < 0) return@mutateTable null
            table.withElement("rows", rows.moveElement(index, targetIndex))
        }
    }

    private fun validateTableColumnPatch(
        columns: List<JsonObject>,
        effectiveType: String,
        formula: String?,
        relationTargetTableId: String?,
        rollupRelationColumnId: String?,
        rollupTargetColumnId: String?,
    ) {
        require(formula == null || formula.isBlank() || effectiveType == "Formula") {
            "Formula can only be set on Formula properties."
        }
        require(relationTargetTableId == null || relationTargetTableId.isBlank() || effectiveType == "Relation") {
            "Relation target can only be set on Relation properties."
        }
        val hasRollupPatch = rollupRelationColumnId?.isNotBlank() == true || rollupTargetColumnId?.isNotBlank() == true
        require(!hasRollupPatch || effectiveType == "Rollup") {
            "Rollup config can only be set on Rollup properties."
        }
        if (!hasRollupPatch) return

        val relationColumnId = rollupRelationColumnId.orEmpty()
        require(relationColumnId.isNotBlank()) {
            "Rollup relation property is required."
        }
        val relationColumn = columns.firstOrNull { column -> column.stringValue("id") == relationColumnId }
        require(relationColumn != null) {
            "Rollup relation property does not exist."
        }
        require(relationColumn.stringValue("type") == "Relation") {
            "Rollup relation property must be a Relation property."
        }
    }

    private fun mutateTable(
        content: String,
        tableBlockId: String,
        transform: (JsonObject) -> JsonObject?,
    ): String? {
        val root = content.toJsonObjectOrNull() ?: return null
        val result = updateBlocks(root["blocks"]) { block ->
            if (block.stringValue("id") != tableBlockId) return@updateBlocks Mutation(block)
            val table = block["table"] as? JsonObject ?: return@updateBlocks Mutation(block)
            val updatedTable = transform(table) ?: return@updateBlocks Mutation(block)
            Mutation(block.withElement("table", updatedTable), changed = true)
        }
        if (!result.changed) return null
        return root.withElement("blocks", result.value).toString()
    }

    private fun updateBlocks(
        element: JsonElement?,
        transform: (JsonObject) -> Mutation<JsonObject>,
    ): Mutation<JsonArray> {
        val blocks = element as? JsonArray ?: JsonArray(emptyList())
        var changed = false
        val updatedBlocks = blocks.map { blockElement ->
            val block = blockElement as? JsonObject ?: return@map blockElement
            var currentBlock = block
            val transformed = transform(currentBlock)
            if (transformed.changed) {
                currentBlock = transformed.value
                changed = true
            }

            val childResult = updateBlocks(currentBlock["children"], transform)
            if (childResult.changed) {
                changed = true
                currentBlock = currentBlock.withElement("children", childResult.value)
            }
            currentBlock
        }
        return Mutation(JsonArray(updatedBlocks), changed)
    }

    private fun deleteBlockFromList(element: JsonElement?, blockId: String): Mutation<JsonArray> {
        val blocks = element as? JsonArray ?: JsonArray(emptyList())
        var changed = false
        val updatedBlocks = blocks.mapNotNull { blockElement ->
            val block = blockElement as? JsonObject ?: return@mapNotNull blockElement
            if (block.stringValue("id") == blockId) {
                changed = true
                return@mapNotNull null
            }
            val childResult = deleteBlockFromList(block["children"], blockId)
            if (childResult.changed) {
                changed = true
                block.withElement("children", childResult.value)
            } else {
                block
            }
        }
        return Mutation(JsonArray(updatedBlocks), changed)
    }

    private fun moveBlockInList(element: JsonElement?, blockId: String, targetIndex: Int): Mutation<JsonArray> {
        val blocks = element as? JsonArray ?: JsonArray(emptyList())
        val index = blocks.indexOfFirst { blockElement ->
            (blockElement as? JsonObject)?.stringValue("id") == blockId
        }
        if (index >= 0) return Mutation(blocks.moveElement(index, targetIndex), changed = true)

        var changed = false
        val updatedBlocks = blocks.map { blockElement ->
            val block = blockElement as? JsonObject ?: return@map blockElement
            val childResult = moveBlockInList(block["children"], blockId, targetIndex)
            if (childResult.changed) {
                changed = true
                block.withElement("children", childResult.value)
            } else {
                block
            }
        }
        return Mutation(JsonArray(updatedBlocks), changed)
    }

    private fun JsonArray.insertElement(
        element: JsonElement,
        afterBlockId: String,
        targetIndex: Int?,
    ): JsonArray {
        val mutable = toMutableList()
        val index = when {
            targetIndex != null -> targetIndex.coerceIn(0, mutable.size)
            afterBlockId.isNotBlank() -> {
                val afterIndex = mutable.indexOfFirst { item ->
                    (item as? JsonObject)?.stringValue("id") == afterBlockId
                }
                if (afterIndex >= 0) afterIndex + 1 else mutable.size
            }
            else -> mutable.size
        }
        mutable.add(index, element)
        return JsonArray(mutable)
    }

    private fun JsonArray.moveElement(fromIndex: Int, targetIndex: Int): JsonArray {
        val mutable = toMutableList()
        val element = mutable.removeAt(fromIndex)
        mutable.add(targetIndex.coerceIn(0, mutable.size), element)
        return JsonArray(mutable)
    }

    private fun String.toJsonObjectOrNull(): JsonObject? {
        return runCatching { json.parseToJsonElement(this) as? JsonObject }.getOrNull()
    }

    private fun JsonObject.stringValue(key: String): String {
        return (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    private fun JsonObject.withString(key: String, value: String): JsonObject {
        return withElement(key, JsonPrimitive(value))
    }

    private fun JsonObject.withElement(key: String, value: JsonElement): JsonObject {
        return JsonObject(toMutableMap().apply { put(key, value) })
    }

    private fun JsonObject.withoutKey(key: String): JsonObject {
        return JsonObject(toMutableMap().apply { remove(key) })
    }

    private fun emptyRelationCellValue(): JsonObject {
        return JsonObject(
            mapOf(
                "type" to JsonPrimitive("Relation"),
                "relationRowIds" to JsonArray(emptyList()),
            ),
        )
    }

    private fun uniqueRowId(rowId: String, rows: JsonArray): String {
        val requestedId = rowId.trim()
        if (requestedId.isNotBlank() && rows.none { row -> (row as? JsonObject)?.stringValue("id") == requestedId }) {
            return requestedId
        }
        while (true) {
            val generatedId = UUID.randomUUID().toString()
            if (rows.none { row -> (row as? JsonObject)?.stringValue("id") == generatedId }) {
                return generatedId
            }
        }
    }

    private val ValidTableFilterOperators = setOf(
        "Contains",
        "Equals",
        "NotEquals",
        "IsEmpty",
        "IsNotEmpty",
        "GreaterThan",
        "LessThan",
        "Before",
        "After",
        "OnOrBefore",
        "OnOrAfter",
    )
}

private data class Mutation<T>(
    val value: T,
    val changed: Boolean = false,
)
