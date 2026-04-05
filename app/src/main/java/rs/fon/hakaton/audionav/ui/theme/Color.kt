package rs.fon.hakaton.audionav.ui.theme

import androidx.compose.ui.graphics.Color

// Visokokontrastna paleta za slabovide korisnike (WCAG AAA)
// Kontrast ratio: zuta na crnoj = 19.6:1 (daleko iznad WCAG AAA minimum 7:1)

val DeepBlack       = Color(0xFF000000)  // pozadina
val NearBlack       = Color(0xFF0D0D0D)  // surface
val CardBackground  = Color(0xFF1A1A1A)  // kartice
val BrightYellow    = Color(0xFFFFE000)  // primary akcija — maksimalan kontrast na crnoj
val LightYellow     = Color(0xFFFFF176)  // secondary / outlined elementi
val PureWhite       = Color(0xFFFFFFFF)  // tekst na tamnoj pozadini
val ErrorRed        = Color(0xFFFF5252)  // greške — dovoljno svetlo na crnoj
val DisabledGray    = Color(0xFF616161)  // onemoguceni elementi
val BorderColor     = Color(0xFFFFE000)  // border kartica — isti kao primary za vidljivost
val ScanningGreen   = Color(0xFF69FF47)  // status aktivnog skeniranja