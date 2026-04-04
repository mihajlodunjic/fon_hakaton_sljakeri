package rs.fon.hakaton.audionav.ui.screens.mode

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rs.fon.hakaton.audionav.domain.PermissionStatus
import rs.fon.hakaton.audionav.domain.PermissionUiState

@Composable
fun ModeSelectionScreen(
    readinessMessage: String,
    permissionUiState: PermissionUiState,
    onBeaconModeClick: () -> Unit,
    onReceiverModeClick: () -> Unit,
    onRequestPermissionsClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onRefreshStatusClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Pametni Audio Nav",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Offline BLE navigacioni MVP sa odvojenim beacon i receiver modom.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Kartica statusa uredjaja" },
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Status uredjaja",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = readinessMessage,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        when (permissionUiState.status) {
            PermissionStatus.MISSING -> {
                Button(
                    onClick = onRequestPermissionsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Zatrazi bluetooth dozvole" }
                        .sizeIn(minHeight = 56.dp),
                ) {
                    Text("Zatrazi dozvole")
                }
            }

            PermissionStatus.PERMANENTLY_DENIED -> {
                Button(
                    onClick = onOpenSettingsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Otvori podesavanja aplikacije" }
                        .sizeIn(minHeight = 56.dp),
                ) {
                    Text("Otvori podesavanja")
                }
            }

            PermissionStatus.GRANTED -> {
                OutlinedButton(
                    onClick = onRefreshStatusClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Osvezi status uredjaja" }
                        .sizeIn(minHeight = 56.dp),
                ) {
                    Text("Osvezi status")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = onBeaconModeClick,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Otvori beacon mod" }
                    .sizeIn(minHeight = 64.dp),
            ) {
                Text("Beacon mod")
            }
            Button(
                onClick = onReceiverModeClick,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Otvori receiver mod" }
                    .sizeIn(minHeight = 64.dp),
            ) {
                Text("Receiver mod")
            }
        }
    }
}
