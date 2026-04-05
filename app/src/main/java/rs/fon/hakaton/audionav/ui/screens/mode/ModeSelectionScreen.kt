package rs.fon.hakaton.audionav.ui.screens.mode

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
import rs.fon.hakaton.audionav.ui.theme.BrightYellow
import rs.fon.hakaton.audionav.ui.theme.CardBackground
import rs.fon.hakaton.audionav.ui.theme.DeepBlack
import rs.fon.hakaton.audionav.ui.theme.LightYellow
import rs.fon.hakaton.audionav.ui.theme.PureWhite

// Zaokruzeni uglovi za kartice — konzistentni kroz ceo ekran
private val CardShape = RoundedCornerShape(12.dp)
// Debeo border na karticama — jasno definisane granice za slabovide korisnike
private val CardBorder = BorderStroke(2.dp, BrightYellow)

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
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── Naslov aplikacije ──────────────────────────────────────────────
        Text(
            text = "Pametni Audio Nav",
            style = MaterialTheme.typography.headlineMedium,
            color = BrightYellow,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics {
                contentDescription = "Pametni Audio Nav, navigaciona aplikacija"
            },
        )

        Text(
            text = "Odaberite režim rada: Beacon ili Receiver.",
            style = MaterialTheme.typography.bodyLarge,
            color = PureWhite,
        )

        // ── Kartica statusa uređaja ────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Status uređaja: $readinessMessage" },
            shape = CardShape,
            border = CardBorder,
            colors = CardDefaults.cardColors(containerColor = CardBackground),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Status uređaja",
                    style = MaterialTheme.typography.titleMedium,
                    color = BrightYellow,
                )
                Text(
                    text = readinessMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureWhite,
                )
            }
        }

        // ── Dugme za dozvole / osvežavanje ────────────────────────────────
        when (permissionUiState.status) {
            PermissionStatus.MISSING -> {
                AccessiblePrimaryButton(
                    text = "Zatraži dozvole",
                    contentDesc = "Zatraži Bluetooth dozvole",
                    onClick = onRequestPermissionsClick,
                )
            }

            PermissionStatus.PERMANENTLY_DENIED -> {
                AccessiblePrimaryButton(
                    text = "Otvori podešavanja",
                    contentDesc = "Otvori podešavanja aplikacije da odobriš dozvole",
                    onClick = onOpenSettingsClick,
                )
            }

            PermissionStatus.GRANTED -> {
                AccessibleOutlinedButton(
                    text = "Osveži status",
                    contentDesc = "Osveži status uređaja",
                    onClick = onRefreshStatusClick,
                )
            }
        }

        // ── Dugmad za odabir moda ──────────────────────────────────────────
        // Razdvojena u dve kolone radi vecih touch targeta

        AccessiblePrimaryButton(
            text = "🔵  Receiver mod",
            contentDesc = "Otvori Receiver mod — slušanje beacon signala",
            onClick = onReceiverModeClick,
            minHeight = 72.dp,
        )

        AccessibleOutlinedButton(
            text = "📡  Beacon mod",
            contentDesc = "Otvori Beacon mod — emitovanje signala",
            onClick = onBeaconModeClick,
            minHeight = 72.dp,
        )
    }
}

// ── Reusable accessible button komponente ─────────────────────────────────────

@Composable
private fun AccessiblePrimaryButton(
    text: String,
    contentDesc: String,
    onClick: () -> Unit,
    minHeight: androidx.compose.ui.unit.Dp = 64.dp,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = minHeight)
            .semantics { contentDescription = contentDesc },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrightYellow,
            contentColor = DeepBlack,
            disabledContainerColor = CardBackground,
            disabledContentColor = PureWhite,
        ),
        border = BorderStroke(2.dp, BrightYellow),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AccessibleOutlinedButton(
    text: String,
    contentDesc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 64.dp,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = minHeight)
            .semantics { contentDescription = contentDesc },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = LightYellow,
            disabledContentColor = PureWhite.copy(alpha = 0.4f),
        ),
        border = BorderStroke(2.dp, if (enabled) LightYellow else PureWhite.copy(alpha = 0.3f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}