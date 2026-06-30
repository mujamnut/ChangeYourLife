package com.changeyourlife.cyl

import android.app.Application
import com.changeyourlife.cyl.data.sync.BackgroundSyncQueue
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ChangeYourLifeApplication : Application() {
    @Inject
    lateinit var backgroundSyncQueue: BackgroundSyncQueue

    override fun onCreate() {
        super.onCreate()
        backgroundSyncQueue.syncSessionSoon()
    }
}
