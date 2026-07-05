package com.changeyourlife.cyl

import android.app.Application
import android.app.Activity
import android.os.Bundle
import com.changeyourlife.cyl.data.sync.BackgroundSyncQueue
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ChangeYourLifeApplication : Application() {
    @Inject
    lateinit var backgroundSyncQueue: BackgroundSyncQueue
    @Inject
    lateinit var reminderRepository: ReminderRepository
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
