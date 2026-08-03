package com.changeyourlife.cyl.domain.usecase.share

import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus
import com.changeyourlife.cyl.domain.model.IncomingShareItemKind
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import com.changeyourlife.cyl.domain.repository.IncomingShareContentMapper
import com.changeyourlife.cyl.domain.repository.IncomingShareDraftRepository
import com.changeyourlife.cyl.domain.repository.PageImportCommitResult
import com.changeyourlife.cyl.domain.repository.PageImportDestination
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.usecase.asset.QueueContentAssetUploadUseCase
import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException

class ImportSharedContentUseCase @Inject constructor(
    private val draftRepository: IncomingShareDraftRepository,
    private val contentMapper: IncomingShareContentMapper,
    private val pageRepository: PageRepository,
    private val queueAssetUpload: QueueContentAssetUploadUseCase,
) {
    suspend operator fun invoke(request: ImportSharedContentRequest): ImportSharedContentResult {
        val draft = draftRepository.get(request.draftId)
            ?: return ImportSharedContentResult.Rejected(ImportSharedContentError.DRAFT_NOT_FOUND)
        val stagedItems = draft.items.filter { item -> item.status == IncomingShareItemStatus.STAGED }
        if (draft.status != IncomingShareDraftStatus.STAGED || stagedItems.isEmpty()) {
            return ImportSharedContentResult.Rejected(ImportSharedContentError.DRAFT_NOT_READY)
        }
        val target = when (val destination = request.destination) {
            is SharedImportDestination.NewPage -> {
                if (destination.workspaceId.isBlank()) {
                    return ImportSharedContentResult.Rejected(ImportSharedContentError.DESTINATION_NOT_FOUND)
                }
                ResolvedImportTarget(
                    destination = PageImportDestination.NewPage(
                        pageId = draft.deterministicPageId(),
                        workspaceId = destination.workspaceId,
                        title = destination.title.trim().ifBlank { "Untitled page" },
                    ),
                    workspaceId = destination.workspaceId,
                )
            }
            is SharedImportDestination.ExistingPage -> {
                val page = pageRepository.getPage(destination.pageId)
                    ?.takeIf { page -> page.deletedAt == null }
                    ?: return ImportSharedContentResult.Rejected(ImportSharedContentError.DESTINATION_NOT_FOUND)
                ResolvedImportTarget(
                    destination = PageImportDestination.ExistingPage(
                        pageId = page.id,
                        expectedRevision = page.revision,
                        expectedUpdatedAt = page.updatedAt,
                    ),
                    workspaceId = page.workspaceId,
                )
            }
        }

        val transitioned = draftRepository.transitionDraft(
            draftId = draft.id,
            expectedStatuses = setOf(IncomingShareDraftStatus.STAGED),
            nextStatus = IncomingShareDraftStatus.IMPORTING,
            updatedAt = System.currentTimeMillis(),
        )
        if (!transitioned) {
            return ImportSharedContentResult.Rejected(ImportSharedContentError.DRAFT_NOT_READY)
        }

        try {
            val blocks = contentMapper.map(stagedItems).mapIndexed { index, block ->
                block.copy(id = draft.deterministicBlockId(index))
            }
            if (blocks.isEmpty()) {
                restoreDraft(draft.id, ImportSharedContentError.EMPTY_CONTENT)
                return ImportSharedContentResult.Rejected(ImportSharedContentError.EMPTY_CONTENT)
            }
            val now = System.currentTimeMillis()
            val assets = stagedItems
                .filter { item -> item.kind == IncomingShareItemKind.STREAM }
                .mapNotNull { item ->
                    val path = item.stagedPath?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    ContentAsset(
                        id = item.id,
                        workspaceId = target.workspaceId,
                        kind = item.assetKind ?: ContentAssetKind.FILE,
                        displayName = item.displayName.ifBlank { "Untitled file" },
                        mimeType = item.resolvedMimeType,
                        sizeBytes = item.sizeBytes,
                        sha256 = item.sha256,
                        localPath = path,
                        status = ContentAssetStatus.LOCAL_READY,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            if (assets.size != stagedItems.count { item -> item.kind == IncomingShareItemKind.STREAM }) {
                restoreDraft(draft.id, ImportSharedContentError.INVALID_ASSET)
                return ImportSharedContentResult.Rejected(ImportSharedContentError.INVALID_ASSET)
            }

            return when (
                val committed = pageRepository.commitImportedContent(
                    draftId = draft.id,
                    destination = target.destination,
                    blocks = blocks,
                    assets = assets,
                )
            ) {
                PageImportCommitResult.DestinationNotFound -> rejectAfterRestore(
                    draft.id,
                    ImportSharedContentError.DESTINATION_NOT_FOUND,
                )
                PageImportCommitResult.RevisionConflict -> rejectAfterRestore(
                    draft.id,
                    ImportSharedContentError.REVISION_CONFLICT,
                )
                PageImportCommitResult.InvalidContent -> rejectAfterRestore(
                    draft.id,
                    ImportSharedContentError.INVALID_ASSET,
                )
                is PageImportCommitResult.Success -> {
                    assets.forEach { asset ->
                        try {
                            queueAssetUpload(asset.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // The persisted UPLOAD_QUEUED state is resumed at application startup.
                        }
                    }
                    ImportSharedContentResult.Success(
                        pageId = committed.page.id,
                        queuedAssetCount = assets.size,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            restoreDraft(draft.id, ImportSharedContentError.UNKNOWN)
            throw cancelled
        } catch (_: Throwable) {
            restoreDraft(draft.id, ImportSharedContentError.UNKNOWN)
            return ImportSharedContentResult.Rejected(ImportSharedContentError.UNKNOWN)
        }
    }

    private suspend fun rejectAfterRestore(
        draftId: String,
        error: ImportSharedContentError,
    ): ImportSharedContentResult.Rejected {
        restoreDraft(draftId, error)
        return ImportSharedContentResult.Rejected(error)
    }

    private suspend fun restoreDraft(
        draftId: String,
        error: ImportSharedContentError,
    ) {
        draftRepository.transitionDraft(
            draftId = draftId,
            expectedStatuses = setOf(IncomingShareDraftStatus.IMPORTING),
            nextStatus = IncomingShareDraftStatus.STAGED,
            errorCode = error.wireValue,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

data class ImportSharedContentRequest(
    val draftId: String,
    val destination: SharedImportDestination,
)

sealed interface SharedImportDestination {
    data class NewPage(
        val workspaceId: String,
        val title: String,
    ) : SharedImportDestination

    data class ExistingPage(val pageId: String) : SharedImportDestination
}

sealed interface ImportSharedContentResult {
    data class Success(
        val pageId: String,
        val queuedAssetCount: Int,
    ) : ImportSharedContentResult

    data class Rejected(val error: ImportSharedContentError) : ImportSharedContentResult
}

enum class ImportSharedContentError(val wireValue: String) {
    DRAFT_NOT_FOUND("draft_not_found"),
    DRAFT_NOT_READY("draft_not_ready"),
    DESTINATION_NOT_FOUND("destination_not_found"),
    REVISION_CONFLICT("revision_conflict"),
    EMPTY_CONTENT("empty_content"),
    INVALID_ASSET("invalid_asset"),
    UNKNOWN("unknown"),
}

private data class ResolvedImportTarget(
    val destination: PageImportDestination,
    val workspaceId: String,
)

private fun com.changeyourlife.cyl.domain.model.IncomingShareDraft.deterministicPageId(): String =
    UUID.nameUUIDFromBytes("incoming-share:$id:page".toByteArray(StandardCharsets.UTF_8)).toString()

private fun com.changeyourlife.cyl.domain.model.IncomingShareDraft.deterministicBlockId(index: Int): String =
    UUID.nameUUIDFromBytes("incoming-share:$id:block:$index".toByteArray(StandardCharsets.UTF_8)).toString()
