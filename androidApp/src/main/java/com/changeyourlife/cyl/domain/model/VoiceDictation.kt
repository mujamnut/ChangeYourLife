package com.changeyourlife.cyl.domain.model

enum class VoiceDictationError(val wireValue: String) {
    ServiceUnavailable("dictation_unavailable"),
    RecognizerBusy("dictation_busy"),
    Network("dictation_network"),
    NoSpeech("dictation_no_speech"),
    PermissionDenied("microphone_permission_denied"),
    Audio("dictation_audio_error"),
    Unknown("dictation_failed"),
}

sealed interface VoiceDictationEvent {
    data object Ready : VoiceDictationEvent

    data class Level(val value: Int) : VoiceDictationEvent

    data class PartialResult(val text: String) : VoiceDictationEvent

    data object Processing : VoiceDictationEvent

    data class FinalResult(val text: String) : VoiceDictationEvent

    data class Failure(val error: VoiceDictationError) : VoiceDictationEvent
}
