package rs.fon.hakaton.audionav.storage

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.fon.hakaton.audionav.domain.CooldownEntry
import rs.fon.hakaton.audionav.domain.DetectedBeaconEvent
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority

@OptIn(ExperimentalCoroutinesApi::class)
class CooldownRepositoryTest {

    @Test
    fun `new beacon is not in cooldown`() = runTest {
        val repository = CooldownRepository(FakeReceiverRuntimeStorage())
        repository.initialize()

        val result = repository.check(
            beaconId = "beacon-a",
            messageCode = 1,
            now = 1_000L,
        )

        assertFalse(result.isBlocked)
        assertEquals(0L, result.remainingMs)
    }

    @Test
    fun `beacon is blocked inside cooldown window`() = runTest {
        val repository = CooldownRepository(FakeReceiverRuntimeStorage())
        repository.initialize()
        repository.recordStableEvent(event(detectedAt = 1_000L, wasAnnounced = true))

        val result = repository.check(
            beaconId = "beacon-a",
            messageCode = 1,
            now = 5_000L,
        )

        assertTrue(result.isBlocked)
        assertTrue(result.remainingMs > 0L)
    }

    @Test
    fun `beacon exits cooldown after ten seconds`() = runTest {
        val repository = CooldownRepository(FakeReceiverRuntimeStorage())
        repository.initialize()
        repository.recordStableEvent(event(detectedAt = 1_000L, wasAnnounced = true))

        val result = repository.check(
            beaconId = "beacon-a",
            messageCode = 1,
            now = 11_001L,
        )

        assertFalse(result.isBlocked)
        assertEquals(0L, result.remainingMs)
    }

    @Test
    fun `initialize loads cooldown entries and recent events from storage`() = runTest {
        val storage = FakeReceiverRuntimeStorage(
            snapshot = ReceiverRuntimeSnapshot(
                cooldownEntries = listOf(
                    CooldownEntry(
                        beaconId = "beacon-a",
                        messageCode = 1,
                        lastTriggeredAt = 500L,
                    ),
                ),
                recentEvents = listOf(
                    event(detectedAt = 500L, wasAnnounced = true),
                ),
            ),
        )
        val repository = CooldownRepository(storage)

        val snapshot = repository.initialize()
        val result = repository.check(
            beaconId = "beacon-a",
            messageCode = 1,
            now = 1_000L,
        )

        assertEquals(1, snapshot.cooldownEntries.size)
        assertEquals(1, snapshot.recentEvents.size)
        assertTrue(result.isBlocked)
    }

    @Test
    fun `recent events are newest first and capped to twenty`() = runTest {
        val storage = FakeReceiverRuntimeStorage()
        val repository = CooldownRepository(storage)
        repository.initialize()

        repeat(25) { index ->
            repository.recordStableEvent(
                event(
                    beaconId = "beacon-$index",
                    detectedAt = index.toLong(),
                    wasAnnounced = index % 2 == 0,
                ),
            )
        }

        val recentEvents = repository.recentEvents()
        assertEquals(20, recentEvents.size)
        assertEquals("beacon-24", recentEvents.first().beaconId)
        assertEquals("beacon-5", recentEvents.last().beaconId)
    }

    private fun event(
        beaconId: String = "beacon-a",
        detectedAt: Long,
        wasAnnounced: Boolean,
    ): DetectedBeaconEvent {
        return DetectedBeaconEvent(
            beaconId = beaconId,
            detectedAt = detectedAt,
            rssi = -60,
            pointType = PointType.CROSSWALK,
            priority = Priority.MEDIUM,
            messageCode = 1,
            wasAnnounced = wasAnnounced,
        )
    }

    private class FakeReceiverRuntimeStorage(
        var snapshot: ReceiverRuntimeSnapshot = ReceiverRuntimeSnapshot(),
    ) : ReceiverRuntimeStorage {

        override suspend fun load(): ReceiverRuntimeSnapshot = snapshot

        override suspend fun save(snapshot: ReceiverRuntimeSnapshot) {
            this.snapshot = snapshot
        }
    }
}
