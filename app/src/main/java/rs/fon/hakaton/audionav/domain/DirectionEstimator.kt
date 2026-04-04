package rs.fon.hakaton.audionav.domain

object DirectionEstimator {

    fun estimate(
        beaconAzimuthDegrees: Int?,
        userHeadingDegrees: Int?,
        headingConfidence: DirectionConfidence,
    ): DirectionEstimate {
        if (beaconAzimuthDegrees == null || userHeadingDegrees == null || headingConfidence != DirectionConfidence.HIGH) {
            return DirectionEstimate(
                direction = DirectionLabel.UNKNOWN,
                relativeAngleDegrees = 0,
                confidence = DirectionConfidence.LOW,
            )
        }

        val relativeAngle = normalizeAngle(beaconAzimuthDegrees - userHeadingDegrees)
        val direction = when {
            relativeAngle in -20..20 -> DirectionLabel.BEHIND
            relativeAngle in 21..144 -> DirectionLabel.LEFT
            relativeAngle in -144..-21 -> DirectionLabel.RIGHT
            else -> DirectionLabel.AHEAD
        }

        return DirectionEstimate(
            direction = direction,
            relativeAngleDegrees = relativeAngle,
            confidence = headingConfidence,
        )
    }

    fun normalizeAngle(angleDegrees: Int): Int {
        var normalized = angleDegrees % 360
        if (normalized > 180) {
            normalized -= 360
        }
        if (normalized < -180) {
            normalized += 360
        }
        return normalized
    }
}
