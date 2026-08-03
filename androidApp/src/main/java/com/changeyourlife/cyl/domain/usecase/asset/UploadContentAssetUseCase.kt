package com.changeyourlife.cyl.domain.usecase.asset

import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import com.changeyourlife.cyl.domain.repository.ContentAssetRemoteUploadResult
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import com.changeyourlife.cyl.domain.repository.ContentAssetTransferError
import com.changeyourlife.cyl.domain.repository.ContentAssetTransferGateway
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class UploadContentAssetUseCase @Inject constructor(
    private val repository: ContentAssetRepository,
    private val gateway: ContentAssetTransferGateway,
) {
    suspend operator fun invoke(assetId: String): ContentAssetUploadWorkResult {
        val initial = repository.getById(assetId) ?: return ContentAssetUploadWorkResult.NoWork
        when (initial.status) {
            ContentAssetStatus.REMOTE_READY,
            ContentAssetStatus.DOWNLOAD_REQUIRED -> return ContentAssetUploadWorkResult.Completed
            ContentAssetStatus.DELETED -> return ContentAssetUploadWorkResult.NoWork
            ContentAssetStatus.PERMANENT_FAILURE -> return ContentAssetUploadWorkResult.PermanentFailure(
                initial.errorCode.orEmpty().ifBlank { ContentAssetTransferError.UNKNOWN.wireValue },
            )
            ContentAssetStatus.LOCAL_READY -> return ContentAssetUploadWorkResult.NoWork
            else -> Unit
        }
        if (initial.localPath.isNullOrBlank()) {
            return persistFailure(
                asset = initial,
                error = ContentAssetTransferError.NOT_FOUND,
                retryable = false,
            )
        }

        val entered = repository.transitionStatus(
            assetId = initial.id,
            expectedStatuses = UploadStartStatuses,
            nextStatus = ContentAssetStatus.UPLOADING,
            progressPercent = initial.progressPercent.coerceIn(0, 99),
            errorCode = null,
            remoteAssetId = null,
            updatedAt = System.currentTimeMillis(),
        )
        if (!entered) return resultForCurrent(initial.id)
        val prepared = repository.getById(initial.id) ?: return ContentAssetUploadWorkResult.NoWork
        return uploadAndPersist(prepared)
    }

    private suspend fun uploadAndPersist(asset: ContentAsset): ContentAssetUploadWorkResult = coroutineScope {
        val signals = Channel<ContentAssetUploadSignal>(Channel.UNLIMITED)
        val stateJob = launch {
            var latestProgress = asset.progressPercent.coerceIn(0, 99)
            for (signal in signals) {
                when (signal) {
                    is ContentAssetUploadSignal.RemoteAccepted -> persistWorkingState(
                        assetId = asset.id,
                        progressPercent = latestProgress,
                        remoteAssetId = signal.remoteAssetId,
                    )
                    is ContentAssetUploadSignal.Progress -> {
                        val next = signal.percent.coerceIn(0, 99)
                        if (next > latestProgress) {
                            latestProgress = next
                            persistWorkingState(asset.id, latestProgress)
                        }
                    }
                }
            }
        }
        val remoteResult = try {
            try {
                gateway.upload(
                    asset = asset,
                    onRemoteAccepted = { remoteId ->
                        signals.trySend(ContentAssetUploadSignal.RemoteAccepted(remoteId))
                    },
                    onProgress = { progress ->
                        signals.trySend(ContentAssetUploadSignal.Progress(progress))
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                ContentAssetRemoteUploadResult.Failure(
                    ContentAssetTransferError.STORAGE_UNAVAILABLE,
                    retryable = true,
                )
            }
        } finally {
            signals.close()
            stateJob.join()
        }

        when (remoteResult) {
            is ContentAssetRemoteUploadResult.Success -> {
                if (remoteResult.status != ContentAssetStatus.REMOTE_READY) {
                    return@coroutineScope persistFailure(
                        asset = repository.getById(asset.id) ?: asset,
                        error = ContentAssetTransferError.INVALID_STATE,
                        retryable = true,
                    )
                }
                repository.transitionStatus(
                    assetId = asset.id,
                    expectedStatuses = UploadWorkingStatuses,
                    nextStatus = ContentAssetStatus.REMOTE_READY,
                    progressPercent = 100,
                    errorCode = null,
                    remoteAssetId = remoteResult.remoteAssetId,
                    updatedAt = System.currentTimeMillis(),
                )
                resultForCurrent(asset.id)
            }
            is ContentAssetRemoteUploadResult.Failure -> persistFailure(
                asset = repository.getById(asset.id) ?: asset,
                error = remoteResult.error,
                retryable = remoteResult.retryable,
            )
        }
    }

    private suspend fun persistWorkingState(
        assetId: String,
        progressPercent: Int,
        remoteAssetId: String? = null,
    ) {
        repository.transitionStatus(
            assetId = assetId,
            expectedStatuses = UploadWorkingStatuses,
            nextStatus = ContentAssetStatus.UPLOADING,
            progressPercent = progressPercent,
            errorCode = null,
            remoteAssetId = remoteAssetId,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun persistFailure(
        asset: ContentAsset,
        error: ContentAssetTransferError,
        retryable: Boolean,
    ): ContentAssetUploadWorkResult {
        val nextStatus = if (retryable) {
            ContentAssetStatus.RETRYABLE_FAILURE
        } else {
            ContentAssetStatus.PERMANENT_FAILURE
        }
        repository.transitionStatus(
            assetId = asset.id,
            expectedStatuses = UploadFailureSourceStatuses,
            nextStatus = nextStatus,
            progressPercent = asset.progressPercent.coerceIn(0, 99),
            errorCode = error.wireValue,
            remoteAssetId = asset.remoteAssetId,
            updatedAt = System.currentTimeMillis(),
        )
        return resultForCurrent(asset.id)
    }

    private suspend fun resultForCurrent(assetId: String): ContentAssetUploadWorkResult {
        val current = repository.getById(assetId) ?: return ContentAssetUploadWorkResult.NoWork
        return when (current.status) {
            ContentAssetStatus.REMOTE_READY,
            ContentAssetStatus.DOWNLOAD_REQUIRED -> ContentAssetUploadWorkResult.Completed
            ContentAssetStatus.DELETED,
            ContentAssetStatus.LOCAL_READY -> ContentAssetUploadWorkResult.NoWork
            ContentAssetStatus.PERMANENT_FAILURE -> ContentAssetUploadWorkResult.PermanentFailure(
                current.errorCode.orEmpty().ifBlank { ContentAssetTransferError.UNKNOWN.wireValue },
            )
            else -> ContentAssetUploadWorkResult.Retry(
                current.errorCode.orEmpty().ifBlank { ContentAssetTransferError.INVALID_STATE.wireValue },
            )
        }
    }
}

sealed interface ContentAssetUploadWorkResult {
    data object Completed : ContentAssetUploadWorkResult
    data object NoWork : ContentAssetUploadWorkResult
    data class Retry(val errorCode: String) : ContentAssetUploadWorkResult
    data class PermanentFailure(val errorCode: String) : ContentAssetUploadWorkResult
}

private sealed interface ContentAssetUploadSignal {
    data class RemoteAccepted(val remoteAssetId: String) : ContentAssetUploadSignal
    data class Progress(val percent: Int) : ContentAssetUploadSignal
}

private val UploadStartStatuses = setOf(
    ContentAssetStatus.UPLOAD_QUEUED,
    ContentAssetStatus.UPLOADING,
    ContentAssetStatus.RETRYABLE_FAILURE,
)
private val UploadWorkingStatuses = setOf(
    ContentAssetStatus.UPLOAD_QUEUED,
    ContentAssetStatus.UPLOADING,
    ContentAssetStatus.RETRYABLE_FAILURE,
)
private val UploadFailureSourceStatuses = UploadStartStatuses + UploadWorkingStatuses
