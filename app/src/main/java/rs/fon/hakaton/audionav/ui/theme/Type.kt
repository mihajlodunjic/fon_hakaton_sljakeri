package rs.fon.hakaton.audionav.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Veliki, čitljivi fontovi za slabovide korisnike
// - Sve veličine su povećane u odnosu na standard
// - FontFamily.SansSerif = sistemski sans-serif (Roboto na Androidu) — odlična čitljivost
// - sp jedinice — automatski skaliraju sa sistemskim podešavanjima fonta korisnika

val AppTypography = Typography(
    // Naslov ekrana — "Pametni Audio Nav"
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    // Naslovi kartica — "Status skeniranja", "TTS status" itd.
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    // Glavni tekst u karticama
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.15.sp,
    ),
    // Sekundarni tekst
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
    ),
    // Tekst u dugmicima
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp,
    ),
)