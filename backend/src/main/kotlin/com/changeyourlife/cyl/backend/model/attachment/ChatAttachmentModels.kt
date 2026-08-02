package com.changeyourlife.cyl.backend.model.attachment

import kotlinx.serialization.Serializable

@Serializable
data class CreateChatAttachmentUploadIntentRequest(
    val kind: String = "audio",
    val mimeType: String = "",
    val originalName: String = "",
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val sha256: String = "",
    val sessionClientId: String = "",
    val messageClientId: String? = null,
)

@Serializable
data class ChatAttachmentUploadIntentResponse(
    val attachmentId: String,
    val status: String,
    val uploadUrl: String? = null,
    val requiredHeaders: Map<String, String> = emptyMap(),
    val expiresAtEpochMillis: Long? = null,
    val replayed: Boolean,
)

@Serializable
data class ChatAttachmentResponse(
    val attachmentId: String,
    val sessionClientId: String,
    val messageClientId: String? = null,
    val kind: String,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sha256: String,
    val status: String,
    val transcript: String? = null,
    val transcriptLanguage: String? = null,
    val errorCode: String? = null,
    val playbackUrl: String? = null,
    val playbackUrlExpiresAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class ChatAttachmentErrorResponse(
    val error: ChatAttachmentApiError,
)

@Serializable
data class ChatAttachmentApiError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
