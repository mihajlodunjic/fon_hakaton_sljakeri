package rs.fon.hakaton.audionav.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementArbiterTest {

    @Test
    fun `higher priority candidate replaces lower pending candidate`() {
        val arbiter = AnnouncementArbiter()
        val lowCandidate = candidate(
            beaconId = "beacon-low",
            priority = Priority.MEDIUM,
            smoothedRssi = -60,
        )
        val highCandidate = candidate(
            beaconId = "beacon-high",
            priority = Priority.HIGH,
            smoothedRssi = -70,
        )

        val firstDecision = arbiter.submitCandidate(lowCandidate, canSpeakImmediately = false)
        val secondDecision = arbiter.submitCandidate(highCandidate, canSpeakImmediately = false)

        assertTrue(firstDecision is AnnouncementArbitrationResult.Queued)
        assertTrue(secondDecision is AnnouncementArbitrationResult.ReplacedPending)
        assertEquals("beacon-high", arbiter.currentPending()?.beaconId)
    }

    @Test
    fun `same priority candidate needs at least five dbm to replace pending`() {
        val arbiter = AnnouncementArbiter()
        val currentPending = candidate(
            beaconId = "beacon-a",
            priority = Priority.HIGH,
            smoothedRssi = -65,
        )
        val notStrongEnough = candidate(
            beaconId = "beacon-b",
            priority = Priority.HIGH,
            smoothedRssi = -61,
        )
        val strongEnough = candidate(
            beaconId = "beacon-c",
            priority = Priority.HIGH,
            smoothedRssi = -60,
        )

        arbiter.submitCandidate(currentPending, canSpeakImmediately = false)

        val weakerDecision = arbiter.submitCandidate(notStrongEnough, canSpeakImmediately = false)
        val strongerDecision = arbiter.submitCandidate(strongEnough, canSpeakImmediately = false)

        assertTrue(weakerDecision is AnnouncementArbitrationResult.DroppedLowerRank)
        assertTrue(strongerDecision is AnnouncementArbitrationResult.ReplacedPending)
        assertEquals("beacon-c", arbiter.currentPending()?.beaconId)
    }

    @Test
    fun `pending candidate older than four seconds is dropped as stale`() {
        val arbiter = AnnouncementArbiter()
        val pending = candidate(
            beaconId = "beacon-stale",
            detectedAt = 1_000L,
        )
        arbiter.submitCandidate(pending, canSpeakImmediately = false)

        val result = arbiter.takePendingCandidate(now = 5_100L)

        assertTrue(result is PendingAnnouncementResult.DroppedStale)
        assertEquals(null, arbiter.currentPending())
    }

    private fun candidate(
        beaconId: String,
        detectedAt: Long = 1_000L,
        priority: Priority = Priority.MEDIUM,
        smoothedRssi: Int = -60,
    ): AnnouncementCandidate {
        return AnnouncementCandidate(
            beaconId = beaconId,
            messageCode = 1,
            pointType = PointType.CROSSWALK,
            priority = priority,
            protocolVersion = 2,
            azimuthDegrees = 90,
            messageDefinition = MessageCatalog.resolve(PointType.CROSSWALK, 1)!!,
            detectedAt = detectedAt,
            smoothedRssi = smoothedRssi,
        )
    }
}
