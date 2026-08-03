package com.changeyourlife.cyl.data.local.session

import android.content.Context
import com.changeyourlife.cyl.domain.model.VoiceDictationLanguage
import com.changeyourlife.cyl.domain.repository.VoiceDictationSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidVoiceDictationSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : VoiceDictationSettingsRepository {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val mutableLanguage = MutableStateFlow(
        VoiceDictationLanguage.fromStorageValue(
            preferences.getString(LanguageKey, VoiceDictationLanguage.AUTO.storageValue),
        ),
    )

    override val language: StateFlow<VoiceDictationLanguage> = mutableLanguage.asStateFlow()

    override fun setLanguage(language: VoiceDictationLanguage) {
        if (mutableLanguage.value == language) return
        preferences.edit().putString(LanguageKey, language.storageValue).apply()
        mutableLanguage.value = language
    }

    private companion object {
        const val PreferencesName = "voice_dictation_settings"
        const val LanguageKey = "dictation_language"
    }
}
