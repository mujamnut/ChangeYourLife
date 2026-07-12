package com.changeyourlife.cyl.data.local.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class SyncSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _isAutoSyncEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    )
    val isAutoSyncEnabled: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    fun setAutoSyncEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
        _isAutoSyncEnabled.update { enabled }
    }

    companion object {
        private const val PREFERENCES_NAME = "sync_settings"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    }
}
