package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WorldLinkDarkScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00242B),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = OnCyanContainerDark,
    secondary = EmeraldSignal,
    onSecondary = Color(0xFF00251A),
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = OnEmeraldContainerDark,
    tertiary = SkyAccent,
    onTertiary = Color(0xFF002438),
    background = MidnightBg,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = MidnightSurfaceHigh,
    surfaceContainerHigh = MidnightSurfaceHighest,
    outline = MidnightOutline,
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralContainerDark,
    onErrorContainer = Color(0xFFFFDADE)
)

private val WorldLinkLightScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = OnCyanContainerLight,
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = OnEmeraldContainerLight,
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = MistBg,
    onBackground = TextPrimaryLight,
    surface = MistSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = MistSurfaceHigh,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = MistSurface,
    surfaceContainer = MistSurfaceHigh,
    surfaceContainerHigh = MistSurfaceHighest,
    outline = MistOutline,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val WorldLinkShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WorldLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WorldLinkDarkScheme else WorldLinkLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WorldLinkTypography,
        shapes = WorldLinkShapes,
        content = content
    )
}
