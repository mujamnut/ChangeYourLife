package com.changeyourlife.cyl.domain.usecase.ai

import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_PDF_BYTES
import com.changeyourlife.cyl.domain.model.AiAttachmentPreparationError
import com.changeyourlife.cyl.domain.model.ContentAssetStageError
import com.changeyourlife.cyl.domain.model.ContentAssetStageResult
import com.changeyourlife.cyl.domain.model.PrepareAiAttachmentRequest
import com.changeyourlife.cyl.domain.model.PrepareAiAttachmentResult
import com.changeyourlife.cyl.domain.model.StageContentAssetRequest
import com.changeyourlife.cyl.domain.repository.AiAttachmentPayloadReadResult
import com.changeyourlife.cyl.domain.repository.AiAttachmentPayloadReader
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import com.changeyourlife.cyl.domain.usecase.asset.StageContentAssetUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class PrepareAiAttachmentUseCase @Inject constructor(
    private val stageContentAsset: StageContentAssetUseCase,
    private val payloadReader: AiAttachmentPayloadReader,
    private val assetRepository: ContentAssetRepository,
    private val localStore: ContentAssetLocalStore,
) {
    suspend operator fun invoke(request: PrepareAiAttachmentRequest): PrepareAiAttachmentResult {
        if (request.workspaceId.isBlank() || request.sourceUri.isBlank() || request.source.isBlank()) {
            return PrepareAiAttachmentResult.Rejected(AiAttachmentPreparationError.INVALID_REQUEST)
        }
        val staged = stageContentAsset(
            StageContentAssetRequest(
                workspaceId = request.workspaceId,
                sourceUri = request.sourceUri,
                suggestedName = request.fallbackName,
                declaredMimeType = request.declaredMimeType,
                maxBytes = MaxAiStagingBytes,
            ),
        )
        if (staged is ContentAssetStageResult.Failure) {
            return PrepareAiAttachmentResult.Rejected(
                error = staged.error.toAiPreparationError(),
                detail = staged.detail,
            )
        }
        staged as ContentAssetStageResult.Success

        return try {
            when (
                val prepared = payloadReader.read(
                    asset = staged.asset,
                    source = request.source,
                    sourceReferenceId = request.sourceReferenceId.ifBlank { staged.asset.id },
                )
            ) {
                is AiAttachmentPayloadReadResult.Success -> {
                    if (request.imageOnly && prepared.attachment.attachmentKind != ChatAttachmentKind.Image) {
                        discard(staged.asset.id, staged.asset.localPath)
                        PrepareAiAttachmentResult.Rejected(AiAttachmentPreparationError.IMAGE_REQUIRED)
                    } else {
                        PrepareAiAttachmentResult.Success(prepared.attachment)
                    }
                }
                is AiAttachmentPayloadReadResult.Rejected -> {
                    discard(staged.asset.id, staged.asset.localPath)
                    PrepareAiAttachmentResult.Rejected(prepared.error, prepared.detail)
                }
            }
        } catch (cancelled: CancellationException) {
            discard(staged.asset.id, staged.asset.localPath)
            throw cancelled
        } catch (_: Throwable) {
            discard(staged.asset.id, staged.asset.localPath)
            PrepareAiAttachmentResult.Rejected(AiAttachmentPreparationError.UNKNOWN)
        }
    }

    private suspend fun discard(assetId: String, localPath: String?) {
        localPath?.let { path -> runCatching { localStore.delete(path) } }
        runCatching { assetRepository.deleteRecord(assetId) }
    }
}

private fun ContentAssetStageError.toAiPreparationError(): AiAttachmentPreparationError = when (this) {
    ContentAssetStageError.INVALID_REQUEST,
    ContentAssetStageError.INVALID_SOURCE -> AiAttachmentPreparationError.INVALID_REQUEST
    ContentAssetStageError.PERMISSION_DENIED -> AiAttachmentPreparationError.PERMISSION_DENIED
    ContentAssetStageError.SOURCE_UNAVAILABLE -> AiAttachmentPreparationError.SOURCE_UNAVAILABLE
    ContentAssetStageError.EMPTY_FILE -> AiAttachmentPreparationError.EMPTY_FILE
    ContentAssetStageError.TOO_LARGE -> AiAttachmentPreparationError.TOO_LARGE
    ContentAssetStageError.STORAGE_UNAVAILABLE -> AiAttachmentPreparationError.STORAGE_UNAVAILABLE
    ContentAssetStageError.PERSISTENCE_FAILED -> AiAttachmentPreparationError.PERSISTENCE_FAILED
    ContentAssetStageError.UNKNOWN -> AiAttachmentPreparationError.UNKNOWN
}

private const val MaxAiStagingBytes = CYL_MAX_AI_PDF_BYTES
