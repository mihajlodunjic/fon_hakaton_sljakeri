package rs.fon.hakaton.audionav.ui.screens.receiver

import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rs.fon.hakaton.audionav.domain.ReceiverScreenState
import rs.fon.hakaton.audionav.domain.toDisplayText
import rs.fon.hakaton.audionav.ui.theme.BrightYellow
import rs.fon.hakaton.audionav.ui.theme.CardBackground
import rs.fon.hakaton.audionav.ui.theme.DeepBlack
import rs.fon.hakaton.audionav.ui.theme.ErrorRed
import rs.fon.hakaton.audionav.ui.theme.LightYellow
import rs.fon.hakaton.audionav.ui.theme.PureWhite
import rs.fon.hakaton.audionav.ui.theme.ScanningGreen

// ── Deljene konstante za dizajn ────────────────────────────────────────────────
private val CardShape = RoundedCornerShape(12.dp)
private val CardBorder = BorderStroke(2.dp, BrightYellow)
private val ButtonShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverScreen(
    state: ReceiverScreenState,
    readinessMessage: String,
    onNavigateBack: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onCalibrateHeading: () -> Unit,
    onResetHeadingCalibration: () -> Unit,
    onRepeatLastClick: () -> Unit,
    onTouchExploreControl: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Receiver mod",
                        style = MaterialTheme.typography.titleMedium,
                        color = BrightYellow,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .sizeIn(minHeight = 56.dp, minWidth = 80.dp)
                            .touchExplore("Nazad", onTouchExploreControl)
                            .semantics { contentDescription = "Nazad na prethodni ekran" },
                    ) {
                        Text(
                            text = "◀ Nazad",
                            style = MaterialTheme.typography.labelLarge,
                            color = BrightYellow,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepBlack,
                    titleContentColor = BrightYellow,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── 1. GLAVNA AKCIJSKA DUGMAD (na vrhu — najvažnije za korisnike) ──
            ReceiverPrimaryButton(
                text = if (state.isScanning) "⏺  Skeniranje aktivno..." else "▶  Pokreni skeniranje",
                contentDesc = if (state.isScanning) "Skeniranje je trenutno aktivno" else "Pokreni receiver skeniranje",
                onClick = onStartClick,
                enabled = state.isReady && !state.isScanning && state.scannerSupported,
                isActive = state.isScanning,
                onTouchExplore = { onTouchExploreControl("Pokreni skeniranje") },
                minHeight = 80.dp,
            )

            ReceiverOutlinedButton(
                text = "⏹  Zaustavi skeniranje",
                contentDesc = "Zaustavi receiver skeniranje",
                onClick = onStopClick,
                enabled = state.isScanning || state.retryScheduled,
                onTouchExplore = { onTouchExploreControl("Zaustavi") },
            )

            ReceiverOutlinedButton(
                text = "🔁  Ponovi poslednju poruku",
                contentDesc = "Pročitaj ponovo poslednju glasovnu poruku: ${state.lastSpokenText ?: "nema poruke"}",
                onClick = onRepeatLastClick,
                enabled = state.lastSpokenText != null,
                onTouchExplore = { onTouchExploreControl("Procitaj opet poslednju poruku") },
            )

            // ── 2. STATUS SKENIRANJA ────────────────────────────────────────────
            AccessibleCard(
                contentDesc = "Status skeniranja: ${state.statusText}",
            ) {
                CardLabel("Status skeniranja")
                CardRow("Stanje", state.statusText)
                CardRow(
                    label = "Scanner",
                    value = if (state.scannerSupported) "Podržan ✓" else "Nije podržan ✗",
                    valueColor = if (state.scannerSupported) ScanningGreen else ErrorRed,
                )
                CardRow("Uređaj", readinessMessage)

                state.errorText?.let { err ->
                    Text(
                        text = "⚠ $err",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (state.retryScheduled) {
                    Text(
                        text = "⏳ Retry skeniranja je zakazan...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LightYellow,
                    )
                }
            }

            // ── 3. POSLEDNJI SIGNAL ─────────────────────────────────────────────
            val lastSignalDesc = buildString {
                append("Poslednji signal. ")
                append(state.lastDecodedText ?: "Nema signala.")
                state.lastDetectedPointType?.let { append(" Tip: ${it.displayName}.") }
                state.lastDetectedPriority?.let { append(" Prioritet: ${it.displayName}.") }
                state.lastRssi?.let { append(" Jačina signala: $it.") }
            }

            AccessibleCard(contentDesc = lastSignalDesc) {
                CardLabel("Poslednji signal")
                Text(
                    text = state.lastDecodedText
                        ?: "Čeka se beacon signal...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.lastDecodedText != null) ScanningGreen else PureWhite.copy(alpha = 0.6f),
                    fontWeight = if (state.lastDecodedText != null) FontWeight.Bold else FontWeight.Normal,
                )
                if (state.lastDetectedBeaconId != null) {
                    CardRow("Beacon ID", state.lastDetectedBeaconId.toString())
                    state.lastDetectedPointType?.let { CardRow("Tip tačke", it.displayName) }
                    state.lastDetectedPriority?.let { CardRow("Prioritet", it.displayName) }
                    state.lastRssi?.let { CardRow("Jačina signala (RSSI)", "$it dBm") }
                }
            }

            // ── 4. SMER ─────────────────────────────────────────────────────────
            val directionDesc = buildString {
                append("Smer kretanja. ")
                append("Smer objekta: ${state.lastDirectionLabel.toDisplayText()}. ")
                state.localHeadingDegrees?.let { append("Ugao: $it stepeni.") }
            }

            AccessibleCard(contentDesc = directionDesc) {
                CardLabel("Smer")
                CardRow(
                    label = "Smer objekta",
                    value = state.lastDirectionLabel.toDisplayText(),
                    valueColor = BrightYellow,
                )
                CardRow(
                    label = "Ugao",
                    value = state.localHeadingDegrees?.let { formatDegrees(it) } ?: "-",
                )
                CardRow("Pouzdanost", state.headingConfidenceText)
                state.directionFallbackReason?.let {
                    Text(
                        text = "ℹ $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LightYellow,
                    )
                }
            }

            // ── 5. KALIBRACIJA SMERA ────────────────────────────────────────────
            AccessibleCard(
                contentDesc = "Kalibracija smera. Status: ${state.directionCalibrationText}",
            ) {
                CardLabel("Kalibracija smera")
                CardRow("Status", state.directionCalibrationText)
                CardRow(
                    label = "Heading",
                    value = state.currentHeadingDegrees?.let { formatDegrees(it) } ?: "-",
                )

                ReceiverPrimaryButton(
                    text = "🧭  Postavi smer kao 0°",
                    contentDesc = "Postavi trenutni smer kao nula stepeni",
                    onClick = onCalibrateHeading,
                    enabled = true,
                    onTouchExplore = { onTouchExploreControl("Postavi trenutni smer kao 0") },
                )

                ReceiverOutlinedButton(
                    text = "↺  Resetuj kalibraciju",
                    contentDesc = "Resetuj kalibraciju smera",
                    onClick = onResetHeadingCalibration,
                    enabled = state.isDirectionCalibrated,
                    onTouchExplore = { onTouchExploreControl("Resetuj kalibraciju") },
                )
            }

            // ── 6. TTS STATUS ───────────────────────────────────────────────────
            val ttsDesc = buildString {
                append("TTS status: ${state.ttsStatusText}. ")
                state.lastSpokenText?.let { append("Poslednja poruka: $it.") }
            }

            AccessibleCard(contentDesc = ttsDesc) {
                CardLabel("Glasovni status (TTS)")
                CardRow(
                    label = "Status",
                    value = state.ttsStatusText,
                    valueColor = if (state.lastTtsError != null) ErrorRed else ScanningGreen,
                )
                state.lastSpokenText?.let {
                    CardRow("Poslednja poruka", it)
                }
                state.lastTtsError?.let { err ->
                    Text(
                        text = "⚠ $err",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ── 7. SKORAŠNJI DOGAĐAJI ───────────────────────────────────────────
            if (state.recentEvents.isNotEmpty()) {
                AccessibleCard(contentDesc = "Skorašnji eventi, ${state.recentEvents.size} događaja") {
                    CardLabel("Skorašnji događaji")
                    state.recentEvents.take(5).forEach { event ->
                        Text(
                            text = buildString {
                                append("${event.beaconId}")
                                append("  •  RSSI: ${event.rssi}")
                                append("  •  ${if (event.wasAnnounced) "Najavljeno ✓" else "Preskočeno"}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (event.wasAnnounced) ScanningGreen else PureWhite.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

// ── Reusable komponente ────────────────────────────────────────────────────────

@Composable
private fun AccessibleCard(
    contentDesc: String,
    content: @Composable Column.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = contentDesc },
        shape = CardShape,
        border = CardBorder,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = { content() },
        )
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = BrightYellow,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CardRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = PureWhite,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PureWhite.copy(alpha = 0.65f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ReceiverPrimaryButton(
    text: String,
    contentDesc: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isActive: Boolean = false,
    onTouchExplore: () -> Unit,
    minHeight: Dp = 64.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = minHeight)
            .touchExploreRaw(onTouchExplore)
            .semantics { contentDescription = contentDesc },
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) ScanningGreen else BrightYellow,
            contentColor = DeepBlack,
            disabledContainerColor = CardBackground,
            disabledContentColor = PureWhite.copy(alpha = 0.4f),
        ),
        border = BorderStroke(2.dp, if (enabled) (if (isActive) ScanningGreen else BrightYellow) else PureWhite.copy(alpha = 0.2f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReceiverOutlinedButton(
    text: String,
    contentDesc: String,
    onClick: () -> Unit,
    enabled: Boolean,
    onTouchExplore: () -> Unit,
    minHeight: Dp = 64.dp,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = minHeight)
            .touchExploreRaw(onTouchExplore)
            .semantics { contentDescription = contentDesc },
        shape = ButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = LightYellow,
            disabledContentColor = PureWhite.copy(alpha = 0.35f),
        ),
        border = BorderStroke(2.dp, if (enabled) LightYellow else PureWhite.copy(alpha = 0.2f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Utility ────────────────────────────────────────────────────────────────────

private fun formatDegrees(angle: Int): String = "$angle°"

private fun formatRelativeAngle(angle: Int): String =
    if (angle > 0) "+$angle°" else "$angle°"

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.touchExploreRaw(onExplore: () -> Unit): Modifier = composed {
    var announced by remember { mutableStateOf(false) }
    pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (!announced) {
                    announced = true
                    onExplore()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> announced = false
        }
        false
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.touchExplore(
    label: String,
    onTouchExploreControl: (String) -> Unit,
): Modifier = composed {
    var announced by remember(label) { mutableStateOf(false) }
    pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (!announced) {
                    announced = true
                    onTouchExploreControl(label)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> announced = false
        }
        false
    }
}