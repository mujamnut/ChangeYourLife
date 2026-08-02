package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.ChatAttachment
import com.changeyourlife.cyl.domain.model.ChatAudioPlaybackState
import com.changeyourlife.cyl.domain.model.RecordedVoiceNote
import com.changeyourlife.cyl.domain.model.VoiceRecorderResult
import kotlinx.coroutines.flow.StateFlow

interface VoiceRecorder {
    suspend fun start(): VoiceRecorderResult<Unit>

    suspend fun stop(): VoiceRecorderResult<RecordedVoiceNote>

    suspend fun cancel()

    suspend fun delete(localPath: String)

    fun currentAmplitude(): Int

    fun release()
}

interface ChatAudioPlayer {
    val state: StateFlow<ChatAudioPlaybackState>

    fun toggle(attachment: ChatAttachment)

    fun pause()

    fun stop()

    fun release()
}
