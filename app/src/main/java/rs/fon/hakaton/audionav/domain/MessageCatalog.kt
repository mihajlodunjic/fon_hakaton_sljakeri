package rs.fon.hakaton.audionav.domain

object MessageCatalog {

    private val definitions = listOf(
        MessageDefinition(
            messageCode = 1,
            pointType = PointType.CROSSWALK,
            operatorLabel = "Pe\u0161a\u010dki prelaz",
            subjectSingular = "Pe\u0161a\u010dki prelaz",
            subjectPlural = false,
            genericTtsText = "Pe\u0161a\u010dki prelaz u blizini.",
            directionPromptStyle = DirectionPromptStyle.CROSSWALK,
            behindSpeechPolicy = BehindSpeechPolicy.PASS_CONFIRMED_MESSAGE,
        ),
        MessageDefinition(
            messageCode = 2,
            pointType = PointType.STAIRS,
            operatorLabel = "Stepenice",
            subjectSingular = "Stepenice",
            subjectPlural = true,
            genericTtsText = "Pa\u017enja, stepenice u blizini.",
            directionPromptStyle = DirectionPromptStyle.DEFAULT,
            behindSpeechPolicy = BehindSpeechPolicy.IMMEDIATE_DIRECTIONAL,
        ),
        MessageDefinition(
            messageCode = 3,
            pointType = PointType.POLE,
            operatorLabel = "Stub",
            subjectSingular = "Stub",
            subjectPlural = false,
            genericTtsText = "Pa\u017enja, stub u blizini.",
            directionPromptStyle = DirectionPromptStyle.DEFAULT,
            behindSpeechPolicy = BehindSpeechPolicy.IMMEDIATE_DIRECTIONAL,
        ),
        MessageDefinition(
            messageCode = 4,
            pointType = PointType.ENTRANCE,
            operatorLabel = "Ulaz",
            subjectSingular = "Ulaz",
            subjectPlural = false,
            genericTtsText = "Ulaz u blizini.",
            directionPromptStyle = DirectionPromptStyle.DEFAULT,
            behindSpeechPolicy = BehindSpeechPolicy.IMMEDIATE_DIRECTIONAL,
        ),
        MessageDefinition(
            messageCode = 5,
            pointType = PointType.BUS_STOP,
            operatorLabel = "Autobusko stajali\u0161te",
            subjectSingular = "Autobusko stajali\u0161te",
            subjectPlural = false,
            genericTtsText = "Autobusko stajali\u0161te u blizini.",
            directionPromptStyle = DirectionPromptStyle.DEFAULT,
            behindSpeechPolicy = BehindSpeechPolicy.IMMEDIATE_DIRECTIONAL,
        ),
        MessageDefinition(
            messageCode = 6,
            pointType = PointType.TRAFFIC_LIGHT,
            operatorLabel = "Semafor",
            subjectSingular = "Semafor",
            subjectPlural = false,
            genericTtsText = "Semafor u blizini.",
            directionPromptStyle = DirectionPromptStyle.TRAFFIC_LIGHT,
            behindSpeechPolicy = BehindSpeechPolicy.PASS_CONFIRMED_MESSAGE,
        ),
    )

    fun allDefinitions(): List<MessageDefinition> = definitions

    fun definitionsFor(pointType: PointType): List<MessageDefinition> =
        definitions.filter { it.pointType == pointType }

    fun resolve(pointType: PointType, messageCode: Short): MessageDefinition? =
        definitions.firstOrNull { it.pointType == pointType && it.messageCode == messageCode }

    fun supportedPointTypes(): List<PointType> =
        definitions.map { it.pointType }.distinct()
}
