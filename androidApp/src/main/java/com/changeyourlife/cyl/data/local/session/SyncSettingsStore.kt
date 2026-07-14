package com.changeyourlife.cyl.data.local.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

@Singleton
class SyncSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _isAutoSyncEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    )
    val isAutoSyncEnabled: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(
        runCatching {
            AppThemeMode.valueOf(
                preferences.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
            )
        }.getOrDefault(AppThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun setAutoSyncEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
        _isAutoSyncEnabled.update { enabled }
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.update { mode }
    }

    companion object {
        private const val PREFERENCES_NAME = "sync_settings"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
