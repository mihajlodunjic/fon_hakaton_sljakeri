package rs.fon.hakaton.audionav.ui.screens.beacon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rs.fon.hakaton.audionav.domain.BeaconScreenState
import rs.fon.hakaton.audionav.domain.MessageCatalog
import rs.fon.hakaton.audionav.domain.MessageDefinition
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconScreen(
    state: BeaconScreenState,
    readinessMessage: String,
    onNavigateBack: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onPointTypeSelected: (PointType) -> Unit,
    onPrioritySelected: (Priority) -> Unit,
    onMessageSelected: (Short) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val selectedMessage = state.availableMessages.firstOrNull {
        it.messageCode == state.selectedMessageCode
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beacon Mode") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Beacon ID",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(state.beaconId)
                }
            }

            OutlinedTextField(
                value = state.labelInput,
                onValueChange = onLabelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Label") },
                enabled = !state.isAdvertising,
                singleLine = true,
            )

            BeaconDropdownField(
                label = "Point type",
                selectedLabel = state.selectedPointType.displayName,
                options = MessageCatalog.supportedPointTypes(),
                optionLabel = { it.displayName },
                enabled = !state.isAdvertising,
                onSelected = onPointTypeSelected,
            )

            BeaconDropdownField(
                label = "Priority",
                selectedLabel = state.selectedPriority.displayName,
                options = Priority.entries.toList(),
                optionLabel = { it.displayName },
                enabled = !state.isAdvertising,
                onSelected = onPrioritySelected,
            )

            BeaconDropdownField(
                label = "Message",
                selectedLabel = selectedMessage?.operatorLabel ?: "Nije definisana",
                options = state.availableMessages,
                optionLabel = { "${it.messageCode} - ${it.operatorLabel}" },
                enabled = !state.isAdvertising,
                onSelected = { onMessageSelected(it.messageCode) },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("State: ${state.statusText}")
                    Text("Advertiser supported: ${if (state.advertiserSupported) "Da" else "Ne"}")
                    Text(readinessMessage, style = MaterialTheme.typography.bodyMedium)
                    state.errorText?.let { errorText ->
                        Text(
                            text = errorText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            state.lastEncodedPayloadHex?.let { payloadHex ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Payload hex",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(payloadHex, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 56.dp),
                enabled = state.isReady && !state.isAdvertising,
            ) {
                Text("Start Broadcasting")
            }

            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 56.dp),
                enabled = state.isAdvertising,
            ) {
                Text("Stop")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> BeaconDropdownField(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
