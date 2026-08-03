package com.changeyourlife.cyl.data.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.changeyourlife.cyl.domain.model.VoiceDictationError
import com.changeyourlife.cyl.domain.model.VoiceDictationEvent
import com.changeyourlife.cyl.domain.repository.VoiceDictationEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidVoiceDictationEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : VoiceDictationEngine {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableEvents = MutableSharedFlow<VoiceDictationEvent>(
        extraBufferCapacity = EventBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<VoiceDictationEvent> = mutableEvents.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    private var generation = 0L
    private var listening = false
    private var stopRequested = false

    override fun start(languageTag: String) {
        runOnMain {
            releaseRecognizer()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                emit(VoiceDictationEvent.Failure(VoiceDictationError.ServiceUnavailable))
                return@runOnMain
            }

            val currentGeneration = ++generation
            val nextRecognizer = runCatching {
                SpeechRecognizer.createSpeechRecognizer(context)
            }.getOrElse {
                emit(VoiceDictationEvent.Failure(VoiceDictationError.ServiceUnavailable))
                return@runOnMain
            }
            recognizer = nextRecognizer
            listening = true
            stopRequested = false
            nextRecognizer.setRecognitionListener(listenerFor(currentGeneration))
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                languageTag.takeIf(String::isNotBlank)?.let { language ->
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                }
            }
            runCatching { nextRecognizer.startListening(intent) }
                .onFailure {
                    finish(currentGeneration)
                    emit(VoiceDictationEvent.Failure(VoiceDictationError.ServiceUnavailable))
                }
        }
    }

    override fun stop() {
        runOnMain {
            if (!listening) return@runOnMain
            stopRequested = true
            emit(VoiceDictationEvent.Processing)
            runCatching { recognizer?.stopListening() }
                .onFailure {
                    val currentGeneration = generation
                    finish(currentGeneration)
                    emit(VoiceDictationEvent.Failure(VoiceDictationError.Unknown))
                }
        }
    }

    override fun cancel() {
        runOnMain {
            generation += 1L
            listening = false
            stopRequested = false
            releaseRecognizer()
        }
    }

    override fun release() {
        runOnMain {
            generation += 1L
            listening = false
            stopRequested = false
            releaseRecognizer()
        }
    }

    private fun listenerFor(listenerGeneration: Long): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                ifActive(listenerGeneration) { emit(VoiceDictationEvent.Ready) }
            }

            override fun onBeginningOfSpeech() {
                ifActive(listenerGeneration) { emit(VoiceDictationEvent.Ready) }
            }

            override fun onRmsChanged(rmsdB: Float) {
                ifActive(listenerGeneration) {
                    emit(VoiceDictationEvent.Level(rmsdB.toLevel()))
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                ifActive(listenerGeneration) { emit(VoiceDictationEvent.Processing) }
            }

            override fun onError(error: Int) {
                if (!isActive(listenerGeneration)) return
                val mappedError = if (error == SpeechRecognizer.ERROR_CLIENT && stopRequested) {
                    VoiceDictationError.NoSpeech
                } else {
                    error.toDictationError()
                }
                finish(listenerGeneration)
                stopRequested = false
                emit(VoiceDictationEvent.Failure(mappedError))
            }

            override fun onResults(results: Bundle?) {
                if (!isActive(listenerGeneration)) return
                val text = results.bestTranscript()
                finish(listenerGeneration)
                stopRequested = false
                if (text.isBlank()) {
                    emit(VoiceDictationEvent.Failure(VoiceDictationError.NoSpeech))
                } else {
                    emit(VoiceDictationEvent.FinalResult(text))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                ifActive(listenerGeneration) {
                    partialResults.bestTranscript()
                        .takeIf(String::isNotBlank)
                        ?.let { text -> emit(VoiceDictationEvent.PartialResult(text)) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

    private fun ifActive(expectedGeneration: Long, action: () -> Unit) {
        if (isActive(expectedGeneration)) action()
    }

    private fun isActive(expectedGeneration: Long): Boolean =
        listening && generation == expectedGeneration

    private fun finish(expectedGeneration: Long) {
        if (generation != expectedGeneration) return
        listening = false
    }

    private fun releaseRecognizer() {
        recognizer?.let { active ->
            runCatching { active.cancel() }
            runCatching { active.destroy() }
        }
        recognizer = null
    }

    private fun emit(event: VoiceDictationEvent) {
        mutableEvents.tryEmit(event)
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun Bundle?.bestTranscript(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

    private fun Float.toLevel(): Int =
        ((coerceIn(0f, MaximumRmsDb) / MaximumRmsDb) * 100f)
            .toInt()
            .coerceIn(MinimumLevel, 100)

    private fun Int.toDictationError(): VoiceDictationError = when (this) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceDictationError.PermissionDenied
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceDictationError.RecognizerBusy
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> VoiceDictationError.Network
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> VoiceDictationError.NoSpeech
        SpeechRecognizer.ERROR_AUDIO -> VoiceDictationError.Audio
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> VoiceDictationError.LanguageUnavailable
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        -> VoiceDictationError.ServiceUnavailable
        else -> VoiceDictationError.Unknown
    }

    private companion object {
        const val EventBufferCapacity = 32
        const val MaximumRmsDb = 12f
        const val MinimumLevel = 6
    }
}
