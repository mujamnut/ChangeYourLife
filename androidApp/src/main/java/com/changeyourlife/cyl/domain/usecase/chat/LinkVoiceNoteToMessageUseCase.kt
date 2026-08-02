package com.changeyourlife.cyl.domain.usecase.chat

import com.changeyourlife.cyl.domain.repository.ChatAttachmentRepository
import javax.inject.Inject

class LinkVoiceNoteToMessageUseCase @Inject constructor(
    private val repository: ChatAttachmentRepository,
) {
    suspend operator fun invoke(
        attachmentId: String,
        messageId: String,
        sessionId: String,
    ) {
        repository.linkToMessage(
            attachmentId = attachmentId,
            messageId = messageId,
            sessionId = sessionId,
            updatedAt = System.currentTimeMillis(),
        )
    }
}
