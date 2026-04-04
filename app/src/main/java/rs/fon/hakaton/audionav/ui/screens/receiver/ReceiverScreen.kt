package rs.fon.hakaton.audionav.ui.screens.receiver

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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receiver Mode") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Status skeniranja",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("State: ${state.statusText}")
                    Text("Scanner supported: ${if (state.scannerSupported) "Da" else "Ne"}")
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
                        text = "Poslednja detekcija",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.lastDecodedText
                            ?: "Ovde ce se prikazivati poslednji validni beacon dogadjaj.",
                    )
                    Text("Beacon ID: ${state.lastDetectedBeaconId ?: "-"}")
                    Text("Point type: ${state.lastDetectedPointType?.displayName ?: "-"}")
                    Text("Priority: ${state.lastDetectedPriority?.displayName ?: "-"}")
                    Text("Message code: ${state.lastDetectedMessageCode?.toString() ?: "-"}")
                    Text("RSSI: ${state.lastRssi?.toString() ?: "-"}")
                    Text("Detected at: ${state.lastDetectedAt?.toString() ?: "-"}")
                }
            }

            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 56.dp),
                enabled = state.isReady && !state.isScanning && state.scannerSupported,
            ) {
                Text("Start Scanning")
            }

            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 56.dp),
                enabled = state.isScanning || state.retryScheduled,
            ) {
                Text("Stop")
            }
        }
    }
}
