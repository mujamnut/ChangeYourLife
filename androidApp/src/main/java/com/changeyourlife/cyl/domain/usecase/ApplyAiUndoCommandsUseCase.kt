package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.AiUndoCommandType
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.toEditorCommand
import com.changeyourlife.cyl.domain.model.toPage
import com.changeyourlife.cyl.domain.repository.PageRepository

class ApplyAiUndoCommandsUseCase(
    private val pageRepository: PageRepository,
    private val applyEditorCommandUseCase: ApplyEditorCommandUseCase,
    private val reconcileTableDateRemindersUseCase: ReconcileTableDateRemindersUseCase? = null,
) {
    suspend operator fun invoke(
        undoCommands: List<AiUndoCommandSummary>,
        fallbackPageId: String = "",
    ): ApplyAiUndoCommandsResult {
        if (undoCommands.isEmpty()) return ApplyAiUndoCommandsResult()

        val pendingDocuments = linkedMapOf<String, PendingAiUndoDocument>()
        val failures = mutableListOf<AiUndoCommandFailure>()
        var changedCount = 0

        suspend fun pendingDocument(
            targetPageId: String,
            command: AiUndoCommandSummary,
        ): PendingAiUndoDocument? {
            pendingDocuments[targetPageId]?.let { return it }
            val page = runCatching { pageRepository.getPage(targetPageId) }
                .getOrElse { error ->
                    failures += command.toFailure(error)
                    return null
                }
            if (page == null) {
                failures += command.toFailure("Could not find page $targetPageId while rolling back.")
                return null
            }
            val originalDocument = runCatching {
                PageContentCodec.decodeDocument(page.content)
            }.getOrElse { error ->
                failures += command.toFailure(error)
                return null
            }
            return PendingAiUndoDocument(
                page = page,
                originalDocument = originalDocument,
            ).also { pendingDocuments[targetPageId] = it }
        }

        suspend fun flushPendingDocuments() {
            val pending = pendingDocuments.values.toList()
            pendingDocuments.clear()
            pending
                .filter(PendingAiUndoDocument::changed)
                .forEach { document ->
                    runCatching {
                        val now = System.currentTimeMillis()
                        val currentPage = pageRepository.getPage(document.page.id) ?: document.page
                        val updatedPage = currentPage.copy(
                            content = PageContentCodec.encodeDocument(document.document),
                            updatedAt = maxOf(now, currentPage.updatedAt.safelyIncrement()),
                        )
                        pageRepository.upsertPage(updatedPage)
                        reconcileTableDateRemindersUseCase?.invoke(
                            previousPage = document.page,
                            currentPage = updatedPage,
                            previousDocument = document.originalDocument,
                            currentDocument = document.document,
                        )
                    }.onFailure { error ->
                        failures += AiUndoCommandFailure(
                            actionIndex = document.lastActionIndex,
                            commandType = "EditorCommandBatch",
                            message = error.message ?: "Could not persist a rolled-back page.",
                        )
                    }
                }
        }

        undoCommands.asReversed().forEach { undoCommand ->
            val editorCommand = undoCommand.toEditorCommand()
            if (editorCommand != null) {
                val targetPageId = undoCommand.pageId.ifBlank { fallbackPageId }
                if (targetPageId.isBlank()) {
                    failures += undoCommand.toFailure("Undo command has no target page.")
                    return@forEach
                }
                val pending = pendingDocument(targetPageId, undoCommand) ?: return@forEach
                runCatching {
                    applyEditorCommandUseCase(pending.document, editorCommand)
                }.onSuccess { applied ->
                    pending.document = applied.document
                    pending.changed = pending.changed || applied.changed
                    pending.lastActionIndex = undoCommand.actionIndex
                    if (applied.changed) changedCount += 1
                }.onFailure { error ->
                    failures += undoCommand.toFailure(error)
                }
                return@forEach
            }

            when (undoCommand.commandType) {
                AiUndoCommandType.RestorePageSnapshots -> {
                    flushPendingDocuments()
                    val snapshots = undoCommand.pageSnapshots.map { snapshot -> snapshot.toPage() }
                    if (snapshots.isEmpty()) {
                        failures += undoCommand.toFailure("Page snapshot is empty.")
                        return@forEach
                    }
                    runCatching {
                        check(pageRepository.restorePageSnapshots(snapshots)) {
                            "Page snapshots could not be restored."
                        }
                        snapshots.forEach { restoredPage ->
                            val restoredDocument = PageContentCodec.decodeDocument(restoredPage.content)
                            if (restoredPage.deletedAt == null) {
                                reconcileTableDateRemindersUseCase?.scheduleAll(
                                    page = restoredPage,
                                    document = restoredDocument,
                                )
                            } else {
                                reconcileTableDateRemindersUseCase?.cancelAll(
                                    page = restoredPage,
                                    document = restoredDocument,
                                )
                            }
                        }
                    }.onSuccess {
                        changedCount += 1
                    }.onFailure { error ->
                        failures += undoCommand.toFailure(error)
                    }
                }

                AiUndoCommandType.DeleteCreatedPage -> {
                    flushPendingDocuments()
                    val targetPageId = undoCommand.pageId
                        .ifBlank { undoCommand.targetId }
                        .ifBlank { fallbackPageId }
                    if (targetPageId.isBlank()) {
                        failures += undoCommand.toFailure("Created page undo has no target page.")
                        return@forEach
                    }
                    runCatching {
                        val pages = pageRepository.getPageTreeSnapshot(targetPageId)
                        if (pages.isNotEmpty()) {
                            pages.forEach { createdPage ->
                                reconcileTableDateRemindersUseCase?.cancelAll(
                                    page = createdPage,
                                    document = PageContentCodec.decodeDocument(createdPage.content),
                                )
                            }
                            pageRepository.deletePagePermanently(targetPageId)
                            true
                        } else {
                            false
                        }
                    }.onSuccess { changed ->
                        if (changed) changedCount += 1
                    }.onFailure { error ->
                        failures += undoCommand.toFailure(error)
                    }
                }

                else -> {
                    failures += undoCommand.toFailure(
                        "Unsupported undo command type: ${undoCommand.commandType}",
                    )
                }
            }
        }
        flushPendingDocuments()

        return ApplyAiUndoCommandsResult(
            changedCount = changedCount,
            failures = failures,
        )
    }
}

data class ApplyAiUndoCommandsResult(
    val changedCount: Int = 0,
    val failures: List<AiUndoCommandFailure> = emptyList(),
) {
    val succeeded: Boolean
        get() = failures.isEmpty()
}

data class AiUndoCommandFailure(
    val actionIndex: Int,
    val commandType: String,
    val message: String,
)

private data class PendingAiUndoDocument(
    val page: Page,
    val originalDocument: PageBlockDocument,
    var document: PageBlockDocument = originalDocument,
    var changed: Boolean = false,
    var lastActionIndex: Int = -1,
)

private fun AiUndoCommandSummary.toFailure(error: Throwable): AiUndoCommandFailure {
    return toFailure(error.message ?: "Undo command failed.")
}

private fun AiUndoCommandSummary.toFailure(message: String): AiUndoCommandFailure {
    return AiUndoCommandFailure(
        actionIndex = actionIndex,
        commandType = commandType,
        message = message,
    )
}

private fun Long.safelyIncrement(): Long {
    return if (this == Long.MAX_VALUE) this else this + 1L
}
