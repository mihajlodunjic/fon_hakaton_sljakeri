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

    fun buildPassedText(definition: MessageDefinition): String {
        return when (definition.directionPromptStyle) {
            DirectionPromptStyle.CROSSWALK -> "Pro\u0161li ste pe\u0161a\u010dki prelaz."
            DirectionPromptStyle.TRAFFIC_LIGHT -> "Pro\u0161li ste semafor."
            DirectionPromptStyle.DEFAULT -> definition.genericTtsText
        }
    }

    private fun buildPrompt(
        definition: MessageDefinition,
        direction: DirectionLabel,
    ): String {
        if (direction == DirectionLabel.UNKNOWN) {
            return definition.genericTtsText
        }

        if (direction == DirectionLabel.BEHIND &&
            definition.behindSpeechPolicy == BehindSpeechPolicy.PASS_CONFIRMED_MESSAGE
        ) {
            return definition.genericTtsText
        }

        return when (definition.directionPromptStyle) {
            DirectionPromptStyle.CROSSWALK -> buildDirectionalPrompt(
                subject = "Pe\u0161a\u010dki prelaz",
                isPlural = false,
                direction = direction,
            )

            DirectionPromptStyle.TRAFFIC_LIGHT -> buildDirectionalPrompt(
                subject = "Semafor",
                isPlural = false,
                direction = direction,
            )

            DirectionPromptStyle.DEFAULT -> buildDirectionalPrompt(
                subject = definition.subjectSingular,
                isPlural = definition.subjectPlural,
                direction = direction,
            )
        }
    }

    private fun buildDirectionalPrompt(
        subject: String,
        isPlural: Boolean,
        direction: DirectionLabel,
    ): String {
        val verb = if (isPlural) "su" else "je"
        val suffix = when (direction) {
            DirectionLabel.AHEAD -> "ispred vas."
            DirectionLabel.LEFT -> "sa va\u0161e leve strane."
            DirectionLabel.RIGHT -> "sa va\u0161e desne strane."
            DirectionLabel.BEHIND -> "iza vas."
            DirectionLabel.UNKNOWN -> "u blizini."
        }
        return "$subject $verb $suffix"
    }
}
