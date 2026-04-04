package rs.fon.hakaton.audionav.domain

interface HeadingSensorController {
    fun start()
    fun stop()
    fun latestEstimate(): HeadingEstimate?
}
