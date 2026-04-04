package rs.fon.hakaton.audionav.domain

sealed interface BehindPassResult {
    data class Tracking(
        val statusText: String,
        val sampleCount: Int,
    ) : BehindPassResult

    data class Passed(
        val statusText: String,
        val sampleCount: Int,
    ) : BehindPassResult
}

class BehindPassTracker(
    private val maxSamples: Int = DEFAULT_MAX_SAMPLES,
    private val sampleIntervalMs: Long = DEFAULT_SAMPLE_INTERVAL_MS,
    private val resetAfterSignalGapMs: Long = DEFAULT_RESET_AFTER_SIGNAL_GAP_MS,
    private val minimumSamplesForDecision: Int = DEFAULT_MINIMUM_SAMPLES_FOR_DECISION,
    private val minimumPeakDropDbm: Int = DEFAULT_MINIMUM_PEAK_DROP_DBM,
) {

    private val states = mutableMapOf<Key, TrackingState>()

    fun observe(
        beaconId: String,
        messageCode: Short,
        smoothedRssi: Int,
        detectedAt: Long,
    ): BehindPassResult {
        val key = Key(beaconId, messageCode)
        val previousState = states[key]

        val baseState = when {
            previousState == null -> TrackingState()
            detectedAt - previousState.lastObservedAt > resetAfterSignalGapMs -> TrackingState()
            else -> previousState
        }

        if (baseState.locked) {
            states[key] = baseState.copy(lastObservedAt = detectedAt)
            return BehindPassResult.Tracking(
                statusText = "Prolazak je ve\u0107 potvr\u0111en za ovaj beacon.",
                sampleCount = baseState.samples.size,
            )
        }

        val shouldAppendSample = baseState.samples.isEmpty() ||
            detectedAt - baseState.lastSampleAt >= sampleIntervalMs
        val samples = if (shouldAppendSample) {
            (baseState.samples + Sample(smoothedRssi, detectedAt)).takeLast(maxSamples)
        } else {
            baseState.samples
        }

        val nextState = baseState.copy(
            samples = samples,
            lastObservedAt = detectedAt,
            lastSampleAt = if (shouldAppendSample) detectedAt else baseState.lastSampleAt,
        )
        states[key] = nextState

        if (samples.size < minimumSamplesForDecision) {
            return BehindPassResult.Tracking(
                statusText = "Objekat je iza vas, \u010dekam potvrdu prolaska.",
                sampleCount = samples.size,
            )
        }

        val latestIndex = samples.lastIndex
        val peakIndex = samples.indices.maxBy { samples[it].rssi }
        val peakRssi = samples[peakIndex].rssi
        val latestRssi = samples.last().rssi
        val hasConfirmedDescendingTail =
            samples[latestIndex].rssi < samples[latestIndex - 1].rssi &&
                samples[latestIndex - 1].rssi < samples[latestIndex - 2].rssi
        val hasPeakBeforeLatest = peakIndex < latestIndex
        val hasRequiredDrop = peakRssi - latestRssi >= minimumPeakDropDbm

        return if (hasConfirmedDescendingTail && hasPeakBeforeLatest && hasRequiredDrop) {
            states[key] = nextState.copy(locked = true)
            BehindPassResult.Passed(
                statusText = "Prolazak potvr\u0111en.",
                sampleCount = samples.size,
            )
        } else {
            val statusText = if (!hasPeakBeforeLatest || peakIndex == latestIndex) {
                "Signal jo\u0161 raste."
            } else {
                "\u010cekam potvr\u0111en pad signala."
            }
            BehindPassResult.Tracking(
                statusText = statusText,
                sampleCount = samples.size,
            )
        }
    }

    fun clear(beaconId: String, messageCode: Short) {
        states.remove(Key(beaconId, messageCode))
    }

    fun reset() {
        states.clear()
    }

    companion object {
        const val DEFAULT_MAX_SAMPLES: Int = 6
        const val DEFAULT_SAMPLE_INTERVAL_MS: Long = 500L
        const val DEFAULT_RESET_AFTER_SIGNAL_GAP_MS: Long = 2_000L
        const val DEFAULT_MINIMUM_SAMPLES_FOR_DECISION: Int = 4
        const val DEFAULT_MINIMUM_PEAK_DROP_DBM: Int = 6
    }

    private data class Key(
        val beaconId: String,
        val messageCode: Short,
    )

    private data class Sample(
        val rssi: Int,
        val detectedAt: Long,
    )

    private data class TrackingState(
        val samples: List<Sample> = emptyList(),
        val lastObservedAt: Long = 0L,
        val lastSampleAt: Long = Long.MIN_VALUE,
        val locked: Boolean = false,
    )
}
