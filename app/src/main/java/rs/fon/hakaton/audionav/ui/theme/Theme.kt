package rs.fon.hakaton.audionav.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Uvek dark tema — crna pozadina + žuti akcenti = maksimalan kontrast (WCAG AAA)
// Ne koristimo isSystemInDarkTheme() jer je visoki kontrast uvek bolji za ciljnu grupu

private val AccessibleDarkColors = darkColorScheme(
    primary          = BrightYellow,    // dugmad, aktivni elementi
    onPrimary        = DeepBlack,       // tekst na žutim dugmadima
    primaryContainer = CardBackground,  // kontejneri
    onPrimaryContainer = PureWhite,

    secondary        = LightYellow,     // outlined dugmad, sekundarni akcenti
    onSecondary      = DeepBlack,
    secondaryContainer = CardBackground,
    onSecondaryContainer = PureWhite,

    background       = DeepBlack,       // pozadina ekrana
    onBackground     = PureWhite,       // tekst na pozadini

    surface          = CardBackground,  // kartice i surface elementi
    onSurface        = PureWhite,       // tekst na karticama
    surfaceVariant   = NearBlack,
    onSurfaceVariant = PureWhite,

    outline          = BrightYellow,    // border oko kartica i outlined dugmadi

    error            = ErrorRed,
    onError          = DeepBlack,
)

@Composable
fun PametniAudioNavTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AccessibleDarkColors,
        typography = AppTypography,
        content = content,
    )
}