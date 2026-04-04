package rs.fon.hakaton.audionav.domain

import kotlin.math.roundToInt

sealed interface RssiStabilizationResult {
    data class Tracking(
        val progress: Int,
        val threshold: Int,
        val lastRssi: Int,
        val smoothedRssi: Int? = null,
        val hasStableWindow: Boolean = false,
    ) : RssiStabilizationResult

    data class Stable(
        val payload: DecodedBeaconPayload,
        val rssi: Int,
        val smoothedRssi: Int,
        val detectedAt: Long,
    ) : RssiStabilizationResult

    data class Rejected(
        val reason: RssiRejectionReason,
        val lastRssi: Int,
    ) : RssiStabilizationResult
}

enum class RssiRejectionReason {
    BELOW_THRESHOLD,
    SIGNAL_GAP_RESET,
}

class RssiStabilizer(
    val rssiThresholdDbm: Int = DEFAULT_RSSI_THRESHOLD_DBM,
    val requiredConsecutiveReads: Int = DEFAULT_REQUIRED_CONSECUTIVE_READS,
    private val resetAfterSignalGapMs: Long = DEFAULT_RESET_AFTER_SIGNAL_GAP_MS,
) {

    private val trackingStates = mutableMapOf<String, BeaconTrackingState>()

    fun observe(
        payload: DecodedBeaconPayload,
        rssi: Int,
        detectedAt: Long,
    ): RssiStabilizationResult {
        val beaconId = payload.beaconId
        val previousState = trackingStates[beaconId]

        if (rssi < rssiThresholdDbm) {
            trackingStates.remove(beaconId)
            return RssiStabilizationResult.Rejected(
                reason = RssiRejectionReason.BELOW_THRESHOLD,
                lastRssi = rssi,
            )
        }

        if (previousState != null && detectedAt - previousState.lastSeenAt > resetAfterSignalGapMs) {
            trackingStates[beaconId] = BeaconTrackingState(
                consecutiveReads = 1,
                lastSeenAt = detectedAt,
                lastRssi = rssi,
                recentRssiReadings = listOf(rssi),
                hasTriggeredSinceReset = false,
            )
            return RssiStabilizationResult.Rejected(
                reason = RssiRejectionReason.SIGNAL_GAP_RESET,
                lastRssi = rssi,
            )
        }

        if (previousState?.hasTriggeredSinceReset == true) {
            val refreshedReadings = (previousState.recentRssiReadings + rssi)
                .takeLast(requiredConsecutiveReads)
            trackingStates[beaconId] = previousState.copy(
                lastSeenAt = detectedAt,
                lastRssi = rssi,
                recentRssiReadings = refreshedReadings,
            )
            return RssiStabilizationResult.Tracking(
                progress = requiredConsecutiveReads,
                threshold = rssiThresholdDbm,
                lastRssi = rssi,
                smoothedRssi = refreshedReadings.average().roundToInt(),
                hasStableWindow = true,
            )
        }

        val nextReads = (previousState?.consecutiveReads ?: 0) + 1
        val nextReadings = ((previousState?.recentRssiReadings ?: emptyList()) + rssi)
            .takeLast(requiredConsecutiveReads)
        val nextState = BeaconTrackingState(
            consecutiveReads = nextReads.coerceAtMost(requiredConsecutiveReads),
            lastSeenAt = detectedAt,
            lastRssi = rssi,
            recentRssiReadings = nextReadings,
            hasTriggeredSinceReset = nextReads >= requiredConsecutiveReads,
        )
        trackingStates[beaconId] = nextState

        return if (nextReads >= requiredConsecutiveReads) {
            RssiStabilizationResult.Stable(
                payload = payload,
                rssi = rssi,
                smoothedRssi = nextReadings.average().roundToInt(),
                detectedAt = detectedAt,
            )
        } else {
            RssiStabilizationResult.Tracking(
                progress = nextReads,
                threshold = rssiThresholdDbm,
                lastRssi = rssi,
            )
        }
    }

    fun reset() {
        trackingStates.clear()
    }

    companion object {
        const val DEFAULT_RSSI_THRESHOLD_DBM: Int = -75
        const val DEFAULT_REQUIRED_CONSECUTIVE_READS: Int = 3
        const val DEFAULT_RESET_AFTER_SIGNAL_GAP_MS: Long = 2_000L
    }

    private data class BeaconTrackingState(
        val consecutiveReads: Int,
        val lastSeenAt: Long,
        val lastRssi: Int,
        val recentRssiReadings: List<Int>,
        val hasTriggeredSinceReset: Boolean,
    )
}
