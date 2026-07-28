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
import com.changeyourlife.cyl.domain.model.PageTableCellValue
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableFilterOperator
import com.changeyourlife.cyl.domain.model.PageTableOptionColor
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.model.RichTextFormat
import com.changeyourlife.cyl.domain.model.RichTextSpanEngine
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.model.toAiMonthReferenceOrNull
import com.changeyourlife.cyl.domain.model.deleteCreatedPageUndo
import com.changeyourlife.cyl.domain.model.normalizedForType
import com.changeyourlife.cyl.domain.model.restorePageSnapshotsUndo
import com.changeyourlife.cyl.domain.model.toAiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.toTypedCellValue
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.usecase.BlockMutationResult as DomainBlockMutationResult
import com.changeyourlife.cyl.domain.usecase.PageMutationResult
import com.changeyourlife.cyl.domain.usecase.PageMutationUseCase
import com.changeyourlife.cyl.domain.usecase.PropertyMutationResult
import com.changeyourlife.cyl.domain.usecase.ReconcileTableDateRemindersUseCase
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
import kotlinx.coroutines.flow.first

internal class AiPageActionMutationEngine(
    private val pageRepository: PageRepository,
    private val pageMutationUseCase: PageMutationUseCase,
    private val tableMutationUseCase: TableMutationUseCase,
    private val scheduleTableDateReminderUseCase: ScheduleTableDateReminderUseCase,
    private val reconcileTableDateRemindersUseCase: ReconcileTableDateRemindersUseCase,
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
            val persistedPageBeforeAction = pageRepository.getPage(page.id) ?: page
            val pageBeforeAction = persistedPageBeforeAction.copy(
                title = workingTitle,
                content = PageBlockCodec.encodeDocument(workingDocument),
            )
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
                        val nextTitle = action.title.ifBlank { error("Missing new page title") }
                        if (nextTitle != workingTitle) {
                            undoCommands += restorePageSnapshotsUndo(
                                actionIndex = actionIndex,
                                pages = listOf(pageBeforeAction),
                            )
                            workingTitle = nextTitle
                            titleChanged = true
                        }
                        "Renamed page to: $workingTitle"
                    }

                    "UPDATE_PAGE" -> {
                        if (action.title.isNotBlank() && action.title != workingTitle) {
                            undoCommands += restorePageSnapshotsUndo(
                                actionIndex = actionIndex,
                                pages = listOf(pageBeforeAction),
                            )
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

                    "MOVE_BLOCK" -> {
                        val targetBlock = workingDocument.blocks.findMatchingBlock(action)
                            ?: error("Could not find block to move")
                        val mutation = action.targetIndex?.let { targetIndex ->
                            pageMutationUseCase.moveBlockToIndex(
                                document = workingDocument,
                                blockId = targetBlock.id,
                                targetIndex = targetIndex.toAiZeroBasedIndex(),
                            )
                        } ?: PageMutationResult(
                            applied = pageMutationUseCase.moveBlock(
                                document = workingDocument,
                                blockId = targetBlock.id,
                                direction = action.moveDirection.toBlockMoveDirection(),
                            ).applied,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Block is already at the requested position")
                        documentChanged = true
                        "Moved block: ${targetBlock.blockLabel()}"
                    }

                    "INDENT_BLOCK", "OUTDENT_BLOCK" -> {
                        val targetBlock = workingDocument.blocks.findMatchingBlock(action)
                            ?: error("Could not find block")
                        val mutation = if (actionType == "INDENT_BLOCK") {
                            pageMutationUseCase.indentBlock(workingDocument, targetBlock.id)
                        } else {
                            pageMutationUseCase.outdentBlock(workingDocument, targetBlock.id)
                        }
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) {
                            error(
                                if (actionType == "INDENT_BLOCK") {
                                    "Block cannot be indented from its current position"
                                } else {
                                    "Block is already at the outermost level"
                                },
                            )
                        }
                        documentChanged = true
                        if (actionType == "INDENT_BLOCK") "Indented block" else "Outdented block"
                    }

                    "DUPLICATE_BLOCK" -> {
                        val targetBlock = workingDocument.blocks.findMatchingBlock(action)
                            ?: error("Could not find block to duplicate")
                        val mutation = pageMutationUseCase.duplicateBlock(
                            document = workingDocument,
                            blockId = targetBlock.id,
                            targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Block could not be duplicated")
                        documentChanged = true
                        "Duplicated block: ${targetBlock.blockLabel()}"
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

                    "RENAME_PROPERTY" -> {
                        val propertyName = action.propertyName
                            .ifBlank { action.title }
                            .ifBlank { error("Missing property name") }
                        val newName = action.newPropertyName
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { error("Missing new property name") }
                        val property = workingDocument.properties.firstOrNull { candidate ->
                            candidate.name.normalizedAiKey() == propertyName.normalizedAiKey()
                        } ?: error("Could not find property: $propertyName")
                        val mutation = pageMutationUseCase.updatePropertyName(
                            document = workingDocument,
                            propertyId = property.id,
                            name = newName,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Property already uses that name")
                        documentChanged = true
                        "Renamed property $propertyName to $newName"
                    }

                    "MOVE_PROPERTY" -> {
                        val propertyName = action.propertyName
                            .ifBlank { action.title }
                            .ifBlank { error("Missing property name") }
                        val property = workingDocument.properties.firstOrNull { candidate ->
                            candidate.name.normalizedAiKey() == propertyName.normalizedAiKey()
                        } ?: error("Could not find property: $propertyName")
                        val mutation = pageMutationUseCase.moveProperty(
                            document = workingDocument,
                            propertyId = property.id,
                            targetIndex = requireNotNull(action.targetIndex).toAiZeroBasedIndex(),
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Property is already at the requested position")
                        documentChanged = true
                        "Moved property: $propertyName"
                    }

                    "DUPLICATE_PROPERTY" -> {
                        val propertyName = action.propertyName
                            .ifBlank { action.title }
                            .ifBlank { error("Missing property name") }
                        val property = workingDocument.properties.firstOrNull { candidate ->
                            candidate.name.normalizedAiKey() == propertyName.normalizedAiKey()
                        } ?: error("Could not find property: $propertyName")
                        val mutation = pageMutationUseCase.duplicateProperty(
                            document = workingDocument,
                            propertyId = property.id,
                            name = action.newPropertyName,
                            targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Property could not be duplicated")
                        documentChanged = true
                        "Duplicated property: ${mutation.property?.name.orEmpty()}"
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

                    "DUPLICATE_DATABASE" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching database")
                        val duplicateMutation = pageMutationUseCase.duplicateBlock(
                            document = workingDocument,
                            blockId = targetTable.id,
                            targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                        )
                        workingDocument = duplicateMutation.captureForAi(actionIndex, undoCommands)
                        if (!duplicateMutation.changed) error("Database could not be duplicated")
                        val duplicatedBlock = duplicateMutation.block
                        val duplicateTitle = action.title.trim().ifBlank { "${targetTable.table.title} copy" }
                        val titleMutation = tableMutationUseCase.updateTitle(
                            document = workingDocument,
                            tableBlockId = duplicatedBlock.id,
                            title = duplicateTitle,
                        )
                        workingDocument = titleMutation.captureForAi(actionIndex, undoCommands)
                        documentChanged = true
                        "Duplicated database as $duplicateTitle"
                    }

                    "ATTACH_TABLE_DATA_SOURCE" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find target database")
                        val workspacePages = pageRepository.observePages(page.workspaceId).first()
                        val sourcePage = workspacePages.resolveSourcePage(action)
                            ?: error("Could not find the requested source page")
                        val sourceDocument = PageBlockCodec.decodeDocument(sourcePage.content)
                        val sourceTable = sourceDocument.resolveSourceTable(action)
                            ?: error("Could not find the requested source database")
                        if (sourcePage.id == page.id && sourceTable.id == targetTable.id) {
                            error("A database cannot use itself as a data source")
                        }
                        val mutation = tableMutationUseCase.attachDataSource(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            sourcePageId = sourcePage.id,
                            sourceTableBlockId = sourceTable.id,
                            sourceTitle = action.sourceTableTitle
                                .ifBlank { sourceTable.table.title }
                                .ifBlank { sourcePage.title },
                            sourceTable = sourceTable.table,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Data source could not be attached")
                        documentChanged = true
                        "Attached data source ${sourceTable.table.title.ifBlank { sourcePage.title }}"
                    }

                    "CLEAR_TABLE_DATA_SOURCE" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find target database")
                        if (targetTable.table.viewConfig.dataSourcePageId.isBlank()) {
                            error("This database has no connected data source")
                        }
                        val mutation = tableMutationUseCase.clearDataSource(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Data source could not be disconnected")
                        documentChanged = true
                        "Disconnected data source from ${targetTable.table.title}"
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

                    "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG", "UPDATE_TABLE_DATE_CONFIG",
                    "UPDATE_FORMULA_COLUMN", "UPDATE_RELATION_COLUMN", "UPDATE_ROLLUP_COLUMN" -> {
                        val resolvedAction = action.withResolvedRelationTarget(workingDocument)
                        val columnName = resolvedAction.columnName
                            .ifBlank { resolvedAction.propertyName }
                            .ifBlank { resolvedAction.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(resolvedAction)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(resolvedAction.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { resolvedAction.columnId }}")
                        if (actionType == "UPDATE_TABLE_DATE_CONFIG" && targetColumn.type != PageTableColumnType.Date) {
                            error("Date configuration can only be applied to a Date column")
                        }
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

                    "ADD_TABLE_COLUMN_OPTION", "UPDATE_TABLE_COLUMN_OPTION", "DELETE_TABLE_COLUMN_OPTION" -> {
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        if (targetColumn.type !in setOf(
                                PageTableColumnType.Select,
                                PageTableColumnType.MultiSelect,
                                PageTableColumnType.Status,
                            )
                        ) {
                            error("Options can only be edited on Select, Multi-select, or Status columns")
                        }
                        val currentOptions = targetColumn.config.options
                        var renamedOption: Pair<String, String>? = null
                        var deletedOptionName = ""
                        val nextOptions = when (actionType) {
                            "ADD_TABLE_COLUMN_OPTION" -> {
                                val optionName = action.optionName
                                    .ifBlank { action.newOptionName }
                                    .ifBlank { action.value }
                                    .ifBlank { action.content }
                                    .trim()
                                    .ifBlank { error("Missing option name") }
                                if (currentOptions.any { option -> option.name.equals(optionName, ignoreCase = true) }) {
                                    error("Option already exists: $optionName")
                                }
                                currentOptions + PageTableSelectOption(
                                    id = action.optionId.ifBlank { UUID.randomUUID().toString() },
                                    name = optionName,
                                    color = action.optionColor.toPageTableOptionColorOrNull()
                                        ?: PageTableOptionColor.Gray,
                                )
                            }

                            "UPDATE_TABLE_COLUMN_OPTION" -> {
                                val targetOption = currentOptions.findAiOption(action)
                                    ?: error("Could not find option: ${action.optionName.ifBlank { action.optionId }}")
                                val newName = action.newOptionName
                                    .ifBlank { action.value }
                                    .ifBlank { action.content }
                                    .trim()
                                    .ifBlank { targetOption.name }
                                if (currentOptions.any { option ->
                                        option.id != targetOption.id &&
                                            option.name.equals(newName, ignoreCase = true)
                                    }
                                ) {
                                    error("Option already exists: $newName")
                                }
                                renamedOption = targetOption.name to newName
                                currentOptions.map { option ->
                                    if (option.id == targetOption.id) {
                                        option.copy(
                                            name = newName,
                                            color = action.optionColor.toPageTableOptionColorOrNull()
                                                ?: option.color,
                                        )
                                    } else {
                                        option
                                    }
                                }
                            }

                            else -> {
                                val targetOption = currentOptions.findAiOption(action)
                                    ?: error("Could not find option: ${action.optionName.ifBlank { action.optionId }}")
                                deletedOptionName = targetOption.name
                                currentOptions.filterNot { option -> option.id == targetOption.id }
                            }
                        }
                        val mutation = tableMutationUseCase.updateColumnOptions(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            columnId = targetColumn.id,
                            options = nextOptions,
                            renamedOption = renamedOption,
                            deletedOptionName = deletedOptionName,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Column options were not changed")
                        documentChanged = true
                        "Updated options in ${targetColumn.name}"
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

                    "DUPLICATE_TABLE_COLUMN" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }.ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val sourceColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        val duplicatedColumn = sourceColumn.copy(
                            id = UUID.randomUUID().toString(),
                            name = action.newColumnName.trim().ifBlank { "${sourceColumn.name} copy" },
                        )
                        val mutation = tableMutationUseCase.duplicateColumn(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            sourceColumnId = sourceColumn.id,
                            duplicatedColumn = duplicatedColumn,
                        )
                        workingDocument = mutation.mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Column could not be duplicated")
                        documentChanged = true
                        "Duplicated column ${duplicatedColumn.name} in ${targetTable.table.title}"
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
                                val column = targetTable.table.findColumnReference(columnReference)
                                    ?: error("Could not uniquely identify column: $columnReference")
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

                    "DUPLICATE_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val sourceRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val duplicate = tableMutationUseCase.duplicateRow(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            sourceRowId = sourceRow.id,
                            targetIndex = action.targetIndex?.toAiZeroBasedIndex(),
                        )
                        workingDocument = duplicate.mutation.captureForAi(actionIndex, undoCommands)
                        if (!duplicate.changed) error("Row could not be duplicated")
                        val newRowTitle = action.newRowTitle.trim()
                        if (newRowTitle.isNotBlank()) {
                            val primaryColumnId = targetTable.table.columns.firstOrNull()?.id
                                ?: error("Database has no primary column")
                            val titleMutation = tableMutationUseCase.updateRow(
                                document = workingDocument,
                                tableBlockId = targetTable.id,
                                rowId = requireNotNull(duplicate.row).id,
                                valuesByColumnId = mapOf(primaryColumnId to newRowTitle),
                            )
                            workingDocument = titleMutation.captureForAi(actionIndex, undoCommands)
                        }
                        documentChanged = true
                        "Duplicated row ${newRowTitle.ifBlank { rowTitle }}"
                    }

                    "DELETE_TABLE_ROWS" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val rows = targetTable.table.resolveBulkRows(action)
                        if (rows.isEmpty()) error("No rows match the requested condition")
                        val mutation = tableMutationUseCase.deleteRows(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowIds = rows.map(PageTableRow::id).toSet(),
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Matching rows could not be deleted")
                        documentChanged = true
                        "Deleted ${rows.size} matching rows from ${targetTable.table.title}"
                    }

                    "UPDATE_TABLE_ROWS" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val rows = targetTable.table.resolveBulkRows(action)
                        if (rows.isEmpty()) error("No rows match the requested condition")
                        val valuesByColumnId = action.cellValues.mapKeys { (columnReference, _) ->
                            targetTable.table.findColumnReference(columnReference)?.id
                                ?: error("Could not uniquely identify column: $columnReference")
                        }
                        if (valuesByColumnId.isEmpty()) error("Bulk update needs cellValues")
                        val mutation = tableMutationUseCase.updateRows(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowIds = rows.map(PageTableRow::id).toSet(),
                            valuesByColumnId = valuesByColumnId,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Matching rows were not changed")
                        documentChanged = true
                        "Updated ${rows.size} matching rows in ${targetTable.table.title}"
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
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        val value = if (isClearAction) {
                            ""
                        } else {
                            action.resolvedTableCellUpdateValue(targetColumn)
                        }
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

                    "SET_RELATION_CELL", "CLEAR_RELATION_CELL" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        if (targetColumn.type != PageTableColumnType.Relation) {
                            error("${targetColumn.name} is not a Relation column")
                        }
                        val relationRowIds = if (actionType == "CLEAR_RELATION_CELL") {
                            emptyList()
                        } else {
                            action.relationRowIds
                        }
                        val mutation = tableMutationUseCase.updateRelationCell(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowId = targetRow.id,
                            columnId = targetColumn.id,
                            relationRowIds = relationRowIds,
                        )
                        workingDocument = mutation.mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Relation cell was not changed")
                        documentChanged = true
                        if (actionType == "CLEAR_RELATION_CELL") {
                            "Cleared ${targetColumn.name} relation"
                        } else {
                            "Updated ${targetColumn.name} relation"
                        }
                    }

                    "ADD_MEDIA_CELL", "REMOVE_MEDIA_CELL", "CLEAR_MEDIA_CELL" -> {
                        val columnName = action.columnName.ifBlank { action.propertyName }
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find row: ${rowTitle.ifBlank { action.rowId }}")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        if (targetColumn.type != PageTableColumnType.FilesMedia) {
                            error("${targetColumn.name} is not a Files & media column")
                        }
                        val mutation = tableMutationUseCase.updateMediaCell(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            rowId = targetRow.id,
                            columnId = targetColumn.id,
                        ) { files ->
                            when (actionType) {
                                "ADD_MEDIA_CELL" -> {
                                    val uri = action.mediaUri.trim().ifBlank { error("Missing media URI") }
                                    files + PageMediaAttachment(
                                        id = action.mediaId.ifBlank { UUID.randomUUID().toString() },
                                        uri = uri,
                                        name = action.mediaName.trim().ifBlank { uri.substringAfterLast('/') },
                                        mimeType = action.mediaMimeType,
                                        sizeBytes = action.mediaSizeBytes,
                                    )
                                }

                                "REMOVE_MEDIA_CELL" -> files.filterNot { file -> file.matchesAiMedia(action) }
                                else -> emptyList()
                            }
                        }
                        workingDocument = mutation.mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Media cell was not changed")
                        documentChanged = true
                        "Updated ${targetColumn.name} media"
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

                    "CREATE_TABLE_SAVED_VIEW" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val viewName = action.viewName
                            .ifBlank { action.title }
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .trim()
                            .ifBlank { error("Missing saved view name") }
                        val view = action.tableView
                            .takeIf(String::isNotBlank)
                            ?.toPageTableView()
                            ?: targetTable.table.view
                        val configuredView = action.toTableViewConfig(targetTable.table)
                        val ruleColumn = targetTable.table.findColumn(action.columnId, action.columnName)
                        val sort = ruleColumn?.takeIf { action.sortDirection.isNotBlank() }?.let { column ->
                            PageTableSort(
                                columnId = column.id,
                                direction = action.sortDirection.toPageTableSortDirection(),
                            )
                        }
                        val filterOperator = action.filterOperator.toPageTableFilterOperator()
                        val filter = ruleColumn?.takeIf {
                            action.filterQuery.isNotBlank() ||
                                filterOperator in setOf(
                                    PageTableFilterOperator.IsEmpty,
                                    PageTableFilterOperator.IsNotEmpty,
                                )
                        }?.let { column ->
                            PageTableFilter(
                                columnId = column.id,
                                query = action.filterQuery,
                                operator = filterOperator,
                            )
                        }
                        val groupColumnId = targetTable.table.findColumn(
                            action.groupByColumnId,
                            action.groupByColumnName,
                        )?.id
                        val mutation = tableMutationUseCase.createSavedView(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            name = viewName,
                            view = view,
                            viewId = action.viewId.ifBlank { UUID.randomUUID().toString() },
                            calendarDateColumnId = configuredView.calendarDateColumnId,
                            timelineStartColumnId = configuredView.timelineStartColumnId,
                            timelineEndColumnId = configuredView.timelineEndColumnId,
                            dashboardMetricColumnId = configuredView.dashboardMetricColumnId,
                            dashboardGroupColumnId = configuredView.dashboardGroupColumnId,
                            sort = sort,
                            filter = filter,
                            groupByColumnId = groupColumnId,
                        )
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Saved view already exists: $viewName")
                        documentChanged = true
                        "Created saved view $viewName"
                    }

                    "RENAME_TABLE_SAVED_VIEW", "DELETE_TABLE_SAVED_VIEW", "ACTIVATE_TABLE_SAVED_VIEW" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val mutation = when (actionType) {
                            "RENAME_TABLE_SAVED_VIEW" -> tableMutationUseCase.renameSavedView(
                                document = workingDocument,
                                tableBlockId = targetTable.id,
                                viewId = action.viewId,
                                viewName = action.viewName,
                                newName = action.newViewName
                                    .ifBlank { action.value }
                                    .ifBlank { action.content },
                            )

                            "DELETE_TABLE_SAVED_VIEW" -> tableMutationUseCase.deleteSavedView(
                                document = workingDocument,
                                tableBlockId = targetTable.id,
                                viewId = action.viewId,
                                viewName = action.viewName,
                            )

                            else -> tableMutationUseCase.activateSavedView(
                                document = workingDocument,
                                tableBlockId = targetTable.id,
                                viewId = action.viewId,
                                viewName = action.viewName,
                            )
                        }
                        workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                        if (!mutation.changed) error("Could not update the requested saved view")
                        documentChanged = true
                        when (actionType) {
                            "RENAME_TABLE_SAVED_VIEW" -> "Renamed saved view"
                            "DELETE_TABLE_SAVED_VIEW" -> "Deleted saved view"
                            else -> "Activated saved view"
                        }
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
                        val operator = action.filterOperator.toPageTableFilterOperator()
                        val query = action.filterQuery.ifBlank { action.value }.ifBlank { action.content }
                        if (query.isBlank() && operator !in setOf(
                                PageTableFilterOperator.IsEmpty,
                                PageTableFilterOperator.IsNotEmpty,
                            )
                        ) {
                            error("Missing filter query")
                        }
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find matching table")
                        val targetColumn = targetTable.table.findColumn(action.columnId, columnName)
                            ?: error("Could not find column: ${columnName.ifBlank { action.columnId }}")
                        val mutation = tableMutationUseCase.updateFilter(
                            document = workingDocument,
                            tableBlockId = targetTable.id,
                            filter = PageTableFilter(
                                columnId = targetColumn.id,
                                query = query,
                                operator = operator,
                            ),
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
                        undoCommands += deleteCreatedPageUndo(
                            actionIndex = actionIndex,
                            pageId = created.id,
                        )
                        reconcileTableDateRemindersUseCase.scheduleAll(
                            page = created,
                            document = PageBlockCodec.decodeDocument(created.content),
                        )
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
                        undoCommands += deleteCreatedPageUndo(
                            actionIndex = actionIndex,
                            pageId = created.id,
                        )
                        reconcileTableDateRemindersUseCase.scheduleAll(
                            page = created,
                            document = PageBlockCodec.decodeDocument(created.content),
                        )
                        if (moduleType != null) "Created ${moduleType.label} module: $pageTitle" else "Created page: $pageTitle"
                    }

                    "MOVE_PAGE" -> {
                        val workspacePages = pageRepository.observePages(page.workspaceId).first()
                        val parentPageId = workspacePages.resolveParentPageId(
                            action = action,
                            movingPage = pageBeforeAction,
                        )
                        if (pageBeforeAction.parentPageId == parentPageId) {
                            error("Page is already in the requested location")
                        }
                        undoCommands += restorePageSnapshotsUndo(
                            actionIndex = actionIndex,
                            pages = listOf(pageBeforeAction),
                        )
                        pageRepository.upsertPage(
                            pageBeforeAction.copy(
                                parentPageId = parentPageId,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        if (parentPageId == null) {
                            "Moved page to workspace root"
                        } else {
                            val parentTitle = workspacePages.firstOrNull { candidate -> candidate.id == parentPageId }
                                ?.title
                                .orEmpty()
                            "Moved page under ${parentTitle.ifBlank { "the selected page" }}"
                        }
                    }

                    "DUPLICATE_PAGE" -> {
                        val duplicateTitle = action.title.trim().ifBlank { "${page.title} copy" }
                        val created = pageRepository.createPage(
                            workspaceId = page.workspaceId,
                            title = duplicateTitle,
                            content = page.content,
                            parentPageId = page.parentPageId,
                        )
                        createdPages += created
                        undoCommands += deleteCreatedPageUndo(
                            actionIndex = actionIndex,
                            pageId = created.id,
                        )
                        reconcileTableDateRemindersUseCase.scheduleAll(
                            page = created,
                            document = PageBlockCodec.decodeDocument(created.content),
                        )
                        "Duplicated page as $duplicateTitle"
                    }

                    "TRASH_PAGE" -> {
                        val snapshots = pageRepository
                            .getPageTreeSnapshot(page.id)
                            .withRootSnapshot(pageBeforeAction)
                        undoCommands += restorePageSnapshotsUndo(
                            actionIndex = actionIndex,
                            pages = snapshots,
                        )
                        pageRepository.deletePage(page.id)
                        reconcileTableDateRemindersUseCase.cancelAll(page, document)
                        "Moved page to trash"
                    }

                    "RESTORE_PAGE" -> {
                        val snapshots = pageRepository
                            .getPageTreeSnapshot(page.id)
                            .withRootSnapshot(pageBeforeAction)
                        undoCommands += restorePageSnapshotsUndo(
                            actionIndex = actionIndex,
                            pages = snapshots,
                        )
                        pageRepository.restorePage(page.id)
                        reconcileTableDateRemindersUseCase.scheduleAll(
                            page = pageBeforeAction.copy(deletedAt = null),
                            document = workingDocument,
                        )
                        "Restored page"
                    }

                    "DELETE_PAGE_PERMANENTLY" -> {
                        if (pageBeforeAction.deletedAt == null) {
                            error("Move the page to trash before deleting it permanently")
                        }
                        val snapshots = pageRepository
                            .getPageTreeSnapshot(page.id)
                            .withRootSnapshot(pageBeforeAction)
                        undoCommands += restorePageSnapshotsUndo(
                            actionIndex = actionIndex,
                            pages = snapshots,
                        )
                        pageRepository.deletePagePermanently(page.id)
                        reconcileTableDateRemindersUseCase.cancelAll(page, document)
                        "Deleted page permanently"
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
                        val reminder = scheduleTableDateReminderUseCase.resolve(
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

                    "CANCEL_REMINDER", "RESCHEDULE_REMINDER", "COMPLETE_REMINDER" -> {
                        val targetTable = workingDocument.blocks.findMatchingTable(action)
                            ?: error("Could not find reminder database")
                        val rowTitle = action.rowTitle.ifBlank { action.title }
                        val targetRow = targetTable.table.findRow(action.rowId, rowTitle)
                            ?: error("Could not find reminder row: ${rowTitle.ifBlank { action.rowId }}")
                        val dateColumn = targetTable.table.resolveReminderDateColumn(action)
                            ?: error("Could not determine the reminder date column")
                        val currentValue = targetRow.cells[dateColumn.id].orEmpty()

                        when (actionType) {
                            "RESCHEDULE_REMINDER" -> {
                                val nextValue = action.taskDateCellValue()
                                    .ifBlank { error("Reschedule reminder needs a future date or delay") }
                                val mutation = tableMutationUseCase.updateCell(
                                    document = workingDocument,
                                    tableBlockId = targetTable.id,
                                    rowId = targetRow.id,
                                    columnId = dateColumn.id,
                                    value = nextValue,
                                )
                                workingDocument = mutation.mutation.captureForAi(actionIndex, undoCommands)
                                if (!mutation.changed) error("Reminder date was not changed")
                                val reminder = scheduleTableDateReminderUseCase.resolve(
                                    page = page,
                                    document = workingDocument,
                                    tableBlockId = targetTable.id,
                                    rowId = targetRow.id,
                                    columnId = dateColumn.id,
                                    value = nextValue,
                                ) ?: error("Reminder date or time must be in the future")
                                createdReminders += reminder
                                documentChanged = true
                                "Rescheduled reminder for ${targetRow.primaryTitle(targetTable.table)}"
                            }

                            "CANCEL_REMINDER", "COMPLETE_REMINDER" -> {
                                val valuesByColumnId = buildMap {
                                    put(dateColumn.id, currentValue.withoutReminderMetadata())
                                    if (actionType == "COMPLETE_REMINDER") {
                                        targetTable.table.columns
                                            .firstOrNull { column ->
                                                column.type == PageTableColumnType.Status ||
                                                    column.name.normalizedAiKey() in setOf("status", "progress")
                                            }
                                            ?.let { statusColumn -> put(statusColumn.id, "Done") }
                                    }
                                }
                                val mutation = tableMutationUseCase.updateRow(
                                    document = workingDocument,
                                    tableBlockId = targetTable.id,
                                    rowId = targetRow.id,
                                    valuesByColumnId = valuesByColumnId,
                                )
                                workingDocument = mutation.captureForAi(actionIndex, undoCommands)
                                documentChanged = documentChanged || mutation.changed
                                if (actionType == "COMPLETE_REMINDER") {
                                    "Completed reminder for ${targetRow.primaryTitle(targetTable.table)}"
                                } else {
                                    "Cancelled reminder for ${targetRow.primaryTitle(targetTable.table)}"
                                }
                            }
                        }
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
    val actionType = action.type.normalizedActionType()

    fun targetNotFound(field: String, targetKind: String, targetLabel: String): AiPageActionValidationIssue {
        val label = targetLabel.ifBlank { "target $targetKind" }
        return AiPageActionValidationIssue(
            actionIndex = actionIndex,
            field = field,
            code = "target_not_found",
            message = "Could not find $targetKind: $label.",
        )
    }

    fun invalidAction(field: String, code: String, message: String): AiPageActionValidationIssue {
        return AiPageActionValidationIssue(
            actionIndex = actionIndex,
            field = field,
            code = code,
            message = message,
        )
    }

    fun targetTable(): PageBlock? = blocks.findMatchingTable(action)

    fun tableIssue(): AiPageActionValidationIssue? {
        return when (blocks.resolveMatchingTable(action)) {
            is AiTableResolution.Found -> null
            AiTableResolution.Ambiguous -> invalidAction(
                field = "tableTitle",
                code = "ambiguous_target",
                message = "More than one table matched. Specify the exact table name or table block ID.",
            )
            AiTableResolution.Missing -> targetNotFound(
                field = "tableTitle",
                targetKind = "table",
                targetLabel = action.tableTitle.ifBlank { action.title },
            )
        }
    }

    fun missingColumnIssue(
        table: PageBlock,
        field: String,
        columnId: String,
        columnName: String,
    ): AiPageActionValidationIssue? {
        if (columnId.isBlank() && columnName.isBlank()) return null
        return when (table.table.resolveColumn(columnId, columnName)) {
            is AiColumnResolution.Found -> null
            AiColumnResolution.Ambiguous -> invalidAction(
                field = field,
                code = "ambiguous_target",
                message = "More than one column matched ${columnName.ifBlank { columnId }}. " +
                    "Specify the exact column name or column ID.",
            )
            AiColumnResolution.Missing -> targetNotFound(
                field = field,
                targetKind = "column",
                targetLabel = columnName.ifBlank { columnId },
            )
        }
    }

    fun targetColumn(table: PageBlock): PageTableColumn? {
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
        return table.table.findColumn(columnId, columnName)
    }

    fun columnIssue(table: PageBlock): AiPageActionValidationIssue? {
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

    fun targetRow(table: PageBlock): PageTableRow? =
        table.table.findRow(action.rowId, action.rowTitle.ifBlank { action.title })

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
        // A non-blank ID may point to a database on another page. Treat that
        // opaque workspace ID as authoritative; title-only targets must resolve locally.
        if (tableId.isNotBlank()) return null
        return when (blocks.resolveTableByTitle(tableTitle)) {
            is AiTableResolution.Found -> null
            AiTableResolution.Ambiguous -> invalidAction(
                field = field,
                code = "ambiguous_target",
                message = "More than one relation target table matched. Specify the exact table block ID.",
            )
            AiTableResolution.Missing -> targetNotFound(
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
        val referenceIssue = FormulaColumnReferenceRegex.findAll(formula)
            .map { match -> match.groupValues.getOrNull(1).orEmpty().trim() }
            .filter { columnName -> columnName.isNotBlank() }
            .firstNotNullOfOrNull { referencedColumnName ->
                when (table.table.resolveColumn(columnName = referencedColumnName)) {
                    is AiColumnResolution.Found -> null
                    AiColumnResolution.Ambiguous -> invalidAction(
                        field = "formula",
                        code = "ambiguous_target",
                        message = "Formula reference $referencedColumnName matches more than one column. " +
                            "Use the exact column name.",
                    )
                    AiColumnResolution.Missing -> targetNotFound(
                        field = "formula",
                        targetKind = "formula column",
                        targetLabel = referencedColumnName,
                    )
                }
            }
        if (referenceIssue != null) return referenceIssue
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
        missingColumnIssue(
            table = table,
            field = "rollupRelationColumnName",
            columnId = action.rollupRelationColumnId,
            columnName = action.rollupRelationColumnName,
        )?.let { issue -> return issue }
        val relationColumn = table.table.findColumn(
            columnId = action.rollupRelationColumnId,
            columnName = action.rollupRelationColumnName,
        )

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

    fun columnDefaultValueIssue(column: PageTableColumn): AiPageActionValidationIssue? {
        val value = action.defaultValue.trim()
        if (value.isBlank() || action.clearDefaultValue == true) return null
        return when (column.type) {
            PageTableColumnType.Text -> null
            PageTableColumnType.Number -> if (value.toDoubleOrNull() != null) {
                null
            } else {
                invalidAction(
                    field = "defaultValue",
                    code = "invalid_default_value",
                    message = "Default value for ${column.name} must be a number.",
                )
            }

            PageTableColumnType.Checkbox -> if (value.normalizedAiKey() in setOf(
                    "true",
                    "false",
                    "yes",
                    "no",
                    "checked",
                    "unchecked",
                    "done",
                    "notdone",
                    "1",
                    "0",
                )
            ) {
                null
            } else {
                invalidAction(
                    field = "defaultValue",
                    code = "invalid_default_value",
                    message = "Default value for ${column.name} must be true or false.",
                )
            }

            PageTableColumnType.Date -> if (value.isValidAiDateCellValue()) {
                null
            } else {
                invalidAction(
                    field = "defaultValue",
                    code = "invalid_date",
                    message = "Could not parse date value: $value.",
                )
            }
            PageTableColumnType.Select,
            PageTableColumnType.Status,
            -> {
                val optionNames = (action.options.takeIf { options -> options.isNotEmpty() }
                    ?: column.config.options.map(PageTableSelectOption::name))
                if (optionNames.any { option -> option.equals(value, ignoreCase = true) }) {
                    null
                } else {
                    invalidAction(
                        field = "defaultValue",
                        code = "invalid_default_value",
                        message = "Default value for ${column.name} must match an existing option.",
                    )
                }
            }

            PageTableColumnType.MultiSelect -> {
                val optionNames = (action.options.takeIf { options -> options.isNotEmpty() }
                    ?: column.config.options.map(PageTableSelectOption::name))
                val requested = value
                    .split(',', ';', '\n')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                if (
                    requested.isNotEmpty() &&
                    requested.all { selected ->
                        optionNames.any { option -> option.equals(selected, ignoreCase = true) }
                    }
                ) {
                    null
                } else {
                    invalidAction(
                        field = "defaultValue",
                        code = "invalid_default_value",
                        message = "Default values for ${column.name} must match existing options.",
                    )
                }
            }

            PageTableColumnType.Formula,
            PageTableColumnType.Rollup,
            PageTableColumnType.Relation,
            PageTableColumnType.FilesMedia,
            -> invalidAction(
                field = "defaultValue",
                code = "invalid_default_value",
                message = "${column.type.name} columns do not support a text default value.",
            )
        }
    }

    fun columnConfigIssue(table: PageBlock): AiPageActionValidationIssue? {
        val column = targetColumn(table) ?: return columnIssue(table)
        return when (actionType) {
            "UPDATE_FORMULA_COLUMN" -> {
                if (column.type != PageTableColumnType.Formula) {
                    invalidAction(
                        field = "columnName",
                        code = "invalid_target_type",
                        message = "${column.name} is not a Formula column.",
                    )
                } else {
                    formulaConfigIssue(table)
                }
            }

            "UPDATE_RELATION_COLUMN" -> {
                if (column.type != PageTableColumnType.Relation) {
                    invalidAction(
                        field = "columnName",
                        code = "invalid_target_type",
                        message = "${column.name} is not a Relation column.",
                    )
                } else {
                    relationConfigIssue()
                }
            }

            "UPDATE_ROLLUP_COLUMN" -> {
                if (column.type != PageTableColumnType.Rollup) {
                    invalidAction(
                        field = "columnName",
                        code = "invalid_target_type",
                        message = "${column.name} is not a Rollup column.",
                    )
                } else {
                    rollupConfigIssue(table)
                }
            }

            "UPDATE_TABLE_DATE_CONFIG" -> {
                if (column.type == PageTableColumnType.Date) {
                    null
                } else {
                    invalidAction(
                        field = "columnName",
                        code = "invalid_target_type",
                        message = "${column.name} is not a Date column.",
                    )
                }
            }

            "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG" -> {
                if (action.options.isNotEmpty() &&
                    column.type !in setOf(
                        PageTableColumnType.Select,
                        PageTableColumnType.MultiSelect,
                        PageTableColumnType.Status,
                    )
                ) {
                    invalidAction(
                        field = "options",
                        code = "invalid_target_type",
                        message = "Options are only valid for Select, Multi-select, or Status columns.",
                    )
                } else {
                    columnDefaultValueIssue(column)
                }
            }

            else -> null
        }
    }

    fun columnOptionIssue(table: PageBlock): AiPageActionValidationIssue? {
        val column = targetColumn(table) ?: return columnIssue(table)
        if (column.type !in setOf(
                PageTableColumnType.Select,
                PageTableColumnType.MultiSelect,
                PageTableColumnType.Status,
            )
        ) {
            return invalidAction(
                field = "columnName",
                code = "invalid_target_type",
                message = "${column.name} does not support options.",
            )
        }
        val options = column.config.options
        return when (actionType) {
            "ADD_TABLE_COLUMN_OPTION" -> {
                val name = action.optionName
                    .ifBlank { action.newOptionName }
                    .ifBlank { action.value }
                    .ifBlank { action.content }
                    .trim()
                if (options.any { option -> option.name.equals(name, ignoreCase = true) }) {
                    invalidAction(
                        field = "optionName",
                        code = "duplicate_target",
                        message = "Option already exists: $name.",
                    )
                } else {
                    null
                }
            }

            "UPDATE_TABLE_COLUMN_OPTION", "DELETE_TABLE_COLUMN_OPTION" -> {
                val targetOption = options.findAiOption(action)
                    ?: return targetNotFound(
                        field = "optionId",
                        targetKind = "option",
                        targetLabel = action.optionName.ifBlank { action.optionId },
                    )
                val newName = action.newOptionName
                    .ifBlank { action.value }
                    .ifBlank { action.content }
                    .trim()
                if (
                    actionType == "UPDATE_TABLE_COLUMN_OPTION" &&
                    newName.isNotBlank() &&
                    options.any { option ->
                        option.id != targetOption.id &&
                            option.name.equals(newName, ignoreCase = true)
                    }
                ) {
                    invalidAction(
                        field = "newOptionName",
                        code = "duplicate_target",
                        message = "Option already exists: $newName.",
                    )
                } else {
                    null
                }
            }

            else -> null
        }
    }

    fun filterRuleIssue(table: PageBlock): AiPageActionValidationIssue? {
        val column = targetColumn(table) ?: return columnIssue(table)
        val operator = action.filterOperator.toPageTableFilterOperator()
        val supported = when (column.type) {
            PageTableColumnType.Number,
            PageTableColumnType.Formula,
            PageTableColumnType.Rollup,
            -> operator in setOf(
                PageTableFilterOperator.Equals,
                PageTableFilterOperator.NotEquals,
                PageTableFilterOperator.GreaterThan,
                PageTableFilterOperator.GreaterThanOrEqual,
                PageTableFilterOperator.LessThan,
                PageTableFilterOperator.LessThanOrEqual,
                PageTableFilterOperator.IsEmpty,
                PageTableFilterOperator.IsNotEmpty,
            )

            PageTableColumnType.Date -> operator in setOf(
                PageTableFilterOperator.Equals,
                PageTableFilterOperator.Before,
                PageTableFilterOperator.After,
                PageTableFilterOperator.OnOrBefore,
                PageTableFilterOperator.OnOrAfter,
                PageTableFilterOperator.IsEmpty,
                PageTableFilterOperator.IsNotEmpty,
            )

            PageTableColumnType.Checkbox -> operator in setOf(
                PageTableFilterOperator.Equals,
                PageTableFilterOperator.NotEquals,
            )

            else -> operator in setOf(
                PageTableFilterOperator.Contains,
                PageTableFilterOperator.NotContains,
                PageTableFilterOperator.Equals,
                PageTableFilterOperator.NotEquals,
                PageTableFilterOperator.IsEmpty,
                PageTableFilterOperator.IsNotEmpty,
            )
        }
        if (!supported) {
            return invalidAction(
                field = "filterOperator",
                code = "invalid_filter_operator",
                message = "${action.filterOperator.ifBlank { operator.name }} cannot be used with ${column.type.name}.",
            )
        }
        val query = action.filterQuery.ifBlank { action.value }.ifBlank { action.content }
        return if (
            query.isBlank() &&
            operator !in setOf(PageTableFilterOperator.IsEmpty, PageTableFilterOperator.IsNotEmpty)
        ) {
            invalidAction(
                field = "filterQuery",
                code = "required",
                message = "Filter ${operator.name} needs a value.",
            )
        } else {
            null
        }
    }

    fun viewConfigTypeIssue(table: PageBlock): AiPageActionValidationIssue? {
        fun configuredColumn(
            field: String,
            columnId: String,
            columnName: String,
            allowedTypes: Set<PageTableColumnType>,
        ): AiPageActionValidationIssue? {
            if (columnId.isBlank() && columnName.isBlank()) return null
            val column = table.table.findColumn(columnId, columnName) ?: return null
            return if (column.type in allowedTypes) {
                null
            } else {
                invalidAction(
                    field = field,
                    code = "invalid_target_type",
                    message = "${column.name} must be ${allowedTypes.joinToString(" or ") { it.name }}.",
                )
            }
        }

        val explicitIssue = configuredColumn(
            field = "calendarDateColumnName",
            columnId = action.calendarDateColumnId,
            columnName = action.calendarDateColumnName,
            allowedTypes = setOf(PageTableColumnType.Date),
        ) ?: configuredColumn(
            field = "timelineStartColumnName",
            columnId = action.timelineStartColumnId,
            columnName = action.timelineStartColumnName,
            allowedTypes = setOf(PageTableColumnType.Date),
        ) ?: configuredColumn(
            field = "timelineEndColumnName",
            columnId = action.timelineEndColumnId,
            columnName = action.timelineEndColumnName,
            allowedTypes = setOf(PageTableColumnType.Date),
        ) ?: configuredColumn(
            field = "dashboardMetricColumnName",
            columnId = action.dashboardMetricColumnId,
            columnName = action.dashboardMetricColumnName,
            allowedTypes = setOf(
                PageTableColumnType.Number,
                PageTableColumnType.Formula,
                PageTableColumnType.Rollup,
            ),
        )
        if (explicitIssue != null) return explicitIssue

        if (action.columnId.isBlank() && action.columnName.isBlank() && action.propertyName.isBlank()) {
            return null
        }
        val requestedView = action.tableView.toPageTableView()
        val usesGenericColumn = when (requestedView) {
            PageTableView.Calendar ->
                action.calendarDateColumnId.isBlank() && action.calendarDateColumnName.isBlank()
            PageTableView.Timeline ->
                action.timelineStartColumnId.isBlank() && action.timelineStartColumnName.isBlank()
            PageTableView.Dashboard ->
                action.dashboardMetricColumnId.isBlank() && action.dashboardMetricColumnName.isBlank()
            else -> false
        }
        if (!usesGenericColumn) return null
        val column = targetColumn(table) ?: return columnIssue(table)
        val allowedTypes = when (requestedView) {
            PageTableView.Calendar,
            PageTableView.Timeline,
            -> setOf(PageTableColumnType.Date)
            PageTableView.Dashboard -> setOf(
                PageTableColumnType.Number,
                PageTableColumnType.Formula,
                PageTableColumnType.Rollup,
            )
            else -> return null
        }
        return if (column.type in allowedTypes) {
            null
        } else {
            invalidAction(
                field = "columnName",
                code = "invalid_target_type",
                message = "${column.name} must be ${allowedTypes.joinToString(" or ") { it.name }}.",
            )
        }
    }

    fun savedViewIssue(table: PageBlock): AiPageActionValidationIssue? {
        val views = table.table.viewConfig.savedViews
        val target = when {
            action.viewId.isNotBlank() -> views.firstOrNull { view -> view.id == action.viewId }
            action.viewName.isNotBlank() -> views.firstOrNull { view ->
                view.name.equals(action.viewName, ignoreCase = true)
            }
            else -> null
        }
        return when (actionType) {
            "CREATE_TABLE_SAVED_VIEW" -> {
                val name = action.viewName
                    .ifBlank { action.title }
                    .ifBlank { action.value }
                    .ifBlank { action.content }
                    .trim()
                when {
                    action.viewId.isNotBlank() && views.any { view -> view.id == action.viewId } ->
                        invalidAction(
                            field = "viewId",
                            code = "duplicate_target",
                            message = "Saved view ID already exists: ${action.viewId}.",
                        )

                    views.any { view -> view.name.equals(name, ignoreCase = true) } ->
                        invalidAction(
                            field = "viewName",
                            code = "duplicate_target",
                            message = "Saved view already exists: $name.",
                        )

                    else -> viewConfigColumnIssue(table)
                        ?: viewConfigTypeIssue(table)
                        ?: if (action.sortDirection.isNotBlank()) {
                            columnIssue(table)
                        } else {
                            null
                        }
                        ?: if (
                            action.filterQuery.isNotBlank() ||
                            action.filterOperator.isNotBlank()
                        ) {
                            filterRuleIssue(table)
                        } else {
                            null
                        }
                }
            }

            "RENAME_TABLE_SAVED_VIEW" -> {
                if (target == null) {
                    targetNotFound(
                        field = "viewId",
                        targetKind = "saved view",
                        targetLabel = action.viewName.ifBlank { action.viewId },
                    )
                } else {
                    val newName = action.newViewName
                        .ifBlank { action.value }
                        .ifBlank { action.content }
                        .trim()
                    if (views.any { view ->
                            view.id != target.id && view.name.equals(newName, ignoreCase = true)
                        }
                    ) {
                        invalidAction(
                            field = "newViewName",
                            code = "duplicate_target",
                            message = "Saved view already exists: $newName.",
                        )
                    } else {
                        null
                    }
                }
            }

            "DELETE_TABLE_SAVED_VIEW" -> target?.let { null } ?: targetNotFound(
                field = "viewId",
                targetKind = "saved view",
                targetLabel = action.viewName.ifBlank { action.viewId },
            )

            "ACTIVATE_TABLE_SAVED_VIEW" -> when {
                target == null -> targetNotFound(
                    field = "viewId",
                    targetKind = "saved view",
                    targetLabel = action.viewName.ifBlank { action.viewId },
                )
                table.table.viewConfig.activeSavedViewId == target.id -> invalidAction(
                    field = "viewId",
                    code = "already_applied",
                    message = "Saved view is already active: ${target.name}.",
                )
                else -> null
            }

            else -> null
        }
    }

    fun relationCellIssue(table: PageBlock): AiPageActionValidationIssue? {
        val row = targetRow(table) ?: return targetNotFound(
            field = "rowTitle",
            targetKind = "row",
            targetLabel = action.rowTitle.ifBlank { action.title }.ifBlank { action.rowId },
        )
        val column = targetColumn(table) ?: return columnIssue(table)
        if (column.type != PageTableColumnType.Relation) {
            return invalidAction(
                field = "columnName",
                code = "invalid_target_type",
                message = "${column.name} is not a Relation column.",
            )
        }
        val currentIds = row.cellValues[column.id]?.relationRowIds.orEmpty()
        if (actionType == "CLEAR_RELATION_CELL") {
            return if (currentIds.isEmpty()) {
                invalidAction(
                    field = "columnName",
                    code = "already_applied",
                    message = "${column.name} is already empty.",
                )
            } else {
                null
            }
        }
        val targetTableId = column.relationTargetTableId
        if (targetTableId.isBlank()) {
            return targetNotFound(
                field = "relationTargetTableId",
                targetKind = "relation table",
                targetLabel = targetTableId,
            )
        }
        val relationTable = findTableBlock(targetTableId)
        if (relationTable == null) {
            // Cross-page relation IDs are opaque here. They were selected from
            // workspace context, so preserve them instead of rejecting a valid target.
            return null
        }
        val missingRowId = action.relationRowIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .firstOrNull { rowId -> relationTable.table.rows.none { row -> row.id == rowId } }
        if (missingRowId != null) {
            return targetNotFound(
                field = "relationRowIds",
                targetKind = "relation row",
                targetLabel = missingRowId,
            )
        }
        val requestedIds = action.relationRowIds.map(String::trim).filter(String::isNotBlank).distinct()
        return if (requestedIds == currentIds) {
            invalidAction(
                field = "relationRowIds",
                code = "already_applied",
                message = "${column.name} already contains the requested relation rows.",
            )
        } else {
            null
        }
    }

    fun mediaCellIssue(table: PageBlock): AiPageActionValidationIssue? {
        val row = targetRow(table) ?: return targetNotFound(
            field = "rowTitle",
            targetKind = "row",
            targetLabel = action.rowTitle.ifBlank { action.title }.ifBlank { action.rowId },
        )
        val column = targetColumn(table) ?: return columnIssue(table)
        if (column.type != PageTableColumnType.FilesMedia) {
            return invalidAction(
                field = "columnName",
                code = "invalid_target_type",
                message = "${column.name} is not a Files & media column.",
            )
        }
        val files = row.cellValues[column.id]?.files.orEmpty()
        return when (actionType) {
            "ADD_MEDIA_CELL" -> {
                val duplicate = files.any { file ->
                    (action.mediaId.isNotBlank() && file.id == action.mediaId) ||
                        file.uri == action.mediaUri
                }
                if (duplicate) {
                    invalidAction(
                        field = "mediaUri",
                        code = "duplicate_target",
                        message = "That media file is already attached.",
                    )
                } else {
                    null
                }
            }

            "REMOVE_MEDIA_CELL" -> if (files.none { file -> file.matchesAiMedia(action) }) {
                targetNotFound(
                    field = "mediaId",
                    targetKind = "media file",
                    targetLabel = action.mediaName.ifBlank { action.mediaId }.ifBlank { action.mediaUri },
                )
            } else {
                null
            }

            "CLEAR_MEDIA_CELL" -> if (files.isEmpty()) {
                invalidAction(
                    field = "columnName",
                    code = "already_applied",
                    message = "${column.name} is already empty.",
                )
            } else {
                null
            }

            else -> null
        }
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
        action.cellValues.forEach { (columnReference, value) ->
            val column = table.table.findColumnReference(columnReference) ?: return@forEach
            if (column.type == PageTableColumnType.Date) {
                invalidDateIssue("cellValues.${column.name}", value)?.let { issue -> return issue }
            }
        }
        return null
    }

    fun cellValuesColumnIssue(table: PageBlock): AiPageActionValidationIssue? {
        return action.cellValues.keys.firstNotNullOfOrNull { columnReference ->
            when (table.table.resolveColumnReference(columnReference)) {
                is AiColumnResolution.Found -> null
                AiColumnResolution.Ambiguous -> invalidAction(
                    field = "cellValues.$columnReference",
                    code = "ambiguous_target",
                    message = "More than one column matched $columnReference. Use the exact column name or column ID.",
                )
                AiColumnResolution.Missing -> targetNotFound(
                    field = "cellValues.$columnReference",
                    targetKind = "column",
                    targetLabel = columnReference,
                )
            }
        }
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
        return when (row.blocks.resolveMatchingBlock(effectiveAction)) {
            is AiBlockResolution.Found -> null
            AiBlockResolution.Ambiguous -> invalidAction(
                field = "rowBlockId",
                code = "ambiguous_target",
                message = "More than one row content block matched. Specify the exact block ID.",
            )
            AiBlockResolution.Missing -> targetNotFound(
                field = "rowBlockId",
                targetKind = "row content block",
                targetLabel = action.blockText.ifBlank { action.content }.ifBlank { action.title },
            )
        }
    }

    fun reminderDateColumnIssue(table: PageBlock): AiPageActionValidationIssue? {
        val requestedName = action.columnName.ifBlank { action.propertyName }
        if (action.columnId.isNotBlank() || requestedName.isNotBlank()) {
            missingColumnIssue(
                table = table,
                field = "columnName",
                columnId = action.columnId,
                columnName = requestedName,
            )?.let { issue -> return issue }
            val column = table.table.findColumn(action.columnId, requestedName)
                ?: return targetNotFound(
                    field = "columnName",
                    targetKind = "date column",
                    targetLabel = requestedName.ifBlank { action.columnId },
                )
            return if (column.type == PageTableColumnType.Date) {
                null
            } else {
                invalidAction(
                    field = "columnName",
                    code = "invalid_target_type",
                    message = "${column.name} is not a Date column.",
                )
            }
        }

        val dateColumns = table.table.columns.filter { column -> column.type == PageTableColumnType.Date }
        if (dateColumns.size <= 1) {
            return if (dateColumns.isEmpty()) {
                targetNotFound(
                    field = "columnName",
                    targetKind = "date column",
                    targetLabel = "",
                )
            } else {
                null
            }
        }
        return if (dateColumns.count { column -> column.name.equals("Date", ignoreCase = true) } == 1) {
            null
        } else {
            invalidAction(
                field = "columnName",
                code = "ambiguous_target",
                message = "More than one Date column matched. Specify the exact column name or column ID.",
            )
        }
    }

    return when (action.type.normalizedActionType()) {
        "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> missingMediaPayloadIssue()

        "DELETE_BLOCK",
        "MOVE_BLOCK",
        "INDENT_BLOCK",
        "OUTDENT_BLOCK",
        "DUPLICATE_BLOCK",
        "FORMAT_BLOCK_TEXT",
        "UPDATE_BLOCK",
        "EDIT_BLOCK",
        "UPDATE_TODO",
        "CHECK_BLOCK",
        "UNCHECK_BLOCK",
        -> {
            when (blocks.resolveMatchingBlock(action)) {
                is AiBlockResolution.Found -> null
                AiBlockResolution.Ambiguous -> invalidAction(
                    field = "blockText",
                    code = "ambiguous_target",
                    message = "More than one block matched. Specify the exact block ID.",
                )
                AiBlockResolution.Missing -> targetNotFound(
                    field = "blockText",
                    targetKind = "block",
                    targetLabel = action.blockText.ifBlank { action.title }.ifBlank { action.content },
                )
            }
        }

        "UPDATE_PROPERTY", "DELETE_PROPERTY", "RENAME_PROPERTY", "MOVE_PROPERTY", "DUPLICATE_PROPERTY" -> {
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

        "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE", "DUPLICATE_DATABASE",
        "ATTACH_TABLE_DATA_SOURCE", "CLEAR_TABLE_DATA_SOURCE",
        "ADD_TABLE_COLUMN",
        "CHANGE_TABLE_VIEW", "SET_TABLE_VIEW",
        "CLEAR_TABLE_SORT", "CLEAR_TABLE_FILTER", "CLEAR_TABLE_GROUP" -> tableIssue()

        "CREATE_TABLE_SAVED_VIEW", "RENAME_TABLE_SAVED_VIEW",
        "DELETE_TABLE_SAVED_VIEW", "ACTIVATE_TABLE_SAVED_VIEW" -> {
            val table = targetTable() ?: return tableIssue()
            savedViewIssue(table)
        }

        "ADD_TABLE_ROW" -> {
            val table = targetTable() ?: return tableIssue()
            cellValuesColumnIssue(table) ?: addedRowDateCellIssue(table)
        }

        "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG" -> {
            val table = targetTable() ?: return tableIssue()
            viewConfigColumnIssue(table) ?: viewConfigTypeIssue(table)
        }

        "DELETE_TABLE_COLUMN", "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN",
        "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE",
        "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG", "UPDATE_TABLE_DATE_CONFIG",
        "UPDATE_FORMULA_COLUMN", "UPDATE_RELATION_COLUMN", "UPDATE_ROLLUP_COLUMN",
        "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN", "DUPLICATE_TABLE_COLUMN",
        "SORT_TABLE", "SET_TABLE_SORT",
        "GROUP_TABLE", "SET_TABLE_GROUP" -> {
            val table = targetTable() ?: return tableIssue()
            columnIssue(table) ?: columnConfigIssue(table)
        }

        "ADD_TABLE_COLUMN_OPTION", "UPDATE_TABLE_COLUMN_OPTION", "DELETE_TABLE_COLUMN_OPTION" -> {
            val table = targetTable() ?: return tableIssue()
            columnIssue(table) ?: columnOptionIssue(table)
        }

        "FILTER_TABLE", "SET_TABLE_FILTER" -> {
            val table = targetTable() ?: return tableIssue()
            columnIssue(table) ?: filterRuleIssue(table)
        }

        "DELETE_TABLE_ROW",
        "REORDER_TABLE_ROW", "MOVE_TABLE_ROW", "DUPLICATE_TABLE_ROW",
        "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table)
        }

        "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table) ?: cellValuesColumnIssue(table)
        }

        "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK",
        "CHECK_ROW_PAGE_BLOCK", "UNCHECK_ROW_PAGE_BLOCK",
        "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" -> {
            val table = targetTable() ?: return tableIssue()
            rowPageBlockIssue(table)
        }

        "UPDATE_TABLE_CELL", "CLEAR_TABLE_CELL" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table) ?: columnIssue(table) ?: run {
                val column = targetColumn(table) ?: return columnIssue(table)
                if (column.type in setOf(PageTableColumnType.Relation, PageTableColumnType.FilesMedia)) {
                    invalidAction(
                        field = "columnName",
                        code = "typed_action_required",
                        message = "${column.type.name} cells require their dedicated typed action.",
                    )
                } else {
                    tableDateCellIssue(table)
                }
            }
        }

        "SET_RELATION_CELL", "CLEAR_RELATION_CELL" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table) ?: columnIssue(table) ?: relationCellIssue(table)
        }

        "ADD_MEDIA_CELL", "REMOVE_MEDIA_CELL", "CLEAR_MEDIA_CELL" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table) ?: columnIssue(table) ?: mediaCellIssue(table)
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

        "DELETE_TABLE_ROWS", "UPDATE_TABLE_ROWS" -> {
            val table = targetTable() ?: return tableIssue()
            if (action.rowIds.isEmpty()) {
                missingColumnIssue(
                    table = table,
                    field = "columnName",
                    columnId = action.columnId,
                    columnName = action.columnName.ifBlank { action.propertyName },
                )?.let { issue -> return issue }
            }
            val rows = table.table.resolveBulkRows(action)
            if (rows.isEmpty()) {
                targetNotFound(
                    field = if (action.rowIds.isNotEmpty()) "rowIds" else "filterQuery",
                    targetKind = "rows",
                    targetLabel = action.filterQuery.ifBlank { action.rowIds.joinToString() },
                )
            } else if (action.type.normalizedActionType() == "UPDATE_TABLE_ROWS") {
                cellValuesColumnIssue(table) ?: action.cellValues.entries.firstNotNullOfOrNull {
                        (columnReference, value) ->
                    val column = table.table.findColumnReference(columnReference)
                        ?: return@firstNotNullOfOrNull null
                    if (column.type == PageTableColumnType.Date) {
                        invalidDateIssue("cellValues.${column.name}", value)
                    } else {
                        null
                    }
                }
            } else {
                null
            }
        }

        "CREATE_TASK" -> taskDateIssue()

        "CREATE_REMINDER" -> missingReminderDateIssue()

        "CANCEL_REMINDER", "COMPLETE_REMINDER" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table) ?: reminderDateColumnIssue(table)
        }

        "RESCHEDULE_REMINDER" -> {
            val table = targetTable() ?: return tableIssue()
            rowIssue(table)
                ?: reminderDateColumnIssue(table)
                ?: missingReminderDateIssue()
        }

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
    return (blocks.resolveTableByTitle(tableTitle) as? AiTableResolution.Found)?.table?.id
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

private fun List<PageBlock>.resolveMatchingTable(action: ChatAction): AiTableResolution {
    val tableBlocks = collectAiTableBlocks()
    if (action.blockId.isNotBlank()) {
        return tableBlocks.resolveUniqueTable { block -> block.id == action.blockId }
    }
    val actionType = action.normalizedExecutionType()
    val tableName = if (actionType in cellTargetActionTypes) {
        action.tableTitle
    } else {
        action.tableTitle.ifBlank { action.title }
    }
    if (tableName.isNotBlank()) {
        tableBlocks.resolveUniqueTable { block ->
            block.table.title.equals(tableName, ignoreCase = true)
        }.unlessMissing()?.let { return it }
        return tableBlocks.resolveUniqueTable { block ->
            block.table.title.contains(tableName, ignoreCase = true) ||
                tableName.contains(block.table.title, ignoreCase = true)
        }
    }
    if (actionType == "ADD_TABLE_ROW" && tableName.isBlank()) {
        tableBlocks.resolveUniqueTable { block -> block.isTransactionLedgerTable() }
            .unlessMissing()
            ?.let { return it }
    }
    val databaseTables = tableBlocks.filter { block -> block.type == PageBlockType.DatabaseTable }
    if (actionType in cellTargetActionTypes && tableName.isBlank()) {
        val columnName = action.columnName.ifBlank { action.propertyName }
        val matchingTables = when (actionType) {
            "CLEAR_TABLE_CELLS" -> {
                val matchQuery = action.bulkCellMatchQuery()
                databaseTables.filter { block ->
                    val column = block.table.findColumn(action.columnId, columnName)
                    column != null &&
                        matchQuery.isNotBlank() &&
                        block.table.rowsMatchingCell(column, matchQuery).isNotEmpty()
                }
            }

            "DELETE_TABLE_ROWS", "UPDATE_TABLE_ROWS" -> {
                databaseTables.filter { block ->
                    block.table.resolveBulkRows(action).isNotEmpty()
                }
            }

            "CANCEL_REMINDER", "RESCHEDULE_REMINDER", "COMPLETE_REMINDER" -> {
                val rowTitle = action.rowTitle.ifBlank { action.title }
                databaseTables.filter { block ->
                    block.table.resolveRow(action.rowId, rowTitle) is AiRowResolution.Found &&
                        block.table.resolveReminderDateColumn(action) != null
                }
            }

            else -> {
                val rowTitle = action.rowTitle.ifBlank { action.title }
                databaseTables.filter { block ->
                    block.table.resolveRow(action.rowId, rowTitle) is AiRowResolution.Found &&
                        block.table.findColumn(action.columnId, columnName) != null
                }
            }
        }
        matchingTables.toTableResolution().unlessMissing()?.let { return it }
        if (databaseTables.size > 1) return AiTableResolution.Ambiguous
    }
    return when {
        databaseTables.isNotEmpty() -> databaseTables.toTableResolution()
        else -> tableBlocks.toTableResolution()
    }
}

private fun List<PageBlock>.resolveTableByTitle(tableTitle: String): AiTableResolution {
    val databaseTables = collectAiTableBlocks()
        .filter { block -> block.type == PageBlockType.DatabaseTable }
    val exact = databaseTables.resolveUniqueTable { block ->
        block.table.title.equals(tableTitle, ignoreCase = true)
    }
    if (exact != AiTableResolution.Missing) return exact
    return databaseTables.resolveUniqueTable { block ->
        block.table.title.contains(tableTitle, ignoreCase = true) ||
            tableTitle.contains(block.table.title, ignoreCase = true)
    }
}

private fun List<PageBlock>.findMatchingTable(action: ChatAction): PageBlock? =
    (resolveMatchingTable(action) as? AiTableResolution.Found)?.table

private val cellTargetActionTypes = setOf(
    "UPDATE_TABLE_CELL",
    "CLEAR_TABLE_CELL",
    "SET_RELATION_CELL",
    "CLEAR_RELATION_CELL",
    "ADD_MEDIA_CELL",
    "REMOVE_MEDIA_CELL",
    "CLEAR_MEDIA_CELL",
    "CLEAR_TABLE_CELLS",
    "DELETE_TABLE_ROWS",
    "UPDATE_TABLE_ROWS",
    "CANCEL_REMINDER",
    "RESCHEDULE_REMINDER",
    "COMPLETE_REMINDER",
)

private fun List<PageBlock>.resolveMatchingBlock(action: ChatAction): AiBlockResolution {
    val matches = buildList {
        collectMatchingBlocks(action, this)
    }
    return when (matches.size) {
        0 -> AiBlockResolution.Missing
        1 -> AiBlockResolution.Found(matches.single())
        else -> AiBlockResolution.Ambiguous
    }
}

private fun List<PageBlock>.collectMatchingBlocks(
    action: ChatAction,
    destination: MutableList<PageBlock>,
) {
    forEach { block ->
        if (block.matchesBlockAction(action)) destination += block
        block.children.collectMatchingBlocks(action, destination)
    }
}

private fun List<PageBlock>.findMatchingBlock(action: ChatAction): PageBlock? =
    (resolveMatchingBlock(action) as? AiBlockResolution.Found)?.block

private fun PageBlock.matchesBlockAction(action: ChatAction): Boolean {
    if (action.blockId.isNotBlank()) return id == action.blockId
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

private fun ChatAction.resolvedTableCellUpdateValue(column: PageTableColumn): String {
    if (value.isNotBlank()) return value
    if (content.isNotBlank()) return content

    return cellValues.entries
        .firstOrNull { entry ->
            entry.key == column.id ||
                entry.key.normalizedAiKey() == column.name.normalizedAiKey()
        }
        ?.value
        ?: cellValues.values.singleOrNull()
        .orEmpty()
}

private fun PageTable.newRowFromAction(action: ChatAction): PageTableRow {
    val title = action.rowTitle.ifBlank { action.title }.ifBlank { action.content }
    val values = action.cellValues.toMutableMap()
    val firstColumn = columns.firstOrNull()
    if (title.isNotBlank() && firstColumn != null) {
        val hasFirstColumnValue = values.keys.any { reference ->
            findColumnReference(reference)?.id == firstColumn.id
        }
        if (!hasFirstColumnValue) values[firstColumn.name] = title
    }
    return columns.newRow(values)
}

private fun List<PageTableColumn>.newRow(valuesByColumnName: Map<String, String>): PageTableRow {
    val tableShape = PageTable(columns = this)
    val requestedValuesByColumnId = buildMap {
        valuesByColumnName.forEach { (columnReference, value) ->
            tableShape.findColumnReference(columnReference)?.let { column ->
                put(column.id, value)
            }
        }
    }
    val cellsByColumnId = associate { column ->
        column.id to requestedValuesByColumnId[column.id].orEmpty()
    }
    return PageBlockCodec.newTableRow(this).copy(
        cells = cellsByColumnId,
        cellValues = associate { column ->
            val displayValue = cellsByColumnId[column.id].orEmpty()
            column.id to column.toTypedCellValue(displayValue)
        },
    )
}

private fun PageTable.findColumn(columnId: String = "", columnName: String): PageTableColumn? {
    return (resolveColumn(columnId, columnName) as? AiColumnResolution.Found)?.column
}

private fun PageTable.resolveColumn(
    columnId: String = "",
    columnName: String,
): AiColumnResolution {
    val requestedId = columnId.trim()
    if (requestedId.isNotBlank()) {
        return columns
            .filter { column -> column.id == requestedId }
            .toColumnResolution()
    }

    val normalizedName = columnName.normalizedAiKey()
    if (normalizedName.isBlank()) return AiColumnResolution.Missing

    val exact = columns
        .filter { column -> column.name.normalizedAiKey() == normalizedName }
        .toColumnResolution()
    if (exact != AiColumnResolution.Missing) return exact

    return columns
        .filter { column ->
            val currentName = column.name.normalizedAiKey()
            currentName.isNotBlank() &&
                (currentName.contains(normalizedName) || normalizedName.contains(currentName))
        }
        .toColumnResolution()
}

private fun PageTable.resolveColumnReference(reference: String): AiColumnResolution {
    val normalizedReference = reference.trim()
    if (normalizedReference.isBlank()) return AiColumnResolution.Missing
    val idResolution = columns
        .filter { column -> column.id == normalizedReference }
        .toColumnResolution()
    return if (idResolution == AiColumnResolution.Missing) {
        resolveColumn(columnName = normalizedReference)
    } else {
        idResolution
    }
}

private fun PageTable.findColumnReference(reference: String): PageTableColumn? =
    (resolveColumnReference(reference) as? AiColumnResolution.Found)?.column

private fun PageTable.findRow(rowId: String = "", rowTitle: String): PageTableRow? {
    return (resolveRow(rowId = rowId, rowTitle = rowTitle) as? AiRowResolution.Found)?.row
}

private fun PageTable.resolveBulkRows(action: ChatAction): List<PageTableRow> {
    val requestedRowIds = action.rowIds.map(String::trim).filter(String::isNotBlank).distinct()
    if (requestedRowIds.isNotEmpty()) {
        val rowsById = rows.associateBy(PageTableRow::id)
        if (requestedRowIds.any { rowId -> rowId !in rowsById }) return emptyList()
        return requestedRowIds.mapNotNull(rowsById::get)
    }
    val columnName = action.columnName.ifBlank { action.propertyName }
    val column = findColumn(action.columnId, columnName) ?: return emptyList()
    val query = action.filterQuery.ifBlank { action.value }.ifBlank { action.content }
    if (query.isBlank()) return emptyList()
    return rowsMatchingCell(column, query)
}

private fun PageTable.resolveReminderDateColumn(action: ChatAction): PageTableColumn? {
    val requestedName = action.columnName.ifBlank { action.propertyName }
    if (action.columnId.isNotBlank() || requestedName.isNotBlank()) {
        return findColumn(action.columnId, requestedName)
            ?.takeIf { column -> column.type == PageTableColumnType.Date }
    }
    val dateColumns = columns.filter { column -> column.type == PageTableColumnType.Date }
    return dateColumns.singleOrNull()
        ?: dateColumns
            .filter { column -> column.name.equals("Date", ignoreCase = true) }
            .singleOrNull()
}

private fun PageTableRow.primaryTitle(table: PageTable): String {
    return table.columns.firstOrNull()
        ?.let { column -> cells[column.id].orEmpty().trim() }
        .orEmpty()
        .ifBlank { id }
}

private fun String.toBlockMoveDirection(): Int {
    return when (trim().lowercase()) {
        "up", "above", "previous", "-1" -> -1
        "down", "below", "next", "1" -> 1
        else -> error("Move block direction must be up or down")
    }
}

private fun List<Page>.resolveSourcePage(action: ChatAction): Page? {
    if (action.sourcePageId.isNotBlank()) {
        return firstOrNull { page -> page.id == action.sourcePageId }
    }
    val requestedTitle = action.sourcePageTitle.trim().removePrefix("@").trim()
    if (requestedTitle.isBlank()) return null
    return filter { page -> page.title.equals(requestedTitle, ignoreCase = true) }.singleOrNull()
}

private fun PageBlockDocument.resolveSourceTable(action: ChatAction): PageBlock? {
    val tables = blocks.collectAiTableBlocks()
    if (action.sourceTableBlockId.isNotBlank()) {
        return tables.firstOrNull { table -> table.id == action.sourceTableBlockId }
    }
    val requestedTitle = action.sourceTableTitle.trim()
    if (requestedTitle.isNotBlank()) {
        return tables.filter { table ->
            table.table.title.equals(requestedTitle, ignoreCase = true)
        }.singleOrNull()
    }
    return tables.singleOrNull()
}

private fun List<Page>.resolveParentPageId(
    action: ChatAction,
    movingPage: Page,
): String? {
    val requestedId = action.parentPageId.trim()
    val requestedTitle = action.parentPageTitle.trim().removePrefix("@").trim()
    val targetsRoot = requestedId.isBlank() &&
        (
            requestedTitle.isBlank() ||
                requestedTitle.equals("root", ignoreCase = true) ||
                requestedTitle.equals("workspace", ignoreCase = true)
            )
    if (targetsRoot) return null

    val parent = when {
        requestedId.isNotBlank() -> firstOrNull { page -> page.id == requestedId }
        else -> filter { page -> page.title.equals(requestedTitle, ignoreCase = true) }.singleOrNull()
    } ?: error("Could not find the requested parent page")
    if (parent.id == movingPage.id) error("A page cannot be moved under itself")

    val pagesById = associateBy(Page::id)
    var cursor: Page? = parent
    while (cursor != null) {
        if (cursor.id == movingPage.id) {
            error("A page cannot be moved under one of its descendants")
        }
        cursor = cursor.parentPageId?.let(pagesById::get)
    }
    return parent.id
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

private sealed interface AiTableResolution {
    data class Found(val table: PageBlock) : AiTableResolution
    data object Missing : AiTableResolution
    data object Ambiguous : AiTableResolution
}

private fun AiTableResolution.unlessMissing(): AiTableResolution? =
    takeUnless { resolution -> resolution == AiTableResolution.Missing }

private inline fun List<PageBlock>.resolveUniqueTable(
    predicate: (PageBlock) -> Boolean,
): AiTableResolution = filter(predicate).toTableResolution()

private fun List<PageBlock>.toTableResolution(): AiTableResolution = when (size) {
    0 -> AiTableResolution.Missing
    1 -> AiTableResolution.Found(single())
    else -> AiTableResolution.Ambiguous
}

private sealed interface AiBlockResolution {
    data class Found(val block: PageBlock) : AiBlockResolution
    data object Missing : AiBlockResolution
    data object Ambiguous : AiBlockResolution
}

private sealed interface AiColumnResolution {
    data class Found(val column: PageTableColumn) : AiColumnResolution
    data object Missing : AiColumnResolution
    data object Ambiguous : AiColumnResolution
}

private fun List<PageTableColumn>.toColumnResolution(): AiColumnResolution = when (size) {
    0 -> AiColumnResolution.Missing
    1 -> AiColumnResolution.Found(single())
    else -> AiColumnResolution.Ambiguous
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

private fun PageTableColumn.withActionConfig(
    action: ChatAction,
    relationColumn: PageTableColumn? = null,
    resolvedRollupTargetColumnId: String = "",
): PageTableColumn {
    val configuredOptions = action.options
        .toPreservedAiTableSelectOptions(config.options)
        .takeIf { options -> options.isNotEmpty() }
        ?: config.options
    val configuredDefaultValue = when {
        action.clearDefaultValue == true -> ""
        action.defaultValue.isNotBlank() -> action.defaultValue
        else -> config.defaultValue
    }
    val configuredDescription = when {
        action.clearDescription == true -> ""
        action.description.isNotBlank() -> action.description
        else -> config.description
    }
    val configuredColumn = config.copy(
        options = configuredOptions,
        isHidden = action.isHidden ?: config.isHidden,
        isRequired = action.isRequired ?: config.isRequired,
        wrapContent = action.wrapContent ?: config.wrapContent,
        widthDp = action.widthDp ?: config.widthDp,
        defaultValue = configuredDefaultValue,
        description = configuredDescription,
    ).normalizedForType(type)
    return copy(
        config = configuredColumn,
        dateFormat = action.dateFormat.toPageTableDateFormatOrNull() ?: dateFormat,
        timeFormat = action.timeFormat.toPageTableTimeFormatOrNull() ?: timeFormat,
        dateReminder = action.dateReminder.toPageTableDateReminderOrNull() ?: dateReminder,
        timezoneLabel = action.timezoneLabel.ifBlank { timezoneLabel },
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

private fun List<String>.toPreservedAiTableSelectOptions(
    existing: List<PageTableSelectOption>,
): List<PageTableSelectOption> {
    val generated = toAiTableSelectOptions()
    return generated.map { option ->
        existing.firstOrNull { current -> current.name.equals(option.name, ignoreCase = true) }
            ?.copy(name = option.name)
            ?: option.copy(id = UUID.randomUUID().toString())
    }
}

private fun List<PageTableSelectOption>.findAiOption(action: ChatAction): PageTableSelectOption? {
    if (action.optionId.isNotBlank()) {
        firstOrNull { option -> option.id == action.optionId }?.let { return it }
    }
    return firstOrNull { option -> option.name.equals(action.optionName, ignoreCase = true) }
}

private fun PageMediaAttachment.matchesAiMedia(action: ChatAction): Boolean {
    return (action.mediaId.isNotBlank() && id == action.mediaId) ||
        (action.mediaUri.isNotBlank() && uri == action.mediaUri) ||
        (action.mediaName.isNotBlank() && name.equals(action.mediaName, ignoreCase = true))
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

private fun String.toPageTableFilterOperator(): PageTableFilterOperator {
    return when (normalizedAiKey()) {
        "equals", "equal", "is", "eq" -> PageTableFilterOperator.Equals
        "notequals", "isnot", "neq" -> PageTableFilterOperator.NotEquals
        "notcontains", "doesnotcontain", "excludes" -> PageTableFilterOperator.NotContains
        "isempty", "empty", "blank" -> PageTableFilterOperator.IsEmpty
        "isnotempty", "notempty", "notblank" -> PageTableFilterOperator.IsNotEmpty
        "greaterthan", "greater", "morethan", "above" -> PageTableFilterOperator.GreaterThan
        "greaterthanorequal", "greaterthanorequals", "atleast", "gte" ->
            PageTableFilterOperator.GreaterThanOrEqual
        "lessthan", "less", "below" -> PageTableFilterOperator.LessThan
        "lessthanorequal", "lessthanorequals", "atmost", "lte" ->
            PageTableFilterOperator.LessThanOrEqual
        "before" -> PageTableFilterOperator.Before
        "after" -> PageTableFilterOperator.After
        "onorbefore", "beforeorequal" -> PageTableFilterOperator.OnOrBefore
        "onorafter", "afterorequal" -> PageTableFilterOperator.OnOrAfter
        else -> PageTableFilterOperator.Contains
    }
}

private fun String.toPageTableDateFormatOrNull(): PageTableDateFormat? {
    if (isBlank()) return null
    return when (normalizedAiKey()) {
        "daymonthyear", "ddmmyyyy" -> PageTableDateFormat.DayMonthYear
        "monthdayyear", "mmddyyyy" -> PageTableDateFormat.MonthDayYear
        "yearmonthday", "yyyymmdd", "iso" -> PageTableDateFormat.YearMonthDay
        else -> null
    }
}

private fun String.toPageTableTimeFormatOrNull(): PageTableTimeFormat? {
    if (isBlank()) return null
    return when (normalizedAiKey()) {
        "hidden", "none", "off" -> PageTableTimeFormat.Hidden
        "twelvehour", "12hour" -> PageTableTimeFormat.TwelveHour
        "twentyfourhour", "24hour" -> PageTableTimeFormat.TwentyFourHour
        else -> null
    }
}

private fun String.toPageTableDateReminderOrNull(): PageTableDateReminder? {
    if (isBlank()) return null
    return when (normalizedAiKey()) {
        "none", "off" -> PageTableDateReminder.None
        "attimeofevent", "attime", "eventtime" -> PageTableDateReminder.AtTimeOfEvent
        "fiveminutesbefore", "5minutesbefore", "5minbefore" -> PageTableDateReminder.FiveMinutesBefore
        "tenminutesbefore", "10minutesbefore", "10minbefore" -> PageTableDateReminder.TenMinutesBefore
        "fifteenminutesbefore", "15minutesbefore", "15minbefore" -> PageTableDateReminder.FifteenMinutesBefore
        "thirtyminutesbefore", "30minutesbefore", "30minbefore" -> PageTableDateReminder.ThirtyMinutesBefore
        "onehourbefore", "1hourbefore" -> PageTableDateReminder.OneHourBefore
        "twohoursbefore", "2hoursbefore" -> PageTableDateReminder.TwoHoursBefore
        "ondayofevent", "sameday", "eventday" -> PageTableDateReminder.OnDayOfEvent
        "onedaybefore", "1daybefore" -> PageTableDateReminder.OneDayBefore
        "twodaysbefore", "2daysbefore" -> PageTableDateReminder.TwoDaysBefore
        "oneweekbefore", "1weekbefore" -> PageTableDateReminder.OneWeekBefore
        else -> null
    }
}

private fun String.toPageTableOptionColorOrNull(): PageTableOptionColor? {
    if (isBlank()) return null
    return PageTableOptionColor.entries.firstOrNull { color ->
        color.name.equals(trim(), ignoreCase = true)
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

private fun List<Page>.withRootSnapshot(root: Page): List<Page> {
    if (isEmpty()) return listOf(root)
    var replaced = false
    val snapshots = map { page ->
        if (page.id == root.id) {
            replaced = true
            root
        } else {
            page
        }
    }
    return if (replaced) snapshots else listOf(root) + snapshots
}

private fun Page.toChatPageLink(): AiChatPageLink {
    return AiChatPageLink(pageId = id, title = title.ifBlank { "Untitled page" })
}
