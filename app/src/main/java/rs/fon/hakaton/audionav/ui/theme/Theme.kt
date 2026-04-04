package rs.fon.hakaton.audionav.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = White,
    secondary = SignalOrange,
    onSecondary = InkBlueDark,
    background = Sand,
    onBackground = InkBlueDark,
    surface = White,
    onSurface = InkBlueDark,
)

private val DarkColors = darkColorScheme(
    primary = SignalOrange,
    onPrimary = InkBlueDark,
    secondary = White,
    onSecondary = InkBlueDark,
    background = InkBlueDark,
    onBackground = White,
    surface = InkBlue,
    onSurface = White,
)

@Composable
fun PametniAudioNavTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

