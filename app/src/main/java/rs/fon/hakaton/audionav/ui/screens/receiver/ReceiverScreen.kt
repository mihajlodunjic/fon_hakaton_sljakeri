package rs.fon.hakaton.audionav.ui.screens.receiver

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import rs.fon.hakaton.audionav.domain.ReceiverScreenState

private val ReceiverScreenBackground = Color(0xFFF7F4F5)
private val ReceiverScreenText = Color(0xFF111111)
private val ReceiverButtonBackground = Color(0xFF1F1F1F)
private val ReceiverStatusBar = Color(0xFF7A7778)
private val ReceiverButtonShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReceiverScreen(
    state: ReceiverScreenState,
    readinessMessage: String,
    onNavigateBack: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onCalibrateHeading: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onResetHeadingCalibration: () -> Unit,
    onRepeatLastClick: () -> Unit,
    onTouchExploreControl: (String) -> Unit,
) {
    ReceiverSystemBars()

    val scanningActive = state.isScanning || state.retryScheduled
    val scanningText = if (scanningActive) "UKLJUČENO" else "ISKLJUČENO"
    val startStopLabel = if (scanningActive) "Stop Scanning" else "Start Scanning"
    val startStopAction = if (scanningActive) onStopClick else onStartClick
    val startStopEnabled = if (scanningActive) {
        true
    } else {
        state.isReady && state.scannerSupported
    }

    val helperText = when {
        state.errorText != null -> state.errorText
        !state.isReady -> readinessMessage
        state.retryScheduled -> "Pokušavam ponovo da pokrenem skeniranje."
        else -> null
    }

    val touchExploreTargets = remember { mutableStateMapOf<String, ReceiverTouchExploreTargetState>() }
    val touchExploreController = remember { ReceiverTouchExploreController() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReceiverScreenBackground)
            .statusBarsPadding()
            .pointerInteropFilter { event ->
                val resolution = touchExploreController.onMotionEvent(
                    actionMasked = event.actionMasked,
                    x = event.x,
                    y = event.y,
                    targets = touchExploreTargets.values.map(ReceiverTouchExploreTargetState::snapshot),
                )
                resolution.announceLabel?.let(onTouchExploreControl)
                resolution.activateTargetId?.let { targetId ->
                    touchExploreTargets[targetId]
                        ?.takeIf { it.enabled }
                        ?.onActivate
                        ?.invoke()
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    -> true

                    else -> false
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text(
                text = "Receiver režim",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Normal,
                color = ReceiverScreenText,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Skeniranje: $scanningText",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ReceiverScreenText,
                )
                helperText?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.errorText != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            ReceiverScreenText.copy(alpha = 0.72f)
                        },
                    )
                }
            }

            ReceiverActionButton(
                targetId = "scan-toggle",
                label = startStopLabel,
                onClick = startStopAction,
                enabled = startStopEnabled,
                touchExploreLabel = startStopLabel,
                contentDescription = if (scanningActive) {
                    "Zaustavi receiver skeniranje"
                } else {
                    "Pokreni receiver skeniranje"
                },
                onTargetChanged = { target ->
                    touchExploreTargets[target.id] = target
                },
                onTargetRemoved = { targetId ->
                    touchExploreTargets.remove(targetId)
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Poslednja detekcija:",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ReceiverScreenText,
                )
                ReceiverInfoLine("Beacon", state.lastDetectedBeaconId ?: "-")
                ReceiverInfoLine("Poruka", state.lastDecodedText ?: "-")
                ReceiverInfoLine("RSSI", state.lastRssi?.toString() ?: "-")
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Lokalni ugao: ${state.localHeadingDegrees?.let(::formatDegrees) ?: "-"}",
                    style = MaterialTheme.typography.titleLarge,
                    color = ReceiverScreenText,
                )
                ReceiverInfoLine("Kalibracija", state.directionCalibrationText)
                ReceiverActionButton(
                    targetId = "calibrate-heading",
                    label = "Postavi trenutni smer kao 0°",
                    onClick = onCalibrateHeading,
                    enabled = true,
                    touchExploreLabel = "",
                    contentDescription = "Postavi trenutni smer kao nula stepeni",
                    onTargetChanged = { target ->
                        touchExploreTargets[target.id] = target
                    },
                    onTargetRemoved = { targetId ->
                        touchExploreTargets.remove(targetId)
                    },
                )
            }
            ReceiverActionButton(
                targetId = "repeat-last-message",
                label = "Ponovi poslednju poruku",
                onClick = onRepeatLastClick,
                enabled = state.lastSpokenText != null,
                touchExploreLabel = "Ponovi poslednju poruku",
                contentDescription = "Ponovi poslednju glasovnu poruku",
                onTargetChanged = { target ->
                    touchExploreTargets[target.id] = target
                },
                onTargetRemoved = { targetId ->
                    touchExploreTargets.remove(targetId)
                },
            )

            ReceiverActionButton(
                targetId = "navigate-back",
                label = "Nazad",
                onClick = onNavigateBack,
                enabled = true,
                touchExploreLabel = "Nazad",
                contentDescription = "Vrati se nazad",
                onTargetChanged = { target ->
                    touchExploreTargets[target.id] = target
                },
                onTargetRemoved = { targetId ->
                    touchExploreTargets.remove(targetId)
                },
            )
        }
    }
}

@Composable
private fun ReceiverInfoLine(
    label: String,
    value: String,
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.headlineSmall,
        color = ReceiverScreenText,
    )
}

@Composable
private fun ReceiverActionButton(
    targetId: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    touchExploreLabel: String,
    contentDescription: String,
    onTargetChanged: (ReceiverTouchExploreTargetState) -> Unit,
    onTargetRemoved: (String) -> Unit,
) {
    var bounds by remember(targetId) { mutableStateOf<Rect?>(null) }

    SideEffect {
        onTargetChanged(
            ReceiverTouchExploreTargetState(
                id = targetId,
                label = touchExploreLabel,
                enabled = enabled,
                bounds = bounds,
                onActivate = onClick,
            ),
        )
    }

    DisposableEffect(targetId) {
        onDispose {
            onTargetRemoved(targetId)
        }
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                bounds = coordinates.boundsInRoot()
            }
            .semantics { this.contentDescription = contentDescription }
            .sizeIn(minHeight = 78.dp),
        shape = ReceiverButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ReceiverButtonBackground,
            contentColor = Color.White,
            disabledContainerColor = ReceiverButtonBackground.copy(alpha = 0.48f),
            disabledContentColor = Color.White.copy(alpha = 0.82f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun ReceiverSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val window = activity?.window
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousStatusBarColor = window.statusBarColor
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            window.statusBarColor = ReceiverStatusBar.toArgb()
            controller.isAppearanceLightStatusBars = false
            onDispose {
                window.statusBarColor = previousStatusBarColor
                controller.isAppearanceLightStatusBars = previousLightStatusBars
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

private fun formatDegrees(angle: Int): String = "${angle}°"

internal data class ReceiverTouchExploreTargetState(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val bounds: Rect?,
    val onActivate: () -> Unit,
) {
    fun snapshot(): ReceiverTouchExploreTargetSnapshot {
        return ReceiverTouchExploreTargetSnapshot(
            id = id,
            label = label,
            enabled = enabled,
            bounds = bounds,
        )
    }
}

internal data class ReceiverTouchExploreTargetSnapshot(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val bounds: Rect?,
)

internal data class ReceiverTouchExploreResolution(
    val announceLabel: String? = null,
    val activateTargetId: String? = null,
)

internal class ReceiverTouchExploreController {
    private var currentHoveredTargetId: String? = null
    private var lastAnnouncedTargetId: String? = null
    private var isPointerActive: Boolean = false

    fun onMotionEvent(
        actionMasked: Int,
        x: Float,
        y: Float,
        targets: Collection<ReceiverTouchExploreTargetSnapshot>,
    ): ReceiverTouchExploreResolution {
        return when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPointerActive = true
                updateHoveredTarget(findTargetAt(x, y, targets))
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isPointerActive) {
                    ReceiverTouchExploreResolution()
                } else {
                    updateHoveredTarget(findTargetAt(x, y, targets))
                }
            }

            MotionEvent.ACTION_UP -> {
                val activateTargetId = if (isPointerActive) {
                    findTargetAt(x, y, targets)?.id
                } else {
                    null
                }
                reset()
                ReceiverTouchExploreResolution(activateTargetId = activateTargetId)
            }

            MotionEvent.ACTION_CANCEL -> {
                reset()
                ReceiverTouchExploreResolution()
            }

            else -> ReceiverTouchExploreResolution()
        }
    }

    private fun updateHoveredTarget(
        target: ReceiverTouchExploreTargetSnapshot?,
    ): ReceiverTouchExploreResolution {
        val nextTargetId = target?.id
        if (nextTargetId == currentHoveredTargetId) {
            return ReceiverTouchExploreResolution()
        }

        currentHoveredTargetId = nextTargetId
        if (target == null) {
            lastAnnouncedTargetId = null
            return ReceiverTouchExploreResolution()
        }

        if (!target.enabled || target.label.isBlank()) {
            lastAnnouncedTargetId = null
            return ReceiverTouchExploreResolution()
        }

        if (lastAnnouncedTargetId == target.id) {
            return ReceiverTouchExploreResolution()
        }

        lastAnnouncedTargetId = target.id
        return ReceiverTouchExploreResolution(announceLabel = target.label)
    }

    private fun findTargetAt(
        x: Float,
        y: Float,
        targets: Collection<ReceiverTouchExploreTargetSnapshot>,
    ): ReceiverTouchExploreTargetSnapshot? {
        return targets.firstOrNull { target ->
            target.enabled && target.bounds?.contains(Offset(x, y)) == true
        }
    }

    private fun reset() {
        currentHoveredTargetId = null
        lastAnnouncedTargetId = null
        isPointerActive = false
    }
}
