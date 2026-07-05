package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.EditorCommandResult
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.toTypedCellValue
import com.changeyourlife.cyl.domain.model.withColumnType
import java.util.UUID

class TableMutationUseCase(
    private val applyEditorCommandUseCase: ApplyEditorCommandUseCase,
) {
    fun replaceTable(
        document: PageBlockDocument,
        tableBlockId: String,
        transform: (PageTable) -> PageTable,
    ): TableMutationResult {
        val tableBlock = document.findTableBlock(tableBlockId)
            ?: return document.noTableResult(tableBlockId)
        return document.replaceTableResult(
            tableBlockId = tableBlockId,
            table = transform(tableBlock.table),
        )
    }

    fun updateTitle(
        document: PageBlockDocument,
        tableBlockId: String,
        title: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(title = title)
    }

    fun updateView(
        document: PageBlockDocument,
        tableBlockId: String,
        view: PageTableView,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(view = view)
    }

    fun updateViewConfig(
        document: PageBlockDocument,
        tableBlockId: String,
        config: PageTableViewConfig,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(viewConfig = config)
    }

    fun attachDataSource(
        document: PageBlockDocument,
        tableBlockId: String,
        sourcePageId: String,
        sourceTableBlockId: String,
        sourceTitle: String,
        sourceTable: PageTable,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val columnIdMap = sourceTable.columns.associate { column -> column.id to UUID.randomUUID().toString() }
        val sourceColumns = sourceTable.columns.map { column ->
            column.copy(
                id = columnIdMap.getValue(column.id),
                rollupRelationColumnId = columnIdMap[column.rollupRelationColumnId].orEmpty(),
                rollupTargetColumnId = columnIdMap[column.rollupTargetColumnId].orEmpty(),
            )
        }
        val sourceRows = sourceTable.rows.map { row ->
            row.copy(
                id = UUID.randomUUID().toString(),
                cells = row.cells.mapNotNull { (columnId, value) ->
                    columnIdMap[columnId]?.let { nextColumnId -> nextColumnId to value }
                }.toMap(),
                cellValues = row.cellValues.mapNotNull { (columnId, value) ->
                    columnIdMap[columnId]?.let { nextColumnId ->
                        val sourceColumn = sourceTable.columns.firstOrNull { column -> column.id == columnId }
                        val nextColumn = sourceColumns.firstOrNull { column -> column.id == nextColumnId }
                        val displayValue = row.cells[columnId].orEmpty()
                        nextColumnId to value.withColumnType(nextColumn?.type ?: sourceColumn?.type ?: value.type, displayValue)
                    }
                }.toMap(),
                blocks = row.blocks.map { block -> block.duplicatedForImportedTableRow() },
            )
        }
        fun mappedColumnId(columnId: String): String = columnIdMap[columnId].orEmpty()
        val sourceViewConfig = sourceTable.viewConfig
        val nextViewConfig = table.viewConfig.copy(
            calendarDateColumnId = mappedColumnId(sourceViewConfig.calendarDateColumnId),
            timelineStartColumnId = mappedColumnId(sourceViewConfig.timelineStartColumnId),
            timelineEndColumnId = mappedColumnId(sourceViewConfig.timelineEndColumnId),
            dashboardMetricColumnId = mappedColumnId(sourceViewConfig.dashboardMetricColumnId),
            dashboardGroupColumnId = mappedColumnId(sourceViewConfig.dashboardGroupColumnId),
            dataSourcePageId = sourcePageId,
            dataSourceTableBlockId = sourceTableBlockId,
            dataSourceTitle = sourceTitle.ifBlank { sourceTable.title },
        )
        table.copy(
            title = table.title.takeUnless { title -> title.isBlank() || title == "Untitled database" }
                ?: sourceTitle.ifBlank { sourceTable.title.ifBlank { table.title } },
            columns = sourceColumns,
            rows = sourceRows,
            sort = PageTableSort(),
            filter = PageTableFilter(),
            groupByColumnId = mappedColumnId(sourceTable.groupByColumnId),
            viewConfig = nextViewConfig,
        )
    }

    fun clearDataSource(
        document: PageBlockDocument,
        tableBlockId: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            viewConfig = table.viewConfig.copy(
                dataSourcePageId = "",
                dataSourceTableBlockId = "",
                dataSourceTitle = "",
            ),
        )
    }

    fun updateSort(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        direction: PageTableSortDirection,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            sort = if (columnId.isBlank()) {
                PageTableSort()
            } else {
                PageTableSort(columnId = columnId, direction = direction)
            },
        )
    }

    fun updateFilter(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        query: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            filter = if (columnId.isBlank() || query.isBlank()) {
                PageTableFilter()
            } else {
                PageTableFilter(columnId = columnId, query = query)
            },
        )
    }

    fun updateGroup(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(groupByColumnId = columnId)
    }

    fun updateColumnName(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        name: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            columns = table.columns.map { column ->
                if (column.id == columnId) column.copy(name = name) else column
            },
        )
    }

    fun updateColumnType(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        type: PageTableColumnType,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            columns = table.columns.map { column ->
                if (column.id == columnId) {
                    column.copy(
                        type = type,
                        formula = if (type == PageTableColumnType.Formula) column.formula else "",
                        relationTargetTableId = if (type == PageTableColumnType.Relation) {
                            column.relationTargetTableId
                        } else {
                            ""
                        },
                        rollupRelationColumnId = if (type == PageTableColumnType.Rollup) {
                            column.rollupRelationColumnId
                        } else {
                            ""
                        },
                        rollupTargetColumnId = if (type == PageTableColumnType.Rollup) {
                            column.rollupTargetColumnId
                        } else {
                            ""
                        },
                    )
                } else {
                    column
                }
            },
            rows = table.rows.map { row ->
                val nextValue = type.coerceExistingCellValue(row.cells[columnId].orEmpty())
                row.copy(
                    cells = row.cells + (columnId to nextValue),
                    cellValues = row.cellValues + (
                        columnId to (
                            row.cellValues[columnId]?.withColumnType(type, nextValue)
                                ?: nextValue.toTypedCellValue(type)
                            )
                        ),
                )
            },
        )
    }

    fun updateColumnDateSettings(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        dateFormat: PageTableDateFormat,
        timeFormat: PageTableTimeFormat,
        dateReminder: PageTableDateReminder,
        timezoneLabel: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            columns = table.columns.map { column ->
                if (column.id == columnId) {
                    column.copy(
                        dateFormat = dateFormat,
                        timeFormat = timeFormat,
                        dateReminder = dateReminder,
                        timezoneLabel = timezoneLabel,
                    )
                } else {
                    column
                }
            },
        )
    }

    fun updateColumnFormula(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        formula: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            columns = table.columns.map { column ->
                if (column.id == columnId) column.copy(formula = formula) else column
            },
        )
    }

    fun updateColumnRelationTarget(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        targetTableId: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            columns = table.columns.map { column ->
                if (column.id == columnId) column.copy(relationTargetTableId = targetTableId) else column
            },
        )
    }

    fun updateColumnRollup(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        relationColumnId: String,
        targetColumnId: String,
        aggregation: PageTableRollupAggregation,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            columns = table.columns.map { column ->
                if (column.id == columnId) {
                    column.copy(
                        rollupRelationColumnId = relationColumnId,
                        rollupTargetColumnId = targetColumnId,
                        rollupAggregation = aggregation,
                    )
                } else {
                    column
                }
            },
        )
    }

    fun updateCell(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
        columnId: String,
        value: String,
    ): TableCellMutationResult {
        var coercedValue: String? = null
        val result = replaceTable(document, tableBlockId) { table ->
            val column = table.columns.firstOrNull { tableColumn -> tableColumn.id == columnId }
                ?: return@replaceTable table
            if (column.type == PageTableColumnType.Formula || column.type == PageTableColumnType.Rollup) {
                return@replaceTable table
            }
            val nextValue = column.type.coerceManualCellValue(value)
            coercedValue = nextValue
            table.copy(
                rows = table.rows.map { row ->
                    if (row.id == rowId) {
                        row.copy(
                            cells = row.cells + (columnId to nextValue),
                            cellValues = row.cellValues + (columnId to column.toTypedCellValue(nextValue)),
                        )
                    } else {
                        row
                    }
                },
            )
        }
        return TableCellMutationResult(
            mutation = result,
            coercedValue = coercedValue,
        )
    }

    fun addColumn(
        document: PageBlockDocument,
        tableBlockId: String,
        column: PageTableColumn,
        targetIndex: Int? = null,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val index = targetIndex?.coerceIn(0, table.columns.size) ?: table.columns.size
        table.copy(
            columns = table.columns.toMutableList().apply { add(index, column) },
            rows = table.rows.map { row ->
                row.copy(
                    cells = row.cells + (column.id to ""),
                    cellValues = row.cellValues + (column.id to column.toTypedCellValue("")),
                )
            },
        )
    }

    fun duplicateColumn(
        document: PageBlockDocument,
        tableBlockId: String,
        sourceColumnId: String,
        duplicatedColumn: PageTableColumn,
    ): DuplicateTableColumnMutationResult {
        var insertIndex: Int? = null
        var cellValues: Map<String, String> = emptyMap()
        val result = replaceTable(document, tableBlockId) { table ->
            val sourceIndex = table.columns.indexOfFirst { column -> column.id == sourceColumnId }
            if (sourceIndex == -1) return@replaceTable table
            insertIndex = sourceIndex + 1
            cellValues = table.rows.associate { row -> row.id to row.cells[sourceColumnId].orEmpty() }
            table.copy(
                columns = table.columns.toMutableList().apply {
                    add(sourceIndex + 1, duplicatedColumn)
                },
                rows = table.rows.map { row ->
                    val copiedValue = row.cells[sourceColumnId].orEmpty()
                    val copiedTypedValue = row.cellValues[sourceColumnId]
                        ?.withColumnType(duplicatedColumn.type, copiedValue)
                        ?: duplicatedColumn.toTypedCellValue(copiedValue)
                    row.copy(
                        cells = row.cells + (duplicatedColumn.id to copiedValue),
                        cellValues = row.cellValues + (duplicatedColumn.id to copiedTypedValue),
                    )
                },
            )
        }
        return DuplicateTableColumnMutationResult(
            mutation = result,
            column = duplicatedColumn,
            insertIndex = insertIndex,
            cellValues = cellValues,
        )
    }

    fun deleteColumn(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        if (table.columns.size <= 1) {
            table
        } else {
            table.copy(
                columns = table.columns
                    .filterNot { column -> column.id == columnId }
                    .map { column -> column.withoutColumnReference(columnId) },
                rows = table.rows.map { row ->
                    row.copy(
                        cells = row.cells - columnId,
                        cellValues = row.cellValues - columnId,
                    )
                },
                sort = if (table.sort.columnId == columnId) PageTableSort() else table.sort,
                filter = if (table.filter.columnId == columnId) PageTableFilter() else table.filter,
                groupByColumnId = if (table.groupByColumnId == columnId) "" else table.groupByColumnId,
            )
        }
    }

    fun addRow(
        document: PageBlockDocument,
        tableBlockId: String,
        row: PageTableRow,
        targetIndex: Int? = null,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val insertIndex = targetIndex?.coerceIn(0, table.rows.size) ?: table.rows.size
        table.copy(
            rows = table.rows.toMutableList().apply {
                add(insertIndex, row)
            },
        )
    }

    fun deleteRow(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(rows = table.rows.filterNot { row -> row.id == rowId })
    }

    fun moveRow(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
        targetIndex: Int,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val currentIndex = table.rows.indexOfFirst { row -> row.id == rowId }
        if (currentIndex < 0) return@replaceTable table
        val mutableRows = table.rows.toMutableList()
        val row = mutableRows.removeAt(currentIndex)
        val nextIndex = targetIndex.coerceIn(0, mutableRows.size)
        mutableRows.add(nextIndex, row)
        table.copy(rows = mutableRows)
    }

    fun updateRowBlocks(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
        command: (PageBlockDocument) -> EditorCommand,
    ): TableMutationResult {
        val tableBlock = document.findTableBlock(tableBlockId)
            ?: return document.noTableResult(tableBlockId)
        var didChange = false
        val rows = tableBlock.table.rows.map { row ->
            if (row.id == rowId) {
                val normalizedRowDocument = PageBlockDocument(blocks = row.blocks.normalizedRowBlocks())
                val editorCommand = command(normalizedRowDocument)
                val rowDocument = if (row.blocks.isEmpty() && editorCommand is EditorCommand.InsertBlock) {
                    PageBlockDocument(blocks = emptyList())
                } else {
                    normalizedRowDocument
                }
                val rowResult = applyEditorCommandUseCase(rowDocument, editorCommand).result
                didChange = didChange || rowResult.changed
                row.copy(blocks = rowResult.document.blocks.normalizedRowBlocks())
            } else {
                row
            }
        }
        val table = if (didChange) {
            tableBlock.table.copy(rows = rows)
        } else {
            tableBlock.table
        }
        return document.replaceTableResult(
            tableBlockId = tableBlockId,
            table = table,
        )
    }

    fun replaceRowBlockWithBlocks(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
        replacementBlocks: List<PageBlock>,
    ): TableMutationResult {
        val tableBlock = document.findTableBlock(tableBlockId)
            ?: return document.noTableResult(tableBlockId)
        if (replacementBlocks.isEmpty()) {
            return document.unchangedTableResult(tableBlockId, tableBlock.table)
        }

        var didChange = false
        val rows = tableBlock.table.rows.map { row ->
            if (row.id == rowId) {
                val rowDocument = PageBlockDocument(blocks = row.blocks.normalizedRowBlocks())
                val result = rowDocument.blocks.replaceBlockWithBlocks(rowBlockId, replacementBlocks)
                didChange = didChange || result.changed
                row.copy(blocks = result.blocks.normalizedRowBlocks())
            } else {
                row
            }
        }
        if (!didChange) {
            return document.unchangedTableResult(tableBlockId, tableBlock.table)
        }

        return document.replaceTableResult(
            tableBlockId = tableBlockId,
            table = tableBlock.table.copy(rows = rows),
        )
    }

    private fun PageBlockDocument.replaceTableResult(
        tableBlockId: String,
        table: PageTable,
    ): TableMutationResult {
        val result = applyEditorCommandUseCase(
            document = this,
            command = EditorCommand.ReplaceTable(
                blockId = tableBlockId,
                table = table,
            ),
        ).result
        return TableMutationResult(
            commandResult = result,
            tableBlockId = tableBlockId,
            table = table,
        )
    }

    private fun PageBlockDocument.unchangedTableResult(
        tableBlockId: String,
        table: PageTable?,
    ): TableMutationResult {
        return TableMutationResult(
            commandResult = EditorCommandResult(document = this),
            tableBlockId = tableBlockId,
            table = table,
        )
    }

    private fun PageBlockDocument.noTableResult(tableBlockId: String): TableMutationResult {
        val result = applyEditorCommandUseCase(
            document = this,
            command = EditorCommand.ReplaceTable(
                blockId = tableBlockId,
                table = PageTable(),
            ),
        ).result
        return TableMutationResult(
            commandResult = result,
            tableBlockId = tableBlockId,
            table = null,
        )
    }
}

private data class ReplaceRowBlocksResult(
    val blocks: List<PageBlock>,
    val changed: Boolean,
)

private fun List<PageBlock>.replaceBlockWithBlocks(
    blockId: String,
    replacementBlocks: List<PageBlock>,
): ReplaceRowBlocksResult {
    val directIndex = indexOfFirst { block -> block.id == blockId }
    if (directIndex >= 0) {
        val currentBlock = this[directIndex]
        val replacements = replacementBlocks.mapIndexed { index, block ->
            if (index == 0) block.copy(id = currentBlock.id) else block
        }
        return ReplaceRowBlocksResult(
            blocks = take(directIndex) + replacements + drop(directIndex + 1),
            changed = true,
        )
    }

    forEachIndexed { index, block ->
        val childResult = block.children.replaceBlockWithBlocks(blockId, replacementBlocks)
        if (childResult.changed) {
            return ReplaceRowBlocksResult(
                blocks = toMutableList().apply {
                    set(index, block.copy(children = childResult.blocks))
                },
                changed = true,
            )
        }
    }

    return ReplaceRowBlocksResult(blocks = this, changed = false)
}

data class TableMutationResult(
    val commandResult: EditorCommandResult,
    val tableBlockId: String,
    val table: PageTable?,
) {
    val document: PageBlockDocument
        get() = commandResult.document

    val changed: Boolean
        get() = commandResult.changed
}

data class TableCellMutationResult(
    val mutation: TableMutationResult,
    val coercedValue: String?,
) {
    val document: PageBlockDocument
        get() = mutation.document

    val changed: Boolean
        get() = mutation.changed
}

data class DuplicateTableColumnMutationResult(
    val mutation: TableMutationResult,
    val column: PageTableColumn,
    val insertIndex: Int?,
    val cellValues: Map<String, String>,
) {
    val document: PageBlockDocument
        get() = mutation.document

    val changed: Boolean
        get() = mutation.changed
}

private fun PageBlockDocument.findTableBlock(tableBlockId: String): PageBlock? {
    if (tableBlockId.isBlank()) return null
    fun walk(blocks: List<PageBlock>): PageBlock? {
        blocks.forEach { block ->
            if (block.id == tableBlockId && block.type == PageBlockType.DatabaseTable) {
                return block
            }
            walk(block.children)?.let { return it }
        }
        return null
    }
    return walk(blocks)
}

private fun List<PageBlock>.normalizedRowBlocks(): List<PageBlock> {
    return ifEmpty { listOf(PageContentCodec.newBlock(PageBlockType.Text)) }
}

private fun PageBlock.duplicatedForImportedTableRow(): PageBlock {
    return copy(
        id = UUID.randomUUID().toString(),
        children = children.map { child -> child.duplicatedForImportedTableRow() },
    )
}

private fun PageTableColumn.withoutColumnReference(columnId: String): PageTableColumn {
    return copy(
        rollupRelationColumnId = if (rollupRelationColumnId == columnId) "" else rollupRelationColumnId,
        rollupTargetColumnId = if (rollupTargetColumnId == columnId) "" else rollupTargetColumnId,
    )
}

private fun PageTableColumnType.coerceManualCellValue(value: String): String {
    return when (this) {
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> ""
        PageTableColumnType.Checkbox -> value.toTableCheckboxValue()
        PageTableColumnType.Date -> value.toTableDateCellStorageValue(allowPartial = true)
        PageTableColumnType.Relation,
        PageTableColumnType.Status,
        PageTableColumnType.Text,
        PageTableColumnType.Number,
        PageTableColumnType.FilesMedia,
        -> value
    }
}

private fun PageTableColumnType.coerceExistingCellValue(value: String): String {
    return when (this) {
        PageTableColumnType.Text -> value
        PageTableColumnType.Number -> value.toTableNumberValue()
        PageTableColumnType.Status -> value.trim()
        PageTableColumnType.Date -> value.toTableDateCellStorageValue(allowPartial = false)
        PageTableColumnType.Checkbox -> value.toTableCheckboxValue()
        PageTableColumnType.FilesMedia -> if (value.trim().startsWith("[")) value else ""
        PageTableColumnType.Formula,
        PageTableColumnType.Relation,
        PageTableColumnType.Rollup,
        -> ""
    }
}

private fun String.toTableCheckboxValue(): String {
    return if (trim().lowercase() in setOf("true", "checked", "done", "yes", "y", "1")) {
        TableCheckboxCheckedValue
    } else {
        ""
    }
}

private fun String.toTableDateCellStorageValue(allowPartial: Boolean): String {
    val trimmed = trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
    return trimmed.toTableDateValue(allowPartial = allowPartial)
}

private fun String.toTableDateValue(allowPartial: Boolean): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return trimmed
    return if (allowPartial) trimmed else ""
}

private fun String.toTableNumberValue(): String {
    val normalized = trim().replace(",", "")
    val number = normalized.toDoubleOrNull() ?: return ""
    return if (number % 1.0 == 0.0) {
        number.toLong().toString()
    } else {
        number.toString().trimEnd('0').trimEnd('.')
    }
}

private const val TableCheckboxCheckedValue = "true"
