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
                row.copy(cells = row.cells + (columnId to type.coerceExistingCellValue(row.cells[columnId].orEmpty())))
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
                    if (row.id == rowId) row.copy(cells = row.cells + (columnId to nextValue)) else row
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
                row.copy(cells = row.cells + (column.id to ""))
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
                    row.copy(cells = row.cells + (duplicatedColumn.id to row.cells[sourceColumnId].orEmpty()))
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
                rows = table.rows.map { row -> row.copy(cells = row.cells - columnId) },
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
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(rows = table.rows + row)
    }

    fun deleteRow(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(rows = table.rows.filterNot { row -> row.id == rowId })
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
                val rowDocument = PageBlockDocument(blocks = row.blocks.normalizedRowBlocks())
                val rowResult = applyEditorCommandUseCase(rowDocument, command(rowDocument)).result
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
