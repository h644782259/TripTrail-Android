package com.personal.triptrail.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Lake = Color(0xFF2E756A)
val Ink = Color(0xFF183A3A)
val Mist = Color(0xFFD8ECE5)
val Canvas = Color(0xFFF5F8F6)
val Coral = Color(0xFFF3A65A)

private val LightColors = lightColorScheme(
    primary = Lake, onPrimary = Color.White, primaryContainer = Mist, onPrimaryContainer = Ink,
    secondary = Coral, background = Canvas, surface = Color.White, onBackground = Ink, onSurface = Ink,
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91D5C8), onPrimary = Color(0xFF00372F), primaryContainer = Color(0xFF145046),
    secondary = Color(0xFFFFB86C), background = Color(0xFF0E1514), surface = Color(0xFF17201F)
)

@Composable
fun TripTrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
