package com.changeyourlife.cyl.backend.domain

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode

sealed interface VoiceAttachmentResult<out T> {
    data class Success<T>(val value: T) : VoiceAttachmentResult<T>

    data class Failure(
        val code: ChatAttachmentErrorCode,
        val developerMessage: String = "",
    ) : VoiceAttachmentResult<Nothing> {
        val retryable: Boolean
            get() = code.retryable
    }
}

data class VoiceAssetUploadRequest(
    val attachmentId: String,
    val storageKey: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sha256: String,
    val expiresAtEpochMillis: Long,
)

data class VoiceAssetUploadIntent(
    val attachmentId: String,
    val uploadUrl: String,
    val requiredHeaders: Map<String, String>,
    val expiresAtEpochMillis: Long,
)

data class VoiceAssetMetadata(
    val storageKey: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sha256: String,
)

data class VoiceAssetReadHandle(
    val readUrl: String,
    val expiresAtEpochMillis: Long,
)

interface VoiceAssetStorage {
    suspend fun createUploadIntent(
        request: VoiceAssetUploadRequest,
    ): VoiceAttachmentResult<VoiceAssetUploadIntent>

    suspend fun head(storageKey: String): VoiceAttachmentResult<VoiceAssetMetadata>

    suspend fun createReadHandle(
        storageKey: String,
        expiresAtEpochMillis: Long,
    ): VoiceAttachmentResult<VoiceAssetReadHandle>

    suspend fun delete(storageKey: String): VoiceAttachmentResult<Unit>
}

data class VoiceTranscriptionRequest(
    val attachmentId: String,
    val sourceUrl: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val languageHint: String = "",
)

data class VoiceTranscriptResult(
    val text: String,
    val language: String = "",
    val provider: String,
    val model: String,
    val version: String = "",
)

interface VoiceTranscriptionGateway {
    suspend fun transcribe(
        request: VoiceTranscriptionRequest,
    ): VoiceAttachmentResult<VoiceTranscriptResult>
}
