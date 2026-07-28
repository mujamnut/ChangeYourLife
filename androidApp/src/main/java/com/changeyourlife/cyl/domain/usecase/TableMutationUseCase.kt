package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.EditorCommandResult
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableCellValue
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableFilterOperator
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSavedView
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.isActive
import com.changeyourlife.cyl.domain.model.normalizedForType
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

    fun createSavedView(
        document: PageBlockDocument,
        tableBlockId: String,
        name: String,
        view: PageTableView,
        viewId: String = UUID.randomUUID().toString(),
        calendarDateColumnId: String? = null,
        timelineStartColumnId: String? = null,
        timelineEndColumnId: String? = null,
        dashboardMetricColumnId: String? = null,
        dashboardGroupColumnId: String? = null,
        sort: PageTableSort? = null,
        filter: PageTableFilter? = null,
        groupByColumnId: String? = null,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val normalizedName = name.trim().ifBlank { view.name }
        if (table.viewConfig.savedViews.any { saved ->
                saved.name.equals(normalizedName, ignoreCase = true)
            }
        ) {
            return@replaceTable table
        }
        val tableWithView = table.copy(
            view = view,
            sort = sort ?: table.sort,
            filter = filter ?: table.filter,
            groupByColumnId = groupByColumnId ?: table.groupByColumnId,
            viewConfig = table.viewConfig.copy(
                calendarDateColumnId = calendarDateColumnId ?: table.viewConfig.calendarDateColumnId,
                timelineStartColumnId = timelineStartColumnId ?: table.viewConfig.timelineStartColumnId,
                timelineEndColumnId = timelineEndColumnId ?: table.viewConfig.timelineEndColumnId,
                dashboardMetricColumnId = dashboardMetricColumnId ?: table.viewConfig.dashboardMetricColumnId,
                dashboardGroupColumnId = dashboardGroupColumnId ?: table.viewConfig.dashboardGroupColumnId,
            ),
        )
        val savedView = tableWithView.toSavedView(viewId, normalizedName)
        tableWithView.copy(
            viewConfig = tableWithView.viewConfig.copy(
                savedViews = tableWithView.viewConfig.savedViews + savedView,
                activeSavedViewId = savedView.id,
            ),
        )
    }

    fun renameSavedView(
        document: PageBlockDocument,
        tableBlockId: String,
        viewId: String,
        viewName: String,
        newName: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val target = table.viewConfig.savedViews.findSavedView(viewId, viewName)
            ?: return@replaceTable table
        val normalizedName = newName.trim()
        if (normalizedName.isBlank() || table.viewConfig.savedViews.any { saved ->
                saved.id != target.id && saved.name.equals(normalizedName, ignoreCase = true)
            }
        ) {
            return@replaceTable table
        }
        table.copy(
            viewConfig = table.viewConfig.copy(
                savedViews = table.viewConfig.savedViews.map { saved ->
                    if (saved.id == target.id) saved.copy(name = normalizedName) else saved
                },
            ),
        )
    }

    fun deleteSavedView(
        document: PageBlockDocument,
        tableBlockId: String,
        viewId: String,
        viewName: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val target = table.viewConfig.savedViews.findSavedView(viewId, viewName)
            ?: return@replaceTable table
        val remaining = table.viewConfig.savedViews.filterNot { saved -> saved.id == target.id }
        val nextActive = if (table.viewConfig.activeSavedViewId == target.id) {
            remaining.firstOrNull()
        } else {
            remaining.firstOrNull { saved -> saved.id == table.viewConfig.activeSavedViewId }
        }
        val withoutTarget = table.copy(
            viewConfig = table.viewConfig.copy(
                savedViews = remaining,
                activeSavedViewId = nextActive?.id.orEmpty(),
            ),
        )
        if (nextActive != null) withoutTarget.applySavedView(nextActive) else withoutTarget
    }

    fun activateSavedView(
        document: PageBlockDocument,
        tableBlockId: String,
        viewId: String,
        viewName: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val target = table.viewConfig.savedViews.findSavedView(viewId, viewName)
            ?: return@replaceTable table
        table.applySavedView(target)
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
        val sourceSavedViews = sourceViewConfig.savedViews.map { saved ->
            saved.copy(
                calendarDateColumnId = mappedColumnId(saved.calendarDateColumnId),
                timelineStartColumnId = mappedColumnId(saved.timelineStartColumnId),
                timelineEndColumnId = mappedColumnId(saved.timelineEndColumnId),
                dashboardMetricColumnId = mappedColumnId(saved.dashboardMetricColumnId),
                dashboardGroupColumnId = mappedColumnId(saved.dashboardGroupColumnId),
                sort = saved.sort.copy(columnId = mappedColumnId(saved.sort.columnId)),
                filter = saved.filter.copy(columnId = mappedColumnId(saved.filter.columnId)),
                groupByColumnId = mappedColumnId(saved.groupByColumnId),
            )
        }
        val nextViewConfig = table.viewConfig.copy(
            calendarDateColumnId = mappedColumnId(sourceViewConfig.calendarDateColumnId),
            timelineStartColumnId = mappedColumnId(sourceViewConfig.timelineStartColumnId),
            timelineEndColumnId = mappedColumnId(sourceViewConfig.timelineEndColumnId),
            dashboardMetricColumnId = mappedColumnId(sourceViewConfig.dashboardMetricColumnId),
            dashboardGroupColumnId = mappedColumnId(sourceViewConfig.dashboardGroupColumnId),
            dataSourcePageId = sourcePageId,
            dataSourceTableBlockId = sourceTableBlockId,
            dataSourceTitle = sourceTitle.ifBlank { sourceTable.title },
            savedViews = sourceSavedViews,
            activeSavedViewId = sourceViewConfig.activeSavedViewId
                .takeIf { activeId -> sourceSavedViews.any { saved -> saved.id == activeId } }
                .orEmpty(),
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
            sort = if (columnId.isNotBlank() && table.columns.any { column -> column.id == columnId }) {
                PageTableSort(columnId = columnId, direction = direction)
            } else {
                PageTableSort()
            },
        )
    }

    fun updateFilter(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        query: String,
    ): TableMutationResult = updateFilter(
        document = document,
        tableBlockId = tableBlockId,
        filter = PageTableFilter(
            columnId = columnId,
            query = query,
            operator = PageTableFilterOperator.Contains,
        ),
    )

    fun updateFilter(
        document: PageBlockDocument,
        tableBlockId: String,
        filter: PageTableFilter,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val normalizedFilter = filter.takeIf { candidate ->
            candidate.isActive() && table.columns.any { column -> column.id == candidate.columnId }
        }
        table.copy(
            filter = normalizedFilter ?: PageTableFilter(),
        )
    }

    fun updateGroup(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val normalizedColumnId = columnId.takeIf { candidate ->
            candidate.isNotBlank() && table.columns.any { column -> column.id == candidate }
        }.orEmpty()
        table.copy(groupByColumnId = normalizedColumnId)
    }

    fun updateColumnName(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        name: String,
    ): TableMutationResult = updateColumn(
        document = document,
        tableBlockId = tableBlockId,
        columnId = columnId,
    ) { column ->
        column.copy(name = name)
    }

    fun updateColumn(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        transform: (PageTableColumn) -> PageTableColumn,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            columns = table.columns.map { column ->
                if (column.id == columnId) transform(column) else column
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
                        config = column.config.normalizedForType(type),
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

    fun updateColumnConfig(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        config: PageTableColumnConfig,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(
            columns = table.columns.map { column ->
                if (column.id == columnId) {
                    column.copy(config = config.normalizedForType(column.type))
                } else {
                    column
                }
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
        val currentColumn = table.columns.firstOrNull { column -> column.id == columnId }
        val targetChanged = currentColumn != null && currentColumn.relationTargetTableId != targetTableId
        table.copy(
            columns = table.columns.map { column ->
                when {
                    column.id == columnId -> column.copy(relationTargetTableId = targetTableId)
                    targetChanged && column.rollupRelationColumnId == columnId -> {
                        column.copy(rollupTargetColumnId = "")
                    }
                    else -> column
                }
            },
            rows = if (targetChanged) {
                table.rows.map { row ->
                    row.copy(
                        cells = row.cells + (columnId to ""),
                        cellValues = row.cellValues + (
                            columnId to PageTableCellValue(type = PageTableColumnType.Relation)
                            ),
                    )
                }
            } else {
                table.rows
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
        val validRelationColumn = table.columns.firstOrNull { column ->
            column.id == relationColumnId && column.type == PageTableColumnType.Relation
        }
        table.copy(
            columns = table.columns.map { column ->
                if (column.id == columnId) {
                    column.copy(
                        rollupRelationColumnId = validRelationColumn?.id.orEmpty(),
                        rollupTargetColumnId = if (validRelationColumn == null) "" else targetColumnId,
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

    fun updateRow(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
        valuesByColumnId: Map<String, String>,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        if (valuesByColumnId.isEmpty()) return@replaceTable table
        val columnsById = table.columns.associateBy(PageTableColumn::id)
        table.copy(
            rows = table.rows.map { row ->
                if (row.id != rowId) return@map row
                valuesByColumnId.entries.fold(row) { currentRow, (columnId, rawValue) ->
                    val column = columnsById[columnId] ?: return@fold currentRow
                    if (column.type == PageTableColumnType.Formula || column.type == PageTableColumnType.Rollup) {
                        return@fold currentRow
                    }
                    val nextValue = column.type.coerceManualCellValue(rawValue)
                    currentRow.copy(
                        cells = currentRow.cells + (columnId to nextValue),
                        cellValues = currentRow.cellValues + (
                            columnId to column.toTypedCellValue(nextValue)
                            ),
                    )
                }
            },
        )
    }

    fun updateRows(
        document: PageBlockDocument,
        tableBlockId: String,
        rowIds: Set<String>,
        valuesByColumnId: Map<String, String>,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        if (rowIds.isEmpty() || valuesByColumnId.isEmpty()) return@replaceTable table
        val columnsById = table.columns.associateBy(PageTableColumn::id)
        table.copy(
            rows = table.rows.map { row ->
                if (row.id !in rowIds) return@map row
                valuesByColumnId.entries.fold(row) { currentRow, (columnId, rawValue) ->
                    val column = columnsById[columnId] ?: return@fold currentRow
                    if (column.type == PageTableColumnType.Formula || column.type == PageTableColumnType.Rollup) {
                        return@fold currentRow
                    }
                    val nextValue = column.type.coerceManualCellValue(rawValue)
                    currentRow.copy(
                        cells = currentRow.cells + (columnId to nextValue),
                        cellValues = currentRow.cellValues + (
                            columnId to column.toTypedCellValue(nextValue)
                            ),
                    )
                }
            },
        )
    }

    fun updateCells(
        document: PageBlockDocument,
        tableBlockId: String,
        rowIds: Set<String>,
        columnId: String,
        value: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        if (rowIds.isEmpty()) return@replaceTable table
        val column = table.columns.firstOrNull { candidate -> candidate.id == columnId }
            ?: return@replaceTable table
        if (column.type == PageTableColumnType.Formula || column.type == PageTableColumnType.Rollup) {
            return@replaceTable table
        }
        val nextValue = column.type.coerceManualCellValue(value)
        table.copy(
            rows = table.rows.map { row ->
                if (row.id !in rowIds) {
                    row
                } else {
                    row.copy(
                        cells = row.cells + (columnId to nextValue),
                        cellValues = row.cellValues + (
                            columnId to column.toTypedCellValue(nextValue)
                            ),
                    )
                }
            },
        )
    }

    fun updateRelationCell(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
        columnId: String,
        relationRowIds: List<String>,
    ): TableCellMutationResult {
        var coercedValue: String? = null
        val result = replaceTable(document, tableBlockId) { table ->
            val column = table.columns.firstOrNull { tableColumn -> tableColumn.id == columnId }
                ?: return@replaceTable table
            if (column.type != PageTableColumnType.Relation) return@replaceTable table
            val nextIds = relationRowIds.normalizedRelationRowIds()
            val nextValue = nextIds.joinToString(",")
            coercedValue = nextValue
            table.copy(
                rows = table.rows.map { row ->
                    if (row.id == rowId) {
                        row.copy(
                            cells = row.cells + (columnId to nextValue),
                            cellValues = row.cellValues + (
                                columnId to PageTableCellValue(
                                    type = PageTableColumnType.Relation,
                                    relationRowIds = nextIds,
                                )
                                ),
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

    fun updateMediaCell(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
        columnId: String,
        transform: (List<PageMediaAttachment>) -> List<PageMediaAttachment>,
    ): TableCellMutationResult {
        var coercedValue: String? = null
        val result = replaceTable(document, tableBlockId) { table ->
            val column = table.columns.firstOrNull { tableColumn -> tableColumn.id == columnId }
                ?: return@replaceTable table
            if (column.type != PageTableColumnType.FilesMedia) return@replaceTable table
            table.copy(
                rows = table.rows.map { row ->
                    if (row.id != rowId) return@map row
                    val currentFiles = row.cellValues[columnId]
                        ?.takeIf { value -> value.type == PageTableColumnType.FilesMedia }
                        ?.files
                        .orEmpty()
                    val nextFiles = transform(currentFiles)
                        .filter { file -> file.uri.isNotBlank() || file.name.isNotBlank() }
                        .distinctBy { file -> file.id.ifBlank { file.uri.ifBlank { file.name } } }
                    val nextValue = nextFiles.joinToString(", ") { file ->
                        file.name.ifBlank { file.uri }
                    }
                    coercedValue = nextValue
                    row.copy(
                        cells = row.cells + (columnId to nextValue),
                        cellValues = row.cellValues + (
                            columnId to PageTableCellValue(
                                type = PageTableColumnType.FilesMedia,
                                files = nextFiles,
                            )
                            ),
                    )
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
                val defaultValue = column.config.defaultValue.coerceManualCellValueFor(column.type)
                row.copy(
                    cells = row.cells + (column.id to defaultValue),
                    cellValues = row.cellValues + (column.id to column.toTypedCellValue(defaultValue)),
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

    fun moveColumn(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        targetIndex: Int,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val currentIndex = table.columns.indexOfFirst { column -> column.id == columnId }
        if (currentIndex < 0) return@replaceTable table
        val mutableColumns = table.columns.toMutableList()
        val column = mutableColumns.removeAt(currentIndex)
        val nextIndex = targetIndex.coerceIn(0, mutableColumns.size)
        mutableColumns.add(nextIndex, column)
        table.copy(columns = mutableColumns)
    }

    fun deleteColumn(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
    ): TableMutationResult {
        val tableBlock = document.findTableBlock(tableBlockId)
            ?: return document.noTableResult(tableBlockId)
        val table = tableBlock.table
        if (table.columns.size <= 1 || table.columns.firstOrNull()?.id == columnId) {
            return document.unchangedTableResult(tableBlockId, table)
        }
        return document.replaceTableResult(
            tableBlockId = tableBlockId,
            table = table.copy(
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
                viewConfig = table.viewConfig.copy(
                    savedViews = table.viewConfig.savedViews.map { saved ->
                        saved.withoutColumn(columnId)
                    },
                ),
            ),
        )
    }

    fun updateColumnOptions(
        document: PageBlockDocument,
        tableBlockId: String,
        columnId: String,
        options: List<PageTableSelectOption>,
        renamedOption: Pair<String, String>? = null,
        deletedOptionName: String = "",
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        val column = table.columns.firstOrNull { candidate -> candidate.id == columnId }
            ?: return@replaceTable table
        if (column.type !in setOf(
                PageTableColumnType.Select,
                PageTableColumnType.MultiSelect,
                PageTableColumnType.Status,
            )
        ) {
            return@replaceTable table
        }
        val nextColumn = column.copy(
            config = column.config.copy(options = options).normalizedForType(column.type),
        )
        table.copy(
            columns = table.columns.map { current ->
                if (current.id == columnId) nextColumn else current
            },
            rows = table.rows.map { row ->
                val currentValue = row.cells[columnId].orEmpty()
                val nextValue = currentValue.remapTableOptionValue(
                    type = column.type,
                    renamedOption = renamedOption,
                    deletedOptionName = deletedOptionName,
                )
                if (nextValue == currentValue) {
                    row
                } else {
                    row.copy(
                        cells = row.cells + (columnId to nextValue),
                        cellValues = row.cellValues + (
                            columnId to nextColumn.toTypedCellValue(nextValue)
                            ),
                    )
                }
            },
        )
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
                add(insertIndex, row.withColumnDefaults(table.columns))
            },
        )
    }

    fun duplicateRow(
        document: PageBlockDocument,
        tableBlockId: String,
        sourceRowId: String,
        targetIndex: Int? = null,
    ): DuplicateTableRowMutationResult {
        var duplicatedRow: PageTableRow? = null
        var insertIndex: Int? = null
        val mutation = replaceTable(document, tableBlockId) { table ->
            val sourceIndex = table.rows.indexOfFirst { row -> row.id == sourceRowId }
            val source = table.rows.getOrNull(sourceIndex) ?: return@replaceTable table
            insertIndex = targetIndex?.coerceIn(0, table.rows.size) ?: (sourceIndex + 1)
            val now = System.currentTimeMillis()
            val duplicate = source.copy(
                id = UUID.randomUUID().toString(),
                cellValues = source.cellValues.mapValues { (_, value) ->
                    value.copy(
                        files = value.files.map { file ->
                            file.copy(id = UUID.randomUUID().toString())
                        },
                    )
                },
                metadata = source.metadata.copy(
                    createdAt = now,
                    lastEditedAt = now,
                ),
                blocks = source.blocks.map { block -> block.duplicatedForImportedTableRow() },
            )
            duplicatedRow = duplicate
            table.copy(
                rows = table.rows.toMutableList().apply {
                    add(requireNotNull(insertIndex), duplicate)
                },
            )
        }
        return DuplicateTableRowMutationResult(
            mutation = mutation,
            row = duplicatedRow,
            insertIndex = insertIndex,
        )
    }

    fun deleteRow(
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        table.copy(rows = table.rows.filterNot { row -> row.id == rowId })
    }

    fun deleteRows(
        document: PageBlockDocument,
        tableBlockId: String,
        rowIds: Set<String>,
    ): TableMutationResult = replaceTable(document, tableBlockId) { table ->
        if (rowIds.isEmpty()) table else table.copy(rows = table.rows.filterNot { row -> row.id in rowIds })
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
        val normalizedTable = table.withActiveSavedViewSynced()
        val result = applyEditorCommandUseCase(
            document = this,
            command = EditorCommand.ReplaceTable(
                blockId = tableBlockId,
                table = normalizedTable,
            ),
        ).result
        return TableMutationResult(
            commandResult = result,
            tableBlockId = tableBlockId,
            table = normalizedTable,
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

data class DuplicateTableRowMutationResult(
    val mutation: TableMutationResult,
    val row: PageTableRow?,
    val insertIndex: Int?,
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
            if (block.id == tableBlockId && block.isTableLikeBlock()) {
                return block
            }
            walk(block.children)?.let { return it }
        }
        return null
    }
    return walk(blocks)
}

private fun PageBlock.isTableLikeBlock(): Boolean =
    type == PageBlockType.DatabaseTable || type == PageBlockType.Table

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

private fun PageTableSavedView.withoutColumn(columnId: String): PageTableSavedView {
    return copy(
        calendarDateColumnId = calendarDateColumnId.takeUnless { it == columnId }.orEmpty(),
        timelineStartColumnId = timelineStartColumnId.takeUnless { it == columnId }.orEmpty(),
        timelineEndColumnId = timelineEndColumnId.takeUnless { it == columnId }.orEmpty(),
        dashboardMetricColumnId = dashboardMetricColumnId.takeUnless { it == columnId }.orEmpty(),
        dashboardGroupColumnId = dashboardGroupColumnId.takeUnless { it == columnId }.orEmpty(),
        sort = if (sort.columnId == columnId) PageTableSort() else sort,
        filter = if (filter.columnId == columnId) PageTableFilter() else filter,
        groupByColumnId = groupByColumnId.takeUnless { it == columnId }.orEmpty(),
    )
}

private fun List<PageTableSavedView>.findSavedView(
    viewId: String,
    viewName: String,
): PageTableSavedView? {
    if (viewId.isNotBlank()) {
        firstOrNull { saved -> saved.id == viewId }?.let { return it }
    }
    return firstOrNull { saved -> saved.name.equals(viewName, ignoreCase = true) }
}

private fun PageTable.toSavedView(
    id: String,
    name: String,
): PageTableSavedView = PageTableSavedView(
    id = id,
    name = name,
    view = view,
    calendarDateColumnId = viewConfig.calendarDateColumnId,
    timelineStartColumnId = viewConfig.timelineStartColumnId,
    timelineEndColumnId = viewConfig.timelineEndColumnId,
    dashboardMetricColumnId = viewConfig.dashboardMetricColumnId,
    dashboardGroupColumnId = viewConfig.dashboardGroupColumnId,
    sort = sort,
    filter = filter,
    groupByColumnId = groupByColumnId,
)

private fun PageTable.applySavedView(savedView: PageTableSavedView): PageTable {
    return copy(
        view = savedView.view,
        sort = savedView.sort,
        filter = savedView.filter,
        groupByColumnId = savedView.groupByColumnId,
        viewConfig = viewConfig.copy(
            calendarDateColumnId = savedView.calendarDateColumnId,
            timelineStartColumnId = savedView.timelineStartColumnId,
            timelineEndColumnId = savedView.timelineEndColumnId,
            dashboardMetricColumnId = savedView.dashboardMetricColumnId,
            dashboardGroupColumnId = savedView.dashboardGroupColumnId,
            activeSavedViewId = savedView.id,
        ),
    )
}

private fun PageTable.withActiveSavedViewSynced(): PageTable {
    val activeId = viewConfig.activeSavedViewId
    if (activeId.isBlank()) return this
    val active = viewConfig.savedViews.firstOrNull { saved -> saved.id == activeId }
        ?: return copy(viewConfig = viewConfig.copy(activeSavedViewId = ""))
    val snapshot = toSavedView(id = active.id, name = active.name)
    return copy(
        viewConfig = viewConfig.copy(
            savedViews = viewConfig.savedViews.map { saved ->
                if (saved.id == active.id) snapshot else saved
            },
        ),
    )
}

private fun PageTableColumnType.coerceManualCellValue(value: String): String {
    return when (this) {
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> ""
        PageTableColumnType.Checkbox -> value.toTableCheckboxValue()
        PageTableColumnType.Date -> value.toTableDateCellStorageValue(allowPartial = true)
        PageTableColumnType.Select,
        PageTableColumnType.Status,
        -> value.trim()
        PageTableColumnType.MultiSelect -> value.toTableChoiceListValue()
        PageTableColumnType.Relation,
        PageTableColumnType.Text,
        PageTableColumnType.FilesMedia,
        -> value
        PageTableColumnType.Number -> value.trim()
    }
}

private fun PageTableColumnType.coerceExistingCellValue(value: String): String {
    return when (this) {
        PageTableColumnType.Text -> value
        PageTableColumnType.Number -> value.toTableNumberValue()
        PageTableColumnType.Select -> value.trim()
        PageTableColumnType.MultiSelect -> value.toTableChoiceListValue()
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

private fun PageTableRow.withColumnDefaults(columns: List<PageTableColumn>): PageTableRow {
    val nextCells = columns.associate { column ->
        val existingValue = cells[column.id]
        val defaultValue = column.config.defaultValue.coerceManualCellValueFor(column.type)
        column.id to (existingValue ?: defaultValue)
    }
    return copy(
        cells = nextCells,
        cellValues = columns.associate { column ->
            val displayValue = nextCells[column.id].orEmpty()
            val typedValue = cellValues[column.id]
                ?.withColumnType(column.type, displayValue)
                ?: column.toTypedCellValue(displayValue)
            column.id to typedValue
        },
    )
}

private fun String.coerceManualCellValueFor(type: PageTableColumnType): String {
    return if (isBlank()) "" else type.coerceManualCellValue(this)
}

private fun String.toTableChoiceListValue(): String {
    return split(",")
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .distinctBy { value -> value.lowercase() }
        .joinToString(", ")
}

private fun String.remapTableOptionValue(
    type: PageTableColumnType,
    renamedOption: Pair<String, String>?,
    deletedOptionName: String,
): String {
    val values = if (type == PageTableColumnType.MultiSelect) {
        split(",").map(String::trim)
    } else {
        listOf(trim())
    }
    val remapped = values.mapNotNull { value ->
        when {
            deletedOptionName.isNotBlank() && value.equals(deletedOptionName, ignoreCase = true) -> null
            renamedOption != null && value.equals(renamedOption.first, ignoreCase = true) -> renamedOption.second
            value.isBlank() -> null
            else -> value
        }
    }
    return if (type == PageTableColumnType.MultiSelect) {
        remapped.distinctBy(String::lowercase).joinToString(", ")
    } else {
        remapped.firstOrNull().orEmpty()
    }
}

private fun List<String>.normalizedRelationRowIds(): List<String> {
    return map { rowId -> rowId.trim() }
        .filter { rowId -> rowId.isNotBlank() }
        .distinct()
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
