package rs.fon.hakaton.audionav.tts

interface TtsAnnouncer {
    fun initialize(
        onStatusChanged: (TtsStatus) -> Unit,
        onPlaybackEvent: (TtsPlaybackEvent) -> Unit,
    )

    fun announce(
        text: String,
        utteranceId: String,
    ): TtsSpeakResult

    fun shutdown()
}

enum class TtsStatus {
    INITIALIZING,
    READY_SR,
    READY_FALLBACK_LOCALE,
    LANGUAGE_UNAVAILABLE,
    ENGINE_ERROR,
}

sealed interface TtsSpeakResult {
    data object Queued : TtsSpeakResult

    data class SkippedNotReady(
        val message: String,
    ) : TtsSpeakResult

    data class Failed(
        val message: String,
    ) : TtsSpeakResult
}

sealed interface TtsPlaybackEvent {
    data class Started(
        val utteranceId: String,
    ) : TtsPlaybackEvent

    data class Done(
        val utteranceId: String,
    ) : TtsPlaybackEvent

    data class Error(
        val utteranceId: String,
        val message: String,
    ) : TtsPlaybackEvent

    data class Stopped(
        val utteranceId: String,
    ) : TtsPlaybackEvent
}

fun TtsStatus.toDisplayText(): String {
    return when (this) {
        TtsStatus.INITIALIZING -> "Inicijalizacija"
        TtsStatus.READY_SR -> "Spreman"
        TtsStatus.READY_FALLBACK_LOCALE -> "Fallback jezik"
        TtsStatus.LANGUAGE_UNAVAILABLE -> "Srpski nije dostupan"
        TtsStatus.ENGINE_ERROR -> "Greska engine-a"
    }
}
