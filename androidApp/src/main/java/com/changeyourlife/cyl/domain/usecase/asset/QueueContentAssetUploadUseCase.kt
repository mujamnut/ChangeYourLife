package com.changeyourlife.cyl.domain.usecase.asset

import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import com.changeyourlife.cyl.domain.repository.ContentAssetUploadScheduler
import javax.inject.Inject

class QueueContentAssetUploadUseCase @Inject constructor(
    private val repository: ContentAssetRepository,
    private val scheduler: ContentAssetUploadScheduler,
) {
    suspend operator fun invoke(assetId: String): ContentAssetQueueResult {
        val asset = repository.getById(assetId)
            ?: return ContentAssetQueueResult.Rejected(ContentAssetQueueError.NOT_FOUND)
        when (asset.status) {
            ContentAssetStatus.REMOTE_READY,
            ContentAssetStatus.DOWNLOAD_REQUIRED -> return ContentAssetQueueResult.AlreadyReady
            ContentAssetStatus.DELETED ->
                return ContentAssetQueueResult.Rejected(ContentAssetQueueError.DELETED)
            ContentAssetStatus.PERMANENT_FAILURE ->
                return ContentAssetQueueResult.Rejected(ContentAssetQueueError.PERMANENT_FAILURE)
            ContentAssetStatus.UPLOAD_QUEUED,
            ContentAssetStatus.UPLOADING -> {
                scheduler.enqueue(asset.id)
                return ContentAssetQueueResult.Queued
            }
            ContentAssetStatus.LOCAL_READY,
            ContentAssetStatus.RETRYABLE_FAILURE -> Unit
        }

        val transitioned = repository.transitionStatus(
            assetId = asset.id,
            expectedStatuses = setOf(
                ContentAssetStatus.LOCAL_READY,
                ContentAssetStatus.RETRYABLE_FAILURE,
            ),
            nextStatus = ContentAssetStatus.UPLOAD_QUEUED,
            progressPercent = 0,
            errorCode = null,
            remoteAssetId = null,
            updatedAt = System.currentTimeMillis(),
        )
        val current = repository.getById(asset.id)
            ?: return ContentAssetQueueResult.Rejected(ContentAssetQueueError.NOT_FOUND)
        if (!transitioned && current.status !in QueueableStatuses) {
            return when (current.status) {
                ContentAssetStatus.REMOTE_READY,
                ContentAssetStatus.DOWNLOAD_REQUIRED -> ContentAssetQueueResult.AlreadyReady
                ContentAssetStatus.DELETED ->
                    ContentAssetQueueResult.Rejected(ContentAssetQueueError.DELETED)
                ContentAssetStatus.PERMANENT_FAILURE ->
                    ContentAssetQueueResult.Rejected(ContentAssetQueueError.PERMANENT_FAILURE)
                else -> ContentAssetQueueResult.Rejected(ContentAssetQueueError.INVALID_STATE)
            }
        }
        scheduler.enqueue(asset.id)
        return ContentAssetQueueResult.Queued
    }
}

sealed interface ContentAssetQueueResult {
    data object Queued : ContentAssetQueueResult
    data object AlreadyReady : ContentAssetQueueResult
    data class Rejected(val error: ContentAssetQueueError) : ContentAssetQueueResult
}

enum class ContentAssetQueueError {
    NOT_FOUND,
    DELETED,
    PERMANENT_FAILURE,
    INVALID_STATE,
}

private val QueueableStatuses = setOf(
    ContentAssetStatus.UPLOAD_QUEUED,
    ContentAssetStatus.UPLOADING,
    ContentAssetStatus.RETRYABLE_FAILURE,
)
