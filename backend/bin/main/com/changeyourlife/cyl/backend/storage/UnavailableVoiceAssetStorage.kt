package com.changeyourlife.cyl.backend.storage

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.backend.domain.VoiceAssetMetadata
import com.changeyourlife.cyl.backend.domain.VoiceAssetReadHandle
import com.changeyourlife.cyl.backend.domain.VoiceAssetStorage
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadIntent
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadRequest
import com.changeyourlife.cyl.backend.domain.VoiceAttachmentResult

class UnavailableVoiceAssetStorage(
    private val featureEnabled: Boolean,
) : VoiceAssetStorage {
    override suspend fun createUploadIntent(
        request: VoiceAssetUploadRequest,
    ): VoiceAttachmentResult<VoiceAssetUploadIntent> = unavailable()

    override suspend fun head(storageKey: String): VoiceAttachmentResult<VoiceAssetMetadata> = unavailable()

    override suspend fun createReadHandle(
        storageKey: String,
        expiresAtEpochMillis: Long,
    ): VoiceAttachmentResult<VoiceAssetReadHandle> = unavailable()

    override suspend fun delete(storageKey: String): VoiceAttachmentResult<Unit> = unavailable()

    private fun unavailable(): VoiceAttachmentResult.Failure = VoiceAttachmentResult.Failure(
        code = if (featureEnabled) {
            ChatAttachmentErrorCode.StorageUnavailable
        } else {
            ChatAttachmentErrorCode.FeatureDisabled
        },
        developerMessage = if (featureEnabled) {
            "Voice-note storage is not configured."
        } else {
            "Voice notes are disabled."
        },
    )
}
