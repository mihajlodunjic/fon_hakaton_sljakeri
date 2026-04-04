package rs.fon.hakaton.audionav.ui.screens.mode

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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Status uređaja",
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
                        .sizeIn(minHeight = 56.dp),
                ) {
                    Text("Zatraži dozvole")
                }
            }

            PermissionStatus.PERMANENTLY_DENIED -> {
                Button(
                    onClick = onOpenSettingsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 56.dp),
                ) {
                    Text("Otvori podešavanja")
                }
            }

            PermissionStatus.GRANTED -> {
                OutlinedButton(
                    onClick = onRefreshStatusClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 56.dp),
                ) {
                    Text("Osveži status")
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
                    .sizeIn(minHeight = 64.dp),
            ) {
                Text("Beacon Mode")
            }
            Button(
                onClick = onReceiverModeClick,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 64.dp),
            ) {
                Text("Receiver Mode")
            }
        }
    }
}
