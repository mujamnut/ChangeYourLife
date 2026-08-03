package com.changeyourlife.cyl

import android.app.Application
import android.app.Activity
import android.os.Bundle
import com.changeyourlife.cyl.data.sync.BackgroundSyncQueue
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.ChatAttachmentUploadScheduler
import com.changeyourlife.cyl.domain.repository.ContentAssetUploadScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ChangeYourLifeApplication : Application() {
    @Inject
    lateinit var backgroundSyncQueue: BackgroundSyncQueue
    @Inject
    lateinit var reminderRepository: ReminderRepository
    @Inject
    lateinit var chatAttachmentUploadScheduler: ChatAttachmentUploadScheduler
    @Inject
    lateinit var contentAssetUploadScheduler: ContentAssetUploadScheduler
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ForegroundSyncLifecycleCallbacks())
        backgroundSyncQueue.ensurePeriodicPullScheduled()
        backgroundSyncQueue.syncSessionSoon()
        applicationScope.launch {
            reminderRepository.reschedulePendingReminders()
        }
        applicationScope.launch {
            try {
                chatAttachmentUploadScheduler.resumePendingUploads()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // WorkManager and the next app start will retry durable queued attachments.
            }
        }
        applicationScope.launch {
            try {
                contentAssetUploadScheduler.resumePendingUploads()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Durable asset rows remain queued and are retried at the next app start.
            }
        }
    }

    private inner class ForegroundSyncLifecycleCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivityCount += 1
            if (startedActivityCount == 1) {
                backgroundSyncQueue.startForegroundRefreshLoop()
            }
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            if (startedActivityCount == 0) {
                backgroundSyncQueue.stopForegroundRefreshLoop()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
