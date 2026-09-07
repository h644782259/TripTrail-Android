package com.personal.triptrail.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TripInk = Color(0xFF24332F)
val TripLake = Color(0xFF4D9496)
val TripLakeText = Color(0xFF296E70)
val TripSage = Color(0xFF6E9C7D)
val TripMist = Color(0xFFBDD6D6)
val TripSand = Color(0xFFD1B88F)
val TripCanvas = Color(0xFFF4F6F3)
val TripSurface = Color(0xFFFFFFFF)
val TripItemSurface = Color(0xFFEDF2EF)

val Lake = TripLakeText
val Ink = TripInk
val Mist = TripMist
val Canvas = TripCanvas
val Coral = TripSand

// Define every Material container role: omitted roles fall back to the purple baseline palette.
private val LightColors = lightColorScheme(
    primary = TripLakeText, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDECE8), onPrimaryContainer = TripInk,
    inversePrimary = Color(0xFF8FC9CB),
    secondary = Color(0xFF526F60), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EBE5), onSecondaryContainer = TripInk,
    tertiary = Color(0xFF746345), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0E9DD), onTertiaryContainer = Color(0xFF493C27),
    background = TripCanvas, onBackground = TripInk,
    surface = TripSurface, onSurface = TripInk,
    surfaceVariant = TripItemSurface, onSurfaceVariant = Color(0xFF64716B),
    surfaceTint = TripLakeText,
    surfaceDim = Color(0xFFDCE3DF), surfaceBright = TripSurface,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAF9),
    surfaceContainer = Color(0xFFF0F4F2), surfaceContainerHigh = Color(0xFFE9EFEC),
    surfaceContainerHighest = Color(0xFFE2E9E5),
    inverseSurface = Color(0xFF29332F), inverseOnSurface = Color(0xFFF0F4F2),
    outline = Color(0xFF879B93), outlineVariant = Color(0xFFD4DFD9),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF4ECE6), onErrorContainer = Color(0xFF7C241D),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC9CB), onPrimary = Color(0xFF073837),
    primaryContainer = Color(0xFF1A4D4C), onPrimaryContainer = Color(0xFFCAEDEA),
    inversePrimary = TripLakeText,
    secondary = Color(0xFF9DCAA9), onSecondary = Color(0xFF183825),
    secondaryContainer = Color(0xFF304C3B), onSecondaryContainer = Color(0xFFD4EBDC),
    tertiary = Color(0xFFE3C697), onTertiary = Color(0xFF40321D),
    tertiaryContainer = Color(0xFF53432A), onTertiaryContainer = Color(0xFFF2E2C6),
    background = Color(0xFF151B18), onBackground = Color(0xFFF0F3EE),
    surface = Color(0xFF202824), onSurface = Color(0xFFF0F3EE),
    surfaceVariant = Color(0xFF29332E), onSurfaceVariant = Color(0xFFBDC9C2),
    surfaceTint = Color(0xFF8FC9CB),
    surfaceDim = Color(0xFF171B1A), surfaceBright = Color(0xFF363E3A),
    surfaceContainerLowest = Color(0xFF111613), surfaceContainerLow = Color(0xFF1C221F),
    surfaceContainer = Color(0xFF202624), surfaceContainerHigh = Color(0xFF29312C),
    surfaceContainerHighest = Color(0xFF333C36),
    inverseSurface = Color(0xFFE3EBE6), inverseOnSurface = Color(0xFF26312B),
    outline = Color(0xFF879B93), outlineVariant = Color(0xFF404F47),
    error = Color(0xFFFFB4A8), onError = Color(0xFF690005),
    errorContainer = Color(0xFF49322B), onErrorContainer = Color(0xFFFFDAD3),
    scrim = Color.Black,
)

private val TripTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp),
)

@Composable
fun TripTrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = TripTypography,
        content = content,
    )
}
