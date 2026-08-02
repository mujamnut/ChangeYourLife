package com.changeyourlife.cyl.aicontract

import kotlinx.serialization.Serializable

const val CYL_CHAT_ATTACHMENT_SCHEMA_NAME = "CYL_CHAT_ATTACHMENT_SCHEMA"
const val CYL_CHAT_ATTACHMENT_SCHEMA_VERSION = 1

enum class ChatAttachmentKind(val wireValue: String) {
    Image("image"),
    TextFile("text"),
    Audio("audio"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWireValue(value: String): ChatAttachmentKind =
            entries.firstOrNull { kind -> kind.wireValue.equals(value.trim(), ignoreCase = true) }
                ?: Unknown
    }
}

enum class ChatAttachmentStatus(val wireValue: String) {
    LocalReady("local_ready"),
    UploadQueued("upload_queued"),
    Uploading("uploading"),
    PendingUpload("pending_upload"),
    Uploaded("uploaded"),
    Transcribing("transcribing"),
    Ready("ready"),
    AiQueued("ai_queued"),
    AiProcessing("ai_processing"),
    Completed("completed"),
    RetryableFailure("retryable_failure"),
    PermanentFailure("permanent_failure"),
    Deleted("deleted"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWireValue(value: String): ChatAttachmentStatus =
            entries.firstOrNull { status -> status.wireValue.equals(value.trim(), ignoreCase = true) }
                ?: Unknown
    }
}

enum class ChatAttachmentErrorCode(val wireValue: String, val retryable: Boolean) {
    FeatureDisabled("feature_disabled", false),
    InvalidRequest("invalid_request", false),
    InvalidState("invalid_state", true),
    IdempotencyConflict("idempotency_conflict", false),
    MicrophonePermissionDenied("microphone_permission_denied", false),
    RecordingTooShort("recording_too_short", true),
    RecordingFailed("recording_failed", true),
    AudioLimitExceeded("audio_limit_exceeded", true),
    UploadOffline("upload_offline", true),
    UploadUrlExpired("upload_url_expired", true),
    UploadValidationFailed("upload_validation_failed", true),
    AttachmentForbidden("attachment_forbidden", false),
    AttachmentNotFound("attachment_not_found", false),
    StorageUnavailable("storage_unavailable", true),
    TranscriptionUnavailable("transcription_unavailable", true),
    TranscriptionFailed("transcription_failed", true),
    NoSpeechDetected("no_speech_detected", true),
    AiProcessingFailed("ai_processing_failed", true),
    PlaybackFailed("playback_failed", true),
    Unknown("unknown", false),
    ;

    companion object {
        fun fromWireValue(value: String): ChatAttachmentErrorCode =
            entries.firstOrNull { code -> code.wireValue.equals(value.trim(), ignoreCase = true) }
                ?: Unknown
    }
}

/**
 * Backward-compatible attachment input used by the existing AI request.
 *
 * Image and text callers continue to use inline payloads. Audio callers must use
 * [assetId]; raw audio bytes or audio data URLs are intentionally not represented.
 */
@Serializable
data class AiAttachmentInputWire(
    val assetId: String = "",
    val dataUrl: String = "",
    val textContent: String = "",
    val mimeType: String = "",
    val name: String = "",
    val sizeBytes: Long = 0,
    val kind: String = ChatAttachmentKind.Image.wireValue,
    val durationMs: Long? = null,
    val sha256: String = "",
) {
    val attachmentKind: ChatAttachmentKind
        get() = ChatAttachmentKind.fromWireValue(kind)

    val isRemoteReference: Boolean
        get() = assetId.isNotBlank()

    val hasInlinePayload: Boolean
        get() = dataUrl.isNotBlank() || textContent.isNotBlank()

    fun validate(): List<ChatAttachmentContractIssue> = buildList {
        if (sizeBytes < 0L) {
            add(
                ChatAttachmentContractIssue(
                    field = "sizeBytes",
                    code = "invalid_attachment_size",
                    message = "Attachment sizeBytes must not be negative.",
                ),
            )
        }
        if (durationMs != null && durationMs < 0L) {
            add(
                ChatAttachmentContractIssue(
                    field = "durationMs",
                    code = "invalid_attachment_duration",
                    message = "Attachment durationMs must not be negative.",
                ),
            )
        }
        if (attachmentKind == ChatAttachmentKind.Unknown) {
            add(
                ChatAttachmentContractIssue(
                    field = "kind",
                    code = "unsupported_attachment_kind",
                    message = "Attachment kind is not supported.",
                ),
            )
        }
        if (attachmentKind == ChatAttachmentKind.Audio) {
            if (assetId.isBlank()) {
                add(
                    ChatAttachmentContractIssue(
                        field = "assetId",
                        code = "missing_audio_asset_id",
                        message = "Audio attachments require a private assetId.",
                    ),
                )
            }
            if (hasInlinePayload) {
                add(
                    ChatAttachmentContractIssue(
                        field = "dataUrl",
                        code = "inline_audio_not_allowed",
                        message = "Audio attachments cannot contain inline dataUrl or textContent payloads.",
                    ),
                )
            }
            if (!mimeType.startsWith("audio/", ignoreCase = true)) {
                add(
                    ChatAttachmentContractIssue(
                        field = "mimeType",
                        code = "invalid_audio_mime_type",
                        message = "Audio attachment mimeType must start with audio/.",
                    ),
                )
            }
            if ((durationMs ?: 0L) <= 0L) {
                add(
                    ChatAttachmentContractIssue(
                        field = "durationMs",
                        code = "missing_audio_duration",
                        message = "Audio attachments require a positive durationMs.",
                    ),
                )
            }
            if (!sha256.matches(Sha256Pattern)) {
                add(
                    ChatAttachmentContractIssue(
                        field = "sha256",
                        code = "invalid_audio_checksum",
                        message = "Audio attachments require a lowercase or uppercase SHA-256 hex digest.",
                    ),
                )
            }
        }
    }
}

data class ChatAttachmentContractIssue(
    val field: String,
    val code: String,
    val message: String,
)

private val Sha256Pattern = Regex("[A-Fa-f0-9]{64}")

fun ChatAttachmentStatus.canTransitionTo(next: ChatAttachmentStatus): Boolean {
    if (this == next) return true
    if (next == ChatAttachmentStatus.Deleted) return this != ChatAttachmentStatus.Deleted

    return next in when (this) {
        ChatAttachmentStatus.LocalReady -> setOf(
            ChatAttachmentStatus.UploadQueued,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.UploadQueued -> setOf(
            ChatAttachmentStatus.Uploading,
            ChatAttachmentStatus.RetryableFailure,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.Uploading -> setOf(
            ChatAttachmentStatus.PendingUpload,
            ChatAttachmentStatus.Uploaded,
            ChatAttachmentStatus.RetryableFailure,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.PendingUpload -> setOf(
            ChatAttachmentStatus.Uploaded,
            ChatAttachmentStatus.RetryableFailure,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.Uploaded -> setOf(
            ChatAttachmentStatus.Transcribing,
            ChatAttachmentStatus.Ready,
            ChatAttachmentStatus.RetryableFailure,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.Transcribing -> setOf(
            ChatAttachmentStatus.Ready,
            ChatAttachmentStatus.RetryableFailure,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.Ready -> setOf(
            ChatAttachmentStatus.AiQueued,
            ChatAttachmentStatus.Completed,
            ChatAttachmentStatus.RetryableFailure,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.AiQueued -> setOf(
            ChatAttachmentStatus.AiProcessing,
            ChatAttachmentStatus.RetryableFailure,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.AiProcessing -> setOf(
            ChatAttachmentStatus.Completed,
            ChatAttachmentStatus.RetryableFailure,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.RetryableFailure -> setOf(
            ChatAttachmentStatus.UploadQueued,
            ChatAttachmentStatus.Uploading,
            ChatAttachmentStatus.PendingUpload,
            ChatAttachmentStatus.Transcribing,
            ChatAttachmentStatus.AiQueued,
            ChatAttachmentStatus.AiProcessing,
            ChatAttachmentStatus.PermanentFailure,
        )
        ChatAttachmentStatus.Completed,
        ChatAttachmentStatus.PermanentFailure,
        ChatAttachmentStatus.Deleted,
        ChatAttachmentStatus.Unknown -> emptySet()
    }
}
