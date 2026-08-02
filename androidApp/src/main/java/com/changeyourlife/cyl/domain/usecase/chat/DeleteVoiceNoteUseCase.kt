package com.changeyourlife.cyl.domain.usecase.chat

import com.changeyourlife.cyl.domain.model.ChatAttachment
import com.changeyourlife.cyl.domain.repository.ChatAttachmentRepository
import com.changeyourlife.cyl.domain.repository.VoiceRecorder
import javax.inject.Inject

class DeleteVoiceNoteUseCase @Inject constructor(
    private val repository: ChatAttachmentRepository,
    private val recorder: VoiceRecorder,
) {
    suspend operator fun invoke(attachment: ChatAttachment) {
        attachment.localPath?.takeIf(String::isNotBlank)?.let { path -> recorder.delete(path) }
        repository.deleteLocal(attachment.id)
    }
}
