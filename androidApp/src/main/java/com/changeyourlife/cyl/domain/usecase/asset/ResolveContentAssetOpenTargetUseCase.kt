package com.changeyourlife.cyl.domain.usecase.asset

import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import com.changeyourlife.cyl.domain.repository.ContentAssetRemoteReadResult
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import com.changeyourlife.cyl.domain.repository.ContentAssetTransferError
import com.changeyourlife.cyl.domain.repository.ContentAssetTransferGateway
import javax.inject.Inject

class ResolveContentAssetOpenTargetUseCase @Inject constructor(
    private val repository: ContentAssetRepository,
    private val localStore: ContentAssetLocalStore,
    private val gateway: ContentAssetTransferGateway,
) {
    suspend operator fun invoke(assetId: String): ContentAssetOpenResult {
        val asset = repository.getById(assetId)
            ?: return resolveRemoteOnly(assetId)
        if (asset.status == ContentAssetStatus.DELETED) {
            return ContentAssetOpenResult.Failure(ContentAssetTransferError.NOT_FOUND, false)
        }
        val localPath = asset.localPath?.takeIf(String::isNotBlank)
        if (localPath != null && localStore.isAvailable(localPath)) {
            return ContentAssetOpenResult.Local(
                path = localPath,
                mimeType = asset.mimeType,
                displayName = asset.displayName,
            )
        }
        val remoteId = asset.remoteAssetId?.takeIf(String::isNotBlank)
            ?: asset.id.takeIf { asset.status in RemotelyResolvableStatuses }
            ?: return ContentAssetOpenResult.Failure(
                ContentAssetTransferError.INVALID_STATE,
                asset.status in RetryableOpenStatuses,
            )
        return when (val result = gateway.getDownloadHandle(remoteId)) {
            is ContentAssetRemoteReadResult.Success -> ContentAssetOpenResult.Remote(
                url = result.downloadUrl,
                expiresAtEpochMillis = result.expiresAtEpochMillis,
                mimeType = asset.mimeType,
                displayName = asset.displayName,
            )
            is ContentAssetRemoteReadResult.Failure -> ContentAssetOpenResult.Failure(
                result.error,
                result.retryable,
            )
        }
    }

    private suspend fun resolveRemoteOnly(assetId: String): ContentAssetOpenResult {
        return when (val result = gateway.getDownloadHandle(assetId)) {
            is ContentAssetRemoteReadResult.Success -> {
                val descriptor = result.asset
                repository.upsert(
                    ContentAsset(
                        id = descriptor.remoteAssetId,
                        workspaceId = descriptor.workspaceId,
                        ownerPageId = descriptor.ownerPageId,
                        kind = descriptor.kind,
                        displayName = descriptor.displayName,
                        mimeType = descriptor.mimeType,
                        sizeBytes = descriptor.sizeBytes,
                        sha256 = descriptor.sha256,
                        localPath = null,
                        remoteAssetId = descriptor.remoteAssetId,
                        status = ContentAssetStatus.DOWNLOAD_REQUIRED,
                        progressPercent = 100,
                        createdAt = descriptor.createdAtEpochMillis,
                        updatedAt = descriptor.updatedAtEpochMillis,
                    ),
                )
                ContentAssetOpenResult.Remote(
                    url = result.downloadUrl,
                    expiresAtEpochMillis = result.expiresAtEpochMillis,
                    mimeType = descriptor.mimeType,
                    displayName = descriptor.displayName,
                )
            }
            is ContentAssetRemoteReadResult.Failure -> ContentAssetOpenResult.Failure(
                result.error,
                result.retryable,
            )
        }
    }
}

sealed interface ContentAssetOpenResult {
    data class Local(
        val path: String,
        val mimeType: String,
        val displayName: String,
    ) : ContentAssetOpenResult

    data class Remote(
        val url: String,
        val expiresAtEpochMillis: Long,
        val mimeType: String,
        val displayName: String,
    ) : ContentAssetOpenResult

    data class Failure(
        val error: ContentAssetTransferError,
        val retryable: Boolean,
    ) : ContentAssetOpenResult
}

private val RetryableOpenStatuses = setOf(
    ContentAssetStatus.UPLOAD_QUEUED,
    ContentAssetStatus.UPLOADING,
    ContentAssetStatus.RETRYABLE_FAILURE,
)

private val RemotelyResolvableStatuses = setOf(
    ContentAssetStatus.REMOTE_READY,
    ContentAssetStatus.DOWNLOAD_REQUIRED,
)
