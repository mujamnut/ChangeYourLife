package com.changeyourlife.cyl.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleSoon() {
        enqueue(delayMs = 0L, policy = ExistingWorkPolicy.REPLACE)
    }

    fun scheduleRetry(delayMs: Long = DefaultRetryDelayMs) {
        enqueue(delayMs = delayMs, policy = ExistingWorkPolicy.KEEP)
    }

    private fun enqueue(delayMs: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<CylSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkerBackoffDelayMs,
                TimeUnit.MILLISECONDS,
            )
            .addTag(SyncWorkTag)
            .build()

        workManager.enqueueUniqueWork(SyncWorkName, policy, request)
    }

    private companion object {
        const val SyncWorkName = "cyl-sync-pending"
        const val SyncWorkTag = "CYL_SYNC"
        const val DefaultRetryDelayMs = 30_000L
        const val WorkerBackoffDelayMs = 30_000L
    }
}
