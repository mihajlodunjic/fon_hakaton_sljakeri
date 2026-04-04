package rs.fon.hakaton.audionav.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCatalogTest {

    @Test
    fun `resolve returns Serbian TTS text for crosswalk code 1`() {
        val definition = MessageCatalog.resolve(PointType.CROSSWALK, 1)

        assertEquals("Pesacki prelaz ispred vas.", definition?.ttsText)
    }

    @Test
    fun `resolve returns null for unsupported point type and message code pair`() {
        val definition = MessageCatalog.resolve(PointType.CROSSWALK, 2)

        assertNull(definition)
    }

    @Test
    fun `definitionsFor stairs contains only stair messages`() {
        val definitions = MessageCatalog.definitionsFor(PointType.STAIRS)

        assertEquals(1, definitions.size)
        assertTrue(definitions.all { it.pointType == PointType.STAIRS })
        assertEquals(2.toShort(), definitions.first().messageCode)
    }
}
