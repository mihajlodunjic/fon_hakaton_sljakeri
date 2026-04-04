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
        ),
        MessageDefinition(
            messageCode = 2,
            pointType = PointType.STAIRS,
            operatorLabel = "Stepenice",
            subjectSingular = "Stepenice",
            subjectPlural = true,
            genericTtsText = "Pa\u017enja, stepenice u blizini.",
        ),
        MessageDefinition(
            messageCode = 3,
            pointType = PointType.POLE,
            operatorLabel = "Stub",
            subjectSingular = "Stub",
            subjectPlural = false,
            genericTtsText = "Pa\u017enja, stub u blizini.",
        ),
        MessageDefinition(
            messageCode = 4,
            pointType = PointType.ENTRANCE,
            operatorLabel = "Ulaz",
            subjectSingular = "Ulaz",
            subjectPlural = false,
            genericTtsText = "Ulaz u blizini.",
        ),
        MessageDefinition(
            messageCode = 5,
            pointType = PointType.BUS_STOP,
            operatorLabel = "Autobusko stajali\u0161te",
            subjectSingular = "Autobusko stajali\u0161te",
            subjectPlural = false,
            genericTtsText = "Autobusko stajali\u0161te u blizini.",
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
