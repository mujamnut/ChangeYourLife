package com.changeyourlife.cyl.data.attachment

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.changeyourlife.cyl.domain.repository.ChatAttachmentRepository
import com.changeyourlife.cyl.domain.repository.ChatAttachmentUploadScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundChatAttachmentUploadScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: ChatAttachmentRepository,
) : ChatAttachmentUploadScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun enqueue(attachmentId: String) {
        if (attachmentId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<ChatAttachmentUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                workDataOf(ChatAttachmentUploadWork.InputAttachmentId to attachmentId),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                UploadBackoffDelaySeconds,
                TimeUnit.SECONDS,
            )
            .addTag(ChatAttachmentUploadWork.WorkTag)
            .build()
        workManager.enqueueUniqueWork(
            ChatAttachmentUploadWork.WorkNamePrefix + attachmentId,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override suspend fun resumePendingUploads() {
        repository.getPendingUploads().forEach { attachment -> enqueue(attachment.id) }
    }

    private companion object {
        const val UploadBackoffDelaySeconds = 30L
    }
}
