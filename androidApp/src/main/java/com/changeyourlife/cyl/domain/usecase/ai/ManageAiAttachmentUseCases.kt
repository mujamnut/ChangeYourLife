package com.changeyourlife.cyl.domain.usecase.ai

import com.changeyourlife.cyl.domain.model.AiAttachment
import com.changeyourlife.cyl.domain.model.AiAttachmentSources
import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus
import com.changeyourlife.cyl.domain.model.IncomingShareItemKind
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import com.changeyourlife.cyl.domain.repository.IncomingShareDraftRepository
import javax.inject.Inject

class DiscardAiAttachmentUseCase @Inject constructor(
    private val assetRepository: ContentAssetRepository,
    private val localStore: ContentAssetLocalStore,
) {
    suspend operator fun invoke(attachment: AiAttachment) {
        if (attachment.source == AiAttachmentSources.IncomingShare) return
        val assetId = attachment.assetId.takeIf(String::isNotBlank) ?: return
        val asset = assetRepository.getById(assetId) ?: return
        if (asset.status != ContentAssetStatus.LOCAL_READY) return
        asset.localPath?.let { path -> localStore.delete(path) }
        assetRepository.deleteRecord(asset.id)
    }
}

class CompleteIncomingShareAiHandoffUseCase @Inject constructor(
    private val draftRepository: IncomingShareDraftRepository,
) {
    suspend operator fun invoke(draftId: String): Boolean {
        val draft = draftRepository.get(draftId) ?: return false
        if (draft.status in FinishedAiHandoffStatuses) return true
        if (draft.status != IncomingShareDraftStatus.STAGED) return false
        val hasAssets = draft.items.any { item ->
            item.status == IncomingShareItemStatus.STAGED &&
                item.kind == IncomingShareItemKind.STREAM
        }
        return draftRepository.transitionDraft(
            draftId = draft.id,
            expectedStatuses = setOf(IncomingShareDraftStatus.STAGED),
            nextStatus = if (hasAssets) {
                IncomingShareDraftStatus.UPLOAD_QUEUED
            } else {
                IncomingShareDraftStatus.COMPLETED
            },
            updatedAt = System.currentTimeMillis(),
        )
    }
}

private val FinishedAiHandoffStatuses = setOf(
    IncomingShareDraftStatus.UPLOAD_QUEUED,
    IncomingShareDraftStatus.COMPLETED,
)
