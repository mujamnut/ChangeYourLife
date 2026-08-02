package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.backend.domain.VoiceAssetMetadata
import com.changeyourlife.cyl.backend.domain.VoiceAssetReadHandle
import com.changeyourlife.cyl.backend.domain.VoiceAssetStorage
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadIntent
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadRequest
import com.changeyourlife.cyl.backend.domain.VoiceAttachmentResult
import java.util.concurrent.ConcurrentHashMap

class InMemoryVoiceAssetStorage : VoiceAssetStorage {
    private val expectedUploads = ConcurrentHashMap<String, VoiceAssetMetadata>()
    private val uploadedAssets = ConcurrentHashMap<String, VoiceAssetMetadata>()

    override suspend fun createUploadIntent(
        request: VoiceAssetUploadRequest,
    ): VoiceAttachmentResult<VoiceAssetUploadIntent> {
        request.validationFailure()?.let { failure -> return failure }

        expectedUploads[request.storageKey] = request.toMetadata()
        return VoiceAttachmentResult.Success(
            VoiceAssetUploadIntent(
                attachmentId = request.attachmentId,
                uploadUrl = "memory://upload/${request.storageKey}",
                requiredHeaders = mapOf(
                    "Content-Type" to request.mimeType,
                    "x-amz-meta-sha256" to request.sha256,
                ),
                expiresAtEpochMillis = request.expiresAtEpochMillis,
            ),
        )
    }

    override suspend fun head(storageKey: String): VoiceAttachmentResult<VoiceAssetMetadata> =
        uploadedAssets[storageKey]
            ?.let { metadata -> VoiceAttachmentResult.Success(metadata) }
            ?: missingAsset(storageKey)

    override suspend fun createReadHandle(
        storageKey: String,
        expiresAtEpochMillis: Long,
    ): VoiceAttachmentResult<VoiceAssetReadHandle> {
        if (!uploadedAssets.containsKey(storageKey)) return missingAsset(storageKey)
        return VoiceAttachmentResult.Success(
            VoiceAssetReadHandle(
                readUrl = "memory://read/$storageKey",
                expiresAtEpochMillis = expiresAtEpochMillis,
            ),
        )
    }

    override suspend fun delete(storageKey: String): VoiceAttachmentResult<Unit> {
        expectedUploads.remove(storageKey)
        uploadedAssets.remove(storageKey)
        return VoiceAttachmentResult.Success(Unit)
    }

    fun markUploaded(storageKey: String): VoiceAttachmentResult<VoiceAssetMetadata> {
        val metadata = expectedUploads[storageKey] ?: return missingAsset(storageKey)
        uploadedAssets[storageKey] = metadata
        return VoiceAttachmentResult.Success(metadata)
    }

    private fun VoiceAssetUploadRequest.validationFailure(): VoiceAttachmentResult.Failure? {
        val problem = when {
            attachmentId.isBlank() -> "attachmentId is required."
            storageKey.isBlank() -> "storageKey is required."
            !mimeType.startsWith("audio/") -> "Voice asset mimeType must be audio."
            sizeBytes <= 0L -> "Voice asset sizeBytes must be positive."
            durationMs <= 0L -> "Voice asset durationMs must be positive."
            sha256.isBlank() -> "Voice asset sha256 is required."
            expiresAtEpochMillis <= 0L -> "Upload intent expiry must be positive."
            else -> return null
        }
        return VoiceAttachmentResult.Failure(
            code = ChatAttachmentErrorCode.UploadValidationFailed,
            developerMessage = problem,
        )
    }

    private fun VoiceAssetUploadRequest.toMetadata(): VoiceAssetMetadata =
        VoiceAssetMetadata(
            storageKey = storageKey,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            sha256 = sha256,
        )

    private fun missingAsset(storageKey: String): VoiceAttachmentResult.Failure =
        VoiceAttachmentResult.Failure(
            code = ChatAttachmentErrorCode.AttachmentNotFound,
            developerMessage = "Voice asset does not exist: $storageKey",
        )
}
