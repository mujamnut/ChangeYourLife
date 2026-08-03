package com.changeyourlife.cyl.data.asset

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import com.changeyourlife.cyl.domain.repository.ContentAssetUploadScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundContentAssetUploadScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: ContentAssetRepository,
) : ContentAssetUploadScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun enqueue(assetId: String) {
        if (assetId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<ContentAssetUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(ContentAssetUploadWork.InputAssetId to assetId))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                UploadBackoffDelaySeconds,
                TimeUnit.SECONDS,
            )
            .addTag(ContentAssetUploadWork.WorkTag)
            .build()
        workManager.enqueueUniqueWork(
            ContentAssetUploadWork.WorkNamePrefix + assetId,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override suspend fun resumePendingUploads() {
        repository.getPendingUploads().forEach { asset -> enqueue(asset.id) }
    }

    private companion object {
        const val UploadBackoffDelaySeconds = 30L
    }
}
