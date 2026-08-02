package com.changeyourlife.cyl.presentation.ai

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.domain.model.ChatAttachment
import com.changeyourlife.cyl.domain.model.ChatAudioPlaybackState
import com.changeyourlife.cyl.domain.model.VoiceRecorderError
import com.changeyourlife.cyl.domain.model.VoiceRecorderResult
import com.changeyourlife.cyl.domain.model.VoiceNoteLimits
import com.changeyourlife.cyl.domain.repository.ChatAudioPlayer
import com.changeyourlife.cyl.domain.repository.VoiceRecorder
import com.changeyourlife.cyl.domain.usecase.chat.DeleteVoiceNoteUseCase
import com.changeyourlife.cyl.domain.usecase.chat.StageVoiceNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val draft: ChatAttachment? = null,
    val elapsedMs: Long = 0L,
    val amplitude: Int = 0,
    val waveform: List<Int> = emptyList(),
    val errorCode: String? = null,
    val permissionPermanentlyDenied: Boolean = false,
)

@HiltViewModel
class VoiceNoteController @Inject constructor(
    private val recorder: VoiceRecorder,
    private val player: ChatAudioPlayer,
    private val stageVoiceNote: StageVoiceNoteUseCase,
    private val deleteVoiceNote: DeleteVoiceNoteUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VoiceNoteUiState())
    val state: StateFlow<VoiceNoteUiState> = mutableState.asStateFlow()
    val playbackState: StateFlow<ChatAudioPlaybackState> = player.state

    private var tickerJob: Job? = null
    private var recordingStartedAtMs: Long = 0L
    private var recordingSessionId: String = ""

    fun markPermissionRequesting() {
        if (mutableState.value.phase == VoiceComposerPhase.Recording) return
        mutableState.update {
            it.copy(
                phase = VoiceComposerPhase.RequestingPermission,
                errorCode = null,
                permissionPermanentlyDenied = false,
            )
        }
    }

    fun onPermissionDenied(permanentlyDenied: Boolean) {
        mutableState.update {
            it.copy(
                phase = VoiceComposerPhase.PermissionDenied,
                errorCode = ChatAttachmentErrorCode.MicrophonePermissionDenied.wireValue,
                permissionPermanentlyDenied = permanentlyDenied,
            )
        }
    }

    fun startRecording(sessionId: String) {
        val currentState = mutableState.value
        val phase = currentState.phase
        if (phase == VoiceComposerPhase.Starting ||
            phase == VoiceComposerPhase.Recording ||
            phase == VoiceComposerPhase.Finishing
        ) {
            return
        }
        val previousDraft = currentState.draft
        recordingSessionId = sessionId
        mutableState.value = VoiceNoteUiState(phase = VoiceComposerPhase.Starting)
        viewModelScope.launch {
            previousDraft?.let { draft ->
                if (player.state.value.attachmentId == draft.id) player.stop()
                deleteVoiceNote(draft)
            }
            player.pause()
            when (val result = recorder.start()) {
                is VoiceRecorderResult.Success -> {
                    recordingStartedAtMs = android.os.SystemClock.elapsedRealtime()
                    mutableState.value = VoiceNoteUiState(phase = VoiceComposerPhase.Recording)
                    startTicker()
                }
                is VoiceRecorderResult.Failure -> {
                    recordingSessionId = ""
                    setFailure(result.error)
                }
            }
        }
    }

    fun stopRecording() {
        if (mutableState.value.phase != VoiceComposerPhase.Recording) return
        tickerJob?.cancel()
        tickerJob = null
        val waveform = mutableState.value.waveform
        mutableState.update { it.copy(phase = VoiceComposerPhase.Finishing, amplitude = 0) }
        viewModelScope.launch {
            when (val result = recorder.stop()) {
                is VoiceRecorderResult.Success -> {
                    val attachment = stageVoiceNote(
                        sessionId = recordingSessionId,
                        recording = result.value,
                        waveform = waveform.ifEmpty { listOf(12, 18, 14, 20, 12) },
                    )
                    mutableState.value = VoiceNoteUiState(
                        phase = VoiceComposerPhase.Recorded,
                        draft = attachment,
                        elapsedMs = attachment.durationMs ?: 0L,
                        waveform = attachment.waveform,
                    )
                }
                is VoiceRecorderResult.Failure -> setFailure(result.error)
            }
            recordingSessionId = ""
            recordingStartedAtMs = 0L
        }
    }

    fun cancelRecording() {
        tickerJob?.cancel()
        tickerJob = null
        viewModelScope.launch {
            recorder.cancel()
            recordingSessionId = ""
            recordingStartedAtMs = 0L
            mutableState.value = VoiceNoteUiState()
        }
    }

    fun deleteDraft() {
        viewModelScope.launch {
            discardDraftOnly()
            mutableState.value = VoiceNoteUiState()
        }
    }

    fun markDraftSent() {
        player.pause()
        mutableState.value = VoiceNoteUiState()
    }

    fun togglePlayback(attachment: ChatAttachment) {
        player.toggle(attachment)
    }

    fun discardComposer() {
        tickerJob?.cancel()
        tickerJob = null
        viewModelScope.launch {
            recorder.cancel()
            discardDraftOnly()
            player.pause()
            recordingSessionId = ""
            recordingStartedAtMs = 0L
            mutableState.value = VoiceNoteUiState()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive && mutableState.value.phase == VoiceComposerPhase.Recording) {
                val elapsed = (
                    android.os.SystemClock.elapsedRealtime() - recordingStartedAtMs
                    ).coerceAtLeast(0L)
                val level = recorder.currentAmplitude().toWaveformLevel()
                mutableState.update { current ->
                    current.copy(
                        elapsedMs = elapsed,
                        amplitude = level,
                        waveform = (current.waveform + level).takeLast(MaxWaveformSamples),
                    )
                }
                if (elapsed >= VoiceNoteLimits.MaximumDurationMs) {
                    stopRecording()
                    break
                }
                delay(AmplitudeSampleIntervalMs)
            }
        }
    }

    private suspend fun discardDraftOnly() {
        mutableState.value.draft?.let { draft ->
            if (player.state.value.attachmentId == draft.id) player.stop()
            deleteVoiceNote(draft)
        }
    }

    private fun setFailure(error: VoiceRecorderError) {
        val code = when (error) {
            VoiceRecorderError.RecordingTooShort -> ChatAttachmentErrorCode.RecordingTooShort
            VoiceRecorderError.LimitExceeded -> ChatAttachmentErrorCode.AudioLimitExceeded
            VoiceRecorderError.AlreadyRecording,
            VoiceRecorderError.StartFailed,
            VoiceRecorderError.RecordingFailed,
            -> ChatAttachmentErrorCode.RecordingFailed
        }
        mutableState.value = VoiceNoteUiState(
            phase = VoiceComposerPhase.Failed,
            errorCode = code.wireValue,
        )
    }

    override fun onCleared() {
        tickerJob?.cancel()
        recorder.release()
        player.release()
        super.onCleared()
    }

    private fun Int.toWaveformLevel(): Int {
        if (this <= 0) return MinimumWaveformLevel
        val normalized = sqrt(coerceAtMost(32_767).toDouble() / 32_767.0)
        return (normalized * 100.0).toInt().coerceIn(MinimumWaveformLevel, 100)
    }

    private companion object {
        const val AmplitudeSampleIntervalMs = 80L
        const val MaxWaveformSamples = 48
        const val MinimumWaveformLevel = 6
    }
}
