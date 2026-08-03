package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.VoiceDictationEvent
import kotlinx.coroutines.flow.Flow

interface VoiceDictationEngine {
    val events: Flow<VoiceDictationEvent>

    fun start(languageTag: String)

    fun stop()

    fun cancel()

    fun release()
}
