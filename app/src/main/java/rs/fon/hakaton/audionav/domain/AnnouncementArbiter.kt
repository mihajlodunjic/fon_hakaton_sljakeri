package rs.fon.hakaton.audionav.domain

data class AnnouncementCandidate(
    val beaconId: String,
    val messageCode: Short,
    val pointType: PointType,
    val priority: Priority,
    val ttsText: String,
    val decodedText: String,
    val detectedAt: Long,
    val smoothedRssi: Int,
)

data class ActiveAnnouncement(
    val candidate: AnnouncementCandidate,
    val utteranceId: String,
)

sealed interface AnnouncementArbitrationResult {
    data class SpeakNow(
        val candidate: AnnouncementCandidate,
    ) : AnnouncementArbitrationResult

    data class Queued(
        val candidate: AnnouncementCandidate,
    ) : AnnouncementArbitrationResult

    data class ReplacedPending(
        val previousCandidate: AnnouncementCandidate,
        val replacementCandidate: AnnouncementCandidate,
    ) : AnnouncementArbitrationResult

    data class RefreshedPending(
        val candidate: AnnouncementCandidate,
    ) : AnnouncementArbitrationResult

    data class DroppedLowerRank(
        val candidate: AnnouncementCandidate,
    ) : AnnouncementArbitrationResult
}

sealed interface PendingAnnouncementResult {
    data class Ready(
        val candidate: AnnouncementCandidate,
    ) : PendingAnnouncementResult

    data class DroppedStale(
        val candidate: AnnouncementCandidate,
    ) : PendingAnnouncementResult

    data object None : PendingAnnouncementResult
}

class AnnouncementArbiter(
    private val samePriorityReplacementDeltaDbm: Int = SAME_PRIORITY_REPLACEMENT_DELTA_DBM,
    private val pendingFreshnessWindowMs: Long = PENDING_FRESHNESS_WINDOW_MS,
) {

    private var activeAnnouncement: ActiveAnnouncement? = null
    private var pendingCandidate: AnnouncementCandidate? = null

    fun submitCandidate(
        candidate: AnnouncementCandidate,
        canSpeakImmediately: Boolean,
    ): AnnouncementArbitrationResult {
        if (canSpeakImmediately && activeAnnouncement == null && pendingCandidate == null) {
            return AnnouncementArbitrationResult.SpeakNow(candidate)
        }

        val currentPending = pendingCandidate
        if (currentPending == null) {
            pendingCandidate = candidate
            return AnnouncementArbitrationResult.Queued(candidate)
        }

        if (isSameCandidate(currentPending, candidate)) {
            val refreshedCandidate = refreshPendingCandidate(
                currentPending = currentPending,
                newCandidate = candidate,
            )
            pendingCandidate = refreshedCandidate
            return AnnouncementArbitrationResult.RefreshedPending(refreshedCandidate)
        }

        val replacementDecision = compareForPendingReplacement(
            newCandidate = candidate,
            existingPending = currentPending,
        )
        return if (replacementDecision) {
            pendingCandidate = candidate
            AnnouncementArbitrationResult.ReplacedPending(
                previousCandidate = currentPending,
                replacementCandidate = candidate,
            )
        } else {
            AnnouncementArbitrationResult.DroppedLowerRank(candidate)
        }
    }

    fun onAnnouncementQueued(
        candidate: AnnouncementCandidate,
        utteranceId: String,
    ) {
        activeAnnouncement = ActiveAnnouncement(
            candidate = candidate,
            utteranceId = utteranceId,
        )
    }

    fun onPlaybackFinished(utteranceId: String): ActiveAnnouncement? {
        val currentActive = activeAnnouncement ?: return null
        if (currentActive.utteranceId != utteranceId) {
            return null
        }

        activeAnnouncement = null
        return currentActive
    }

    fun takePendingCandidate(now: Long): PendingAnnouncementResult {
        val currentPending = pendingCandidate ?: return PendingAnnouncementResult.None
        pendingCandidate = null

        return if (now - currentPending.detectedAt > pendingFreshnessWindowMs) {
            PendingAnnouncementResult.DroppedStale(currentPending)
        } else {
            PendingAnnouncementResult.Ready(currentPending)
        }
    }

    fun currentActive(): ActiveAnnouncement? = activeAnnouncement

    fun currentPending(): AnnouncementCandidate? = pendingCandidate

    fun clear() {
        activeAnnouncement = null
        pendingCandidate = null
    }

    private fun compareForPendingReplacement(
        newCandidate: AnnouncementCandidate,
        existingPending: AnnouncementCandidate,
    ): Boolean {
        val priorityComparison = newCandidate.priority.code.compareTo(existingPending.priority.code)
        if (priorityComparison != 0) {
            return priorityComparison > 0
        }

        return newCandidate.smoothedRssi >= existingPending.smoothedRssi + samePriorityReplacementDeltaDbm
    }

    private fun refreshPendingCandidate(
        currentPending: AnnouncementCandidate,
        newCandidate: AnnouncementCandidate,
    ): AnnouncementCandidate {
        return currentPending.copy(
            detectedAt = maxOf(currentPending.detectedAt, newCandidate.detectedAt),
            smoothedRssi = maxOf(currentPending.smoothedRssi, newCandidate.smoothedRssi),
            decodedText = newCandidate.decodedText,
            ttsText = newCandidate.ttsText,
        )
    }

    private fun isSameCandidate(
        left: AnnouncementCandidate,
        right: AnnouncementCandidate,
    ): Boolean {
        return left.beaconId == right.beaconId &&
            left.messageCode == right.messageCode
    }

    companion object {
        const val SAME_PRIORITY_REPLACEMENT_DELTA_DBM: Int = 5
        const val PENDING_FRESHNESS_WINDOW_MS: Long = 4_000L
    }
}
