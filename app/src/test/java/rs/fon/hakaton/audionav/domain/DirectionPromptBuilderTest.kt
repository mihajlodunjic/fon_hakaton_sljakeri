package rs.fon.hakaton.audionav.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionPromptBuilderTest {

    @Test
    fun `builder returns exact output for all entrance directions`() {
        val definition = MessageCatalog.resolve(PointType.ENTRANCE, 4)!!

        assertEquals(
            "Ulaz je ispred vas.",
            DirectionPromptBuilder.buildTtsText(
                definition,
                DirectionEstimate(DirectionLabel.AHEAD, 180, DirectionConfidence.HIGH),
            ),
        )
        assertEquals(
            "Ulaz je sa va\u0161e leve strane.",
            DirectionPromptBuilder.buildTtsText(
                definition,
                DirectionEstimate(DirectionLabel.LEFT, -90, DirectionConfidence.HIGH),
            ),
        )
        assertEquals(
            "Ulaz je sa va\u0161e desne strane.",
            DirectionPromptBuilder.buildTtsText(
                definition,
                DirectionEstimate(DirectionLabel.RIGHT, 90, DirectionConfidence.HIGH),
            ),
        )
        assertEquals(
            "Ulaz je iza vas.",
            DirectionPromptBuilder.buildTtsText(
                definition,
                DirectionEstimate(DirectionLabel.BEHIND, 0, DirectionConfidence.HIGH),
            ),
        )
        assertEquals(
            "Ulaz u blizini.",
            DirectionPromptBuilder.buildTtsText(
                definition,
                DirectionEstimate(DirectionLabel.UNKNOWN, 0, DirectionConfidence.LOW),
            ),
        )
    }

    @Test
    fun `crosswalk behind falls back to generic prompt until pass confirmed`() {
        val definition = MessageCatalog.resolve(PointType.CROSSWALK, 1)!!

        assertEquals(
            "Pe\u0161a\u010dki prelaz u blizini.",
            DirectionPromptBuilder.buildTtsText(
                definition,
                DirectionEstimate(DirectionLabel.BEHIND, 0, DirectionConfidence.HIGH),
            ),
        )
        assertEquals(
            "Pro\u0161li ste pe\u0161a\u010dki prelaz.",
            DirectionPromptBuilder.buildPassedText(definition),
        )
    }

    @Test
    fun `traffic light behind falls back to generic prompt until pass confirmed`() {
        val definition = MessageCatalog.resolve(PointType.TRAFFIC_LIGHT, 6)!!

        assertEquals(
            "Semafor u blizini.",
            DirectionPromptBuilder.buildUiText(
                definition,
                DirectionEstimate(DirectionLabel.BEHIND, 0, DirectionConfidence.HIGH),
            ),
        )
        assertEquals(
            "Pro\u0161li ste semafor.",
            DirectionPromptBuilder.buildPassedText(definition),
        )
    }
}
