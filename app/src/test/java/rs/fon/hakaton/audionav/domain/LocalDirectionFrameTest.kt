package rs.fon.hakaton.audionav.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDirectionFrameTest {

    @Test
    fun `calibration raw heading becomes zero`() {
        val localHeading = LocalDirectionFrame.toLocalHeading(
            rawHeadingDegrees = 73,
            headingOffsetDegrees = 73,
        )

        assertEquals(0, localHeading)
    }

    @Test
    fun `local heading is measured relative to stored offset`() {
        val localHeading = LocalDirectionFrame.toLocalHeading(
            rawHeadingDegrees = 163,
            headingOffsetDegrees = 73,
        )

        assertEquals(90, localHeading)
    }

    @Test
    fun `normalize360 wraps around zero correctly`() {
        assertEquals(359, LocalDirectionFrame.normalize360(-1))
        assertEquals(1, LocalDirectionFrame.normalize360(361))
    }

    @Test
    fun `missing offset returns null local heading`() {
        assertNull(
            LocalDirectionFrame.toLocalHeading(
                rawHeadingDegrees = 90,
                headingOffsetDegrees = null,
            ),
        )
    }
}
