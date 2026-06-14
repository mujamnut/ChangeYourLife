package com.changeyourlife.cyl.data.local.session

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class AuthTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _token = MutableStateFlow(preferences.getString(KEY_TOKEN, null))

    val token: StateFlow<String?> = _token

    fun saveToken(token: String) {
        preferences.edit {
            putString(KEY_TOKEN, token)
        }
        _token.value = token
    }

    fun clearToken() {
        preferences.edit {
            remove(KEY_TOKEN)
        }
        _token.value = null
    }

    private companion object {
        const val PREFERENCES_NAME = "cyl_auth"
        const val KEY_TOKEN = "jwt_token"
    }
}

