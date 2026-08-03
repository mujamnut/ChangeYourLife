package com.changeyourlife.cyl.backend.storage

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.backend.domain.PrivateAssetReadRequest
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorage
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorageError
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorageResult
import com.changeyourlife.cyl.backend.domain.PrivateAssetUploadRequest
import com.changeyourlife.cyl.backend.domain.VoiceAssetMetadata
import com.changeyourlife.cyl.backend.domain.VoiceAssetReadHandle
import com.changeyourlife.cyl.backend.domain.VoiceAssetStorage
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadIntent
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadRequest
import com.changeyourlife.cyl.backend.domain.VoiceAttachmentResult

class VoiceAssetStorageAdapter(
    private val delegate: PrivateAssetStorage,
) : VoiceAssetStorage {
    override suspend fun createUploadIntent(
        request: VoiceAssetUploadRequest,
    ): VoiceAttachmentResult<VoiceAssetUploadIntent> = delegate.createUploadIntent(
        PrivateAssetUploadRequest(
            assetId = request.attachmentId,
            storageKey = request.storageKey,
            mimeType = request.mimeType,
            sizeBytes = request.sizeBytes,
            sha256 = request.sha256,
            expiresAtEpochMillis = request.expiresAtEpochMillis,
            metadata = mapOf(
                MetadataDurationMs to request.durationMs.toString(),
                MetadataAttachmentId to request.attachmentId,
            ),
        ),
    ).map { intent ->
        VoiceAssetUploadIntent(
            attachmentId = intent.assetId,
            uploadUrl = intent.uploadUrl,
            requiredHeaders = intent.requiredHeaders,
            expiresAtEpochMillis = intent.expiresAtEpochMillis,
        )
    }

    override suspend fun head(storageKey: String): VoiceAttachmentResult<VoiceAssetMetadata> =
        delegate.head(storageKey).map { metadata ->
            VoiceAssetMetadata(
                storageKey = metadata.storageKey,
                mimeType = metadata.mimeType,
                sizeBytes = metadata.sizeBytes,
                durationMs = metadata.metadata[MetadataDurationMs]?.toLongOrNull() ?: 0L,
                sha256 = metadata.sha256,
            )
        }

    override suspend fun createReadHandle(
        storageKey: String,
        expiresAtEpochMillis: Long,
    ): VoiceAttachmentResult<VoiceAssetReadHandle> = delegate.createReadHandle(
        PrivateAssetReadRequest(
            storageKey = storageKey,
            expiresAtEpochMillis = expiresAtEpochMillis,
        ),
    ).map { handle ->
        VoiceAssetReadHandle(
            readUrl = handle.readUrl,
            expiresAtEpochMillis = handle.expiresAtEpochMillis,
        )
    }

    override suspend fun delete(storageKey: String): VoiceAttachmentResult<Unit> =
        delegate.delete(storageKey).map { Unit }
}

class R2VoiceAssetStorage(
    endpoint: String,
    bucket: String,
    accessKeyId: String,
    secretAccessKey: String,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
) : VoiceAssetStorage, AutoCloseable {
    private val delegate = R2PrivateAssetStorage(
        endpoint = endpoint,
        bucket = bucket,
        accessKeyId = accessKeyId,
        secretAccessKey = secretAccessKey,
        nowEpochMillis = nowEpochMillis,
    )
    private val adapter = VoiceAssetStorageAdapter(delegate)

    override suspend fun createUploadIntent(request: VoiceAssetUploadRequest) =
        adapter.createUploadIntent(request)

    override suspend fun head(storageKey: String) = adapter.head(storageKey)

    override suspend fun createReadHandle(storageKey: String, expiresAtEpochMillis: Long) =
        adapter.createReadHandle(storageKey, expiresAtEpochMillis)

    override suspend fun delete(storageKey: String) = adapter.delete(storageKey)

    override fun close() = delegate.close()
}

private inline fun <T, R> PrivateAssetStorageResult<T>.map(
    transform: (T) -> R,
): VoiceAttachmentResult<R> = when (this) {
    is PrivateAssetStorageResult.Success -> VoiceAttachmentResult.Success(transform(value))
    is PrivateAssetStorageResult.Failure -> VoiceAttachmentResult.Failure(
        code = when (error) {
            PrivateAssetStorageError.NotFound -> ChatAttachmentErrorCode.AttachmentNotFound
            PrivateAssetStorageError.InvalidObject -> ChatAttachmentErrorCode.UploadValidationFailed
            PrivateAssetStorageError.Unavailable -> ChatAttachmentErrorCode.StorageUnavailable
        },
        developerMessage = developerMessage,
    )
}

private const val MetadataDurationMs = "duration-ms"
private const val MetadataAttachmentId = "attachment-id"
