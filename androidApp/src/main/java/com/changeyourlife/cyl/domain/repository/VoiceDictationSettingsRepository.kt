package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.VoiceDictationLanguage
import kotlinx.coroutines.flow.StateFlow

interface VoiceDictationSettingsRepository {
    val language: StateFlow<VoiceDictationLanguage>

    fun setLanguage(language: VoiceDictationLanguage)
}
