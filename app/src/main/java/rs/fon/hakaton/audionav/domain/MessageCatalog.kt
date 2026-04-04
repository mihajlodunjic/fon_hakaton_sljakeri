package rs.fon.hakaton.audionav.domain

object MessageCatalog {

    private val definitions = listOf(
        MessageDefinition(
            messageCode = 1,
            pointType = PointType.CROSSWALK,
            operatorLabel = "Pesacki prelaz",
            ttsText = "Pesacki prelaz ispred vas.",
        ),
        MessageDefinition(
            messageCode = 2,
            pointType = PointType.STAIRS,
            operatorLabel = "Stepenice",
            ttsText = "Paznja, stepenice.",
        ),
        MessageDefinition(
            messageCode = 3,
            pointType = PointType.POLE,
            operatorLabel = "Stub",
            ttsText = "Paznja, stub u blizini.",
        ),
        MessageDefinition(
            messageCode = 4,
            pointType = PointType.ENTRANCE,
            operatorLabel = "Ulaz",
            ttsText = "Ulaz u objekat sa desne strane.",
        ),
        MessageDefinition(
            messageCode = 5,
            pointType = PointType.BUS_STOP,
            operatorLabel = "Autobusko stajaliste",
            ttsText = "Autobusko stajaliste ispred vas.",
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
