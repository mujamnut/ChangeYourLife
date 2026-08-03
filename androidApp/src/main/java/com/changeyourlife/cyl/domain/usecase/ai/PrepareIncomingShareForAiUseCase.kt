package com.changeyourlife.cyl.domain.usecase.ai

import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_ATTACHMENTS
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_TEXT_BYTES
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.domain.model.AiAttachment
import com.changeyourlife.cyl.domain.model.AiAttachmentPreparationError
import com.changeyourlife.cyl.domain.model.AiAttachmentSources
import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus
import com.changeyourlife.cyl.domain.model.IncomingShareItem
import com.changeyourlife.cyl.domain.model.IncomingShareItemKind
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import com.changeyourlife.cyl.domain.repository.AiAttachmentPayloadReadResult
import com.changeyourlife.cyl.domain.repository.AiAttachmentPayloadReader
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import com.changeyourlife.cyl.domain.repository.IncomingShareContentMapper
import com.changeyourlife.cyl.domain.repository.IncomingShareDraftRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class PrepareIncomingShareForAiUseCase @Inject constructor(
    private val draftRepository: IncomingShareDraftRepository,
    private val contentMapper: IncomingShareContentMapper,
    private val assetRepository: ContentAssetRepository,
    private val payloadReader: AiAttachmentPayloadReader,
) {
    suspend operator fun invoke(
        workspaceId: String,
        draftId: String,
    ): PrepareIncomingShareForAiResult {
        if (workspaceId.isBlank() || draftId.isBlank()) {
            return rejected(AiAttachmentPreparationError.INVALID_REQUEST)
        }
        val draft = draftRepository.get(draftId)
            ?: return rejected(AiAttachmentPreparationError.DRAFT_NOT_FOUND)
        if (draft.status != IncomingShareDraftStatus.STAGED) {
            return rejected(AiAttachmentPreparationError.DRAFT_NOT_READY)
        }
        val items = draft.items
            .filter { item -> item.status == IncomingShareItemStatus.STAGED }
            .sortedBy(IncomingShareItem::position)
        if (items.isEmpty()) return rejected(AiAttachmentPreparationError.DRAFT_NOT_READY)

        val attachments = mutableListOf<AiAttachment>()
        var skippedCount = 0
        var lastError: AiAttachmentPreparationError? = null
        for (item in items) {
            if (attachments.size >= CYL_MAX_AI_ATTACHMENTS) {
                skippedCount += 1
                lastError = AiAttachmentPreparationError.TOO_MANY_ATTACHMENTS
                continue
            }
            val prepared = try {
                prepareItem(workspaceId = workspaceId, draftId = draft.id, item = item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                AiAttachmentPayloadReadResult.Rejected(AiAttachmentPreparationError.UNKNOWN)
            }
            when (prepared) {
                is AiAttachmentPayloadReadResult.Success -> attachments += prepared.attachment
                is AiAttachmentPayloadReadResult.Rejected -> {
                    skippedCount += 1
                    lastError = prepared.error
                }
            }
        }
        if (attachments.isEmpty()) {
            return rejected(
                error = lastError ?: AiAttachmentPreparationError.UNSUPPORTED_TYPE,
                detail = "None of the shared items can be read by AI.",
            )
        }
        return PrepareIncomingShareForAiResult.Success(
            attachments = attachments,
            skippedItemCount = skippedCount,
        )
    }

    private suspend fun prepareItem(
        workspaceId: String,
        draftId: String,
        item: IncomingShareItem,
    ): AiAttachmentPayloadReadResult {
        val sourceReference = "$draftId/${item.id}"
        if (item.kind != IncomingShareItemKind.STREAM) {
            val text = contentMapper.map(listOf(item))
                .joinToString(separator = "\n") { block -> block.text.trim() }
                .trim()
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty()) {
                return AiAttachmentPayloadReadResult.Rejected(AiAttachmentPreparationError.EMPTY_FILE)
            }
            if (bytes.size.toLong() > CYL_MAX_AI_TEXT_BYTES) {
                return AiAttachmentPayloadReadResult.Rejected(AiAttachmentPreparationError.TOO_LARGE)
            }
            return AiAttachmentPayloadReadResult.Success(
                AiAttachment(
                    id = item.id,
                    textContent = text,
                    mimeType = item.resolvedMimeType.ifBlank { "text/plain" },
                    name = item.displayName.ifBlank { "Shared text" },
                    sizeBytes = bytes.size.toLong(),
                    kind = ChatAttachmentKind.TextFile.wireValue,
                    status = ChatAttachmentStatus.LocalReady.wireValue,
                    source = AiAttachmentSources.IncomingShare,
                    sourceReferenceId = sourceReference,
                ),
            )
        }

        val localPath = item.stagedPath?.takeIf(String::isNotBlank)
            ?: return AiAttachmentPayloadReadResult.Rejected(AiAttachmentPreparationError.SOURCE_UNAVAILABLE)
        val now = System.currentTimeMillis()
        val asset = ContentAsset(
            id = item.id,
            workspaceId = workspaceId,
            kind = item.assetKind ?: ContentAssetKind.FILE,
            displayName = item.displayName.ifBlank { "Shared file" },
            mimeType = item.resolvedMimeType,
            sizeBytes = item.sizeBytes,
            sha256 = item.sha256,
            localPath = localPath,
            status = ContentAssetStatus.LOCAL_READY,
            createdAt = now,
            updatedAt = now,
        )
        assetRepository.upsert(asset)
        return payloadReader.read(
            asset = asset,
            source = AiAttachmentSources.IncomingShare,
            sourceReferenceId = sourceReference,
        )
    }
}

sealed interface PrepareIncomingShareForAiResult {
    data class Success(
        val attachments: List<AiAttachment>,
        val skippedItemCount: Int,
    ) : PrepareIncomingShareForAiResult

    data class Rejected(
        val error: AiAttachmentPreparationError,
        val detail: String = "",
    ) : PrepareIncomingShareForAiResult
}

private fun rejected(
    error: AiAttachmentPreparationError,
    detail: String = "",
) = PrepareIncomingShareForAiResult.Rejected(error = error, detail = detail)
