package rs.fon.hakaton.audionav.domain

object DirectionPromptBuilder {

    fun buildUiText(
        definition: MessageDefinition,
        directionEstimate: DirectionEstimate,
    ): String = buildPrompt(definition, directionEstimate.direction)

    fun buildTtsText(
        definition: MessageDefinition,
        directionEstimate: DirectionEstimate,
    ): String = buildPrompt(definition, directionEstimate.direction)

    private fun buildPrompt(
        definition: MessageDefinition,
        direction: DirectionLabel,
    ): String {
        if (direction == DirectionLabel.UNKNOWN) {
            return definition.genericTtsText
        }

        val verb = if (definition.subjectPlural) "su" else "je"
        val suffix = when (direction) {
            DirectionLabel.AHEAD -> "ispred vas."
            DirectionLabel.LEFT -> "sa va\u0161e leve strane."
            DirectionLabel.RIGHT -> "sa va\u0161e desne strane."
            DirectionLabel.BEHIND -> "iza vas."
            DirectionLabel.UNKNOWN -> definition.genericTtsText
        }

        return "${definition.subjectSingular} $verb $suffix"
    }
}
