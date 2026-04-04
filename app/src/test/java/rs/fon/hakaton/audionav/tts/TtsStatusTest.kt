package rs.fon.hakaton.audionav.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsStatusTest {

    @Test
    fun `tts statuses map to expected UI text`() {
        assertEquals("Spreman", TtsStatus.READY_SR.toDisplayText())
        assertEquals("Fallback jezik", TtsStatus.READY_FALLBACK_LOCALE.toDisplayText())
        assertEquals("Srpski nije dostupan", TtsStatus.LANGUAGE_UNAVAILABLE.toDisplayText())
        assertEquals("Greska engine-a", TtsStatus.ENGINE_ERROR.toDisplayText())
        assertEquals("Inicijalizacija", TtsStatus.INITIALIZING.toDisplayText())
    }
}
