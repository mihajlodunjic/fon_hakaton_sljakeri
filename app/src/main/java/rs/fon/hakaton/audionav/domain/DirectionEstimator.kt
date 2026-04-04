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
            relativeAngle in -20..20 -> DirectionLabel.AHEAD
            relativeAngle in 21..160 -> DirectionLabel.RIGHT
            relativeAngle in -160..-21 -> DirectionLabel.LEFT
            else -> DirectionLabel.BEHIND
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
