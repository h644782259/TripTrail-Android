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

val TripInk = Color(0xFF1A3B36)
val TripLake = Color(0xFF4D9496)
val TripLakeText = Color(0xFF296E70)
val TripSage = Color(0xFF6E9C7D)
val TripMist = Color(0xFFBDD6D6)
val TripSand = Color(0xFFD1B88F)
val TripCanvas = Color(0xFFF7F5ED)
val TripSurface = Color(0xFFFFFDF8)
val TripItemSurface = Color(0xFFF6F3EC)

val Lake = TripLakeText
val Ink = TripInk
val Mist = TripMist
val Canvas = TripCanvas
val Coral = TripSand

private val LightColors = lightColorScheme(
    primary = TripLakeText, onPrimary = Color.White,
    primaryContainer = TripMist.copy(alpha = .42f), onPrimaryContainer = TripInk,
    secondary = TripSage, tertiary = TripSand,
    background = TripCanvas, onBackground = TripInk,
    surface = TripSurface, onSurface = TripInk,
    surfaceVariant = TripItemSurface, onSurfaceVariant = TripInk.copy(alpha = .66f),
    outline = TripMist.copy(alpha = .7f), error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC9CB), onPrimary = Color(0xFF073837),
    primaryContainer = Color(0xFF1A4D4C), secondary = Color(0xFF9DCAA9), tertiary = Color(0xFFE3C697),
    background = Color(0xFF171B1A), surface = Color(0xFF202624), surfaceVariant = Color(0xFF28302D),
    onBackground = Color(0xFFF0F3EE), onSurface = Color(0xFFF0F3EE),
)

private val TripTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 35.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 14.sp),
)

@Composable
fun TripTrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = TripTypography,
        content = content,
    )
}
