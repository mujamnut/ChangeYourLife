package com.changeyourlife.cyl.domain.usecase.chat

import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.domain.repository.ChatAttachmentRepository
import com.changeyourlife.cyl.domain.repository.ChatAttachmentUploadScheduler
import com.changeyourlife.cyl.domain.repository.ChatHistoryRepository
import javax.inject.Inject

class QueueVoiceNoteUploadUseCase @Inject constructor(
    private val attachmentRepository: ChatAttachmentRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val uploadScheduler: ChatAttachmentUploadScheduler,
) {
    suspend operator fun invoke(attachmentId: String): Boolean {
        val attachment = attachmentRepository.getById(attachmentId) ?: return false
        if (attachment.messageId.isNullOrBlank() || attachment.localPath.isNullOrBlank()) return false
        if (attachment.status in RemoteCompleteStatuses) return true
        if (attachment.status == ChatAttachmentStatus.Deleted ||
            attachment.status == ChatAttachmentStatus.PermanentFailure
        ) {
            return false
        }

        val queued = attachmentRepository.transitionUploadState(
            attachmentId = attachment.id,
            expectedStatuses = setOf(
                ChatAttachmentStatus.LocalReady,
                ChatAttachmentStatus.UploadQueued,
                ChatAttachmentStatus.RetryableFailure,
            ),
            nextStatus = ChatAttachmentStatus.UploadQueued,
            progressPercent = attachment.progressPercent,
            errorCode = null,
            updatedAt = System.currentTimeMillis(),
        )
        val current = attachmentRepository.getById(attachment.id) ?: return false
        if (queued || current.status in ResumableUploadStatuses) {
            current.messageId?.let { messageId ->
                chatHistoryRepository.updateMessageAttachment(
                    messageId = messageId,
                    attachment = current.toMessageAttachment(),
                    syncRemote = false,
                )
            }
            runCatching { uploadScheduler.enqueue(current.id) }
            return true
        }
        return current.status in RemoteCompleteStatuses
    }
}

private val ResumableUploadStatuses = setOf(
    ChatAttachmentStatus.UploadQueued,
    ChatAttachmentStatus.Uploading,
    ChatAttachmentStatus.PendingUpload,
    ChatAttachmentStatus.RetryableFailure,
)

internal val RemoteCompleteStatuses = setOf(
    ChatAttachmentStatus.Uploaded,
    ChatAttachmentStatus.Transcribing,
    ChatAttachmentStatus.Ready,
    ChatAttachmentStatus.AiQueued,
    ChatAttachmentStatus.AiProcessing,
    ChatAttachmentStatus.Completed,
)
