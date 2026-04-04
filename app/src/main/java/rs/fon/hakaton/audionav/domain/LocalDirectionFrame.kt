package rs.fon.hakaton.audionav.domain

object LocalDirectionFrame {

    fun normalize360(angleDegrees: Int): Int {
        var normalized = angleDegrees % 360
        if (normalized < 0) {
            normalized += 360
        }
        return normalized
    }

    fun toLocalHeading(
        rawHeadingDegrees: Int?,
        headingOffsetDegrees: Int?,
    ): Int? {
        if (rawHeadingDegrees == null || headingOffsetDegrees == null) {
            return null
        }
        return normalize360(rawHeadingDegrees - headingOffsetDegrees)
    }
}
