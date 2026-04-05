package rs.fon.hakaton.audionav.ui.screens.receiver

import android.view.MotionEvent
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiverTouchExploreControllerTest {

    @Test
    fun `drag across targets announces new target and activates released target`() {
        val controller = ReceiverTouchExploreController()
        val targets = listOf(
            target(id = "a", label = "Start Scanning", left = 0f, top = 0f, right = 100f, bottom = 80f),
            target(id = "b", label = "Nazad", left = 0f, top = 100f, right = 100f, bottom = 180f),
        )

        val down = controller.onMotionEvent(
            actionMasked = MotionEvent.ACTION_DOWN,
            x = 20f,
            y = 20f,
            targets = targets,
        )
        assertEquals("Start Scanning", down.announceLabel)
        assertNull(down.activateTargetId)

        val move = controller.onMotionEvent(
            actionMasked = MotionEvent.ACTION_MOVE,
            x = 20f,
            y = 120f,
            targets = targets,
        )
        assertEquals("Nazad", move.announceLabel)
        assertNull(move.activateTargetId)

        val up = controller.onMotionEvent(
            actionMasked = MotionEvent.ACTION_UP,
            x = 20f,
            y = 120f,
            targets = targets,
        )
        assertNull(up.announceLabel)
        assertEquals("b", up.activateTargetId)
    }

    @Test
    fun `leaving and reentering same target announces it again`() {
        val controller = ReceiverTouchExploreController()
        val targets = listOf(
            target(id = "repeat", label = "Ponovi poslednju poruku", left = 0f, top = 0f, right = 140f, bottom = 80f),
        )

        val firstEnter = controller.onMotionEvent(
            actionMasked = MotionEvent.ACTION_DOWN,
            x = 20f,
            y = 20f,
            targets = targets,
        )
        assertEquals("Ponovi poslednju poruku", firstEnter.announceLabel)

        val leave = controller.onMotionEvent(
            actionMasked = MotionEvent.ACTION_MOVE,
            x = 220f,
            y = 220f,
            targets = targets,
        )
        assertNull(leave.announceLabel)

        val reenter = controller.onMotionEvent(
            actionMasked = MotionEvent.ACTION_MOVE,
            x = 20f,
            y = 20f,
            targets = targets,
        )
        assertEquals("Ponovi poslednju poruku", reenter.announceLabel)
    }

    @Test
    fun `disabled target is ignored for announce and activation`() {
        val controller = ReceiverTouchExploreController()
        val targets = listOf(
            target(
                id = "disabled",
                label = "Ponovi poslednju poruku",
                left = 0f,
                top = 0f,
                right = 140f,
                bottom = 80f,
                enabled = false,
            ),
        )

        val down = controller.onMotionEvent(
            actionMasked = MotionEvent.ACTION_DOWN,
            x = 20f,
            y = 20f,
            targets = targets,
        )
        assertNull(down.announceLabel)

        val up = controller.onMotionEvent(
            actionMasked = MotionEvent.ACTION_UP,
            x = 20f,
            y = 20f,
            targets = targets,
        )
        assertNull(up.activateTargetId)
    }

    private fun target(
        id: String,
        label: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        enabled: Boolean = true,
    ): ReceiverTouchExploreTargetSnapshot {
        return ReceiverTouchExploreTargetSnapshot(
            id = id,
            label = label,
            enabled = enabled,
            bounds = Rect(left, top, right, bottom),
        )
    }
}
