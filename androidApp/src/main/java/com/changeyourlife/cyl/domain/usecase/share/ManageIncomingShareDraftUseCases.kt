package com.changeyourlife.cyl.domain.usecase.share

import com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus
import com.changeyourlife.cyl.domain.model.IncomingShareErrorCode
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import com.changeyourlife.cyl.domain.model.IncomingShareLimits
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import com.changeyourlife.cyl.domain.repository.IncomingShareDraftRepository
import com.changeyourlife.cyl.domain.usecase.asset.StageIncomingShareItemUseCase
import javax.inject.Inject

class RemoveIncomingShareItemUseCase @Inject constructor(
    private val repository: IncomingShareDraftRepository,
    private val localStore: ContentAssetLocalStore,
) {
    suspend operator fun invoke(draftId: String, itemId: String): Boolean {
        val draft = repository.get(draftId) ?: return false
        if (draft.status !in EditableDraftStatuses) return false
        val item = draft.items.firstOrNull { candidate -> candidate.id == itemId } ?: return false
        val path = item.stagedPath
        val deleted = path == null || localStore.delete(path)
        repository.updateItem(
            item.copy(
                stagedPath = path.takeUnless { deleted },
                sizeBytes = 0L,
                sha256 = "",
                status = IncomingShareItemStatus.REMOVED,
                errorCode = null,
            ),
        )
        return true
    }
}

class RetryIncomingShareItemUseCase @Inject constructor(
    private val repository: IncomingShareDraftRepository,
    private val stageItem: StageIncomingShareItemUseCase,
    private val localStore: ContentAssetLocalStore,
) {
    suspend operator fun invoke(draftId: String, itemId: String): Boolean {
        val draft = repository.get(draftId) ?: return false
        if (draft.status !in EditableDraftStatuses) return false
        val item = draft.items.firstOrNull { candidate -> candidate.id == itemId } ?: return false
        if (item.status != IncomingShareItemStatus.FAILED) return false
        item.stagedPath?.let { path -> localStore.delete(path) }
        val usedBytes = draft.items
            .filter { candidate -> candidate.id != itemId && candidate.status == IncomingShareItemStatus.STAGED }
            .sumOf { candidate -> candidate.sizeBytes }
        val staging = item.copy(
            stagedPath = null,
            sizeBytes = 0L,
            sha256 = "",
            status = IncomingShareItemStatus.STAGING,
            errorCode = null,
        )
        repository.updateItem(staging)
        val staged = stageItem(
            item = staging,
            remainingTotalBytes = (IncomingShareLimits.MAX_TOTAL_BYTES - usedBytes).coerceAtLeast(0L),
        )
        repository.updateItem(staged)
        repository.transitionDraft(
            draftId = draft.id,
            expectedStatuses = EditableDraftStatuses,
            nextStatus = IncomingShareDraftStatus.STAGED,
            errorCode = if (staged.status == IncomingShareItemStatus.FAILED) {
                IncomingShareErrorCode.PARTIAL_FAILURE.wireValue
            } else {
                null
            },
            updatedAt = System.currentTimeMillis(),
        )
        return staged.status == IncomingShareItemStatus.STAGED
    }
}

class CancelIncomingShareDraftUseCase @Inject constructor(
    private val repository: IncomingShareDraftRepository,
    private val localStore: ContentAssetLocalStore,
) {
    suspend operator fun invoke(draftId: String): Boolean {
        val draft = repository.get(draftId) ?: return false
        if (draft.status == IncomingShareDraftStatus.CANCELLED) return true
        if (draft.status in NonCancellableDraftStatuses) return false
        val retainedPaths = draft.items.mapNotNull { item -> item.stagedPath }.associateWith { path ->
            !localStore.delete(path)
        }
        draft.items.forEach { item ->
            if (item.status != IncomingShareItemStatus.REMOVED) {
                repository.updateItem(
                    item.copy(
                        stagedPath = item.stagedPath?.takeIf { path -> retainedPaths[path] == true },
                        sizeBytes = 0L,
                        sha256 = "",
                        status = IncomingShareItemStatus.REMOVED,
                        errorCode = null,
                    ),
                )
            }
        }
        return repository.transitionDraft(
            draftId = draft.id,
            expectedStatuses = EditableDraftStatuses,
            nextStatus = IncomingShareDraftStatus.CANCELLED,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

private val EditableDraftStatuses = setOf(
    IncomingShareDraftStatus.RECEIVED,
    IncomingShareDraftStatus.VALIDATING,
    IncomingShareDraftStatus.STAGED,
    IncomingShareDraftStatus.FAILED,
)

private val NonCancellableDraftStatuses = setOf(
    IncomingShareDraftStatus.IMPORTING,
    IncomingShareDraftStatus.UPLOAD_QUEUED,
    IncomingShareDraftStatus.COMPLETED,
)
