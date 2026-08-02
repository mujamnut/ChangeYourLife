package com.changeyourlife.cyl.domain.usecase.chat

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.domain.model.ChatAttachment
import com.changeyourlife.cyl.domain.model.ChatMessageAttachment
import com.changeyourlife.cyl.domain.repository.ChatAttachmentChecksumResult
import com.changeyourlife.cyl.domain.repository.ChatAttachmentRemoteUploadResult
import com.changeyourlife.cyl.domain.repository.ChatAttachmentRepository
import com.changeyourlife.cyl.domain.repository.ChatAttachmentUploadGateway
import com.changeyourlife.cyl.domain.repository.ChatHistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class UploadVoiceNoteUseCase @Inject constructor(
    private val attachmentRepository: ChatAttachmentRepository,
    private val uploadGateway: ChatAttachmentUploadGateway,
    private val chatHistoryRepository: ChatHistoryRepository,
) {
    suspend operator fun invoke(attachmentId: String): VoiceNoteUploadWorkResult {
        val initial = attachmentRepository.getById(attachmentId)
            ?: return VoiceNoteUploadWorkResult.NoWork
        if (initial.status in RemoteCompleteStatuses) return VoiceNoteUploadWorkResult.Completed
        if (initial.status == ChatAttachmentStatus.Deleted) return VoiceNoteUploadWorkResult.NoWork
        if (initial.status == ChatAttachmentStatus.PermanentFailure) {
            return VoiceNoteUploadWorkResult.PermanentFailure(
                initial.errorCode.orEmpty().ifBlank { ChatAttachmentErrorCode.Unknown.wireValue },
            )
        }
        if (initial.messageId.isNullOrBlank() || initial.localPath.isNullOrBlank()) {
            return persistFailure(
                attachment = initial,
                code = ChatAttachmentErrorCode.InvalidRequest,
                retryable = false,
            )
        }

        val checksum = if (initial.sha256.matches(Sha256Pattern)) {
            initial.sha256.lowercase()
        } else {
            when (val result = uploadGateway.calculateSha256(initial)) {
                is ChatAttachmentChecksumResult.Success -> result.sha256.lowercase()
                is ChatAttachmentChecksumResult.Failure -> return persistFailure(
                    attachment = initial,
                    code = result.code,
                    retryable = result.retryable,
                )
            }
        }
        val workingStatus = if (initial.status == ChatAttachmentStatus.PendingUpload) {
            ChatAttachmentStatus.PendingUpload
        } else {
            ChatAttachmentStatus.Uploading
        }
        val enteredWorkingState = attachmentRepository.transitionUploadState(
            attachmentId = initial.id,
            expectedStatuses = UploadStartStatuses,
            nextStatus = workingStatus,
            progressPercent = 0,
            errorCode = null,
            sha256 = checksum,
            updatedAt = System.currentTimeMillis(),
        )
        if (!enteredWorkingState) {
            val current = attachmentRepository.getById(initial.id)
                ?: return VoiceNoteUploadWorkResult.NoWork
            return when {
                current.status in RemoteCompleteStatuses -> VoiceNoteUploadWorkResult.Completed
                current.status == ChatAttachmentStatus.Deleted -> VoiceNoteUploadWorkResult.NoWork
                current.status == ChatAttachmentStatus.PermanentFailure ->
                    VoiceNoteUploadWorkResult.PermanentFailure(current.errorCode.orEmpty())
                else -> VoiceNoteUploadWorkResult.Retry(current.errorCode.orEmpty())
            }
        }

        val prepared = attachmentRepository.getById(initial.id)
            ?: return VoiceNoteUploadWorkResult.NoWork
        publishSnapshot(prepared, syncRemote = false)
        return uploadAndPersist(prepared)
    }

    private suspend fun uploadAndPersist(
        attachment: ChatAttachment,
    ): VoiceNoteUploadWorkResult = coroutineScope {
        val signals = Channel<UploadSignal>(capacity = Channel.UNLIMITED)
        val stateJob = launch {
            var latestProgress = attachment.progressPercent.coerceIn(0, 99)
            for (signal in signals) {
                when (signal) {
                    is UploadSignal.RemoteAccepted -> {
                        persistWorkingState(
                            attachmentId = attachment.id,
                            status = attachment.status,
                            progressPercent = latestProgress,
                            remoteAssetId = signal.remoteAssetId,
                        )
                    }
                    is UploadSignal.Progress -> {
                        val next = signal.percent.coerceIn(0, 99)
                        if (next > latestProgress) {
                            latestProgress = next
                            persistWorkingState(
                                attachmentId = attachment.id,
                                status = attachment.status,
                                progressPercent = latestProgress,
                            )
                        }
                    }
                }
            }
        }

        val result = try {
            try {
                uploadGateway.upload(
                    attachment = attachment,
                    onRemoteAccepted = { remoteAssetId ->
                        signals.trySend(UploadSignal.RemoteAccepted(remoteAssetId))
                    },
                    onProgress = { percent -> signals.trySend(UploadSignal.Progress(percent)) },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                ChatAttachmentRemoteUploadResult.Failure(
                    code = ChatAttachmentErrorCode.StorageUnavailable,
                    retryable = true,
                )
            }
        } finally {
            signals.close()
            stateJob.join()
        }

        when (result) {
            is ChatAttachmentRemoteUploadResult.Success -> {
                val finalStatus = result.status.takeIf { it in RemoteCompleteStatuses }
                    ?: ChatAttachmentStatus.Uploaded
                val transitioned = attachmentRepository.transitionUploadState(
                    attachmentId = attachment.id,
                    expectedStatuses = UploadWorkingStatuses,
                    nextStatus = finalStatus,
                    progressPercent = 100,
                    errorCode = null,
                    remoteAssetId = result.remoteAssetId,
                    updatedAt = System.currentTimeMillis(),
                )
                val current = attachmentRepository.getById(attachment.id)
                    ?: return@coroutineScope VoiceNoteUploadWorkResult.NoWork
                publishSnapshot(current, syncRemote = transitioned)
                when {
                    current.status in RemoteCompleteStatuses -> VoiceNoteUploadWorkResult.Completed
                    current.status == ChatAttachmentStatus.Deleted -> VoiceNoteUploadWorkResult.NoWork
                    current.status == ChatAttachmentStatus.PermanentFailure ->
                        VoiceNoteUploadWorkResult.PermanentFailure(current.errorCode.orEmpty())
                    else -> VoiceNoteUploadWorkResult.Retry(
                        current.errorCode.orEmpty().ifBlank {
                            ChatAttachmentErrorCode.InvalidState.wireValue
                        },
                    )
                }
            }
            is ChatAttachmentRemoteUploadResult.Failure -> persistFailure(
                attachment = attachmentRepository.getById(attachment.id) ?: attachment,
                code = result.code,
                retryable = result.retryable,
            )
        }
    }

    private suspend fun persistWorkingState(
        attachmentId: String,
        status: ChatAttachmentStatus,
        progressPercent: Int,
        remoteAssetId: String? = null,
    ) {
        attachmentRepository.transitionUploadState(
            attachmentId = attachmentId,
            expectedStatuses = UploadWorkingStatuses,
            nextStatus = status,
            progressPercent = progressPercent,
            errorCode = null,
            remoteAssetId = remoteAssetId,
            updatedAt = System.currentTimeMillis(),
        )
        attachmentRepository.getById(attachmentId)?.let { current ->
            publishSnapshot(current, syncRemote = false)
        }
    }

    private suspend fun persistFailure(
        attachment: ChatAttachment,
        code: ChatAttachmentErrorCode,
        retryable: Boolean,
    ): VoiceNoteUploadWorkResult {
        val failureStatus = if (retryable) {
            ChatAttachmentStatus.RetryableFailure
        } else {
            ChatAttachmentStatus.PermanentFailure
        }
        val transitioned = attachmentRepository.transitionUploadState(
            attachmentId = attachment.id,
            expectedStatuses = UploadFailureSourceStatuses,
            nextStatus = failureStatus,
            progressPercent = attachment.progressPercent.coerceIn(0, 99),
            errorCode = code.wireValue,
            updatedAt = System.currentTimeMillis(),
        )
        val current = attachmentRepository.getById(attachment.id)
            ?: return VoiceNoteUploadWorkResult.NoWork
        publishSnapshot(current, syncRemote = transitioned)
        return when {
            current.status in RemoteCompleteStatuses -> VoiceNoteUploadWorkResult.Completed
            current.status == ChatAttachmentStatus.Deleted -> VoiceNoteUploadWorkResult.NoWork
            current.status == ChatAttachmentStatus.PermanentFailure ->
                VoiceNoteUploadWorkResult.PermanentFailure(current.errorCode.orEmpty())
            current.status == ChatAttachmentStatus.RetryableFailure ->
                VoiceNoteUploadWorkResult.Retry(current.errorCode.orEmpty())
            retryable -> VoiceNoteUploadWorkResult.Retry(code.wireValue)
            else -> VoiceNoteUploadWorkResult.PermanentFailure(code.wireValue)
        }
    }

    private suspend fun publishSnapshot(
        attachment: ChatAttachment,
        syncRemote: Boolean,
    ) {
        val messageId = attachment.messageId ?: return
        chatHistoryRepository.updateMessageAttachment(
            messageId = messageId,
            attachment = attachment.toMessageAttachment(),
            syncRemote = syncRemote,
        )
    }
}

sealed interface VoiceNoteUploadWorkResult {
    data object Completed : VoiceNoteUploadWorkResult
    data object NoWork : VoiceNoteUploadWorkResult
    data class Retry(val errorCode: String) : VoiceNoteUploadWorkResult
    data class PermanentFailure(val errorCode: String) : VoiceNoteUploadWorkResult
}

internal fun ChatAttachment.toMessageAttachment(): ChatMessageAttachment = ChatMessageAttachment(
    id = id,
    name = name,
    mimeType = mimeType,
    kind = kind.wireValue,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    sha256 = sha256,
    localPath = localPath.orEmpty(),
    remoteAssetId = remoteAssetId.orEmpty(),
    waveform = waveform,
    transcript = transcript.orEmpty(),
    language = language.orEmpty(),
    status = status.wireValue,
    progressPercent = progressPercent,
    aiJobId = aiJobId.orEmpty(),
    errorCode = errorCode.orEmpty(),
)

private sealed interface UploadSignal {
    data class RemoteAccepted(val remoteAssetId: String) : UploadSignal
    data class Progress(val percent: Int) : UploadSignal
}

private val UploadStartStatuses = setOf(
    ChatAttachmentStatus.LocalReady,
    ChatAttachmentStatus.UploadQueued,
    ChatAttachmentStatus.Uploading,
    ChatAttachmentStatus.PendingUpload,
    ChatAttachmentStatus.RetryableFailure,
)
private val UploadWorkingStatuses = setOf(
    ChatAttachmentStatus.Uploading,
    ChatAttachmentStatus.PendingUpload,
)
private val UploadFailureSourceStatuses = UploadStartStatuses + UploadWorkingStatuses
private val Sha256Pattern = Regex("[A-Fa-f0-9]{64}")
