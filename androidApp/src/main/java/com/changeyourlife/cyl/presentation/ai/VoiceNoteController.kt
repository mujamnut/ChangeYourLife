package com.changeyourlife.cyl.presentation.ai

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.domain.model.ChatAttachment
import com.changeyourlife.cyl.domain.model.ChatAudioPlaybackState
import com.changeyourlife.cyl.domain.model.VoiceDictationError
import com.changeyourlife.cyl.domain.model.VoiceDictationEvent
import com.changeyourlife.cyl.domain.repository.ChatAudioPlayer
import com.changeyourlife.cyl.domain.repository.VoiceDictationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VoiceComposerPhase {
    Idle,
    RequestingPermission,
    Starting,
    Recording,
    Finishing,
    Recorded,
    PermissionDenied,
    Failed,
}

@Immutable
data class VoiceNoteUiState(
    val phase: VoiceComposerPhase = VoiceComposerPhase.Idle,
    val elapsedMs: Long = 0L,
    val amplitude: Int = 0,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val resultVersion: Long = 0L,
    val errorCode: String? = null,
    val permissionPermanentlyDenied: Boolean = false,
)

@HiltViewModel
class VoiceNoteController @Inject constructor(
    private val dictationEngine: VoiceDictationEngine,
    private val player: ChatAudioPlayer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VoiceNoteUiState())
    val state: StateFlow<VoiceNoteUiState> = mutableState.asStateFlow()
    val playbackState: StateFlow<ChatAudioPlaybackState> = player.state

    private var tickerJob: Job? = null
    private var processingTimeoutJob: Job? = null
    private var dictationStartedAtMs = 0L
    private var nextResultVersion = 0L

    init {
        viewModelScope.launch {
            dictationEngine.events.collect(::handleDictationEvent)
        }
    }

    fun markPermissionRequesting() {
        if (mutableState.value.phase.isActiveDictation()) return
        mutableState.update {
            it.copy(
                phase = VoiceComposerPhase.RequestingPermission,
                errorCode = null,
                permissionPermanentlyDenied = false,
            )
        }
    }

    fun onPermissionDenied(permanentlyDenied: Boolean) {
        mutableState.value = VoiceNoteUiState(
            phase = VoiceComposerPhase.PermissionDenied,
            errorCode = VoiceDictationError.PermissionDenied.wireValue,
            permissionPermanentlyDenied = permanentlyDenied,
        )
    }

    fun startDictation() {
        if (mutableState.value.phase.isActiveDictation()) return
        player.pause()
        processingTimeoutJob?.cancel()
        dictationStartedAtMs = android.os.SystemClock.elapsedRealtime()
        mutableState.value = VoiceNoteUiState(phase = VoiceComposerPhase.Starting)
        startTicker()
        dictationEngine.start(Locale.getDefault().toLanguageTag())
    }

    fun stopDictation() {
        if (!mutableState.value.phase.isActiveDictation()) return
        mutableState.update {
            it.copy(
                phase = VoiceComposerPhase.Finishing,
                amplitude = 0,
            )
        }
        dictationEngine.stop()
        startProcessingTimeout()
    }

    fun cancelDictation() {
        tickerJob?.cancel()
        tickerJob = null
        processingTimeoutJob?.cancel()
        processingTimeoutJob = null
        dictationEngine.cancel()
        dictationStartedAtMs = 0L
        mutableState.value = VoiceNoteUiState()
    }

    fun consumeTranscript(resultVersion: Long) {
        if (mutableState.value.resultVersion != resultVersion) return
        mutableState.value = VoiceNoteUiState()
    }

    fun togglePlayback(attachment: ChatAttachment) {
        player.toggle(attachment)
    }

    fun discardComposer() {
        cancelDictation()
        player.pause()
    }

    private fun handleDictationEvent(event: VoiceDictationEvent) {
        when (event) {
            VoiceDictationEvent.Ready -> {
                mutableState.update { current ->
                    if (current.phase.isActiveDictation()) {
                        current.copy(
                            phase = if (current.phase == VoiceComposerPhase.Finishing) {
                                VoiceComposerPhase.Finishing
                            } else {
                                VoiceComposerPhase.Recording
                            },
                        )
                    } else {
                        current
                    }
                }
            }
            is VoiceDictationEvent.Level -> {
                mutableState.update { current ->
                    if (current.phase.isActiveDictation()) {
                        current.copy(amplitude = event.value)
                    } else {
                        current
                    }
                }
            }
            is VoiceDictationEvent.PartialResult -> {
                mutableState.update { current ->
                    if (current.phase.isActiveDictation()) {
                        current.copy(
                            phase = if (current.phase == VoiceComposerPhase.Finishing) {
                                VoiceComposerPhase.Finishing
                            } else {
                                VoiceComposerPhase.Recording
                            },
                            partialTranscript = event.text,
                        )
                    } else {
                        current
                    }
                }
            }
            VoiceDictationEvent.Processing -> {
                startProcessingTimeout()
                mutableState.update { current ->
                    if (current.phase.isActiveDictation()) {
                        current.copy(
                            phase = VoiceComposerPhase.Finishing,
                            amplitude = 0,
                        )
                    } else {
                        current
                    }
                }
            }
            is VoiceDictationEvent.FinalResult -> completeTranscript(event.text)
            is VoiceDictationEvent.Failure -> handleFailure(event.error)
        }
    }

    private fun completeTranscript(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) {
            handleFailure(VoiceDictationError.NoSpeech)
            return
        }
        val elapsedMs = elapsedSinceStart()
        processingTimeoutJob?.cancel()
        processingTimeoutJob = null
        stopTicker()
        nextResultVersion += 1L
        mutableState.value = VoiceNoteUiState(
            phase = VoiceComposerPhase.Recorded,
            elapsedMs = elapsedMs,
            finalTranscript = text,
            resultVersion = nextResultVersion,
        )
    }

    private fun handleFailure(error: VoiceDictationError) {
        val partialText = mutableState.value.partialTranscript.trim()
        if (error == VoiceDictationError.NoSpeech && partialText.isNotBlank()) {
            completeTranscript(partialText)
            return
        }
        processingTimeoutJob?.cancel()
        processingTimeoutJob = null
        stopTicker()
        mutableState.value = VoiceNoteUiState(
            phase = if (error == VoiceDictationError.PermissionDenied) {
                VoiceComposerPhase.PermissionDenied
            } else {
                VoiceComposerPhase.Failed
            },
            errorCode = error.wireValue,
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive && mutableState.value.phase.isActiveDictation()) {
                val elapsed = elapsedSinceStart()
                mutableState.update { current -> current.copy(elapsedMs = elapsed) }
                if (elapsed >= MaximumDictationDurationMs) {
                    stopDictation()
                    break
                }
                delay(ElapsedUpdateIntervalMs)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        dictationStartedAtMs = 0L
    }

    private fun startProcessingTimeout() {
        processingTimeoutJob?.cancel()
        processingTimeoutJob = viewModelScope.launch {
            delay(ProcessingTimeoutMs)
            if (mutableState.value.phase == VoiceComposerPhase.Finishing) {
                dictationEngine.cancel()
                handleFailure(VoiceDictationError.ServiceUnavailable)
            }
        }
    }

    private fun elapsedSinceStart(): Long {
        if (dictationStartedAtMs <= 0L) return 0L
        return (android.os.SystemClock.elapsedRealtime() - dictationStartedAtMs).coerceAtLeast(0L)
    }

    override fun onCleared() {
        tickerJob?.cancel()
        processingTimeoutJob?.cancel()
        dictationEngine.release()
        player.release()
        super.onCleared()
    }

    private fun VoiceComposerPhase.isActiveDictation(): Boolean =
        this == VoiceComposerPhase.Starting ||
            this == VoiceComposerPhase.Recording ||
            this == VoiceComposerPhase.Finishing

    private companion object {
        const val ElapsedUpdateIntervalMs = 100L
        const val MaximumDictationDurationMs = 60_000L
        const val ProcessingTimeoutMs = 15_000L
    }
}
