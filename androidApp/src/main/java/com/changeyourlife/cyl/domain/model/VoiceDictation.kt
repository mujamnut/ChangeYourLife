package com.changeyourlife.cyl.domain.model

enum class VoiceDictationLanguage(
    val storageValue: String,
    val languageTag: String?,
) {
    AUTO(storageValue = "auto", languageTag = null),
    MALAY(storageValue = "ms-MY", languageTag = "ms-MY"),
    INDONESIAN(storageValue = "id-ID", languageTag = "id-ID"),
    ENGLISH(storageValue = "en-US", languageTag = "en-US"),
    ;

    companion object {
        fun fromStorageValue(value: String?): VoiceDictationLanguage =
            entries.firstOrNull { language -> language.storageValue == value } ?: AUTO
    }
}

enum class VoiceDictationError(val wireValue: String) {
    ServiceUnavailable("dictation_unavailable"),
    LanguageUnavailable("dictation_language_unavailable"),
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
