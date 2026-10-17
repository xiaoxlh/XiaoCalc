package com.example.xiaocalc.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.example.xiaocalc.calc.ThemeColor

/** Primary/secondary/tertiary all derive from the theme accent; only lightness separates them. */
fun colorSchemeFor(theme: ThemeColor): ColorScheme = darkColorScheme(
    primary = Color(theme.accent),
    onPrimary = Color(theme.onAccent),
    primaryContainer = Color(theme.accentContainer),
    onPrimaryContainer = Color(theme.onAccentContainer),

    secondary = Color(theme.accent),
    onSecondary = Color(theme.onAccent),
    secondaryContainer = Color(theme.accentContainer),
    onSecondaryContainer = Color(theme.onAccentContainer),

    tertiary = Color(theme.onAccentContainer),
    onTertiary = Color(theme.accentContainer),
    tertiaryContainer = Color(theme.accentContainer),
    onTertiaryContainer = Color(theme.onAccentContainer),

    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB9B9B9),

    surfaceContainerLowest = Color(0xFF060606),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1B1B1B),
    surfaceContainerHigh = Color(0xFF252525),
    surfaceContainerHighest = Color(0xFF303030),

    outline = Color(0xFF5C5C5C),
    outlineVariant = Color(0xFF333333),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),

    scrim = Color(0xCC000000),
)

val CurvePalette: List<Color> = listOf(
    Color(0xFF4FC3F7),
    Color(0xFF9CCC65),
    Color(0xFFFFD54F),
    Color(0xFFBA68C8),
    Color(0xFF4DD0E1),
    Color(0xFFFF8A65),
    Color(0xFFF06292),
    Color(0xFFB0BEC5),
)

fun curveColors(primary: Color): List<Color> = buildList {
    add(primary)
    addAll(CurvePalette)
}
