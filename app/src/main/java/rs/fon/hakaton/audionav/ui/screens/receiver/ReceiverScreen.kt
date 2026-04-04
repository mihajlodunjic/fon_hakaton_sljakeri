package rs.fon.hakaton.audionav.ui.screens.receiver

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rs.fon.hakaton.audionav.domain.ReceiverScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverScreen(
    state: ReceiverScreenState,
    readinessMessage: String,
    onNavigateBack: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val voiceAnnouncementStatus = when {
        state.lastEligibleForAnnouncement != true && state.lastTtsError == null && state.lastSpokenAt == null -> {
            "Nije pokusana"
        }
        state.lastTtsError != null -> "Nije zakazana"
        state.lastEligibleForAnnouncement == true -> "Uspesno zakazana"
        else -> "Ceka se ishod TTS-a"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receiver mod") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Nazad")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Kartica statusa receiver moda" },
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Status skeniranja",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Stanje: ${state.statusText}")
                    Text("Scanner podrzan: ${if (state.scannerSupported) "Da" else "Ne"}")
                    Text(readinessMessage, style = MaterialTheme.typography.bodyMedium)
                    state.errorText?.let { errorText ->
                        Text(
                            text = errorText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.retryScheduled) {
                        Text(
                            text = "Retry skeniranja je zakazan za nekoliko sekundi.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Poslednji signal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.lastDecodedText
                            ?: "Ovde ce se prikazivati poslednji validni beacon dogadjaj.",
                    )
                    Text("Beacon ID: ${state.lastDetectedBeaconId ?: "-"}")
                    Text("Tip tacke: ${state.lastDetectedPointType?.displayName ?: "-"}")
                    Text("Prioritet: ${state.lastDetectedPriority?.displayName ?: "-"}")
                    Text("Kod poruke: ${state.lastDetectedMessageCode?.toString() ?: "-"}")
                    Text("RSSI: ${state.lastRssi?.toString() ?: "-"}")
                    Text("Detektovano u: ${state.lastDetectedAt?.toString() ?: "-"}")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Stabilizacija",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${state.stabilizationProgress}/${state.requiredStabilizationCount} iznad ${state.rssiThreshold} dBm",
                    )
                    Text(
                        text = state.lastGateDecisionText
                            ?: "Ceka se dovoljan broj uzastopnih validnih RSSI ocitavanja.",
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Cooldown i odluka",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when (state.lastEligibleForAnnouncement) {
                            true -> "Najava dozvoljena po gate-u."
                            false -> "Najava trenutno nije dozvoljena po gate-u."
                            null -> "Gate odluka jos nije doneta."
                        },
                    )
                    Text("Poslednja dozvoljena najava: ${state.lastAnnouncementAt?.toString() ?: "-"}")
                    Text("Glasovna najava: $voiceAnnouncementStatus")
                    Text(
                        text = state.lastGateDecisionText
                            ?: "Cooldown odluka ce biti prikazana kada signal postane stabilan.",
                    )
                    Text(
                        text = state.lastArbitrationDecisionText
                            ?: "Arbitraza prioriteta i blizine jos nije aktivirana.",
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Scheduler najava",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Trenutno govori beacon: ${state.currentAnnouncementBeaconId ?: "-"}")
                    Text("Tekst trenutne najave: ${state.currentAnnouncementText ?: "-"}")
                    Text("Prioritet trenutne najave: ${state.currentAnnouncementPriority?.displayName ?: "-"}")
                    Text("Stabilizovan RSSI trenutne najave: ${state.currentAnnouncementRssi?.toString() ?: "-"}")
                    Text("Cekajuci beacon: ${state.pendingAnnouncementBeaconId ?: "-"}")
                    Text("Tekst cekajuce najave: ${state.pendingAnnouncementText ?: "-"}")
                    Text("Prioritet cekajuce najave: ${state.pendingAnnouncementPriority?.displayName ?: "-"}")
                    Text("Stabilizovan RSSI cekajuce najave: ${state.pendingAnnouncementRssi?.toString() ?: "-"}")
                    Text("Globalni gap aktivan do: ${state.globalAnnouncementGapUntil?.toString() ?: "-"}")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Kartica TTS statusa" },
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "TTS status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Status: ${state.ttsStatusText}")
                    Text("Poslednja izgovorena poruka: ${state.lastSpokenText ?: "-"}")
                    Text("Poslednja glasovna najava u: ${state.lastSpokenAt?.toString() ?: "-"}")
                    state.lastTtsError?.let { ttsError ->
                        Text(
                            text = ttsError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Skorasnji dogadjaji",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.recentEvents.isEmpty()) {
                        Text("Jos nema stabilizovanih receiver odluka.")
                    } else {
                        state.recentEvents.take(5).forEach { event ->
                            Text(
                                text = buildString {
                                    append(event.beaconId)
                                    append(" | msg=")
                                    append(event.messageCode)
                                    append(" | RSSI=")
                                    append(event.rssi)
                                    append(" | allowed=")
                                    append(if (event.wasAnnounced) "da" else "ne")
                                    append(" | at=")
                                    append(event.detectedAt)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Pokreni receiver skeniranje" }
                    .sizeIn(minHeight = 56.dp),
                enabled = state.isReady && !state.isScanning && state.scannerSupported,
            ) {
                Text("Pokreni skeniranje")
            }

            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Zaustavi receiver skeniranje" }
                    .sizeIn(minHeight = 56.dp),
                enabled = state.isScanning || state.retryScheduled,
            ) {
                Text("Zaustavi")
            }
        }
    }
}
