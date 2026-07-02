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
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.model.RichTextFormat
import com.changeyourlife.cyl.domain.model.RichTextSpanEngine
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.model.toAiUndoCommandSummary
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.domain.usecase.ApplyEditorCommandUseCase
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import com.changeyourlife.cyl.presentation.page.PageModuleTemplates
import com.changeyourlife.cyl.presentation.page.PageModuleType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class AiPageActionExecutor @Inject constructor(
    private val pageRepository: PageRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val applyEditorCommandUseCase: ApplyEditorCommandUseCase,
) {
    fun supports(action: ChatAction): Boolean {
        return action.type.normalizedActionType() in supportedActions
    }

    suspend fun executeOnPage(
        page: Page,
        title: String,
        document: PageBlockDocument,
        actions: List<ChatAction>,
    ): AiPageActionExecutionResult {
        var workingTitle = title.ifBlank { page.title }
        var workingDocument = document
        var titleChanged = false
        var documentChanged = false
        var directPageChanged = false
        val messages = mutableListOf<String>()
        val validationIssues = mutableListOf<AiPageActionValidationIssue>()
        val createdPages = mutableListOf<Page>()
        val createdTasks = mutableListOf<TaskItem>()
        val createdReminders = mutableListOf<Reminder>()
        val undoCommands = mutableListOf<AiUndoCommandSummary>()

        for ((actionIndex, action) in actions.withIndex()) {
            val actionType = action.type.normalizedActionType()
            val validationIssue = workingDocument.validateActionTarget(action, actionIndex)
            if (validationIssue != null) {
                val issue = validationIssue
                validationIssues += issue
                messages += "Rejected ${action.type}: ${issue.message}"
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
                            workingDocument = PageBlockDocument(
                                properties = workingDocument.properties,
                                blocks = listOf(PageBlockCodec.newBlock(PageBlockType.Text).copy(text = action.content)),
                            )
                            documentChanged = true
                        }
                        "Updated page"
                    }

                    "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> {
                        val block = action.toPageBlock()
                        val updatedDirectly = !documentChanged && pageRepository.addBlock(
                            pageId = page.id,
                            block = block,
                            targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                        )
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                        } else {
                            workingDocument = workingDocument.applyAiEditorCommand(
                                EditorCommand.InsertBlock(
                                    block = block,
                                    index = action.targetIndex?.toAiZeroBasedIndex(),
                                ),
                                actionIndex = actionIndex,
                                undoCommands = undoCommands,
                            )
                            documentChanged = true
                        }
                        "Added ${block.type.name} block"
                    }

                    "ADD_PROPERTY", "UPDATE_PROPERTY" -> {
                        val propertyName = action.propertyName
                            .ifBlank { action.title }
                            .ifBlank { error("Missing property name") }
                        val value = action.value.ifBlank { action.content }
                        val updatedDirectly = if (!documentChanged && actionType == "UPDATE_PROPERTY" && value.isNotBlank()) {
                            pageRepository.updatePropertyValue(
                                pageId = page.id,
                                propertyName = propertyName,
                                value = value,
                            )
                        } else if (!documentChanged && actionType == "ADD_PROPERTY") {
                            pageRepository.addProperty(
                                pageId = page.id,
                                property = PageBlockCodec.newProperty(
                                    type = action.propertyType.toPagePropertyType(),
                                    name = propertyName,
                                ).copy(value = value),
                                targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                            )
                        } else {
                            false
                        }
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                        } else {
                            workingDocument = workingDocument.upsertProperty(
                                name = propertyName,
                                type = action.propertyType.toPagePropertyType(),
                                value = value,
                            )
                            documentChanged = true
                        }
                        "Updated property: $propertyName"
                    }

                    "DELETE_PROPERTY" -> {
                        val propertyName = action.propertyName
                            .ifBlank { action.title }
                            .ifBlank { error("Missing property name") }
                        val propertyId = workingDocument.properties.firstOrNull { property ->
                            property.name.normalizedAiKey() == propertyName.normalizedAiKey()
                        }?.id.orEmpty()
                        val updatedDirectly = !documentChanged &&
                            propertyId.isNotBlank() &&
                            pageRepository.deleteProperty(page.id, propertyId)
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                        } else {
                            workingDocument = workingDocument.deletePropertyByName(propertyName)
                            documentChanged = true
                        }
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
                        val updatedDirectly = !documentChanged &&
                            action.blockId.isNotBlank() &&
                            pageRepository.deleteBlock(page.id, action.blockId)
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                            "Deleted block"
                        } else {
                            val deleteResult = workingDocument.deleteMatchingBlock(action, actionIndex, undoCommands)
                            workingDocument = deleteResult.document
                            documentChanged = true
                            "Deleted block: ${deleteResult.label}"
                        }
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
                        val nextText = action.content.ifBlank { action.value }
                        val canDirectUpdate = actionType in setOf("UPDATE_BLOCK", "EDIT_BLOCK") &&
                            !documentChanged &&
                            action.blockId.isNotBlank() &&
                            action.blockType.isBlank() &&
                            nextText.isNotBlank()
                        if (canDirectUpdate) {
                            val updated = pageRepository.updateBlockText(
                                pageId = page.id,
                                blockId = action.blockId,
                                text = nextText,
                            )
                            if (!updated) error("Could not find block to update")
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                            "Updated block"
                        } else {
                            val updateResult = workingDocument.updateMatchingBlock(action, actionIndex, undoCommands)
                            workingDocument = updateResult.document
                            documentChanged = true
                            "Updated block: ${updateResult.label}"
                        }
                    }

                    "CREATE_DATABASE", "CREATE_TABLE" -> {
                        val tableBlock = action.toDatabaseBlock()
                        val updatedDirectly = !documentChanged && pageRepository.addBlock(
                            pageId = page.id,
                            block = tableBlock,
                            targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                        )
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                        } else {
                            workingDocument = workingDocument.applyAiEditorCommand(
                                EditorCommand.InsertBlock(
                                    block = tableBlock,
                                    index = action.targetIndex?.toAiZeroBasedIndex(),
                                ),
                                actionIndex = actionIndex,
                                undoCommands = undoCommands,
                            )
                            documentChanged = true
                        }
                        "Created database: ${tableBlock.table.title}"
                    }

                    "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" -> {
                        val newTitle = action.title
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { action.newColumnName }
                            .ifBlank { error("Missing new table title") }
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.copy(table = block.table.copy(title = newTitle))
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Renamed ${update.tableTitle} to $newTitle"
                    }

                    "ADD_TABLE_COLUMN" -> {
                        val resolvedAction = action.withResolvedRelationTarget(workingDocument)
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                            .ifBlank { error("Missing column name") }
                        val columnType = action.columnType.ifBlank { action.propertyType }.toPageTableColumnType()
                        val targetTable = workingDocument.blocks.findMatchingTable(resolvedAction)
                        val directColumn = targetTable?.let { block ->
                            val column = PageBlockCodec.newTableColumn(columnName, columnType)
                                .withActionConfig(
                                    action = resolvedAction,
                                    resolvedRollupTargetColumnId = block.resolveRollupTargetColumnId(
                                        action = resolvedAction,
                                        relationColumn = block.table.findColumn(
                                            columnId = resolvedAction.rollupRelationColumnId,
                                            columnName = resolvedAction.rollupRelationColumnName,
                                        ),
                                        document = workingDocument,
                                    ),
                                )
                            column
                        }
                        val updatedDirectly = !documentChanged &&
                            targetTable != null &&
                            directColumn != null &&
                            pageRepository.addTableColumn(
                                pageId = page.id,
                                tableBlockId = targetTable.id,
                                column = directColumn,
                                targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                            )
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                            "Added column $columnName to ${targetTable.table.title}"
                        } else {
                            val update = workingDocument.updateMatchingTable(resolvedAction, actionIndex, undoCommands) { block ->
                                val column = PageBlockCodec.newTableColumn(columnName, columnType)
                                    .withActionConfig(
                                        action = resolvedAction,
                                        resolvedRollupTargetColumnId = block.resolveRollupTargetColumnId(
                                            action = resolvedAction,
                                            relationColumn = block.table.findColumn(
                                                columnId = resolvedAction.rollupRelationColumnId,
                                                columnName = resolvedAction.rollupRelationColumnName,
                                            ),
                                            document = workingDocument,
                                        ),
                                    )
                                block.copy(
                                    table = block.table.copy(
                                        columns = block.table.columns + column,
                                        rows = block.table.rows.map { row ->
                                            row.copy(cells = row.cells + (column.id to ""))
                                        },
                                    ),
                                )
                            }
                            workingDocument = update.document
                            documentChanged = true
                            "Added column $columnName to ${update.tableTitle}"
                        }
                    }

                    "DELETE_TABLE_COLUMN" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                        val targetColumn = targetTable?.table?.findColumn(action.columnId, columnName)
                        val updatedDirectly = !documentChanged &&
                            targetTable != null &&
                            targetColumn != null &&
                            pageRepository.deleteTableColumn(
                                pageId = page.id,
                                tableBlockId = targetTable.id,
                                columnId = targetColumn.id,
                            )
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                            "Deleted column ${targetColumn.name} from ${targetTable.table.title}"
                        } else {
                            val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                                block.deleteTableColumn(action.columnId, columnName)
                            }
                            workingDocument = update.document
                            documentChanged = true
                            "Deleted column ${columnName.ifBlank { action.columnId }} from ${update.tableTitle}"
                        }
                    }

                    "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val newColumnName = action.newColumnName
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { error("Missing new column name") }
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.renameTableColumn(action.columnId, columnName, newColumnName)
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Renamed column to $newColumnName in ${update.tableTitle}"
                    }

                    "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE" -> {
                        val resolvedAction = action.withResolvedRelationTarget(workingDocument)
                        val columnName = resolvedAction.columnName.ifBlank { resolvedAction.propertyName }.ifBlank { resolvedAction.title }
                        val columnType = resolvedAction.columnType
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { error("Missing column type") }
                            .toPageTableColumnType()
                        val update = workingDocument.updateMatchingTable(resolvedAction, actionIndex, undoCommands) { block ->
                            block.updateTableColumnType(resolvedAction.columnId, columnName, columnType, resolvedAction, workingDocument)
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Changed column ${columnName.ifBlank { action.columnId }} to ${columnType.name} in ${update.tableTitle}"
                    }

                    "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG",
                    "UPDATE_FORMULA_COLUMN", "UPDATE_RELATION_COLUMN", "UPDATE_ROLLUP_COLUMN" -> {
                        val resolvedAction = action.withResolvedRelationTarget(workingDocument)
                        val columnName = resolvedAction.columnName
                            .ifBlank { resolvedAction.propertyName }
                            .ifBlank { resolvedAction.title }
                        val update = workingDocument.updateMatchingTable(resolvedAction, actionIndex, undoCommands) { block ->
                            block.configureTableColumn(
                                columnId = resolvedAction.columnId,
                                columnName = columnName,
                                action = resolvedAction,
                                document = workingDocument,
                            )
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Updated column configuration in ${update.tableTitle}"
                    }

                    "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val targetIndex = action.targetIndex ?: error("Missing target index")
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                        val targetColumn = targetTable?.table?.findColumn(action.columnId, columnName)
                        val updatedDirectly = !documentChanged &&
                            targetTable != null &&
                            targetColumn != null &&
                            pageRepository.moveTableColumn(
                                pageId = page.id,
                                tableBlockId = targetTable.id,
                                columnId = targetColumn.id,
                                targetIndex = targetIndex.toAiZeroBasedIndex(),
                            )
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                            "Moved column in ${targetTable.table.title}"
                        } else {
                            val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                                block.reorderTableColumn(
                                    columnId = action.columnId,
                                    columnName = columnName,
                                    targetIndex = targetIndex,
                                )
                            }
                            workingDocument = update.document
                            documentChanged = true
                            "Moved column in ${update.tableTitle}"
                        }
                    }

                    "ADD_TABLE_ROW" -> {
                        if (action.isTaskTableRowAction()) {
                            val mutation = workingDocument.withTaskTableAction(action)
                            workingDocument = mutation.document
                            documentChanged = true
                            "Added task row ${mutation.rowTitle} to ${mutation.tableTitle}"
                        } else {
                            val targetTable = workingDocument.blocks.findMatchingTable(action)
                            val row = targetTable?.table?.newRowFromAction(action)
                            val updatedDirectly = !documentChanged &&
                                targetTable != null &&
                                row != null &&
                                runCatching {
                                    pageRepository.addTableRow(
                                        pageId = page.id,
                                        tableBlockId = targetTable.id,
                                        row = row,
                                        targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                                    )
                                }.getOrDefault(false)
                            if (updatedDirectly) {
                                refreshPageDocument(page.id)?.let { refreshedDocument ->
                                    workingDocument = refreshedDocument
                                }
                                directPageChanged = true
                                "Added row to ${targetTable.table.title}"
                            } else {
                                val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                                    block.copy(table = block.table.copy(rows = block.table.rows + block.table.newRowFromAction(action)))
                                }
                                workingDocument = update.document
                                documentChanged = true
                                "Added row to ${update.tableTitle}"
                            }
                        }
                    }

                    "DELETE_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                        val targetRow = targetTable?.table?.findRow(action.rowId, rowTitle)
                        val updatedDirectly = !documentChanged &&
                            targetTable != null &&
                            targetRow != null &&
                            pageRepository.deleteTableRow(
                                pageId = page.id,
                                tableBlockId = targetTable.id,
                                rowId = targetRow.id,
                            )
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                            "Deleted row ${rowTitle.ifBlank { targetRow.id }} from ${targetTable.table.title}"
                        } else {
                            val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                                block.deleteTableRow(action.rowId, rowTitle)
                            }
                            workingDocument = update.document
                            documentChanged = true
                            "Deleted row ${rowTitle.ifBlank { action.rowId }} from ${update.tableTitle}"
                        }
                    }

                    "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val newRowTitle = action.newRowTitle.ifBlank { action.value }.ifBlank { action.content }
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.updateTableRow(action.rowId, rowTitle, newRowTitle, action.cellValues)
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Updated row ${newRowTitle.ifBlank { rowTitle.ifBlank { action.rowId } }} in ${update.tableTitle}"
                    }

                    "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val targetIndex = action.targetIndex ?: error("Missing target index")
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                        val targetRow = targetTable?.table?.findRow(action.rowId, rowTitle)
                        val updatedDirectly = !documentChanged &&
                            targetTable != null &&
                            targetRow != null &&
                            pageRepository.moveTableRow(
                                pageId = page.id,
                                tableBlockId = targetTable.id,
                                rowId = targetRow.id,
                                targetIndex = targetIndex.toAiZeroBasedIndex(),
                            )
                        if (updatedDirectly) {
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                            "Moved row in ${targetTable.table.title}"
                        } else {
                            val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                                block.reorderTableRow(
                                    rowId = action.rowId,
                                    rowTitle = rowTitle,
                                    targetIndex = targetIndex,
                                )
                            }
                            workingDocument = update.document
                            documentChanged = true
                            "Moved row in ${update.tableTitle}"
                        }
                    }

                    "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.targetTitle }.ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) error("Missing row target")
                        val rowBlock = action.toPageBlock()
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.addRowPageBlock(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                                rowBlock = rowBlock,
                            )
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Added ${rowBlock.type.name} block inside ${rowTitle.ifBlank { action.rowId }} in ${update.tableTitle}"
                    }

                    "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK",
                    "CHECK_ROW_PAGE_BLOCK", "UNCHECK_ROW_PAGE_BLOCK" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.targetTitle }.ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) error("Missing row target")
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.updateRowPageBlock(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                                rowBlockId = action.rowBlockId,
                                action = action,
                            )
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Updated row content in ${rowTitle.ifBlank { action.rowId }} in ${update.tableTitle}"
                    }

                    "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.targetTitle }.ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) error("Missing row target")
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.deleteRowPageBlock(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                                rowBlockId = action.rowBlockId,
                                action = action,
                            )
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Deleted row content from ${rowTitle.ifBlank { action.rowId }} in ${update.tableTitle}"
                    }

                    "UPDATE_TABLE_CELL" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val value = action.value.ifBlank { action.content }
                        if (!documentChanged && action.rowId.isNotBlank() && action.columnId.isNotBlank()) {
                            val updated = pageRepository.updateTableCellValue(
                                pageId = page.id,
                                rowId = action.rowId,
                                columnId = action.columnId,
                                value = value,
                            )
                            if (!updated) error("Could not find table cell")
                            refreshPageDocument(page.id)?.let { refreshedDocument ->
                                workingDocument = refreshedDocument
                            }
                            directPageChanged = true
                            "Updated ${columnName.ifBlank { action.columnId }} for ${rowTitle.ifBlank { action.rowId }}"
                        } else {
                            val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                                block.updateCellByNames(
                                    rowId = action.rowId,
                                    rowTitle = rowTitle,
                                    columnId = action.columnId,
                                    columnName = columnName,
                                    value = value,
                                )
                            }
                            workingDocument = update.document
                            documentChanged = true
                            "Updated ${columnName.ifBlank { action.columnId }} for ${rowTitle.ifBlank { action.rowId }} in ${update.tableTitle}"
                        }
                    }

                    "CHANGE_TABLE_VIEW", "SET_TABLE_VIEW" -> {
                        val view = action.tableView.ifBlank { action.value }.ifBlank { action.content }.toPageTableView()
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.copy(table = block.table.copy(view = view))
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Changed ${update.tableTitle} view to ${view.name}"
                    }

                    "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG" -> {
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.copy(table = block.table.copy(viewConfig = action.toTableViewConfig(block.table)))
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Updated ${update.tableTitle} view config"
                    }

                    "SORT_TABLE", "SET_TABLE_SORT" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val direction = action.sortDirection.ifBlank { action.value }.ifBlank { action.content }.toPageTableSortDirection()
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.sortTable(action.columnId, columnName, direction)
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Sorted ${update.tableTitle} by ${columnName.ifBlank { action.columnId }} ${direction.name.lowercase()}"
                    }

                    "CLEAR_TABLE_SORT" -> {
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.copy(table = block.table.copy(sort = PageTableSort()))
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Cleared sort in ${update.tableTitle}"
                    }

                    "FILTER_TABLE", "SET_TABLE_FILTER" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val query = action.filterQuery.ifBlank { action.value }.ifBlank { action.content }.ifBlank { error("Missing filter query") }
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.filterTable(action.columnId, columnName, query)
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Filtered ${update.tableTitle} by ${columnName.ifBlank { action.columnId }}"
                    }

                    "CLEAR_TABLE_FILTER" -> {
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.copy(table = block.table.copy(filter = PageTableFilter()))
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Cleared filter in ${update.tableTitle}"
                    }

                    "GROUP_TABLE", "SET_TABLE_GROUP" -> {
                        val columnId = action.groupByColumnId.ifBlank { action.columnId }
                        val columnName = action.groupByColumnName.ifBlank { action.columnName }.ifBlank { action.propertyName }.ifBlank { action.title }
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.groupTable(columnId, columnName)
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Grouped ${update.tableTitle} by ${columnName.ifBlank { columnId }}"
                    }

                    "CLEAR_TABLE_GROUP" -> {
                        val update = workingDocument.updateMatchingTable(action, actionIndex, undoCommands) { block ->
                            block.copy(table = block.table.copy(groupByColumnId = ""))
                        }
                        workingDocument = update.document
                        documentChanged = true
                        "Cleared group in ${update.tableTitle}"
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
                        val mutation = workingDocument.withTaskTableAction(action)
                        workingDocument = mutation.document
                        documentChanged = true
                        "Added task row ${mutation.rowTitle} to ${mutation.tableTitle}"
                    }

                    "CREATE_REMINDER" -> {
                        val mutation = workingDocument.withTaskTableAction(action)
                        workingDocument = mutation.document
                        documentChanged = true
                        "Added reminder row ${mutation.rowTitle} to ${mutation.tableTitle}"
                    }

                    else -> error("Unsupported action type: ${action.type}")
                }
            }.onSuccess { message ->
                messages += "Done: $message"
            }.onFailure { error ->
                messages += "Failed ${action.type}: ${error.localizedMessage ?: "Unknown error"}"
            }
        }

        val pageLinks = buildList {
            if (titleChanged || documentChanged || directPageChanged) {
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
        )
    }

    private fun PageBlockDocument.applyAiEditorCommand(
        command: EditorCommand,
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
    ): PageBlockDocument {
        val applied = applyEditorCommandUseCase(this, command)
        if (applied.changed) {
            applied.result.undoCommand
                ?.toAiUndoCommandSummary(actionIndex)
                ?.let(undoCommands::add)
        }
        return applied.document
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

    private fun PageBlockDocument.updateMatchingTable(
        action: ChatAction,
        actionIndex: Int,
        undoCommands: MutableList<AiUndoCommandSummary>,
        transform: (PageBlock) -> PageBlock,
    ): DocumentTableUpdateResult {
        val target = blocks.findMatchingTable(action)
            ?: error("Could not find table: ${action.tableTitle.ifBlank { action.blockId.ifBlank { "first table" } }}")
        val updatedBlock = transform(target)
        val updatedDocument = if (target.table == updatedBlock.table) {
            this
        } else {
            applyAiEditorCommand(
                EditorCommand.ReplaceTable(
                    blockId = target.id,
                    table = updatedBlock.table,
                ),
                actionIndex = actionIndex,
                undoCommands = undoCommands,
            )
        }
        return DocumentTableUpdateResult(
            document = updatedDocument,
            tableTitle = updatedBlock.table.title,
        )
    }

    private suspend fun refreshPageDocument(pageId: String): PageBlockDocument? {
        return pageRepository.getPage(pageId)?.let { refreshedPage ->
            PageBlockCodec.decodeDocument(refreshedPage.content)
        }
    }

    companion object {
        private val supportedActions = setOf(
            "RENAME_CURRENT_PAGE",
            "RENAME_PAGE",
            "UPDATE_PAGE",
            "APPEND_BLOCK",
            "APPEND_PAGE_BLOCK",
            "ADD_BLOCK",
            "ADD_PROPERTY",
            "UPDATE_PROPERTY",
            "DELETE_PROPERTY",
            "DELETE_ALL_BLOCKS",
            "DELETE_BLOCK",
            "FORMAT_BLOCK_TEXT",
            "UPDATE_BLOCK",
            "EDIT_BLOCK",
            "UPDATE_TODO",
            "CHECK_BLOCK",
            "UNCHECK_BLOCK",
            "CREATE_DATABASE",
            "CREATE_TABLE",
            "RENAME_TABLE",
            "RENAME_DATABASE",
            "UPDATE_TABLE_TITLE",
            "ADD_TABLE_COLUMN",
            "DELETE_TABLE_COLUMN",
            "RENAME_TABLE_COLUMN",
            "UPDATE_TABLE_COLUMN",
            "UPDATE_TABLE_COLUMN_TYPE",
            "CHANGE_TABLE_COLUMN_TYPE",
            "SET_TABLE_COLUMN_TYPE",
            "UPDATE_TABLE_COLUMN_CONFIG",
            "SET_TABLE_COLUMN_CONFIG",
            "UPDATE_FORMULA_COLUMN",
            "UPDATE_RELATION_COLUMN",
            "UPDATE_ROLLUP_COLUMN",
            "REORDER_TABLE_COLUMN",
            "MOVE_TABLE_COLUMN",
            "ADD_TABLE_ROW",
            "DELETE_TABLE_ROW",
            "UPDATE_TABLE_ROW",
            "RENAME_TABLE_ROW",
            "REORDER_TABLE_ROW",
            "MOVE_TABLE_ROW",
            "ADD_ROW_PAGE_BLOCK",
            "APPEND_ROW_PAGE_BLOCK",
            "ADD_TABLE_ROW_BLOCK",
            "UPDATE_ROW_PAGE_BLOCK",
            "EDIT_ROW_PAGE_BLOCK",
            "UPDATE_TABLE_ROW_BLOCK",
            "CHECK_ROW_PAGE_BLOCK",
            "UNCHECK_ROW_PAGE_BLOCK",
            "DELETE_ROW_PAGE_BLOCK",
            "DELETE_TABLE_ROW_BLOCK",
            "UPDATE_TABLE_CELL",
            "CHANGE_TABLE_VIEW",
            "SET_TABLE_VIEW",
            "SET_TABLE_VIEW_CONFIG",
            "CONFIGURE_TABLE_VIEW",
            "UPDATE_TABLE_VIEW_CONFIG",
            "SORT_TABLE",
            "SET_TABLE_SORT",
            "CLEAR_TABLE_SORT",
            "FILTER_TABLE",
            "SET_TABLE_FILTER",
            "CLEAR_TABLE_FILTER",
            "GROUP_TABLE",
            "SET_TABLE_GROUP",
            "CLEAR_TABLE_GROUP",
            "CREATE_SUBPAGE",
            "CREATE_PAGE",
            "CREATE_TASK",
            "CREATE_REMINDER",
        )
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
)

data class AiPageActionValidationIssue(
    val actionIndex: Int? = null,
    val field: String = "",
    val code: String = "",
    val message: String = "",
)

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
        val rowTitle = action.rowTitle.ifBlank { action.targetTitle }.ifBlank { action.title }
        return if (table.table.findRow(action.rowId, rowTitle) == null) {
            targetNotFound(
                field = "rowTitle",
                targetKind = "row",
                targetLabel = rowTitle,
            )
        } else {
            null
        }
    }

    fun rowPageBlockIssue(table: PageBlock): AiPageActionValidationIssue? {
        val rowTitle = action.rowTitle.ifBlank { action.targetTitle }.ifBlank { action.title }
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

        "UPDATE_TABLE_CELL" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table) ?: columnIssue(table) ?: tableDateCellIssue(table)
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

private data class DocumentTableUpdateResult(
    val document: PageBlockDocument,
    val tableTitle: String,
)

private data class DocumentBlockMutationResult(
    val document: PageBlockDocument,
    val label: String,
)

private data class BlockMutationResult(
    val blocks: List<PageBlock>,
    val label: String,
    val changed: Boolean,
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
        else -> listOf(emptyMap())
    }
    return rowMaps.map { values -> columns.newRow(values) }
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
            if (block.id == tableBlockId && block.type == PageBlockType.DatabaseTable) return block
            walk(block.children)?.let { return it }
        }
        return null
    }
    return walk(blocks)
}

private fun PageBlockDocument.updateMatchingTable(
    action: ChatAction,
    transform: (PageBlock) -> PageBlock,
): DocumentTableUpdateResult {
    val target = blocks.findMatchingTable(action)
        ?: error("Could not find table: ${action.tableTitle.ifBlank { action.blockId.ifBlank { "first table" } }}")
    val updatedBlocks = blocks.map { block ->
        if (block.id == target.id) transform(block) else block
    }
    val updatedTarget = updatedBlocks.firstOrNull { block -> block.id == target.id } ?: target
    return DocumentTableUpdateResult(
        document = copy(blocks = updatedBlocks),
        tableTitle = updatedTarget.table.title,
    )
}

private fun List<PageBlock>.findMatchingTable(action: ChatAction): PageBlock? {
    if (action.blockId.isNotBlank()) {
        firstOrNull { block -> block.id == action.blockId && block.type == PageBlockType.DatabaseTable }?.let { return it }
    }
    val tableName = action.tableTitle.ifBlank { action.title }
    if (tableName.isNotBlank()) {
        firstOrNull { block ->
            block.type == PageBlockType.DatabaseTable && block.table.title.equals(tableName, ignoreCase = true)
        }?.let { return it }
        firstOrNull { block ->
            block.type == PageBlockType.DatabaseTable && block.table.title.contains(tableName, ignoreCase = true)
        }?.let { return it }
    }
    return filter { block -> block.type == PageBlockType.DatabaseTable }.singleOrNull()
        ?: firstOrNull { block -> block.type == PageBlockType.DatabaseTable }
}

private fun PageBlockDocument.upsertProperty(
    name: String,
    type: PagePropertyType,
    value: String,
): PageBlockDocument {
    val existing = properties.firstOrNull { property -> property.name.equals(name, ignoreCase = true) }
    return if (existing == null) {
        copy(properties = properties + PageBlockCodec.newProperty(type, name).copy(value = value))
    } else {
        copy(
            properties = properties.map { property ->
                if (property.id == existing.id) {
                    property.copy(type = type, value = value.ifBlank { property.value })
                } else {
                    property
                }
            },
        )
    }
}

private fun PageBlockDocument.deletePropertyByName(propertyName: String): PageBlockDocument {
    val normalized = propertyName.normalizedAiKey()
    val updatedProperties = properties.filterNot { property -> property.name.normalizedAiKey() == normalized }
    if (updatedProperties.size == properties.size) error("Could not find property: $propertyName")
    return copy(properties = updatedProperties)
}

private fun PageBlockDocument.deleteMatchingBlock(action: ChatAction): DocumentBlockMutationResult {
    val result = blocks.deleteMatchingBlock(action)
    if (!result.changed) error("Could not find block to delete")
    return DocumentBlockMutationResult(document = copy(blocks = result.blocks), label = result.label)
}

private fun PageBlockDocument.updateMatchingBlock(action: ChatAction): DocumentBlockMutationResult {
    val result = blocks.updateMatchingBlock(action)
    if (!result.changed) error("Could not find block to update")
    return DocumentBlockMutationResult(document = copy(blocks = result.blocks), label = result.label)
}

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

private fun List<PageBlock>.deleteMatchingBlock(action: ChatAction): BlockMutationResult {
    val updated = mutableListOf<PageBlock>()
    for (block in this) {
        if (block.matchesBlockAction(action)) {
            return BlockMutationResult(updated + drop(indexOf(block) + 1), block.blockLabel(), true)
        }
        val childResult = block.children.deleteMatchingBlock(action)
        if (childResult.changed) {
            updated += block.copy(children = childResult.blocks)
            updated += drop(indexOf(block) + 1)
            return BlockMutationResult(updated, childResult.label, true)
        }
        updated += block
    }
    return BlockMutationResult(this, "", false)
}

private fun List<PageBlock>.updateMatchingBlock(action: ChatAction): BlockMutationResult {
    return mapIndexed { index, block ->
        if (block.matchesBlockAction(action)) {
            val updated = block.withActionUpdate(action)
            return BlockMutationResult(
                blocks = take(index) + updated + drop(index + 1),
                label = block.blockLabel(),
                changed = true,
            )
        }
        val childResult = block.children.updateMatchingBlock(action)
        if (childResult.changed) {
            return BlockMutationResult(
                blocks = take(index) + block.copy(children = childResult.blocks) + drop(index + 1),
                label = childResult.label,
                changed = true,
            )
        }
        block
    }.let { BlockMutationResult(it, "", false) }
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
    return if (targetType == PageBlockType.DatabaseTable) {
        copy(
            type = targetType,
            table = table.copy(title = nextText.ifBlank { table.title }),
            isChecked = action.isChecked ?: isChecked,
        )
    } else {
        copy(
            type = targetType,
            text = nextText,
            isChecked = action.isChecked ?: isChecked,
        )
    }
}

private fun PageBlock.blockLabel(): String {
    return if (type == PageBlockType.DatabaseTable) table.title.ifBlank { "database table" } else text.ifBlank { type.name }
}

private fun List<PageBlock>.countNestedBlocks(): Int {
    return sumOf { block -> 1 + block.children.countNestedBlocks() }
}

private fun PageBlock.deleteTableColumn(columnId: String, columnName: String): PageBlock {
    val column = table.findColumn(columnId = columnId, columnName = columnName)
        ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
    return copy(
        table = table.copy(
            columns = table.columns.filterNot { existing -> existing.id == column.id },
            rows = table.rows.map { row -> row.copy(cells = row.cells - column.id) },
        ),
    )
}

private fun PageBlock.renameTableColumn(columnId: String, columnName: String, newColumnName: String): PageBlock {
    val column = table.findColumn(columnId = columnId, columnName = columnName)
        ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
    return copy(
        table = table.copy(
            columns = table.columns.map { existing ->
                if (existing.id == column.id) existing.copy(name = newColumnName) else existing
            },
        ),
    )
}

private fun PageBlock.reorderTableColumn(
    columnId: String,
    columnName: String,
    targetIndex: Int,
): PageBlock {
    val column = table.findColumn(columnId = columnId, columnName = columnName)
        ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
    return copy(
        table = table.copy(
            columns = table.columns.moveItem(
                itemId = column.id,
                targetIndex = targetIndex,
                idSelector = PageTableColumn::id,
            ),
        ),
    )
}

private fun PageBlock.updateTableColumnType(
    columnId: String,
    columnName: String,
    columnType: PageTableColumnType,
    action: ChatAction,
    document: PageBlockDocument,
): PageBlock {
    val column = table.findColumn(columnId = columnId, columnName = columnName)
        ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
    val relationColumn = table.findColumn(
        columnId = action.rollupRelationColumnId,
        columnName = action.rollupRelationColumnName,
    )
    val rollupTargetColumnId = resolveRollupTargetColumnId(
        action = action,
        relationColumn = relationColumn,
        document = document,
    )
    return copy(
        table = table.copy(
            columns = table.columns.map { existing ->
                if (existing.id == column.id) {
                    existing.copy(type = columnType)
                        .withActionConfig(
                            action = action,
                            relationColumn = relationColumn,
                            resolvedRollupTargetColumnId = rollupTargetColumnId,
                        )
                } else {
                    existing
                }
            },
        ),
    )
}

private fun PageBlock.configureTableColumn(
    columnId: String,
    columnName: String,
    action: ChatAction,
    document: PageBlockDocument,
): PageBlock {
    val column = table.findColumn(columnId = columnId, columnName = columnName)
        ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
    val relationColumn = table.findColumn(
        columnId = action.rollupRelationColumnId,
        columnName = action.rollupRelationColumnName,
    )
    val rollupTargetColumnId = resolveRollupTargetColumnId(
        action = action,
        relationColumn = relationColumn,
        document = document,
    )
    return copy(
        table = table.copy(
            columns = table.columns.map { existing ->
                if (existing.id == column.id) {
                    existing.withActionConfig(
                        action = action,
                        relationColumn = relationColumn,
                        resolvedRollupTargetColumnId = rollupTargetColumnId,
                    )
                } else {
                    existing
                }
            },
        ),
    )
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

private fun PageBlock.updateTableRow(
    rowId: String,
    rowTitle: String,
    newRowTitle: String,
    cellValues: Map<String, String>,
): PageBlock {
    val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
        ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
    val titleColumn = table.columns.firstOrNull()
    val resolvedCells = cellValues.toMutableMap()
    if (newRowTitle.isNotBlank() && titleColumn != null) resolvedCells[titleColumn.name] = newRowTitle
    return copy(
        table = table.copy(
            rows = table.rows.map { existing ->
                if (existing.id == row.id) {
                    existing.copy(
                        cells = existing.cells + table.columns.newRow(resolvedCells).cells.filterValues { value -> value.isNotBlank() },
                    )
                } else {
                    existing
                }
            },
        ),
    )
}

private fun PageBlock.reorderTableRow(
    rowId: String,
    rowTitle: String,
    targetIndex: Int,
): PageBlock {
    val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
        ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
    return copy(
        table = table.copy(
            rows = table.rows.moveItem(
                itemId = row.id,
                targetIndex = targetIndex,
                idSelector = PageTableRow::id,
            ),
        ),
    )
}

private fun PageBlock.updateCellByNames(
    rowId: String,
    rowTitle: String,
    columnId: String,
    columnName: String,
    value: String,
): PageBlock {
    val column = table.findColumn(columnId = columnId, columnName = columnName)
        ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
    val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
        ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
    return copy(
        table = table.copy(
            rows = table.rows.map { existing ->
                if (existing.id == row.id) existing.copy(cells = existing.cells + (column.id to value)) else existing
            },
        ),
    )
}

private fun PageBlock.addRowPageBlock(
    rowId: String,
    rowTitle: String,
    rowBlock: PageBlock,
): PageBlock {
    val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
        ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
    return copy(
        table = table.copy(
            rows = table.rows.map { existing ->
                if (existing.id == row.id) {
                    existing.copy(blocks = existing.blocks + rowBlock)
                } else {
                    existing
                }
            },
        ),
    )
}

private fun PageBlock.updateRowPageBlock(
    rowId: String,
    rowTitle: String,
    rowBlockId: String,
    action: ChatAction,
): PageBlock {
    val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
        ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
    if (row.blocks.isEmpty()) error("No row content found in ${rowTitle.ifBlank { rowId }}")
    val effectiveAction = when (action.type.normalizedActionType()) {
        "CHECK_ROW_PAGE_BLOCK" -> action.copy(isChecked = true)
        "UNCHECK_ROW_PAGE_BLOCK" -> action.copy(isChecked = false)
        else -> action
    }
    val update = if (rowBlockId.isNotBlank()) {
        row.blocks.updateMatchingBlock(effectiveAction.copy(blockId = rowBlockId))
    } else {
        row.blocks.updateMatchingBlock(effectiveAction)
    }
    if (!update.changed) {
        val label = rowBlockId
            .ifBlank { action.blockText }
            .ifBlank { action.content }
            .ifBlank { action.title }
            .ifBlank { action.blockType }
        error("Could not find row content block: $label")
    }
    return copy(
        table = table.copy(
            rows = table.rows.map { existing ->
                if (existing.id == row.id) existing.copy(blocks = update.blocks) else existing
            },
        ),
    )
}

private fun PageBlock.deleteRowPageBlock(
    rowId: String,
    rowTitle: String,
    rowBlockId: String,
    action: ChatAction,
): PageBlock {
    val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
        ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
    if (row.blocks.isEmpty()) error("No row content found in ${rowTitle.ifBlank { rowId }}")
    val delete = if (rowBlockId.isNotBlank()) {
        row.blocks.deleteMatchingBlock(action.copy(blockId = rowBlockId))
    } else {
        row.blocks.deleteMatchingBlock(action)
    }
    if (!delete.changed) {
        val label = rowBlockId
            .ifBlank { action.blockText }
            .ifBlank { action.content }
            .ifBlank { action.title }
            .ifBlank { action.blockType }
        error("Could not find row content block: $label")
    }
    return copy(
        table = table.copy(
            rows = table.rows.map { existing ->
                if (existing.id == row.id) existing.copy(blocks = delete.blocks) else existing
            },
        ),
    )
}

private fun PageBlock.deleteTableRow(rowId: String, rowTitle: String): PageBlock {
    val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
        ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
    return copy(table = table.copy(rows = table.rows.filterNot { existing -> existing.id == row.id }))
}

private fun PageBlock.sortTable(columnId: String, columnName: String, direction: PageTableSortDirection): PageBlock {
    val column = table.findColumn(columnId = columnId, columnName = columnName)
        ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
    return copy(table = table.copy(sort = PageTableSort(columnId = column.id, direction = direction)))
}

private fun PageBlock.filterTable(columnId: String, columnName: String, query: String): PageBlock {
    val column = table.findColumn(columnId = columnId, columnName = columnName)
        ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
    return copy(table = table.copy(filter = PageTableFilter(columnId = column.id, query = query)))
}

private fun PageBlock.groupTable(columnId: String, columnName: String): PageBlock {
    val column = table.findColumn(columnId = columnId, columnName = columnName)
        ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
    return copy(table = table.copy(groupByColumnId = column.id))
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
    return PageBlockCodec.newTableRow(this).copy(
        cells = associate { column -> column.id to valuesByNormalizedName[column.name.normalizedAiKey()].orEmpty() },
    )
}

private fun <T> List<T>.moveItem(
    itemId: String,
    targetIndex: Int,
    idSelector: (T) -> String,
): List<T> {
    val currentIndex = indexOfFirst { item -> idSelector(item) == itemId }
    if (currentIndex == -1) return this
    val item = this[currentIndex]
    val withoutItem = toMutableList().also { items -> items.removeAt(currentIndex) }
    val zeroBasedTarget = (targetIndex - 1).coerceIn(0, withoutItem.size)
    withoutItem.add(zeroBasedTarget, item)
    return withoutItem
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
    if (rowId.isNotBlank()) rows.firstOrNull { row -> row.id == rowId }?.let { return it }
    if (rowTitle.isBlank()) return null
    val titleColumn = columns.firstOrNull()
    return rows.firstOrNull { row -> row.cellText(titleColumn).equals(rowTitle, ignoreCase = true) }
        ?: rows.firstOrNull { row ->
            val cellText = row.cellText(titleColumn)
            cellText.isNotBlank() && (cellText.contains(rowTitle, ignoreCase = true) || rowTitle.contains(cellText, ignoreCase = true))
        }
}

private fun PageTableRow.cellText(column: PageTableColumn?): String {
    return column?.let { tableColumn -> cells[tableColumn.id] }.orEmpty().trim()
}

private fun PageTableColumn.withActionConfig(
    action: ChatAction,
    relationColumn: PageTableColumn? = null,
    resolvedRollupTargetColumnId: String = "",
): PageTableColumn {
    return copy(
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
        "quote" -> PageBlockType.Quote
        "divider", "line" -> PageBlockType.Divider
        "media", "file", "files", "image", "photo", "video", "attachment", "attachments", "mediafile" -> PageBlockType.MediaFile
        "database", "table", "databasetable" -> PageBlockType.DatabaseTable
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
        "quote" -> PageBlockType.Quote
        "divider", "line" -> PageBlockType.Divider
        "media", "file", "files", "image", "photo", "video", "attachment", "attachments", "mediafile" -> PageBlockType.MediaFile
        "database", "table", "databasetable" -> PageBlockType.DatabaseTable
        else -> null
    }
}

private fun String.toPageTableColumnType(): PageTableColumnType {
    return when (normalizedAiKey()) {
        "number", "count", "amount", "price", "cost", "total" -> PageTableColumnType.Number
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
