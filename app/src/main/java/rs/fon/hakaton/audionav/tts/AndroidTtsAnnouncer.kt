package rs.fon.hakaton.audionav.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import rs.fon.hakaton.audionav.AppLogger
import rs.fon.hakaton.audionav.LogTag

class AndroidTtsAnnouncer(
    context: Context,
) : TtsAnnouncer {

    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var statusListener: ((TtsStatus) -> Unit)? = null
    private var playbackListener: ((TtsPlaybackEvent) -> Unit)? = null
    private var currentStatus: TtsStatus = TtsStatus.INITIALIZING
    private var isInitializing: Boolean = false

    override fun initialize(
        onStatusChanged: (TtsStatus) -> Unit,
        onPlaybackEvent: (TtsPlaybackEvent) -> Unit,
    ) {
        statusListener = onStatusChanged
        playbackListener = onPlaybackEvent
        onStatusChanged(currentStatus)

        if (textToSpeech != null || isInitializing) {
            return
        }

        isInitializing = true
        updateStatus(TtsStatus.INITIALIZING)

        var createdEngine: TextToSpeech? = null
        createdEngine = TextToSpeech(appContext) { initStatus ->
            isInitializing = false
            val engine = createdEngine ?: textToSpeech
            if (initStatus != TextToSpeech.SUCCESS || engine == null) {
                AppLogger.e(LogTag.TTS, "TextToSpeech initialization failed: status=$initStatus")
                updateStatus(TtsStatus.ENGINE_ERROR)
                return@TextToSpeech
            }

            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        playbackListener?.invoke(TtsPlaybackEvent.Started(utteranceId))
                    }

                    override fun onDone(utteranceId: String) {
                        playbackListener?.invoke(TtsPlaybackEvent.Done(utteranceId))
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {
                        playbackListener?.invoke(
                            TtsPlaybackEvent.Error(
                                utteranceId = utteranceId,
                                message = "Doslo je do TTS greske tokom reprodukcije.",
                            ),
                        )
                    }

                    override fun onError(
                        utteranceId: String,
                        errorCode: Int,
                    ) {
                        playbackListener?.invoke(
                            TtsPlaybackEvent.Error(
                                utteranceId = utteranceId,
                                message = "Doslo je do TTS greske tokom reprodukcije (kod=$errorCode).",
                            ),
                        )
                    }

                    override fun onStop(
                        utteranceId: String,
                        interrupted: Boolean,
                    ) {
                        playbackListener?.invoke(TtsPlaybackEvent.Stopped(utteranceId))
                    }
                },
            )
            configureLanguage(engine)
        }
        textToSpeech = createdEngine
    }

    override fun announce(
        text: String,
        utteranceId: String,
    ): TtsSpeakResult {
        val engine = textToSpeech
            ?: run {
                AppLogger.w(LogTag.TTS, "TTS skipped: engine nije inicijalizovan")
                return TtsSpeakResult.SkippedNotReady("TTS engine nije inicijalizovan.")
            }

        if (currentStatus != TtsStatus.READY_SR && currentStatus != TtsStatus.READY_FALLBACK_LOCALE) {
            val message = when (currentStatus) {
                TtsStatus.INITIALIZING -> "TTS jos nije spreman."
                TtsStatus.LANGUAGE_UNAVAILABLE -> "TTS jezik nije dostupan."
                TtsStatus.ENGINE_ERROR -> "TTS engine nije dostupan."
                else -> "TTS nije spreman za glasovnu najavu."
            }
            AppLogger.w(LogTag.TTS, "TTS skipped: $message")
            return TtsSpeakResult.SkippedNotReady(
                message,
            )
        }

        val speakResult = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )

        return if (speakResult == TextToSpeech.SUCCESS) {
            AppLogger.d(LogTag.TTS, "TTS queued successfully")
            TtsSpeakResult.Queued
        } else {
            AppLogger.e(LogTag.TTS, "TextToSpeech speak failed with code=$speakResult")
            TtsSpeakResult.Failed("TTS nije uspeo da zakaze glasovnu najavu.")
        }
    }

    override fun stop() {
        runCatching {
            textToSpeech?.stop()
        }.onFailure { throwable ->
            AppLogger.e(LogTag.TTS, "TextToSpeech stop failed: ${throwable.message}")
        }
    }

    override fun shutdown() {
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }.onFailure { throwable ->
            AppLogger.e(LogTag.TTS, "TextToSpeech shutdown failed: ${throwable.message}")
        }
        textToSpeech = null
        isInitializing = false
    }

    private fun configureLanguage(engine: TextToSpeech) {
        val locales = listOf(
            Locale("sr", "RS") to TtsStatus.READY_SR,
            Locale("sr") to TtsStatus.READY_SR,
            Locale.getDefault() to TtsStatus.READY_FALLBACK_LOCALE,
        )

        for ((locale, status) in locales) {
            val availability = engine.isLanguageAvailable(locale)
            if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
                continue
            }

            val setLanguageResult = engine.setLanguage(locale)
            if (setLanguageResult == TextToSpeech.LANG_MISSING_DATA ||
                setLanguageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                continue
            }

            AppLogger.d(LogTag.TTS, "TextToSpeech language configured: $locale")
            updateStatus(status)
            return
        }

        AppLogger.w(LogTag.TTS, "No supported TTS locale found for AudioNav")
        updateStatus(TtsStatus.LANGUAGE_UNAVAILABLE)
    }

    private fun updateStatus(status: TtsStatus) {
        currentStatus = status
        AppLogger.d(LogTag.TTS, "TTS status changed to $status")
        statusListener?.invoke(status)
    }
}
