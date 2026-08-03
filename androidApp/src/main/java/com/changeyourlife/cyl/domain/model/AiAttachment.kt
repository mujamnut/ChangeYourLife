package com.changeyourlife.cyl.domain.model

import com.changeyourlife.cyl.aicontract.ChatAttachmentKind

data class AiAttachment(
    val id: String = "",
    val assetId: String = "",
    val dataUrl: String = "",
    val previewDataUrl: String = "",
    val textContent: String = "",
    val mimeType: String = "",
    val name: String = "",
    val sizeBytes: Long = 0,
    val kind: String = ChatAttachmentKind.Image.wireValue,
    val durationMs: Long? = null,
    val sha256: String = "",
    val localPath: String = "",
    val waveform: List<Int> = emptyList(),
    val status: String = "",
    val source: String = "",
    val sourceReferenceId: String = "",
    val approvedAtEpochMillis: Long = 0L,
) {
    val attachmentKind: ChatAttachmentKind
        get() = ChatAttachmentKind.fromWireValue(kind)

    val isRemoteReference: Boolean
        get() = assetId.isNotBlank()
}

object AiAttachmentSources {
    const val ComposerPicker = "composer_picker"
    const val ComposerPaste = "composer_paste"
    const val ComposerCamera = "composer_camera"
    const val IncomingShare = "incoming_share"
    const val VoiceDictation = "voice_dictation"
    const val VoiceNote = "voice_note"
}

data class PrepareAiAttachmentRequest(
    val workspaceId: String,
    val sourceUri: String,
    val fallbackName: String,
    val declaredMimeType: String = "",
    val source: String = AiAttachmentSources.ComposerPicker,
    val sourceReferenceId: String = "",
    val imageOnly: Boolean = false,
)

sealed interface PrepareAiAttachmentResult {
    data class Success(val attachment: AiAttachment) : PrepareAiAttachmentResult

    data class Rejected(
        val error: AiAttachmentPreparationError,
        val detail: String = "",
    ) : PrepareAiAttachmentResult
}

enum class AiAttachmentPreparationError {
    INVALID_REQUEST,
    SOURCE_UNAVAILABLE,
    PERMISSION_DENIED,
    EMPTY_FILE,
    TOO_LARGE,
    UNSUPPORTED_TYPE,
    IMAGE_REQUIRED,
    CORRUPT_CONTENT,
    STORAGE_UNAVAILABLE,
    PERSISTENCE_FAILED,
    DRAFT_NOT_FOUND,
    DRAFT_NOT_READY,
    TOO_MANY_ATTACHMENTS,
    UNKNOWN,
}
