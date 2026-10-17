package com.satya.biker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BrandOrange = Color(0xFFFF6D00)
val BrandOrangeDark = Color(0xFFE65100)
val SosRed = Color(0xFFD32F2F)
val SafeGreen = Color(0xFF2E7D32)
val PendingAmber = Color(0xFFF9A825)
val Ink = Color(0xFF0E0F12)
val Surface1 = Color(0xFF17191E)
val Surface2 = Color(0xFF20232A)

private val DarkColors = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2200),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color.Black,
    tertiary = Color(0xFF4FC3F7),
    background = Ink,
    onBackground = Color(0xFFECECEC),
    surface = Surface1,
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFB8BBC2),
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerLow = Color(0xFF131519),
    outline = Color(0xFF3A3E47),
    error = Color(0xFFFF5252),
)

private val LightColors = lightColorScheme(
    primary = BrandOrangeDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF8D4F00),
    tertiary = Color(0xFF0277BD),
    background = Color(0xFFF7F5F2),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0EBE6),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF2EEEA),
    surfaceContainerLow = Color(0xFFFAF8F6),
    outline = Color(0xFFD5CEC8),
    error = SosRed,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
)

@Composable
fun BikerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
