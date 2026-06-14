package com.changeyourlife.cyl.data.local.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class WorkspaceSelectionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _activeWorkspaceId = MutableStateFlow(preferences.getString(KEY_ACTIVE_WORKSPACE_ID, null))

    val activeWorkspaceId: StateFlow<String?> = _activeWorkspaceId

    fun setActiveWorkspaceId(workspaceId: String) {
        preferences.edit()
            .putString(KEY_ACTIVE_WORKSPACE_ID, workspaceId)
            .apply()
        _activeWorkspaceId.value = workspaceId
    }

    companion object {
        const val PREFERENCES_NAME = "cyl_workspace_selection"
        const val KEY_ACTIVE_WORKSPACE_ID = "active_workspace_id"
    }
}
