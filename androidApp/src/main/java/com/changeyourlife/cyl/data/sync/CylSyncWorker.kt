package com.changeyourlife.cyl.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import retrofit2.HttpException

class CylSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java,
        )
        return runCatching {
            val syncCoordinator = entryPoint.sessionSyncCoordinator()
            val completed = when (inputData.getString(SyncWorkerMode.InputKey)) {
                SyncWorkerMode.SessionSync -> syncCoordinator.syncSessionForWorker()
                else -> syncCoordinator.pushPendingChangesForWorker()
            }
            if (completed) {
                Result.success()
            } else {
                Result.retry()
            }
        }.getOrElse { error ->
            if (error is HttpException && error.code() == 401) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun sessionSyncCoordinator(): SessionSyncCoordinator
    }
}
