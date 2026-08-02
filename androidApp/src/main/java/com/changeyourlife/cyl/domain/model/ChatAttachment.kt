package com.changeyourlife.cyl.domain.model

import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus

data class ChatAttachment(
    val id: String,
    val messageId: String? = null,
    val sessionId: String,
    val kind: ChatAttachmentKind,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val sha256: String = "",
    val localPath: String? = null,
    val remoteAssetId: String? = null,
    val waveform: List<Int> = emptyList(),
    val transcript: String? = null,
    val language: String? = null,
    val status: ChatAttachmentStatus,
    val progressPercent: Int = 0,
    val aiJobId: String? = null,
    val errorCode: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RecordedVoiceNote(
    val id: String,
    val localPath: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sha256: String,
)

enum class VoiceRecorderError {
    AlreadyRecording,
    StartFailed,
    RecordingTooShort,
    RecordingFailed,
    LimitExceeded,
}

sealed interface VoiceRecorderResult<out T> {
    data class Success<T>(val value: T) : VoiceRecorderResult<T>
    data class Failure(val error: VoiceRecorderError) : VoiceRecorderResult<Nothing>
}

data class ChatAudioPlaybackState(
    val attachmentId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorCode: String? = null,
)

object VoiceNoteLimits {
    const val MinimumDurationMs = 500L
    const val MaximumDurationMs = 5 * 60 * 1_000L
    const val MaximumFileBytes = 10L * 1024L * 1024L
    const val MimeType = "audio/mp4"
}
