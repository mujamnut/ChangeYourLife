package com.changeyourlife.cyl.presentation

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.changeyourlife.cyl.data.local.session.SyncSettingsStore
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.presentation.app.CylApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var syncSettingsStore: SyncSettingsStore

    private var wasExactAlarmAllowed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wasExactAlarmAllowed = canScheduleExactAlarms()
        requestNotificationPermission()
        requestExactAlarmPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            val themeMode by syncSettingsStore.themeMode.collectAsState()
            CylApp(themeMode = themeMode)
        }
    }

    override fun onResume() {
        super.onResume()
        val isExactAlarmAllowed = canScheduleExactAlarms()
        if (isExactAlarmAllowed && !wasExactAlarmAllowed) {
            lifecycleScope.launch {
                reminderRepository.reschedulePendingReminders()
            }
        }
        wasExactAlarmAllowed = isExactAlarmAllowed
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NotificationPermissionRequestCode) {
            requestExactAlarmPermissionIfNeeded()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NotificationPermissionRequestCode)
        }
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (canScheduleExactAlarms()) return

        val preferences = getSharedPreferences(PermissionPreferencesName, Context.MODE_PRIVATE)
        if (preferences.getBoolean(ExactAlarmPermissionRequestedKey, false)) return
        preferences.edit().putBoolean(ExactAlarmPermissionRequestedKey, true).apply()

        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private companion object {
        const val NotificationPermissionRequestCode = 1001
        const val PermissionPreferencesName = "cyl_permission_prompts"
        const val ExactAlarmPermissionRequestedKey = "exact_alarm_permission_requested"
    }
}
