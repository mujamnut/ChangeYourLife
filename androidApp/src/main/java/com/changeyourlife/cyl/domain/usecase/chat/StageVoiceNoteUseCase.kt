package com.changeyourlife.cyl.domain.usecase.chat

import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.domain.model.ChatAttachment
import com.changeyourlife.cyl.domain.model.RecordedVoiceNote
import com.changeyourlife.cyl.domain.repository.ChatAttachmentRepository
import javax.inject.Inject

class StageVoiceNoteUseCase @Inject constructor(
    private val repository: ChatAttachmentRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        recording: RecordedVoiceNote,
        waveform: List<Int>,
    ): ChatAttachment {
        val now = System.currentTimeMillis()
        val attachment = ChatAttachment(
            id = recording.id,
            sessionId = sessionId.ifBlank { DraftVoiceSessionId },
            kind = ChatAttachmentKind.Audio,
            name = recording.name,
            mimeType = recording.mimeType,
            sizeBytes = recording.sizeBytes,
            durationMs = recording.durationMs,
            sha256 = recording.sha256,
            localPath = recording.localPath,
            waveform = waveform.map { value -> value.coerceIn(0, 100) },
            status = ChatAttachmentStatus.LocalReady,
            createdAt = now,
            updatedAt = now,
        )
        repository.upsert(attachment)
        return attachment
    }

    private companion object {
        const val DraftVoiceSessionId = "draft-voice-chat"
    }
}
