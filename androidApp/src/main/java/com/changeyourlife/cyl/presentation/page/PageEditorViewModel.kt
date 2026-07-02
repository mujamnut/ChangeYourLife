package com.changeyourlife.cyl.presentation.page

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.domain.model.EditorCommand
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageContentCodec
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
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.usecase.AppliedEditorCommand
import com.changeyourlife.cyl.domain.usecase.ApplyEditorCommandUseCase
import com.changeyourlife.cyl.domain.usecase.EditorCommandHistory
import com.changeyourlife.cyl.domain.usecase.PageMutationUseCase
import com.changeyourlife.cyl.domain.usecase.TableMutationResult
import com.changeyourlife.cyl.domain.usecase.TableMutationUseCase
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
enum class TableColumnInsertSide {
    Left,
    Right,
}

@HiltViewModel
class PageEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pageRepository: PageRepository,
    private val applyEditorCommandUseCase: ApplyEditorCommandUseCase,
    private val pageMutationUseCase: PageMutationUseCase,
    private val tableMutationUseCase: TableMutationUseCase,
) : ViewModel() {
    private val pageId: String = checkNotNull(savedStateHandle["pageId"])

    private val _pendingChanges = MutableStateFlow<PageBlockDocument?>(null)
    private val _pendingGranularDocumentSave = MutableStateFlow<PendingGranularDocumentSave?>(null)
    private val _pendingTitle = MutableStateFlow<String?>(null)
    private val _canUndoEditorChange = MutableStateFlow(false)
    private val editorUndoSnapshots = ArrayDeque<PageBlockDocument>()
    private val editorCommandHistory = EditorCommandHistory(
        applyEditorCommandUseCase = applyEditorCommandUseCase,
        maxEntries = MaxEditorUndoSnapshots,
    )

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

    val uiState: StateFlow<PageEditorUiState> = combine(
        _dbState,
        _pendingChanges,
        _pendingTitle,
        _canUndoEditorChange,
    ) { dbState, pendingDoc, pendingTitle, canUndoEditorChange ->
        if (dbState.page != null) {
            dbState.copy(
                title = pendingTitle ?: dbState.title,
                properties = pendingDoc?.properties ?: dbState.properties,
                blocks = pendingDoc?.blocks ?: dbState.blocks,
                isSaving = pendingDoc != null || pendingTitle != null,
                canUndoEditorChange = canUndoEditorChange,
            )
        } else {
            dbState.copy(
                isSaving = pendingDoc != null || pendingTitle != null,
                canUndoEditorChange = canUndoEditorChange,
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

    private fun PageBlockDocument.replaceTableWithCommand(
        tableBlockId: String,
        transform: (PageTable) -> PageTable,
    ) = tableMutationUseCase.replaceTable(
        document = this,
        tableBlockId = tableBlockId,
        transform = transform,
    ).commandResult

    private fun PageBlockDocument.replaceTableRowBlocksWithCommand(
        tableBlockId: String,
        rowId: String,
        command: (PageBlockDocument) -> EditorCommand,
    ) = tableMutationUseCase.updateRowBlocks(
        document = this,
        tableBlockId = tableBlockId,
        rowId = rowId,
        command = command,
    ).commandResult.also { result ->
        if (result.changed) {
            recordEditorUndoCommand(result.undoCommand)
        }
    }

    private fun queueGranularDocumentUpdate(
        previous: PageBlockDocument,
        fallback: PageBlockDocument,
        recordUndo: Boolean = true,
        mutation: suspend () -> Boolean,
    ) {
        viewModelScope.launch {
            flushPendingEdits()
            if (mutation()) {
                if (recordUndo) {
                    pushEditorUndoSnapshot(previous)
                }
            } else {
                queueDocumentUpdate(fallback, previous = previous, recordUndo = recordUndo)
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
        updateCanUndoEditorChange()
    }

    private fun recordEditorUndoCommand(command: EditorCommand?) {
        editorCommandHistory.recordUndoCommand(command)
        updateCanUndoEditorChange()
    }

    private fun recordEditorUndo(applied: AppliedEditorCommand) {
        editorCommandHistory.record(applied)
        updateCanUndoEditorChange()
    }

    private fun recordTableUndo(result: TableMutationResult) {
        if (result.changed) {
            recordEditorUndoCommand(result.commandResult.undoCommand)
        }
    }

    private fun updateCanUndoEditorChange() {
        _canUndoEditorChange.value = editorCommandHistory.canUndo || editorUndoSnapshots.isNotEmpty()
    }

    fun undoLastEditorChange() {
        val document = currentDocument() ?: return
        val commandUndo = editorCommandHistory.undo(document)
        if (commandUndo != null && commandUndo.changed) {
            _pendingChanges.value = commandUndo.document.normalizedForEditor()
            updateCanUndoEditorChange()
            return
        }
        if (editorUndoSnapshots.isEmpty()) return
        _pendingChanges.value = editorUndoSnapshots.removeLast().normalizedForEditor()
        updateCanUndoEditorChange()
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
            val result = pageMutationUseCase.updateBlockText(
                document = document,
                blockId = blockId,
                text = text,
            )
            if (!result.changed) return
            queueGranularPendingDocument(result.document) { normalized ->
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
            val result = pageMutationUseCase.updateBlockRichText(
                document = document,
                blockId = blockId,
                text = text,
                spans = spans,
            )
            if (!result.changed) return
            queueBlockPatchPendingDocument(blockId, result.document)
        }
    }

    fun pasteBlocks(
        blockId: String,
        pasteBlocks: List<RichTextPasteBlock>,
    ) {
        if (pasteBlocks.isEmpty()) return
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val replacementBlocks = pasteBlocks.map { pasteBlock ->
                PageContentCodec.newBlock(pasteBlock.type).copy(
                    text = pasteBlock.text,
                    richTextSpans = pasteBlock.spans,
                    isChecked = pasteBlock.isChecked,
                )
            }
            val result = pageMutationUseCase.replaceBlockWithBlocks(
                document = document,
                blockId = blockId,
                replacementBlocks = replacementBlocks,
            )
            if (!result.changed) return
            queueDocumentUpdate(
                updated = result.document,
                previous = document,
                recordUndo = true,
            )
        }
    }

    fun changeBlockType(blockId: String, type: PageBlockType) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.changeBlockType(
                document = document,
                blockId = blockId,
                type = type,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueBlockPatchPendingDocument(
                blockId = blockId,
                updated = result.document,
            )
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
            val result = pageMutationUseCase.addBlockMediaAttachments(
                document = document,
                blockId = blockId,
                attachments = attachments,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueBlockPatchPendingDocument(blockId, result.document)
        }
    }

    fun removeBlockMediaAttachment(
        blockId: String,
        attachmentId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.removeBlockMediaAttachment(
                document = document,
                blockId = blockId,
                attachmentId = attachmentId,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueBlockPatchPendingDocument(blockId, result.document)
        }
    }

    fun toggleTodoBlock(blockId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.toggleTodoBlock(
                document = document,
                blockId = blockId,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueBlockPatchPendingDocument(
                blockId = blockId,
                updated = result.document,
            )
        }
    }

    fun addBlock(type: PageBlockType) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.addBlock(
                document = document,
                type = type,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
            ) {
                pageRepository.addBlock(
                    pageId = pageId,
                    block = result.block,
                )
            }
        }
    }

    fun addChildBlock(parentBlockId: String, type: PageBlockType) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.addBlock(
                document = document,
                type = type,
                parentBlockId = parentBlockId,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
            ) {
                pageRepository.addBlock(
                    pageId = pageId,
                    block = result.block,
                    parentBlockId = parentBlockId,
                )
            }
        }
    }

    fun insertBlockNear(
        blockId: String,
        type: PageBlockType,
        position: PageBlockInsertPosition,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.insertBlockNear(
                document = document,
                blockId = blockId,
                type = type,
                position = position,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            val insertCommand = result.insertCommand
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
            ) {
                pageRepository.addBlock(
                    pageId = pageId,
                    block = result.block,
                    parentBlockId = insertCommand?.parentBlockId.orEmpty(),
                    targetIndex = insertCommand?.index,
                )
            }
        }
    }

    fun createLinkedChildPageFromBlock(blockId: String) {
        viewModelScope.launch {
            val parent = pageRepository.getPage(pageId) ?: return@launch
            val childPage = pageRepository.createPage(
                workspaceId = parent.workspaceId,
                title = "",
                content = PageBlockCodec.encode(listOf(PageBlockCodec.newBlock(PageBlockType.Text))),
                parentPageId = parent.id,
            )
            val currentUiState = uiState.value
            val document = currentDocument(currentUiState) ?: return@launch
            val block = document.findBlock(blockId) ?: return@launch
            val linked = block.withAppendedPageMention(childPage)
            val result = pageMutationUseCase.updateBlockRichText(
                document = document,
                blockId = blockId,
                text = linked.text,
                spans = linked.richTextSpans,
            )
            if (!result.changed) return@launch
            recordEditorUndo(result.applied)
            queueBlockPatchPendingDocument(blockId, result.document)
        }
    }

    fun deleteBlock(blockId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.deleteBlock(
                document = document,
                blockId = blockId,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document.normalizedForEditor(),
                recordUndo = false,
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
            val result = pageMutationUseCase.moveBlock(
                document = document,
                blockId = blockId,
                direction = -1,
            )
            if (!result.changed) return
            val targetIndex = result.targetIndex ?: return
            recordEditorUndo(result.applied)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
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
            val result = pageMutationUseCase.moveBlock(
                document = document,
                blockId = blockId,
                direction = 1,
            )
            if (!result.changed) return
            val targetIndex = result.targetIndex ?: return
            recordEditorUndo(result.applied)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
            ) {
                pageRepository.moveBlock(
                    pageId = pageId,
                    blockId = blockId,
                    targetIndex = targetIndex,
                )
            }
        }
    }

    fun indentBlock(blockId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.indentBlock(
                document = document,
                blockId = blockId,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueDocumentUpdate(result.document)
        }
    }

    fun outdentBlock(blockId: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.outdentBlock(
                document = document,
                blockId = blockId,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueDocumentUpdate(result.document)
        }
    }

    fun updateTableTitle(blockId: String, title: String) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = tableMutationUseCase.updateTitle(document, blockId, title)
            if (!result.changed) return
            recordTableUndo(result)
            queueTablePatchPendingDocument(blockId, result.document)
        }
    }

    fun updateTableView(
        blockId: String,
        view: PageTableView,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = tableMutationUseCase.updateView(document, blockId, view)
            if (!result.changed) return
            recordTableUndo(result)
            queueTablePatchPendingDocument(blockId, result.document)
        }
    }

    fun updateTableViewConfig(
        blockId: String,
        config: PageTableViewConfig,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = tableMutationUseCase.updateViewConfig(document, blockId, config)
            if (!result.changed) return
            recordTableUndo(result)
            queueTablePatchPendingDocument(blockId, result.document)
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
            val result = tableMutationUseCase.updateSort(document, blockId, columnId, direction)
            if (!result.changed) return
            recordTableUndo(result)
            queueTablePatchPendingDocument(blockId, result.document)
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
            val result = tableMutationUseCase.updateFilter(document, blockId, columnId, query)
            if (!result.changed) return
            recordTableUndo(result)
            queueTablePatchPendingDocument(blockId, result.document)
        }
    }

    fun updateTableGroup(
        blockId: String,
        columnId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = tableMutationUseCase.updateGroup(document, blockId, columnId)
            if (!result.changed) return
            recordTableUndo(result)
            queueTablePatchPendingDocument(blockId, result.document)
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
            val result = tableMutationUseCase.updateColumnName(document, blockId, columnId, name)
            if (!result.changed) return
            recordTableUndo(result)
            queueTableColumnPatchPendingDocument(blockId, columnId, result.document)
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
            val result = tableMutationUseCase.updateColumnType(document, blockId, columnId, type)
            if (!result.changed) return
            recordTableUndo(result)
            queueTableColumnPatchPendingDocument(
                tableBlockId = blockId,
                columnId = columnId,
                updated = result.document,
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
            val result = tableMutationUseCase.updateColumnDateSettings(
                document = document,
                tableBlockId = blockId,
                columnId = columnId,
                dateFormat = dateFormat,
                timeFormat = timeFormat,
                dateReminder = dateReminder,
                timezoneLabel = timezoneLabel,
            )
            if (!result.changed) return
            recordTableUndo(result)
            queueTableColumnPatchPendingDocument(
                tableBlockId = blockId,
                columnId = columnId,
                updated = result.document,
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
            val result = tableMutationUseCase.updateColumnFormula(document, blockId, columnId, formula)
            if (!result.changed) return
            recordTableUndo(result)
            queueTableColumnPatchPendingDocument(blockId, columnId, result.document)
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
            val result = tableMutationUseCase.updateColumnRelationTarget(document, blockId, columnId, targetTableId)
            if (!result.changed) return
            recordTableUndo(result)
            queueTableColumnPatchPendingDocument(blockId, columnId, result.document)
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
            val result = tableMutationUseCase.updateColumnRollup(
                document = document,
                tableBlockId = blockId,
                columnId = columnId,
                relationColumnId = relationColumnId,
                targetColumnId = targetColumnId,
                aggregation = aggregation,
            )
            if (!result.changed) return
            recordTableUndo(result)
            queueTableColumnPatchPendingDocument(blockId, columnId, result.document)
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
            val result = tableMutationUseCase.updateCell(document, blockId, rowId, columnId, value)
            result.coercedValue?.let { nextValue ->
                if (!result.changed) return
                recordTableUndo(result.mutation)
                queueGranularPendingDocument(result.document) { normalized ->
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
            val result = tableMutationUseCase.addColumn(document, blockId, column)
            if (!result.changed) return
            recordTableUndo(result)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
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
            val result = tableMutationUseCase.addColumn(document, blockId, column, targetIndex = insertIndex)
            if (!result.changed) return
            recordTableUndo(result)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
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
            document.findTableBlock(blockId)?.table?.let { table ->
                val sourceIndex = table.columns.indexOfFirst { column -> column.id == columnId }
                val sourceColumn = table.columns.getOrNull(sourceIndex) ?: return
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
            }
            val columnToAdd = duplicatedColumn
            if (columnToAdd == null) {
                return
            }
            val result = tableMutationUseCase.duplicateColumn(document, blockId, columnId, columnToAdd)
            if (result.insertIndex == null || !result.changed) {
                return
            }
            recordTableUndo(result.mutation)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
            ) {
                pageRepository.addTableColumn(
                    pageId = pageId,
                    tableBlockId = blockId,
                    column = columnToAdd,
                    cellValues = result.cellValues,
                    targetIndex = result.insertIndex,
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
            val result = tableMutationUseCase.deleteColumn(document, blockId, columnId)
            if (!result.changed) return
            recordTableUndo(result)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
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
            val result = tableMutationUseCase.addRow(document, blockId, row)
            if (!result.changed) return
            recordTableUndo(result)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
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
            val result = tableMutationUseCase.deleteRow(document, blockId, rowId)
            if (!result.changed) return
            recordTableUndo(result)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
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
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) { rowDocument ->
                EditorCommand.UpdateBlockText(
                    blockId = rowBlockId,
                    text = text,
                    richTextSpans = rowDocument.findBlock(rowBlockId)?.richTextSpans.orEmpty(),
                )
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
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
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) {
                EditorCommand.UpdateBlockText(
                    blockId = rowBlockId,
                    text = text,
                    richTextSpans = spans,
                )
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
            )
        }
    }

    fun pasteTableRowBlocks(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
        pasteBlocks: List<RichTextPasteBlock>,
    ) {
        if (pasteBlocks.isEmpty()) return
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val replacementBlocks = pasteBlocks.map { pasteBlock ->
                PageContentCodec.newBlock(pasteBlock.type).copy(
                    text = pasteBlock.text,
                    richTextSpans = pasteBlock.spans,
                    isChecked = pasteBlock.isChecked,
                )
            }
            val result = tableMutationUseCase.replaceRowBlockWithBlocks(
                document = document,
                tableBlockId = tableBlockId,
                rowId = rowId,
                rowBlockId = rowBlockId,
                replacementBlocks = replacementBlocks,
            )
            if (!result.changed) return
            recordTableUndo(result)
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
            )
        }
    }

    fun changeTableRowBlockType(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
        type: PageBlockType,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) {
                EditorCommand.ChangeBlockType(rowBlockId, type)
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
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
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) { rowDocument ->
                val block = rowDocument.findBlock(rowBlockId)
                EditorCommand.ReplaceBlockMediaAttachments(
                    blockId = rowBlockId,
                    mediaAttachments = block?.mediaAttachments.orEmpty() + attachments,
                )
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
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
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) { rowDocument ->
                val block = rowDocument.findBlock(rowBlockId)
                EditorCommand.ReplaceBlockMediaAttachments(
                    blockId = rowBlockId,
                    mediaAttachments = block?.mediaAttachments.orEmpty().filterNot { attachment ->
                        attachment.id == attachmentId
                    },
                )
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
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
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) {
                EditorCommand.ToggleTodo(rowBlockId)
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
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
            val newBlock = PageBlockCodec.newBlock(type)
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) {
                EditorCommand.InsertBlock(block = newBlock)
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
            )
        }
    }

    fun insertTableRowPageBlockNear(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
        type: PageBlockType,
        position: PageBlockInsertPosition,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val newBlock = PageBlockCodec.newBlock(type)
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) { rowDocument ->
                pageMutationUseCase.insertBlockNearCommand(
                    document = rowDocument,
                    blockId = rowBlockId,
                    block = newBlock,
                    position = position,
                ) ?: EditorCommand.InsertBlock(block = newBlock)
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
            )
        }
    }

    fun createLinkedChildPageFromTableRowBlock(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
    ) {
        viewModelScope.launch {
            val parent = pageRepository.getPage(pageId) ?: return@launch
            val childPage = pageRepository.createPage(
                workspaceId = parent.workspaceId,
                title = "",
                content = PageBlockCodec.encode(listOf(PageBlockCodec.newBlock(PageBlockType.Text))),
                parentPageId = parent.id,
            )
            val currentUiState = uiState.value
            val document = currentDocument(currentUiState) ?: return@launch
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) { rowDocument ->
                val block = rowDocument.findBlock(rowBlockId)
                val linked = block?.withAppendedPageMention(childPage)
                EditorCommand.UpdateBlockText(
                    blockId = rowBlockId,
                    text = linked?.text.orEmpty(),
                    richTextSpans = linked?.richTextSpans.orEmpty(),
                )
            }
            if (!result.changed) return@launch
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
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
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) {
                EditorCommand.DeleteBlock(rowBlockId)
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
            )
        }
    }

    fun indentTableRowPageBlock(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) { rowDocument ->
                pageMutationUseCase.indentBlockCommand(rowDocument, rowBlockId)
                    ?: rowDocument.noOpRowBlockCommand(rowBlockId)
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
            )
        }
    }

    fun outdentTableRowPageBlock(
        tableBlockId: String,
        rowId: String,
        rowBlockId: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = document.replaceTableRowBlocksWithCommand(tableBlockId, rowId) { rowDocument ->
                pageMutationUseCase.outdentBlockCommand(rowDocument, rowBlockId)
                    ?: rowDocument.noOpRowBlockCommand(rowBlockId)
            }
            if (!result.changed) return
            queueTableRowPatchPendingDocument(
                tableBlockId = tableBlockId,
                rowId = rowId,
                updated = result.document,
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
            val result = pageMutationUseCase.addProperty(
                document = document,
                type = type,
                name = name,
            )
            if (!result.changed) return
            val property = result.property ?: return
            recordEditorUndo(result.applied)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
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
            val result = pageMutationUseCase.updatePropertyName(
                document = document,
                propertyId = propertyId,
                name = name,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            _pendingChanges.value = result.document.normalizedForEditor()
        }
    }

    fun updatePropertyValue(
        propertyId: String,
        value: String,
    ) {
        val currentUiState = uiState.value
        if (currentUiState.page != null) {
            val document = currentDocument(currentUiState) ?: return
            val result = pageMutationUseCase.updatePropertyValue(
                document = document,
                propertyId = propertyId,
                value = value,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueGranularPendingDocument(result.document) { normalized ->
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
            val result = pageMutationUseCase.deleteProperty(
                document = document,
                propertyId = propertyId,
            )
            if (!result.changed) return
            recordEditorUndo(result.applied)
            queueGranularDocumentUpdate(
                previous = document,
                fallback = result.document,
                recordUndo = false,
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
                title = "",
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

    private fun PageBlockDocument.findBlock(blockId: String): PageBlock? {
        fun walk(blocks: List<PageBlock>): PageBlock? {
            blocks.forEach { block ->
                if (block.id == blockId) return block
                walk(block.children)?.let { return it }
                if (block.type == PageBlockType.DatabaseTable) {
                    block.table.rows.forEach { row ->
                        walk(row.blocks)?.let { return it }
                    }
                }
            }
            return null
        }
        return walk(blocks)
    }

    private fun PageBlockDocument.findTableBlock(blockId: String): PageBlock? {
        return findBlock(blockId)?.takeIf { block -> block.type == PageBlockType.DatabaseTable }
    }

    private fun PageBlockDocument.noOpRowBlockCommand(blockId: String): EditorCommand {
        val block = findBlock(blockId)
        return EditorCommand.UpdateBlockText(
            blockId = blockId,
            text = block?.text.orEmpty(),
            richTextSpans = block?.richTextSpans.orEmpty(),
        )
    }

    private fun PageBlockDocument.normalizedForEditor(): PageBlockDocument {
        return copy(
            blocks = blocks.ifEmpty { listOf(PageBlockCodec.newBlock(PageBlockType.Text)) },
        )
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

private fun PageBlock.withAppendedPageMention(page: Page): PageBlock {
    val label = "@${page.title.ifBlank { "Untitled page" }}"
    val prefix = if (text.isBlank() || text.endsWith(" ")) "" else " "
    val start = text.length + prefix.length
    val nextText = "$text$prefix$label "
    return copy(
        text = nextText,
        richTextSpans = richTextSpans + PageTextSpan(
            start = start,
            end = start + label.length,
            mentionPageId = page.id,
            mentionLabel = label,
        ),
    )
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
