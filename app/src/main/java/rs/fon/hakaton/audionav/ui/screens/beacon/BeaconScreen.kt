package rs.fon.hakaton.audionav.ui.screens.beacon

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconScreen(
    readinessMessage: String,
    statusText: String,
    onNavigateBack: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Placeholder forma",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Ovde dolaze polja za label, pointType, priority i messageCode.")
                }
            }

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
                    Text(statusText)
                    Text(readinessMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 56.dp),
            ) {
                Text("Start Broadcasting")
            }

            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 56.dp),
            ) {
                Text("Stop")
            }
        }
    }
}
