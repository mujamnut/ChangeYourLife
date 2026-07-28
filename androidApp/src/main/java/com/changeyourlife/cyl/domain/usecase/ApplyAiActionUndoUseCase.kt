package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.AiActionUndoState
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.repository.AiActionLogRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ApplyAiActionUndoUseCase(
    private val aiActionLogRepository: AiActionLogRepository,
    private val pageRepository: PageRepository,
    private val applyEditorCommandUseCase: ApplyEditorCommandUseCase,
    private val reconcileTableDateRemindersUseCase: ReconcileTableDateRemindersUseCase? = null,
) {
    private val applyAiUndoCommandsUseCase = ApplyAiUndoCommandsUseCase(
        pageRepository = pageRepository,
        applyEditorCommandUseCase = applyEditorCommandUseCase,
        reconcileTableDateRemindersUseCase = reconcileTableDateRemindersUseCase,
    )
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend operator fun invoke(
        auditId: String,
        pageId: String,
    ): AiActionUndoResult {
        if (auditId.isBlank()) return AiActionUndoResult("Missing AI action id.")

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

        val result = applyAiUndoCommandsUseCase(
            undoCommands = undoCommands,
            fallbackPageId = pageId,
        )

        if (!result.succeeded) {
            return AiActionUndoResult(
                message = result.failures.joinToString(
                    separator = " ",
                    prefix = "Undo could not be completed: ",
                ) { failure -> failure.message },
            )
        }
        if (result.changedCount == 0) {
            return AiActionUndoResult("Nothing changed. The page may already be back to the previous state.")
        }

        val now = System.currentTimeMillis()
        aiActionLogRepository.upsert(
            log.copy(
                undoState = AiActionUndoState.Applied,
                updatedAt = now,
            ),
        )

        return AiActionUndoResult(
            message = "Undone ${result.changedCount} AI change${if (result.changedCount == 1) "" else "s"}.",
            changed = true,
        )
    }
}

data class AiActionUndoResult(
    val message: String,
    val changed: Boolean = false,
)
