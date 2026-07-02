package com.changeyourlife.cyl.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun schedulePendingSoon() {
        enqueueOneTime(
            delayMs = 0L,
            policy = ExistingWorkPolicy.REPLACE,
            mode = SyncWorkerMode.PendingPush,
        )
    }

    fun scheduleSessionSoon() {
        enqueueOneTime(
            delayMs = 0L,
            policy = ExistingWorkPolicy.REPLACE,
            mode = SyncWorkerMode.SessionSync,
        )
    }

    fun scheduleRetry(delayMs: Long = DefaultRetryDelayMs) {
        enqueueOneTime(
            delayMs = delayMs,
            policy = ExistingWorkPolicy.KEEP,
            mode = SyncWorkerMode.PendingPush,
        )
    }

    fun schedulePeriodicPull() {
        val request = PeriodicWorkRequestBuilder<CylSyncWorker>(
            PeriodicPullIntervalMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(SyncWorkerMode.InputKey to SyncWorkerMode.SessionSync))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkerBackoffDelayMs,
                TimeUnit.MILLISECONDS,
            )
            .addTag(SyncWorkTag)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PeriodicPullWorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun enqueueOneTime(
        delayMs: Long,
        policy: ExistingWorkPolicy,
        mode: String,
    ) {
        val request = OneTimeWorkRequestBuilder<CylSyncWorker>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(SyncWorkerMode.InputKey to mode))
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

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    private companion object {
        const val SyncWorkName = "cyl-sync-pending"
        const val PeriodicPullWorkName = "cyl-sync-periodic-pull"
        const val SyncWorkTag = "CYL_SYNC"
        const val DefaultRetryDelayMs = 30_000L
        const val WorkerBackoffDelayMs = 30_000L
        const val PeriodicPullIntervalMinutes = 30L
    }
}

internal object SyncWorkerMode {
    const val InputKey = "syncMode"
    const val PendingPush = "pendingPush"
    const val SessionSync = "sessionSync"
}
