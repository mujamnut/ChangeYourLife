package com.changeyourlife.cyl.data.attachment

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.changeyourlife.cyl.domain.usecase.chat.UploadVoiceNoteUseCase
import com.changeyourlife.cyl.domain.usecase.chat.VoiceNoteUploadWorkResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class ChatAttachmentUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val attachmentId = inputData.getString(ChatAttachmentUploadWork.InputAttachmentId)
            ?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            UploadWorkerEntryPoint::class.java,
        )
        return try {
            when (val result = entryPoint.uploadVoiceNoteUseCase()(attachmentId)) {
                VoiceNoteUploadWorkResult.Completed,
                VoiceNoteUploadWorkResult.NoWork -> Result.success()
                is VoiceNoteUploadWorkResult.Retry -> Result.retry()
                is VoiceNoteUploadWorkResult.PermanentFailure -> Result.failure(
                    workDataOf(ChatAttachmentUploadWork.OutputErrorCode to result.errorCode),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UploadWorkerEntryPoint {
        fun uploadVoiceNoteUseCase(): UploadVoiceNoteUseCase
    }
}

internal object ChatAttachmentUploadWork {
    const val InputAttachmentId = "attachmentId"
    const val OutputErrorCode = "errorCode"
    const val WorkNamePrefix = "cyl-chat-attachment-upload:"
    const val WorkTag = "CYL_CHAT_ATTACHMENT_UPLOAD"
}
