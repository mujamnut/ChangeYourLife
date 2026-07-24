package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.model.RichTextFormat
import com.changeyourlife.cyl.domain.model.RichTextSpanEngine
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.model.normalizedForType
import com.changeyourlife.cyl.domain.model.toAiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.toTypedCellValue
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.usecase.BlockMutationResult as DomainBlockMutationResult
import com.changeyourlife.cyl.domain.usecase.PageMutationResult
import com.changeyourlife.cyl.domain.usecase.PageMutationUseCase
import com.changeyourlife.cyl.domain.usecase.PropertyMutationResult
import com.changeyourlife.cyl.domain.usecase.ScheduleTableDateReminderUseCase
import com.changeyourlife.cyl.domain.usecase.TableMutationResult
import com.changeyourlife.cyl.domain.usecase.TableMutationUseCase
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import com.changeyourlife.cyl.presentation.page.PageModuleTemplates
import com.changeyourlife.cyl.presentation.page.PageModuleType
import com.changeyourlife.cyl.presentation.page.isTransactionLedgerTable
import com.changeyourlife.cyl.presentation.page.withBudgetLedgerSummarySynced
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal class AiPageActionMutationEngine(
    private val pageRepository: PageRepository,
    private val pageMutationUseCase: PageMutationUseCase,
    private val tableMutationUseCase: TableMutationUseCase,
    private val scheduleTableDateReminderUseCase: ScheduleTableDateReminderUseCase,
) {
    fun supports(action: ChatAction): Boolean {
        return AiActionExecutionRegistry.supports(action)
    }

    suspend fun executeOnPage(
        page: Page,
        title: String,
        document: PageBlockDocument,
        actions: List<ChatAction>,
        hasPendingDocumentChanges: Boolean = false,
    ): AiPageActionExecutionResult {
        var workingTitle = title.ifBlank { page.title }
        var workingDocument = document
        var titleChanged = false
        var documentChanged = hasPendingDocumentChanges
        val messages = mutableListOf<String>()
        val validationIssues = mutableListOf<AiPageActionValidationIssue>()
        val createdPages = mutableListOf<Page>()
        val createdTasks = mutableListOf<TaskItem>()
        val createdReminders = mutableListOf<Reminder>()
        val undoCommands = mutableListOf<AiUndoCommandSummary>()
        val executedActionIndexes = mutableListOf<Int>()

        for ((actionIndex, action) in actions.withIndex()) {
            val trace = AiActionExecutionRegistry.trace(actionIndex, action)
            val actionType = trace.actionType
            val validationIssue = workingDocument.validateActionTarget(action, actionIndex)
            if (validationIssue != null) {
                val issue = validationIssue.withTrace(trace)
                validationIssues += issue
                messages += "Rejected ${trace.messageLabel}: ${issue.message}"
                continue
            }
            runCatching {
                when (actionType) {
                    "RENAME_CURRENT_PAGE", "RENAME_PAGE" -> {
                        workingTitle = action.title.ifBlank { error("Missing new page title") }
                        titleChanged = true
                        "Renamed page to: $workingTitle"
                    }

                    "UPDATE_PAGE" -> {
                        if (action.title.isNotBlank()) {
                            workingTitle = action.title
                            titleChanged = true
                        }
                        if (action.content.isNotBlank()) {
                            workingDocument.blocks.forEach { block ->
                                workingDocument = workingDocument.applyAiEditorCommand(
                                    command = EditorCommand.DeleteBlock(block.id),
                                    actionIndex = actionIndex,
                                    undoCommands = undoCommands,
                                )
                            }
                            workingDocument = workingDocument.applyAiEditorCommand(
                                command = EditorCommand.InsertBlock(
                                    block = PageBlockCodec.newBlock(PageBlockType.Text).copy(text = action.content),
                                ),
                                actionIndex = actionIndex,
                                undoCommands = undoCommands,
                            )
                            documentChanged = true
                        }
                        "Updated page"
                    }

                    "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> {
                        val block = action.toPageBlock()
                        workingDocument = workingDocument.applyAiEditorCommand(
                            EditorCommand.InsertBlock(
                                block = block,
                                index = action.targetIndex?.toAiZeroBasedIndex(),
                            ),
                            actionIndex = actionIndex,
                            undoCommands = undoCommands,
                        )
                        documentChanged = true
                        "Added ${block.type.name} block"
                    }

                    "ADD_PROPERTY", "UPDATE_PROPERTY" -> {
                        val propertyName = action.propertyName
                            .ifBlank { action.title }
                            .ifBlank { error("Missing property name") }
                        val value = action.value.ifBlank { action.content }
                        val existingProperty = workingDocument.properties.firstOrNull { property ->
                            property.name.normalizedAiKey() == propertyName.normalizedAiKey()
                        }
                        if (existingProperty == null) {
                            val mutation = pageMutationUseCase.addProperty(
                                document = workingDocument,
                                type = action.propertyType.toPagePropertyType(),
                                name = propertyName,
                                value = value,
                                index = action.targetIndex?.toAiZeroBasedIndex(),
                            )
                            workingDocument = mutation.captureForAi(
                                actionIndex = actionIndex,
                                undoCommands = undoCommands,
                            )
                            documentChanged = documentChanged || mutation.changed
                        } else {
                            if (action.propertyType.isBlank() && value.isBlank()) {
                                error("Missing property update value or type")
                            }
                            if (action.propertyType.isNotBlank()) {
                                val typeMutation = pageMutationUseCase.updatePropertyType(
                                    document = workingDocument,
                                    propertyId = existingProperty.id,
                                    type = action.propertyType.toPagePropertyType(),
                                )
                                workingDocument = typeMutation.captureForAi(
                                    actionIndex = actionIndex,
                                    undoCommands = undoCommands,
                                )
                                documentChanged = documentChanged || typeMutation.changed
                            }
                            if (value.isNotBlank()) {
                                val valueMutation = pageMutationUseCase.updatePropertyValue(
                                    document = workingDocument,
                                    propertyId = existingProperty.id,
                                    value = value,
                                )
                                workingDocument = valueMutation.captureForAi(
                                    actionIndex = actionIndex,
                                    undoCommands = undoCommands,
                                )
                                documentChanged = documentChanged || valueMutation.changed
                            }
                        }
                        "Updated property: $propertyName"
                    }

                    "DELETE_PROPERTY" -> {
                        val propertyName = action.propertyName
                            .ifBlank { action.title }
                            .ifBlank { error("Missing property name") }
                        val propertyId = workingDocument.properties.firstOrNull { property ->
                            property.name.normalizedAiKey() == propertyName.normalizedAiKey()
                        }?.id ?: error("Could not find property: $propertyName")
                        val mutation = pageMutationUseCase.deleteProperty(
                            document = workingDocument,
                            propertyId = propertyId,
                        )
                        workingDocument = mutation.captureForAi(
                            actionIndex = actionIndex,
                            undoCommands = undoCommands,
                        )
                        documentChanged = documentChanged || mutation.changed
                        "Deleted property: $propertyName"
                    }

                    "DELETE_ALL_BLOCKS" -> {
                        val deletedCount = workingDocument.blocks.countNestedBlocks()
                        workingDocument = workingDocument.blocks.fold(workingDocument) { currentDocument, block ->
                            currentDocument.applyAiEditorCommand(
                                command = EditorCommand.DeleteBlock(block.id),
                                actionIndex = actionIndex,
                                undoCommands = undoCommands,
                            )
                        }
                        documentChanged = deletedCount > 0
                        if (deletedCount > 0) {
                            "Deleted all blocks"
                        } else {
                            "No blocks to delete"
                        }
                    }

                    "DELETE_BLOCK" -> {
                        val deleteResult = workingDocument.deleteMatchingBlock(action, actionIndex, undoCommands)
                        workingDocument = deleteResult.document
                        documentChanged = true
                        "Deleted block: ${deleteResult.label}"
                    }

                    "FORMAT_BLOCK_TEXT" -> {
                        val formatResult = workingDocument.formatMatchingBlockText(
                            action = action,
                            actionIndex = actionIndex,
                            undoCommands = undoCommands,
                        )
                        workingDocument = formatResult.document
                        documentChanged = true
                        "Formatted text: ${formatResult.label}"
                    }

                    "UPDATE_BLOCK", "EDIT_BLOCK", "UPDATE_TODO", "CHECK_BLOCK", "UNCHECK_BLOCK" -> {
                        val updateResult = workingDocument.updateMatchingBlock(action, actionIndex, undoCommands)
                        workingDocument = updateResult.document
                        documentChanged = true
                        "Updated block: ${updateResult.label}"
                    }

                    "CREATE_DATABASE", "CREATE_TABLE" -> {
                        val tableBlock = action.toDatabaseBlock()
                        workingDocument = workingDocument.applyAiEditorCommand(
                            EditorCommand.InsertBlock(
                                block = tableBlock,
                                index = action.targetIndex?.toAiZeroBasedIndex(),
                            ),
                            actionIndex = actionIndex,
                            undoCommands = undoCommands,
                        )
                        documentChanged = true
                        "Created database: ${tableBlock.table.title}"
                    }

                    "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" -> {
                        val newTitle = action.title
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { action.newColumnName }
                            .ifBlank { error("Missing new table title") }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val oldTitle = targetTable.table.title
                        val mutation = tableMutationUseCase.updateTitle(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            title = newTitle,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Renamed $oldTitle to $newTitle"
                    }

                    "ADD_TABLE_COLUMN" -> {
                        val resolvedAction = action.withResolvedRelationTarget(workingDocument)
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                            .ifBlank { error("Missing column name") }
                        val columnType = action.columnType.ifBlank { action.propertyType }.toPageTableColumnType()
                        val targetTable = workingDocument.blocks.findMatchingTable(resolvedAction)
                            ?: error("Could not find matching table")
                        val column = PageBlockCodec.newTableColumn(columnName, columnType)
                            .withActionConfig(
                                action = resolvedAction,
                                resolvedRollupTargetColumnId = targetTable.resolveRollupTargetColumnId(
                                    action = resolvedAction,
                                    relationColumn = targetTable.table.findColumn(
                                        columnId = resolvedAction.rollupRelationColumnId,
                                        columnName = resolvedAction.rollupRelationColumnName,
                                    ),
                                    document = workingDocument,
                                ),
                            )
                        val mutation = tableMutationUseCase.addColumn(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            column = column,
                            targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Added column $columnName to ${targetTable.table.title}"
                    }

                    "DELETE_TABLE_COLUMN" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        if (targetTable.table.columns.firstOrNull()?.id == targetColumn.id) {
                            error("The primary column cannot be deleted")
                        }
                        val mutation = tableMutationUseCase.deleteColumn(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = targetColumn.id,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Deleted column ${targetColumn.name} from ${targetTable.table.title}"
                    }

                    "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val newColumnName = action.newColumnName
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { error("Missing new column name") }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        val mutation = tableMutationUseCase.updateColumnName(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = targetColumn.id,
                            name = newColumnName,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Renamed column to $newColumnName in ${targetTable.table.title}"
                    }

                    "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE" -> {
                        val resolvedAction = action.withResolvedRelationTarget(workingDocument)
                        val columnName = resolvedAction.columnName.ifBlank { resolvedAction.propertyName }.ifBlank { resolvedAction.title }
                        val columnType = resolvedAction.columnType
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { error("Missing column type") }
                            .toPageTableColumnType()
                        val targetTable = workingDocument.blocks.findMatchingTable(resolvedAction)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(resolvedAction.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { resolvedAction.columnId }}")
                        val typeMutation = tableMutationUseCase.updateColumnType(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = targetColumn.id,
                            type = columnType,
                        )
                        workingDocument = typeMutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || typeMutation.changed

                        val typedTable = workingDocument.findTableBlock(targetTable.id)
                            ?: error("Could not reload changed table")
                        val typedColumn = typedTable.table.findColumn(targetColumn.id, targetColumn.name)
                            ?: error("Could not reload changed column")
                        val relationColumn = typedTable.table.findColumn(
                            columnId = resolvedAction.rollupRelationColumnId,
                            columnName = resolvedAction.rollupRelationColumnName,
                        )
                        val configuredColumn = typedColumn.withActionConfig(
                            action = resolvedAction,
                            relationColumn = relationColumn,
                            resolvedRollupTargetColumnId = typedTable.resolveRollupTargetColumnId(
                                action = resolvedAction,
                                relationColumn = relationColumn,
                                document = workingDocument,
                            ),
                        )
                        if (configuredColumn != typedColumn) {
                            val configMutation = tableMutationUseCase.updateColumn(
                                document = workingDocument,
                                tableBlockId = typedTable.id,
                                columnId = typedColumn.id,
                                transform = { configuredColumn },
                            )
                            workingDocument = configMutation.captureForAi(actionIndex, undoCommands)
                            documentChanged = documentChanged || configMutation.changed
                        }
                        "Changed column ${columnName.ifBlank { action.columnId }} to ${columnType.name} in ${targetTable.table.title}"
                    }

                    "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG",
                    "UPDATE_FORMULA_COLUMN", "UPDATE_RELATION_COLUMN", "UPDATE_ROLLUP_COLUMN" -> {
                        val resolvedAction = action.withResolvedRelationTarget(workingDocument)
                        val columnName = resolvedAction.columnName
                            .ifBlank { resolvedAction.propertyName }
                            .ifBlank { resolvedAction.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(resolvedAction)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(resolvedAction.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { resolvedAction.columnId }}")
                        val relationColumn = targetTable.table.findColumn(
                            columnId = resolvedAction.rollupRelationColumnId,
                            columnName = resolvedAction.rollupRelationColumnName,
                        )
                        val configuredColumn = targetColumn.withActionConfig(
                            action = resolvedAction,
                            relationColumn = relationColumn,
                            resolvedRollupTargetColumnId = targetTable.resolveRollupTargetColumnId(
                                action = resolvedAction,
                                relationColumn = relationColumn,
                                document = workingDocument,
                            ),
                        )
                        val mutation = tableMutationUseCase.updateColumn(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = targetColumn.id,
                            transform = { configuredColumn },
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Updated column configuration in ${targetTable.table.title}"
                    }

                    "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val targetIndex = action.targetIndex ?: error("Missing target index")
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        val mutation = tableMutationUseCase.moveColumn(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = targetColumn.id,
                            targetIndex = targetIndex.toAiZeroBasedIndex(),
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Moved column in ${targetTable.table.title}"
                    }

                    "ADD_TABLE_ROW" -> {
                        if (!action.hasMeaningfulTableRowPayload()) {
                            error("Add row needs at least one non-empty value")
                        }
                        if (action.isTaskTableRowAction()) {
                            val plan = workingDocument.planTaskTableAction(action)
                            val nextDocument = workingDocument.applyTaskTablePlan(
                                plan = plan,
                                actionIndex = actionIndex,
                                undoCommands = undoCommands,
                            )
                            documentChanged = documentChanged || nextDocument != workingDocument
                            workingDocument = nextDocument
                            "Added task row ${plan.rowTitle} to ${plan.tableTitle}"
                        } else {
                            val targetTable = workingDocument.blocks.findMatchingTable(action)
                                ?: error("Could not find matching table")
                            val mutation = tableMutationUseCase.addRow(
                                document = workingDocument,
                                tableBlockId = targetTable.id,
                                row = targetTable.table.newRowFromAction(action),
                                targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                            )
                            workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                            documentChanged = documentChanged || mutation.changed
                            "Added row to ${targetTable.table.title}"
                        }
                    }

                    "DELETE_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val mutation = tableMutationUseCase.deleteRow(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowId = targetRow.id,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Deleted row ${rowTitle.ifBlank { targetRow.id }} from ${targetTable.table.title}"
                    }

                    "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val newRowTitle = action.newRowTitle.ifBlank { action.value }.ifBlank { action.content }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val valuesByColumnId = buildMap {
                            if (newRowTitle.isNotBlank()) {
                                targetTable.table.columns.firstOrNull()?.id?.let { firstColumnId ->
                                    put(firstColumnId, newRowTitle)
                                }
                            }
                            action.cellValues.forEach { (columnReference, value) ->
                                val column = targetTable.table.findColumn(
                                    columnId = columnReference,
                                    columnName = columnReference,
                                ) ?: error("Could not find column: $columnReference")
                                put(column.id, value)
                            }
                        }
                        if (valuesByColumnId.isEmpty()) error("Missing row values")
                        val mutation = tableMutationUseCase.updateRow(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowId = targetRow.id,
                            valuesByColumnId = valuesByColumnId,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Updated row ${newRowTitle.ifBlank { rowTitle.ifBlank { action.rowId } }} in ${targetTable.table.title}"
                    }

                    "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val targetIndex = action.targetIndex ?: error("Missing target index")
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val mutation = tableMutationUseCase.moveRow(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowId = targetRow.id,
                            targetIndex = targetIndex.toAiZeroBasedIndex(),
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Moved row in ${targetTable.table.title}"
                    }

                    "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) error("Missing row target")
                        val rowBlock = action.toPageBlock()
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val mutation = tableMutationUseCase.updateRowBlocks(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowId = targetRow.id,
                            command = {
                                EditorCommand.InsertBlock(
                                    block = rowBlock,
                                    index = action.targetIndex?.toAiZeroBasedIndex(),
                                )
                            },
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Added ${rowBlock.type.name} block inside ${rowTitle.ifBlank { targetRow.id }} in ${targetTable.table.title}"
                    }

                    "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK",
                    "CHECK_ROW_PAGE_BLOCK", "UNCHECK_ROW_PAGE_BLOCK" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) error("Missing row target")
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val effectiveAction = when (actionType) {
                            "CHECK_ROW_PAGE_BLOCK" -> action.copy(isChecked = true)
                            "UNCHECK_ROW_PAGE_BLOCK" -> action.copy(isChecked = false)
                            else -> action
                        }
                        val targetAction = effectiveAction.rowBlockId
                            .takeIf(String::isNotBlank)
                            ?.let { rowBlockId -> effectiveAction.copy(blockId = rowBlockId) }
                            ?: effectiveAction
                        val targetBlock = targetRow.blocks.findMatchingBlock(targetAction)
                            ?: error("Could not find row content block")
                        val updatedBlock = targetBlock.withActionUpdate(effectiveAction)
                        val nextDocument = workingDocument.updateRowBlockThroughUseCase(
                            tableBlockId = targetTable.id,
                            rowId = targetRow.id,
                            currentBlock = targetBlock,
                            updatedBlock = updatedBlock,
                            actionIndex = actionIndex,
                            undoCommands = undoCommands,
                        )
                        documentChanged = documentChanged || nextDocument != workingDocument
                        workingDocument = nextDocument
                        "Updated row content in ${rowTitle.ifBlank { targetRow.id }} in ${targetTable.table.title}"
                    }

                    "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) error("Missing row target")
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val targetAction = action.rowBlockId
                            .takeIf(String::isNotBlank)
                            ?.let { rowBlockId -> action.copy(blockId = rowBlockId) }
                            ?: action
                        val targetBlock = targetRow.blocks.findMatchingBlock(targetAction)
                            ?: error("Could not find row content block")
                        val mutation = tableMutationUseCase.updateRowBlocks(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowId = targetRow.id,
                            command = { EditorCommand.DeleteBlock(targetBlock.id) },
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Deleted row content from ${rowTitle.ifBlank { targetRow.id }} in ${targetTable.table.title}"
                    }

                    "UPDATE_TABLE_CELL", "CLEAR_TABLE_CELL" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val isClearAction = actionType == "CLEAR_TABLE_CELL"
                        val value = if (isClearAction) "" else action.resolvedTableCellUpdateValue(columnName)
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        val mutation = tableMutationUseCase.updateCell(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowId = targetRow.id,
                            columnId = targetColumn.id,
                            value = value,
                        )
                        workingDocument = mutation.mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        if (isClearAction) {
                            "Cleared ${targetColumn.name} for ${rowTitle.ifBlank { targetRow.id }} in ${targetTable.table.title}"
                        } else {
                            "Updated ${targetColumn.name} for ${rowTitle.ifBlank { targetRow.id }} in ${targetTable.table.title}"
                        }
                    }

                    "CLEAR_TABLE_CELLS" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }
                        val matchQuery = action.bulkCellMatchQuery()
                            .ifBlank { error("Missing cell match value") }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val column = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        val matchingCellCount = targetTable.table
                            .rowsMatchingCell(column, matchQuery)
                            .size
                        if (matchingCellCount == 0) {
                            error("Could not find cells matching: $matchQuery")
                        }
                        val rowIds = targetTable.table.rowsMatchingCell(column, matchQuery)
                            .map(PageTableRow::id)
                            .toSet()
                        val mutation = tableMutationUseCase.updateCells(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowIds = rowIds,
                            columnId = column.id,
                            value = "",
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Cleared $matchingCellCount matching cell${if (matchingCellCount == 1) "" else "s"} in ${targetTable.table.title}"
                    }

                    "CHANGE_TABLE_VIEW", "SET_TABLE_VIEW" -> {
                        val view = action.tableView.ifBlank { action.value }.ifBlank { action.content }.toPageTableView()
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val mutation = tableMutationUseCase.updateView(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            view = view,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Changed ${targetTable.table.title} view to ${view.name}"
                    }

                    "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val mutation = tableMutationUseCase.updateViewConfig(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            config = action.toTableViewConfig(targetTable.table),
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Updated ${targetTable.table.title} view config"
                    }

                    "SORT_TABLE", "SET_TABLE_SORT" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val direction = action.sortDirection.ifBlank { action.value }.ifBlank { action.content }.toPageTableSortDirection()
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        val mutation = tableMutationUseCase.updateSort(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = targetColumn.id,
                            direction = direction,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Sorted ${targetTable.table.title} by ${targetColumn.name} ${direction.name.lowercase()}"
                    }

                    "CLEAR_TABLE_SORT" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val mutation = tableMutationUseCase.updateSort(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = "",
                            direction = PageTableSortDirection.Ascending,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Cleared sort in ${targetTable.table.title}"
                    }

                    "FILTER_TABLE", "SET_TABLE_FILTER" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val query = action.filterQuery.ifBlank { action.value }.ifBlank { action.content }.ifBlank { error("Missing filter query") }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        val mutation = tableMutationUseCase.updateFilter(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = targetColumn.id,
                            query = query,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Filtered ${targetTable.table.title} by ${targetColumn.name}"
                    }

                    "CLEAR_TABLE_FILTER" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val mutation = tableMutationUseCase.updateFilter(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            filter = PageTableFilter(),
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Cleared filter in ${targetTable.table.title}"
                    }

                    "GROUP_TABLE", "SET_TABLE_GROUP" -> {
                        val columnId = action.groupByColumnId.ifBlank { action.columnId }
                        val columnName = action.groupByColumnName.ifBlank { action.columnName }.ifBlank { action.propertyName }.ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
                        val mutation = tableMutationUseCase.updateGroup(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = targetColumn.id,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Grouped ${targetTable.table.title} by ${targetColumn.name}"
                    }

                    "CLEAR_TABLE_GROUP" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val mutation = tableMutationUseCase.updateGroup(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = "",
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = documentChanged || mutation.changed
                        "Cleared group in ${targetTable.table.title}"
                    }

                    "CREATE_SUBPAGE" -> {
                        val pageTitle = action.title.ifBlank { error("Missing subpage title") }
                        val created = pageRepository.createPage(
                            workspaceId = page.workspaceId,
                            title = pageTitle,
                            content = action.content.toPageContentDocument(),
                            parentPageId = page.id,
                        )
                        createdPages += created
                        "Created subpage: $pageTitle"
                    }

                    "CREATE_PAGE" -> {
                        val moduleType = action.requestedModuleType()
                        val pageTitle = action.title.ifBlank {
                            moduleType?.let { PageModuleTemplates.defaultTitle(it) } ?: error("Missing page title")
                        }
                        val created = pageRepository.createPage(
                            workspaceId = page.workspaceId,
                            title = pageTitle,
                            content = moduleType?.let { PageModuleTemplates.contentFor(it) }
                                ?: action.content.toPageContentDocument(),
                            parentPageId = if (moduleType != null) page.id else null,
                        )
                        createdPages += created
                        if (moduleType != null) "Created ${moduleType.label} module: $pageTitle" else "Created page: $pageTitle"
                    }

                    "CREATE_TASK" -> {
                        val plan = workingDocument.planTaskTableAction(action)
                        val nextDocument = workingDocument.applyTaskTablePlan(
                            plan = plan,
                            actionIndex = actionIndex,
                            undoCommands = undoCommands,
                        )
                        documentChanged = documentChanged || nextDocument != workingDocument
                        workingDocument = nextDocument
                        "Added task row ${plan.rowTitle} to ${plan.tableTitle}"
                    }

                    "CREATE_REMINDER" -> {
                        val plan = workingDocument.planTaskTableAction(action)
                        val reminderUndoCommands = mutableListOf<AiUndoCommandSummary>()
                        val nextDocument = workingDocument.applyTaskTablePlan(
                            plan = plan,
                            actionIndex = actionIndex,
                            undoCommands = reminderUndoCommands,
                        )
                        val reminder = scheduleTableDateReminderUseCase(
                            page = page,
                            document = nextDocument,
                            tableBlockId = plan.tableBlock.id,
                            rowId = plan.rowId,
                            columnId = plan.dateColumnId,
                            value = plan.dateCellValue,
                        ) ?: error("Reminder date or time must be in the future.")
                        documentChanged = documentChanged || nextDocument != workingDocument
                        workingDocument = nextDocument
                        undoCommands += reminderUndoCommands
                        createdReminders += reminder
                        "Added reminder row ${plan.rowTitle} to ${plan.tableTitle}"
                    }

                    else -> error("Unsupported action type: ${action.type}")
                }
            }.onSuccess { message ->
                executedActionIndexes += actionIndex
                messages += "Done: $message"
            }.onFailure { error ->
                val errorMessage = error.localizedMessage ?: "Action failed before it could update the page."
                validationIssues += AiPageActionValidationIssue(
                    actionIndex = actionIndex,
                    actionType = trace.actionType,
                    actionDomain = trace.domain.id,
                    field = "type",
                    code = "execution_failed",
                    message = errorMessage,
                )
                messages += "Failed ${trace.messageLabel}: $errorMessage"
            }
        }

        if (executedActionIndexes.isNotEmpty()) {
            val syncedDocument = workingDocument.withBudgetLedgerSummarySynced()
            if (syncedDocument != workingDocument) {
                val nextDocument = workingDocument.applyDerivedTableChanges(
                    plannedDocument = syncedDocument,
                    actionIndex = executedActionIndexes.last(),
                    undoCommands = undoCommands,
                )
                documentChanged = documentChanged || nextDocument != workingDocument
                workingDocument = nextDocument
            }
        }

        val pageLinks = buildList {
            if (titleChanged || documentChanged) {
                add(AiChatPageLink(pageId = page.id, title = workingTitle.ifBlank { "Untitled page" }))
            }
            createdPages.forEach { createdPage ->
                add(createdPage.toChatPageLink())
            }
        }

        return AiPageActionExecutionResult(
            messages = messages,
            updatedTitle = workingTitle.takeIf { titleChanged },
            updatedDocument = workingDocument.takeIf { documentChanged },
            createdPages = createdPages,
            createdTasks = createdTasks,
            createdReminders = createdReminders,
            pageLinks = pageLinks,
            validationIssues = validationIssues,
            undoCommands = undoCommands,
            executedActionIndexes = executedActionIndexes,
        )
    }

    private fun PageBlockDocument.applyAiEditorCommand(
        command: EditorCommand,
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): PageBlockDocument {
        return when (command) {
            is EditorCommand.InsertBlock -> pageMutationUseCase.insertBlock(
                document = this,
                block = command.block,
                parentBlockId = command.parentBlockId,
                index = command.index,
            ).captureForAi(actionIndex, undoCommands)

            is EditorCommand.DeleteBlock -> pageMutationUseCase.deleteBlock(
                document = this,
                blockId = command.blockId,
            ).captureForAi(actionIndex, undoCommands)

            is EditorCommand.ChangeBlockType -> pageMutationUseCase.changeBlockType(
                document = this,
                blockId = command.blockId,
                type = command.type,
            ).captureForAi(actionIndex, undoCommands)

            is EditorCommand.UpdateBlockText -> pageMutationUseCase.updateBlockRichText(
                document = this,
                blockId = command.blockId,
                text = command.text,
                spans = command.richTextSpans,
            ).captureForAi(actionIndex, undoCommands)

            is EditorCommand.ToggleTodo -> pageMutationUseCase.toggleTodoBlock(
                document = this,
                blockId = command.blockId,
                isChecked = command.isChecked,
            ).captureForAi(actionIndex, undoCommands)

            is EditorCommand.ReplaceTable -> tableMutationUseCase.replaceTable(
                document = this,
                tableBlockId = command.blockId,
                transform = { command.table },
            ).captureForAi(actionIndex, undoCommands)

            else -> error("AI mutation command must use a shared domain use case: ${command::class.simpleName}")
        }
    }

    private fun DomainBlockMutationResult.captureForAi(
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): PageBlockDocument {
        if (changed) {
            applied.result.undoCommand.captureForAi(actionIndex, undoCommands)
        }
        return document
    }

    private fun PageMutationResult.captureForAi(
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): PageBlockDocument {
        if (changed) {
            applied.result.undoCommand.captureForAi(actionIndex, undoCommands)
        }
        return document
    }

    private fun PropertyMutationResult.captureForAi(
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): PageBlockDocument {
        if (changed) {
            applied.result.undoCommand.captureForAi(actionIndex, undoCommands)
        }
        return document
    }

    private fun TableMutationResult.captureForAi(
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): PageBlockDocument {
        if (changed) {
            commandResult.undoCommand.captureForAi(actionIndex, undoCommands)
        }
        return document
    }

    private fun EditorCommand?.captureForAi(
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ) {
        this?.toAiUndoCommandSummary(actionIndex)?.let(undoCommands::add)
    }

    private fun PageBlockDocument.applyTaskTablePlan(
        plan: TaskTableMutationPlan,
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): PageBlockDocument {
        return if (plan.isNewTable) {
            pageMutationUseCase.insertBlock(
                document = this,
                block = plan.tableBlock,
            ).captureForAi(actionIndex, undoCommands)
        } else {
            tableMutationUseCase.replaceTable(
                document = this,
                tableBlockId = plan.tableBlock.id,
                transform = { plan.tableBlock.table },
            ).captureForAi(actionIndex, undoCommands)
        }
    }

    private fun PageBlockDocument.applyDerivedTableChanges(
        plannedDocument: PageBlockDocument,
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): PageBlockDocument {
        return plannedDocument.blocks.collectAiTableBlocks().fold(this) { currentDocument, plannedBlock ->
            val currentBlock = currentDocument.findTableBlock(plannedBlock.id)
            if (currentBlock == null || currentBlock.table == plannedBlock.table) {
                currentDocument
            } else {
                tableMutationUseCase.replaceTable(
                    document = currentDocument,
                    tableBlockId = plannedBlock.id,
                    transform = { plannedBlock.table },
                ).captureForAi(actionIndex, undoCommands)
            }
        }
    }

    private fun PageBlockDocument.deleteMatchingBlock(
        action: ChatAction,
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): DocumentBlockMutationResult {
        val target = blocks.findMatchingBlock(action)
            ?: error("Could not find block to delete")
        return DocumentBlockMutationResult(
            document = applyAiEditorCommand(
                command = EditorCommand.DeleteBlock(target.id),
                actionIndex = actionIndex,
                undoCommands = undoCommands,
            ),
            label = target.blockLabel(),
        )
    }

    private fun PageBlockDocument.updateMatchingBlock(
        action: ChatAction,
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): DocumentBlockMutationResult {
        val target = blocks.findMatchingBlock(action)
            ?: error("Could not find block to update")
        val updatedBlock = target.withActionUpdate(action)
        var updatedDocument = this

        if (target.type != updatedBlock.type) {
            updatedDocument = updatedDocument.applyAiEditorCommand(
                EditorCommand.ChangeBlockType(
                    blockId = target.id,
                    type = updatedBlock.type,
                ),
                actionIndex = actionIndex,
                undoCommands = undoCommands,
            )
        }
        if (updatedBlock.type == PageBlockType.DatabaseTable) {
            if (target.table != updatedBlock.table) {
                updatedDocument = updatedDocument.applyAiEditorCommand(
                    EditorCommand.ReplaceTable(
                        blockId = target.id,
                        table = updatedBlock.table,
                    ),
                    actionIndex = actionIndex,
                    undoCommands = undoCommands,
                )
            }
        } else if (target.text != updatedBlock.text || target.richTextSpans != updatedBlock.richTextSpans) {
            updatedDocument = updatedDocument.applyAiEditorCommand(
                EditorCommand.UpdateBlockText(
                    blockId = target.id,
                    text = updatedBlock.text,
                    richTextSpans = updatedBlock.richTextSpans,
                ),
                actionIndex = actionIndex,
                undoCommands = undoCommands,
            )
        }
        if (target.isChecked != updatedBlock.isChecked) {
            updatedDocument = updatedDocument.applyAiEditorCommand(
                EditorCommand.ToggleTodo(
                    blockId = target.id,
                    isChecked = updatedBlock.isChecked,
                ),
                actionIndex = actionIndex,
                undoCommands = undoCommands,
            )
        }

        return DocumentBlockMutationResult(
            document = updatedDocument,
            label = target.blockLabel(),
        )
    }

    private fun PageBlockDocument.formatMatchingBlockText(
        action: ChatAction,
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): DocumentBlockMutationResult {
        val target = blocks.findMatchingBlock(action)
            ?: error("Could not find block to format")
        val (start, end) = action.findFormatRangeIn(target.text)
            ?: error("Could not find text to format")
        val nextSpans = action.applyTextFormat(
            spans = target.richTextSpans,
            start = start,
            end = end,
            textLength = target.text.length,
        )
        if (nextSpans == RichTextSpanEngine.normalize(target.richTextSpans, target.text)) {
            error("No supported text format was provided")
        }
        return DocumentBlockMutationResult(
            document = applyAiEditorCommand(
                command = EditorCommand.UpdateBlockText(
                    blockId = target.id,
                    text = target.text,
                    richTextSpans = nextSpans,
                ),
                actionIndex = actionIndex,
                undoCommands = undoCommands,
            ),
            label = target.blockLabel(),
        )
    }

    private fun PageBlockDocument.updateRowBlockThroughUseCase(
        tableBlockId: String,
        rowId: String,
        currentBlock: PageBlock,
        updatedBlock: PageBlock,
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): PageBlockDocument {
        var nextDocument = this

        fun applyRowCommand(command: EditorCommand) {
            val mutation = tableMutationUseCase.updateRowBlocks(
                document = nextDocument,
                tableBlockId = tableBlockId,
                rowId = rowId,
                command = { command },
            )
            nextDocument = mutation.captureForAi(actionIndex, undoCommands)
        }

        if (currentBlock.type != updatedBlock.type) {
            applyRowCommand(
                EditorCommand.ChangeBlockType(
                    blockId = currentBlock.id,
                    type = updatedBlock.type,
                ),
            )
        }
        if (
            (updatedBlock.type == PageBlockType.DatabaseTable || updatedBlock.type == PageBlockType.Table) &&
            currentBlock.table != updatedBlock.table
        ) {
            applyRowCommand(
                EditorCommand.ReplaceTable(
                    blockId = currentBlock.id,
                    table = updatedBlock.table,
                ),
            )
        } else if (
            currentBlock.text != updatedBlock.text ||
            currentBlock.richTextSpans != updatedBlock.richTextSpans
        ) {
            applyRowCommand(
                EditorCommand.UpdateBlockText(
                    blockId = currentBlock.id,
                    text = updatedBlock.text,
                    richTextSpans = updatedBlock.richTextSpans,
                ),
            )
        }
        if (currentBlock.isChecked != updatedBlock.isChecked) {
            applyRowCommand(
                EditorCommand.ToggleTodo(
                    blockId = currentBlock.id,
                    isChecked = updatedBlock.isChecked,
                ),
            )
        }
        return nextDocument
    }

}

private fun List<PageBlock>.collectAiTableBlocks(): List<PageBlock> {
    return buildList {
        this@collectAiTableBlocks.forEach { block ->
            if (block.type == PageBlockType.DatabaseTable || block.type == PageBlockType.Table) {
                add(block)
            }
            addAll(block.children.collectAiTableBlocks())
        }
    }
}

data class AiPageActionExecutionResult(
    val messages: List<String> = emptyList(),
    val updatedTitle: String? = null,
    val updatedDocument: PageBlockDocument? = null,
    val createdPages: List<Page> = emptyList(),
    val createdTasks: List<TaskItem> = emptyList(),
    val createdReminders: List<Reminder> = emptyList(),
    val pageLinks: List<AiChatPageLink> = emptyList(),
    val validationIssues: List<AiPageActionValidationIssue> = emptyList(),
    val undoCommands: List<AiUndoCommandSummary> = emptyList(),
    val executedActionIndexes: List<Int> = emptyList(),
)

data class AiPageActionValidationIssue(
    val actionIndex: Int? = null,
    val actionType: String = "",
    val actionDomain: String = "",
    val field: String = "",
    val code: String = "",
    val message: String = "",
)

private fun AiPageActionValidationIssue.withTrace(
    trace: AiActionExecutionTrace,
): AiPageActionValidationIssue {
    return copy(
        actionType = actionType.ifBlank { trace.actionType },
        actionDomain = actionDomain.ifBlank { trace.domain.id },
    )
}

private val FormulaColumnReferenceRegex by lazy { Regex("""\{([^}]+)\}""") }
private val DateCellStartDateRegex by lazy { Regex(""""startDate"\s*:\s*"([^"]+)"""") }
private val TaskDateCellKeys = setOf("date", "due date", "deadline", "time", "reminder")

private fun PageBlockDocument.validateActionTarget(
    action: ChatAction,
    actionIndex: Int,
): AiPageActionValidationIssue? {
    fun targetNotFound(field: String, targetKind: String, targetLabel: String): AiPageActionValidationIssue {
        val label = targetLabel.ifBlank { "target $targetKind" }
        return AiPageActionValidationIssue(
            actionIndex = actionIndex,
            field = field,
            code = "target_not_found",
            message = "Could not find $targetKind: $label.",
        )
    }

    fun targetTable(): PageBlock? = blocks.findMatchingTable(action)

    fun tableIssue(): AiPageActionValidationIssue? {
        return if (targetTable() == null) {
            targetNotFound(
                field = "tableTitle",
                targetKind = "table",
                targetLabel = action.tableTitle.ifBlank { action.title },
            )
        } else {
            null
        }
    }

    fun missingColumnIssue(
        table: PageBlock,
        field: String,
        columnId: String,
        columnName: String,
    ): AiPageActionValidationIssue? {
        if (columnId.isBlank() && columnName.isBlank()) return null
        return if (table.table.findColumn(columnId, columnName) == null) {
            targetNotFound(
                field = field,
                targetKind = "column",
                targetLabel = columnName.ifBlank { columnId },
            )
        } else {
            null
        }
    }

    fun columnIssue(table: PageBlock): AiPageActionValidationIssue? {
        val actionType = action.type.normalizedActionType()
        val columnId = when (actionType) {
            "GROUP_TABLE", "SET_TABLE_GROUP" -> action.groupByColumnId
            else -> action.columnId
        }
        val columnName = when (actionType) {
            "GROUP_TABLE", "SET_TABLE_GROUP" -> action.groupByColumnName
            else -> action.columnName
        }
            .ifBlank { action.propertyName }
            .ifBlank { action.title }
        return missingColumnIssue(
            table = table,
            field = if (actionType in setOf("GROUP_TABLE", "SET_TABLE_GROUP")) {
                "groupByColumnName"
            } else {
                "columnName"
            },
            columnId = columnId,
            columnName = columnName,
        ) ?: if (columnId.isBlank() && columnName.isBlank()) {
            targetNotFound(
                field = "columnName",
                targetKind = "column",
                targetLabel = "",
            )
        } else {
            null
        }
    }

    fun viewConfigColumnIssue(table: PageBlock): AiPageActionValidationIssue? {
        return missingColumnIssue(
            table = table,
            field = "calendarDateColumnName",
            columnId = action.calendarDateColumnId,
            columnName = action.calendarDateColumnName,
        ) ?: missingColumnIssue(
            table = table,
            field = "timelineStartColumnName",
            columnId = action.timelineStartColumnId,
            columnName = action.timelineStartColumnName,
        ) ?: missingColumnIssue(
            table = table,
            field = "timelineEndColumnName",
            columnId = action.timelineEndColumnId,
            columnName = action.timelineEndColumnName,
        ) ?: missingColumnIssue(
            table = table,
            field = "dashboardMetricColumnName",
            columnId = action.dashboardMetricColumnId,
            columnName = action.dashboardMetricColumnName,
        ) ?: missingColumnIssue(
            table = table,
            field = "dashboardGroupColumnName",
            columnId = action.dashboardGroupColumnId,
            columnName = action.dashboardGroupColumnName,
        ) ?: missingColumnIssue(
            table = table,
            field = "groupByColumnName",
            columnId = action.groupByColumnId,
            columnName = action.groupByColumnName,
        )
    }

    fun missingTableIssue(field: String, tableId: String, tableTitle: String): AiPageActionValidationIssue? {
        if (tableId.isBlank() && tableTitle.isBlank()) return null
        val found = if (tableId.isNotBlank()) {
            findTableBlock(tableId) != null
        } else {
            findTableBlockId(tableTitle) != null
        }
        return if (found) {
            null
        } else {
            targetNotFound(
                field = field,
                targetKind = "table",
                targetLabel = tableTitle.ifBlank { tableId },
            )
        }
    }

    fun relationConfigIssue(): AiPageActionValidationIssue? {
        return missingTableIssue(
            field = "relationTargetTableTitle",
            tableId = action.relationTargetTableId,
            tableTitle = action.relationTargetTableTitle,
        )
    }

    fun formulaConfigIssue(table: PageBlock): AiPageActionValidationIssue? {
        val formula = action.effectiveFormula()
        if (formula.isBlank()) {
            return if (action.type.normalizedActionType() == "UPDATE_FORMULA_COLUMN") {
                AiPageActionValidationIssue(
                    actionIndex = actionIndex,
                    field = "formula",
                    code = "required",
                    message = "Formula column needs a formula before it can be updated.",
                )
            } else {
                null
            }
        }
        val targetColumn = table.table.findColumn(
            columnId = action.columnId,
            columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title },
        )
        val hasSelfReference = targetColumn != null &&
            FormulaColumnReferenceRegex.findAll(formula)
                .map { match -> match.groupValues.getOrNull(1).orEmpty().trim().normalizedAiKey() }
                .any { reference -> reference == targetColumn.name.normalizedAiKey() }
        if (hasSelfReference) {
            return AiPageActionValidationIssue(
                actionIndex = actionIndex,
                field = "formula",
                code = "invalid_formula",
                message = "Formula cannot reference its own column: ${targetColumn.name}.",
            )
        }
        val missingReference = FormulaColumnReferenceRegex.findAll(formula)
            .map { match -> match.groupValues.getOrNull(1).orEmpty().trim() }
            .filter { columnName -> columnName.isNotBlank() }
            .firstOrNull { columnName -> table.table.findColumn(columnName = columnName) == null }
        if (missingReference != null) {
            return targetNotFound(
                field = "formula",
                targetKind = "formula column",
                targetLabel = missingReference,
            )
        }
        if (!formula.isValidAiFormula(table.table)) {
            return AiPageActionValidationIssue(
                actionIndex = actionIndex,
                field = "formula",
                code = "invalid_formula",
                message = "Formula must use numbers, column references like {Amount}, operators + - * /, and parentheses.",
            )
        }
        return null
    }

    fun rollupConfigIssue(table: PageBlock): AiPageActionValidationIssue? {
        val relationColumn = table.table.findColumn(
            columnId = action.rollupRelationColumnId,
            columnName = action.rollupRelationColumnName,
        )
        if ((action.rollupRelationColumnId.isNotBlank() || action.rollupRelationColumnName.isNotBlank()) &&
            relationColumn == null
        ) {
            return targetNotFound(
                field = "rollupRelationColumnName",
                targetKind = "column",
                targetLabel = action.rollupRelationColumnName.ifBlank { action.rollupRelationColumnId },
            )
        }

        val targetColumnId = action.rollupTargetColumnId
        val targetColumnName = action.rollupTargetColumnName
        if (targetColumnId.isBlank() && targetColumnName.isBlank()) return null

        val targetTable = relationColumn
            ?.relationTargetTableId
            ?.takeIf { tableId -> tableId.isNotBlank() }
            ?.let { tableId -> findTableBlock(tableId) }
            ?: action.relationTargetTableId
                .takeIf { tableId -> tableId.isNotBlank() }
                ?.let { tableId -> findTableBlock(tableId) }
            ?: action.relationTargetTableTitle
                .takeIf { tableTitle -> tableTitle.isNotBlank() }
                ?.let { tableTitle ->
                    findTableBlockId(tableTitle)?.let { tableId -> findTableBlock(tableId) }
                }

        if (targetTable == null) {
            return AiPageActionValidationIssue(
                actionIndex = actionIndex,
                field = "relationTargetTableTitle",
                code = "target_not_found",
                message = "Could not find rollup target table for ${targetColumnName.ifBlank { targetColumnId }}.",
            )
        }

        return missingColumnIssue(
            table = targetTable,
            field = "rollupTargetColumnName",
            columnId = targetColumnId,
            columnName = targetColumnName,
        )
    }

    fun columnConfigIssue(table: PageBlock): AiPageActionValidationIssue? {
        return formulaConfigIssue(table) ?: relationConfigIssue() ?: rollupConfigIssue(table)
    }

    fun invalidDateIssue(field: String, value: String): AiPageActionValidationIssue? {
        if (value.isBlank() || value.isValidAiDateCellValue()) return null
        return AiPageActionValidationIssue(
            actionIndex = actionIndex,
            field = field,
            code = "invalid_date",
            message = "Could not parse date value: $value.",
        )
    }

    fun missingReminderDateIssue(): AiPageActionValidationIssue? {
        if (action.delayMinutes != null) return null
        val dateValue = action.explicitTaskDateCellValue()
        if (dateValue.isNotBlank()) return invalidDateIssue("cellValues.date", dateValue)
        return AiPageActionValidationIssue(
            actionIndex = actionIndex,
            field = "cellValues.date",
            code = "required",
            message = "Reminder needs a date or time before it can be created.",
        )
    }

    fun missingMediaPayloadIssue(): AiPageActionValidationIssue? {
        if (action.blockType.toPageBlockTypeOrNull() != PageBlockType.MediaFile) return null
        if (action.mediaUri.isNotBlank()) return null
        return AiPageActionValidationIssue(
            actionIndex = actionIndex,
            field = "mediaUri",
            code = "required",
            message = "Media/file block needs mediaUri before it can be created.",
        )
    }

    fun taskDateIssue(): AiPageActionValidationIssue? {
        val dateValue = action.explicitTaskDateCellValue()
        return invalidDateIssue("cellValues.date", dateValue)
    }

    fun tableDateCellIssue(table: PageBlock): AiPageActionValidationIssue? {
        val columnName = action.columnName.ifBlank { action.propertyName }
        val column = table.table.findColumn(action.columnId, columnName) ?: return null
        if (column.type != PageTableColumnType.Date) return null
        val value = action.value.ifBlank { action.content }
        return invalidDateIssue("value", value)
    }

    fun addedRowDateCellIssue(table: PageBlock): AiPageActionValidationIssue? {
        table.table.columns
            .filter { column -> column.type == PageTableColumnType.Date }
            .forEach { column ->
                val value = action.cellValues.entries.firstOrNull { entry ->
                    entry.key.normalizedAiKey() == column.name.normalizedAiKey()
                }?.value.orEmpty()
                invalidDateIssue("cellValues.${column.name}", value)?.let { issue -> return issue }
            }
        return null
    }

    fun rowIssue(table: PageBlock): AiPageActionValidationIssue? {
        val rowTitle = action.rowTitle.ifBlank { action.title }
        return when (table.table.resolveRow(action.rowId, rowTitle)) {
            is AiRowResolution.Found -> null
            AiRowResolution.Ambiguous -> AiPageActionValidationIssue(
                actionIndex = actionIndex,
                field = "rowTitle",
                code = "ambiguous_target",
                message = "More than one row matches: ${rowTitle.ifBlank { action.rowId }}.",
            )
            AiRowResolution.Missing -> targetNotFound(
                field = "rowTitle",
                targetKind = "row",
                targetLabel = rowTitle,
            )
        }
    }

    fun rowPageBlockIssue(table: PageBlock): AiPageActionValidationIssue? {
        val rowTitle = action.rowTitle.ifBlank { action.title }
        val row = table.table.findRow(action.rowId, rowTitle)
            ?: return rowIssue(table)
        val effectiveAction = action.copy(blockId = action.rowBlockId.ifBlank { action.blockId })
        return if (row.blocks.anyMatchingBlock(effectiveAction)) {
            null
        } else {
            targetNotFound(
                field = "rowBlockId",
                targetKind = "row content block",
                targetLabel = action.blockText.ifBlank { action.content }.ifBlank { action.title },
            )
        }
    }

    return when (action.type.normalizedActionType()) {
        "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> missingMediaPayloadIssue()

        "DELETE_BLOCK",
        "FORMAT_BLOCK_TEXT",
        "UPDATE_BLOCK",
        "EDIT_BLOCK",
        "UPDATE_TODO",
        "CHECK_BLOCK",
        "UNCHECK_BLOCK",
        -> {
            if (blocks.anyMatchingBlock(action)) {
                null
            } else {
                targetNotFound(
                    field = "blockText",
                    targetKind = "block",
                    targetLabel = action.blockText.ifBlank { action.title }.ifBlank { action.content },
                )
            }
        }

        "UPDATE_PROPERTY", "DELETE_PROPERTY" -> {
            val propertyName = action.propertyName.ifBlank { action.title }
            if (properties.any { property -> property.name.normalizedAiKey() == propertyName.normalizedAiKey() }) {
                null
            } else {
                targetNotFound(
                    field = "propertyName",
                    targetKind = "property",
                    targetLabel = propertyName,
                )
            }
        }

        "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE",
        "ADD_TABLE_COLUMN",
        "CHANGE_TABLE_VIEW", "SET_TABLE_VIEW",
        "CLEAR_TABLE_SORT", "CLEAR_TABLE_FILTER", "CLEAR_TABLE_GROUP" -> tableIssue()

        "ADD_TABLE_ROW" -> {
            val table = targetTable() ?: return tableIssue()
            addedRowDateCellIssue(table)
        }

        "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG" -> {
            val table = targetTable() ?: return tableIssue()
            viewConfigColumnIssue(table)
        }

        "DELETE_TABLE_COLUMN", "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN",
        "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE",
        "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG",
        "UPDATE_FORMULA_COLUMN", "UPDATE_RELATION_COLUMN", "UPDATE_ROLLUP_COLUMN",
        "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN",
        "SORT_TABLE", "SET_TABLE_SORT",
        "FILTER_TABLE", "SET_TABLE_FILTER",
        "GROUP_TABLE", "SET_TABLE_GROUP" -> {
            val table = targetTable() ?: return tableIssue()
            columnIssue(table) ?: columnConfigIssue(table)
        }

        "DELETE_TABLE_ROW", "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW",
        "REORDER_TABLE_ROW", "MOVE_TABLE_ROW",
        "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table)
        }

        "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK",
        "CHECK_ROW_PAGE_BLOCK", "UNCHECK_ROW_PAGE_BLOCK",
        "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" -> {
            val table = targetTable() ?: return tableIssue()
            rowPageBlockIssue(table)
        }

        "UPDATE_TABLE_CELL", "CLEAR_TABLE_CELL" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table) ?: columnIssue(table) ?: tableDateCellIssue(table)
        }

        "CLEAR_TABLE_CELLS" -> {
            val table = targetTable() ?: return tableIssue()
            columnIssue(table)?.let { issue -> return issue }
            val matchQuery = action.bulkCellMatchQuery()
            if (matchQuery.isBlank()) {
                AiPageActionValidationIssue(
                    actionIndex = actionIndex,
                    field = "filterQuery",
                    code = "required",
                    message = "Bulk cell clear needs a value to match.",
                )
            } else {
                val columnName = action.columnName.ifBlank { action.propertyName }
                val column = table.table.findColumn(action.columnId, columnName)
                    ?: return columnIssue(table)
                if (table.table.rowsMatchingCell(column, matchQuery).isEmpty()) {
                    targetNotFound(
                        field = "filterQuery",
                        targetKind = "cell value",
                        targetLabel = matchQuery,
                    )
                } else {
                    null
                }
            }
        }

        "CREATE_TASK" -> taskDateIssue()

        "CREATE_REMINDER" -> missingReminderDateIssue()

        else -> null
    }
}

private fun ChatAction.explicitTaskDateCellValue(): String {
    return cellValues.entries.firstOrNull { entry ->
        entry.key.normalizedAiKey() in TaskDateCellKeys
    }?.value.orEmpty()
}

private fun ChatAction.effectiveFormula(): String =
    formula.ifBlank { value }.ifBlank { content }.trim()

private fun String.isValidAiFormula(table: PageTable): Boolean {
    if (isBlank()) return false
    var expression = this
    table.columns
        .sortedByDescending { column -> column.name.length }
        .forEach { column ->
            expression = expression.replace("{${column.name}}", "1", ignoreCase = true)
        }
    if (FormulaColumnReferenceRegex.containsMatchIn(expression)) return false
    return expression.evaluateAiFormulaArithmeticExpression() != null
}

private fun String.evaluateAiFormulaArithmeticExpression(): Double? {
    class Parser(private val input: String) {
        private var index = 0

        fun parse(): Double? {
            val value = parseExpression() ?: return null
            skipSpaces()
            return if (index == input.length) value else null
        }

        private fun parseExpression(): Double? {
            var value = parseTerm() ?: return null
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '+' -> {
                        index++
                        value + (parseTerm() ?: return null)
                    }
                    '-' -> {
                        index++
                        value - (parseTerm() ?: return null)
                    }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double? {
            var value = parseFactor() ?: return null
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '*' -> {
                        index++
                        value * (parseFactor() ?: return null)
                    }
                    '/' -> {
                        index++
                        val divisor = parseFactor() ?: return null
                        if (divisor == 0.0) return null
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double? {
            skipSpaces()
            if (peek() == '-') {
                index++
                return -(parseFactor() ?: return null)
            }
            if (peek() == '(') {
                index++
                val value = parseExpression() ?: return null
                skipSpaces()
                if (peek() != ')') return null
                index++
                return value
            }
            val start = index
            var dotCount = 0
            while (peek()?.let { char -> char.isDigit() || char == '.' } == true) {
                if (peek() == '.') dotCount++
                if (dotCount > 1) return null
                index++
            }
            return input.substring(start, index).toDoubleOrNull()
        }

        private fun skipSpaces() {
            while (peek()?.isWhitespace() == true) {
                index++
            }
        }

        private fun peek(): Char? = input.getOrNull(index)
    }

    return Parser(this).parse()
}

private fun String.isValidAiDateCellValue(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank()) return true
    val dateText = if (trimmed.startsWith("{")) {
        DateCellStartDateRegex.find(trimmed)?.groupValues?.getOrNull(1).orEmpty()
    } else {
        trimmed
    }
    return dateText.toAiLocalDateOrNull() != null
}

private fun String.toAiLocalDateOrNull(): LocalDate? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    val formatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US),
    )
    return formatters.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDate.parse(trimmed, formatter) }.getOrNull()
    }
}

private data class DocumentBlockMutationResult(
    val document: PageBlockDocument,
    val label: String,
)

private fun ChatAction.toPageBlock(): PageBlock {
    val blockType = blockType.toPageBlockType()
    val block = PageBlockCodec.newBlock(blockType)
    return when (blockType) {
        PageBlockType.DatabaseTable -> {
            block.copy(table = block.table.copy(title = content.ifBlank { title }.ifBlank { "AI table" }))
        }
        PageBlockType.MediaFile -> {
            block.copy(
                text = content.ifBlank { title },
                mediaAttachments = listOfNotNull(toMediaAttachmentOrNull()),
            )
        }
        else -> block.copy(text = content.ifBlank { title })
    }
}

private fun ChatAction.toMediaAttachmentOrNull(): PageMediaAttachment? {
    val uri = mediaUri.trim()
    if (uri.isBlank()) return null
    val fallbackName = title
        .ifBlank { content }
        .ifBlank { uri.substringBefore('?').substringAfterLast('/').ifBlank { "AI attachment" } }
    return PageMediaAttachment(
        id = "media-${UUID.randomUUID()}",
        uri = uri,
        name = mediaName.ifBlank { fallbackName },
        mimeType = mediaMimeType,
        sizeBytes = mediaSizeBytes.coerceAtLeast(0),
    )
}

private fun ChatAction.findFormatRangeIn(text: String): Pair<Int, Int>? {
    if (text.isBlank()) return null
    val start = rangeStart
    val end = rangeEnd
    if (start != null && end != null && start >= 0 && end > start && end <= text.length) {
        return start to end
    }
    val target = textToFormat
        .ifBlank { value }
        .ifBlank { content }
        .trim()
    if (target.isBlank()) return null
    val index = text.indexOf(target, ignoreCase = true)
    return if (index >= 0) index to (index + target.length) else null
}

private fun ChatAction.applyTextFormat(
    spans: List<PageTextSpan>,
    start: Int,
    end: Int,
    textLength: Int,
): List<PageTextSpan> {
    var nextSpans = RichTextSpanEngine.normalize(spans, " ".repeat(textLength))
    when (format.normalizedActionType()) {
        "BOLD", "STRONG" -> {
            nextSpans = RichTextSpanEngine.toggleFormat(nextSpans, RichTextFormat.Bold, start, end, textLength)
        }
        "ITALIC", "EMPHASIS" -> {
            nextSpans = RichTextSpanEngine.toggleFormat(nextSpans, RichTextFormat.Italic, start, end, textLength)
        }
        "UNDERLINE" -> {
            nextSpans = RichTextSpanEngine.toggleFormat(nextSpans, RichTextFormat.Underline, start, end, textLength)
        }
        "STRIKETHROUGH", "STRIKE" -> {
            nextSpans = RichTextSpanEngine.toggleFormat(nextSpans, RichTextFormat.Strikethrough, start, end, textLength)
        }
        "CODE", "MONOSPACE" -> {
            nextSpans = RichTextSpanEngine.toggleFormat(nextSpans, RichTextFormat.Code, start, end, textLength)
        }
    }
    if (linkUrl.isNotBlank()) {
        nextSpans = RichTextSpanEngine.applyLink(nextSpans, start, end, textLength, linkUrl)
    }
    if (color.isNotBlank()) {
        nextSpans = RichTextSpanEngine.applyColor(nextSpans, start, end, textLength, color)
    }
    if (highlight.isNotBlank()) {
        nextSpans = RichTextSpanEngine.applyHighlight(nextSpans, start, end, textLength, highlight)
    }
    return nextSpans
}

private fun ChatAction.toDatabaseBlock(): PageBlock {
    val tableName = tableTitle.ifBlank { title }.ifBlank { content }.ifBlank { "AI database" }
    val columns = buildTableColumns()
    return PageBlockCodec.newBlock(PageBlockType.DatabaseTable).copy(
        table = PageTable(
            title = tableName,
            view = tableView.toPageTableView(),
            columns = columns,
            rows = buildTableRows(columns),
        ),
    )
}

private fun Int.toAiZeroBasedIndex(): Int {
    return (this - 1).coerceAtLeast(0)
}

private fun ChatAction.buildTableColumns(): List<PageTableColumn> {
    val fromAction = tableColumns.mapNotNull { column ->
        val name = column.name.trim()
        if (name.isBlank()) {
            null
        } else {
            column.toPageTableColumnFromAi()
        }
    }
    if (fromAction.isNotEmpty()) return fromAction

    val keys = (tableRows.flatMap { row -> row.keys } + cellValues.keys)
        .map { key -> key.trim() }
        .filter { key -> key.isNotBlank() }
        .distinctBy { key -> key.normalizedAiKey() }
    if (keys.isNotEmpty()) {
        return keys.map { key -> PageBlockCodec.newTableColumn(key, key.inferTableColumnType()) }
    }

    return listOf(
        PageBlockCodec.newTableColumn("Name"),
        PageBlockCodec.newTableColumn("Status", PageTableColumnType.Status),
        PageBlockCodec.newTableColumn("Date", PageTableColumnType.Date),
    )
}

private fun ChatAction.buildTableRows(columns: List<PageTableColumn>): List<PageTableRow> {
    val rowMaps = when {
        tableRows.isNotEmpty() -> tableRows
        cellValues.isNotEmpty() -> listOf(cellValues)
        rowTitle.isNotBlank() || content.isNotBlank() -> listOf(mapOf(columns.first().name to rowTitle.ifBlank { content }))
        else -> emptyList()
    }
    return rowMaps
        .filter { values -> values.values.any { value -> value.isNotBlank() } }
        .map { values -> columns.newRow(values) }
}

private fun ChatAction.hasMeaningfulTableRowPayload(): Boolean {
    return rowTitle.isNotBlank() ||
        title.isNotBlank() ||
        content.isNotBlank() ||
        cellValues.values.any { value -> value.isNotBlank() } ||
        tableRows.any { row -> row.values.any { value -> value.isNotBlank() } }
}

private fun ChatAction.withResolvedRelationTarget(document: PageBlockDocument): ChatAction {
    if (relationTargetTableId.isNotBlank() || relationTargetTableTitle.isBlank()) return this
    return copy(relationTargetTableId = document.findTableBlockId(relationTargetTableTitle).orEmpty())
}

private fun PageBlockDocument.findTableBlockId(tableTitle: String): String? {
    fun walk(blocks: List<PageBlock>): String? {
        blocks.forEach { block ->
            if (block.type == PageBlockType.DatabaseTable) {
                val title = block.table.title
                if (title.equals(tableTitle, ignoreCase = true) ||
                    title.contains(tableTitle, ignoreCase = true) ||
                    tableTitle.contains(title, ignoreCase = true)
                ) {
                    return block.id
                }
            }
            walk(block.children)?.let { return it }
        }
        return null
    }
    return walk(blocks)
}

private fun PageBlockDocument.findTableBlock(tableBlockId: String): PageBlock? {
    if (tableBlockId.isBlank()) return null
    fun walk(blocks: List<PageBlock>): PageBlock? {
        blocks.forEach { block ->
            if (
                block.id == tableBlockId &&
                (block.type == PageBlockType.DatabaseTable || block.type == PageBlockType.Table)
            ) {
                return block
            }
            walk(block.children)?.let { return it }
        }
        return null
    }
    return walk(blocks)
}

private fun List<PageBlock>.findMatchingTable(action: ChatAction): PageBlock? {
    val tableBlocks = collectAiTableBlocks()
    if (action.blockId.isNotBlank()) {
        tableBlocks.firstOrNull { block -> block.id == action.blockId }?.let { return it }
    }
    val actionType = action.normalizedExecutionType()
    val tableName = if (actionType in cellTargetActionTypes) {
        action.tableTitle
    } else {
        action.tableTitle.ifBlank { action.title }
    }
    if (tableName.isNotBlank()) {
        tableBlocks.firstOrNull { block ->
            block.table.title.equals(tableName, ignoreCase = true)
        }?.let { return it }
        tableBlocks.firstOrNull { block ->
            block.table.title.contains(tableName, ignoreCase = true)
        }?.let { return it }
    }
    if (actionType == "ADD_TABLE_ROW" && tableName.isBlank()) {
        tableBlocks.firstOrNull { block -> block.isTransactionLedgerTable() }?.let { return it }
    }
    val databaseTables = tableBlocks.filter { block -> block.type == PageBlockType.DatabaseTable }
    if (actionType in cellTargetActionTypes && tableName.isBlank()) {
        val columnName = action.columnName.ifBlank { action.propertyName }
        val matchingTables = if (actionType == "CLEAR_TABLE_CELLS") {
            val matchQuery = action.bulkCellMatchQuery()
            databaseTables.filter { block ->
                val column = block.table.findColumn(action.columnId, columnName)
                column != null &&
                    matchQuery.isNotBlank() &&
                    block.table.rowsMatchingCell(column, matchQuery).isNotEmpty()
            }
        } else {
            val rowTitle = action.rowTitle.ifBlank { action.title }
            databaseTables.filter { block ->
                block.table.resolveRow(action.rowId, rowTitle) is AiRowResolution.Found &&
                    block.table.findColumn(action.columnId, columnName) != null
            }
        }
        matchingTables.singleOrNull()?.let { return it }
        if (databaseTables.size > 1) return null
    }
    return databaseTables.singleOrNull()
        ?: databaseTables.firstOrNull()
        ?: tableBlocks.singleOrNull()
        ?: tableBlocks.firstOrNull()
}

private val cellTargetActionTypes = setOf("UPDATE_TABLE_CELL", "CLEAR_TABLE_CELL", "CLEAR_TABLE_CELLS")

private fun List<PageBlock>.anyMatchingBlock(action: ChatAction): Boolean {
    return any { block ->
        block.matchesBlockAction(action) || block.children.anyMatchingBlock(action)
    }
}

private fun List<PageBlock>.findMatchingBlock(action: ChatAction): PageBlock? {
    for (block in this) {
        if (block.matchesBlockAction(action)) return block
        block.children.findMatchingBlock(action)?.let { return it }
    }
    return null
}

private fun PageBlock.matchesBlockAction(action: ChatAction): Boolean {
    if (action.blockId.isNotBlank() && id == action.blockId) return true
    val requestedType = action.blockType.toPageBlockTypeOrNull()
    if (requestedType != null && type != requestedType) return false
    if (type == PageBlockType.DatabaseTable) {
        val requestedTableTitle = action.tableTitle
            .ifBlank { action.blockText }
            .ifBlank { action.content }
            .ifBlank { action.title }
            .trim()
        if (requestedTableTitle.isBlank()) return requestedType == PageBlockType.DatabaseTable
        return table.title.equals(requestedTableTitle, ignoreCase = true) ||
            table.title.contains(requestedTableTitle, ignoreCase = true) ||
            requestedTableTitle.contains(table.title, ignoreCase = true)
    }
    val targetText = action.blockText.ifBlank { action.content }.ifBlank { action.title }
    if (targetText.isBlank()) return requestedType != null
    val currentText = text.ifBlank { table.title }
    return currentText.equals(targetText, ignoreCase = true) ||
        currentText.contains(targetText, ignoreCase = true) ||
        targetText.contains(currentText, ignoreCase = true)
}

private fun PageBlock.withActionUpdate(action: ChatAction): PageBlock {
    val targetType = action.blockType.toPageBlockTypeOrNull() ?: type
    val nextText = action.content.ifBlank { action.value }.ifBlank { text }
    return when (targetType) {
        PageBlockType.DatabaseTable -> copy(
            type = targetType,
            table = table.copy(title = nextText.ifBlank { table.title }),
            isChecked = action.isChecked ?: isChecked,
        )
        PageBlockType.Table -> copy(
            type = targetType,
            table = table.takeIf { currentTable -> currentTable.columns.isNotEmpty() }
                ?: PageBlockCodec.newBlock(PageBlockType.Table).table,
            isChecked = action.isChecked ?: isChecked,
        )
        else -> copy(
            type = targetType,
            text = nextText,
            isChecked = action.isChecked ?: isChecked,
        )
    }
}

private fun PageBlock.blockLabel(): String {
    return when (type) {
        PageBlockType.DatabaseTable -> table.title.ifBlank { "database table" }
        PageBlockType.Table -> "table"
        else -> text.ifBlank { type.name }
    }
}

private fun List<PageBlock>.countNestedBlocks(): Int {
    return sumOf { block -> 1 + block.children.countNestedBlocks() }
}

private fun PageBlock.resolveRollupTargetColumnId(
    action: ChatAction,
    relationColumn: PageTableColumn?,
    document: PageBlockDocument,
): String {
    if (action.rollupTargetColumnId.isNotBlank()) return action.rollupTargetColumnId
    val targetColumnName = action.rollupTargetColumnName.trim()
    if (targetColumnName.isBlank() || relationColumn == null) return ""
    val targetTableId = relationColumn.relationTargetTableId
    if (targetTableId.isBlank()) return ""
    return document.findTableBlock(targetTableId)
        ?.table
        ?.findColumn(columnName = targetColumnName)
        ?.id
        .orEmpty()
}

private fun ChatAction.resolvedTableCellUpdateValue(columnName: String): String {
    if (value.isNotBlank()) return value
    if (content.isNotBlank()) return content

    val normalizedColumnName = columnName.normalizedAiKey()
    return cellValues.entries
        .firstOrNull { entry -> entry.key.normalizedAiKey() == normalizedColumnName }
        ?.value
        ?: cellValues.values.singleOrNull()
        .orEmpty()
}

private fun PageTable.newRowFromAction(action: ChatAction): PageTableRow {
    val title = action.rowTitle.ifBlank { action.title }.ifBlank { action.content }
    val values = action.cellValues.toMutableMap()
    val firstColumn = columns.firstOrNull()
    if (title.isNotBlank() && firstColumn != null) {
        val hasFirstColumnValue = values.keys.any { key -> key.normalizedAiKey() == firstColumn.name.normalizedAiKey() }
        if (!hasFirstColumnValue) values[firstColumn.name] = title
    }
    return columns.newRow(values)
}

private fun List<PageTableColumn>.newRow(valuesByColumnName: Map<String, String>): PageTableRow {
    val valuesByNormalizedName = valuesByColumnName.entries.associate { entry -> entry.key.normalizedAiKey() to entry.value }
    val cellsByColumnId = associate { column -> column.id to valuesByNormalizedName[column.name.normalizedAiKey()].orEmpty() }
    return PageBlockCodec.newTableRow(this).copy(
        cells = cellsByColumnId,
        cellValues = associate { column ->
            val displayValue = cellsByColumnId[column.id].orEmpty()
            column.id to column.toTypedCellValue(displayValue)
        },
    )
}

private fun PageTable.findColumn(columnId: String = "", columnName: String): PageTableColumn? {
    if (columnId.isNotBlank()) columns.firstOrNull { column -> column.id == columnId }?.let { return it }
    if (columnName.isBlank()) return null
    val normalized = columnName.normalizedAiKey()
    return columns.firstOrNull { column -> column.name.normalizedAiKey() == normalized }
        ?: columns.firstOrNull { column ->
            val current = column.name.normalizedAiKey()
            current.contains(normalized) || normalized.contains(current)
        }
}

private fun PageTable.findRow(rowId: String = "", rowTitle: String): PageTableRow? {
    return (resolveRow(rowId = rowId, rowTitle = rowTitle) as? AiRowResolution.Found)?.row
}

private fun PageTable.resolveRow(
    rowId: String = "",
    rowTitle: String,
): AiRowResolution {
    if (rowId.isNotBlank()) {
        rows.firstOrNull { row -> row.id == rowId }?.let { row ->
            return AiRowResolution.Found(row)
        }
    }
    val target = rowTitle.trim()
    if (target.isBlank()) return AiRowResolution.Missing
    val titleColumn = columns.firstOrNull()
    val targetKey = target.normalizedAiKey()

    rows.resolveUniqueRow { row ->
        row.cellText(titleColumn).normalizedAiKey() == targetKey
    }.unlessMissing()?.let { return it }

    val targetMonth = target.toAiMonthReferenceOrNull()
    if (targetMonth != null) {
        rows.resolveUniqueRow { row ->
            row.cellText(titleColumn)
                .toAiMonthReferenceOrNull()
                ?.matches(targetMonth) == true
        }.unlessMissing()?.let { return it }
    }

    rows.resolveUniqueRow { row ->
        row.searchableCellTexts().any { value -> value.normalizedAiKey() == targetKey }
    }.unlessMissing()?.let { return it }

    if (targetMonth != null) {
        rows.resolveUniqueRow { row ->
            row.searchableCellTexts().any { value ->
                value.toAiMonthReferenceOrNull()?.matches(targetMonth) == true
            }
        }.unlessMissing()?.let { return it }
    }

    rows.resolveUniqueRow { row ->
        val titleKey = row.cellText(titleColumn).normalizedAiKey()
        titleKey.isNotBlank() &&
            targetKey.isNotBlank() &&
            (titleKey.contains(targetKey) || targetKey.contains(titleKey))
    }.unlessMissing()?.let { return it }

    return rows.resolveUniqueRow { row ->
        row.searchableCellTexts().any { value ->
            val valueKey = value.normalizedAiKey()
            valueKey.isNotBlank() &&
                targetKey.isNotBlank() &&
                (valueKey.contains(targetKey) || targetKey.contains(valueKey))
        }
    }
}

private fun PageTableRow.cellText(column: PageTableColumn?): String {
    return column?.let { tableColumn -> cells[tableColumn.id] }.orEmpty().trim()
}

private fun PageTableRow.searchableCellTexts(): Sequence<String> =
    cells.values.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)

private fun ChatAction.bulkCellMatchQuery(): String {
    return filterQuery
        .ifBlank { value }
        .ifBlank { rowTitle }
        .ifBlank { content }
        .ifBlank { title }
        .trim()
}

private fun PageTable.rowsMatchingCell(
    column: PageTableColumn,
    matchQuery: String,
): List<PageTableRow> {
    return rows.filter { row ->
        row.cellText(column).matchesAiCellQuery(matchQuery)
    }
}

private fun String.matchesAiCellQuery(query: String): Boolean {
    val current = trim()
    val target = query.trim()
    if (current.isBlank() || target.isBlank()) return false
    if (current.normalizedAiKey() == target.normalizedAiKey()) return true
    val currentMonth = current.toAiMonthReferenceOrNull()
    val targetMonth = target.toAiMonthReferenceOrNull()
    return currentMonth != null &&
        targetMonth != null &&
        currentMonth.matches(targetMonth)
}

private sealed interface AiRowResolution {
    data class Found(val row: PageTableRow) : AiRowResolution
    data object Missing : AiRowResolution
    data object Ambiguous : AiRowResolution
}

private fun AiRowResolution.unlessMissing(): AiRowResolution? =
    takeUnless { resolution -> resolution == AiRowResolution.Missing }

private inline fun List<PageTableRow>.resolveUniqueRow(
    predicate: (PageTableRow) -> Boolean,
): AiRowResolution {
    var match: PageTableRow? = null
    for (row in this) {
        if (!predicate(row)) continue
        if (match != null) return AiRowResolution.Ambiguous
        match = row
    }
    return match?.let { row -> AiRowResolution.Found(row) } ?: AiRowResolution.Missing
}

private data class AiMonthReference(
    val year: Int?,
    val month: Int,
) {
    fun matches(other: AiMonthReference): Boolean {
        return month == other.month &&
            (year == null || other.year == null || year == other.year)
    }
}

private fun String.toAiMonthReferenceOrNull(): AiMonthReference? {
    val value = trim().lowercase(Locale.ROOT)
    if (value.isBlank()) return null

    AiYearMonthRegex.find(value)?.let { match ->
        val month = match.groupValues[2].toIntOrNull() ?: return@let
        return AiMonthReference(
            year = match.groupValues[1].toIntOrNull(),
            month = month,
        )
    }
    AiNamedMonthNumberRegex.find(value)?.let { match ->
        val month = match.groupValues[1].toIntOrNull() ?: return@let
        return AiMonthReference(
            year = match.groupValues[2].toIntOrNull(),
            month = month,
        )
    }

    val normalizedWords = value
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .split(Regex("\\s+"))
    val month = normalizedWords
        .firstNotNullOfOrNull { word -> AiMonthNames[word] }
        ?: return null
    val year = normalizedWords
        .firstNotNullOfOrNull { word ->
            word.toIntOrNull()?.takeIf { number -> number in 1900..2999 }
        }
    return AiMonthReference(year = year, month = month)
}

private val AiYearMonthRegex by lazy {
    Regex("""(?<!\d)(\d{4})[-/.](0?[1-9]|1[0-2])(?:[-/.]\d{1,2})?(?!\d)""")
}

private val AiNamedMonthNumberRegex by lazy {
    Regex(
        """\b(?:bulan|month|bln)\s*(?:ke[-\s]*)?(0?[1-9]|1[0-2])(?:\s*(?:tahun|year)?\s*(\d{4}))?\b""",
        RegexOption.IGNORE_CASE,
    )
}

private val AiMonthNames = mapOf(
    "january" to 1,
    "januari" to 1,
    "jan" to 1,
    "february" to 2,
    "februari" to 2,
    "feb" to 2,
    "march" to 3,
    "maret" to 3,
    "mac" to 3,
    "mar" to 3,
    "april" to 4,
    "apr" to 4,
    "may" to 5,
    "mei" to 5,
    "june" to 6,
    "juni" to 6,
    "jun" to 6,
    "july" to 7,
    "juli" to 7,
    "julai" to 7,
    "jul" to 7,
    "august" to 8,
    "agustus" to 8,
    "ogos" to 8,
    "agu" to 8,
    "aug" to 8,
    "september" to 9,
    "sep" to 9,
    "october" to 10,
    "oktober" to 10,
    "okt" to 10,
    "oct" to 10,
    "november" to 11,
    "nov" to 11,
    "december" to 12,
    "disember" to 12,
    "desember" to 12,
    "dis" to 12,
    "dec" to 12,
)

private fun PageTableColumn.withActionConfig(
    action: ChatAction,
    relationColumn: PageTableColumn? = null,
    resolvedRollupTargetColumnId: String = "",
): PageTableColumn {
    val optionConfig = action.options.toAiTableSelectOptions()
        .takeIf { options -> options.isNotEmpty() }
        ?.let { options -> config.copy(options = options).normalizedForType(type) }
        ?: config.normalizedForType(type)
    return copy(
        config = optionConfig,
        formula = action.effectiveFormula().ifBlank { formula },
        relationTargetTableId = action.relationTargetTableId.ifBlank { relationTargetTableId },
        rollupRelationColumnId = relationColumn?.id ?: action.rollupRelationColumnId.ifBlank { rollupRelationColumnId },
        rollupTargetColumnId = resolvedRollupTargetColumnId
            .ifBlank { action.rollupTargetColumnId }
            .ifBlank { rollupTargetColumnId },
        rollupAggregation = action.rollupAggregation
            .takeIf { value -> value.isNotBlank() }
            ?.toPageTableRollupAggregation()
            ?: rollupAggregation,
    )
}

private fun ChatAction.toTableViewConfig(table: PageTable): PageTableViewConfig {
    fun resolve(columnId: String, columnName: String): String {
        if (columnId.isNotBlank()) return columnId
        return table.findColumn(columnName = columnName)?.id.orEmpty()
    }
    val tableViewName = tableView.ifBlank { value }.ifBlank { content }.normalizedAiKey()
    val genericColumnId = resolve(columnId, columnName)
    return table.viewConfig.copy(
        calendarDateColumnId = resolve(calendarDateColumnId, calendarDateColumnName)
            .ifBlank { if (tableViewName == "calendar") genericColumnId else "" }
            .ifBlank { table.viewConfig.calendarDateColumnId },
        timelineStartColumnId = resolve(timelineStartColumnId, timelineStartColumnName)
            .ifBlank { if (tableViewName == "timeline") genericColumnId else "" }
            .ifBlank { table.viewConfig.timelineStartColumnId },
        timelineEndColumnId = resolve(timelineEndColumnId, timelineEndColumnName)
            .ifBlank { table.viewConfig.timelineEndColumnId },
        dashboardMetricColumnId = resolve(dashboardMetricColumnId, dashboardMetricColumnName)
            .ifBlank { if (tableViewName == "dashboard" || tableViewName == "chart") genericColumnId else "" }
            .ifBlank { table.viewConfig.dashboardMetricColumnId },
        dashboardGroupColumnId = resolve(dashboardGroupColumnId, dashboardGroupColumnName)
            .ifBlank { resolve(groupByColumnId, groupByColumnName) }
            .ifBlank { table.viewConfig.dashboardGroupColumnId },
    )
}

private fun ChatAction.requestedModuleType(): PageModuleType? {
    return PageModuleTemplates.fromActionFields(moduleType, type, title, tableTitle, content, blockType)
}

private fun String.toPageContentDocument(): String {
    return PageBlockCodec.encode(listOf(PageBlockCodec.newBlock(PageBlockType.Text).copy(text = trim())))
}

private fun String.toPageBlockType(): PageBlockType {
    return when (normalizedAiKey()) {
        "heading", "title", "h1" -> PageBlockType.Heading
        "todo", "task", "checkbox", "checklist" -> PageBlockType.Todo
        "bullet", "list", "bulletedlist" -> PageBlockType.Bullet
        "numbered", "number", "ordered", "orderedlist", "numberedlist", "ol" -> PageBlockType.Numbered
        "toggle", "togglelist", "collapse" -> PageBlockType.Toggle
        "quote" -> PageBlockType.Quote
        "callout", "notice", "info" -> PageBlockType.Callout
        "code", "snippet", "pre" -> PageBlockType.Code
        "table", "grid", "plaintable" -> PageBlockType.Table
        "bookmark", "webbookmark", "web", "urlpreview" -> PageBlockType.WebBookmark
        "divider", "line" -> PageBlockType.Divider
        "media", "file", "files", "image", "photo", "video", "attachment", "attachments", "mediafile" -> PageBlockType.MediaFile
        "database", "db", "databasetable" -> PageBlockType.DatabaseTable
        else -> PageBlockType.Text
    }
}

private fun String.toPageBlockTypeOrNull(): PageBlockType? {
    if (isBlank()) return null
    return when (normalizedAiKey()) {
        "text", "paragraph" -> PageBlockType.Text
        "heading", "title", "h1" -> PageBlockType.Heading
        "todo", "task", "checkbox", "checklist" -> PageBlockType.Todo
        "bullet", "list", "bulletedlist" -> PageBlockType.Bullet
        "numbered", "number", "ordered", "orderedlist", "numberedlist", "ol" -> PageBlockType.Numbered
        "toggle", "togglelist", "collapse" -> PageBlockType.Toggle
        "quote" -> PageBlockType.Quote
        "callout", "notice", "info" -> PageBlockType.Callout
        "code", "snippet", "pre" -> PageBlockType.Code
        "table", "grid", "plaintable" -> PageBlockType.Table
        "bookmark", "webbookmark", "web", "urlpreview" -> PageBlockType.WebBookmark
        "divider", "line" -> PageBlockType.Divider
        "media", "file", "files", "image", "photo", "video", "attachment", "attachments", "mediafile" -> PageBlockType.MediaFile
        "database", "db", "databasetable" -> PageBlockType.DatabaseTable
        else -> null
    }
}

private fun String.toPageTableColumnType(): PageTableColumnType {
    return when (normalizedAiKey()) {
        "number", "count", "amount", "price", "cost", "total" -> PageTableColumnType.Number
        "select", "option", "choice" -> PageTableColumnType.Select
        "multiselect", "multi select", "multi-select", "tags", "tag", "labels", "label" -> PageTableColumnType.MultiSelect
        "status", "stage", "state", "phase" -> PageTableColumnType.Status
        "date", "day", "deadline", "due", "time", "calendar" -> PageTableColumnType.Date
        "file", "files", "media", "attachment", "attachments", "image", "photo", "video", "filesmedia", "filemedia" -> PageTableColumnType.FilesMedia
        "checkbox", "check", "done", "complete", "completed", "boolean" -> PageTableColumnType.Checkbox
        "formula", "calculation", "calculate", "computed" -> PageTableColumnType.Formula
        "relation", "related", "link", "linkedrow", "linkedrows" -> PageTableColumnType.Relation
        "rollup", "aggregate", "aggregation" -> PageTableColumnType.Rollup
        else -> PageTableColumnType.Text
    }
}

private fun String.inferTableColumnType(): PageTableColumnType = toPageTableColumnType()

private fun String.toPageTableView(): PageTableView {
    return when (normalizedAiKey()) {
        "list" -> PageTableView.List
        "board", "kanban" -> PageTableView.Board
        "calendar" -> PageTableView.Calendar
        "gallery" -> PageTableView.Gallery
        "timeline" -> PageTableView.Timeline
        "dashboard", "chart", "charts" -> PageTableView.Dashboard
        else -> PageTableView.Table
    }
}

private fun String.toPageTableSortDirection(): PageTableSortDirection {
    return when (normalizedAiKey()) {
        "descending", "desc", "ztoa", "newest", "latest", "highest", "largest", "down" -> PageTableSortDirection.Descending
        else -> PageTableSortDirection.Ascending
    }
}

private fun String.toPageTableRollupAggregation(): PageTableRollupAggregation {
    return when (normalizedAiKey()) {
        "sum", "total" -> PageTableRollupAggregation.Sum
        "average", "avg", "mean" -> PageTableRollupAggregation.Average
        "min", "minimum", "lowest" -> PageTableRollupAggregation.Min
        "max", "maximum", "highest" -> PageTableRollupAggregation.Max
        else -> PageTableRollupAggregation.Count
    }
}

private fun String.toPagePropertyType(): PagePropertyType {
    return when (normalizedAiKey()) {
        "summarize", "summary" -> PagePropertyType.Summarize
        "translate", "translation" -> PagePropertyType.Translate
        "number" -> PagePropertyType.Number
        "select" -> PagePropertyType.Select
        "multiselect" -> PagePropertyType.MultiSelect
        "status" -> PagePropertyType.Status
        "date" -> PagePropertyType.Date
        "person", "people" -> PagePropertyType.Person
        "filesmedia", "filemedia", "filesandmedia", "attachment", "attachments" -> PagePropertyType.FilesMedia
        "checkbox", "check" -> PagePropertyType.Checkbox
        "url", "link" -> PagePropertyType.Url
        "email" -> PagePropertyType.Email
        "phone", "telephone" -> PagePropertyType.Phone
        "formula" -> PagePropertyType.Formula
        "relation" -> PagePropertyType.Relation
        "rollup" -> PagePropertyType.Rollup
        "createdtime" -> PagePropertyType.CreatedTime
        "createdby" -> PagePropertyType.CreatedBy
        "lasteditedtime" -> PagePropertyType.LastEditedTime
        "lasteditedby" -> PagePropertyType.LastEditedBy
        "button" -> PagePropertyType.Button
        "place", "location", "map" -> PagePropertyType.Place
        "id" -> PagePropertyType.Id
        else -> PagePropertyType.Text
    }
}

private fun String.normalizedActionType(): String = trim().uppercase()

private fun String.normalizedAiKey(): String {
    return trim().lowercase().replace(Regex("[^a-z0-9]"), "")
}

private fun Page.toChatPageLink(): AiChatPageLink {
    return AiChatPageLink(pageId = id, title = title.ifBlank { "Untitled page" })
}
