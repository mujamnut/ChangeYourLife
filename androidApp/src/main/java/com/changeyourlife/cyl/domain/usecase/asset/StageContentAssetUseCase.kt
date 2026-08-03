package com.changeyourlife.cyl.domain.usecase.asset

import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetStageError
import com.changeyourlife.cyl.domain.model.ContentAssetStageResult
import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import com.changeyourlife.cyl.domain.model.LocalContentAssetCopyRequest
import com.changeyourlife.cyl.domain.model.LocalContentAssetCopyResult
import com.changeyourlife.cyl.domain.model.StageContentAssetRequest
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class StageContentAssetUseCase @Inject constructor(
    private val localStore: ContentAssetLocalStore,
    private val repository: ContentAssetRepository,
) {
    suspend operator fun invoke(request: StageContentAssetRequest): ContentAssetStageResult {
        if (
            request.workspaceId.isBlank() ||
            request.sourceUri.isBlank() ||
            request.maxBytes <= 0L
        ) {
            return ContentAssetStageResult.Failure(ContentAssetStageError.INVALID_REQUEST)
        }

        val assetId = UUID.randomUUID().toString()
        val copied = localStore.copyIntoAssetStorage(
            LocalContentAssetCopyRequest(
                assetId = assetId,
                sourceUri = request.sourceUri,
                suggestedName = request.suggestedName,
                declaredMimeType = request.declaredMimeType,
                maxBytes = request.maxBytes,
            ),
        )
        if (copied is LocalContentAssetCopyResult.Failure) {
            return ContentAssetStageResult.Failure(copied.error, copied.detail)
        }
        copied as LocalContentAssetCopyResult.Success

        val now = System.currentTimeMillis()
        val asset = ContentAsset(
            id = assetId,
            workspaceId = request.workspaceId,
            ownerPageId = request.ownerPageId?.takeIf(String::isNotBlank),
            kind = copied.kind,
            displayName = copied.displayName,
            mimeType = copied.mimeType,
            sizeBytes = copied.sizeBytes,
            sha256 = copied.sha256,
            localPath = copied.localPath,
            status = ContentAssetStatus.LOCAL_READY,
            createdAt = now,
            updatedAt = now,
        )

        return try {
            repository.upsert(asset)
            ContentAssetStageResult.Success(asset)
        } catch (cancellation: CancellationException) {
            localStore.delete(copied.localPath)
            throw cancellation
        } catch (_: Exception) {
            localStore.delete(copied.localPath)
            ContentAssetStageResult.Failure(ContentAssetStageError.PERSISTENCE_FAILED)
        }
    }
}
