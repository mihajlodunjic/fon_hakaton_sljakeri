package rs.fon.hakaton.audionav.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BehindPassTrackerTest {

    @Test
    fun `rising trend keeps tracker in tracking state`() {
        val tracker = BehindPassTracker()

        val results = listOf(
            tracker.observe("beacon", 1, -64, 0L),
            tracker.observe("beacon", 1, -62, 500L),
            tracker.observe("beacon", 1, -60, 1_000L),
            tracker.observe("beacon", 1, -58, 1_500L),
        )

        assertTrue(results.all { it is BehindPassResult.Tracking })
        assertEquals(4, (results.last() as BehindPassResult.Tracking).sampleCount)
    }

    @Test
    fun `noisy trend without clear decline does not confirm pass`() {
        val tracker = BehindPassTracker()

        val results = listOf(
            tracker.observe("beacon", 1, -64, 0L),
            tracker.observe("beacon", 1, -62, 500L),
            tracker.observe("beacon", 1, -60, 1_000L),
            tracker.observe("beacon", 1, -61, 1_500L),
            tracker.observe("beacon", 1, -60, 2_000L),
        )

        assertTrue(results.last() is BehindPassResult.Tracking)
    }

    @Test
    fun `confirmed peak and decline marks object as passed`() {
        val tracker = BehindPassTracker()

        tracker.observe("beacon", 1, -64, 0L)
        tracker.observe("beacon", 1, -62, 500L)
        tracker.observe("beacon", 1, -60, 1_000L)
        tracker.observe("beacon", 1, -58, 1_500L)
        tracker.observe("beacon", 1, -61, 2_000L)
        val result = tracker.observe("beacon", 1, -66, 2_500L)

        assertTrue(result is BehindPassResult.Passed)
        assertEquals(6, (result as BehindPassResult.Passed).sampleCount)
    }

    @Test
    fun `tracker resets after signal gap`() {
        val tracker = BehindPassTracker()

        tracker.observe("beacon", 1, -64, 0L)
        tracker.observe("beacon", 1, -62, 500L)
        val result = tracker.observe("beacon", 1, -60, 3_000L)

        assertTrue(result is BehindPassResult.Tracking)
        assertEquals(1, (result as BehindPassResult.Tracking).sampleCount)
    }

    @Test
    fun `clear removes previous tracking state`() {
        val tracker = BehindPassTracker()

        tracker.observe("beacon", 1, -64, 0L)
        tracker.observe("beacon", 1, -62, 500L)
        tracker.clear("beacon", 1)

        val result = tracker.observe("beacon", 1, -60, 1_000L)

        assertTrue(result is BehindPassResult.Tracking)
        assertEquals(1, (result as BehindPassResult.Tracking).sampleCount)
    }
}
