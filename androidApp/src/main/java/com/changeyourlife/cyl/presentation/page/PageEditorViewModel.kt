package com.changeyourlife.cyl.presentation.page

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.repository.AiBlockContext
import com.changeyourlife.cyl.domain.repository.AiPageContext
import com.changeyourlife.cyl.domain.repository.AiRepository
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.presentation.ai.AiChatPageLink
import com.changeyourlife.cyl.presentation.ai.AiPageActionExecutor
import com.changeyourlife.cyl.presentation.ai.isTaskTableRowAction
import com.changeyourlife.cyl.presentation.ai.toPageTableColumnFromAi
import com.changeyourlife.cyl.presentation.ai.withTaskTableAction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val MaxEditorUndoSnapshots = 40
private const val TableCheckboxCheckedValue = "true"

enum class TableColumnInsertSide {
    Left,
    Right,
}

@HiltViewModel
class PageEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pageRepository: PageRepository,
    private val aiRepository: AiRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val aiPageActionExecutor: AiPageActionExecutor,
) : ViewModel() {
    private val pageId: String = checkNotNull(savedStateHandle["pageId"])

    private val _pendingChanges = MutableStateFlow<PageBlockDocument?>(null)
    private val _pendingGranularDocumentSave = MutableStateFlow<PendingGranularDocumentSave?>(null)
    private val _pendingTitle = MutableStateFlow<String?>(null)
    private val _canUndoEditorChange = MutableStateFlow(false)
    private val _isAiGenerating = MutableStateFlow(false)
    private val _aiError = MutableStateFlow<String?>(null)
    private val editorUndoSnapshots = ArrayDeque<PageBlockDocument>()

    private val _dbState = combine(
        pageRepository.observePage(pageId),
        pageRepository.observeChildPages(pageId),
        pageRepository.observePageSyncState(pageId),
    ) { page, childPages, syncState ->
        if (page == null) {
            PageEditorUiState(
                isLoading = false,
                syncState = syncState,
            )
        } else {
            val document = PageBlockCodec.decodeDocument(page.content)
            PageEditorUiState(
                isLoading = false,
                page = page,
                title = page.title,
                properties = document.properties,
                blocks = document.blocks,
                childPages = childPages,
                syncState = syncState,
            )
        }
    }

    private val _aiState = combine(
        _isAiGenerating,
        _aiError,
    ) { isGenerating, aiError ->
        PageEditorAiState(
            isAiGenerating = isGenerating,
            aiError = aiError,
        )
    }

    val uiState: StateFlow<PageEditorUiState> = combine(
        _dbState,
        _pendingChanges,
        _pendingTitle,
        _aiState,
        _canUndoEditorChange,
    ) { dbState, pendingDoc, pendingTitle, aiState, canUndoEditorChange ->
        if (dbState.page != null) {
            dbState.copy(
                title = pendingTitle ?: dbState.title,
                properties = pendingDoc?.properties ?: dbState.properties,
                blocks = pendingDoc?.blocks ?: dbState.blocks,
                isSaving = pendingDoc != null || pendingTitle != null,
                canUndoEditorChange = canUndoEditorChange,
                isAiGenerating = aiState.isAiGenerating,
                aiError = aiState.aiError,
            )
        } else {
            dbState.copy(
                isSaving = pendingDoc != null || pendingTitle != null,
                canUndoEditorChange = canUndoEditorChange,
                isAiGenerating = aiState.isAiGenerating,
                aiError = aiState.aiError,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PageEditorUiState(isLoading = true),
    )

    init {
        setupPendingChangesCollection()
        setupPendingTitleCollection()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun setupPendingChangesCollection() {
        viewModelScope.launch {
            _pendingChanges
                .debounce(300)
                .collect { pendingDoc ->
                    if (pendingDoc != null) {
                        savePendingDocument(pendingDoc)
                    }
                }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun setupPendingTitleCollection() {
        viewModelScope.launch {
            _pendingTitle
                .debounce(300)
                .collect { pendingTitle ->
                    if (pendingTitle != null) {
                        savePendingTitle(pendingTitle)
                    }
                }
        }
    }

    override fun onCleared() {
        runBlocking {
            flushPendingEdits()
        }
        super.onCleared()
    }

    private suspend fun flushPendingEdits() {
        _pendingChanges.value?.let { pendingDoc ->
            savePendingDocument(pendingDoc)
        }
        _pendingTitle.value?.let { pendingTitle ->
            savePendingTitle(pendingTitle)
        }
    }

    private suspend fun savePendingDocument(pendingDoc: PageBlockDocument) {
        if (savePendingGranularDocument(pendingDoc)) return

        val page = pageRepository.getPage(pageId) ?: return
        val normalizedDoc = pendingDoc.normalizedForEditor()
        pageRepository.upsertPage(
            page.copy(
                content = PageBlockCodec.encodeDocument(normalizedDoc),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        _pendingGranularDocumentSave.value = null
        if (_pendingChanges.value == pendingDoc) {
            _pendingChanges.value = null
        }
    }

    private suspend fun savePendingGranularDocument(pendingDoc: PageBlockDocument): Boolean {
        val pendingSave = _pendingGranularDocumentSave.value ?: return false
        val normalizedDoc = pendingDoc.normalizedForEditor()
        if (pendingSave.document != normalizedDoc) return false

        val page = pageRepository.getPage(pageId) ?: return false
        val baseDocument = PageBlockCodec.decodeDocument(page.content).normalizedForEditor()
        if (pendingSave.applyTo(baseDocument).normalizedForEditor() != normalizedDoc) {
            return false
        }

        val saved = when (pendingSave) {
            is PendingGranularDocumentSave.BlockText -> {
                pageRepository.updateBlockText(
                    pageId = pageId,
                    blockId = pendingSave.blockId,
                    text = pendingSave.text,
                )
            }

            is PendingGranularDocumentSave.BlockPatch -> {
                pageRepository.updateBlock(
                    pageId = pageId,
                    block = pendingSave.block,
                )
            }

            is PendingGranularDocumentSave.PropertyValue -> {
                pageRepository.updatePropertyValue(
                    pageId = pageId,
                    propertyId = pendingSave.propertyId,
                    value = pendingSave.value,
                )
            }

            is PendingGranularDocumentSave.TableCellValue -> {
                pageRepository.updateTableCellValue(
                    pageId = pageId,
                    rowId = pendingSave.rowId,
                    columnId = pendingSave.columnId,
                    value = pendingSave.value,
                )
            }

            is PendingGranularDocumentSave.TablePatch -> {
                pageRepository.updateTable(
                    pageId = pageId,
                    tableBlockId = pendingSave.tableBlockId,
                    table = pendingSave.table,
                )
            }

            is PendingGranularDocumentSave.TableColumnPatch -> {
                pageRepository.updateTableColumn(
                    pageId = pageId,
                    tableBlockId = pendingSave.tableBlockId,
                    column = pendingSave.column,
                )
            }

            is PendingGranularDocumentSave.TableRowPatch -> {
                pageRepository.updateTableRow(
                    pageId = pageId,
                    tableBlockId = pendingSave.tableBlockId,
                    row = pendingSave.row,
                )
            }
        }
        if (!saved) return false

        if (_pendingGranularDocumentSave.value == pendingSave) {
            _pendingGranularDocumentSave.value = null
        }
        if (_pendingChanges.value == pendingDoc || _pendingChanges.value == normalizedDoc) {
            _pendingChanges.value = null
        }
        return true
    }

    private fun PendingGranularDocumentSave.applyTo(document: PageBlockDocument): PageBlockDocument {
        return when (this) {
            is PendingGranularDocumentSave.BlockText -> {
                document.copy(
                    blocks = updateBlockById(document.blocks, blockId) { block ->
                        block.copy(text = text)
                    },
                )
            }

            is PendingGranularDocumentSave.BlockPatch -> {
                document.copy(
                    blocks = updateBlockById(document.blocks, block.id) { currentBlock ->
                        currentBlock.copy(
                            text = block.text,
                            richTextSpans = block.richTextSpans,
                            mediaAttachments = block.mediaAttachments,
                            isChecked = block.isChecked,
                        )
                    },
                )
            }

            is PendingGranularDocumentSave.PropertyValue -> {
                document.copy(
                    properties = document.properties.map { property ->
                        if (property.id == propertyId) {
                            property.copy(value = value)
                        } else {
                            property
                        }
                    },
                )
            }

            is PendingGranularDocumentSave.TableCellValue -> {
                document.copy(
                    blocks = updateTableCellValueInBlocks(
                        blocks = document.blocks,
                        rowId = rowId,
                        columnId = columnId,
                        value = value,
                    ),
                )
            }

            is PendingGranularDocumentSave.TablePatch -> {
                document.copy(
                    blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                        block.copy(
                            table = block.table.copy(
                                title = table.title,
                                view = table.view,
                                viewConfig = table.viewConfig,
                                sort = table.sort,
                                filter = table.filter,
                                groupByColumnId = table.groupByColumnId,
                            ),
                        )
                    },
                )
            }

            is PendingGranularDocumentSave.TableColumnPatch -> {
                document.copy(
                    blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                        block.copy(
                            table = block.table.copy(
                                columns = block.table.columns.map { existing ->
                                    if (existing.id == column.id) column else existing
                                },
                            ),
                        )
                    },
                )
            }

            is PendingGranularDocumentSave.TableRowPatch -> {
                document.copy(
                    blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                        block.copy(
                            table = block.table.copy(
                                rows = block.table.rows.map { existing ->
                                    if (existing.id == row.id) row else existing
                                },
                            ),
                        )
                    },
                )
            }
        }
    }

    private suspend fun savePendingTitle(pendingTitle: String) {
        val page = pageRepository.getPage(pageId) ?: return
        pageRepository.upsertPage(
            page.copy(
                title = pendingTitle,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        if (_pendingTitle.value == pendingTitle) {
            _pendingTitle.value = null
        }
    }

    private fun currentDocument(currentUiState: PageEditorUiState = uiState.value): PageBlockDocument? {
        if (currentUiState.page == null) return null
        return _pendingChanges.value
            ?: PageBlockDocument(
                properties = currentUiState.properties,
                blocks = currentUiState.blocks,
            ).normalizedForEditor()
    }

    private fun queueDocumentUpdate(
        updated: PageBlockDocument,
        previous: PageBlockDocument? = null,
        recordUndo: Boolean = false,
    ) {
        val normalized = updated.normalizedForEditor()
        _pendingGranularDocumentSave.value = null
        if (recordUndo && previous != null && previous.normalizedForEditor() != normalized) {
            pushEditorUndoSnapshot(previous)
        }
        _pendingChanges.value = normalized
    }

    private fun queueGranularPendingDocument(
        updated: PageBlockDocument,
        pendingSave: (PageBlockDocument) -> PendingGranularDocumentSave,
    ) {
        val normalized = updated.normalizedForEditor()
        _pendingGranularDocumentSave.value = pendingSave(normalized)
        _pendingChanges.value = normalized
    }

    private fun queueBlockPatchPendingDocument(
        blockId: String,
        updated: PageBlockDocument,
        previous: PageBlockDocument? = null,
        recordUndo: Boolean = false,
    ) {
        val normalized = updated.normalizedForEditor()
        val block = normalized.findBlock(blockId)
        if (block == null) {
            queueDocumentUpdate(updated, previous = previous, recordUndo = recordUndo)
            return
        }
        if (recordUndo && previous != null && previous.normalizedForEditor() != normalized) {
            pushEditorUndoSnapshot(previous)
        }
        _pendingGranularDocumentSave.value = PendingGranularDocumentSave.BlockPatch(
            document = normalized,
            block = block,
        )
        _pendingChanges.value = normalized
    }

    private fun queueTablePatchPendingDocument(
        tableBlockId: String,
        updated: PageBlockDocument,
        previous: PageBlockDocument? = null,
        recordUndo: Boolean = false,
    ) {
        val normalized = updated.normalizedForEditor()
        val table = normalized.findTableBlock(tableBlockId)?.table
        if (table == null) {
            queueDocumentUpdate(updated, previous = previous, recordUndo = recordUndo)
            return
        }
        if (recordUndo && previous != null && previous.normalizedForEditor() != normalized) {
            pushEditorUndoSnapshot(previous)
        }
        _pendingGranularDocumentSave.value = PendingGranularDocumentSave.TablePatch(
            document = normalized,
            tableBlockId = tableBlockId,
            table = table,
        )
        _pendingChanges.value = normalized
    }

    private fun queueTableColumnPatchPendingDocument(
        tableBlockId: String,
        columnId: String,
        updated: PageBlockDocument,
        previous: PageBlockDocument? = null,
        recordUndo: Boolean = false,
    ) {
        val normalized = updated.normalizedForEditor()
        val column = normalized
            .findTableBlock(tableBlockId)
            ?.table
            ?.columns
            ?.firstOrNull { tableColumn -> tableColumn.id == columnId }
        if (column == null) {
            queueDocumentUpdate(updated, previous = previous, recordUndo = recordUndo)
            return
        }
        if (recordUndo && previous != null && previous.normalizedForEditor() != normalized) {
            pushEditorUndoSnapshot(previous)
        }
        _pendingGranularDocumentSave.value = PendingGranularDocumentSave.TableColumnPatch(
            document = normalized,
            tableBlockId = tableBlockId,
            column = column,
        )
        _pendingChanges.value = normalized
    }

    private fun queueTableRowPatchPendingDocument(
        tableBlockId: String,
        rowId: String,
        updated: PageBlockDocument,
        previous: PageBlockDocument? = null,
        recordUndo: Boolean = false,
    ) {
        val normalized = updated.normalizedForEditor()
        val row = normalized
            .findTableBlock(tableBlockId)
            ?.table
            ?.rows
            ?.firstOrNull { tableRow -> tableRow.id == rowId }
        if (row == null) {
            queueDocumentUpdate(updated, previous = previous, recordUndo = recordUndo)
            return
        }
        if (recordUndo && previous != null && previous.normalizedForEditor() != normalized) {
            pushEditorUndoSnapshot(previous)
        }
        _pendingGranularDocumentSave.value = PendingGranularDocumentSave.TableRowPatch(
            document = normalized,
            tableBlockId = tableBlockId,
            row = row,
        )
        _pendingChanges.value = normalized
    }

    private fun queueGranularDocumentUpdate(
        previous: PageBlockDocument,
        fallback: PageBlockDocument,
        mutation: suspend () -> Boolean,
    ) {
        viewModelScope.launch {
            flushPendingEdits()
            if (mutation()) {
                pushEditorUndoSnapshot(previous)
            } else {
                queueDocumentUpdate(fallback, previous = previous, recordUndo = true)
            }
        }
    }

    private fun pushEditorUndoSnapshot(document: PageBlockDocument) {
        val normalized = document.normalizedForEditor()
        if (editorUndoSnapshots.lastOrNull() == normalized) return
        if (editorUndoSnapshots.size >= MaxEditorUndoSnapshots) {
            editorUndoSnapshots.removeFirst()
        }
        editorUndoSnapshots.addLast(normalized)
        _canUndoEditorChange.value = true
    }

    fun undoLastEditorChange() {
        if (editorUndoSnapshots.isEmpty()) return
        _pendingChanges.value = editorUndoSnapshots.removeLast().normalizedForEditor()
        _canUndoEditorChange.value = editorUndoSnapshots.isNotEmpty()
    }

    fun keepLocalConflict() {
        viewModelScope.launch {
            pageRepository.keepLocalPageConflict(pageId)
        }
    }

    fun useRemoteConflict() {
        _pendingChanges.value = null
        _pendingTitle.value = null
        viewModelScope.launch {
            pageRepository.useRemotePageConflict(pageId)
        }
    }

    fun updateTitle(title: String) {
        _pendingTitle.value = title
    }

    fun updateBlockText(blockId: String, text: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        text = text,
                        richTextSpans = block.richTextSpans.normalizedForText(text),
                    )
                },
            )
            queueGranularPendingDocument(updated) { normalized ->
                PendingGranularDocumentSave.BlockText(
                    document = normalized,
                    blockId = blockId,
                    text = text,
                )
            }
        }
    }

    fun updateBlockRichText(
        blockId: String,
        text: String,
        spans: List<PageTextSpan>,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        text = text,
                        richTextSpans = spans.normalizedForText(text),
                    )
                },
            )
            queueBlockPatchPendingDocument(blockId, updated)
        }
    }

    fun addBlockMediaAttachments(
        blockId: String,
        attachments: List<PageMediaAttachment>,
    ) {
        if (attachments.isEmpty()) return
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(mediaAttachments = block.mediaAttachments + attachments)
                },
            )
            queueBlockPatchPendingDocument(blockId, updated)
        }
    }

    fun removeBlockMediaAttachment(
        blockId: String,
        attachmentId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        mediaAttachments = block.mediaAttachments.filterNot { attachment ->
                            attachment.id == attachmentId
                        },
                    )
                },
            )
            queueBlockPatchPendingDocument(blockId, updated)
        }
    }

    fun toggleTodoBlock(blockId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(isChecked = !block.isChecked)
                },
            )
            queueBlockPatchPendingDocument(
                blockId = blockId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun addBlock(type: PageBlockType) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val newBlock = PageBlockCodec.newBlock(type)
            val updated = document.copy(
                blocks = document.blocks + newBlock,
            )
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.addBlock(
                    pageId = pageId,
                    block = newBlock,
                )
            }
        }
    }

    fun addChildBlock(parentBlockId: String, type: PageBlockType) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val newBlock = PageBlockCodec.newBlock(type)
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, parentBlockId) { existingBlock ->
                    existingBlock.copy(children = existingBlock.children + newBlock)
                },
            )
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.addBlock(
                    pageId = pageId,
                    block = newBlock,
                    parentBlockId = parentBlockId,
                )
            }
        }
    }

    fun deleteBlock(blockId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val blocks = deleteBlockRecursive(document.blocks, blockId)
                .ifEmpty { listOf(PageBlockCodec.newBlock(PageBlockType.Text)) }
            val updated = document.copy(blocks = blocks)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.deleteBlock(
                    pageId = pageId,
                    blockId = blockId,
                )
            }
        }
    }

    fun moveBlockUp(blockId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = moveBlockInBlocks(document.blocks, blockId, -1),
            )
            val targetIndex = document.blocks.findBlockMoveTargetIndex(blockId, -1) ?: return
            if (updated == document) return
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.moveBlock(
                    pageId = pageId,
                    blockId = blockId,
                    targetIndex = targetIndex,
                )
            }
        }
    }

    fun moveBlockDown(blockId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = moveBlockInBlocks(document.blocks, blockId, 1),
            )
            val targetIndex = document.blocks.findBlockMoveTargetIndex(blockId, 1) ?: return
            if (updated == document) return
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.moveBlock(
                    pageId = pageId,
                    blockId = blockId,
                    targetIndex = targetIndex,
                )
            }
        }
    }

    fun updateTableTitle(blockId: String, title: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(table = block.table.copy(title = title))
                },
            )
            queueTablePatchPendingDocument(blockId, updated)
        }
    }

    fun updateTableView(
        blockId: String,
        view: PageTableView,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(table = block.table.copy(view = view))
                },
            )
            queueTablePatchPendingDocument(blockId, updated)
        }
    }

    fun updateTableViewConfig(
        blockId: String,
        config: PageTableViewConfig,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(table = block.table.copy(viewConfig = config))
                },
            )
            queueTablePatchPendingDocument(blockId, updated)
        }
    }

    fun updateTableSort(
        blockId: String,
        columnId: String,
        direction: PageTableSortDirection,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            sort = if (columnId.isBlank()) {
                                PageTableSort()
                            } else {
                                PageTableSort(columnId = columnId, direction = direction)
                            },
                        ),
                    )
                },
            )
            queueTablePatchPendingDocument(blockId, updated)
        }
    }

    fun updateTableFilter(
        blockId: String,
        columnId: String,
        query: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            filter = if (columnId.isBlank() || query.isBlank()) {
                                PageTableFilter()
                            } else {
                                PageTableFilter(columnId = columnId, query = query)
                            },
                        ),
                    )
                },
            )
            queueTablePatchPendingDocument(blockId, updated)
        }
    }

    fun updateTableGroup(
        blockId: String,
        columnId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(table = block.table.copy(groupByColumnId = columnId))
                },
            )
            queueTablePatchPendingDocument(blockId, updated)
        }
    }

    fun updateTableColumnName(
        blockId: String,
        columnId: String,
        name: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            columns = block.table.columns.map { column ->
                                if (column.id == columnId) {
                                    column.copy(name = name)
                                } else {
                                    column
                                }
                            },
                        ),
                    )
                },
            )
            queueTableColumnPatchPendingDocument(blockId, columnId, updated)
        }
    }

    fun updateTableColumnType(
        blockId: String,
        columnId: String,
        type: PageTableColumnType,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            columns = block.table.columns.map { column ->
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
                            rows = block.table.rows.map { row ->
                                row.copy(
                                    cells = row.cells + (
                                        columnId to type.coerceExistingCellValue(row.cells[columnId].orEmpty())
                                        ),
                                )
                            },
                        ),
                    )
                },
            )
            queueTableColumnPatchPendingDocument(
                tableBlockId = blockId,
                columnId = columnId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun updateTableColumnDateSettings(
        blockId: String,
        columnId: String,
        dateFormat: PageTableDateFormat,
        timeFormat: PageTableTimeFormat,
        dateReminder: PageTableDateReminder,
        timezoneLabel: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            columns = block.table.columns.map { column ->
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
                        ),
                    )
                },
            )
            queueTableColumnPatchPendingDocument(
                tableBlockId = blockId,
                columnId = columnId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun updateTableColumnFormula(
        blockId: String,
        columnId: String,
        formula: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            columns = block.table.columns.map { column ->
                                if (column.id == columnId) {
                                    column.copy(formula = formula)
                                } else {
                                    column
                                }
                            },
                        ),
                    )
                },
            )
            queueTableColumnPatchPendingDocument(blockId, columnId, updated)
        }
    }

    fun updateTableColumnRelationTarget(
        blockId: String,
        columnId: String,
        targetTableId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            columns = block.table.columns.map { column ->
                                if (column.id == columnId) {
                                    column.copy(relationTargetTableId = targetTableId)
                                } else {
                                    column
                                }
                            },
                        ),
                    )
                },
            )
            queueTableColumnPatchPendingDocument(blockId, columnId, updated)
        }
    }

    fun updateTableColumnRollup(
        blockId: String,
        columnId: String,
        relationColumnId: String,
        targetColumnId: String,
        aggregation: PageTableRollupAggregation,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            columns = block.table.columns.map { column ->
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
                        ),
                    )
                },
            )
            queueTableColumnPatchPendingDocument(blockId, columnId, updated)
        }
    }

    fun updateTableCell(
        blockId: String,
        rowId: String,
        columnId: String,
        value: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            var coercedCellValue: String? = null
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    val column = block.table.columns.firstOrNull { tableColumn -> tableColumn.id == columnId }
                        ?: return@updateBlockById block
                    if (column.type == PageTableColumnType.Formula || column.type == PageTableColumnType.Rollup) {
                        return@updateBlockById block
                    }
                    val nextValue = column.type.coerceManualCellValue(value)
                    coercedCellValue = nextValue
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows.map { row ->
                                if (row.id == rowId) {
                                    row.copy(cells = row.cells + (columnId to nextValue))
                                } else {
                                    row
                                }
                            },
                        ),
                    )
                },
            )
            coercedCellValue?.let { nextValue ->
                queueGranularPendingDocument(updated) { normalized ->
                    PendingGranularDocumentSave.TableCellValue(
                        document = normalized,
                        rowId = rowId,
                        columnId = columnId,
                        value = nextValue,
                    )
                }
            }
        }
    }

    fun addTableColumn(blockId: String) {
        addTableColumn(
            blockId = blockId,
            name = "",
            type = PageTableColumnType.Text,
        )
    }

    fun addTableColumn(
        blockId: String,
        name: String,
        type: PageTableColumnType,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val tableBlock = document.findTableBlock(blockId) ?: return
            val column = PageBlockCodec.newTableColumn(
                name = name.trim().ifBlank { "Column ${tableBlock.table.columns.size + 1}" },
                type = type,
            )
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            columns = block.table.columns + column,
                            rows = block.table.rows.map { row ->
                                row.copy(cells = row.cells + (column.id to ""))
                            },
                        ),
                    )
                },
            )
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.addTableColumn(
                    pageId = pageId,
                    tableBlockId = blockId,
                    column = column,
                )
            }
        }
    }

    fun insertTableColumn(
        blockId: String,
        anchorColumnId: String,
        side: TableColumnInsertSide,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val tableBlock = document.findTableBlock(blockId) ?: return
            val anchorIndex = tableBlock.table.columns.indexOfFirst { column -> column.id == anchorColumnId }
            val insertIndex = when {
                anchorIndex == -1 -> tableBlock.table.columns.size
                side == TableColumnInsertSide.Left -> anchorIndex
                else -> anchorIndex + 1
            }.coerceIn(0, tableBlock.table.columns.size)
            val column = PageBlockCodec.newTableColumn(
                name = "Property ${tableBlock.table.columns.size + 1}",
                type = PageTableColumnType.Text,
            )
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            columns = block.table.columns.toMutableList().apply {
                                add(insertIndex, column)
                            },
                            rows = block.table.rows.map { row ->
                                row.copy(cells = row.cells + (column.id to ""))
                            },
                        ),
                    )
                },
            )
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.addTableColumn(
                    pageId = pageId,
                    tableBlockId = blockId,
                    column = column,
                    targetIndex = insertIndex,
                )
            }
        }
    }

    fun duplicateTableColumn(
        blockId: String,
        columnId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            var duplicatedColumn: PageTableColumn? = null
            var insertIndex: Int? = null
            var cellValues: Map<String, String> = emptyMap()
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    val sourceIndex = block.table.columns.indexOfFirst { column -> column.id == columnId }
                    val sourceColumn = block.table.columns.getOrNull(sourceIndex) ?: return@updateBlockById block
                    val nextColumn = PageBlockCodec
                        .newTableColumn("${sourceColumn.name.ifBlank { "Property" }} copy", sourceColumn.type)
                        .copy(
                            dateFormat = sourceColumn.dateFormat,
                            timeFormat = sourceColumn.timeFormat,
                            dateReminder = sourceColumn.dateReminder,
                            timezoneLabel = sourceColumn.timezoneLabel,
                            formula = sourceColumn.formula,
                            relationTargetTableId = sourceColumn.relationTargetTableId,
                            rollupRelationColumnId = sourceColumn.rollupRelationColumnId,
                            rollupTargetColumnId = sourceColumn.rollupTargetColumnId,
                            rollupAggregation = sourceColumn.rollupAggregation,
                        )
                    duplicatedColumn = nextColumn
                    insertIndex = sourceIndex + 1
                    cellValues = block.table.rows.associate { row ->
                        row.id to row.cells[columnId].orEmpty()
                    }
                    block.copy(
                        table = block.table.copy(
                            columns = block.table.columns.toMutableList().apply {
                                add(sourceIndex + 1, nextColumn)
                            },
                            rows = block.table.rows.map { row ->
                                row.copy(
                                    cells = row.cells + (
                                        nextColumn.id to row.cells[columnId].orEmpty()
                                        ),
                                )
                            },
                        ),
                    )
                },
            )
            val columnToAdd = duplicatedColumn
            if (columnToAdd == null || insertIndex == null) {
                queueDocumentUpdate(updated, previous = document, recordUndo = true)
                return
            }
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.addTableColumn(
                    pageId = pageId,
                    tableBlockId = blockId,
                    column = columnToAdd,
                    cellValues = cellValues,
                    targetIndex = insertIndex,
                )
            }
        }
    }

    fun deleteTableColumn(
        blockId: String,
        columnId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val tableBlock = document.findTableBlock(blockId) ?: return
            if (tableBlock.table.columns.size <= 1) return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    if (block.table.columns.size <= 1) {
                        block
                    } else {
                        block.copy(
                            table = block.table.copy(
                                columns = block.table.columns
                                    .filterNot { column -> column.id == columnId }
                                    .map { column -> column.withoutColumnReference(columnId) },
                                rows = block.table.rows.map { row ->
                                    row.copy(cells = row.cells - columnId)
                                },
                                sort = if (block.table.sort.columnId == columnId) {
                                    PageTableSort()
                                } else {
                                    block.table.sort
                                },
                                filter = if (block.table.filter.columnId == columnId) {
                                    PageTableFilter()
                                } else {
                                    block.table.filter
                                },
                                groupByColumnId = if (block.table.groupByColumnId == columnId) {
                                    ""
                                } else {
                                    block.table.groupByColumnId
                                },
                            ),
                        )
                    }
                },
            )
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.deleteTableColumn(
                    pageId = pageId,
                    tableBlockId = blockId,
                    columnId = columnId,
                )
            }
        }
    }

    fun addTableRow(blockId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val tableBlock = document.findTableBlock(blockId) ?: return
            val row = PageBlockCodec.newTableRow(tableBlock.table.columns)
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows + row,
                        ),
                    )
                },
            )
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.addTableRow(
                    pageId = pageId,
                    tableBlockId = blockId,
                    row = row,
                )
            }
        }
    }

    fun deleteTableRow(
        blockId: String,
        rowId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, blockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows.filterNot { row -> row.id == rowId },
                        ),
                    )
                },
            )
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.deleteTableRow(
                    pageId = pageId,
                    tableBlockId = blockId,
                    rowId = rowId,
                )
            }
        }
    }

    fun updateTableRowBlockText(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
        text: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows.map { row ->
                                if (row.id == rowId) {
                                    row.copy(
                                        blocks = updateBlockById(row.blocks.normalizedRowBlocks(), rowBlockId) { rowBlock ->
                                            rowBlock.copy(
                                                text = text,
                                                richTextSpans = rowBlock.richTextSpans.normalizedForText(text),
                                            )
                                        },
                                    )
                                } else {
                                    row
                                }
                            },
                        ),
                    )
                },
            )
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun updateTableRowBlockRichText(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
        text: String,
        spans: List<PageTextSpan>,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows.map { row ->
                                if (row.id == rowId) {
                                    row.copy(
                                        blocks = updateBlockById(row.blocks.normalizedRowBlocks(), rowBlockId) { rowBlock ->
                                            rowBlock.copy(
                                                text = text,
                                                richTextSpans = spans.normalizedForText(text),
                                            )
                                        },
                                    )
                                } else {
                                    row
                                }
                            },
                        ),
                    )
                },
            )
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun addTableRowBlockMediaAttachments(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
        attachments: List<PageMediaAttachment>,
    ) {
        if (attachments.isEmpty()) return
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows.map { row ->
                                if (row.id == rowId) {
                                    row.copy(
                                        blocks = updateBlockById(row.blocks.normalizedRowBlocks(), rowBlockId) { rowBlock ->
                                            rowBlock.copy(
                                                mediaAttachments = rowBlock.mediaAttachments + attachments,
                                            )
                                        },
                                    )
                                } else {
                                    row
                                }
                            },
                        ),
                    )
                },
            )
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun removeTableRowBlockMediaAttachment(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
        attachmentId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows.map { row ->
                                if (row.id == rowId) {
                                    row.copy(
                                        blocks = updateBlockById(row.blocks.normalizedRowBlocks(), rowBlockId) { rowBlock ->
                                            rowBlock.copy(
                                                mediaAttachments = rowBlock.mediaAttachments.filterNot { attachment ->
                                                    attachment.id == attachmentId
                                                },
                                            )
                                        },
                                    )
                                } else {
                                    row
                                }
                            },
                        ),
                    )
                },
            )
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun toggleTableRowTodoBlock(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows.map { row ->
                                if (row.id == rowId) {
                                    row.copy(
                                        blocks = updateBlockById(row.blocks.normalizedRowBlocks(), rowBlockId) { rowBlock ->
                                            rowBlock.copy(isChecked = !rowBlock.isChecked)
                                        },
                                    )
                                } else {
                                    row
                                }
                            },
                        ),
                    )
                },
            )
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun addTableRowPageBlock(
        tableBlockId: String,
        rowId: String,
        type: PageBlockType,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows.map { row ->
                                if (row.id == rowId) {
                                    row.copy(blocks = row.blocks.normalizedRowBlocks() + PageBlockCodec.newBlock(type))
                                } else {
                                    row
                                }
                            },
                        ),
                    )
                },
            )
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun deleteTableRowPageBlock(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                blocks = updateBlockById(document.blocks, tableBlockId) { block ->
                    block.copy(
                        table = block.table.copy(
                            rows = block.table.rows.map { row ->
                                if (row.id == rowId) {
                                    row.copy(
                                        blocks = deleteBlockRecursive(row.blocks.normalizedRowBlocks(), rowBlockId)
                                            .ifEmpty { listOf(PageBlockCodec.newBlock(PageBlockType.Text)) },
                                    )
                                } else {
                                    row
                                }
                            },
                        ),
                    )
                },
            )
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = updated,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun addProperty(
        type: PagePropertyType,
        name: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val normalizedName = name.ifBlank { "Untitled property" }
            val property = PageBlockCodec.newProperty(
                type = type,
                name = normalizedName,
            )
            val updated = document.copy(
                properties = document.properties + property,
            )
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.addProperty(
                    pageId = pageId,
                    property = property,
                )
            }
        }
    }

    fun updatePropertyName(
        propertyId: String,
        name: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                properties = document.properties.map { property ->
                    if (property.id == propertyId) {
                        property.copy(name = name)
                    } else {
                        property
                    }
                },
            )
            _pendingChanges.value = updated
        }
    }

    fun updatePropertyValue(
        propertyId: String,
        value: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                properties = document.properties.map { property ->
                    if (property.id == propertyId) {
                        property.copy(value = value)
                    } else {
                        property
                    }
                },
            )
            queueGranularPendingDocument(updated) { normalized ->
                PendingGranularDocumentSave.PropertyValue(
                    document = normalized,
                    propertyId = propertyId,
                    value = value,
                )
            }
        }
    }

    fun deleteProperty(propertyId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val updated = document.copy(
                properties = document.properties.filterNot { property -> property.id == propertyId },
            )
            queueGranularDocumentUpdate(
                previous = document,
                fallback = updated,
            ) {
                pageRepository.deleteProperty(
                    pageId = pageId,
                    propertyId = propertyId,
                )
            }
        }
    }

    fun createChildPage(onCreated: (Page) -> Unit) {
        viewModelScope.launch {
            val parent = pageRepository.getPage(pageId) ?: return@launch
            val childPage = pageRepository.createPage(
                workspaceId = parent.workspaceId,
                title = "Untitled page",
                content = PageBlockCodec.encode(listOf(PageBlockCodec.newBlock(PageBlockType.Text))),
                parentPageId = parent.id,
            )
            onCreated(childPage)
        }
    }

    private fun updateBlockById(
        blocks: List<PageBlock>,
        blockId: String,
        transform: (PageBlock) -> PageBlock,
    ): List<PageBlock> {
        return blocks.map { block ->
            if (block.id == blockId) {
                transform(block)
            } else {
                block.copy(children = updateBlockById(block.children, blockId, transform))
            }
        }
    }

    private fun updateTableCellValueInBlocks(
        blocks: List<PageBlock>,
        rowId: String,
        columnId: String,
        value: String,
    ): List<PageBlock> {
        return blocks.map { block ->
            val updatedChildren = updateTableCellValueInBlocks(
                blocks = block.children,
                rowId = rowId,
                columnId = columnId,
                value = value,
            )
            if (block.type == PageBlockType.DatabaseTable) {
                block.copy(
                    table = block.table.copy(
                        rows = block.table.rows.map { row ->
                            if (row.id == rowId) {
                                row.copy(cells = row.cells + (columnId to value))
                            } else {
                                row
                            }
                        },
                    ),
                    children = updatedChildren,
                )
            } else {
                block.copy(children = updatedChildren)
            }
        }
    }

    private fun List<PageBlock>.normalizedRowBlocks(): List<PageBlock> {
        return ifEmpty { listOf(PageBlockCodec.newBlock(PageBlockType.Text)) }
    }

    private fun PageBlockDocument.normalizedForEditor(): PageBlockDocument {
        return copy(
            blocks = blocks.ifEmpty { listOf(PageBlockCodec.newBlock(PageBlockType.Text)) },
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

    private fun String.toTableDateValue(allowPartial: Boolean): String {
        val trimmed = trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return trimmed
        return if (allowPartial) trimmed else ""
    }

    private fun String.toTableDateCellStorageValue(allowPartial: Boolean): String {
        val trimmed = trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        return trimmed.toTableDateValue(allowPartial = allowPartial)
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

    private fun deleteBlockRecursive(
        blocks: List<PageBlock>,
        blockId: String,
    ): List<PageBlock> {
        return blocks
            .filterNot { block -> block.id == blockId }
            .map { block ->
                block.copy(children = deleteBlockRecursive(block.children, blockId))
            }
    }

    private fun moveBlockInBlocks(
        blocks: List<PageBlock>,
        blockId: String,
        direction: Int,
    ): List<PageBlock> {
        val index = blocks.indexOfFirst { block -> block.id == blockId }
        if (index != -1) {
            if (direction < 0 && index > 0) {
                return blocks.toMutableList().apply {
                    val block = removeAt(index)
                    add(index - 1, block)
                }
            }
            if (direction > 0 && index < blocks.lastIndex) {
                return blocks.toMutableList().apply {
                    val block = removeAt(index)
                    add(index + 1, block)
                }
            }
            return blocks
        }

        return blocks.map { block ->
            block.copy(children = moveBlockInBlocks(block.children, blockId, direction))
        }
    }

    private fun List<PageBlock>.findBlockMoveTargetIndex(
        blockId: String,
        direction: Int,
    ): Int? {
        val index = indexOfFirst { block -> block.id == blockId }
        if (index != -1) {
            val targetIndex = index + direction
            return targetIndex.takeIf { it in indices }
        }
        for (block in this) {
            block.children.findBlockMoveTargetIndex(blockId, direction)?.let { return it }
        }
        return null
    }

    fun clearAiError() {
        _aiError.value = null
    }

    private suspend fun executePageAiActions(actions: List<ChatAction>): PageAiExecutionResult {
        val page = pageRepository.getPage(pageId) ?: return PageAiExecutionResult(
            messages = listOf("Failed: current page was not found."),
        )
        return runCatching {
            executePageAiActions(actions, page, uiState.value)
        }.getOrElse { error ->
            PageAiExecutionResult(
                messages = listOf(error.toPageAiExecutionErrorMessage()),
            )
        }
    }

    private suspend fun executePageAiActions(
        actions: List<ChatAction>,
        page: Page,
        currentUiState: PageEditorUiState,
    ): PageAiExecutionResult {
        if (actions.all { action -> aiPageActionExecutor.supports(action) }) {
            val document = PageBlockDocument(
                properties = currentUiState.properties,
                blocks = currentUiState.blocks,
            )
            val execution = aiPageActionExecutor.executeOnPage(
                page = page,
                title = currentUiState.title.ifBlank { page.title },
                document = document,
                actions = actions,
            )
            execution.updatedTitle?.let { title -> _pendingTitle.value = title }
            execution.updatedDocument?.let { updatedDocument ->
                _pendingChanges.value = updatedDocument
            }
            return PageAiExecutionResult(
                messages = execution.messages,
                createdPages = execution.createdPages,
                createdTasks = execution.createdTasks,
                createdReminders = execution.createdReminders,
                pageLinks = execution.pageLinks,
            )
        }

        var document = _pendingChanges.value ?: PageBlockCodec.decodeDocument(page.content)
        var documentChanged = false
        val results = mutableListOf<String>()
        val createdPages = mutableListOf<Page>()
        val createdTasks = mutableListOf<TaskItem>()
        val createdReminders = mutableListOf<Reminder>()
        var didChangeCurrentPage = false

        for (action in actions) {
            runCatching {
                when (action.type.trim().uppercase()) {
                    "RENAME_CURRENT_PAGE", "RENAME_PAGE" -> {
                        val title = action.title.ifBlank { error("Missing new page title") }
                        _pendingTitle.value = title
                        "Renamed page to: $title"
                    }

                    "UPDATE_PAGE" -> {
                        ensureTargetsCurrentPage(action, page)
                        if (action.title.isNotBlank()) {
                            _pendingTitle.value = action.title
                            didChangeCurrentPage = true
                        }
                        if (action.content.isNotBlank()) {
                            document = document.copy(
                                blocks = listOf(
                                    PageBlockCodec.newBlock(PageBlockType.Text).copy(text = action.content),
                                ),
                            )
                            documentChanged = true
                            didChangeCurrentPage = true
                        }
                        "Updated current page"
                    }

                    "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> {
                        if (action.type.trim().uppercase() == "APPEND_PAGE_BLOCK") {
                            ensureTargetsCurrentPage(action, page)
                        }
                        val block = action.toPageBlock()
                        document = document.copy(blocks = document.blocks + block)
                        documentChanged = true
                        didChangeCurrentPage = true
                        "Added ${block.type.name} block"
                    }

                    "ADD_PROPERTY", "UPDATE_PROPERTY" -> {
                        val propertyName = action.propertyName
                            .ifBlank { action.title }
                            .ifBlank { error("Missing property name") }
                        val propertyType = action.propertyType.toPagePropertyType()
                        val propertyValue = action.value.ifBlank { action.content }
                        document = document.upsertProperty(
                            name = propertyName,
                            type = propertyType,
                            value = propertyValue,
                        )
                        documentChanged = true
                        didChangeCurrentPage = true
                        "Updated property: $propertyName"
                    }

                    "DELETE_PROPERTY" -> {
                        val propertyName = action.propertyName
                            .ifBlank { action.title }
                            .ifBlank { error("Missing property name") }
                        document = document.deletePropertyByName(propertyName)
                        documentChanged = true
                        didChangeCurrentPage = true
                        "Deleted property: $propertyName"
                    }

                    "DELETE_BLOCK" -> {
                        val deleteResult = document.deleteMatchingBlock(action)
                        document = deleteResult.document
                        documentChanged = true
                        didChangeCurrentPage = true
                        "Deleted block: ${deleteResult.deletedLabel}"
                    }

                    "UPDATE_BLOCK", "EDIT_BLOCK", "UPDATE_TODO", "CHECK_BLOCK", "UNCHECK_BLOCK" -> {
                        val updateResult = document.updateMatchingBlock(action)
                        document = updateResult.document
                        documentChanged = true
                        didChangeCurrentPage = true
                        "Updated block: ${updateResult.updatedLabel}"
                    }

                    "CREATE_DATABASE", "CREATE_TABLE" -> {
                        val tableBlock = action.toDatabaseBlock()
                        document = document.copy(blocks = document.blocks + tableBlock)
                        documentChanged = true
                        didChangeCurrentPage = true
                        "Created database: ${tableBlock.table.title}"
                    }

                    "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" -> {
                        val newTitle = action.title
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { action.newColumnName }
                            .ifBlank { error("Missing new table title") }
                        val update = document.updateMatchingTable(action) { block ->
                            block.copy(table = block.table.copy(title = newTitle))
                        }
                        document = update.document
                        documentChanged = true
                        "Renamed ${update.tableTitle} to $newTitle"
                    }

                    "CREATE_MODULE", "CREATE_GOAL_MODULE", "CREATE_HABIT_MODULE",
                    "CREATE_TRAVEL_MODULE", "CREATE_BUDGET_MODULE" -> {
                        val moduleType = action.toModuleType()
                        val title = action.title.ifBlank { PageModuleTemplates.defaultTitle(moduleType) }
                        val createdPage = pageRepository.createPage(
                            workspaceId = page.workspaceId,
                            title = title,
                            content = PageModuleTemplates.contentFor(moduleType),
                            parentPageId = page.id,
                        )
                        createdPages += createdPage
                        "Created ${moduleType.label} module: $title"
                    }

                    "ADD_TABLE_COLUMN" -> {
                        val resolvedAction = action.withResolvedRelationTarget(document)
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                            .ifBlank { error("Missing column name") }
                        val columnType = action.columnType
                            .ifBlank { action.propertyType }
                            .toPageTableColumnType()
                        val update = document.updateMatchingTable(resolvedAction) { block ->
                            val relationColumn = block.table.findColumn(
                                columnId = resolvedAction.rollupRelationColumnId,
                                columnName = resolvedAction.rollupRelationColumnName,
                            )
                            val rollupTargetColumnId = block.resolveRollupTargetColumnId(
                                action = resolvedAction,
                                relationColumn = relationColumn,
                                document = document,
                            )
                            val column = PageBlockCodec.newTableColumn(columnName, columnType)
                                .withActionConfig(
                                    action = resolvedAction,
                                    relationColumn = relationColumn,
                                    resolvedRollupTargetColumnId = rollupTargetColumnId,
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
                        document = update.document
                        documentChanged = true
                        "Added column $columnName to ${update.tableTitle}"
                    }

                    "ADD_TABLE_ROW" -> {
                        if (action.isTaskTableRowAction()) {
                            val mutation = document.withTaskTableAction(action)
                            document = mutation.document
                            documentChanged = true
                            didChangeCurrentPage = true
                            "Added task row ${mutation.rowTitle} to ${mutation.tableTitle}"
                        } else {
                            val update = document.updateMatchingTable(action) { block ->
                                block.copy(
                                    table = block.table.copy(
                                        rows = block.table.rows + block.table.newRowFromAction(action),
                                    ),
                                )
                            }
                            document = update.document
                            documentChanged = true
                            "Added row to ${update.tableTitle}"
                        }
                    }

                    "DELETE_TABLE_COLUMN" -> {
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                        val update = document.updateMatchingTable(action) { block ->
                            block.deleteTableColumn(
                                columnId = action.columnId,
                                columnName = columnName,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Deleted column ${columnName.ifBlank { action.columnId }} from ${update.tableTitle}"
                    }

                    "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE" -> {
                        val resolvedAction = action.withResolvedRelationTarget(document)
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                        val columnType = action.columnType
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { error("Missing column type") }
                            .toPageTableColumnType()
                        val update = document.updateMatchingTable(resolvedAction) { block ->
                            block.updateTableColumnType(
                                columnId = resolvedAction.columnId,
                                columnName = columnName,
                                columnType = columnType,
                            ).configureTableColumn(
                                columnId = resolvedAction.columnId,
                                columnName = columnName,
                                action = resolvedAction,
                                document = document,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Changed column ${columnName.ifBlank { action.columnId }} to ${columnType.name} in ${update.tableTitle}"
                    }

                    "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG",
                    "UPDATE_FORMULA_COLUMN", "UPDATE_RELATION_COLUMN", "UPDATE_ROLLUP_COLUMN" -> {
                        val resolvedAction = action.withResolvedRelationTarget(document)
                        val columnName = resolvedAction.columnName
                            .ifBlank { resolvedAction.propertyName }
                            .ifBlank { resolvedAction.title }
                        val update = document.updateMatchingTable(resolvedAction) { block ->
                            block.configureTableColumn(
                                columnId = resolvedAction.columnId,
                                columnName = columnName,
                                action = resolvedAction,
                                document = document,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Updated column configuration in ${update.tableTitle}"
                    }

                    "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN" -> {
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                        val newColumnName = action.newColumnName
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { error("Missing new column name") }
                        val update = document.updateMatchingTable(action) { block ->
                            block.renameTableColumn(
                                columnId = action.columnId,
                                columnName = columnName,
                                newColumnName = newColumnName,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Renamed column to $newColumnName in ${update.tableTitle}"
                    }

                    "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN" -> {
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                        val targetIndex = action.targetIndex ?: error("Missing target index")
                        val update = document.updateMatchingTable(action) { block ->
                            block.reorderTableColumn(
                                columnId = action.columnId,
                                columnName = columnName,
                                targetIndex = targetIndex,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Moved column in ${update.tableTitle}"
                    }

                    "DELETE_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle
                            .ifBlank { action.title }
                        val update = document.updateMatchingTable(action) { block ->
                            block.deleteTableRow(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Deleted row ${rowTitle.ifBlank { action.rowId }} from ${update.tableTitle}"
                    }

                    "RENAME_TABLE_ROW", "UPDATE_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle
                            .ifBlank { action.title }
                        val newRowTitle = action.newRowTitle
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                        val update = document.updateMatchingTable(action) { block ->
                            block.updateTableRow(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                                newRowTitle = newRowTitle,
                                cellValues = action.cellValues,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Updated row ${newRowTitle.ifBlank { rowTitle.ifBlank { action.rowId } }} in ${update.tableTitle}"
                    }

                    "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" -> {
                        val rowTitle = action.rowTitle
                            .ifBlank { action.title }
                        val targetIndex = action.targetIndex ?: error("Missing target index")
                        val update = document.updateMatchingTable(action) { block ->
                            block.reorderTableRow(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                                targetIndex = targetIndex,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Moved row in ${update.tableTitle}"
                    }

                    "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK" -> {
                        val rowTitle = action.rowTitle
                            .ifBlank { action.targetTitle }
                            .ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) {
                            error("Missing row target")
                        }
                        val rowBlock = action.toPageBlock()
                        val update = document.updateMatchingTable(action) { block ->
                            block.addRowPageBlock(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                                rowBlock = rowBlock,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Added ${rowBlock.type.name} block inside $rowTitle in ${update.tableTitle}"
                    }

                    "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK",
                    "CHECK_ROW_PAGE_BLOCK", "UNCHECK_ROW_PAGE_BLOCK" -> {
                        val rowTitle = action.rowTitle
                            .ifBlank { action.targetTitle }
                            .ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) {
                            error("Missing row target")
                        }
                        val update = document.updateMatchingTable(action) { block ->
                            block.updateRowPageBlock(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                                rowBlockId = action.rowBlockId,
                                action = action,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Updated row content in $rowTitle in ${update.tableTitle}"
                    }

                    "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" -> {
                        val rowTitle = action.rowTitle
                            .ifBlank { action.targetTitle }
                            .ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) {
                            error("Missing row target")
                        }
                        val update = document.updateMatchingTable(action) { block ->
                            block.deleteRowPageBlock(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                                rowBlockId = action.rowBlockId,
                                action = action,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Deleted row content from $rowTitle in ${update.tableTitle}"
                    }

                    "UPDATE_TABLE_CELL" -> {
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                        if (columnName.isBlank() && action.columnId.isBlank()) {
                            error("Missing column name")
                        }
                        val rowTitle = action.rowTitle
                            .ifBlank { action.title }
                        if (rowTitle.isBlank() && action.rowId.isBlank()) {
                            error("Missing row title")
                        }
                        val cellValue = action.value.ifBlank { action.content }
                        val update = document.updateMatchingTable(action) { block ->
                            block.updateCellByNames(
                                rowId = action.rowId,
                                rowTitle = rowTitle,
                                columnId = action.columnId,
                                columnName = columnName,
                                value = cellValue,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Updated $columnName for $rowTitle in ${update.tableTitle}"
                    }

                    "CHANGE_TABLE_VIEW", "SET_TABLE_VIEW" -> {
                        val view = action.tableView
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .toPageTableView()
                        val update = document.updateMatchingTable(action) { block ->
                            block.copy(table = block.table.copy(view = view))
                        }
                        document = update.document
                        documentChanged = true
                        "Changed ${update.tableTitle} view to ${view.name}"
                    }

                    "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG" -> {
                        val update = document.updateMatchingTable(action) { block ->
                            block.copy(
                                table = block.table.copy(
                                    viewConfig = action.toTableViewConfig(block.table),
                                ),
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Updated ${update.tableTitle} view config"
                    }

                    "SORT_TABLE", "SET_TABLE_SORT" -> {
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                        val direction = action.sortDirection
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .toPageTableSortDirection()
                        val update = document.updateMatchingTable(action) { block ->
                            block.sortTable(
                                columnId = action.columnId,
                                columnName = columnName,
                                direction = direction,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Sorted ${update.tableTitle} by ${columnName.ifBlank { action.columnId }} ${direction.name.lowercase()}"
                    }

                    "CLEAR_TABLE_SORT" -> {
                        val update = document.updateMatchingTable(action) { block ->
                            block.copy(table = block.table.copy(sort = PageTableSort()))
                        }
                        document = update.document
                        documentChanged = true
                        "Cleared sort in ${update.tableTitle}"
                    }

                    "FILTER_TABLE", "SET_TABLE_FILTER" -> {
                        val columnName = action.columnName
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                        val query = action.filterQuery
                            .ifBlank { action.value }
                            .ifBlank { action.content }
                            .ifBlank { error("Missing filter query") }
                        val update = document.updateMatchingTable(action) { block ->
                            block.filterTable(
                                columnId = action.columnId,
                                columnName = columnName,
                                query = query,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Filtered ${update.tableTitle} by ${columnName.ifBlank { action.columnId }}"
                    }

                    "CLEAR_TABLE_FILTER" -> {
                        val update = document.updateMatchingTable(action) { block ->
                            block.copy(table = block.table.copy(filter = PageTableFilter()))
                        }
                        document = update.document
                        documentChanged = true
                        "Cleared filter in ${update.tableTitle}"
                    }

                    "GROUP_TABLE", "SET_TABLE_GROUP" -> {
                        val columnId = action.groupByColumnId.ifBlank { action.columnId }
                        val columnName = action.groupByColumnName
                            .ifBlank { action.columnName }
                            .ifBlank { action.propertyName }
                            .ifBlank { action.title }
                        val update = document.updateMatchingTable(action) { block ->
                            block.groupTable(
                                columnId = columnId,
                                columnName = columnName,
                            )
                        }
                        document = update.document
                        documentChanged = true
                        "Grouped ${update.tableTitle} by ${columnName.ifBlank { columnId }}"
                    }

                    "CLEAR_TABLE_GROUP" -> {
                        val update = document.updateMatchingTable(action) { block ->
                            block.copy(table = block.table.copy(groupByColumnId = ""))
                        }
                        document = update.document
                        documentChanged = true
                        "Cleared group in ${update.tableTitle}"
                    }

                    "CREATE_SUBPAGE" -> {
                        ensureTargetsCurrentPage(action, page)
                        val title = action.title.ifBlank { error("Missing subpage title") }
                        val createdPage = pageRepository.createPage(
                            workspaceId = page.workspaceId,
                            title = title,
                            content = action.content.toPageContentDocument(),
                            parentPageId = page.id,
                        )
                        createdPages += createdPage
                        "Created subpage: $title"
                    }

                    "CREATE_PAGE" -> {
                        val requestedModuleType = action.requestedModuleType("CREATE_PAGE")
                        val title = action.title.ifBlank {
                            requestedModuleType?.let { PageModuleTemplates.defaultTitle(it) }
                                ?: error("Missing page title")
                        }
                        val createdPage = if (requestedModuleType != null) {
                            pageRepository.createPage(
                                workspaceId = page.workspaceId,
                                title = title,
                                content = PageModuleTemplates.contentFor(requestedModuleType),
                                parentPageId = page.id,
                            )
                        } else {
                            pageRepository.createPage(
                                workspaceId = page.workspaceId,
                                title = title,
                                content = action.content.toPageContentDocument(),
                            )
                        }
                        createdPages += createdPage
                        if (requestedModuleType != null) {
                            "Created ${requestedModuleType.label} module: $title"
                        } else {
                            "Created page: $title"
                        }
                    }

                    "CREATE_TASK" -> {
                        val mutation = document.withTaskTableAction(action)
                        document = mutation.document
                        documentChanged = true
                        didChangeCurrentPage = true
                        "Added task row ${mutation.rowTitle} to ${mutation.tableTitle}"
                    }

                    "CREATE_REMINDER" -> {
                        val mutation = document.withTaskTableAction(action)
                        document = mutation.document
                        documentChanged = true
                        didChangeCurrentPage = true
                        "Added reminder row ${mutation.rowTitle} to ${mutation.tableTitle}"
                    }

                    else -> error("Unsupported page action: ${action.type}")
                }
            }.onSuccess { message ->
                results += "Done: $message"
            }.onFailure { error ->
                results += "Failed ${action.type}: ${error.localizedMessage ?: "Unknown error"}"
            }
        }

        if (documentChanged) {
            _pendingChanges.value = document
        }
        val pageLinks = buildList {
            if (documentChanged || didChangeCurrentPage) {
                add(
                    AiChatPageLink(
                        pageId = page.id,
                        title = (_pendingTitle.value ?: page.title).ifBlank { "Untitled page" },
                    ),
                )
            }
            createdPages.forEach { createdPage ->
                add(createdPage.toChatPageLink())
            }
        }

        return PageAiExecutionResult(
            messages = results,
            createdPages = createdPages,
            createdTasks = createdTasks,
            createdReminders = createdReminders,
            pageLinks = pageLinks,
        )
    }

    private fun ensureTargetsCurrentPage(
        action: ChatAction,
        page: Page,
    ) {
        val target = action.targetTitle.trim()
        if (target.isBlank()) return

        val currentTitle = (_pendingTitle.value ?: page.title).ifBlank { "Untitled page" }
        val normalizedTarget = target.removePrefix("@").trim()
        val isCurrentAlias = normalizedTarget.equals("this page", ignoreCase = true) ||
            normalizedTarget.equals("current page", ignoreCase = true) ||
            normalizedTarget.equals("page ini", ignoreCase = true) ||
            normalizedTarget.equals("sini", ignoreCase = true)
        val matchesCurrentTitle = normalizedTarget.equals(currentTitle, ignoreCase = true)

        if (!isCurrentAlias && !matchesCurrentTitle) {
            error("Target is not the current page: $target")
        }
    }

    private fun buildPageContextPrompt(
        state: PageEditorUiState,
        prompt: String,
    ): String {
        val title = state.title.ifBlank { "Untitled page" }
        val propertiesText = state.properties
            .joinToString(separator = "\n") { property ->
                "- ${property.name} (${property.type.name}): ${property.value.ifBlank { "empty" }}"
            }
            .ifBlank { "- No properties" }
        val childPagesText = state.childPages
            .joinToString(separator = "\n") { childPage -> "- ${childPage.title.ifBlank { "Untitled page" }}" }
            .ifBlank { "- No subpages" }
        val blocksText = state.blocks
            .toContextText()
            .ifBlank { "- No blocks" }
            .take(6_000)

        return """
            Current page is attached as @$title.
            Treat "this page", "current page", "page ini", and "sini" as @$title.

            Page title:
            $title

            Properties:
            $propertiesText

            Blocks:
            $blocksText

            Subpages:
            $childPagesText

            User request:
            $prompt
        """.trimIndent()
    }

    private fun PageEditorUiState.toAiPageContext(pageId: String): AiPageContext {
        val propertyContexts = properties.map { property ->
            AiBlockContext(
                id = property.id,
                type = "Property",
                text = "${property.name} (${property.type.name}) = ${property.value.ifBlank { "empty" }}",
                path = "property:${property.name}",
            )
        }
        return AiPageContext(
            id = pageId,
            title = title.ifBlank { "Untitled page" },
            blocks = (propertyContexts + blocks.toAiBlockContexts()).take(140),
        )
    }

    private fun List<PageBlock>.toAiBlockContexts(
        pathPrefix: String = "",
        tableBlockId: String = "",
        tableTitle: String = "",
        rowId: String = "",
        rowTitle: String = "",
    ): List<AiBlockContext> {
        return flatMapIndexed { index, block ->
            val path = if (pathPrefix.isBlank()) {
                "${index + 1}"
            } else {
                "$pathPrefix.${index + 1}"
            }
            val currentTableTitle = if (block.type == PageBlockType.DatabaseTable) block.table.title else tableTitle
            val blockContext = listOf(
                AiBlockContext(
                    id = block.id,
                    type = block.type.name,
                    text = block.contextText(),
                    path = path,
                    tableTitle = currentTableTitle,
                    tableBlockId = tableBlockId,
                    rowId = rowId,
                    rowTitle = rowTitle,
                    rowBlockId = if (rowId.isNotBlank()) block.id else "",
                    isChecked = if (block.type == PageBlockType.Todo) block.isChecked else null,
                )
            ) + block.children.toAiBlockContexts(
                pathPrefix = path,
                tableBlockId = tableBlockId,
                tableTitle = currentTableTitle,
                rowId = rowId,
                rowTitle = rowTitle,
            )
            val rowBlockContexts = if (block.type == PageBlockType.DatabaseTable) {
                val titleColumn = block.table.columns.firstOrNull()
                block.table.rows.take(20).flatMap { row ->
                    val rowLabel = row.cellText(titleColumn).ifBlank { row.id }
                    row.blocks.toAiBlockContexts(
                        pathPrefix = "$path.row:${row.id}",
                        tableBlockId = block.id,
                        tableTitle = block.table.title,
                        rowId = row.id,
                        rowTitle = rowLabel,
                    )
                }
            } else {
                emptyList()
            }
            blockContext + rowBlockContexts
        }
    }

    private fun List<PageBlock>.toContextText(indentLevel: Int = 0, pathPrefix: String = ""): String {
        return flatMapIndexed { index, block ->
            val path = if (pathPrefix.isBlank()) {
                "${index + 1}"
            } else {
                "$pathPrefix.${index + 1}"
            }
            val prefix = "  ".repeat(indentLevel) + "- "
            val line = when (block.type) {
                PageBlockType.DatabaseTable -> {
                    val columns = block.table.columns.joinToString { column ->
                        column.aiContextText()
                    }
                    val rows = block.table.rows.take(12).joinToString(separator = "; ") { row ->
                        val cells = block.table.columns.joinToString { column ->
                            "${column.name}=${row.cells[column.id].orEmpty()}"
                        }
                        val rowBlocks = row.blocks.take(6).joinToString(separator = " | ") { rowBlock ->
                            "${rowBlock.id}:${rowBlock.type.name}:${rowBlock.text.ifBlank { "empty" }}"
                        }.ifBlank { "none" }
                        "${row.id}[$cells; rowBlocks=$rowBlocks]"
                    }
                    val tableState = block.table.contextStateText()
                    "${prefix}[blockId=${block.id}; path=$path] DatabaseTable: ${block.table.title}; $tableState; columns: $columns; rows: $rows"
                }
                PageBlockType.Divider -> "${prefix}[blockId=${block.id}; path=$path] Divider"
                else -> "${prefix}[blockId=${block.id}; path=$path] ${block.type.name}: ${block.text.ifBlank { "empty" }}"
            }
            listOf(line) + block.children.toContextText(indentLevel + 1, path).lines().filter { it.isNotBlank() }
        }.joinToString("\n")
    }

    private fun PageBlock.contextText(): String {
        if (type == PageBlockType.DatabaseTable) {
            val columns = table.columns.joinToString { column ->
                column.aiContextText()
            }
            val rows = table.rows.take(12).joinToString(separator = "; ") { row ->
                val cells = table.columns.joinToString { column ->
                    "${column.name}=${row.cells[column.id].orEmpty()}"
                }
                val rowBlocks = row.blocks.take(6).joinToString(separator = " | ") { rowBlock ->
                    "${rowBlock.id}:${rowBlock.type.name}:${rowBlock.text.ifBlank { "empty" }}"
                }.ifBlank { "none" }
                "${row.id}[$cells; rowBlocks=$rowBlocks]"
            }
            return "title=${table.title}; ${table.contextStateText()}; columns=$columns; rows=$rows".take(1_800)
        }
        return text.take(600)
    }

    private fun buildPageAiSearchReply(
        state: PageEditorUiState,
        prompt: String,
    ): String? {
        if (!prompt.isReadOnlySearchRequest()) return null
        val terms = prompt.searchTerms()
        if (terms.isEmpty()) {
            return "Boleh. Beritahu keyword atau data apa yang awak nak saya cari dalam page ini."
        }

        val pageTitle = state.title.ifBlank { state.page?.title.orEmpty() }.ifBlank { "Untitled page" }
        val titleHit = "Page title: $pageTitle".toPageAiSearchHit("Page", terms)
        val propertyHits = state.properties.mapNotNull { property ->
            val line = "Property ${property.name} (${property.type.name}) ${property.value.ifBlank { "empty" }}"
            line.toPageAiSearchHit("Properties", terms)
        }
        val blockHits = state.blocks.flatMapIndexed { index, block ->
            block.aiSearchHits(path = "Block ${index + 1}", terms = terms)
        }
        val hits = (listOfNotNull(titleHit) + propertyHits + blockHits)
            .sortedWith(compareByDescending<PageAiSearchHit> { it.score }.thenBy { hit -> hit.title })
            .take(10)

        if (hits.isEmpty()) {
            return "Saya tak jumpa padanan untuk `${terms.joinToString(" ")}` dalam page ini."
        }

        return buildString {
            appendLine("Saya jumpa ${hits.size} padanan dalam page ini:")
            hits.forEachIndexed { index, hit ->
                appendLine("${index + 1}. ${hit.title} - ${hit.detail}")
            }
        }.trim()
    }

    private data class PageAiSearchHit(
        val title: String,
        val detail: String,
        val score: Int,
    )

    private fun PageBlock.aiSearchHits(
        path: String,
        terms: List<String>,
    ): List<PageAiSearchHit> {
        val selfHits = when (type) {
            PageBlockType.DatabaseTable -> table.aiSearchHits(path, terms)
            PageBlockType.MediaFile -> {
                val line = mediaAttachments.joinToString(separator = "; ") { attachment ->
                    "File ${attachment.name} ${attachment.mimeType}"
                }.ifBlank { "Media file" }
                listOfNotNull(line.toPageAiSearchHit(path, terms))
            }
            PageBlockType.Divider -> listOfNotNull("Divider".toPageAiSearchHit(path, terms))
            else -> listOfNotNull(
                "${type.name}: ${text.ifBlank { "empty" }}".toPageAiSearchHit(path, terms),
            )
        }
        val childHits = children.flatMapIndexed { index, child ->
            child.aiSearchHits(path = "$path.${index + 1}", terms = terms)
        }
        return selfHits + childHits
    }

    private fun PageTable.aiSearchHits(
        path: String,
        terms: List<String>,
    ): List<PageAiSearchHit> {
        val tableHits = listOfNotNull(
            "Table $title ${view.name}".toPageAiSearchHit(path, terms),
        )
        val columnHits = columns.mapNotNull { column ->
            "Column ${column.name} ${column.type.name} ${column.formula}"
                .toPageAiSearchHit("$path columns", terms)
        }
        val rowHits = rows.flatMapIndexed { rowIndex, row ->
            val rowTitle = row.cellText(columns.firstOrNull()).ifBlank { "Row ${rowIndex + 1}" }
            val cells = columns.mapNotNull { column ->
                row.cells[column.id]
                    ?.takeIf { value -> value.isNotBlank() }
                    ?.let { value -> "${column.name}: $value" }
            }
            val rowLine = "Row $rowTitle ${cells.joinToString(separator = "; ")}"
            val currentRowHits = listOfNotNull(
                rowLine.toPageAiSearchHit("$path row ${rowIndex + 1}", terms),
            )
            val rowBlockHits = row.blocks.flatMapIndexed { blockIndex, block ->
                block.aiSearchHits(
                    path = "$path row ${rowIndex + 1}.${blockIndex + 1}",
                    terms = terms,
                )
            }
            currentRowHits + rowBlockHits
        }
        return tableHits + columnHits + rowHits
    }

    private fun String.toPageAiSearchHit(
        title: String,
        terms: List<String>,
    ): PageAiSearchHit? {
        val score = searchScore(terms)
        return if (score <= 0) {
            null
        } else {
            PageAiSearchHit(
                title = title,
                detail = compactAiSearchLine(),
                score = score,
            )
        }
    }

    private fun String.isReadOnlySearchRequest(): Boolean {
        val text = lowercase()
        val searchWords = listOf(
            "cari",
            "search",
            "find",
            "lookup",
            "senarai",
            "list",
            "tunjuk",
            "show",
            "apa",
            "mana",
            "berapa",
            "count",
            "kira",
            "ringkas",
            "summarize",
        )
        val mutationWords = listOf(
            "buat",
            "create",
            "tambah",
            "add",
            "ubah",
            "tukar",
            "change",
            "update",
            "edit",
            "delete",
            "padam",
            "remove",
            "rename",
            "complete",
            "mark",
            "sort",
            "filter",
            "group",
            "set",
            "jadikan",
        )
        return searchWords.any { word -> text.contains(word) } &&
            mutationWords.none { word -> text.contains(word) }
    }

    private fun String.searchTerms(): List<String> {
        val stopWords = setOf(
            "ai",
            "aku",
            "awak",
            "boleh",
            "can",
            "cari",
            "search",
            "find",
            "lookup",
            "senarai",
            "list",
            "tunjuk",
            "show",
            "apa",
            "mana",
            "berapa",
            "count",
            "kira",
            "ringkas",
            "summarize",
            "dalam",
            "dekat",
            "di",
            "page",
            "pages",
            "table",
            "row",
            "rows",
            "data",
            "item",
            "rekod",
            "record",
            "records",
            "yang",
            "dan",
            "atau",
            "the",
            "a",
            "an",
            "to",
            "for",
            "of",
            "in",
            "on",
            "me",
            "please",
            "tolong",
        )
        return lowercase()
            .split(Regex("[^a-z0-9@]+"))
            .map { term -> term.trim('@') }
            .filter { term -> term.length >= 2 && term !in stopWords }
            .distinct()
    }

    private fun String.searchScore(terms: List<String>): Int {
        val text = lowercase()
        return terms.count { term -> text.contains(term) }
    }

    private fun String.compactAiSearchLine(): String {
        return trim()
            .replace(Regex("\\s+"), " ")
            .take(180)
    }

    private fun ChatAction.toPageBlock(): PageBlock {
        val type = blockType.toPageBlockType()
        val text = content.ifBlank { title }
        val block = PageBlockCodec.newBlock(type)
        return if (type == PageBlockType.DatabaseTable) {
            block.copy(
                table = block.table.copy(
                    title = text.ifBlank { "AI table" },
                ),
            )
        } else {
            block.copy(text = text)
        }
    }

    private fun ChatAction.previewLabel(): String {
        return when (type.trim().uppercase()) {
            "RENAME_CURRENT_PAGE", "RENAME_PAGE" -> "Rename page to \"${title.ifBlank { "Untitled page" }}\""
            "UPDATE_PAGE" -> "Update current page"
            "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> "Add ${blockType.ifBlank { "Text" }} block: ${content.ifBlank { title }.ifBlank { "empty" }}"
            "ADD_PROPERTY" -> "Add property: ${propertyName.ifBlank { title }.ifBlank { "Untitled property" }}"
            "UPDATE_PROPERTY" -> "Update property: ${propertyName.ifBlank { title }.ifBlank { "Untitled property" }}"
            "DELETE_PROPERTY" -> "Delete property: ${propertyName.ifBlank { title }.ifBlank { "Untitled property" }}"
            "UPDATE_BLOCK", "EDIT_BLOCK", "UPDATE_TODO", "CHECK_BLOCK", "UNCHECK_BLOCK" -> "Update block: ${
                tableTitle
                    .ifBlank { blockText }
                    .ifBlank { content }
                    .ifBlank { value }
                    .ifBlank { title }
                    .ifBlank { blockId }
                    .ifBlank { blockType.ifBlank { "block" } }
            }"
            "DELETE_BLOCK" -> "Delete block: ${
                tableTitle
                    .ifBlank { blockText }
                    .ifBlank { content }
                    .ifBlank { title }
                    .ifBlank { blockId }
                    .ifBlank { blockType.ifBlank { "block" } }
            }"
            "CREATE_DATABASE", "CREATE_TABLE" -> "Create database: ${tableTitle.ifBlank { title }.ifBlank { "AI database" }}"
            "CREATE_MODULE", "CREATE_GOAL_MODULE", "CREATE_HABIT_MODULE",
            "CREATE_TRAVEL_MODULE", "CREATE_BUDGET_MODULE" -> "Create module: ${
                PageModuleTemplates.fromActionFields(moduleType, type, title, tableTitle, content, blockType)
                    ?.defaultTitle
                    ?: title.ifBlank { "Module" }
            }"
            "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG" -> "Update table view setup"
            "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" -> "Rename table to ${
                title.ifBlank { value }.ifBlank { content }.ifBlank { newColumnName }.ifBlank { "New table" }
            }"
            "ADD_TABLE_COLUMN" -> "Add table column: ${columnName.ifBlank { propertyName }.ifBlank { title }.ifBlank { "Column" }}"
            "ADD_TABLE_ROW" -> "Add table row: ${rowTitle.ifBlank { title }.ifBlank { cellValues.values.firstOrNull().orEmpty() }.ifBlank { "New row" }}"
            "DELETE_TABLE_COLUMN" -> "Delete table column: ${columnName.ifBlank { propertyName }.ifBlank { title }.ifBlank { columnId }.ifBlank { "Column" }}"
            "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE" -> "Change table column type: ${
                columnName.ifBlank { propertyName }.ifBlank { columnId }.ifBlank { "Column" }
            } to ${columnType.ifBlank { value }.ifBlank { content }.ifBlank { "Text" }}"
            "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG",
            "UPDATE_FORMULA_COLUMN", "UPDATE_RELATION_COLUMN", "UPDATE_ROLLUP_COLUMN" -> "Update table column config: ${
                columnName.ifBlank { propertyName }.ifBlank { columnId }.ifBlank { "Column" }
            }"
            "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN" -> "Rename table column: ${
                columnName.ifBlank { propertyName }.ifBlank { columnId }.ifBlank { "Column" }
            } to ${newColumnName.ifBlank { value }.ifBlank { content }.ifBlank { "New column" }}"
            "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN" -> "Move table column: ${columnName.ifBlank { propertyName }.ifBlank { columnId }.ifBlank { "Column" }}"
            "DELETE_TABLE_ROW" -> "Delete table row: ${rowTitle.ifBlank { title }.ifBlank { rowId }.ifBlank { "Row" }}"
            "RENAME_TABLE_ROW", "UPDATE_TABLE_ROW" -> "Update table row: ${rowTitle.ifBlank { title }.ifBlank { rowId }.ifBlank { "Row" }}"
            "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" -> "Move table row: ${rowTitle.ifBlank { title }.ifBlank { rowId }.ifBlank { "Row" }}"
            "UPDATE_TABLE_CELL" -> "Update table cell: ${rowTitle.ifBlank { title }.ifBlank { "row" }} / ${columnName.ifBlank { propertyName }.ifBlank { "column" }}"
            "SORT_TABLE", "SET_TABLE_SORT" -> "Sort table by ${
                columnName.ifBlank { propertyName }.ifBlank { title }.ifBlank { columnId }.ifBlank { "Column" }
            } ${sortDirection.ifBlank { value }.ifBlank { content }.ifBlank { "Ascending" }}"
            "CLEAR_TABLE_SORT" -> "Clear table sort"
            "FILTER_TABLE", "SET_TABLE_FILTER" -> "Filter table: ${
                columnName.ifBlank { propertyName }.ifBlank { title }.ifBlank { columnId }.ifBlank { "Column" }
            } contains ${filterQuery.ifBlank { value }.ifBlank { content }.ifBlank { "value" }}"
            "CLEAR_TABLE_FILTER" -> "Clear table filter"
            "GROUP_TABLE", "SET_TABLE_GROUP" -> "Group table by ${
                groupByColumnName.ifBlank { columnName }.ifBlank { propertyName }.ifBlank { title }.ifBlank { groupByColumnId }.ifBlank { columnId }.ifBlank { "Column" }
            }"
            "CLEAR_TABLE_GROUP" -> "Clear table group"
            "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK" ->
                "Add row content: ${rowTitle.ifBlank { title }.ifBlank { rowId }.ifBlank { "Row" }} / ${content.ifBlank { blockType }.ifBlank { "block" }}"
            "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK",
            "CHECK_ROW_PAGE_BLOCK", "UNCHECK_ROW_PAGE_BLOCK" ->
                "Update row content: ${rowTitle.ifBlank { title }.ifBlank { rowId }.ifBlank { "Row" }} / ${rowBlockId.ifBlank { blockText }.ifBlank { "block" }}"
            "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" ->
                "Delete row content: ${rowTitle.ifBlank { title }.ifBlank { rowId }.ifBlank { "Row" }} / ${rowBlockId.ifBlank { blockText }.ifBlank { "block" }}"
            "CHANGE_TABLE_VIEW", "SET_TABLE_VIEW" -> "Change table view to ${tableView.ifBlank { value }.ifBlank { content }.ifBlank { "Table" }}"
            "CREATE_SUBPAGE" -> "Create subpage: ${title.ifBlank { "Untitled page" }}"
            "CREATE_PAGE" -> "Create page: ${title.ifBlank { "Untitled page" }}"
            "CREATE_TASK" -> "Add task row: ${title.ifBlank { "Untitled task" }}"
            "CREATE_REMINDER" -> "Add reminder row: ${title.ifBlank { "Untitled reminder" }}"
            else -> type.ifBlank { "Unknown action" }
        }
    }

    private fun ChatAction.toModuleType(): PageModuleType {
        return requestedModuleType(type.trim().uppercase())
            ?: error("Missing module type. Use Goal, Habit, Travel, or Budget.")
    }

    private fun ChatAction.requestedModuleType(actionType: String): PageModuleType? {
        val normalizedActionType = actionType.replace("_", "")
        val isModuleAction = normalizedActionType.startsWith("CREATE") &&
            (
                normalizedActionType.contains("MODULE") ||
                    normalizedActionType.contains("TRACKER") ||
                    normalizedActionType.contains("PLANNER")
                )
        if (isModuleAction) {
            return PageModuleTemplates.fromActionFields(
                moduleType,
                type,
                title,
                tableTitle,
                content,
                blockType,
            )
        }

        if (actionType != "CREATE_PAGE") return null
        if (moduleType.isNotBlank()) {
            return PageModuleType.from(moduleType)
        }
        val looksLikeModulePage = title.looksLikeModuleTitle() ||
            tableTitle.looksLikeModuleTitle() ||
            content.looksLikeModuleTitle()
        if (!looksLikeModulePage) return null
        return PageModuleTemplates.fromActionFields(
            title,
            tableTitle,
            content,
        )
    }

    private fun String.looksLikeModuleTitle(): Boolean {
        val value = trim().lowercase()
        if (value.isBlank()) return false
        return value.contains("module") ||
            value.contains("tracker") ||
            value.contains("planner") ||
            value.contains("itinerary")
    }

    private fun ChatAction.toDatabaseBlock(): PageBlock {
        val tableName = tableTitle
            .ifBlank { title }
            .ifBlank { content }
            .ifBlank { "AI database" }
        val columns = buildTableColumns()
        val rows = buildTableRows(columns)

        return PageBlockCodec.newBlock(PageBlockType.DatabaseTable).copy(
            table = PageTable(
                title = tableName,
                view = tableView.toPageTableView(),
                columns = columns,
                rows = rows,
            ),
        )
    }

    private fun PageBlockDocument.deletePropertyByName(propertyName: String): PageBlockDocument {
        val normalized = propertyName.normalizedAiKey()
        val matchingProperty = properties.firstOrNull { property ->
            property.name.normalizedAiKey() == normalized
        } ?: properties.firstOrNull { property ->
            property.name.contains(propertyName, ignoreCase = true) ||
                propertyName.contains(property.name, ignoreCase = true)
        } ?: error("Could not find property: $propertyName")

        return copy(
            properties = properties.filterNot { property -> property.id == matchingProperty.id },
        )
    }

    private fun PageBlockDocument.deleteMatchingBlock(action: ChatAction): BlockDeleteResult {
        val requestedBlockId = action.blockId.trim()
        val result = if (requestedBlockId.isNotBlank()) {
            blocks.deleteBlockById(requestedBlockId)
        } else {
            blocks.deleteFirstMatchingBlock(action)
        }
        if (!result.didDelete) {
            val label = requestedBlockId
                .ifBlank { action.blockText }
                .ifBlank { action.tableTitle }
                .ifBlank { action.content }
                .ifBlank { action.title }
                .ifBlank { action.blockType }
            error("Could not find block: $label")
        }
        return BlockDeleteResult(
            document = copy(
                blocks = result.blocks.ifEmpty { listOf(PageBlockCodec.newBlock(PageBlockType.Text)) },
            ),
            deletedLabel = result.deletedLabel.ifBlank { action.blockType.ifBlank { "block" } },
        )
    }

    private fun PageBlockDocument.updateMatchingBlock(action: ChatAction): BlockUpdateResult {
        val requestedBlockId = action.blockId.trim()
        val result = if (requestedBlockId.isNotBlank()) {
            blocks.updateBlockById(requestedBlockId, action)
        } else {
            blocks.updateFirstMatchingBlock(action)
        }
        if (!result.didUpdate) {
            val label = requestedBlockId
                .ifBlank { action.blockText }
                .ifBlank { action.tableTitle }
                .ifBlank { action.content }
                .ifBlank { action.title }
                .ifBlank { action.blockType }
            error("Could not find block: $label")
        }
        return BlockUpdateResult(
            document = copy(blocks = result.blocks),
            updatedLabel = result.updatedLabel.ifBlank { action.blockType.ifBlank { "block" } },
        )
    }

    private fun List<PageBlock>.updateBlockById(
        blockId: String,
        action: ChatAction,
    ): BlockUpdateTraversal {
        var didUpdate = false
        var updatedLabel = ""

        fun walk(blocks: List<PageBlock>): List<PageBlock> {
            return blocks.map { block ->
                if (!didUpdate && block.id == blockId) {
                    didUpdate = true
                    val updated = block.applyAiBlockUpdate(action)
                    updatedLabel = updated.deleteLabel()
                    updated
                } else {
                    block.copy(children = walk(block.children))
                }
            }
        }

        return BlockUpdateTraversal(
            blocks = walk(this),
            updatedLabel = updatedLabel,
            didUpdate = didUpdate,
        )
    }

    private fun List<PageBlock>.updateFirstMatchingBlock(action: ChatAction): BlockUpdateTraversal {
        var didUpdate = false
        var updatedLabel = ""

        fun walk(blocks: List<PageBlock>): List<PageBlock> {
            return blocks.map { block ->
                if (!didUpdate && block.matchesDeleteAction(action)) {
                    didUpdate = true
                    val updated = block.applyAiBlockUpdate(action)
                    updatedLabel = updated.deleteLabel()
                    updated
                } else {
                    block.copy(children = walk(block.children))
                }
            }
        }

        return BlockUpdateTraversal(
            blocks = walk(this),
            updatedLabel = updatedLabel,
            didUpdate = didUpdate,
        )
    }

    private fun PageBlock.applyAiBlockUpdate(action: ChatAction): PageBlock {
        val requestedType = action.blockType.toPageBlockTypeOrNull()
        val newText = action.content
            .ifBlank { action.value }
            .ifBlank { action.title }
            .trim()

        var updated = if (requestedType != null && requestedType != type) {
            copy(type = requestedType)
        } else {
            this
        }

        if (newText.isNotBlank()) {
            updated = if (updated.type == PageBlockType.DatabaseTable) {
                updated.copy(table = updated.table.copy(title = newText))
            } else {
                updated.copy(text = newText)
            }
        }

        if (action.isChecked != null && updated.type == PageBlockType.Todo) {
            updated = updated.copy(isChecked = action.isChecked)
        }

        return updated
    }

    private fun List<PageBlock>.deleteBlockById(blockId: String): BlockDeleteTraversal {
        var didDelete = false
        var deletedLabel = ""

        fun walk(blocks: List<PageBlock>): List<PageBlock> {
            return blocks.mapNotNull { block ->
                if (!didDelete && block.id == blockId) {
                    didDelete = true
                    deletedLabel = block.deleteLabel()
                    null
                } else {
                    block.copy(children = walk(block.children))
                }
            }
        }

        return BlockDeleteTraversal(
            blocks = walk(this),
            deletedLabel = deletedLabel,
            didDelete = didDelete,
        )
    }

    private fun List<PageBlock>.deleteFirstMatchingBlock(action: ChatAction): BlockDeleteTraversal {
        var didDelete = false
        var deletedLabel = ""

        fun walk(blocks: List<PageBlock>): List<PageBlock> {
            return blocks.mapNotNull { block ->
                if (!didDelete && block.matchesDeleteAction(action)) {
                    didDelete = true
                    deletedLabel = block.deleteLabel()
                    null
                } else {
                    block.copy(children = walk(block.children))
                }
            }
        }

        return BlockDeleteTraversal(
            blocks = walk(this),
            deletedLabel = deletedLabel,
            didDelete = didDelete,
        )
    }

    private fun PageBlock.matchesDeleteAction(action: ChatAction): Boolean {
        val requestedType = action.blockType.toPageBlockTypeOrNull()
        val typeMatches = requestedType == null || type == requestedType
        if (!typeMatches) return false

        if (type == PageBlockType.DatabaseTable) {
            val requestedTableTitle = action.tableTitle
                .ifBlank { action.blockText }
                .ifBlank { action.content }
                .ifBlank { action.title }
                .trim()
            if (requestedTableTitle.isBlank()) {
                return requestedType == PageBlockType.DatabaseTable
            }
            return table.title.equals(requestedTableTitle, ignoreCase = true) ||
                table.title.contains(requestedTableTitle, ignoreCase = true) ||
                requestedTableTitle.contains(table.title, ignoreCase = true)
        }

        val requestedText = action.blockText
            .ifBlank { action.content }
            .ifBlank { action.title }
            .trim()
        if (requestedText.isBlank()) {
            return requestedType != null
        }

        return text.equals(requestedText, ignoreCase = true) ||
            text.contains(requestedText, ignoreCase = true) ||
            requestedText.contains(text, ignoreCase = true)
    }

    private fun PageBlock.deleteLabel(): String {
        return if (type == PageBlockType.DatabaseTable) {
            table.title.ifBlank { "database table" }
        } else {
            text.ifBlank { type.name }
        }
    }

    private fun ChatAction.buildTableColumns(): List<PageTableColumn> {
        val fromAction = tableColumns
            .mapNotNull { column ->
                val name = column.name.trim()
                if (name.isBlank()) {
                    null
                } else {
                    column.toPageTableColumnFromAi().copy(
                        rollupRelationColumnId = "",
                        rollupTargetColumnId = "",
                    )
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

        return rowMaps.map { values ->
            columns.newRow(values)
        }
    }

    private fun ChatAction.withResolvedRelationTarget(document: PageBlockDocument): ChatAction {
        if (relationTargetTableId.isNotBlank() || relationTargetTableTitle.isBlank()) {
            return this
        }
        return copy(
            relationTargetTableId = document.findTableBlockId(relationTargetTableTitle).orEmpty(),
        )
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
                if (block.id == tableBlockId && block.type == PageBlockType.DatabaseTable) {
                    return block
                }
                walk(block.children)?.let { return it }
            }
            return null
        }
        return walk(blocks)
    }

    private fun PageBlockDocument.findBlock(blockId: String): PageBlock? {
        if (blockId.isBlank()) return null
        fun walk(blocks: List<PageBlock>): PageBlock? {
            blocks.forEach { block ->
                if (block.id == blockId) return block
                walk(block.children)?.let { return it }
            }
            return null
        }
        return walk(blocks)
    }

    private fun PageBlockDocument.updateMatchingTable(
        action: ChatAction,
        transform: (PageBlock) -> PageBlock,
    ): TableUpdateResult {
        val tableBlockId = action.blockId.trim()
        val tableName = action.tableTitle.ifBlank { action.targetTitle }.trim()
        val update = blocks.updateMatchingTableBlock(tableBlockId, tableName, transform)
        if (!update.didUpdate) {
            error(
                if (tableBlockId.isNotBlank()) {
                    "Could not find database table block: $tableBlockId"
                } else if (tableName.isBlank()) {
                    "No database table found in this page"
                } else {
                    "Could not find database table: $tableName"
                },
            )
        }
        return TableUpdateResult(
            document = copy(blocks = update.blocks),
            tableTitle = update.tableTitle.ifBlank { tableName.ifBlank { "database" } },
        )
    }

    private fun List<PageBlock>.updateMatchingTableBlock(
        tableBlockId: String,
        tableName: String,
        transform: (PageBlock) -> PageBlock,
    ): TableBlockUpdate {
        var updatedTitle = ""
        var didUpdate = false

        fun matches(block: PageBlock): Boolean {
            if (block.type != PageBlockType.DatabaseTable) return false
            if (tableBlockId.isNotBlank()) return block.id == tableBlockId
            if (tableName.isBlank()) return true
            val title = block.table.title
            return title.equals(tableName, ignoreCase = true) ||
                title.contains(tableName, ignoreCase = true) ||
                tableName.contains(title, ignoreCase = true)
        }

        fun updateBlocks(blocks: List<PageBlock>): List<PageBlock> {
            return blocks.map { block ->
                if (!didUpdate && matches(block)) {
                    didUpdate = true
                    updatedTitle = block.table.title.ifBlank { "database" }
                    transform(block)
                } else {
                    block.copy(children = updateBlocks(block.children))
                }
            }
        }

        return TableBlockUpdate(
            blocks = updateBlocks(this),
            tableTitle = updatedTitle,
            didUpdate = didUpdate,
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
                rows = table.rows.map { tableRow ->
                    if (tableRow.id == row.id) {
                        tableRow.copy(cells = tableRow.cells + (column.id to value))
                    } else {
                        tableRow
                    }
                },
            ),
        )
    }

    private fun PageBlock.deleteTableColumn(
        columnId: String,
        columnName: String,
    ): PageBlock {
        val column = table.findColumn(columnId = columnId, columnName = columnName)
            ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
        if (table.columns.size <= 1) {
            error("Cannot delete the last column")
        }

        return copy(
            table = table.copy(
                columns = table.columns
                    .filterNot { it.id == column.id }
                    .map { existing -> existing.withoutColumnReference(column.id) },
                rows = table.rows.map { row ->
                    row.copy(cells = row.cells - column.id)
                },
                sort = if (table.sort.columnId == column.id) {
                    PageTableSort()
                } else {
                    table.sort
                },
                filter = if (table.filter.columnId == column.id) {
                    PageTableFilter()
                } else {
                    table.filter
                },
                groupByColumnId = if (table.groupByColumnId == column.id) {
                    ""
                } else {
                    table.groupByColumnId
                },
            ),
        )
    }

    private fun PageBlock.renameTableColumn(
        columnId: String,
        columnName: String,
        newColumnName: String,
    ): PageBlock {
        val column = table.findColumn(columnId = columnId, columnName = columnName)
            ?: error("Could not find column: ${columnName.ifBlank { columnId }}")

        return copy(
            table = table.copy(
                columns = table.columns.map { existing ->
                    if (existing.id == column.id) {
                        existing.copy(name = newColumnName)
                    } else {
                        existing
                    }
                },
            ),
        )
    }

    private fun PageBlock.updateTableColumnType(
        columnId: String,
        columnName: String,
        columnType: PageTableColumnType,
    ): PageBlock {
        val column = table.findColumn(columnId = columnId, columnName = columnName)
            ?: error("Could not find column: ${columnName.ifBlank { columnId }}")

        return copy(
            table = table.copy(
                columns = table.columns.map { existing ->
                    if (existing.id == column.id) {
                        existing.copy(type = columnType)
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

    private fun PageTableColumn.withoutColumnReference(columnId: String): PageTableColumn {
        return copy(
            rollupRelationColumnId = if (rollupRelationColumnId == columnId) "" else rollupRelationColumnId,
            rollupTargetColumnId = if (rollupTargetColumnId == columnId) "" else rollupTargetColumnId,
        )
    }

    private fun PageTableColumn.withActionConfig(
        action: ChatAction,
        relationColumn: PageTableColumn? = null,
        resolvedRollupTargetColumnId: String = "",
    ): PageTableColumn {
        return copy(
            formula = action.formula.ifBlank { action.value }.ifBlank { action.content }.ifBlank { formula },
            relationTargetTableId = action.relationTargetTableId.ifBlank { relationTargetTableId },
            rollupRelationColumnId = relationColumn?.id
                ?: action.rollupRelationColumnId.ifBlank { rollupRelationColumnId },
            rollupTargetColumnId = resolvedRollupTargetColumnId
                .ifBlank { action.rollupTargetColumnId }
                .ifBlank { rollupTargetColumnId },
            rollupAggregation = action.rollupAggregation
                .takeIf { value -> value.isNotBlank() }
                ?.toPageTableRollupAggregation()
                ?: rollupAggregation,
        )
    }

    private fun PageBlock.resolveRollupTargetColumnId(
        action: ChatAction,
        relationColumn: PageTableColumn?,
        document: PageBlockDocument,
    ): String {
        if (action.rollupTargetColumnId.isNotBlank()) {
            return action.rollupTargetColumnId
        }
        val targetColumnName = action.rollupTargetColumnName.trim()
        if (targetColumnName.isBlank() || relationColumn == null) {
            return ""
        }
        val targetTableId = relationColumn.relationTargetTableId
        if (targetTableId.isBlank()) {
            return ""
        }
        return document.findTableBlock(targetTableId)
            ?.table
            ?.findColumn(columnName = targetColumnName)
            ?.id
            .orEmpty()
    }

    private fun PageBlock.sortTable(
        columnId: String,
        columnName: String,
        direction: PageTableSortDirection,
    ): PageBlock {
        val column = table.findColumn(columnId = columnId, columnName = columnName)
            ?: error("Could not find column: ${columnName.ifBlank { columnId }}")

        return copy(
            table = table.copy(
                sort = PageTableSort(columnId = column.id, direction = direction),
            ),
        )
    }

    private fun PageBlock.filterTable(
        columnId: String,
        columnName: String,
        query: String,
    ): PageBlock {
        val column = table.findColumn(columnId = columnId, columnName = columnName)
            ?: error("Could not find column: ${columnName.ifBlank { columnId }}")

        return copy(
            table = table.copy(
                filter = PageTableFilter(columnId = column.id, query = query),
            ),
        )
    }

    private fun PageBlock.groupTable(
        columnId: String,
        columnName: String,
    ): PageBlock {
        val column = table.findColumn(columnId = columnId, columnName = columnName)
            ?: error("Could not find column: ${columnName.ifBlank { columnId }}")

        return copy(table = table.copy(groupByColumnId = column.id))
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

    private fun PageBlock.deleteTableRow(
        rowId: String,
        rowTitle: String,
    ): PageBlock {
        val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
            ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")

        return copy(
            table = table.copy(
                rows = table.rows.filterNot { it.id == row.id },
            ),
        )
    }

    private fun PageBlock.updateTableRow(
        rowId: String,
        rowTitle: String,
        newRowTitle: String,
        cellValues: Map<String, String>,
    ): PageBlock {
        val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
            ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
        if (newRowTitle.isBlank() && cellValues.isEmpty()) {
            error("Missing row update values")
        }
        val titleColumn = table.columns.firstOrNull()

        return copy(
            table = table.copy(
                rows = table.rows.map { existing ->
                    if (existing.id != row.id) {
                        existing
                    } else {
                        val updatedCells = existing.cells.toMutableMap()
                        if (newRowTitle.isNotBlank() && titleColumn != null) {
                            updatedCells[titleColumn.id] = newRowTitle
                        }
                        cellValues.forEach { (columnKey, cellValue) ->
                            val column = table.findColumn(columnName = columnKey)
                                ?: error("Could not find column: $columnKey")
                            updatedCells[column.id] = cellValue
                        }
                        existing.copy(cells = updatedCells)
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
        if (row.blocks.isEmpty()) {
            error("No row content found in ${rowTitle.ifBlank { rowId }}")
        }

        val effectiveAction = when (action.type.trim().uppercase()) {
            "CHECK_ROW_PAGE_BLOCK" -> action.copy(isChecked = true)
            "UNCHECK_ROW_PAGE_BLOCK" -> action.copy(isChecked = false)
            else -> action
        }
        val update = if (rowBlockId.isNotBlank()) {
            row.blocks.updateBlockById(rowBlockId, effectiveAction)
        } else {
            row.blocks.updateFirstMatchingBlock(effectiveAction)
        }
        if (!update.didUpdate) {
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
                    if (existing.id == row.id) {
                        existing.copy(blocks = update.blocks)
                    } else {
                        existing
                    }
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
        if (row.blocks.isEmpty()) {
            error("No row content found in ${rowTitle.ifBlank { rowId }}")
        }

        val delete = if (rowBlockId.isNotBlank()) {
            row.blocks.deleteBlockById(rowBlockId)
        } else {
            row.blocks.deleteFirstMatchingBlock(action)
        }
        if (!delete.didDelete) {
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
                    if (existing.id == row.id) {
                        existing.copy(blocks = delete.blocks)
                    } else {
                        existing
                    }
                },
            ),
        )
    }

    private fun PageTable.newRowFromAction(action: ChatAction): PageTableRow {
        val values = action.cellValues.toMutableMap()
        val title = action.rowTitle.ifBlank { action.title }
        if (title.isNotBlank() && columns.isNotEmpty()) {
            val firstColumn = columns.first()
            val hasFirstColumnValue = values.keys.any { key ->
                key.equals(firstColumn.name, ignoreCase = true)
            }
            if (!hasFirstColumnValue) {
                values[firstColumn.name] = title
            }
        }
        return columns.newRow(values)
    }

    private fun List<PageTableColumn>.newRow(valuesByColumnName: Map<String, String>): PageTableRow {
        val valuesByNormalizedName = valuesByColumnName.entries.associate { entry ->
            entry.key.normalizedAiKey() to entry.value
        }
        return PageBlockCodec.newTableRow(this).copy(
            cells = associate { column ->
                column.id to valuesByNormalizedName[column.name.normalizedAiKey()].orEmpty()
            },
        )
    }

    private fun PageTable.findColumn(
        columnId: String = "",
        columnName: String,
    ): PageTableColumn? {
        if (columnId.isNotBlank()) {
            columns.firstOrNull { column -> column.id == columnId }?.let { return it }
        }
        if (columnName.isBlank()) return null
        val normalized = columnName.normalizedAiKey()
        return columns.firstOrNull { column -> column.name.normalizedAiKey() == normalized }
            ?: columns.firstOrNull { column -> column.name.contains(columnName, ignoreCase = true) }
            ?: columns.firstOrNull { column -> columnName.contains(column.name, ignoreCase = true) }
    }

    private fun ChatAction.toTableViewConfig(table: PageTable): PageTableViewConfig {
        fun resolveColumnId(columnId: String, columnName: String): String {
            return table.findColumn(columnId = columnId, columnName = columnName)?.id.orEmpty()
        }

        val tableViewName = tableView.ifBlank { value }.ifBlank { content }.lowercase()
        val genericCalendarId = if (tableViewName == "calendar") {
            resolveColumnId(columnId, columnName)
        } else {
            ""
        }
        val genericTimelineId = if (tableViewName == "timeline") {
            resolveColumnId(columnId, columnName)
        } else {
            ""
        }
        val genericDashboardId = if (tableViewName == "dashboard" || tableViewName == "chart" || tableViewName == "charts") {
            resolveColumnId(columnId, columnName)
        } else {
            ""
        }

        val calendarDateId = resolveColumnId(calendarDateColumnId, calendarDateColumnName)
            .ifBlank { genericCalendarId }
            .ifBlank { table.viewConfig.calendarDateColumnId }
        val timelineStartId = resolveColumnId(timelineStartColumnId, timelineStartColumnName)
            .ifBlank { genericTimelineId }
            .ifBlank { table.viewConfig.timelineStartColumnId }
        val timelineEndId = resolveColumnId(timelineEndColumnId, timelineEndColumnName)
            .ifBlank { table.viewConfig.timelineEndColumnId }
        val dashboardMetricId = resolveColumnId(dashboardMetricColumnId, dashboardMetricColumnName)
            .ifBlank { genericDashboardId }
            .ifBlank { table.viewConfig.dashboardMetricColumnId }
        val dashboardGroupId = resolveColumnId(dashboardGroupColumnId, dashboardGroupColumnName)
            .ifBlank {
                resolveColumnId(groupByColumnId, groupByColumnName)
            }
            .ifBlank { table.viewConfig.dashboardGroupColumnId }

        return table.viewConfig.copy(
            calendarDateColumnId = calendarDateId,
            timelineStartColumnId = timelineStartId,
            timelineEndColumnId = timelineEndId,
            dashboardMetricColumnId = dashboardMetricId,
            dashboardGroupColumnId = dashboardGroupId,
        )
    }

    private fun PageTable.findRow(
        rowId: String = "",
        rowTitle: String,
    ): PageTableRow? {
        if (rowId.isNotBlank()) {
            rows.firstOrNull { row -> row.id == rowId }?.let { return it }
        }
        if (rowTitle.isBlank()) return null
        val titleColumn = columns.firstOrNull()
        return rows.firstOrNull { row ->
            row.cellText(titleColumn).equals(rowTitle, ignoreCase = true)
        } ?: rows.firstOrNull { row ->
            val cellText = row.cellText(titleColumn)
            cellText.isNotBlank() && (
                cellText.contains(rowTitle, ignoreCase = true) ||
                    rowTitle.contains(cellText, ignoreCase = true)
                )
        }
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

    private fun PageTableRow.cellText(column: PageTableColumn?): String {
        return column?.let { tableColumn -> cells[tableColumn.id] }.orEmpty().trim()
    }

    private fun PageTable.contextStateText(): String {
        val sortText = if (sort.columnId.isBlank()) {
            "none"
        } else {
            "${sort.columnId}:${sort.direction.name}"
        }
        val filterText = if (filter.columnId.isBlank() || filter.query.isBlank()) {
            "none"
        } else {
            "${filter.columnId}:${filter.query}"
        }
        val groupText = groupByColumnId.ifBlank { "none" }
        val viewConfigText = listOf(
            "calendarDate=${viewConfig.calendarDateColumnId.ifBlank { "auto" }}",
            "timelineStart=${viewConfig.timelineStartColumnId.ifBlank { "auto" }}",
            "timelineEnd=${viewConfig.timelineEndColumnId.ifBlank { "none" }}",
            "dashboardMetric=${viewConfig.dashboardMetricColumnId.ifBlank { "auto" }}",
            "dashboardGroup=${viewConfig.dashboardGroupColumnId.ifBlank { "auto" }}",
        ).joinToString(",")
        return "sort=$sortText; filter=$filterText; groupBy=$groupText; viewConfig=$viewConfigText"
    }

    private fun PageTableColumn.aiContextText(): String {
        val config = when (type) {
            PageTableColumnType.Formula -> " formula=${formula.ifBlank { "none" }}"
            PageTableColumnType.Relation -> " relationTargetTableId=${relationTargetTableId.ifBlank { "none" }}"
            PageTableColumnType.Rollup -> " rollupRelationColumnId=${rollupRelationColumnId.ifBlank { "none" }} rollupTargetColumnId=${rollupTargetColumnId.ifBlank { "none" }} rollupAggregation=${rollupAggregation.name}"
            PageTableColumnType.Text,
            PageTableColumnType.Number,
            PageTableColumnType.Status,
            PageTableColumnType.Date,
            PageTableColumnType.Checkbox,
            PageTableColumnType.FilesMedia,
            -> ""
        }
        return "$id:$name:${type.name}$config"
    }

    private fun List<PageTextSpan>.normalizedForText(text: String): List<PageTextSpan> {
        if (text.isEmpty()) return emptyList()
        return mapNotNull { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(0, text.length)
            if (start >= end || !span.hasAnyStyle()) {
                null
            } else {
                span.copy(start = start, end = end)
            }
        }.mergeAdjacentTextSpans()
    }

    private fun List<PageTextSpan>.mergeAdjacentTextSpans(): List<PageTextSpan> {
        if (isEmpty()) return emptyList()
        return sortedWith(compareBy<PageTextSpan> { it.start }.thenBy { it.end })
            .fold(mutableListOf()) { merged, span ->
                val last = merged.lastOrNull()
                if (last != null && last.end >= span.start && last.sameStyleAs(span)) {
                    merged[merged.lastIndex] = last.copy(end = maxOf(last.end, span.end))
                } else {
                    merged += span
                }
                merged
            }
    }

    private fun PageTextSpan.hasAnyStyle(): Boolean {
        return bold || italic || underline || strikethrough
    }

    private fun PageTextSpan.sameStyleAs(other: PageTextSpan): Boolean {
        return bold == other.bold &&
            italic == other.italic &&
            underline == other.underline &&
            strikethrough == other.strikethrough
    }

    private fun String.toPageBlockType(): PageBlockType {
        return when (normalizedAiKey()) {
            "heading", "title", "h1" -> PageBlockType.Heading
            "todo", "task", "checkbox", "checklist" -> PageBlockType.Todo
            "bullet", "list", "bulletedlist" -> PageBlockType.Bullet
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

    private fun String.inferTableColumnType(): PageTableColumnType {
        return toPageTableColumnType()
    }

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

    private fun PageBlockDocument.upsertProperty(
        name: String,
        type: PagePropertyType,
        value: String,
    ): PageBlockDocument {
        val existing = properties.firstOrNull { property ->
            property.name.equals(name, ignoreCase = true)
        }
        return if (existing == null) {
            copy(
                properties = properties + PageBlockCodec.newProperty(type, name).copy(value = value),
            )
        } else {
            copy(
                properties = properties.map { property ->
                    if (property.id == existing.id) {
                        property.copy(
                            type = type,
                            value = value.ifBlank { property.value },
                        )
                    } else {
                        property
                    }
                },
            )
        }
    }

    private fun String.toPageContentDocument(): String {
        return PageBlockCodec.encode(
            listOf(
                PageBlockCodec.newBlock(PageBlockType.Text).copy(text = trim()),
            ),
        )
    }

    private fun String.normalizedAiKey(): String {
        return trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }

    fun summarizePage(onSuccess: (String) -> Unit) {
        val page = uiState.value.page ?: return
        val currentBlocks = uiState.value.blocks
        val textContent = currentBlocks.joinToString("\n") { it.text }
        if (textContent.isBlank()) {
            _aiError.value = "Page content is empty. Add some text first."
            return
        }

        viewModelScope.launch {
            _isAiGenerating.value = true
            _aiError.value = null
            aiRepository.summarize(textContent)
                .onSuccess { summary ->
                    _isAiGenerating.value = false
                    onSuccess(summary)

                    // Prepend a Quote block at the top with the summary
                    val summaryBlock = PageBlockCodec.newBlock(PageBlockType.Quote).copy(
                        text = "Summary: $summary"
                    )
                    val document = _pendingChanges.value
                        ?: PageBlockCodec.decodeDocument(page.content)
                    val updated = document.copy(
                        blocks = listOf(summaryBlock) + document.blocks
                    )
                    _pendingChanges.value = updated
                }
                .onFailure { error ->
                    _isAiGenerating.value = false
                    _aiError.value = error.localizedMessage
                }
        }
    }

    fun extractTasksFromPage(onTasksExtracted: (List<String>) -> Unit) {
        val page = uiState.value.page ?: return
        val currentBlocks = uiState.value.blocks
        val textContent = currentBlocks.joinToString("\n") { it.text }
        if (textContent.isBlank()) {
            _aiError.value = "Page content is empty. Add some text first."
            return
        }

        viewModelScope.launch {
            _isAiGenerating.value = true
            _aiError.value = null
            aiRepository.generateTasks(textContent)
                .onSuccess { tasks ->
                    _isAiGenerating.value = false
                    onTasksExtracted(tasks)
                }
                .onFailure { error ->
                    _isAiGenerating.value = false
                    _aiError.value = error.localizedMessage
                }
        }
    }

    fun createExtractedTasks(tasks: List<String>) {
        val page = uiState.value.page ?: return
        viewModelScope.launch {
            tasks.forEach { title ->
                taskRepository.createTask(
                    workspaceId = page.workspaceId,
                    title = title,
                    notes = "Extracted from page: ${uiState.value.title.ifBlank { "Untitled" }}",
                    dueAt = null,
                    priority = 1,
                    pageId = page.id
                )
            }
        }
    }

    fun generatePlan(prompt: String) {
        val page = uiState.value.page ?: return
        if (prompt.isBlank()) return

        viewModelScope.launch {
            _isAiGenerating.value = true
            _aiError.value = null
            aiRepository.generatePlan(prompt)
                .onSuccess { doc ->
                    _isAiGenerating.value = false
                    val document = _pendingChanges.value
                        ?: PageBlockCodec.decodeDocument(page.content)
                    
                    // Merge properties
                    val existingNames = document.properties.map { it.name }.toSet()
                    val mergedProperties = document.properties + doc.properties.filter { it.name !in existingNames }
                    
                    // Append blocks
                    val mergedBlocks = document.blocks + doc.blocks
                    
                    val updated = document.copy(
                        properties = mergedProperties,
                        blocks = mergedBlocks
                    )
                    _pendingChanges.value = updated
                }
                .onFailure { error ->
                    _isAiGenerating.value = false
                    _aiError.value = error.localizedMessage
                }
        }
    }
}

private sealed interface PendingGranularDocumentSave {
    val document: PageBlockDocument

    data class BlockText(
        override val document: PageBlockDocument,
        val blockId: String,
        val text: String,
    ) : PendingGranularDocumentSave

    data class BlockPatch(
        override val document: PageBlockDocument,
        val block: PageBlock,
    ) : PendingGranularDocumentSave

    data class PropertyValue(
        override val document: PageBlockDocument,
        val propertyId: String,
        val value: String,
    ) : PendingGranularDocumentSave

    data class TableCellValue(
        override val document: PageBlockDocument,
        val rowId: String,
        val columnId: String,
        val value: String,
    ) : PendingGranularDocumentSave

    data class TablePatch(
        override val document: PageBlockDocument,
        val tableBlockId: String,
        val table: PageTable,
    ) : PendingGranularDocumentSave

    data class TableColumnPatch(
        override val document: PageBlockDocument,
        val tableBlockId: String,
        val column: PageTableColumn,
    ) : PendingGranularDocumentSave

    data class TableRowPatch(
        override val document: PageBlockDocument,
        val tableBlockId: String,
        val row: PageTableRow,
    ) : PendingGranularDocumentSave
}

data class PageEditorUiState(
    val isLoading: Boolean = true,
    val page: Page? = null,
    val title: String = "",
    val properties: List<PageProperty> = emptyList(),
    val blocks: List<PageBlock> = emptyList(),
    val childPages: List<Page> = emptyList(),
    val isSaving: Boolean = false,
    val isAiGenerating: Boolean = false,
    val aiError: String? = null,
    val canUndoEditorChange: Boolean = false,
    val syncState: PageSyncState = PageSyncState(),
)

private data class PageEditorAiState(
    val isAiGenerating: Boolean,
    val aiError: String?,
)

private data class PageAiExecutionResult(
    val messages: List<String>,
    val createdPages: List<Page> = emptyList(),
    val createdTasks: List<TaskItem> = emptyList(),
    val createdReminders: List<Reminder> = emptyList(),
    val pageLinks: List<AiChatPageLink> = emptyList(),
)

private data class TableUpdateResult(
    val document: PageBlockDocument,
    val tableTitle: String,
)

private data class TableBlockUpdate(
    val blocks: List<PageBlock>,
    val tableTitle: String,
    val didUpdate: Boolean,
)

private data class BlockDeleteResult(
    val document: PageBlockDocument,
    val deletedLabel: String,
)

private data class BlockUpdateResult(
    val document: PageBlockDocument,
    val updatedLabel: String,
)

private data class BlockDeleteTraversal(
    val blocks: List<PageBlock>,
    val deletedLabel: String,
    val didDelete: Boolean,
)

private data class BlockUpdateTraversal(
    val blocks: List<PageBlock>,
    val updatedLabel: String,
    val didUpdate: Boolean,
)

private fun Page.toChatPageLink(): AiChatPageLink {
    return AiChatPageLink(
        pageId = id,
        title = title.ifBlank { "Untitled page" },
    )
}

private fun Throwable.toPageAiExecutionErrorMessage(): String {
    val root = generateSequence(this) { error -> error.cause }.last()
    val detail = root.localizedMessage?.takeIf { message -> message.isNotBlank() }
        ?: "AI edit failed before it could update the page. (${root.javaClass.simpleName})"
    return "Failed: $detail"
}
