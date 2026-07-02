package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.AiActionUndoState
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.toEditorCommand
import com.changeyourlife.cyl.domain.repository.AiActionLogRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ApplyAiActionUndoUseCase(
    private val aiActionLogRepository: AiActionLogRepository,
    private val pageRepository: PageRepository,
    private val applyEditorCommandUseCase: ApplyEditorCommandUseCase,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend operator fun invoke(
        auditId: String,
        pageId: String,
    ): AiActionUndoResult {
        if (auditId.isBlank()) return AiActionUndoResult("Missing AI action id.")
        if (pageId.isBlank()) return AiActionUndoResult("Missing target page.")

        val log = aiActionLogRepository.getByAuditId(auditId)
            ?: return AiActionUndoResult("Could not find that AI action.")
        if (log.undoState == AiActionUndoState.Applied) {
            return AiActionUndoResult("That AI action has already been undone.")
        }
        if (log.undoState != AiActionUndoState.Available) {
            return AiActionUndoResult("That AI action cannot be undone yet.")
        }

        val undoCommands = runCatching {
            json.decodeFromString<List<AiUndoCommandSummary>>(log.undoCommandsJson)
        }.getOrDefault(emptyList())
        if (undoCommands.isEmpty()) {
            return AiActionUndoResult("No undo payload was saved for that AI action.")
        }

        val page = pageRepository.getPage(pageId)
            ?: return AiActionUndoResult("Could not find the target page.")
        var document = PageContentCodec.decodeDocument(page.content)
        var changedCount = 0

        undoCommands.asReversed().forEach { undoCommand ->
            val command = undoCommand.toEditorCommand() ?: return@forEach
            val applied = applyEditorCommandUseCase(document, command)
            document = applied.document
            if (applied.changed) changedCount += 1
        }

        if (changedCount == 0) {
            return AiActionUndoResult("Nothing changed. The page may already be back to the previous state.")
        }

        val now = System.currentTimeMillis()
        pageRepository.upsertPage(
            page.copy(
                content = PageContentCodec.encodeDocument(document),
                updatedAt = now,
            ),
        )
        aiActionLogRepository.upsert(
            log.copy(
                undoState = AiActionUndoState.Applied,
                updatedAt = now,
            ),
        )

        return AiActionUndoResult(
            message = "Undone $changedCount AI change${if (changedCount == 1) "" else "s"}.",
            changed = true,
        )
    }
}

data class AiActionUndoResult(
    val message: String,
    val changed: Boolean = false,
)
